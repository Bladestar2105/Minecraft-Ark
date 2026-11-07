from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import tempfile
import threading
import unittest

from panel.wardrobe_client import WardrobeClient, WardrobeError, fixed_origin, signing_connection, module_status


class PrivateFixture(BaseHTTPRequestHandler):
    status = 200
    body = b'{"ok":true}'
    media = "application/json"
    last = None

    def do_GET(self):
        type(self).last = (self.command, self.path, dict(self.headers), self.rfile.read(int(self.headers.get("Content-Length", 0))))
        self.send_response(type(self).status)
        self.send_header("Content-Type", type(self).media)
        self.send_header("Content-Length", str(len(type(self).body)))
        self.end_headers()
        self.wfile.write(type(self).body)

    do_POST = do_PUT = do_DELETE = do_GET

    def log_message(self, *_):
        pass


class WardrobeClientTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), PrivateFixture)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.token = Path(self.tmp.name) / "token"
        self.token.write_text("player-service-token-at-least-32-characters\n")
        self.base = "http://127.0.0.1:" + str(self.server.server_port)
        self.client = WardrobeClient(self.base, self.token)
        PrivateFixture.status, PrivateFixture.body, PrivateFixture.media, PrivateFixture.last = 200, b'{"ok":true}', "application/json", None

    def test_real_http_uses_fixed_paths_scoped_headers_and_json_only(self):
        self.assertEqual(self.client.request("POST", "/v1/pair/redeem", {"code": "ABCD-EFGH"}), {"ok": True})
        method, path, headers, body = PrivateFixture.last
        self.assertEqual((method, path), ("POST", "/v1/pair/redeem"))
        self.assertEqual(headers["Authorization"], "Bearer player-service-token-at-least-32-characters")
        self.assertNotIn("X-Player-Session", headers)
        self.assertEqual(json.loads(body), {"code": "ABCD-EFGH"})
        self.client.request("GET", "/v1/profile", session_token="private-player-session")
        self.assertEqual(PrivateFixture.last[2]["X-Player-Session"], "private-player-session")

    def test_player_service_cannot_select_admin_scope_or_arbitrary_paths(self):
        for method, path, admin in (("GET", "/v1/admin/signing", True), ("GET", "/v1/admin/signing", False),
                                     ("GET", "/v1/skins/../admin/signing", False), ("GET", "https://evil.test/", False),
                                     ("POST", "/v1/profile", False), ("GET", "/v1/skins?uuid=other", False)):
            with self.subTest(path=path), self.assertRaises(WardrobeError) as error:
                self.client.request(method, path, admin=admin)
            self.assertEqual(error.exception.status, 403)
        self.assertIsNone(PrivateFixture.last)
        admin = WardrobeClient(self.base, self.token, admin=True)
        self.assertEqual(admin.request("GET", "/v1/admin/signing", admin=True), {"ok": True})
        with self.assertRaises(WardrobeError):
            admin.request("GET", "/v1/profile", session_token="private-player-session")

    def test_support_modules_and_operator_http_keep_service_keys_scoped(self):
        self.client.request("GET", "/v1/support", session_token="private-player-session")
        self.assertEqual(PrivateFixture.last[2]["X-Player-Session"], "private-player-session")
        admin_token = Path(self.tmp.name) / "admin-token"
        admin_token.write_text("admin-service-token-at-least-32-characters")
        admin = WardrobeClient(self.base, admin_token, admin=True)
        for method, path, payload in (("GET", "/v1/admin/accounts", None), ("GET", "/v1/admin/support", None),
                                     ("GET", "/v1/admin/modules", None), ("POST", "/v1/admin/auth/login", {"code": "OP_ADMIN_1234"}),
                                     ("POST", "/v1/admin/auth/check", {"token": "private-operator-token"}),
                                     ("POST", "/v1/admin/auth/logout", {"token": "private-operator-token"})):
            admin.request(method, path, payload=payload, admin=True)
            self.assertEqual(PrivateFixture.last[2]["Authorization"], "Bearer admin-service-token-at-least-32-characters")
            self.assertNotIn("X-Player-Session", PrivateFixture.last[2])
        for path in ("/v1/admin/auth/login", "/v1/admin/accounts/password", "/v1/admin/support"):
            with self.assertRaises(WardrobeError) as error:
                self.client.request("POST", path, session_token="private-player-session")
            self.assertEqual(error.exception.status, 403)

    def test_module_status_rejects_browser_style_claims_or_missing_core(self):
        for flags in (dict(accounts=False, wardrobe=True, support=True), dict(accounts=True, wardrobe="yes", support=True),
                      dict(accounts=True, wardrobe=True), dict(accounts=True, wardrobe=1, support=False)):
            with self.assertRaises(WardrobeError) as error:
                module_status(flags)
            self.assertEqual(error.exception.status, 502)

    def test_malformed_error_redirects_and_oversized_responses_are_sanitized(self):
        for status, body, media in ((401, b'{"code":"invalid_credentials","error":"private-token"}', "application/json"),
                                    (429, b'{"code":"busy","error":"private-token"}', "application/json"),
                                    (302, b'{"code":"private_token"}', "application/json"),
                                    (200, b'not-json private-token', "application/json"),
                                    (200, b'[]', "application/json"),
                                    (200, b'{"code":{}}', "text/html"),
                                    (200, b'x' * (8 * 1024 * 1024 + 1), "application/json")):
            PrivateFixture.status, PrivateFixture.body, PrivateFixture.media = status, body, media
            with self.subTest(status=status, media=media), self.assertRaises(WardrobeError) as error:
                self.client.request("GET", "/v1/profile", session_token="private-player-session")
            self.assertNotIn("private", str(error.exception))
            self.assertIn(error.exception.status, (401, 429, 502))

    def test_secret_files_and_origins_fail_closed(self):
        for origin in ("https://evil.test/path", "http://user:pass@host", "file:///x", "http://host?target=x", "http://host:bad", "http://host/#x"):
            with self.subTest(origin=origin), self.assertRaises(RuntimeError):
                fixed_origin(origin)
        self.token.write_text("short")
        with self.assertRaises(RuntimeError):
            WardrobeClient(self.base, self.token)
        target = Path(self.tmp.name) / "linked"
        target.symlink_to(self.token)
        with self.assertRaises(RuntimeError):
            WardrobeClient(self.base, target)

    def test_malformed_error_code_never_escapes_as_a_type_error(self):
        PrivateFixture.status, PrivateFixture.body = 400, b'{"code":{"token":"private-token"}}'
        with self.assertRaises(WardrobeError) as error:
            self.client.request("GET", "/v1/profile", session_token="private-player-session")
        self.assertEqual(error.exception.code, "upstream_invalid")

    def test_device_flow_rejects_bad_destinations_and_nonfinite_or_negative_timing(self):
        body = dict(user_code="ABCD-EFGH", verification_uri="https://microsoft.com/devicelogin", expires_at=2000000000, interval=5)
        for changes in (dict(verification_uri="https://microsoft.com:bad/device"), dict(verification_uri="https://evil.test/device"),
                        dict(expires_at=float("nan")), dict(expires_at=-1), dict(interval=0), dict(interval=999999)):
            with self.subTest(changes=changes), self.assertRaises(WardrobeError) as error:
                signing_connection(dict(body, **changes))
            self.assertEqual(error.exception.status, 502)


if __name__ == "__main__":
    unittest.main()
