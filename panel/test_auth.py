from datetime import datetime, timedelta, timezone
import json
import os
from pathlib import Path
import tempfile
import time
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from werkzeug.security import check_password_hash, generate_password_hash

from panel.app import create_app


class AccountTests(unittest.TestCase):
    password = "current-test-password"
    new_password = "a-new-private-password"
    password_hash = generate_password_hash(password, method="scrypt:1024:8:1")

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.options = {"TESTING": True, "AUTH_USERNAME": "admin", "AUTH_PASSWORD_HASH": self.password_hash,
                        "SECRET_KEY": "stable-test-session-secret-at-least-32-chars", "DATA_DIR": str(self.root / "game"),
                        "STATE_DIR": str(self.root / "state"), "DOCKER_CLIENT": SimpleNamespace(),
                        "TRUSTED_HOSTS": ["localhost"]}
        self.app = create_app(self.options)
        self.client = self.app.test_client()
        self.auth_path = self.root / "state/panel-auth.json"

    def login(self, client=None, password=None):
        return (client or self.client).post("/api/login", json={"username": "admin", "password": password or self.password},
                                            headers={"Origin": "http://localhost"})

    def change(self, csrf, **changes):
        value = {"current_password": self.password, "new_password": self.new_password, "confirm_password": self.new_password}
        value.update(changes)
        return self.client.post("/api/password", json=value,
                                headers={"Origin": "http://localhost", "X-CSRF-Token": csrf})

    def test_login_cookie_lasts_30_days_without_sliding_and_survives_app_restart(self):
        before = datetime.now(timezone.utc)
        response = self.login()
        self.assertEqual(response.status_code, 200)
        cookie = self.client.get_cookie("ark_panel_session")
        self.assertLess(abs((cookie.expires - before - timedelta(days=30)).total_seconds()), 3)
        self.assertIn("HttpOnly", response.headers["Set-Cookie"])
        self.assertIn("SameSite=Strict", response.headers["Set-Cookie"])
        self.assertNotIn("Set-Cookie", self.client.get("/api/session").headers)
        restarted = create_app(self.options).test_client()
        restarted.set_cookie("ark_panel_session", cookie.value)
        self.assertTrue(restarted.get("/api/session").json["authenticated"])
        now = int(time.time())
        with patch("itsdangerous.timed.TimestampSigner.get_timestamp", return_value=now + 29 * 86400):
            self.assertTrue(restarted.get("/api/session").json["authenticated"])
        with patch("itsdangerous.timed.TimestampSigner.get_timestamp", return_value=now + 31 * 86400):
            self.assertFalse(restarted.get("/api/session").json["authenticated"])

    def test_secure_cookie_is_opt_in_with_exact_environment_value(self):
        for value, secure in ((None, False), ("1", True), ("0", False), ("true", False), ("false", False), ("", False)):
            with self.subTest(value=value), patch.dict("os.environ"):
                os.environ.pop("PANEL_COOKIE_SECURE", None)
                if value is not None:
                    os.environ["PANEL_COOKIE_SECURE"] = value
                client = create_app(self.options).test_client()
                origin = "https://localhost" if secure else "http://localhost"
                response = client.post("/api/login", base_url=origin,
                    json={"username": "admin", "password": self.password}, headers={"Origin": origin})
                self.assertEqual(response.status_code, 200, response.json)
                self.assertEqual(client.get_cookie("ark_panel_session").secure, secure)

    def test_password_change_persists_only_hash_and_revokes_other_existing_cookies(self):
        csrf = self.login().json["csrf"]
        old_cookie = self.client.get_cookie("ark_panel_session").value
        other = create_app(self.options).test_client()
        other.set_cookie("ark_panel_session", old_cookie)
        response = self.change(csrf)
        self.assertEqual(response.status_code, 200, response.json)
        self.assertTrue(response.json["ok"])
        self.assertEqual(response.json["username"], "admin")
        self.assertNotEqual(response.json["csrf"], csrf)
        self.assertTrue(self.client.get("/api/session").json["authenticated"])
        self.assertFalse(other.get("/api/session").json["authenticated"])
        self.assertEqual(other.get("/api/configs").status_code, 401)
        self.assertEqual(self.auth_path.stat().st_mode & 0o777, 0o600)
        stored = json.loads(self.auth_path.read_text())
        self.assertEqual(set(stored), {"password_hash"})
        self.assertNotIn(self.new_password, self.auth_path.read_text())
        self.assertTrue(check_password_hash(stored["password_hash"], self.new_password))
        restarted = create_app(self.options).test_client()
        self.assertEqual(self.login(restarted).status_code, 401)
        self.assertEqual(self.login(restarted, self.new_password).status_code, 200)
        self.assertEqual(self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": csrf}).status_code, 403)
        logout = self.client.post("/api/logout", json={}, headers={"Origin": "http://localhost", "X-CSRF-Token": response.json["csrf"]})
        self.assertEqual(logout.status_code, 200)
        self.assertIsNone(self.client.get_cookie("ark_panel_session"))

    def test_password_validation_preserves_credentials_and_current_session(self):
        csrf = self.login().json["csrf"]
        for fields, status in [({"current_password": "wrong"}, 403), ({"confirm_password": "different"}, 400),
                               ({"new_password": "short", "confirm_password": "short"}, 400),
                               ({"new_password": "x" * 1025, "confirm_password": "x" * 1025}, 400),
                               ({"new_password": None}, 400), ({"current_password": "x" * 1025}, 400)]:
            with self.subTest(fields=fields):
                self.assertEqual(self.change(csrf, **fields).status_code, status)
                self.assertTrue(self.client.get("/api/session").json["authenticated"])
        self.assertTrue(check_password_hash(json.loads(self.auth_path.read_text())["password_hash"], self.password))

    def test_password_change_requires_authentication_origin_and_csrf(self):
        self.assertEqual(self.change("missing").status_code, 401)
        csrf = self.login().json["csrf"]
        for headers in ({"Origin": "http://localhost"}, {"Origin": "http://evil.invalid", "X-CSRF-Token": csrf},
                        {"X-CSRF-Token": csrf}):
            with self.subTest(headers=headers):
                self.assertEqual(self.client.post("/api/password", json={}, headers=headers).status_code, 403)

    def test_wrong_current_password_is_rate_limited_without_logging_out(self):
        csrf = self.login().json["csrf"]
        for _ in range(5):
            self.assertEqual(self.change(csrf, current_password="wrong").status_code, 403)
        self.assertEqual(self.change(csrf).status_code, 429)
        self.assertTrue(self.client.get("/api/session").json["authenticated"])

    def test_failed_atomic_write_keeps_previous_hash_and_session(self):
        csrf = self.login().json["csrf"]
        before = self.auth_path.read_bytes()
        cookie = self.client.get_cookie("ark_panel_session").value
        with patch("panel.app.os.replace", side_effect=OSError("disk unavailable")):
            self.assertEqual(self.change(csrf).status_code, 503)
        self.assertEqual(self.auth_path.read_bytes(), before)
        self.assertEqual(self.client.get_cookie("ark_panel_session").value, cookie)
        self.assertEqual(self.client.get("/api/session").json["csrf"], csrf)
        self.assertEqual(list(self.auth_path.parent.glob(".panel-auth-*")), [])
        self.assertEqual(self.login(create_app(self.options).test_client()).status_code, 200)

    def test_malformed_override_never_falls_back_to_bootstrap_password(self):
        self.login()
        for raw in ("{", '{"password_hash":"invalid"}', '{"password_hash":null}', '[]',
                    json.dumps({"password_hash": self.password_hash}) + " " * 4096 + "invalid"):
            with self.subTest(raw=raw):
                self.auth_path.write_text(raw)
                self.assertEqual(self.client.get("/api/session").status_code, 503)
                restarted = create_app(self.options).test_client()
                self.assertEqual(self.login(restarted).status_code, 503)
                self.assertEqual(self.auth_path.read_text(), raw)

    def test_symlink_override_is_rejected_without_modifying_its_target(self):
        self.login()
        target = self.root / "outside.json"
        original = self.auth_path.read_bytes()
        target.write_bytes(original)
        self.auth_path.unlink()
        self.auth_path.symlink_to(target)
        restarted = create_app(self.options).test_client()
        self.assertEqual(self.login(restarted).status_code, 503)
        self.assertEqual(target.read_bytes(), original)

    def test_trusted_hosts_can_be_set_for_another_private_deployment(self):
        options = dict(self.options)
        del options["TRUSTED_HOSTS"]
        with patch.dict("os.environ", {"PANEL_TRUSTED_HOSTS": "panel.example.test, localhost"}):
            client = create_app(options).test_client()
        self.assertEqual(client.get("/healthz", base_url="http://panel.example.test").status_code, 200)
        self.assertEqual(client.get("/healthz", base_url="http://untrusted.test").status_code, 400)


if __name__ == "__main__":
    unittest.main()
