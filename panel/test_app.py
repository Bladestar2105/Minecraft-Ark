import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import requests
import yaml
from docker.errors import NotFound
from docker.models.containers import ExecResult
from werkzeug.security import generate_password_hash

try:
    from panel.app import PanelError, create_app, validate_yaml
except ModuleNotFoundError:
    create_app = None


class FakeContainer:
    def __init__(self, name, project="minecraft-ark"):
        self.name = name
        self.status = "running"
        self.attrs = {
            "Config": {"Labels": {"com.docker.compose.project": project}},
            "State": {"Status": "running", "Health": {"Status": "healthy"}, "StartedAt": "2026-11-01T00:00:00Z"},
            "RestartCount": 0,
        }
        self.commands = []
        self.actions = []

    def reload(self):
        pass

    def exec_run(self, command):
        self.commands.append(command)
        return ExecResult(0, b"There are 0 of a max of 20 players online")

    def logs(self, **kwargs):
        return b"x" * 140000

    def start(self):
        self.actions.append("start")

    def stop(self, timeout):
        self.actions.append(("stop", timeout))

    def restart(self, timeout):
        self.actions.append(("restart", timeout))


class FakeContainers:
    def __init__(self):
        self.items = {name: FakeContainer(name) for name in ["minecraft-ark", "minecraft-dns"]}
        self.lookups = []

    def get(self, name):
        self.lookups.append(name)
        if name not in self.items:
            raise NotFound("missing")
        return self.items[name]


