import json
import unittest
from unittest.mock import patch

from panel import test_app as helpers


class LiveSettingsTests(unittest.TestCase):
    password_hash = helpers.PanelTests.password_hash
    setUp = helpers.PanelTests.setUp
    login = helpers.PanelTests.login
    mutate = helpers.PanelTests.mutate

    def native(self, components):
        return patch('panel.app.rcon_command', return_value='ARKPANEL ' + json.dumps(components))

    def test_minecraft_keeps_file_values_separate_from_real_runtime_and_origins(self):
        self.login()
        self.properties.write_bytes(self.original + b'difficulty=easy\nallow-flight=false\nplayer-idle-timeout=0\n')
        with self.native({'minecraft': {'available': True, 'observed_at': '2026-11-04T11:00:00Z',
                'settings': {'difficulty': 'normal', 'allow-flight': False, 'player-idle-timeout': 0, 'motd': '',
                             'rcon.password': 'must-not-leak', 'view-distance': 'wrong type'},
                'scopes': {'difficulty': 'primary_world'}}}):
            response = self.client.get('/api/settings/minecraft')
        self.assertEqual(response.status_code, 200)
        data = response.json
        self.assertEqual(data['settings']['difficulty'], 'easy')
        self.assertEqual(data['runtime']['settings']['difficulty'], 'normal')
        self.assertIs(data['runtime']['settings']['allow-flight'], False)
        self.assertEqual(data['runtime']['settings']['player-idle-timeout'], 0)
        self.assertEqual(data['runtime']['settings']['motd'], '')
        self.assertNotIn('view-distance', data['runtime']['settings'])
        self.assertEqual(data['runtime']['scopes'], {'difficulty': 'primary_world'})
        self.assertEqual(data['sources']['difficulty'], 'file')
        self.assertEqual(data['sources']['view-distance'], 'default')
        self.assertIsInstance(data['observed_at'], str)
        self.assertNotIn('must-not-leak', response.get_data(as_text=True))

    def test_unavailable_native_snapshot_still_allows_stopped_file_configuration(self):
        self.login()
        self.containers.items['minecraft-ark'].status = 'exited'
        response = self.client.get('/api/settings/minecraft')
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json['runtime']['available'])
        self.assertEqual(response.json['runtime']['settings'], {})
        self.assertEqual(response.json['settings']['motd'], 'Old world')
        self.assertNotIn('do-not-return-or-change', response.get_data(as_text=True))

    def test_missing_container_and_malformed_runtime_do_not_replace_saved_values(self):
        self.login()
        del self.containers.items['minecraft-ark']
        data = self.client.get('/api/settings/minecraft').json
        self.assertFalse(data['runtime']['available'])
        self.assertEqual(data['settings']['max-players'], 20)
        with self.native({'minecraft': {'available': True, 'settings': {'rcon.password': 'secret'}}}):
            # Native transport is not reached without the Minecraft container.
            data = self.client.get('/api/settings/minecraft').json
        self.assertFalse(data['runtime']['available'])

    def test_plugin_origins_and_loaded_getters_do_not_call_file_defaults_live(self):
        self.login()
        with self.native({'geyser': {'available': True, 'observed_at': '2026-11-04T11:00:00Z',
                'settings': {'gameplay.force-resource-packs': True, 'debug-mode': False,
                             'gameplay.server-name': 'Running title'}, 'scopes': {}}}):
            data = self.client.get('/api/settings/geyser').json
        self.assertFalse(data['settings']['gameplay.force-resource-packs'])
        self.assertTrue(data['runtime']['settings']['gameplay.force-resource-packs'])
        self.assertEqual(data['sources']['gameplay.force-resource-packs'], 'default')
        self.assertNotIn('motd.max-players', data['runtime']['settings'])

    def test_old_structured_versions_conflict_without_write_or_backup(self):
        self.login()
        loaded = self.client.get('/api/settings/minecraft').json
        changed = self.properties.read_bytes() + b'view-distance=7\n'
        self.properties.write_bytes(changed)
        response = self.mutate('/api/settings/minecraft', {'settings': {'motd': 'Old tab'},
            'version': loaded['version']}, method='put')
        self.assertEqual(response.status_code, 409)
        self.assertEqual(self.properties.read_bytes(), changed)
        self.assertFalse(list(self.state.glob('backups/**/*')))

    def test_live_world_rules_and_config_sections_have_explanations_and_read_time(self):
        self.login()
        with self.native({'worlds': [{'name': 'ark', 'key': 'minecraft:overworld', 'difficulty': 'normal',
                'rules': [{'name': 'minecraft:keep_inventory', 'type': 'boolean', 'value': False}]}]}):
            data = self.client.get('/api/worlds').json
        self.assertIsInstance(data['observed_at'], str)
        self.assertIs(data['worlds'][0]['rules'][0]['value'], False)
        self.assertTrue(data['worlds'][0]['rules'][0]['description'])
        self.assertNotEqual(data['worlds'][0]['rules'][0]['label'], 'minecraft:keep_inventory')
        self.assertTrue(all(entry['description'] for entry in self.client.get('/api/configs').json['configs']))


if __name__ == '__main__':
    unittest.main()
