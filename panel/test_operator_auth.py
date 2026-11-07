from pathlib import Path
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from werkzeug.security import generate_password_hash

from panel.app import create_app
from panel.player_app import create_player_app
from panel.test_player_app import NativeFixture, UUID
from panel.wardrobe_client import WardrobeError


class OperatorFixture(NativeFixture):
    def __init__(self):
        super().__init__()
        self.operator = True
        self.operator_revoked = False
        self.operator_error = None
        self.operator_expiry = time.time() + 8 * 3600

    def request(self, method, path, payload=None, session_token=None, admin=False):
        if path.startswith("/v1/admin/auth/"):
            self.calls.append((method, path, payload, session_token, admin))
            if self.operator_error:
                raise self.operator_error
            if not admin or session_token is not None:
                raise WardrobeError("forbidden", 403)
            if not self.operator:
                raise WardrobeError("operator_required", 403)
            if path.endswith("/login"):
                if payload != {"username": "OpAlice", "password": "server-password"} and payload != {"code": "OP_ADMIN_1234"}:
                    raise WardrobeError("invalid_credentials" if "username" in payload else "invalid_code", 401)
                return dict(uuid=UUID, name="OpAlice", token="private-native-operator-token", expires_at=self.operator_expiry,
                            refresh_token="must-not-leak")
            if payload != {"token": "private-native-operator-token"} or self.operator_revoked:
                raise WardrobeError("unauthorized", 401)
            if path.endswith("/logout"):
                self.operator_revoked = True
                return {"ok": True}
            return dict(uuid=UUID, name="OpAlice", expires_at=self.operator_expiry, token="must-not-leak")
        return super().request(method, path, payload, session_token, admin)


