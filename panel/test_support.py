import copy
import tempfile
from types import SimpleNamespace
import unittest

from werkzeug.security import generate_password_hash

from panel.app import create_app
from panel.player_app import create_player_app
from panel.test_player_app import NativeFixture, UUID
from panel.wardrobe_client import WardrobeError

TICKET_ID = "23456789-1234-1234-1234-123456789abc"
OTHER_UUID = "34567890-1234-1234-1234-123456789abc"


class SupportFixture(NativeFixture):
    def __init__(self):
        super().__init__()
        self.support_error = None
        self.delete_closed = True
        self.delete_result = dict(ok=True, token="must-not-leak")
        self.ticket = dict(id=TICKET_ID, player_uuid=UUID, player_name=".Bedrock", priority="normal", status="open", revision=1,
            created_at="2026-11-07T12:00:00Z", updated_at="2026-11-07T12:00:00Z",
            messages=[dict(author="player", message="Help\n<script>plain text</script>", created_at="2026-11-07T12:00:00Z", token="must-not-leak")], token="must-not-leak")

    def request(self, method, path, payload=None, session_token=None, admin=False):
        if path.startswith(("/v1/support", "/v1/admin/support")):
            self.calls.append((method, path, payload, session_token, admin))
            if self.support_error:
                raise self.support_error
            if path in ("/v1/support", "/v1/admin/support") and method == "GET":
                summary = {key: value for key, value in self.ticket.items() if key != "messages"}
                result = dict(tickets=[dict(summary, message_count=1)])
                if self.delete_closed is not None:
                    result["delete_closed"] = self.delete_closed
                return result
            if method == "DELETE":
                return copy.deepcopy(self.delete_result)
            return dict(ticket=copy.deepcopy(self.ticket))
        if path == "/v1/admin/accounts":
            self.calls.append((method, path, payload, session_token, admin))
            return dict(accounts=[dict(uuid=UUID, name=".Bedrock", password_hash="must-not-leak")])
        if path == "/v1/admin/accounts/password":
            self.calls.append((method, path, payload, session_token, admin))
            if self.support_error:
                raise self.support_error
            return dict(ok=True, token="must-not-leak")
        if path in ("/v1/modules", "/v1/admin/modules"):
            self.calls.append((method, path, payload, session_token, admin))
            return dict(accounts=True, wardrobe=False, support=True, token="must-not-leak")
        return super().request(method, path, payload, session_token, admin)


