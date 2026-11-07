import base64
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import tempfile
import threading
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch
from unittest.mock import MagicMock

from werkzeug.security import generate_password_hash

from panel.app import create_app

try:
    from panel.player_app import create_player_app
    from panel.wardrobe_client import WardrobeError
except ModuleNotFoundError:
    create_player_app = None
    WardrobeError = None


UUID = "12345678-1234-1234-1234-123456789abc"
PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"fixture").decode()


class NativeFixture:
    def __init__(self):
        self.calls = []
        self.used = False
        self.revoked = False
        self.expiry = time.time() + 30 * 86400
        self.error = None
        self.password_reset_allowed = False

    def request(self, method, path, payload=None, session_token=None, admin=False):
        self.calls.append((method, path, payload, session_token, admin))
        if self.error:
            raise self.error
        if path in ("/v1/pair/redeem", "/v1/login"):
            if self.used:
                raise WardrobeError("unauthorized", 401)
            self.used = True
            return dict(uuid=UUID, name=".Bedrock", token="private-player-token", expires_at=self.expiry)
        if path == "/v1/recover":
            self.revoked = True
            return {"ok": True, "token": "never-auto-login"}
        if admin:
            if path.endswith("/connect"):
                return dict(user_code="ABCD-EFGH", verification_uri="https://microsoft.com/devicelogin",
                            expires_at=time.time() + 600, interval=5, device_code="private-device-token")
            return dict(provider="selfhosted", client_id="operator-client-id", key_configured=True,
                        account=dict(connected=False, access_token="private-access-token"), status="not_verified",
                        refresh_token="private-refresh-token")
        if session_token != "private-player-token" or self.revoked:
            raise WardrobeError("unauthorized", 401)
        if path == "/v1/logout":
            self.revoked = True
            return {"ok": True}
        if path == "/v1/session":
            return dict(uuid=UUID, name=".Bedrock", expires_at=self.expiry)
        if path == "/v1/account":
            return dict(password_configured=True, recovery_enabled=True, recovery_codes_remaining=7,
                        password_reset_allowed=self.password_reset_allowed,
                        password_hash="must-not-leak")
        if path == "/v1/password":
            self.revoked = True
            return {"ok": True}
        if path == "/v1/recovery":
            return dict(codes=["ABCD-EFGH-1234-5678", "JKLM-NPQR-9876-5432"], hashes=["must-not-leak"])
        if path == "/v1/profile":
            return dict(uuid=UUID, name=".Bedrock", online=True, stats=dict(play_time_ticks=20, deaths=0,
                        mob_kills=1, player_kills=0), observed_at="2026-11-07T05:00:00Z", active_skin_id=None,
                        token="must-not-leak")
        skin = dict(id=UUID, name="First", model="classic", revision=1, png=PNG, ready=False,
                    selected=False, status="preparing", signature="must-not-leak")
        return dict(skins=[skin], limit=20) if method == "GET" else dict(skin=skin)


class PlayerTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(create_player_app, "Player app factory is missing")
        self.native = NativeFixture()
        self.options = dict(TESTING=True, SECRET_KEY="player-only-session-secret-at-least-32-characters",
                            SESSION_COOKIE_PATH="/", TRUSTED_HOSTS=["localhost"], WARDROBE_CLIENT=self.native)
        self.app = create_player_app(self.options)
        self.client = self.app.test_client()

    def login(self):
        response = self.client.post("/api/login", json={"code": "ABCD-EFGH"}, headers={"Origin": "http://localhost"})
        self.assertEqual(response.status_code, 200, response.json)
        return response.json["csrf"]

    def write(self, path, payload, method="post", csrf=None):
        return getattr(self.client, method)(path, json=payload, headers={"Origin": "http://localhost",
                                             "X-CSRF-Token": csrf or self.csrf})

    def test_pairing_is_consumed_once_cookie_is_independent_and_server_checks_revocation(self):
        self.csrf = self.login()
        cookie = self.client.get_cookie("ark_player_session")
        self.assertIsNotNone(cookie)
        self.assertIsNone(self.client.get_cookie("ark_panel_session"))
        response = self.client.get("/api/session")
        self.assertEqual(response.json["username"], ".Bedrock")
        self.assertTrue(response.json["authenticated"])
        self.assertNotIn("Set-Cookie", response.headers)
        self.assertEqual(self.client.post("/api/login", json={"code": "ABCD-EFGH"},
                         headers={"Origin": "http://localhost"}).status_code, 401)
        self.native.revoked = True
        self.assertEqual(self.client.get("/api/me").status_code, 401)
        self.assertFalse(self.client.get("/api/session").json["authenticated"])

    def test_generated_pair_code_with_underscore_reaches_native_and_logs_in(self):
        response = self.client.post("/api/login", json={"code": "ABCD_EFGH"}, headers={"Origin": "http://localhost"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(self.native.calls[-1][1:3], ("/v1/pair/redeem", {"code": "ABCD_EFGH"}))
        self.assertTrue(self.client.get("/api/session").json["authenticated"])

    def test_no_cookie_or_admin_session_can_authorize_player_routes(self):
        self.client.set_cookie("ark_panel_session", "not-a-player-cookie")
        self.assertEqual(self.client.get("/api/me").status_code, 401)
        for path in ("/api/settings/wardrobe", "/api/services/minecraft/stop", "/api/console", "/dns/"):
            self.assertIn(self.client.get(path).status_code, (401, 404, 405))
        self.assertEqual(self.native.calls, [])

    def test_mutations_require_exact_origin_and_csrf_including_logout(self):
        for origin in (None, "http://evil.test", "http://localhost.evil.test", "https://localhost"):
            headers = {"Origin": origin} if origin else {}
            self.assertEqual(self.client.post("/api/login", json={"code": "ABCD-EFGH"}, headers=headers).status_code, 403)
        self.csrf = self.login()
        for headers in ({}, {"Origin": "http://localhost"}, {"Origin": "http://evil.test", "X-CSRF-Token": self.csrf}):
            self.assertEqual(self.client.post("/api/logout", json={}, headers=headers).status_code, 403)
        self.assertEqual(self.write("/api/logout", {}).status_code, 200)
        self.assertTrue(self.native.revoked)
        self.assertIsNone(self.client.get_cookie("ark_player_session"))

    def test_malformed_csrf_header_is_denied_without_a_server_error(self):
        self.login()
        response = self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": "\ud800"})
        self.assertEqual(response.status_code, 403)

    def test_profile_and_skin_output_are_allowlisted(self):
        self.csrf = self.login()
        profile = self.client.get("/api/me")
        self.assertEqual(profile.json["uuid"], UUID)
        self.assertEqual(profile.json["stats"]["mob_kills"], 1)
        skins = self.client.get("/api/skins")
        self.assertEqual(skins.json["limit"], 20)
        self.assertNotIn("token", profile.json)
        self.assertNotIn("signature", skins.json["skins"][0])
        self.assertNotIn("must-not-leak", profile.text + skins.text)

    def test_skin_actions_forward_revision_but_never_browser_identity(self):
        self.csrf = self.login()
        body = dict(name="First", model="classic", png=PNG)
        self.assertEqual(self.write("/api/skins", body).status_code, 201)
        self.assertEqual(self.write("/api/skins/" + UUID, dict(body, revision=1), "put").status_code, 200)
        self.assertEqual(self.write("/api/skins/" + UUID + "/copy", dict(name="Copy", revision=1)).status_code, 201)
        self.assertEqual(self.write("/api/skins/" + UUID + "/activate", dict(revision=1)).status_code, 202)
        self.assertEqual(self.write("/api/skins/" + UUID, dict(revision=1), "delete").status_code, 200)
        before = len(self.native.calls)
        self.assertEqual(self.write("/api/skins", dict(body, uuid="someone-else")).status_code, 400)
        self.assertEqual(len(self.native.calls), before + 1)  # session verification only
        self.assertTrue(all(call[3] == "private-player-token" for call in self.native.calls if call[1] != "/v1/pair/redeem"))

    def test_malformed_paths_payloads_and_oversized_images_never_reach_storage(self):
        self.csrf = self.login()
        for path in ("/api/skins/not-an-id", "/api/skins/%2e%2e", "/api/skins/%5csecret", "/api/skins/" + UUID + "/admin"):
            self.assertIn(self.write(path, dict(revision=1), "delete").status_code, (400, 404, 405))
        cases = [dict(name="", model="classic", png=PNG), dict(name="x"*33, model="classic", png=PNG),
                 dict(name="x", model="future", png=PNG), dict(name="x", model="slim", png="https://evil/x.png"),
                 dict(name="x", model="slim", png="x"*400000), dict(name="x\n", model="slim", png=PNG)]
        for body in cases:
            self.assertIn(self.write("/api/skins", body).status_code, (400, 413))
        for revision in (None, True, 0, "1", -1):
            self.assertEqual(self.write("/api/skins/" + UUID + "/activate", dict(revision=revision)).status_code, 400)
        self.assertFalse(any(call[1].startswith("/v1/skins") for call in self.native.calls))

    def test_session_expiry_and_cookie_attributes(self):
        self.login()
        cookie = self.client.get_cookie("ark_player_session")
        self.assertTrue(cookie.http_only)
        self.assertEqual(cookie.same_site, "Strict")
        self.assertLess(abs((cookie.expires - datetime.now(timezone.utc) - timedelta(days=30)).total_seconds()), 3)
        self.native.expiry = time.time() - 1
        self.assertFalse(self.client.get("/api/session").json["authenticated"])
        self.assertEqual(self.client.get("/api/skins").status_code, 401)

    def test_trusted_proxy_host_and_scheme_preserve_exact_public_origin(self):
        proxied = create_player_app(dict(self.options, TRUST_PROXY_HEADERS=True, TRUSTED_HOSTS=["public.test"]))
        client = proxied.test_client()
        response = client.post("/api/login", base_url="http://players:8091", json={"code": "ABCD-EFGH"},
            headers={"X-Forwarded-Host": "public.test", "X-Forwarded-Proto": "https", "Origin": "https://public.test"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(self.client.post("/api/login", json={"code": "ABCD-EFGH"},
            headers={"X-Forwarded-Host": "public.test", "X-Forwarded-Proto": "https", "Origin": "https://public.test"}).status_code, 403)

    def test_upstream_errors_are_localized_and_never_reflect_private_details(self):
        self.csrf = self.login()
        self.native.error = WardrobeError("storage_unavailable", 503)
        english = self.client.get("/api/skins", headers={"Accept-Language": "en-US"})
        german = self.client.get("/api/skins", headers={"Accept-Language": "de-DE"})
        self.assertEqual(english.status_code, 503)
        self.assertEqual(english.json["code"], "storage_unavailable")
        self.assertNotEqual(english.json["error"], german.json["error"])
        self.assertNotIn("private", english.text + german.text)

    def test_missing_secret_files_fail_closed_and_player_factory_never_opens_docker(self):
        with tempfile.TemporaryDirectory() as root, patch.dict("os.environ", {"PLAYER_CREDENTIALS_FILE": root + "/missing"}):
            with self.assertRaises(RuntimeError):
                create_player_app({"TESTING": True, "WARDROBE_CLIENT": self.native})
        with patch("panel.app.docker.from_env", side_effect=AssertionError("Docker must not be constructed")):
            self.assertIsNotNone(create_player_app(self.options))

    def test_password_login_and_account_return_only_public_fields(self):
        login = self.client.post("/api/login", json=dict(username=".Bedrock", password="game-password"),
                                 headers={"Origin": "http://localhost"})
        self.assertEqual(login.status_code, 200, login.json)
        self.assertNotIn("token", login.json)
        account = self.client.get("/api/account")
        self.assertEqual(account.status_code, 200)
        self.assertEqual(account.json, dict(password_configured=True, recovery_enabled=True,
                                          recovery_codes_remaining=7, password_reset_allowed=False))

    def test_account_forwards_only_native_boolean_password_reset_permission(self):
        self.csrf = self.login()
        self.native.password_reset_allowed = True
        self.assertIs(self.client.get("/api/account").json["password_reset_allowed"], True)
        self.native.password_reset_allowed = "true"
        self.assertEqual(self.client.get("/api/account").status_code, 502)

    def test_password_change_needs_current_password_and_clears_browser_session(self):
        self.csrf = self.login()
        body = dict(current_password="old-game-password", new_password="new-game-password", confirmation="new-game-password")
        for invalid in (dict(body, confirmation="other"), dict(body, new_password="short", confirmation="short"),
                        dict(body, uuid=UUID), dict(body, new_password="x"*65, confirmation="x"*65)):
            self.assertEqual(self.write("/api/password", invalid).status_code, 400)
        self.assertEqual(self.write("/api/password", body).status_code, 200)
        self.assertTrue(self.native.revoked)
        self.assertIsNone(self.client.get_cookie("ark_player_session"))

    def test_first_password_setup_forwards_empty_current_password_to_game_proof_authority(self):
        self.csrf = self.login()
        response = self.write("/api/password", dict(current_password="", new_password="new-game-password", confirmation="new-game-password"))
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(self.native.calls[-1][1:3], ("/v1/password", dict(current_password="", new_password="new-game-password", confirmation="new-game-password")))
        self.assertIsNone(self.client.get_cookie("ark_player_session"))

    def test_empty_current_password_never_overrides_native_denial(self):
        self.csrf = self.login()
        original = self.native.request
        def deny_password(method, path, **kwargs):
            if path == "/v1/password":
                raise WardrobeError("invalid_credentials", 401)
            return original(method, path, **kwargs)
        with patch.object(self.native, "request", side_effect=deny_password):
            response = self.write("/api/password", dict(current_password="", new_password="new-game-password", confirmation="new-game-password"))
        self.assertEqual(response.status_code, 401)
        self.assertEqual(response.json["code"], "invalid_credentials")
        self.assertIsNotNone(self.client.get_cookie("ark_player_session"))

    def test_public_auth_throttle_covers_rotating_names_pair_and_recovery_per_source(self):
        self.native.error = WardrobeError("invalid_credentials", 401)
        for index in range(10):
            response = self.client.post("/api/login", json=dict(username="unknown" + str(index), password="wrong-password"),
                headers={"Origin": "http://localhost", "X-Forwarded-For": "198.51.100." + str(index)})
            self.assertEqual(response.status_code, 401)
        calls = len(self.native.calls)
        for path, body in (("/api/login", dict(code="ABCD-EFGH")), ("/api/recover", dict(username="another", recovery_code="wrong-code",
                           new_password="new-game-password", confirmation="new-game-password"))):
            response = self.client.post(path, json=body, headers={"Origin": "http://localhost"})
            self.assertEqual(response.status_code, 429)
            self.assertEqual(response.json["code"], "session_limit")
        self.assertEqual(len(self.native.calls), calls)
        self.native.error = None
        response = self.client.post("/api/login", json=dict(username="known", password="correct-password"),
            headers={"Origin": "http://localhost"}, environ_overrides={"REMOTE_ADDR": "203.0.113.2"})
        self.assertEqual(response.status_code, 200, response.json)

    def test_success_does_not_erase_other_failures_and_throttle_expires(self):
        self.native.error = WardrobeError("invalid_credentials", 401)
        with patch("panel.player_app.time.monotonic", return_value=100):
            for _ in range(9):
                self.client.post("/api/login", json=dict(username="unknown", password="wrong-password"), headers={"Origin": "http://localhost"})
            self.native.error = None
            self.assertEqual(self.client.post("/api/login", json=dict(code="ABCD-EFGH"), headers={"Origin": "http://localhost"}).status_code, 200)
            self.native.error = WardrobeError("invalid_credentials", 401)
            self.assertEqual(self.client.post("/api/login", json=dict(username="other", password="wrong-password"), headers={"Origin": "http://localhost"}).status_code, 401)
            self.assertEqual(self.client.post("/api/login", json=dict(username="other", password="wrong-password"), headers={"Origin": "http://localhost"}).status_code, 429)
        with patch("panel.player_app.time.monotonic", return_value=161):
            self.assertEqual(self.client.post("/api/login", json=dict(username="other", password="wrong-password"), headers={"Origin": "http://localhost"}).status_code, 401)

    def test_trusted_proxy_source_is_used_only_when_explicitly_enabled(self):
        proxied = create_player_app(dict(self.options, TRUST_PROXY_HEADERS=True))
        client = proxied.test_client()
        self.native.error = WardrobeError("invalid_credentials", 401)
        for _ in range(10):
            self.assertEqual(client.post("/api/login", json=dict(username="unknown", password="wrong-password"),
                headers={"Origin": "http://localhost", "X-Forwarded-For": "203.0.113.1"}).status_code, 401)
        self.assertEqual(client.post("/api/login", json=dict(username="other", password="wrong-password"),
            headers={"Origin": "http://localhost", "X-Forwarded-For": "203.0.113.1"}).status_code, 429)
        self.native.error = None
        self.assertEqual(client.post("/api/login", json=dict(code="ABCD-EFGH"),
            headers={"Origin": "http://localhost", "X-Forwarded-For": "203.0.113.2"}).status_code, 200)

    def test_concurrent_auth_attempts_cannot_pass_the_source_budget_while_pending(self):
        entered, release, lock = threading.Event(), threading.Event(), threading.Lock()
        count = 0
        def pending(*args, **kwargs):
            nonlocal count
            with lock:
                count += 1
                if count == 10:
                    entered.set()
            release.wait(timeout=5)
            raise WardrobeError("invalid_credentials", 401)
        def attempt():
            return self.app.test_client().post("/api/login", json=dict(username="unknown", password="wrong-password"),
                                              headers={"Origin": "http://localhost"}).status_code
        with patch.object(self.native, "request", side_effect=pending), ThreadPoolExecutor(max_workers=10) as pool:
            futures = [pool.submit(attempt) for _ in range(10)]
            try:
                self.assertTrue(entered.wait(timeout=5))
                self.assertEqual(attempt(), 429)
                self.assertEqual(count, 10)
            finally:
                release.set()
            self.assertEqual([future.result() for future in futures], [401] * 10)

    def test_new_password_rejects_all_iso_controls_and_old_passwords_remain_usable(self):
        self.csrf = self.login()
        body = dict(current_password="old-game-password", new_password="new-game-password\u0085", confirmation="new-game-password\u0085")
        self.assertEqual(self.write("/api/password", body).status_code, 400)
        self.native.used = False
        login = self.client.post("/api/login", json=dict(username=".Bedrock", password="old\u0085password"),
                                 headers={"Origin": "http://localhost"})
        self.assertEqual(login.status_code, 200)

    def test_known_account_failures_keep_status_and_stable_safe_error_codes(self):
        for code, status in (("invalid_credentials", 401), ("invalid_recovery_code", 401), ("session_limit", 429), ("game_proof_required", 403)):
            self.native.error = WardrobeError(code, status)
            response = self.client.post("/api/login", json=dict(username=".Bedrock", password="old-password"),
                                        headers={"Origin": "http://localhost"})
            self.assertEqual(response.status_code, status)
            self.assertEqual(response.json["code"], code)

    def test_recovery_management_needs_session_proof_and_exposes_new_codes_only_once(self):
        self.assertEqual(self.client.post("/api/recovery", json={}, headers={"Origin": "http://localhost"}).status_code, 401)
        self.csrf = self.login()
        body = dict(current_password="game-password", recovery_code="ABCD-EFGH-1234-5678")
        enabled = self.write("/api/recovery", body)
        self.assertEqual(enabled.status_code, 200, enabled.json)
        self.assertEqual(set(enabled.json), {"codes"})
        self.assertEqual(len(enabled.json["codes"]), 2)
        self.assertNotIn("hashes", enabled.json)
        self.assertNotIn("codes", self.client.get("/api/account").json)
        self.assertEqual(self.write("/api/recovery", body, "delete").json, {"ok": True})

    def test_public_recovery_requires_origin_and_validation_never_auto_logs_in(self):
        body = dict(username=".Bedrock", recovery_code="ABCD-EFGH-1234-5678", new_password="new-game-password", confirmation="new-game-password")
        self.assertEqual(self.client.post("/api/recover", json=body).status_code, 403)
        self.assertEqual(self.client.post("/api/recover", json=dict(body, uuid=UUID), headers={"Origin": "http://localhost"}).status_code, 400)
        response = self.client.post("/api/recover", json=body, headers={"Origin": "http://localhost"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(response.json, {"ok": True})
        self.assertIsNone(self.client.get_cookie("ark_player_session"))
        self.assertEqual(self.client.get("/api/me").status_code, 401)


class PlayerBoundaryTests(unittest.TestCase):
    password_hash = generate_password_hash("test-admin-password", method="scrypt:1024:8:1")

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.native = NativeFixture()
        self.app = create_app(dict(TESTING=True, SECRET_KEY="separate-admin-secret-at-least-32-characters",
            AUTH_USERNAME="admin", AUTH_PASSWORD_HASH=self.password_hash, STATE_DIR=self.tmp.name,
            DATA_DIR=self.tmp.name, DOCKER_CLIENT=SimpleNamespace(), WARDROBE_CLIENT=self.native,
            TRUSTED_HOSTS=["localhost"], PLAYER_BASE_URL="http://player:8091"))
        self.client = self.app.test_client()

    def login(self):
        return self.client.post("/api/login", json=dict(username="admin", password="test-admin-password"),
                                headers={"Origin": "http://localhost"}).json["csrf"]

    def test_signing_routes_require_admin_and_filter_private_tokens(self):
        self.assertEqual(self.client.get("/api/settings/wardrobe").status_code, 401)
        self.client.set_cookie("ark_player_session", "forged-player-cookie")
        self.assertEqual(self.client.get("/api/settings/wardrobe").status_code, 401)
        csrf = self.login()
        settings = self.client.get("/api/settings/wardrobe")
        self.assertEqual(settings.status_code, 200, settings.json)
        self.assertEqual(settings.json["provider"], "selfhosted")
        self.assertNotIn("private", settings.text)
        self.assertEqual(self.client.post("/api/wardrobe/connect", json={}, headers={"Origin":"http://localhost"}).status_code, 403)
        connect = self.client.post("/api/wardrobe/connect", json={}, headers={"Origin":"http://localhost", "X-CSRF-Token":csrf})
        self.assertEqual(connect.status_code, 200, connect.json)
        self.assertEqual(set(connect.json), {"user_code", "verification_uri", "expires_at", "interval"})
        self.assertTrue(self.native.calls[-1][-1])

    def test_even_renamed_valid_player_cookie_cannot_authorize_admin(self):
        self.assertIsNotNone(create_player_app)
        player = create_player_app(dict(TESTING=True, SECRET_KEY="different-player-secret-at-least-32-characters",
                                        SESSION_COOKIE_PATH="/", WARDROBE_CLIENT=self.native, TRUSTED_HOSTS=["localhost"])).test_client()
        self.assertEqual(player.post("/api/login", json={"code": "ABCD-EFGH"}, headers={"Origin": "http://localhost"}).status_code, 200)
        self.client.set_cookie("ark_panel_session", player.get_cookie("ark_player_session").value)
        self.assertFalse(self.client.get("/api/session").json["authenticated"])
        self.assertEqual(self.client.get("/api/settings/wardrobe").status_code, 401)

    def test_public_player_proxy_strips_admin_cookie_and_spoofed_forwarding_headers(self):
        self.client.set_cookie("ark_panel_session", "admin-cookie-secret")
        self.client.set_cookie("ark_player_session", "player-cookie", path="/")
        remote = MagicMock(status=200)
        remote.read.return_value = b'{"authenticated":true}'
        remote.getheaders.return_value = [("Content-Type", "application/json"),
            ("Set-Cookie", "ark_player_session=new-player; Path=/; HttpOnly; SameSite=Strict"),
            ("Set-Cookie", "ark_panel_session=attacker; Path=/"), ("Connection", "X-Hop"), ("X-Hop", "secret")]
        with patch("panel.app.http.client.HTTPConnection") as factory:
            connection = factory.return_value
            connection.getresponse.return_value = remote
            response = self.client.post("/api/player/recovery", json={"code": "ABCD-EFGH"}, headers={"Origin": "http://localhost",
                "Authorization": "Bearer admin-key", "X-Forwarded-Host": "evil.test", "X-Forwarded-Proto": "https", "X-Forwarded-For": "evil-source", "X-Admin-Key": "secret"},
                environ_overrides={"REMOTE_ADDR": "203.0.113.10"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(factory.call_args.args[:2], ("player", 8091))
        args, kwargs = connection.request.call_args
        self.assertEqual(args[:2], ("POST", "/api/recovery"))
        headers = {key.lower(): value for key, value in kwargs["headers"].items()}
        self.assertEqual(headers["cookie"], "ark_player_session=player-cookie")
        self.assertEqual(headers["x-forwarded-host"], "localhost")
        self.assertEqual(headers["x-forwarded-proto"], "http")
        self.assertEqual(headers["x-forwarded-for"], "203.0.113.10")
        self.assertNotIn("authorization", headers)
        self.assertNotIn("x-admin-key", headers)
        self.assertNotIn("X-Hop", response.headers)
        self.assertIn("Path=/;", response.headers["Set-Cookie"])
        self.assertTrue(any("Path=/play/" in cookie and "Max-Age=0" in cookie
                            for cookie in response.headers.getlist("Set-Cookie")))
        self.assertNotIn("ark_panel_session", str(response.headers.getlist("Set-Cookie")))

    def test_player_proxy_rejects_bad_origin_paths_redirects_and_unbounded_response(self):
        self.assertEqual(self.client.post("/api/player/recovery", json={"code": "ABCD-EFGH"}).status_code, 403)
        for path in ("/api/player/%5cadmin", "/api/player/%2e%2e/secret", "/api/player/static/%252e%252e/admin"):
            self.assertEqual(self.client.get(path).status_code, 400)
        remote = MagicMock(status=302)
        remote.read.return_value = b""
        remote.getheaders.return_value = [("Location", "https://evil.test/")]
        with patch("panel.app.http.client.HTTPConnection") as factory:
            factory.return_value.getresponse.return_value = remote
            self.assertEqual(self.client.get("/api/player/me").status_code, 502)
            remote.status = 200
            remote.getheaders.return_value = []
            remote.read.return_value = b"x" * (8 * 1024 * 1024 + 1)
            self.assertEqual(self.client.get("/api/player/me").status_code, 502)

    def test_proxy_does_not_forward_current_admin_csrf_as_player_csrf(self):
        csrf = self.login()
        self.client.set_cookie("ark_player_session", "player-cookie", path="/")
        remote = MagicMock(status=200)
        remote.read.return_value, remote.getheaders.return_value = b"{}", [("Content-Type", "application/json")]
        with patch("panel.app.http.client.HTTPConnection") as factory:
            factory.return_value.getresponse.return_value = remote
            response = self.client.post("/api/player/recovery", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": csrf})
        self.assertEqual(response.status_code, 200)
        outgoing = {key.lower(): value for key, value in factory.return_value.request.call_args.kwargs["headers"].items()}
        self.assertNotIn("x-csrf-token", outgoing)

    def test_player_proxy_exposes_only_player_support_and_never_admin_or_operator_routes(self):
        with patch("panel.app.http.client.HTTPConnection") as factory:
            for path in ("/api/player/operator/login", "/api/player/player-accounts", "/api/player/settings/wardrobe", "/api/player/admin/support"):
                self.assertEqual(self.client.post(path, json={}, headers={"Origin": "http://localhost"}).status_code, 404)
            self.assertFalse(factory.called)
            remote = MagicMock(status=200)
            remote.read.return_value, remote.getheaders.return_value = b'{"tickets":[]}', [("Content-Type", "application/json")]
            factory.return_value.getresponse.return_value = remote
            self.assertEqual(self.client.get("/api/player/support").status_code, 200)
            self.assertEqual(factory.return_value.request.call_args.args[:2], ("GET", "/api/support"))

    def test_concurrent_locales_do_not_mutate_global_metadata_or_original_game_data(self):
        self.login()
        def query(language):
            with self.app.test_request_context("/", headers={"Accept-Language": language}):
                from panel.catalog import SERVER_FIELDS, field_for, rule_help
                return field_for("motd", "Hallo Welt"), rule_help("do_daylight_cycle"), SERVER_FIELDS["motd"]["label"]
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(query, ["en", "de"] * 20))
        self.assertNotEqual(results[0][0]["label"], results[1][0]["label"])
        self.assertEqual({result[2] for result in results}, {"Serverbeschreibung"})
        self.assertEqual(results[0][0]["default"], "A Minecraft Server")


if __name__ == "__main__":
    unittest.main()
