"""The shipped eight-egg assets must resolve identically on both editions."""
import base64
import json
from pathlib import Path
import struct
import unittest
from build_eggs import assets as generated_assets

ROOT = Path(__file__).resolve().parents[1]
SPECIES = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus', 'mosasaurus')


class EggAssetsTest(unittest.TestCase):
    def test_each_species_has_a_unique_texture_and_resolving_java_and_bedrock_paths(self):
        source = ROOT / 'dino-items/egg-assets.json'
        self.assertTrue(source.is_file(), 'Eight species egg assets have not been generated')
        assets = json.loads(source.read_text())
        java = {name: base64.b64decode(value, validate=True) for name, value in assets['java'].items()}
        bedrock = {name: base64.b64decode(value, validate=True) for name, value in assets['bedrock'].items()}
        self.assertEqual(generated_assets(), {'java': java, 'bedrock': bedrock, 'geyser': assets['geyser']}, 'Regenerate assets after changing egg art')
        mappings = assets['geyser']['items']['minecraft:sniffer_egg']
        self.assertEqual(assets['geyser']['format_version'], 2)
        self.assertEqual(len(java), 24)
        self.assertEqual(len(mappings), 8)
        self.assertEqual(len(set(value for name, value in java.items() if name.endswith('.png'))), 8)
        atlas = json.loads(bedrock['textures/item_texture.json'])['texture_data']
        for species in SPECIES:
            name = 'egg_' + species
            key = 'arkdinos:' + name
            definition = next(item for item in mappings if item['model'] == key)
            self.assertEqual(definition['type'], 'definition')
            self.assertEqual(definition['bedrock_identifier'], key)
            icon = definition['bedrock_options']['icon']
            texture = java['assets/arkdinos/textures/item/' + name + '.png']
            self.assertEqual(texture[:8], b'\x89PNG\r\n\x1a\n')
            self.assertEqual(struct.unpack('>II', texture[16:24]), (16, 16))
            self.assertEqual(bedrock[atlas[icon]['textures'] + '.png'], texture)
            item = json.loads(java['assets/arkdinos/items/' + name + '.json'])
            self.assertEqual(item['model'], {'type': 'minecraft:model', 'model': 'arkdinos:item/' + name})
            model = json.loads(java['assets/arkdinos/models/item/' + name + '.json'])
            self.assertEqual(model['textures']['layer0'], 'arkdinos:item/' + name)
        properties = dict(line.split('=', 1) for line in (ROOT / 'dino-plugin/src/main/resources/egg-assets.properties').read_text().splitlines())
        self.assertEqual({name: base64.b64decode(value, validate=True) for name, value in properties.items()}, java)


if __name__ == '__main__':
    unittest.main()
