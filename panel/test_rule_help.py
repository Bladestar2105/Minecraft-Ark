import unittest

from panel.catalog import EXTRA_CONFIGS, EXTRA_SERVER_HELP, RULE_HELP, SERVER_FIELDS, config_description, field_for, rule_help


NATIVE_RULES = '''advance_time advance_weather allow_entering_nether_using_portals block_drops
block_explosion_drop_decay command_block_output command_blocks_work drowning_damage elytra_movement_check
ender_pearls_vanish_on_death entity_drops fall_damage fire_damage fire_spread_radius_around_player
forgive_dead_players freeze_damage global_sound_events immediate_respawn keep_inventory lava_source_conversion
limited_crafting locator_bar log_admin_commands max_block_modifications max_command_forks
max_command_sequence_length max_entity_cramming max_snow_accumulation_height mob_drops
mob_explosion_drop_decay mob_griefing natural_health_regeneration player_movement_check
players_nether_portal_creative_delay players_nether_portal_default_delay players_sleeping_percentage
projectiles_can_break_blocks pvp raids random_tick_speed reduced_debug_info respawn_radius send_command_feedback
show_advancement_messages show_death_messages spawn_mobs spawn_monsters spawn_patrols spawn_phantoms
spawn_wandering_traders spawn_wardens spawner_blocks_work spectators_generate_chunks spread_vines tnt_explodes
tnt_explosion_drop_decay universal_anger water_source_conversion'''.split()


class RuleHelpTests(unittest.TestCase):
    def test_all_native_rules_have_short_human_labels_and_effects(self):
        expected = {'minecraft:' + name for name in NATIVE_RULES}
        self.assertEqual(len(expected), 58)
        self.assertEqual(set(RULE_HELP), expected)
        for name in expected:
            with self.subTest(name=name):
                metadata = rule_help(name)
                self.assertEqual(set(metadata), {'label', 'description'})
                self.assertNotEqual(metadata['label'], name)
                self.assertNotIn('minecraft:', metadata['label'])
                self.assertTrue(3 <= len(metadata['label']) <= 90)
                self.assertTrue(25 <= len(metadata['description']) <= 240)

    def test_inventory_help_explains_both_switch_positions(self):
        metadata = rule_help('minecraft:keep_inventory')
        self.assertIn('behalten', metadata['label'])
        self.assertIn('aus', metadata['description'].lower())
        self.assertIn('fallen', metadata['description'].lower())
        self.assertIn('Tod', metadata['description'])

    def test_numeric_help_explains_known_zero_and_disable_values(self):
        for name in ['minecraft:max_entity_cramming', 'minecraft:random_tick_speed',
                     'minecraft:max_snow_accumulation_height']:
            self.assertIn('0', rule_help(name)['description'])
        fire = rule_help('minecraft:fire_spread_radius_around_player')['description']
        self.assertIn('0', fire)
        self.assertIn('-1', fire)
        self.assertIn('ohne Spieler', fire)
        self.assertIn('0', SERVER_FIELDS['player-idle-timeout']['description'])
        self.assertIn('-1', SERVER_FIELDS['pause-when-empty-seconds']['description'])
        self.assertIn('-1', SERVER_FIELDS['network-compression-threshold']['description'])

    def test_positive_renamed_rules_do_not_invert_their_effect(self):
        self.assertIn('erlauben', rule_help('minecraft:raids')['label'])
        self.assertIn('prüfen', rule_help('minecraft:elytra_movement_check')['label'])
        self.assertIn('prüfen', rule_help('minecraft:player_movement_check')['label'])

    def test_unknown_rules_keep_their_identifier_without_guessing_meaning(self):
        name = 'example:future_rule'
        metadata = rule_help(name)
        self.assertEqual(metadata['label'], name)
        self.assertIn('nicht bekannt', metadata['description'])
        self.assertNotIn(name, RULE_HELP)

    def test_rule_metadata_returned_to_a_caller_is_not_shared(self):
        first = rule_help('minecraft:keep_inventory')
        first['description'] = 'changed'
        self.assertNotEqual(rule_help('minecraft:keep_inventory')['description'], 'changed')

    def test_all_exposed_config_sections_have_a_specific_purpose(self):
        descriptions = [config_description(name) for name in [*EXTRA_CONFIGS, 'geyser', 'quests', 'quest-settings', 'permissions']]
        self.assertEqual(len(descriptions), 41)
        self.assertEqual(len(set(descriptions)), len(descriptions))
        self.assertTrue(all(25 <= len(text) <= 240 for text in descriptions))
        self.assertIn('Dokumentation', config_description('future-config'))


    def test_existing_extra_properties_have_specific_help_without_new_defaults(self):
        names = set("""accepts-transfers broadcast-rcon-to-ops bug-report-link chat-spam-threshold-seconds
command-spam-threshold-seconds debug enable-code-of-conduct entity-broadcast-range-percentage
function-permission-level generate-structures generator-settings hardcore initial-disabled-packs
initial-enabled-packs level-type max-chained-neighbor-updates op-permission-level region-file-compression
status-heartbeat-interval sync-chunk-writes text-filtering-version use-native-transport""".split())
        self.assertEqual(set(EXTRA_SERVER_HELP), names)
        self.assertEqual(len(SERVER_FIELDS), 27)
        self.assertFalse(names & SERVER_FIELDS.keys())
        for name in names:
            for raw, kind, default in [('false', 'boolean', False), ('-12', 'integer', -12), ('custom', 'string', 'custom')]:
                with self.subTest(name=name, raw=raw):
                    field = field_for(name, raw)
                    self.assertEqual((field['type'], field['default']), (kind, default))
                    self.assertNotEqual(field['label'], name)
                    self.assertNotIn('Zusätzliche Eigenschaft', field['description'])
                    self.assertTrue(25 <= len(field['description']) <= 240)
                    self.assertEqual(field['group'], 'Weitere Einstellungen')
                    if kind == 'string':
                        self.assertEqual(field['max_length'], 4096)

    def test_new_world_properties_do_not_promise_changes_to_the_existing_world(self):
        for name in ['generate-structures', 'generator-settings', 'hardcore', 'initial-disabled-packs',
                     'initial-enabled-packs', 'level-type']:
            self.assertIn('neu', field_for(name, 'value')['description'].lower(), name)

    def test_spam_and_management_help_use_verified_units_and_disable_values(self):
        for name in ['chat-spam-threshold-seconds', 'command-spam-threshold-seconds']:
            help_text = field_for(name, 10)['description']
            self.assertIn('Zähler', help_text)
            self.assertIn('Sekunde', help_text)
            self.assertIn('0', help_text)
        heartbeat = field_for('status-heartbeat-interval', 0)['description']
        self.assertIn('Management', heartbeat)
        self.assertIn('Sekunden', heartbeat)
        self.assertIn('0', heartbeat)


if __name__ == '__main__':
    unittest.main()
