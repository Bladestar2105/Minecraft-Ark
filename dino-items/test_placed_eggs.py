"""Species shells must hide the native mesh without changing native egg blocks."""
import base64
import json
import itertools
import math
from pathlib import Path
import struct
import unittest
import zlib

from build_eggs import egg_pixels

ROOT = Path(__file__).resolve().parent
SPECIES = ('trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus')


def models(species):
    name = 'ark_egg_' + species
    folder = ROOT / 'models' / name
    java = json.loads((folder / (name + '.bbmodel')).read_text())
    bedrock = json.loads((folder / (name + '.geo.json')).read_text())['minecraft:geometry'][0]
    return java, bedrock


def texture_rows(model):
    texture = base64.b64decode(model['textures'][0]['source'].split(',')[1], validate=True)
    width, height = struct.unpack('>II', texture[16:24])
    at, compressed = 8, b''
    while at < len(texture):
        length = struct.unpack('>I', texture[at:at + 4])[0]
        if texture[at + 4:at + 8] == b'IDAT':
            compressed += texture[at + 8:at + 8 + length]
        at += length + 12
    raw = zlib.decompress(compressed)
    stride = 1 + width * 4
    assert len(raw) == height * stride and all(raw[y * stride] == 0 for y in range(height))
    return [[tuple(raw[i:i + 4]) for i in range(y * stride + 1, (y + 1) * stride, 4)] for y in range(height)]


def edges(face):
    return {'top': face[0], 'bottom': face[-1],
            'left': [row[0] for row in face], 'right': [row[-1] for row in face]}


def world_vertices(cube):
    pitch, yaw, roll = map(math.radians, cube['rotation'])
    vertices = {}
    for key in itertools.product((0, 1), repeat=3):
        local = [cube[('from', 'to')[bit]][axis] for axis, bit in enumerate(key)]
        x, y, z = (a - b for a, b in zip(local, cube['origin']))
        y, z = y*math.cos(pitch)-z*math.sin(pitch), y*math.sin(pitch)+z*math.cos(pitch)
        x, z = x*math.cos(yaw)+z*math.sin(yaw), -x*math.sin(yaw)+z*math.cos(yaw)
        x, y = x*math.cos(roll)-y*math.sin(roll), x*math.sin(roll)+y*math.cos(roll)
        vertices[key] = tuple(a + b for a, b in zip((x, y, z), cube['origin']))
    return vertices