class PanelTests(unittest.TestCase):
    password_hash = generate_password_hash("a-long-test-password", method="scrypt:1024:8:1")

    def setUp(self):
        self.assertIsNotNone(create_app, "Backend factory is not implemented")
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.data = Path(self.temp.name) / "game"
        self.state = Path(self.temp.name) / "state"
        self.data.mkdir()
        self.properties = self.data / "server.properties"
        self.original = b"# preserve me\r\nrcon.password=do-not-return-or-change\r\nmotd=Old\\u0020world\r\nmax-players=20\r\npvp=false\r\nonline-mode=true\r\n"
        self.properties.write_bytes(self.original)
        self.geyser = self.data / "plugins/Geyser-Spigot/config.yml"
        self.geyser.parent.mkdir(parents=True)
        self.geyser.write_text("# keep this\nbedrock:\n  port: 19132\njava:\n  auth-type: floodgate\nconfig-version: 12\n")
        self.containers = FakeContainers()
        def fake_rcon(host, port, password, command):
            result = self.containers.items["minecraft-ark"].exec_run(["rcon-cli", "--", command])
            if result.exit_code:
                raise OSError("Unavailable test RCON")
            return result.output.decode("utf-8")
        transport = patch("panel.app.rcon_command", side_effect=fake_rcon)
        transport.start()
        self.addCleanup(transport.stop)
        self.options = {
            "TESTING": True, "AUTH_USERNAME": "admin", "AUTH_PASSWORD_HASH": self.password_hash,
            "SECRET_KEY": "test-session-secret-at-least-32-characters", "DATA_DIR": str(self.data),
            "STATE_DIR": str(self.state), "DOCKER_CLIENT": SimpleNamespace(containers=self.containers),
            "AGH_BASE_URL": "http://dns:3000", "TRUSTED_HOSTS": ["localhost"],
        }
        self.app = create_app(self.options)
        self.client = self.app.test_client()
        self.csrf = None

    def login(self):
        response = self.client.post("/api/login", json={"username": "admin", "password": "a-long-test-password"}, headers={"Origin": "http://localhost"})
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.csrf = response.json["csrf"]
        return response

    def mutate(self, path, payload, method="post", **kwargs):
        return getattr(self.client, method)(path, json=payload, headers={"Origin": "http://localhost", "X-CSRF-Token": self.csrf}, **kwargs)

    def test_public_dino_pack_download_supports_game_clients_without_login(self):
        pack = self.data / 'plugins/BetterModel/build.zip'
        pack.parent.mkdir(parents=True)
        pack.write_bytes(b'PK\x03\x04public model pack')
        response = self.client.get('/resources/dinos.zip')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, pack.read_bytes())
        self.assertEqual(response.mimetype, 'application/zip')
        response.close()
        partial = self.client.get('/resources/dinos.zip', headers={'Range': 'bytes=0-3'})
        self.assertEqual(partial.status_code, 206)
        self.assertEqual(partial.data, b'PK\x03\x04')
        partial.close()
        self.assertEqual(self.containers.lookups, [])

    def test_dino_pack_route_never_serves_a_linked_or_arbitrary_file(self):
        self.assertEqual(self.client.get('/resources/dinos.zip').status_code, 404)
        pack = self.data / 'plugins/BetterModel/build.zip'
        pack.parent.mkdir(parents=True)
        pack.symlink_to(self.properties)
        rejected = self.client.get('/resources/dinos.zip')
        self.assertEqual(rejected.status_code, 400)
        self.assertNotIn(b'do-not-return-or-change', rejected.data)
        self.assertEqual(self.client.get('/resources/server.properties').status_code, 404)

    def test_portal_config_is_discoverable_and_saves_only_while_stopped(self):
        self.login()
        path = self.data / "plugins/ArkAccounts/config.yml"
        path.parent.mkdir(parents=True)
        original = ('# retain me\nbind: 0.0.0.0\nport: 8101\n'
                    'player-token-file: /private/player\nadmin-token-file: /private/admin\n'
                    'portal-url: https://old.example/\n')
        path.write_text(original)
        entry = next(item for item in self.client.get('/api/configs').json['configs'] if item['id'] == 'ark-accounts')
        self.assertTrue(entry['available'])
        self.assertTrue(entry['requires_stopped'])
        self.assertTrue(entry['restart_required'])
        english = next(item for item in self.client.get('/api/configs', headers={'Accept-Language': 'en'}).json['configs'] if item['id'] == 'ark-accounts')
        self.assertEqual(english['name'], 'Player portal (ArkAccounts)')
        self.assertIn('public portal address', english['description'])
        self.assertIn('overrides portal-url', english['protected_note'])
        snapshot = self.client.get('/api/configs/ark-accounts')
        self.assertEqual(snapshot.status_code, 200)
        hidden = snapshot.json['content']
        self.assertNotIn('/private/', hidden)
        changed = hidden.replace('https://old.example/', 'https://portal.example/') + 'portal-announcement-seconds: 300\nportal-spawn-display: true\n'
        self.assertEqual(self.mutate('/api/configs/ark-accounts', {'content': changed}, method='put').status_code, 409)
        self.assertEqual(path.read_text(), original)
        self.containers.items['minecraft-ark'].status = 'exited'
        saved = self.mutate('/api/configs/ark-accounts', {'content': changed, 'version': snapshot.json['version']}, method='put')
        self.assertEqual(saved.status_code, 200, saved.json)
        self.assertTrue(saved.json['restart_required'])
        self.assertIn('# retain me', path.read_text())
        document = yaml.safe_load(path.read_text())
        self.assertEqual(document['portal-url'], 'https://portal.example/')
        self.assertEqual(document['player-token-file'], '/private/player')
        self.assertEqual(document['admin-token-file'], '/private/admin')
        self.assertEqual(next((self.state / 'config-backups').iterdir()).read_text(), original)

    def test_portal_editor_protects_internal_listener_and_secret_paths(self):
        self.login()
        path = self.data / 'plugins/ArkAccounts/config.yml'
        path.parent.mkdir(parents=True)
        original = 'bind: 0.0.0.0\nport: 8101\nplayer-token-file: /private/player\nadmin-token-file: /private/admin\nportal-url: https://portal.example/\n'
        path.write_text(original)
        self.containers.items['minecraft-ark'].status = 'exited'
        hidden = self.client.get('/api/configs/ark-accounts').json['content']
        for change in [hidden.replace('0.0.0.0', '127.0.0.1'), hidden.replace('8101', '8123'),
                       hidden.replace('bind: 0.0.0.0\n', ''), hidden.replace('port: 8101\n', ''),
                       hidden.replace('player-token-file: "__ARK_PROTECTED__"', 'player-token-file: /changed'),
                       hidden.replace('admin-token-file: "__ARK_PROTECTED__"\n', '')]:
            with self.subTest(change=change):
                self.assertEqual(self.mutate('/api/configs/ark-accounts', {'content': change}, method='put').status_code, 400)
                self.assertEqual(path.read_text(), original)
        self.assertFalse((self.state / 'config-backups').exists())

    def test_portal_config_rejects_unsafe_urls_and_bad_notice_types(self):
        for url in ['https://portal.example/', 'http://localhost:8090/', 'HTTPS://example.com/a?x=1',
                    'https://[::1]:8443/', 'https://portal.example/überblick', 'https://example.com./']:
            with self.subTest(url=url):
                self.assertEqual(validate_yaml('ark-accounts', yaml.safe_dump({'portal-url': url}))['portal-url'], url)
        invalid_urls = [None, '', 'ftp://example.com/', '//example.com', 'https://', 'https://user@example.com/',
                        'https://example.com/#', 'https://example.com/#fragment', 'https://example.com:0/',
                        'https://example.com:65536/', 'https://example.com:/', 'https://bad_host/',
                        'https://-bad.example/', 'https://a..example/', 'https://example.com\\evil/',
                        'https://example.com/\nhello', 'https://example.com/\x7f', 'https://example.com/\x80', 'https://example.com/%xx',
                        'https://example.com/' + 'a' * 512]
        for url in invalid_urls:
            with self.subTest(url=url), self.assertRaises(PanelError):
                validate_yaml('ark-accounts', yaml.safe_dump({'portal-url': url}))
        for value in [0, 30, 300, 86400]:
            validate_yaml('ark-accounts', yaml.safe_dump({'portal-url': 'https://example.com/', 'portal-announcement-seconds': value, 'portal-spawn-display': False}))
        for field, values in [('portal-announcement-seconds', [True, '300', -1, 1, 29, 86401, 30.0]),
                              ('portal-spawn-display', ['true', 1, None])]:
            for value in values:
                with self.subTest(field=field, value=value), self.assertRaises(PanelError):
                    validate_yaml('ark-accounts', yaml.safe_dump({'portal-url': 'https://example.com/', field: value}))

    def test_private_routes_require_login_and_security_headers(self):
        for path in ["/api/map/players", "/api/map/terrain", "/api/map/terrain?world=minecraft:ark_creative", "/api/status", "/api/logs/minecraft", "/api/settings/minecraft", "/api/settings/geyser", "/api/settings/quests", "/api/configs", "/dns/", "/dns/control/status"]:
            with self.subTest(path=path):
                r = self.client.get(path)
                self.assertEqual(r.status_code, 401)
                self.assertEqual(r.headers["Cache-Control"], "no-store")
                self.assertEqual(r.headers["X-Frame-Options"], "SAMEORIGIN")
        self.assertFalse(self.client.get("/api/session").json["authenticated"])

    def test_map_joins_positions_by_name_and_keeps_dimensions_separate(self):
        self.login()
        home = self.data / "admin-map"
        home.mkdir()
        (home / "worlds.json").write_text(json.dumps({"worlds": [
            {"id": "minecraft:overworld", "name": "ARK Survival", "terrain": True, "min_x": -2048, "max_x": 2048, "min_z": -2048, "max_z": 2048},
            {"id": "minecraft:ark_creative", "name": "Kreativwelt", "terrain": False, "min_x": -2048, "max_x": 2048, "min_z": -2048, "max_z": 2048}]}))
        outputs = {
            "execute as @a run data get entity @s Pos": "Mäx has the following entity data: [12.5d, 75.0d, -8.0d]\n.Floodgate has the following entity data: [-2.0d, 90.0d, 3.5d]\nOther has the following entity data: [1d, 2d, 3d]",
            "execute as @a run data get entity @s Dimension": '.Floodgate has the following entity data: "minecraft:ark_creative"\nMäx has the following entity data: "minecraft:overworld"\nNobody has the following entity data: "minecraft:the_nether"',
        }
        item = self.containers.items["minecraft-ark"]
        with patch.object(item, "exec_run", side_effect=lambda command: ExecResult(0, outputs[command[-1]].encode())) as run:
            response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["online"])
        players = {p["name"]: p for p in response.json["players"]}
        self.assertEqual(set(players), {"Mäx", ".Floodgate"})
        self.assertEqual((players["Mäx"]["x"], players["Mäx"]["y"], players["Mäx"]["z"]), (12.5, 75, -8))
        self.assertEqual(players[".Floodgate"]["dimension"], "minecraft:ark_creative")
        self.assertEqual(players[".Floodgate"]["world"], "Kreativwelt")
        self.assertEqual([c.args[0] for c in run.call_args_list], [["rcon-cli", "--", command] for command in outputs])

    def test_map_parses_native_rcon_records_concatenated_without_newlines(self):
        self.login()
        positions = 'ARKTestOne has the following entity data: [50.5d, 75.0d, 240.5d].Mäx has the following entity data: [-951.5810581988984d, 79.0d, -1247.7993549693913d]\n'
        dimensions = 'ARKTestOne has the following entity data: "minecraft:overworld".Mäx has the following entity data: "minecraft:ark_creative"\n'
        with patch.object(self.containers.items["minecraft-ark"], "exec_run", side_effect=[ExecResult(0, positions.encode()), ExecResult(0, dimensions.encode())]):
            response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        players = {player["name"]: player for player in response.json["players"]}
        self.assertEqual(set(players), {"ARKTestOne", ".Mäx"})
        self.assertEqual(players[".Mäx"]["dimension"], "minecraft:ark_creative")
        self.assertEqual(players["ARKTestOne"]["x"], 50.5)

    def test_map_ignores_malformed_nonfinite_and_missing_dimension_positions(self):
        self.login()
        positions = "\n".join(name + " has the following entity data: " + value for name, value in [
            ("Valid", "[1e2d, -64d, .5d]"), ("NaN", "[NaNd, 2d, 3d]"), ("Infinite", "[1e400d, 2d, 3d]"),
            ("Short", "[1d, 2d]"), ("Injection", "[1d, 2d, 3d] trailing"), ("Missing", "[1d, 2d, 3d]")])
        dimensions = "\n".join(name + ' has the following entity data: "minecraft:the_nether"' for name in ["Valid", "NaN", "Infinite", "Short", "Injection"])
        item = self.containers.items["minecraft-ark"]
        with patch.object(item, "exec_run", side_effect=[ExecResult(0, positions.encode()), ExecResult(0, dimensions.encode())]):
            response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        self.assertEqual([p["name"] for p in response.json["players"]], ["Valid"])
        self.assertEqual(response.json["players"][0]["dimension"], "minecraft:the_nether")
        self.assertEqual(response.json["players"][0]["world"], "Nether")

    def test_map_cache_avoids_duplicate_rcon_and_clears_when_server_stops(self):
        self.login()
        item = self.containers.items["minecraft-ark"]
        with patch.object(item, "exec_run", side_effect=[ExecResult(0, b'A has the following entity data: [1d, 2d, 3d]'), ExecResult(0, b'A has the following entity data: "minecraft:overworld"')]) as run:
            first = self.client.get("/api/map/players")
            self.assertEqual(first.status_code, 200)
            self.assertEqual(len(first.json["players"]), 1)
            self.assertEqual(len(self.client.get("/api/map/players").json["players"]), 1)
            self.assertEqual(run.call_count, 2)
            item.status = "exited"
            offline = self.client.get("/api/map/players").json
            self.assertFalse(offline["online"])
            self.assertEqual(offline["players"], [])
            self.assertIn("offline", offline["error"].lower())

    def test_map_rcon_failures_return_no_old_locations(self):
        self.login()
        with patch.object(self.containers.items["minecraft-ark"], "exec_run", return_value=ExecResult(1, b"private error detail")):
            response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json["online"])
        self.assertEqual(response.json["players"], [])
        self.assertNotIn("private error detail", response.get_data(as_text=True))

    def test_map_bad_metadata_uses_safe_island_defaults(self):
        self.login()
        home = self.data / "admin-map"
        home.mkdir()
        (home / "worlds.json").write_text(json.dumps({"worlds": [
            {"id": "../server.properties", "name": "bad", "min_x": 0, "max_x": 10, "min_z": 0, "max_z": 10},
            {"id": "minecraft:overworld", "name": "bad", "min_x": 10 ** 1000, "max_x": 10 ** 1001, "min_z": 0, "max_z": 10}]}))
        response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        worlds = {world["id"]: world for world in response.json["worlds"]}
        self.assertEqual(set(worlds), {"minecraft:overworld", "minecraft:ark_creative", "minecraft:ark_exploration"})
        self.assertEqual(worlds["minecraft:overworld"]["name"], "ARK Survival")
        self.assertEqual(worlds["minecraft:overworld"]["min_x"], -10240)
        self.assertEqual(worlds["minecraft:overworld"]["max_z"], 10240)

    def test_map_tracks_exploration_players_without_a_terrain_image(self):
        self.login()
        outputs = {
            "execute as @a run data get entity @s Pos": ".Explorer has the following entity data: [12.5d, 75.0d, -8.0d]",
            "execute as @a run data get entity @s Dimension": '.Explorer has the following entity data: "minecraft:ark_exploration"',
        }
        item = self.containers.items["minecraft-ark"]
        with patch.object(item, "exec_run", side_effect=lambda command: ExecResult(0, outputs[command[-1]].encode())):
            response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.json["online"])
        self.assertEqual(response.json["worlds"][0]["id"], "minecraft:overworld")
        world = next(world for world in response.json["worlds"] if world["id"] == "minecraft:ark_exploration")
        self.assertEqual(world["name"], "Erkundungswelt")
        self.assertFalse(world["terrain"])
        self.assertEqual(world["terrain_bounds"], {"min_x": -10240, "max_x": 10240, "min_z": -10240, "max_z": 10240})
        self.assertEqual(response.json["players"], [dict(name=".Explorer", dimension=world["id"], world=world["name"], x=12.5, y=75, z=-8)])
        self.assertEqual(self.client.get("/api/map/terrain?world=minecraft:ark_exploration").status_code, 404)

    def test_map_serves_each_world_image_from_a_fixed_allowlist(self):
        self.login()
        home = self.data / "admin-map"
        home.mkdir()
        (home / "island.png").write_bytes(b"ARK terrain")
        (home / "creative.png").write_bytes(b"Creative terrain")
        (home / "exploration.png").write_bytes(b"Exploration terrain")
        for world, expected in [("minecraft:overworld", b"ARK terrain"), ("minecraft:ark_creative", b"Creative terrain"), ("minecraft:ark_exploration", b"Exploration terrain")]:
            with self.subTest(world=world):
                response = self.client.get("/api/map/terrain", query_string={"world": world})
                self.addCleanup(response.close)
                self.assertEqual(response.status_code, 200)
                self.assertEqual(response.data, expected)
                self.assertEqual(response.mimetype, "image/png")
                response.close()
        for world in ["../server.properties", "minecraft:the_nether", "island.png", "/etc/passwd"]:
            response = self.client.get("/api/map/terrain", query_string={"world": world})
            self.addCleanup(response.close)
            self.assertEqual(response.status_code, 404)
        (home / "creative.png").unlink()
        (home / "creative.png").symlink_to(self.properties)
        self.assertEqual(self.client.get("/api/map/terrain?world=minecraft:ark_creative").status_code, 400)

    def test_map_metadata_preserves_full_world_and_rendered_extent_per_world(self):
        self.login()
        home = self.data / "admin-map"
        home.mkdir()
        (home / "creative.png").write_bytes(b"Creative terrain")
        (home / "worlds.json").write_text(json.dumps({"worlds": [
            {"id": "minecraft:overworld", "name": "ARK Survival", "terrain": True,
             "min_x": -10240, "max_x": 10240, "min_z": -10240, "max_z": 10240,
             "terrain_updated_at": "2026-11-02"},
            {"id": "minecraft:ark_creative", "name": "Kreativwelt", "terrain": True,
             "min_x": -10240, "max_x": 10240, "min_z": -10240, "max_z": 10240,
             "terrain_bounds": {"min_x": -512, "max_x": 1024, "min_z": -1024, "max_z": 512},
             "terrain_updated_at": "2026-11-03"}]}))
        response = self.client.get("/api/map/players")
        self.assertEqual(response.status_code, 200)
        worlds = {world["id"]: world for world in response.json["worlds"]}
        self.assertTrue(worlds["minecraft:ark_creative"]["terrain"])
        self.assertEqual(worlds["minecraft:ark_creative"]["terrain_bounds"], {"min_x": -512, "max_x": 1024, "min_z": -1024, "max_z": 512})
        self.assertEqual(worlds["minecraft:ark_creative"]["max_x"], 10240)
        self.assertEqual(worlds["minecraft:overworld"]["min_z"], -10240)
        self.assertEqual(worlds["minecraft:overworld"]["terrain_updated_at"], "2026-11-02")
        self.assertEqual(worlds["minecraft:ark_creative"]["terrain_updated_at"], "2026-11-03")

    def test_map_rejects_invalid_or_outside_rendered_extents(self):
        self.login()
        home = self.data / "admin-map"
        home.mkdir()
        for bounds in [
                {"min_x": -12000, "max_x": 512, "min_z": -512, "max_z": 512},
                {"min_x": 512, "max_x": -512, "min_z": -512, "max_z": 512},
                {"min_x": float("nan"), "max_x": 512, "min_z": -512, "max_z": 512}]:
            with self.subTest(bounds=bounds):
                (home / "worlds.json").write_text(json.dumps({"worlds": [
                    {"id": "minecraft:ark_creative", "name": "Kreativwelt", "min_x": -10240, "max_x": 10240,
                     "min_z": -10240, "max_z": 10240, "terrain_bounds": bounds}]}))
                # A fresh factory keeps this metadata check independent of the position cache.
                client = create_app(self.options).test_client()
                client.set_cookie("ark_panel_session", self.client.get_cookie("ark_panel_session").value)
                worlds = {world["id"]: world for world in client.get("/api/map/players").json["worlds"]}
                self.assertIn("terrain_bounds", worlds["minecraft:ark_creative"])
                self.assertEqual(worlds["minecraft:ark_creative"]["terrain_bounds"], {"min_x": -10240, "max_x": 10240, "min_z": -10240, "max_z": 10240})

    def test_map_image_uses_only_fixed_authenticated_file_and_blocks_symlinks(self):
        self.login()
        self.assertEqual(self.client.get("/api/map/terrain").status_code, 404)
        home = self.data / "admin-map"
        home.mkdir()
        image = home / "island.png"
        content = b"\x89PNG\r\n\x1a\nfixture"
        image.write_bytes(content)
        response = self.client.get("/api/map/terrain?path=server.properties")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.mimetype, "image/png")
        self.assertEqual(response.data, content)
        self.assertEqual(response.headers["Cache-Control"], "no-store")
        response.close()
        image.unlink()
        image.symlink_to(self.properties)
        self.assertEqual(self.client.get("/api/map/terrain").status_code, 400)

    def test_login_rotates_session_and_logout_requires_csrf(self):
        r = self.login()
        self.assertIn("HttpOnly", r.headers["Set-Cookie"])
        self.assertIn("SameSite=Strict", r.headers["Set-Cookie"])
        self.assertEqual(self.client.get("/api/session").json["username"], "admin")
        self.assertEqual(self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost"}).status_code, 403)
        self.assertEqual(self.mutate("/api/logout", {}).status_code, 200)
        self.assertEqual(self.client.get("/api/status").status_code, 401)

    def test_origin_host_and_brute_limit(self):
        credentials = {"username": "admin", "password": "wrong"}
        self.assertEqual(self.client.post("/api/login", json=credentials).status_code, 403)
        self.assertEqual(self.client.post("/api/login", json=credentials, headers={"Origin": "http://evil.invalid"}).status_code, 403)
        self.assertEqual(self.client.get("/api/session", headers={"Host": "evil.invalid"}).status_code, 400)
        for _ in range(5):
            self.assertEqual(self.client.post("/api/login", json=credentials, headers={"Origin": "http://localhost", "X-Forwarded-For": "different"}).status_code, 401)
        self.assertEqual(self.client.post("/api/login", json=credentials, headers={"Origin": "http://localhost"}).status_code, 429)

    def test_brute_limit_expires_without_trusting_forwarded_addresses(self):
        credentials = {"username": "admin", "password": "wrong"}
        with patch("panel.app.time.monotonic", return_value=100):
            for _ in range(5):
                self.client.post("/api/login", json=credentials, headers={"Origin": "http://localhost"})
            blocked = self.client.post("/api/login", json=credentials, headers={"Origin": "http://localhost", "X-Forwarded-For": "8.8.8.8"})
            self.assertEqual(blocked.status_code, 429)
        with patch("panel.app.time.monotonic", return_value=161):
            self.assertEqual(self.client.post("/api/login", json=credentials, headers={"Origin": "http://localhost"}).status_code, 401)

    def test_mutations_reject_missing_csrf_and_cross_origin(self):
        self.login()
        for headers in [{"Origin": "http://localhost"}, {"Origin": "http://evil.invalid", "X-CSRF-Token": self.csrf}, {"X-CSRF-Token": self.csrf}]:
            self.assertEqual(self.client.post("/api/services/minecraft/stop", json={}, headers=headers).status_code, 403)
        self.assertEqual(self.containers.items["minecraft-ark"].actions, [])

    def test_closed_container_targets_and_stack_ownership(self):
        self.login()
        self.assertEqual(self.mutate("/api/services/iptv/stop", {}).status_code, 404)
        self.assertEqual(self.mutate("/api/services/minecraft/remove", {}).status_code, 404)
        self.assertEqual(self.containers.lookups, [])
        self.assertEqual(self.mutate("/api/services/dns/restart", {}).status_code, 200)
        self.assertEqual(self.containers.items["minecraft-dns"].actions, [("restart", 20)])
        self.assertEqual(self.mutate("/api/services/dns/stop", {}).status_code, 200)
        self.assertEqual(self.containers.items["minecraft-dns"].actions, [("restart", 20), ("stop", 20)])
        for action in ["stop", "restart"]:
            self.assertEqual(self.mutate("/api/services/minecraft/" + action, {}).status_code, 200)
        self.assertEqual(self.containers.items["minecraft-ark"].actions, [("stop", 120), ("restart", 120)])
        self.containers.items["minecraft-ark"].attrs["Config"]["Labels"] = {"com.docker.compose.project": "iptv"}
        self.assertEqual(self.mutate("/api/services/minecraft/stop", {}).status_code, 403)
        self.assertEqual(self.containers.items["minecraft-ark"].actions, [("stop", 120), ("restart", 120)])

    def test_rcon_passes_one_literal_argument_and_sdk_execresult(self):
        self.login()
        command = "say $(touch /tmp/nope); hello"
        r = self.mutate("/api/console", {"command": command})
        self.assertEqual(r.status_code, 200)
        self.assertIn("0 of a max", r.json["output"])
        self.assertEqual(self.containers.items["minecraft-ark"].commands, [["rcon-cli", "--", command]])
        self.assertEqual(self.mutate("/api/console", {"command": "--host=attacker.example"}).status_code, 200)
        self.assertEqual(self.containers.items["minecraft-ark"].commands[-1], ["rcon-cli", "--", "--host=attacker.example"])
        for value in ["list\nstop", "a\x00b", "x" * 501, "", 123]:
            self.assertEqual(self.mutate("/api/console", {"command": value}).status_code, 400)
        self.assertEqual(len(self.containers.items["minecraft-ark"].commands), 2)

    def test_status_logs_and_fixed_reload_commands(self):
        self.login()
        r = self.client.get("/api/status")
        self.assertEqual([s["id"] for s in r.json["services"]], ["minecraft", "dns"])
        self.assertIn("0 of a max", r.json["players"])
        self.assertEqual(len(self.client.get("/api/logs/minecraft?tail=20").json["text"].encode()), 128 * 1024)
        self.assertEqual(self.client.get("/api/logs/minecraft?tail=501").status_code, 400)
        for target, command in [("geyser", "geyser-spigot:geyser reload"), ("quests", "quests:questsadmin reload")]:
            self.assertEqual(self.mutate("/api/reload/" + target, {}).status_code, 200)
            self.assertEqual(self.containers.items["minecraft-ark"].commands[-1], ["rcon-cli", "--", command])
        self.assertEqual(self.mutate("/api/reload/iptv", {}).status_code, 404)

    def test_properties_preserve_secrets_and_back_up_exact_original(self):
        self.login()
        r = self.client.get("/api/settings/minecraft")
        self.assertNotIn("do-not-return-or-change", r.get_data(as_text=True))
        self.assertEqual(r.json["settings"]["motd"], "Old world")
        self.assertNotIn("pvp", r.json["settings"])
        changes = {"motd": "Neue Welt 🦖", "max-players": 30, "allow-flight": True}
        r = self.mutate("/api/settings/minecraft", {"settings": changes}, method="put")
        self.assertEqual(r.status_code, 200, r.get_data(as_text=True))
        self.assertTrue(r.json["restart_required"])
        saved = self.properties.read_bytes()
        self.assertIn(b"rcon.password=do-not-return-or-change\r\n", saved)
        self.assertIn(b"online-mode=true\r\n", saved)
        backups = list((self.state / "config-backups").iterdir())
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_bytes(), self.original)
        self.assertEqual(backups[0].stat().st_mode & 0o777, 0o600)
        self.assertEqual(self.client.get("/api/settings/minecraft").json["settings"]["motd"], changes["motd"])

    def test_invalid_properties_do_not_mutate_or_back_up(self):
        self.login()
        for change in [{"rcon.password": "oops"}, {"max-players": True}, {"view-distance": 1}, {"pvp": "true"}, {"motd": "a\nb"}, {"motd": "a\x00b"}, {"difficulty": "extreme"}, {"gamemode": "1"}]:
            self.assertEqual(self.mutate("/api/settings/minecraft", {"settings": change}, method="put").status_code, 400)
        self.assertEqual(self.properties.read_bytes(), self.original)
        self.assertFalse((self.state / "config-backups").exists())

    def test_yaml_rejects_bad_shapes_tags_aliases_and_unknown_paths(self):
        self.login()
        original = self.geyser.read_bytes()
        for content in ["- not a map", "bedrock: []\njava: {}", "x: !!python/object/apply:os.system ['id']", "bedrock: &x {port: 19132}\njava: *x", "bedrock: {port: true}\njava: {}", "bedrock: {port: 19132}\nbedrock: {}\njava: {}"]:
            self.assertEqual(self.mutate("/api/configs/geyser", {"content": content}, method="put").status_code, 400)
        self.assertEqual(self.geyser.read_bytes(), original)
        self.assertEqual(self.mutate("/api/configs/not-a-file", {"content": "x: y"}, method="put").status_code, 404)
        self.assertEqual(self.mutate("/api/configs/geyser", {"content": "x" * (256 * 1024 + 1)}, method="put").status_code, 413)

    def test_yaml_keeps_comments_and_rejects_symlink_escape(self):
        self.login()
        content = "# custom comment\nbedrock:\n  port: 19132\njava:\n  auth-type: floodgate\n"
        self.assertEqual(self.mutate("/api/configs/geyser", {"content": content}, method="put").status_code, 200)
        self.assertEqual(self.geyser.read_text(), content)
        outside = Path(self.temp.name) / "outside.yml"
        outside.write_text(content)
        self.geyser.unlink()
        self.geyser.symlink_to(outside)
        self.assertEqual(self.client.get("/api/configs/geyser").status_code, 400)
        self.assertEqual(self.mutate("/api/configs/geyser", {"content": content}, method="put").status_code, 400)

    def test_failed_atomic_replace_keeps_original_and_backup(self):
        self.login()
        with self.assertLogs(self.app.logger, level="ERROR"):
            with patch("panel.app.os.replace", side_effect=OSError("secret path must not be leaked")):
                r = self.mutate("/api/settings/minecraft", {"settings": {"motd": "New world"}}, method="put")
        self.assertEqual(r.status_code, 500)
        self.assertNotIn("secret path", r.get_data(as_text=True))
        self.assertEqual(self.properties.read_bytes(), self.original)
        self.assertEqual(next((self.state / "config-backups").iterdir()).read_bytes(), self.original)

    def test_unicode_identity_and_csrf_are_rejected_without_internal_errors(self):
        r = self.client.post("/api/login", json={"username": "falscher ädmin", "password": "wrong"}, headers={"Origin": "http://localhost"})
        self.assertEqual(r.status_code, 401)
        self.login()
        r = self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": "ä"})
        self.assertEqual(r.status_code, 403)

    def test_unpaired_unicode_is_invalid_input_and_never_mutates(self):
        self.login()
        for path, body in [("/api/settings/minecraft", {"settings": {"motd": "bad\ud800"}}), ("/api/configs/geyser", {"content": "bad\ud800"})]:
            self.assertEqual(self.mutate(path, body, method="put").status_code, 400)
        self.assertEqual(self.properties.read_bytes(), self.original)

    def test_output_limit_applies_after_decoding_invalid_utf8(self):
        self.login()
        self.containers.items["minecraft-ark"].logs = lambda **kwargs: b"\xff" * 140000
        r = self.client.get("/api/logs/minecraft")
        self.assertLessEqual(len(r.json["text"].encode("utf-8")), 128 * 1024)

    def test_all_config_shapes_accept_real_package_and_reject_unrelated_maps(self):
        self.login()
        fixtures = {"quests": "config/quests/quests.yml", "quest-settings": "config/quests/config.yml", "permissions": "config/permissions.yml"}
        relative = {"quests": "plugins/Quests/storage/quests.yml", "quest-settings": "plugins/Quests/config.yml", "permissions": "permissions.yml"}
        for config_id, fixture in fixtures.items():
            target = self.data / relative[config_id]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(Path(fixture).read_text())
            original = target.read_bytes()
            self.assertEqual(self.mutate("/api/configs/" + config_id, {"content": "unrelated: value"}, method="put").status_code, 400)
            self.assertEqual(target.read_bytes(), original)
            r = self.mutate("/api/configs/" + config_id, {"content": original.decode()}, method="put")
            self.assertEqual(r.status_code, 200, r.get_data(as_text=True))
            self.assertEqual(r.json["restart_required"], config_id == "permissions")

    def test_dns_proxy_fixes_target_strips_credentials_and_rewrites_headers(self):
        self.login()
        upstream = requests.Response()
        upstream.status_code = 302
        upstream._content = b"native response"
        upstream.headers = {"Content-Type": "text/html", "Location": "/login.html", "Set-Cookie": "agh=test; Path=/; HttpOnly", "Connection": "X-Remove", "X-Remove": "secret"}
        with patch("panel.app.requests.Session") as factory:
            proxy = factory.return_value.__enter__.return_value
            proxy.request.return_value = upstream
            r = self.client.post("/dns/control/test?x=1", data=b"{}", headers={"Origin": "http://localhost", "Authorization": "Bearer secret", "X-CSRF-Token": self.csrf, "Connection": "X-Hop", "X-Hop": "remove"})
            self.assertEqual(r.status_code, 302)
            self.assertEqual(r.data, b"native response")
            self.assertEqual(r.headers["Location"], "/dns/login.html")
            self.assertIn("Path=/dns/", r.headers["Set-Cookie"])
            self.assertNotIn("X-Remove", r.headers)
            args, kwargs = proxy.request.call_args
            self.assertEqual(args[:2], ("POST", "http://dns:3000/control/test?x=1"))
            self.assertEqual(kwargs["timeout"], 15)
            self.assertFalse(kwargs["allow_redirects"])
            self.assertFalse(proxy.trust_env)
            forwarded = {k.lower() for k in kwargs["headers"]}
            self.assertFalse(forwarded & {"cookie", "authorization", "x-csrf-token", "x-hop", "host"})

    def test_dns_mutations_need_origin_and_upstream_failure_is_sanitized(self):
        self.login()
        self.assertEqual(self.client.post("/dns/control/restart", data=b"{}").status_code, 403)
        with patch("panel.app.requests.Session") as factory:
            factory.return_value.__enter__.return_value.request.side_effect = requests.ConnectionError("secret upstream detail")
            r = self.client.get("/dns/control/status")
        self.assertEqual(r.status_code, 502)
        self.assertNotIn("secret upstream detail", r.get_data(as_text=True))

    def test_dns_proxy_does_not_follow_external_redirects(self):
        self.login()
        upstream = requests.Response()
        upstream.status_code = 302
        upstream._content = b"redirect"
        upstream.headers = {"Location": "https://untrusted.invalid/"}
        with patch("panel.app.requests.Session") as factory:
            factory.return_value.__enter__.return_value.request.return_value = upstream
            r = self.client.get("/dns/control/status")
        self.assertEqual(r.status_code, 502)
        self.assertEqual(factory.return_value.__enter__.return_value.request.call_count, 1)

    def test_factory_never_uses_default_credentials(self):
        with patch.dict("os.environ", {"PANEL_CREDENTIALS_FILE": str(Path(self.temp.name) / "missing.json")}):
            with self.assertRaises(RuntimeError):
                create_app({"TESTING": True, "DOCKER_CLIENT": self.options["DOCKER_CLIENT"]})

    def test_geyser_form_defaults_match_pinned_schema_without_rewriting_minimal_config(self):
        self.login()
        original = Path("config/geyser.yml").read_bytes()
        self.geyser.write_bytes(original)
        response = self.client.get("/api/settings/geyser")
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertEqual(response.json["settings"], {
            "motd.primary-motd": "Geyser", "motd.secondary-motd": "Another Geyser server.",
            "motd.passthrough-motd": True, "motd.max-players": 100, "motd.passthrough-player-counts": True,
            "gameplay.server-name": "Geyser", "gameplay.command-suggestions": True,
            "gameplay.show-coordinates": True, "gameplay.force-resource-packs": False,
            "gameplay.enable-integrated-pack": False, "debug-mode": False,
        })
        self.assertEqual(self.geyser.read_bytes(), original)
        self.assertFalse((self.state / "config-backups").exists())

    def test_geyser_form_inserts_missing_nested_values_preserving_network_and_comment(self):
        self.login()
        original = Path("config/geyser.yml").read_bytes()
        self.geyser.write_bytes(original)
        changes = {"motd.primary-motd": "ARK: # Eine Welt 🦖", "gameplay.server-name": "Meine Welt", "gameplay.show-coordinates": False, "debug-mode": True}
        response = self.mutate("/api/settings/geyser", {"settings": changes}, method="put")
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertEqual(response.json, {"ok": True, "restart_required": False, "reload_target": "geyser", "version": hashlib.sha256(self.geyser.read_bytes()).hexdigest()})
        text = self.geyser.read_text()
        parsed = yaml.safe_load(text)
        self.assertEqual(parsed["motd"]["primary-motd"], changes["motd.primary-motd"])
        self.assertFalse(parsed["motd"]["integrated-ping-passthrough"])
        self.assertEqual(parsed["bedrock"], yaml.safe_load(original)["bedrock"])
        self.assertEqual(parsed["java"], {"auth-type": "floodgate"})
        self.assertEqual(parsed["config-version"], 12)
        self.assertEqual(parsed["gameplay"], {"server-name": "Meine Welt", "show-coordinates": False, "force-resource-packs": False, "enable-integrated-pack": False})
        self.assertIn("# shortcut: Geyser b1280 lacks Paper's socket-address method, remove after a compatible Geyser update.", text)
        self.assertEqual(next((self.state / "config-backups").iterdir()).read_bytes(), original)
        self.assertEqual(self.client.get("/api/settings/geyser").json["settings"]["gameplay.server-name"], "Meine Welt")
        self.assertEqual(self.containers.lookups, ["minecraft-ark"])
        self.assertEqual(self.containers.items["minecraft-ark"].actions, [])
        self.assertEqual(self.containers.items["minecraft-ark"].commands, [["rcon-cli", "--", "arkpanel settings"]])

    def test_geyser_form_replaces_only_scalar_and_preserves_inline_comments_crlf(self):
        self.login()
        original = b'bedrock:\r\n  port: 19132\r\njava:\r\n  auth-type: floodgate\r\nmotd:\r\n  primary-motd: "Old" # keep this exact comment\r\n  integrated-ping-passthrough: false # workaround\r\nconfig-version: 12\r\n'
        self.geyser.write_bytes(original)
        response = self.mutate("/api/settings/geyser", {"settings": {"motd.primary-motd": "New: # text"}}, method="put")
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertEqual(self.geyser.read_bytes(), original.replace(b'"Old"', b'"New: # text"'))

    def test_structured_plugin_settings_reject_wrong_types_and_unknown_fields_without_mutation(self):
        self.login()
        original = self.geyser.read_bytes()
        for changes in [{"bedrock.port": 1}, {"java.auth-type": "offline"}, {"motd.integrated-ping-passthrough": True}, {"motd.max-players": True}, {"motd.max-players": 0}, {"motd.max-players": 1001}, {"debug-mode": "true"}, {"gameplay.server-name": ""}, {"motd.primary-motd": "line\nbreak"}, {"motd.secondary-motd": "x" * 161}, {}]:
            self.assertEqual(self.mutate("/api/settings/geyser", {"settings": changes}, method="put").status_code, 400, changes)
        self.assertEqual(self.geyser.read_bytes(), original)
        self.assertFalse((self.state / "config-backups").exists())
        self.assertEqual(self.client.get("/api/settings/other").status_code, 404)

    def test_quests_form_reads_and_updates_common_settings_preserving_storage_and_comments(self):
        self.login()
        path = self.data / "plugins/Quests/config.yml"
        path.parent.mkdir(parents=True)
        original = '# existing comment\nmax-quests: 3 # active cap\nclickable-prompts: false\nlanguage: de-DE\nlanguage-override-client: true\nstorage-data:\n  password: "never disclose or change"\nstorage-method:\n  player-data: yaml\n'
        path.write_text(original)
        response = self.client.get("/api/settings/quests")
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertNotIn("never disclose", response.get_data(as_text=True))
        self.assertEqual(response.json["settings"]["max-quests"], 3)
        self.assertEqual(response.json["settings"]["accept-timeout"], 20)
        self.assertFalse(response.json["settings"]["clickable-prompts"])
        changes = {"max-quests": 0, "language": "en-US", "confirm-accept": False, "accept-timeout": 300}
        response = self.mutate("/api/settings/quests", {"settings": changes}, method="put")
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertEqual(response.json, {"ok": True, "restart_required": False, "reload_target": "quests", "version": hashlib.sha256(path.read_bytes()).hexdigest()})
        saved = path.read_text()
        self.assertIn('storage-data:\n  password: "never disclose or change"\n', saved)
        self.assertIn('# existing comment\nmax-quests: 0 # active cap\n', saved)
        self.assertTrue(yaml.safe_load(saved)["language-override-client"])
        self.assertEqual(next((self.state / "config-backups").iterdir()).read_text(), original)
        before = path.read_bytes()
        for changes in [{"max-quests": -1}, {"max-quests": 21}, {"language": "unknown"}, {"accept-timeout": 4}, {"confirm-accept": 1}, {"storage-data.password": "oops"}]:
            self.assertEqual(self.mutate("/api/settings/quests", {"settings": changes}, method="put").status_code, 400)
        self.assertEqual(path.read_bytes(), before)

    def test_plugin_forms_safely_reject_custom_flow_or_block_values_without_losing_comments(self):
        self.login()
        fixtures = [
            'bedrock: {port: 19132}\njava: {auth-type: floodgate}\nmotd: {primary-motd: "old"} # keep\nconfig-version: 12\n',
            'bedrock: {port: 19132}\njava: {auth-type: floodgate}\nmotd:\n  primary-motd: | # keep\n    old line\nconfig-version: 12\n',
            'bedrock: {port: 19132}\njava: {auth-type: floodgate}\nconfig-version: 11\n',
        ]
        for original in fixtures:
            self.geyser.write_text(original)
            response = self.mutate("/api/settings/geyser", {"settings": {"motd.primary-motd": "new"}}, method="put")
            self.assertEqual(response.status_code, 400, response.get_data(as_text=True))
            self.assertIn("Editor", response.json["error"])
            self.assertEqual(self.geyser.read_text(), original)
        self.assertFalse((self.state / "config-backups").exists())

    def test_raw_properties_mask_escaped_and_continued_secrets_restore_exactly_and_check_version(self):
        self.login()
        original = b'# keep\r\nrcon\\.password : pri\\\r\n  vate-secret\r\nmanagement-server-secret=hidden-management\r\nmotd=Old world\r\n'
        self.properties.write_bytes(original)
        response = self.client.get('/api/configs/server')
        self.assertEqual(response.status_code, 200)
        content, version = response.json['content'], response.json['version']
        self.assertNotIn('private-secret', content)
        self.assertNotIn('vate-secret', content)
        self.assertNotIn('hidden-management', content)
        self.assertEqual(version, hashlib.sha256(original).hexdigest())
        saved = self.mutate('/api/configs/server', {'content': content.replace('Old world', 'New world'), 'version': version}, method='put')
        self.assertEqual(saved.status_code, 200, saved.get_data(as_text=True))
        self.assertEqual(self.properties.read_bytes(), original.replace(b'Old world', b'New world'))
        self.assertEqual(saved.json['version'], hashlib.sha256(self.properties.read_bytes()).hexdigest())
        self.assertEqual(self.mutate('/api/configs/server', {'content': content, 'version': version}, method='put').status_code, 409)

    def test_raw_secret_and_managed_connection_edits_fail_without_backup(self):
        self.login()
        original = self.geyser.read_text() + 'database:\n  password: hidden-database\n'
        self.geyser.write_text(original)
        response = self.client.get('/api/configs/geyser')
        self.assertNotIn('hidden-database', response.json['content'])
        for content in [response.json['content'].replace('__ARK_PROTECTED__', 'stolen'), response.json['content'].replace('  auth-type: floodgate\n', ''), response.json['content'].replace('port: 19132', 'port: 19133')]:
            self.assertEqual(self.mutate('/api/configs/geyser', {'content': content}, method='put').status_code, 400)
        self.assertEqual(self.geyser.read_text(), original)
        self.assertFalse((self.state / 'config-backups').exists())

    def test_multiverse_groups_preserve_full_survival_sharing_separate_from_creative(self):
        self.login()
        self.containers.items['minecraft-ark'].status = 'exited'
        target = self.data / 'plugins/Multiverse-Inventories/groups.yml'
        target.parent.mkdir(parents=True)
        original = 'groups:\n  survival:\n    worlds: [ark, ark_nether, ark_the_end]\n    shares: [all]\n  creative:\n    worlds: [ark_creative]\n    shares: [all]\n'
        target.write_text(original)
        for content in [original.replace('[ark, ark_nether, ark_the_end]', '[ark, ark_nether, ark_the_end, ark_creative]').replace('[ark_creative]', '[]'), original.replace('shares: [all]', 'shares: [inventory]', 1)]:
            self.assertEqual(self.mutate('/api/configs/mv-groups', {'content': content}, method='put').status_code, 400)
        self.assertEqual(target.read_text(), original)
        self.assertEqual(self.mutate('/api/configs/mv-groups', {'content': original}, method='put').status_code, 200)

    def test_multiverse_groups_reject_matchers_share_subtraction_and_shadow_groups(self):
        self.login()
        self.containers.items['minecraft-ark'].status = 'exited'
        target = self.data / 'plugins/Multiverse-Inventories/groups.yml'
        target.parent.mkdir(parents=True)
        original = 'groups:\n  survival:\n    worlds: [ark, ark_nether, ark_the_end]\n    shares: [all]\n  creative:\n    worlds: [ark_creative]\n    shares: [all]\n'
        target.write_text(original)
        unsafe = [original.replace('ark_the_end]', 'ark_the_end, "' + pattern + '"]')
                  for pattern in ['*', 'r=.*', '-ark_creative', '!ark_creative', 'ark_*']]
        unsafe += [original.replace('shares: [all]', 'shares: [all, "-inventory"]', 1),
                   original.replace('shares: [all]', 'shares: [all]\n    disabled-shares: [inventory]', 1),
                   original.replace('[ark, ark_nether, ark_the_end]', '[overworld, the_nether, the_end]'),
                   original.replace('[ark_creative]', '[ARK_CREATIVE]'),
                   original + '  shadow:\n    worlds: [ark]\n    shares: [all]\n',
                   original + '  shadow:\n    worlds: ["r=.*"]\n    shares: [all]\n']
        for content in unsafe:
            with self.subTest(content=content):
                self.assertEqual(self.mutate('/api/configs/mv-groups', {'content': content}, method='put').status_code, 400)
                self.assertEqual(target.read_text(), original)
        self.assertFalse((self.state / 'config-backups').exists())
        unrelated = original + '  lobby:\n    worlds: [lobby]\n    shares: [inventory]\n'
        self.assertEqual(self.mutate('/api/configs/mv-groups', {'content': unrelated}, method='put').status_code, 200)

    def test_actual_unknown_properties_are_editable_but_new_or_protected_keys_are_rejected(self):
        self.login()
        self.properties.write_bytes(self.original + b'future-setting=12\nmanagement-server-secret=hidden\n')
        response = self.client.get('/api/settings/minecraft')
        self.assertEqual(response.json['settings']['future-setting'], 12)
        self.assertNotIn('hidden', response.get_data(as_text=True))
        self.assertEqual(self.mutate('/api/settings/minecraft', {'settings': {'future-setting': 13}}, method='put').status_code, 200)
        for settings in [{'new-unknown-setting': 1}, {'pvp': True}, {'management-server-secret': 'other'}]:
            self.assertEqual(self.mutate('/api/settings/minecraft', {'settings': settings}, method='put').status_code, 400)

    def test_actual_property_keys_keep_java_escape_semantics(self):
        self.login()
        self.properties.write_bytes(self.original + b'custom\\ key\\:=5\n')
        response = self.mutate('/api/settings/minecraft', {'settings': {'custom key:': 6}}, method='put')
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        settings = self.client.get('/api/settings/minecraft').json['settings']
        self.assertEqual(settings['custom key:'], 6)
        self.assertNotIn('custom', settings)

    def test_structured_plugin_version_rejects_external_update_without_backup(self):
        self.login()
        snapshot = self.client.get('/api/settings/geyser').json
        external = self.geyser.read_bytes() + b'# external plugin write\n'
        self.geyser.write_bytes(external)
        response = self.mutate('/api/settings/geyser', {'settings': {'debug-mode': True}, 'version': snapshot['version']}, method='put')
        self.assertEqual(response.status_code, 409)
        self.assertEqual(self.geyser.read_bytes(), external)
        self.assertFalse((self.state / 'config-backups').exists())

    def test_advanced_json_requires_stopped_server_and_preserves_secret_and_identity(self):
        self.login()
        target = self.data / 'plugins/BlueMap/core.conf'
        target.parent.mkdir(parents=True)
        original = '{"data":"bluemap","api-token":"hidden-json-secret","render-thread":1}\n'
        target.write_text(original)
        snapshot = self.client.get('/api/configs/bluemap-core').json
        self.assertNotIn('hidden-json-secret', snapshot['content'])
        changed = snapshot['content'].replace('"render-thread": 1', '"render-thread": 2')
        for status in ['running', 'paused', 'restarting']:
            self.containers.items['minecraft-ark'].status = status
            self.assertEqual(self.mutate('/api/configs/bluemap-core', {'content': changed}, method='put').status_code, 409, status)
        self.assertFalse((self.state / 'config-backups').exists())
        self.containers.items['minecraft-ark'].status = 'exited'
        for invalid in ['{"duplicate":1,"duplicate":2}', changed.replace('"bluemap"', '"elsewhere"')]:
            self.assertEqual(self.mutate('/api/configs/bluemap-core', {'content': invalid}, method='put').status_code, 400)
            self.assertEqual(target.read_text(), original)
        response = self.mutate('/api/configs/bluemap-core', {'content': changed, 'version': snapshot['version']}, method='put')
        self.assertEqual(response.status_code, 200, response.get_data(as_text=True))
        self.assertEqual(json.loads(target.read_text()), {'data': 'bluemap', 'api-token': 'hidden-json-secret', 'render-thread': 2})
        self.assertEqual(response.json['version'], hashlib.sha256(target.read_bytes()).hexdigest())


if __name__ == "__main__":
    unittest.main()
