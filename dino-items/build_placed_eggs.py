"""Build seven opaque species shells around the unchanged native Sniffer Egg."""
import base64
import json
from pathlib import Path
import uuid

from build_eggs import EGGS, egg_pixels, png, rgb

ROOT = Path(__file__).resolve().parent
SIDES = ('north', 'south', 'east', 'west', 'up', 'down')


def identity(value):
    return str(uuid.uuid5(uuid.NAMESPACE_URL, 'ark-placed-eggs/' + value))


def build():
    for species, (label, shell, dark, light, *_) in EGGS.items():
        if species == 'mosasaurus':
            continue
        name = 'ark_egg_' + species
        folder = ROOT / 'models' / name
        folder.mkdir(parents=True, exist_ok=True)
        sprite = egg_pixels(species)
        pixels = [[(*rgb(shell), 255) for _ in range(64)] for _ in range(64)]
        for side in range(6):
            for y in range(16):
                for x in range(16):
                    original = sprite[y][x]
                    color = original if original[3] and original[:3] != rgb(dark) else (*rgb(shell), 255)
                    # Carry each sprite's interior markings over the entire opaque shell.
                    if 3 <= x <= 12 and 4 <= y <= 12 and original[3]:
                        color = original
                    pixels[(side // 4) * 16 + y][(side % 4) * 16 + x] = color
        texture = png(pixels)
        bone = identity(name + '/shell')
        faces = {side: {'uv': [(i % 4) * 16, (i // 4) * 16, (i % 4 + 1) * 16, (i // 4 + 1) * 16], 'texture': 0} for i, side in enumerate(SIDES)}
        # Mojang 26.3 native mesh: [1,0,2]..[15,16,14], centered at x/z=8.
        cube = {'name': 'species_shell', 'type': 'cube', 'uuid': identity(name + '/cube'),
                'box_uv': False, 'from': [-7.02, -.02, -6.02], 'to': [7.02, 16.02, 6.02],
                'origin': [0, 0, 0], 'rotation': [0, 0, 0], 'faces': faces}
        animation = {'uuid': identity(name + '/idle'), 'name': 'idle', 'loop': 'loop', 'override': False,
                     'length': 1, 'snapping': 20, 'animators': {bone: {'name': 'shell', 'type': 'bone',
                     'keyframes': [{'channel': 'rotation', 'time': t, 'uuid': identity(name + '/idle/' + str(t)),
                                    'interpolation': 'linear', 'data_points': [{'x': '0', 'y': '0', 'z': '0'}]} for t in (0, 1)]}}}
        model = {'meta': {'format_version': '5.0', 'model_format': 'free', 'box_uv': False},
                 'name': name, 'model_identifier': name, 'resolution': {'width': 64, 'height': 64},
                 'elements': [cube], 'groups': [{'name': 'shell', 'origin': [0, 0, 0], 'uuid': bone, 'rotation': [0, 0, 0], 'export': True}],
                 'outliner': [{'uuid': bone, 'isOpen': True, 'children': [cube['uuid']]}],
                 'textures': [{'name': name + '.png', 'id': '0', 'uuid': identity(name + '/texture'), 'width': 64,
                               'height': 64, 'uv_width': 64, 'uv_height': 64, 'source': 'data:image/png;base64,' + base64.b64encode(texture).decode()}],
                 'animations': [animation]}
        uv = {side: {'uv': face['uv'][2:], 'uv_size': [-16, -16]} if side in ('up', 'down') else {'uv': face['uv'][:2], 'uv_size': [16, 16]} for side, face in faces.items()}
        geometry = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {
            'identifier': 'geometry.' + name, 'texture_width': 64, 'texture_height': 64,
            'visible_bounds_width': 2, 'visible_bounds_height': 2, 'visible_bounds_offset': [0, .5, 0]},
            'bones': [{'name': 'shell', 'pivot': [0, 0, 0], 'cubes': [{'origin': [-cube['to'][0], cube['from'][1], cube['from'][2]],
                'size': [b - a for a, b in zip(cube['from'], cube['to'])], 'uv': uv}]}]}]}
        outputs = {name + '.bbmodel': model, name + '.geo.json': geometry,
                   name + '.animation.json': {'format_version': '1.8.0', 'animations': {'idle': {'loop': True, 'animation_length': 1, 'bones': {'shell': {'rotation': {'0': [0, 0, 0], '1': [0, 0, 0]}}}}}},
                   'config.json': {'head_rotation': False, 'material': 'entity_alphatest_change_color_one_sided', 'blend_transition': True,
                                   'binding_bones': {name: ['*']}, 'anim_textures': {}, 'texture_materials': {}, 'disable_part_visibility': True}}
        for path, content in outputs.items():
            (folder / path).write_text(json.dumps(content, indent=2) + '\n')
        (folder / (name + '.png')).write_bytes(texture)
    print('Built seven distinct placed egg shells for Java and Bedrock.')


if __name__ == '__main__':
    build()
