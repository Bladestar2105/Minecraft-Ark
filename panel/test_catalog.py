import unittest

from panel.catalog import EXTRA_CONFIGS, SERVER_FIELDS, field_for, protected_property, validate_field


class CatalogTests(unittest.TestCase):
    def test_native_fields_and_fixed_unique_config_paths(self):
        self.assertIn('motd', SERVER_FIELDS)
        for key in ['pvp', 'allow-nether', 'spawn-monsters', 'enable-command-block']:
            self.assertNotIn(key, SERVER_FIELDS)
        self.assertEqual(len(EXTRA_CONFIGS), len({entry['path'] for entry in EXTRA_CONFIGS.values()}))
        self.assertFalse({'geyser', 'quests', 'quest-settings', 'permissions'} & EXTRA_CONFIGS.keys())
        self.assertTrue(all(not entry['path'].startswith('/') and '..' not in entry['path'].split('/') for entry in EXTRA_CONFIGS.values()))
        self.assertTrue(EXTRA_CONFIGS['bluemap-core']['requires_stopped'])
        self.assertEqual(EXTRA_CONFIGS['bluemap-map-creative']['format'], 'json')

    def test_protected_properties_include_future_credentials_and_native_legacy_keys(self):
        for key in ['rcon.password', 'RCON.PORT', 'management-server-tls-keystore', 'management-server-secret',
                    'future-api-token', 'service_password', 'new-secret', 'online-mode', 'enforce-secure-profile',
                    'level-name', 'level-seed', 'server-ip', 'server-port', 'enable-rcon', 'enable-query', 'query.port',
                    'text-filtering-config', 'pvp', 'allow-nether', 'spawn-monsters', 'enable-command-block']:
            self.assertTrue(protected_property(key), key)
            self.assertIsNone(field_for(key, 'hidden'), key)
        self.assertFalse(protected_property('allow-flight'))

    def test_unknown_actual_fields_use_types_and_do_not_mutate_shared_metadata(self):
        self.assertEqual(field_for('future-bool', 'false')['type'], 'boolean')
        self.assertEqual(field_for('future-int', '-12')['default'], -12)
        self.assertEqual(field_for('future-text', 'value')['group'], 'Weitere Einstellungen')
        field = field_for('motd', 'Actual'); field['label'] = 'Changed'
        self.assertNotEqual(SERVER_FIELDS['motd']['label'], 'Changed')

    def test_exact_boolean_and_integer_types(self):
        validate_field(SERVER_FIELDS['allow-flight'], True)
        validate_field(SERVER_FIELDS['max-players'], 10)
        for key, value in [('allow-flight', 1), ('allow-flight', 'true'), ('max-players', True), ('max-players', 2.0)]:
            with self.assertRaises(ValueError): validate_field(SERVER_FIELDS[key], value)

    def test_integer_boundaries_and_choice_values(self):
        field = SERVER_FIELDS['view-distance']
        validate_field(field, field['min']); validate_field(field, field['max'])
        for value in [field['min'] - 1, field['max'] + 1]:
            with self.assertRaises(ValueError): validate_field(field, value)
        validate_field(SERVER_FIELDS['gamemode'], 'creative')
        with self.assertRaises(ValueError): validate_field(SERVER_FIELDS['gamemode'], 'CREATIVE')

    def test_utf8_controls_and_string_length(self):
        validate_field(SERVER_FIELDS['motd'], 'ARK – Willkommen 🦕')
        for value in ['', 'x' * 161, 'hello\nworld', 'hello\rworld', 'hello\0world', '\ud800']:
            with self.assertRaises(ValueError): validate_field(SERVER_FIELDS['motd'], value)
        field = field_for('future-text', 'value')
        validate_field(field, 'x' * 4096)
        with self.assertRaises(ValueError): validate_field(field, 'x' * 4097)

    def test_resource_pack_url_sha1_and_uuid_validation(self):
        for value in ['', 'https://example.com/pack.zip', 'http://192.168.1.1/pack.zip']:
            validate_field(SERVER_FIELDS['resource-pack'], value)
        for value in ['file:///etc/passwd', 'javascript:alert(1)', 'https://', 'https://example.com/a b', 'https://example.com:invalid/pack.zip']:
            with self.assertRaises(ValueError): validate_field(SERVER_FIELDS['resource-pack'], value)
        for value in ['', 'a' * 40]: validate_field(SERVER_FIELDS['resource-pack-sha1'], value)
        for value in ['a' * 39, 'z' * 40]:
            with self.assertRaises(ValueError): validate_field(SERVER_FIELDS['resource-pack-sha1'], value)
        for value in ['', 'f815248c-4558-3fc9-aa11-17be911bf4d0']:
            validate_field(SERVER_FIELDS['resource-pack-id'], value)
        with self.assertRaises(ValueError): validate_field(SERVER_FIELDS['resource-pack-id'], 'not-a-uuid')


if __name__ == '__main__':
    unittest.main()
