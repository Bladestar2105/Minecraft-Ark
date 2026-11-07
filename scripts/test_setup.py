import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

from init import initialize
from build_plugins import download


class SetupTests(unittest.TestCase):
    def test_fresh_credentials_are_private_and_never_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data/ark").mkdir(parents=True)
            (root / "data/ark/level.dat").write_bytes(b"fixture")
            (root / ".env.example").write_text("EULA=FALSE\n")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                initialize(root)
            credentials = json.loads((root / "secrets/panel.json").read_text())
            password = (root / "secrets/panel-login.txt").read_text().split("Password: ")[1].strip()
            self.assertNotIn(password, output.getvalue())
            method, salt, digest = credentials["password_hash"].split("$")
            self.assertEqual(method, "scrypt:32768:8:1")
            actual = hashlib.scrypt(password.encode(), salt=salt.encode(), n=32768,
                                    r=8, p=1, maxmem=128 * 1024 * 1024, dklen=64).hex()
            self.assertEqual(actual, digest)
            if importlib.util.find_spec("werkzeug"):
                from werkzeug.security import check_password_hash
                self.assertTrue(check_password_hash(credentials["password_hash"], password))
            self.assertEqual((root / "secrets").stat().st_mode & 0o777, 0o700)
            self.assertEqual((root / "secrets/panel-login.txt").stat().st_mode & 0o777, 0o600)
            before = (root / "secrets/panel.json").read_bytes()
            with self.assertRaises(ValueError):
                initialize(root)
            self.assertEqual(before, (root / "secrets/panel.json").read_bytes())
            self.assertIn("online-mode=true", (root / "data/server.properties").read_text())

    def test_portal_credentials_are_separate_and_upgrade_keeps_existing_secrets(self):
        from init import initialize_portal
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data/ark").mkdir(parents=True)
            (root / "data/ark/level.dat").write_bytes(b"fixture")
            (root / "secrets").mkdir(mode=0o700)
            (root / "secrets/panel.json").write_text('{"existing":"admin"}')
            initialize_portal(root)
            private = root / "secrets"
            player = (private / "wardrobe-player-token").read_text().strip()
            admin = (private / "wardrobe-admin-token").read_text().strip()
            session = json.loads((private / "player.json").read_text())["session_secret"]
            self.assertGreaterEqual(len(player), 48)
            self.assertEqual(len({player, admin, session}), 3)
            before = {p.name: p.read_bytes() for p in private.iterdir()}
            initialize_portal(root)
            self.assertEqual(before, {p.name: p.read_bytes() for p in private.iterdir()})
            self.assertEqual((private / "panel.json").read_text(), '{"existing":"admin"}')
            (private / "wardrobe-admin-token").unlink()
            (private / "wardrobe-admin-token").symlink_to(private / "wardrobe-player-token")
            with self.assertRaises(ValueError):
                initialize_portal(root)

    def test_world_required_and_cached_artifact_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                initialize(root)
            self.assertFalse((root / "secrets").exists())
            (root / "library.jar").write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                download({"name": "library.jar", "sha256": "0" * 64}, root)

    def test_portal_upgrade_can_create_private_directory_but_requires_world(self):
        from init import initialize_portal
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                initialize_portal(root)
            self.assertFalse((root / "secrets").exists())
            (root / "data/ark").mkdir(parents=True)
            (root / "data/ark/level.dat").write_bytes(b"fixture")
            initialize_portal(root)
            self.assertEqual((root / "secrets").stat().st_mode & 0o777, 0o700)

    def test_portal_upgrade_rejects_public_directory_and_equivalent_role_tokens(self):
        from init import initialize_portal
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data/ark").mkdir(parents=True)
            (root / "data/ark/level.dat").write_bytes(b"fixture")
            private = root / "secrets"
            private.mkdir(mode=0o700)
            private.chmod(0o755)
            with self.assertRaises(ValueError):
                initialize_portal(root)
            self.assertEqual(list(private.iterdir()), [])
            private.chmod(0o700)
            (private / "wardrobe-player-token").write_text("a" * 64 + "\n")
            (private / "wardrobe-admin-token").write_text("a" * 64)
            with self.assertRaises(ValueError):
                initialize_portal(root)

    def test_runtime_manifest_preserves_world_enabled_datapack_names(self):
        root = Path(__file__).resolve().parents[1]
        items = json.loads((root / "dependencies-runtime.json").read_text())
        packs = [item["name"] for item in items if item["kind"] == "datapack"]
        self.assertEqual(sorted(packs), ["Dungeons and Taverns v6.1.0.zip", "Geophilic v3.7.dp.zip"])
        self.assertEqual(len(items), len({item["name"] for item in items}))
        for item in items:
            self.assertTrue(item["url"].startswith("https://"))
            self.assertEqual(Path(item["name"]).name, item["name"])
            self.assertTrue("sha256" in item or "sha512" in item)


class FirstSetupTests(unittest.TestCase):
    def test_one_command_uses_verified_binaries_and_keeps_eula_unaccepted(self):
        from setup import setup
        from test_distribution import plugins_fixture
        from test_world_setup import fixture
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            plugins_fixture(root)
            (root / ".env.example").write_text("EULA=FALSE\n")
            (root / "dependencies-runtime.json").write_text("[]")
            (root / "config").mkdir()
            for name in ("geyser.yml", "permissions.yml", "skinsrestorer.yml"):
                (root / "config" / name).write_text("fixture: true\n")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                setup(root, ["accounts", "support"])
            self.assertEqual((root / "data/ark/level.dat").read_bytes(), b"world")
            self.assertTrue((root / "data/plugins/ArkAccounts-1.0.0.jar").is_file())
            self.assertTrue((root / "data/plugins/ArkSupport-1.0.0.jar").is_file())
            self.assertFalse((root / "data/plugins/ArkWardrobe-1.0.0.jar").exists())
            self.assertTrue((root / "secrets/panel-login.txt").is_file())
            self.assertEqual((root / ".env").read_text(), "EULA=FALSE\n")
            self.assertFalse((root / "data/eula.txt").exists())
            self.assertIn("docker compose", output.getvalue())
            self.assertIn("EULA", output.getvalue())
            before = (root / "secrets/panel.json").read_bytes()
            with self.assertRaises(ValueError):
                setup(root, ["accounts", "support"])
            self.assertEqual((root / "secrets/panel.json").read_bytes(), before)

    def test_preflight_rejects_bad_binaries_or_existing_installation_before_world_download(self):
        from setup import setup
        from test_distribution import plugins_fixture
        from unittest.mock import patch
        for existing in (False, True):
            with self.subTest(existing=existing), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                entries = plugins_fixture(root)
                if existing:
                    (root / "data").mkdir()
                else:
                    (root / entries[0]["path"]).write_bytes(b"tampered")
                with patch("setup.install_world", side_effect=AssertionError("must preflight")):
                    with self.assertRaises(ValueError):
                        setup(root)
                self.assertFalse((root / "secrets").exists())
