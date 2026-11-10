"""Species shells must hide the native mesh without changing native egg blocks."""
import base64
import hashlib
import json
from pathlib import Path
import struct
import unittest
import zlib

ROOT = Path(__file__).resolve().parent
SPECIES = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus')


class PlacedEggsTest(unittest.TestCase):
    def test_seven_opaque_shells_cover_the_native_mesh_in_both_editions(self):
        textures = set()
        for species in SPECIES:
            name = 'ark_egg_' + species
            folder = ROOT / 'models' / name
            self.assertTrue((folder / (name + '.bbmodel')).is_file(), name + ' has no placed model')
            model = json.loads((folder / (name + '.bbmodel')).read_text())
            self.assertEqual([group['name'] for group in model['groups']], ['shell'])
            self.assertEqual(len(model['elements']), 1)
            cube = model['elements'][0]
            # Mojang 26.3 client e877b6a07acd633fb3bb475002175cec036e7b87,
            # assets/minecraft/models/block/sniffer_egg.json, relative to block center.
            for minimum, native, maximum in zip(cube['from'], (-7, 0, -6), cube['to']):
                self.assertLess(minimum, native)
            for minimum, native, maximum in zip(cube['from'], (7, 16, 6), cube['to']):
                self.assertGreater(maximum, native)
            texture = base64.b64decode(model['textures'][0]['source'].split(',')[1])
            textures.add(hashlib.sha256(texture).digest())
            width, height = struct.unpack('>II', texture[16:24])
            at, compressed = 8, b''
            while at < len(texture):
                length = struct.unpack('>I', texture[at:at + 4])[0]
                if texture[at + 4:at + 8] == b'IDAT':
                    compressed += texture[at + 8:at + 8 + length]
                at += length + 12
            pixels = zlib.decompress(compressed)
            self.assertEqual(len(pixels), height * (1 + width * 4))
            for y in range(height):
                self.assertEqual(pixels[y * (1 + width * 4)], 0)
                self.assertTrue(all(pixels[y * (1 + width * 4) + 4 + x * 4] == 255 for x in range(width)))
            geometry = json.loads((folder / (name + '.geo.json')).read_text())['minecraft:geometry'][0]
            self.assertEqual(geometry['bones'][0]['name'], 'shell')
            bedrock = geometry['bones'][0]['cubes'][0]
            self.assertEqual(bedrock['origin'], [-cube['to'][0], cube['from'][1], cube['from'][2]])
            self.assertEqual(bedrock['size'], [b - a for a, b in zip(cube['from'], cube['to'])])
        self.assertEqual(len(textures), len(SPECIES), 'Placed eggs need seven distinct species textures')


if __name__ == '__main__':
    unittest.main()
