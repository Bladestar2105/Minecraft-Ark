"""The shipped eight-egg assets must resolve identically on both editions."""
import base64
import json
from pathlib import Path
import struct
import unittest
from build_eggs import assets as generated_assets

ROOT = Path(__file__).resolve().parents[1]
SPECIES = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus', 'mosasaurus', 'gallimimus')


class EggAssetsTest(unittest.TestCase):
    def test_bedrock_clients_refresh_the_species_art_under_the_existing_pack_uuid(self):
        manifest = json.loads(generated_assets()['bedrock']['manifest.json'])
        header, module = manifest['header'], manifest['modules'][0]
        self.assertEqual(header['uuid'], '4b6d5dd8-9250-48cf-bc87-48251cfaf744')
        self.assertEqual(module['uuid'], '708d04a5-5dc7-490e-bb47-0a282214ddc2')
        self.assertGreater(header['version'], [1, 1, 0])
        self.assertEqual(header['version'], module['version'])

    def test_normal_creative_aliases_use_species_art_and_names_on_both_editions(self):
        aliases = ('bat', 'endermite', 'silverfish', 'vex', 'phantom', 'strider', 'tadpole', 'trader_llama', 'armadillo')
        result = generated_assets()
        for alias, species in zip(aliases, SPECIES):
            path = f'assets/minecraft/items/{alias}_spawn_egg.json'
            self.assertIn(path, result['java'], 'The ordinary Creative egg must show its dinosaur icon')
            item = json.loads(result['java'][path])
            self.assertEqual(item['model'], {'type': 'minecraft:model', 'model': f'arkdinos:item/egg_{species}'})
            for language in ('en_us', 'de_de'):
                names = json.loads(result['java'][f'assets/minecraft/lang/{language}.json'])
                self.assertIn(species.replace('trex', 'T-Rex').lower(), names[f'item.minecraft.{alias}_spawn_egg'].lower())
            self.assertEqual(result['bedrock'][f'textures/items/egg_{alias}.png'], result['bedrock'][f'textures/items/arkdinos/egg_{species}.png'])
            self.assertEqual(result['bedrock'][f'textures/items/spawn_eggs/spawn_egg_{alias}.png'], result['bedrock'][f'textures/items/arkdinos/egg_{species}.png'])
            for language in ('en_US', 'de_DE'):
                self.assertIn(f'item.spawn_egg.entity.{alias}.name=', result['bedrock'][f'texts/{language}.lang'].decode())

    def test_each_species_has_a_unique_texture_and_resolving_java_and_bedrock_paths(self):
        source = ROOT / 'dino-items/egg-assets.json'
        self.assertTrue(source.is_file(), 'Eight species egg assets have not been generated')
        assets = json.loads(source.read_text())
        java = {name: base64.b64decode(value, validate=True) for name, value in assets['java'].items()}
        bedrock = {name: base64.b64decode(value, validate=True) for name, value in assets['bedrock'].items()}
        self.assertEqual(generated_assets(), {'java': java, 'bedrock': bedrock, 'geyser': assets['geyser']}, 'Regenerate assets after changing egg art')
        mappings = assets['geyser']['items']['minecraft:sniffer_egg']
        self.assertEqual(assets['geyser']['format_version'], 2)
        self.assertEqual(len(java), 38)
        self.assertEqual(len(mappings), 9)
        self.assertEqual(len(set(value for name, value in java.items() if name.endswith('.png'))), 9)
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