class PlacedEggsTest(unittest.TestCase):
    def test_shell_patterns_wrap_the_surface_without_repeating_inventory_icons(self):
        for species in SPECIES:
            model, _ = models(species)
            pixels = texture_rows(model)
            icon = egg_pixels(species)
            visible = [(x, y) for y in range(16) for x in range(16) if icon[y][x][3]]
            for cube in model['elements']:
                faces = {}
                for side, face in cube['faces'].items():
                    u, v, u2, v2 = face['uv']
                    faces[side] = [row[u:u2] for row in pixels[v:v2]]
                borders = {side: edges(face) for side, face in faces.items()}
                # Independent face orientation at all twelve shared edges.
                for a, ae, b, be, reverse in (
                    ('north', 'top', 'up', 'top', True), ('north', 'bottom', 'down', 'bottom', True),
                    ('north', 'left', 'east', 'right', False), ('north', 'right', 'west', 'left', False),
                    ('south', 'top', 'up', 'bottom', False), ('south', 'bottom', 'down', 'top', False),
                    ('south', 'left', 'west', 'right', False), ('south', 'right', 'east', 'left', False),
                    ('east', 'top', 'up', 'right', True), ('east', 'bottom', 'down', 'right', False),
                    ('west', 'top', 'up', 'left', False), ('west', 'bottom', 'down', 'left', True),
                ):
                    other = borders[b][be]
                    self.assertEqual(borders[a][ae], other[::-1] if reverse else other, (species, cube['name'], a, ae, b, be))
                if cube['name'] == 'species_shell':
                    self.assertEqual(len({tuple(p for row in face for p in row) for face in faces.values()}), 6)
                    self.assertGreater(len({p for face in borders.values() for p in face['top']}), 4)
                    for side, face in faces.items():
                        copied = sum(face[y * len(face) // 16][x * len(face[0]) // 16] == icon[y][x] for x, y in visible)
                        self.assertLess(copied / len(visible), .6, (species, side, 'inventory silhouette stamped on shell'))

    def test_middle_side_facets_export_as_exact_quarter_turns(self):
        # BetterModel 3.6 folds a small pitch at ±90° yaw into the Java item's yaw.
        for species in SPECIES:
            model, _ = models(species)
            flanks = [cube for cube in model['elements'] if cube['name'] in ('shell_3_2', 'shell_3_6')]
            self.assertEqual({tuple(cube['rotation']) for cube in flanks}, {(0, -90, 0), (0, 90, 0)}, species)

    def test_native_coverage_and_tapered_shell_fit_an_adjacent_nest(self):
        profiles = set()
        for species in SPECIES:
            model, _ = models(species)
            elements = model['elements']
            # Mojang 26.3 client e877b6a07acd633fb3bb475002175cec036e7b87, sniffer_egg.json.
            core = next(c for c in elements if c['name'] == 'species_shell')
            self.assertEqual(core['from'], [-7.02, -.02, -6.02])
            self.assertEqual(core['to'], [7.02, 16.02, 6.02])
            vertices = [world_vertices(c) for c in elements]
            points = [point for cube in vertices for point in cube.values()]
            height = max(point[1] for point in points)
            self.assertGreater(height, 17, species + ' has no modeled crown')
            self.assertLess(height, 22, species + ' crown is distant from the clickable native block')
            self.assertTrue(all(abs(point[i]) <= 8.0001 for i in (0, 2) for point in points), species)

            def section(y):
                intersections = []
                for cube in vertices:
                    for key, a in cube.items():
                        for axis in range(3):
                            if key[axis]:
                                continue
                            other = list(key)
                            other[axis] = 1
                            b = cube[tuple(other)]
                            if min(a[1], b[1]) <= y <= max(a[1], b[1]) and a[1] != b[1]:
                                t = (y - a[1]) / (b[1] - a[1])
                                intersections.append([a[i] + t*(b[i]-a[i]) for i in (0, 2)])
                return tuple(max(p[i] for p in intersections) - min(p[i] for p in intersections) for i in (0, 1))

            base, belly, shoulder, cap = (section(y) for y in (.1, 8, 16.1, height - .05))
            for bottom, middle, upper, tip in zip(base, belly, shoulder, cap):
                self.assertLess(bottom, middle, species)
                self.assertLess(upper, middle, species)
                self.assertLess(tip, upper * .65, species)
            profiles.add((height, belly, cap))
        self.assertEqual(len(profiles), len(SPECIES))

    def test_bedrock_has_every_visible_part_and_uv_of_the_opaque_java_shell(self):
        textures = set()
        for species in SPECIES:
            model, geometry = models(species)
            self.assertEqual([group['name'] for group in model['groups']], ['shell'])
            self.assertEqual([bone['name'] for bone in geometry['bones']], ['shell'])
            self.assertEqual(model['outliner'][0]['children'], [cube['uuid'] for cube in model['elements']])
            texture = model['textures'][0]
            textures.add(texture['source'])
            rows = texture_rows(model)
            width, height = len(rows[0]), len(rows)
            self.assertEqual(model['resolution'], {'width': width, 'height': height})
            self.assertEqual((geometry['description']['texture_width'], geometry['description']['texture_height']), (width, height))
            self.assertTrue(all(pixel[3] == 255 for row in rows for pixel in row), species)
            bedrock = geometry['bones'][0]['cubes']
            self.assertEqual(len(model['elements']), len(bedrock))
            for cube, exported in zip(model['elements'], bedrock):
                self.assertEqual(exported['origin'], [-cube['to'][0], cube['from'][1], cube['from'][2]])
                self.assertEqual(exported['size'], [b - a for a, b in zip(cube['from'], cube['to'])])
                if any(cube['rotation']):
                    pitch, yaw, roll = cube['rotation']
                    self.assertEqual(exported['rotation'], [-pitch, -yaw, roll])
                    self.assertEqual(exported['pivot'], [-cube['origin'][0], *cube['origin'][1:]])
                for side, face in cube['faces'].items():
                    u, v, u2, v2 = face['uv']
                    self.assertTrue(0 <= u < u2 <= width and 0 <= v < v2 <= height)
                    self.assertEqual(exported['uv'][side],
                                     {'uv': [u2, v2], 'uv_size': [u - u2, v - v2]} if side in ('up', 'down')
                                     else {'uv': [u, v], 'uv_size': [u2 - u, v2 - v]})
        self.assertEqual(len(textures), len(SPECIES), 'Placed eggs need seven distinct species textures')


if __name__ == '__main__':
    unittest.main()