class OperatorTests(unittest.TestCase):
    password_hash = generate_password_hash("local-admin-password", method="scrypt:1024:8:1")

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.native = OperatorFixture()
        self.options = dict(TESTING=True, AUTH_USERNAME="local-admin", AUTH_PASSWORD_HASH=self.password_hash,
            SECRET_KEY="local-admin-secret-at-least-32-characters", DATA_DIR=self.tmp.name, STATE_DIR=self.tmp.name,
            DOCKER_CLIENT=SimpleNamespace(), TRUSTED_HOSTS=["localhost"], WARDROBE_CLIENT=self.native)
        self.app = create_app(self.options)
        self.client = self.app.test_client()

    def login(self, body=None):
        return self.client.post("/api/operator/login", json=body or {"username": "OpAlice", "password": "server-password"},
                                headers={"Origin": "http://localhost"})

    def test_operator_password_and_separate_purpose_code_login_hide_native_token(self):
        for body in ({"username": "OpAlice", "password": "server-password"}, {"code": "OP_ADMIN_1234"}):
            response = self.login(body)
            self.assertEqual(response.status_code, 200, response.json)
            self.assertEqual(response.json["auth_type"], "operator")
            self.assertEqual(response.json["username"], "OpAlice")
            self.assertNotIn("private", response.text)
            self.assertNotIn("token", response.json)
            cookie_expiry = self.client.get_cookie("ark_panel_session").expires.timestamp()
            self.assertLess(abs(cookie_expiry - self.native.operator_expiry), 2)
            with self.client.session_transaction() as cookie:
                self.assertEqual(cookie["operator_token"], "private-native-operator-token")
                self.assertNotIn("credential_revision", cookie)
            info = self.client.get("/api/session")
            self.assertTrue(info.json["authenticated"])
            self.assertEqual(info.json["username"], "OpAlice")
            self.assertEqual(info.json["auth_type"], "operator")
            self.assertNotIn("token", info.json)
            self.assertNotIn("Set-Cookie", info.headers)

    def test_unknown_session_role_never_falls_back_to_local_credential_revision(self):
        self.client.post("/api/login", json={"username": "local-admin", "password": "local-admin-password"}, headers={"Origin": "http://localhost"})
        with self.client.session_transaction() as cookie:
            cookie["auth_type"] = "player"
        self.assertEqual(self.client.get("/api/configs").status_code, 401)

    def test_ordinary_code_player_cookie_and_browser_role_cannot_upgrade(self):
        self.assertEqual(self.login({"code": "ABCD_EFGH"}).status_code, 401)
        self.assertEqual(self.login({"username": "OpAlice", "password": "server-password", "role": "operator"}).status_code, 400)
        player = create_player_app(dict(TESTING=True, SECRET_KEY=self.options["SECRET_KEY"], SESSION_COOKIE_PATH="/",
            TRUSTED_HOSTS=["localhost"], WARDROBE_CLIENT=self.native)).test_client()
        self.assertEqual(player.post("/api/login", json={"code": "ABCD_EFGH"}, headers={"Origin": "http://localhost"}).status_code, 200)
        self.client.set_cookie("ark_panel_session", player.get_cookie("ark_player_session").value)
        self.assertEqual(self.client.get("/api/configs").status_code, 401)

    def test_deop_revocation_and_server_outage_fail_closed_on_every_protected_request(self):
        for failure in ("deop", "revision", "server"):
            self.client = self.app.test_client()
            self.native.operator = True
            self.native.operator_revoked = False
            self.native.operator_error = None
            self.assertEqual(self.login().status_code, 200)
            self.assertEqual(self.client.get("/api/configs").status_code, 200)
            before = len(self.native.calls)
            if failure == "deop":
                self.native.operator = False
            elif failure == "revision":
                self.native.operator_revoked = True
            else:
                self.native.operator_error = WardrobeError("server_unavailable", 503)
            response = self.client.get("/api/configs")
            self.assertEqual(response.status_code, {"deop": 403, "revision": 401, "server": 503}[failure])
            self.assertEqual(len(self.native.calls), before + 1)
            self.assertIsNone(self.client.get_cookie("ark_panel_session"))
            self.assertFalse(self.client.get("/api/session").json["authenticated"])

    def test_operator_absolute_expiry_and_public_pages_do_not_depend_on_native(self):
        self.assertEqual(self.login().status_code, 200)
        self.native.operator_error = WardrobeError("server_unavailable", 503)
        before = len(self.native.calls)
        for path in ("/", "/healthz", "/static/style.css"):
            with self.client.get(path) as response:
                self.assertEqual(response.status_code, 200)
        self.assertEqual(len(self.native.calls), before)
        self.native.operator_error = None
        with patch("panel.app.time.time", return_value=self.native.operator_expiry + 1):
            self.assertEqual(self.client.get("/api/configs").status_code, 401)

    def test_local_admin_remains_independent_and_operator_cannot_change_bootstrap_password(self):
        csrf = self.login().json["csrf"]
        path = Path(self.tmp.name) / "panel-auth.json"
        original = path.read_bytes()
        denied = self.client.post("/api/password", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": csrf})
        self.assertEqual(denied.status_code, 403)
        self.assertEqual(denied.json["code"], "operator_local_password_denied")
        self.assertEqual(path.read_bytes(), original)
        self.native.operator_error = WardrobeError("server_unavailable", 503)
        response = self.client.post("/api/login", json={"username": "local-admin", "password": "local-admin-password"},
                                    headers={"Origin": "http://localhost"})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(response.json["auth_type"], "local")
        self.assertEqual(self.client.get("/api/configs").status_code, 200)
        self.assertEqual(self.client.get("/api/session").json["username"], "local-admin")

    def test_operator_logout_revalidates_csrf_and_revokes_native_token(self):
        csrf = self.login().json["csrf"]
        self.assertEqual(self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost"}).status_code, 403)
        response = self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": csrf})
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(response.json["auth_type"], "operator")
        self.assertEqual(response.json["username"], "OpAlice")
        self.assertTrue(self.native.operator_revoked)
        self.assertIsNone(self.client.get_cookie("ark_panel_session"))

    def test_public_operator_login_origin_and_source_attempt_budget(self):
        self.assertEqual(self.client.post("/api/operator/login", json={"code": "OP_ADMIN_1234"}).status_code, 403)
        for index in range(5):
            self.assertEqual(self.login({"username": "unknown" + str(index), "password": "wrong-password"}).status_code, 401)
        calls = len(self.native.calls)
        self.assertEqual(self.login().status_code, 429)
        self.assertEqual(len(self.native.calls), calls)
        response = self.client.post("/api/operator/login", json={"code": "OP_ADMIN_1234"}, headers={"Origin": "http://localhost"},
                                    environ_overrides={"REMOTE_ADDR": "203.0.113.99"})
        self.assertEqual(response.status_code, 200)


if __name__ == "__main__":
    unittest.main()