class SupportTests(unittest.TestCase):
    password_hash = generate_password_hash("local-admin-password", method="scrypt:1024:8:1")

    def setUp(self):
        self.native = SupportFixture()
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.admin = create_app(dict(TESTING=True, SECRET_KEY="local-admin-secret-at-least-32-characters", AUTH_USERNAME="admin",
            AUTH_PASSWORD_HASH=self.password_hash, DATA_DIR=self.temp.name, STATE_DIR=self.temp.name, DOCKER_CLIENT=SimpleNamespace(),
            TRUSTED_HOSTS=["localhost"], WARDROBE_CLIENT=self.native)).test_client()
        self.player = create_player_app(dict(TESTING=True, SECRET_KEY="player-secret-at-least-32-characters", SESSION_COOKIE_PATH="/",
            TRUSTED_HOSTS=["localhost"], WARDROBE_CLIENT=self.native)).test_client()
        self.admin_csrf = self.admin.post("/api/login", json=dict(username="admin", password="local-admin-password"),
                                          headers={"Origin": "http://localhost"}).json["csrf"]
        self.player_csrf = self.player.post("/api/login", json=dict(code="ABCD_EFGH"), headers={"Origin": "http://localhost"}).json["csrf"]

    def write(self, client, path, body, method="post"):
        csrf = self.admin_csrf if client is self.admin else self.player_csrf
        return getattr(client, method)(path, json=body, headers={"Origin": "http://localhost", "X-CSRF-Token": csrf})

    def test_support_lists_details_and_replies_use_distinct_native_scopes_and_safe_plain_text(self):
        for client, admin, prefix in ((self.player, False, "/v1/support"), (self.admin, True, "/v1/admin/support")):
            response = client.get("/api/support")
            self.assertEqual(response.status_code, 200, response.json)
            self.assertEqual(response.json["tickets"][0]["message_count"], 1)
            self.assertNotIn("messages", response.json["tickets"][0])
            detail = client.get("/api/support/" + TICKET_ID)
            self.assertEqual(detail.status_code, 200, detail.json)
            self.assertEqual(detail.json["ticket"]["messages"][0]["message"], "Help\n<script>plain text</script>")
            self.assertNotIn("must-not-leak", detail.text + response.text)
            reply = self.write(client, "/api/support/" + TICKET_ID + "/reply", dict(revision=1, message="A reply\nwith lines"))
            self.assertEqual(reply.status_code, 200, reply.json)
            self.assertEqual(self.native.calls[-1][1], prefix + "/" + TICKET_ID + "/reply")
            self.assertEqual(self.native.calls[-1][-1], admin)

    def test_player_can_create_but_cannot_close_or_choose_ticket_owner(self):
        response = self.write(self.player, "/api/support", dict(priority="high", message="Please help"))
        self.assertEqual(response.status_code, 201, response.json)
        for body in (dict(priority="high", message="x", player_uuid=OTHER_UUID), dict(priority="urgent", message="x"),
                     dict(priority="normal", message=""), dict(priority="normal", message="x" * 2001), dict(priority="normal", message="x\tbad")):
            self.assertEqual(self.write(self.player, "/api/support", body).status_code, 400)
        self.assertIn(self.write(self.player, "/api/support/" + TICKET_ID + "/status", dict(status="closed", revision=1), "put").status_code, (404, 405))
        closed = self.write(self.admin, "/api/support/" + TICKET_ID + "/status", dict(status="closed", revision=1), "put")
        self.assertEqual(closed.status_code, 200, closed.json)
        self.assertTrue(self.native.calls[-1][-1])

    def test_native_conflicts_ownership_and_limits_are_preserved(self):
        for code, status in (("stale_revision", 409), ("support_closed", 409), ("support_cooldown", 429), ("support_capacity", 503), ("not_found", 404)):
            self.native.support_error = WardrobeError(code, status)
            response = self.write(self.player, "/api/support/" + TICKET_ID + "/reply", dict(revision=1, message="reply"))
            self.assertEqual(response.status_code, status)
            self.assertEqual(response.json["code"], code)

    def test_player_output_rejects_wrong_owner_and_corrupt_or_excessive_details(self):
        self.native.ticket["player_uuid"] = OTHER_UUID
        response = self.player.get("/api/support/" + TICKET_ID)
        self.assertEqual(response.status_code, 502)
        self.assertNotIn("<script>", response.text)
        self.native.ticket["player_uuid"] = UUID
        for field, value in (("priority", "urgent"), ("revision", True), ("created_at", "bad"),
                             ("messages", self.native.ticket["messages"] * 51)):
            before = self.native.ticket[field]
            self.native.ticket[field] = value
            self.assertEqual(self.admin.get("/api/support/" + TICKET_ID).status_code, 502)
            self.native.ticket[field] = before

    def test_support_writes_require_origin_csrf_exact_fields_and_valid_revision(self):
        for client in (self.player, self.admin):
            self.assertEqual(client.post("/api/support/" + TICKET_ID + "/reply", json=dict(revision=1, message="x")).status_code, 403)
            for body in (dict(revision=True, message="x"), dict(revision=0, message="x"), dict(revision=1, message="x", token="secret")):
                self.assertEqual(self.write(client, "/api/support/" + TICKET_ID + "/reply", body).status_code, 400)
            for path in ("/api/support/not-a-uuid", "/api/support/%2e%2e", "/api/support/" + TICKET_ID + "/unknown"):
                self.assertIn(client.get(path).status_code, (400, 404, 405))

    def test_known_account_reset_is_admin_only_bounded_and_uses_canonical_native_route(self):
        response = self.admin.get("/api/player-accounts")
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(response.json, dict(accounts=[dict(uuid=UUID, name=".Bedrock")]))
        self.assertEqual(self.player.get("/api/player-accounts").status_code, 404)
        body = dict(username=".Bedrock", new_password="new-server-password", confirmation="new-server-password")
        self.assertEqual(self.admin.post("/api/player-accounts/password", json=body, headers={"Origin": "http://localhost"}).status_code, 403)
        response = self.write(self.admin, "/api/player-accounts/password", body)
        self.assertEqual(response.json, {"ok": True})
        self.assertEqual(self.native.calls[-1][1:3], ("/v1/admin/accounts/password", body))
        self.assertTrue(self.native.calls[-1][-1])
        self.assertEqual(self.admin.get("/api/session").json["username"], "admin")
        for invalid in (dict(body, confirmation="other"), dict(body, new_password="short", confirmation="short"), dict(body, uuid=UUID)):
            self.assertEqual(self.write(self.admin, "/api/player-accounts/password", invalid).status_code, 400)
        self.native.support_error = WardrobeError("account_not_found", 404)
        self.assertEqual(self.write(self.admin, "/api/player-accounts/password", body).status_code, 404)

    def test_actual_module_flags_use_each_scope_and_never_echo_extra_fields(self):
        for client, admin in ((self.player, False), (self.admin, True)):
            response = client.get("/api/modules")
            self.assertEqual(response.status_code, 200, response.json)
            self.assertEqual(response.json, dict(accounts=True, wardrobe=False, support=True))
            self.assertEqual(self.native.calls[-1][1], "/v1/admin/modules" if admin else "/v1/modules")
            self.assertEqual(self.native.calls[-1][-1], admin)

    def test_delete_is_admin_only_requires_exact_revision_and_sanitizes_success(self):
        path = "/api/support/" + TICKET_ID
        self.assertEqual(self.admin.delete(path, json=dict(revision=1)).status_code, 403)
        for client, prefix in ((self.player, "/api/support/"), (self.admin, "/api/player/support/")):
            calls = sum(call[0] == "DELETE" for call in self.native.calls)
            self.assertIn(self.write(client, prefix + TICKET_ID, dict(revision=1), "delete").status_code, (404, 405))
            self.assertEqual(sum(call[0] == "DELETE" for call in self.native.calls), calls)
        for body in ({}, dict(revision=True), dict(revision=0), dict(revision="1"), dict(revision=1, owner=UUID)):
            self.assertEqual(self.write(self.admin, path, body, "delete").status_code, 400)
        result = self.write(self.admin, path, dict(revision=1), "delete")
        self.assertEqual(result.status_code, 200, result.json)
        self.assertEqual(result.json, dict(ok=True))
        self.assertEqual(self.native.calls[-1], ("DELETE", "/v1/admin/support/" + TICKET_ID, dict(revision=1), None, True))
        for bad in ({}, dict(ok=False), dict(ok=1)):
            self.native.delete_result = bad
            self.assertEqual(self.write(self.admin, path, dict(revision=1), "delete").status_code, 502)

    def test_delete_preserves_native_open_conflict_missing_and_storage_errors(self):
        for code, status in (("support_open", 409), ("stale_revision", 409), ("not_found", 404), ("storage_unavailable", 503)):
            self.native.support_error = WardrobeError(code, status)
            response = self.write(self.admin, "/api/support/" + TICKET_ID, dict(revision=2), "delete")
            self.assertEqual(response.status_code, status)
            self.assertEqual(response.json["code"], code)

    def test_delete_capability_is_explicit_admin_only_and_old_module_stays_usable(self):
        for value, expected in ((True, True), (False, False), (None, False), ("true", False), (1, False)):
            self.native.delete_closed = value
            result = self.admin.get("/api/support")
            self.assertEqual(result.status_code, 200, result.json)
            self.assertIs(result.json["delete_closed"], expected)
            self.assertNotIn("delete_closed", self.player.get("/api/support").json)


if __name__ == "__main__":
    unittest.main()
