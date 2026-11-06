import base64
import json
import unittest
from unittest.mock import patch

import panel.test_app as helpers


class AdminTests(unittest.TestCase):
    password_hash = helpers.PanelTests.password_hash
    setUp = helpers.PanelTests.setUp
    login = helpers.PanelTests.login
    mutate = helpers.PanelTests.mutate

    def bridge(self, worlds=None, players=None):
        item = self.containers.items['minecraft-ark']
        previous = item.exec_run
        def run(argv):
            command = argv[-1]
            if command.startswith('arkpanel '):
                item.commands.append(argv)
                if command == 'arkpanel worlds':
                    result = {'worlds': worlds or [dict(name='ark', key='minecraft:overworld', difficulty='easy',
                        rules=[dict(name='minecraft:keep_inventory', type='boolean', value=True),
                               dict(name='minecraft:random_tick_speed', type='integer', value=3)])]}
                elif command == 'arkpanel players':
                    result = players or {'players': [dict(name='.Yoshisan88', key='minecraft:overworld', world='ark', gamemode='survival')], 'whitelist': [], 'banned': []}
                else:
                    result = {'available': True, 'ready': True, 'start': 0, 'end': 2,
                              'suggestions': [{'text': 'give'}, {'text': '<unsafe>'}]}
                return helpers.ExecResult(0, ('ARKPANEL ' + json.dumps(result)).encode())
            return previous(argv)
        item.exec_run = run

    def test_catalog_covers_server_paper_and_installed_plugins(self):
        self.login()
        configs = self.client.get('/api/configs').json['configs']
        ids = {c['id'] for c in configs}
        self.assertTrue({'server', 'paper-global', 'paper-world-defaults', 'spigot', 'bukkit',
                         'mv-core', 'mv-worlds', 'mv-inventories', 'mv-groups', 'mv-portals',
                         'worldguard', 'griefprevention', 'parties', 'quest-conditions',
                         'floodgate', 'ark-dinos', 'chunky', 'bluemap-core'} <= ids)
        self.assertTrue(all('format' in c and 'requires_stopped' in c for c in configs))

    def test_server_fields_are_native_and_secrets_never_returned(self):
        self.login()
        self.properties.write_bytes(self.original + b'management-server-secret=another-secret\n')
        response = self.client.get('/api/settings/minecraft')
        self.assertIn('fields', response.json)
        fields = {f['key'] for f in response.json['fields']}
        self.assertTrue({'allow-flight', 'player-idle-timeout', 'spawn-protection', 'force-gamemode'} <= fields)
        self.assertNotIn('pvp', fields)
        self.assertNotIn('another-secret', response.get_data(as_text=True))
        self.assertEqual(self.mutate('/api/settings/minecraft', {'settings': {'allow-flight': True}}, method='put').status_code, 200)

    def test_advanced_properties_preserve_protected_scalars_and_detect_stale_edits(self):
        self.login()
        response = self.client.get('/api/configs/server')
        self.assertEqual(response.status_code, 200)
        self.assertNotIn('do-not-return-or-change', response.json['content'])
        changed = response.json['content'].replace('Old\\u0020world', 'Browser world')
        self.assertEqual(self.mutate('/api/configs/server', {'content': changed, 'version': response.json['version']}, method='put').status_code, 200)
        self.assertIn(b'rcon.password=do-not-return-or-change', self.properties.read_bytes())
        self.assertEqual(self.mutate('/api/configs/server', {'content': changed, 'version': response.json['version']}, method='put').status_code, 409)
        fresh = self.client.get('/api/configs/server').json
        forbidden = fresh['content'].replace('__ARK_PROTECTED__', 'changed-secret')
        before = self.properties.read_bytes()
        self.assertEqual(self.mutate('/api/configs/server', {'content': forbidden}, method='put').status_code, 400)
        self.assertEqual(before, self.properties.read_bytes())

    def test_runtime_owned_config_requires_stopped_server(self):
        self.login()
        path = self.data / 'plugins/Multiverse-Core/worlds.yml'
        path.parent.mkdir(parents=True)
        path.write_text('worlds:\n  ark: {}\n')
        original = path.read_bytes()
        self.assertEqual(self.mutate('/api/configs/mv-worlds', {'content': 'worlds:\n  ark: {alias: Test}\n'}, method='put').status_code, 409)
        self.assertEqual(path.read_bytes(), original)
        self.containers.items['minecraft-ark'].status = 'exited'
        self.assertEqual(self.mutate('/api/configs/mv-worlds', {'content': 'worlds:\n  ark: {alias: Test}\n'}, method='put').status_code, 200)

    def test_native_world_rule_validation_and_floodgate_player_actions(self):
        self.login()
        self.bridge()
        response = self.client.get('/api/worlds')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json['worlds'][0]['key'], 'minecraft:overworld')
        response = self.mutate('/api/worlds/minecraft:overworld', {'action': 'rule', 'name': 'minecraft:keep_inventory', 'value': False}, method='put')
        self.assertEqual(response.status_code, 200, response.json)
        self.assertEqual(self.containers.items['minecraft-ark'].commands[-1][-1], 'execute in minecraft:overworld run gamerule minecraft:keep_inventory false')
        self.assertEqual(self.mutate('/api/worlds/minecraft:overworld', {'action': 'rule', 'name': 'minecraft:keep_inventory', 'value': 'false'}, method='put').status_code, 400)
        self.assertEqual(self.mutate('/api/worlds/minecraft:overworld', {'action': 'rule', 'name': 'made-up', 'value': True}, method='put').status_code, 400)
        self.assertEqual(self.mutate('/api/players/action', {'action': 'kick', 'player': '.Yoshisan88', 'reason': 'Test'}, method='post').status_code, 200)
        self.assertEqual(self.containers.items['minecraft-ark'].commands[-1][-1], 'kick .Yoshisan88 Test')
        self.assertEqual(self.mutate('/api/players/action', {'action': 'op', 'player': 'name\nstop'}).status_code, 400)

    def test_completion_only_uses_encoded_read_protocol(self):
        self.login()
        self.bridge()
        response = self.mutate('/api/console/complete', {'input': 'gi', 'cursor': 2})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json['suggestions'][0]['text'], 'give')
        commands = self.containers.items['minecraft-ark'].commands
        self.assertEqual(commands[-1][-1], 'arkpanel complete 2 ' + base64.urlsafe_b64encode(b'gi').decode().rstrip('='))
        self.assertTrue(all(c[-1].startswith('arkpanel ') for c in commands))
        self.assertEqual(self.mutate('/api/console/complete', {'input': 'gi\nstop', 'cursor': 2}).status_code, 400)

    def test_world_registry_uses_complete_rcon_response_beyond_one_packet(self):
        self.login()
        worlds = [dict(name='ark', key='minecraft:overworld',
                       rules=[dict(name='minecraft:test_' + str(i), type='boolean', value=True) for i in range(100)])]
        complete = 'ARKPANEL ' + json.dumps({'worlds': worlds})
        self.assertGreater(len(complete), 4096)
        with patch('panel.app.rcon_command', return_value=complete, create=True) as run:
            response = self.client.get('/api/worlds')
        self.assertEqual(response.status_code, 200)
        actual = response.json['worlds'][0]
        self.assertEqual(actual['key'], worlds[0]['key'])
        self.assertEqual([{key: value for key, value in rule.items() if key not in ['label', 'description']}
                          for rule in actual['rules']], worlds[0]['rules'])
        self.assertTrue(all(rule['description'] for rule in actual['rules']))
        run.assert_called_once_with('minecraft-ark', 25575, 'do-not-return-or-change', 'arkpanel worlds')

    def test_map_routes_require_auth_and_stream_without_forwarding_credentials(self):
        self.assertEqual(self.client.get('/map/').status_code, 401)
        self.assertEqual(self.client.get('/api/map/viewer').status_code, 401)
        self.login()
        remote = helpers.SimpleNamespace(status_code=200, headers={'Content-Type': 'text/event-stream', 'Cache-Control': 'public'},
            iter_content=lambda chunk_size: iter([b'data: tile\n\n']), close=lambda: None)
        with patch('panel.app.requests.Session') as factory:
            factory.return_value.get.return_value = remote
            response = self.client.get('/map/maps/ark/live/sse', headers={'Authorization': 'should-not-forward'})
            self.assertEqual(response.status_code, 200)
            self.assertEqual(response.data, b'data: tile\n\n')
            self.assertEqual(response.headers['Cache-Control'], 'no-store')
            forwarded = factory.return_value.get.call_args.kwargs['headers']
            self.assertNotIn('Authorization', forwarded)
            self.assertNotIn('Cookie', forwarded)

    def test_moderation_success_can_echo_error_words_without_reporting_failure(self):
        self.login()
        item = self.containers.items['minecraft-ark']
        item.exec_run = lambda args: helpers.ExecResult(0, b'Banned .Yoshisan88: Could not follow the rules')
        response = self.mutate('/api/players/action', {'action': 'ban', 'player': '.Yoshisan88', 'reason': 'Could not follow the rules'})
        self.assertEqual(response.status_code, 200)
        item.exec_run = lambda args: helpers.ExecResult(0, b'No player was found')
        self.assertEqual(self.mutate('/api/players/action', {'action': 'kick', 'player': '.Yoshisan88'}).status_code, 422)

    def test_spawn_uses_runtime_legacy_world_name_for_multiverse_location_parser(self):
        self.login()
        self.bridge()
        response = self.mutate('/api/worlds/minecraft:overworld', {'action': 'spawn',
            'value': dict(x=-480, y=113, z=-352, yaw=180, pitch=0)}, method='put')
        self.assertEqual(response.status_code, 200)
        self.assertEqual(self.containers.items['minecraft-ark'].commands[-1][-1], 'mv setspawn ark:-480,113,-352,180,0')

    def test_player_action_invalid_type_fails_before_native_mutation(self):
        self.login()
        response = self.mutate('/api/players/action', {'action': {}, 'player': '.Yoshisan88'})
        self.assertEqual(response.status_code, 400)
        self.assertEqual(self.containers.items['minecraft-ark'].commands, [])

    def test_unsafe_spawn_rejection_is_not_reported_as_success(self):
        self.login()
        item = self.containers.items['minecraft-ark']
        previous = item.exec_run
        def run(args):
            if args[-1].startswith('mv setspawn '):
                return helpers.ExecResult(0, b'The new spawn location is unsafe! Use the --unsafe flag.')
            return previous(args)
        item.exec_run = run
        self.bridge()
        response = self.mutate('/api/worlds/minecraft:overworld', {'action': 'spawn',
            'value': dict(x=0, y=2048, z=0, yaw=0, pitch=0)}, method='put')
        self.assertEqual(response.status_code, 422)
        self.assertIn('Minecraft hat die Änderung abgelehnt', response.json['error'])

    def test_map_redirect_validation_is_case_insensitive(self):
        self.login()
        remote = helpers.SimpleNamespace(status_code=302, headers={'location': 'https://outside.invalid/'}, close=lambda: None,
            iter_content=lambda chunk_size: iter([b'']))
        with patch('panel.app.requests.Session') as factory:
            factory.return_value.get.return_value = remote
            self.assertEqual(self.client.get('/map/').status_code, 502)


if __name__ == '__main__':
    unittest.main()
