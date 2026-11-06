import json
import unittest

import yaml
from config_content import PROTECTED, redact_content, restore_content, validate_json


class ConfigContentTests(unittest.TestCase):
    def test_yaml_redacts_secret_leaves_and_preserves_normal_comments(self):
        original = '# keep this header\nnormal: old # keep inline\nauth:\n  password: "sensitive value" # note\n  API-Key: example-api\n'
        hidden = redact_content('yaml', 'quests', original)
        self.assertNotIn('sensitive value', hidden)
        self.assertNotIn('example-api', hidden)
        self.assertIn('# keep this header\nnormal: old # keep inline', hidden)
        saved = restore_content('yaml', 'quests', original, hidden.replace('normal: old', 'normal: new'))
        self.assertEqual(yaml.safe_load(saved)['auth'], yaml.safe_load(original)['auth'])
        self.assertIn('normal: new # keep inline', saved)

    def test_empty_yaml_secret_keeps_mapping_separator_and_roundtrips_null(self):
        for original in ['password:\nnormal: old\n', 'password: # keep comment\nnormal: old\n']:
            with self.subTest(original=original):
                hidden = redact_content('yaml', 'other', original)
                self.assertEqual(yaml.safe_load(hidden)['password'], PROTECTED)
                self.assertIn('password: "' + PROTECTED + '"', hidden)
                if '# keep comment' in original:
                    self.assertIn('# keep comment', hidden)
                restored = restore_content('yaml', 'other', original, hidden.replace('normal: old', 'normal: new'))
                self.assertIsNone(yaml.safe_load(restored)['password'])
                self.assertIn('normal: new', restored)
                if '# keep comment' in original:
                    self.assertIn('# keep comment', restored)

    def test_yaml_multiline_formatted_and_nested_list_secrets(self):
        original = 'normal: yes\nservices:\n  - token: |\n      line one\n      line two\n  - options: {private-key: "quoted\\nvalue"}\nsecret-bag: {first: one, second: [two, three]}\n'
        hidden = redact_content('yaml', 'other', original)
        for value in ['line one', 'line two', 'quoted', 'first: one']:
            self.assertNotIn(value, hidden)
        self.assertEqual(yaml.safe_load(restore_content('yaml', 'other', original, hidden)), yaml.safe_load(original))

    def test_yaml_without_secrets_stays_byte_for_byte(self):
        content = '# comment\nnormal: [1, 2] # inline\n'
        self.assertEqual(redact_content('yaml', 'quests', content), content)
        self.assertEqual(restore_content('yaml', 'quests', content, content), content)

    def test_secret_deletion_addition_or_change_fails_closed(self):
        original = 'password: original\nnormal: value\n'
        for submitted in ['normal: value\n', 'password: changed\nnormal: value\n', 'password: original\nnew-token: anything\n']:
            with self.subTest(submitted=submitted), self.assertRaises(ValueError):
                restore_content('yaml', 'other', original, submitted)
        self.assertEqual(restore_content('yaml', 'other', original, 'password: "original"\nnormal: changed\n'), 'password: "original"\nnormal: changed\n')

    def test_identity_fields_remain_visible_but_typed_changes_fail(self):
        original = 'map-id: 9\nnormal: old\n'
        self.assertEqual(redact_content('yaml', 'ark-atlas', original), original)
        for changed in ['map-id: 10\n', 'map-id: true\n', 'normal: changed\n']:
            with self.assertRaises(ValueError): restore_content('yaml', 'ark-atlas', original, changed)
        with self.assertRaises(ValueError): restore_content('yaml', 'ark-atlas', 'normal: old\n', 'normal: old\nmap-id: 9\n')

    def test_geyser_nested_identity_sequence_and_floodgate(self):
        original = 'bedrock:\n  port: 19132\n  signaling:\n    advertise-addresses: [one, two]\njava: {auth-type: floodgate}\nnormal: old\n'
        self.assertIn('advertise-addresses: [one, two]', redact_content('yaml', 'geyser', original))
        with self.assertRaises(ValueError): restore_content('yaml', 'geyser', original, original.replace('[one, two]', '[one, changed]'))
        self.assertIn('normal: new', restore_content('yaml', 'geyser', original, original.replace('normal: old', 'normal: new')))
        with self.assertRaises(ValueError): restore_content('yaml', 'floodgate', 'username-prefix: "."\n', 'username-prefix: ""\n')

    def test_json_masks_secrets_and_preserves_logical_values(self):
        original = json.dumps({'data': '/data', 'password': 'exact\nsecret', 'services': [{'token': 'private'}], 'visible': 1})
        hidden = redact_content('json', 'bluemap-core', original)
        self.assertNotIn('exact', hidden)
        self.assertNotIn('private', hidden)
        document = json.loads(hidden); document['visible'] = 2
        restored = json.loads(restore_content('json', 'bluemap-core', original, json.dumps(document)))
        self.assertEqual(restored['password'], 'exact\nsecret'); self.assertEqual(restored['services'], [{'token': 'private'}]); self.assertEqual(restored['visible'], 2)
        document['data'] = 'https://example.invalid/'
        with self.assertRaises(ValueError): restore_content('json', 'bluemap-core', original, json.dumps(document))

    def test_json_rejects_duplicates_hocon_nonfinite_surrogates_and_empty_roots(self):
        for text in ['{"a":1,"a":2}', '{a: 1}', '{"a": ${?URL}}', '{"a":NaN}', '{"a":Infinity}', '{"a":"\\ud800"}', '{}', '[]', 'null']:
            with self.subTest(text=text), self.assertRaises(ValueError): validate_json(text)
        self.assertEqual(validate_json('{"url":"https://example.invalid/", "emoji":"\\ud83d\\ude00"}')['emoji'], '😀')

    def test_empty_yaml_mapping_and_non_json_secret_types_fail_safely(self):
        self.assertEqual(redact_content('yaml', 'quest-actions', '{}\n'), '{}\n')
        self.assertEqual(restore_content('yaml', 'quest-conditions', '{}\n', '{}\n'), '{}\n')
        original = 'secret-bag: {1: first, 2: second}\nnormal: old\n'
        hidden = redact_content('yaml', 'other', original)
        self.assertNotIn('first', hidden)
        with self.assertRaises(ValueError): restore_content('yaml', 'other', original, hidden)

    def test_json_exact_container_depth_and_finite_number_boundaries(self):
        self.assertIsInstance(validate_json('{"x":' + '[' * 39 + '0' + ']' * 39 + '}'), dict)
        with self.assertRaises(ValueError): validate_json('{"x":' + '[' * 40 + '0' + ']' * 40 + '}')
        with self.assertRaises(ValueError): validate_json('{"x":1e999}')
        self.assertEqual(len(validate_json(json.dumps({'x': [0] * 19997}))['x']), 19997)
        with self.assertRaises(ValueError): validate_json(json.dumps({'x': [0] * 19998}))

    def test_json_bounds_and_identity_types(self):
        for text in [json.dumps({'x': 'a' * (256 * 1024)}), '{"x":' + '[' * 41 + '0' + ']' * 41 + '}', json.dumps({'x': [0] * 20000})]:
            with self.assertRaises(ValueError): validate_json(text)
        with self.assertRaises(ValueError): restore_content('json', 'bluemap-webserver', '{"ip":"127.0.0.1","port":8100}', '{"ip":"0.0.0.0","port":8100}')
        with self.assertRaises(ValueError): restore_content('json', 'bluemap-webserver', '{"port":1}', '{"port":true}')
        with self.assertRaises(ValueError): restore_content('json', 'bluemap-map-ark', '{"world":"ark","dimension":"minecraft:overworld"}', '{"world":"other","dimension":"minecraft:overworld"}')


if __name__ == '__main__':
    unittest.main()
