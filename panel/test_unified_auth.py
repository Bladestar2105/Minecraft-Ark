import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

from werkzeug.security import generate_password_hash

from panel.app import create_app
from panel.player_app import create_player_app
from panel.test_player_app import NativeFixture, UUID
from panel.wardrobe_client import WardrobeError


class UnifiedNative(NativeFixture):
    def __init__(self):
        super().__init__()
        self.is_op = False
        self.role_error = None

    def request(self, method, path, payload=None, session_token=None, admin=False):
        if path == '/v1/admin/auth/player-check':
            self.calls.append((method, path, payload, session_token, admin))
            if self.role_error:
                raise self.role_error
            if not admin or payload != {'token': 'private-player-token'} or self.revoked:
                raise WardrobeError('unauthorized', 401)
            return dict(uuid=UUID, name='.Bedrock', expires_at=self.expiry, is_op=self.is_op)
        return super().request(method, path, payload, session_token, admin)


class UnifiedAuthTests(unittest.TestCase):
    player_key = 'separate-player-session-secret-at-least-32-characters'
    local_hash = generate_password_hash('local-password', method='scrypt:1024:8:1')

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.native = UnifiedNative()
        self.player = create_player_app(dict(TESTING=True, SECRET_KEY=self.player_key,
            SESSION_COOKIE_PATH='/', TRUSTED_HOSTS=['localhost'], WARDROBE_CLIENT=self.native))
        self.app = create_app(dict(TESTING=True, AUTH_USERNAME='owner', AUTH_PASSWORD_HASH=self.local_hash,
            SECRET_KEY='different-local-session-secret-at-least-32-characters', PLAYER_SESSION_SECRET=self.player_key,
            DOCKER_CLIENT=SimpleNamespace(), STATE_DIR=self.tmp.name, DATA_DIR=self.tmp.name,
            TRUSTED_HOSTS=['localhost'], WARDROBE_CLIENT=self.native, PLAYER_BASE_URL='http://players:8091'))
        self.client = self.app.test_client()
        owner = self

        class Connection:
            def __init__(self, *args, **kwargs):
                pass

            def request(self, method, path, body=None, headers=None):
                self.response = owner.player.test_client(use_cookies=False).open(path, method=method,
                    data=body, headers=headers, base_url='http://localhost')

            def getresponse(self):
                response = self.response
                return SimpleNamespace(status=response.status_code, read=lambda limit: response.data[:limit],
                                       getheaders=lambda: list(response.headers))

            def close(self):
                pass

        self.addCleanup(patch.stopall)
        patch('panel.app.http.client.HTTPConnection', Connection).start()

    def login(self, body=None):
        return self.client.post('/api/login', json=body or {'username': '.Bedrock', 'password': 'server-password'},
                                headers={'Origin': 'http://localhost'})

    def test_same_login_accepts_player_credentials_and_ordinary_game_code(self):
        for body in ({'username': '.Bedrock', 'password': 'server-password'}, {'code': 'ABCD_EFGH'}):
            with self.subTest(body=body):
                self.client = self.app.test_client()
                self.native.used = False
                result = self.login(body)
                self.assertEqual(result.status_code, 200, result.json)
                self.assertTrue(result.json['authenticated'])
                self.assertTrue(result.json['is_player'])
                self.assertFalse(result.json['is_admin'])
                self.assertEqual(result.json['username'], '.Bedrock')
                self.assertEqual(result.json['auth_type'], 'player')
                self.assertNotIn('token', result.json)
                cookie = self.client.get_cookie('ark_player_session')
                self.assertIsNotNone(cookie)
                self.assertEqual(cookie.path, '/')
                self.assertEqual(self.client.get('/api/player/me').json['uuid'], UUID)
                self.assertEqual(self.client.get('/api/configs').status_code, 403)
                self.assertEqual(self.client.get('/api/configs').json['code'], 'admin_required')

    def test_op_and_deop_apply_without_relogin_and_keep_personal_session(self):
        self.assertEqual(self.login().status_code, 200)
        for role in (True, False, True):
            self.native.is_op = role
            info = self.client.get('/api/session')
            self.assertEqual(info.status_code, 200, info.json)
            self.assertEqual(info.json['is_admin'], role)
            self.assertTrue(info.json['is_player'])
            self.assertEqual(self.client.get('/api/configs').status_code, 200 if role else 403)
            self.assertEqual(self.client.get('/api/player/me').status_code, 200)

    def test_cookie_role_claims_and_uuid_swap_never_grant_administration(self):
        self.assertEqual(self.login().status_code, 200)
        signed = self.player.test_client()
        signed.set_cookie('ark_player_session', self.client.get_cookie('ark_player_session').value)
        with signed.session_transaction() as value:
            value['is_admin'] = True
            value['authenticated'] = True
            value['auth_type'] = 'local'
        self.client.set_cookie('ark_player_session', signed.get_cookie('ark_player_session').value)
        self.assertFalse(self.client.get('/api/session').json['is_admin'])
        self.assertEqual(self.client.get('/api/configs').status_code, 403)
        self.native.is_op = True
        with signed.session_transaction() as value:
            value['uuid'] = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa'
        self.client.set_cookie('ark_player_session', signed.get_cookie('ark_player_session').value)
        self.assertEqual(self.client.get('/api/configs').status_code, 401)

    def test_revocation_and_outage_fail_closed_and_csrf_protects_both_surfaces(self):
        self.native.is_op = True
        response = self.login()
        self.assertEqual(response.status_code, 200, response.json)
        csrf = response.json['csrf']
        for path in ('/api/configs/no-such-file', '/api/player/recovery'):
            self.assertEqual(self.client.post(path, json={}, headers={'Origin': 'http://localhost'}).status_code, 403)
        self.assertEqual(self.client.post('/api/logout', json={}, headers={'Origin': 'http://evil.test',
            'X-CSRF-Token': csrf}).status_code, 403)
        self.native.role_error = WardrobeError('server_unavailable', 503)
        self.assertEqual(self.client.get('/api/configs').status_code, 503)
        self.native.role_error = None
        self.assertTrue(self.client.get('/api/session').json['is_admin'])
        self.native.revoked = True
        self.assertEqual(self.client.get('/api/configs').status_code, 401)
        self.assertFalse(self.client.get('/api/session').json['authenticated'])

    def test_identity_switch_and_logout_clear_both_cookie_scopes(self):
        self.assertEqual(self.login({'username': 'owner', 'password': 'local-password'}).status_code, 200)
        self.assertIsNotNone(self.client.get_cookie('ark_panel_session'))
        response = self.login()
        self.assertEqual(response.status_code, 200, response.json)
        self.assertIsNone(self.client.get_cookie('ark_panel_session'))
        self.assertEqual(self.client.get('/api/configs').status_code, 403)
        response = self.login({'username': 'owner', 'password': 'local-password'})
        self.assertTrue(response.json['is_admin'])
        self.assertFalse(response.json['is_player'])
        self.assertIsNone(self.client.get_cookie('ark_player_session'))
        self.assertTrue(any('Path=/play/' in value and 'Max-Age=0' in value
                            for value in response.headers.getlist('Set-Cookie')))
        self.native.used = False
        response = self.login()
        logged_out = self.client.post('/api/logout', json={}, headers={'Origin': 'http://localhost',
            'X-CSRF-Token': response.json['csrf']})
        self.assertEqual(logged_out.status_code, 200, logged_out.json)
        self.assertTrue(self.native.revoked)
        self.assertIsNone(self.client.get_cookie('ark_player_session'))
        self.assertFalse(self.client.get('/api/session').json['authenticated'])

    def test_legacy_page_redirect_and_api_flood_rejected_before_upstream(self):
        for path in ('/play', '/play/'):
            response = self.client.get(path)
            self.assertEqual(response.status_code, 302)
            self.assertEqual(response.headers['Location'], '/')
        for _ in range(40):
            self.client.get('/api/session')
        calls = len(self.native.calls)
        response = self.client.get('/api/session')
        self.assertEqual(response.status_code, 429)
        self.assertGreaterEqual(int(response.headers['Retry-After']), 1)
        self.assertEqual(len(self.native.calls), calls)
        self.assertEqual(self.client.get('/healthz').status_code, 200)

    def test_authentication_has_no_side_door_that_can_resurrect_local_identity(self):
        self.assertEqual(self.login({'username': 'owner', 'password': 'local-password'}).status_code, 200)
        calls = len(self.native.calls)
        for prefix in ('/api/player/', '/play/api/'):
            for name in ('login', 'logout', 'recover'):
                response = self.client.post(prefix + name, json={'code': 'ABCD_EFGH'},
                                            headers={'Origin': 'http://localhost'})
                self.assertEqual(response.status_code, 404, (prefix + name, response.json))
        self.assertEqual(len(self.native.calls), calls)

    def test_new_permission_and_rate_errors_follow_the_selected_language(self):
        self.assertEqual(self.login().status_code, 200)
        denied = self.client.get('/api/configs', headers={'Accept-Language': 'en'})
        self.assertEqual(denied.status_code, 403)
        self.assertEqual(denied.json['error'], 'Administrator privileges are required.')
        for app in (self.app, self.player):
            client = app.test_client()
            for _ in range(41):
                response = client.get('/api/session', headers={'Accept-Language': 'en'},
                                      environ_overrides={'REMOTE_ADDR': '203.0.113.73'})
            self.assertEqual(response.status_code, 429)
            self.assertEqual(response.json['error'], 'Too many requests. Please try again later.')


if __name__ == '__main__':
    unittest.main()
