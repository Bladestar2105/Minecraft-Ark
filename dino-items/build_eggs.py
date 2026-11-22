#!/usr/bin/env python3
"""Build original 16px egg art and matching edition assets, using only stdlib."""
import base64
import binascii
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
# Shell, pigment and chalk colors are shared with the placed models. The glyphs are hand-pixeled at inventory scale.
EGGS = {
    'trex': ('T-Rex', '#a79d71', '#524836', '#e6d9ae', (
        'wls', 'wlhhbs', 'wlhlblbs', 'whlpblbls', 'wllpwbblbws',
        'wlpwlllbbpws', 'wlbpwblbppssd', 'sblbbllpwbbwd', 'sbwllplwbpssd',
        'swwpllpbbwsd', 'spwbbpwwpsd', 'dspwbspsd', 'dsswsd', 'ddd')),
    'triceratops': ('Triceratops', '#cbb18a', '#8a644d', '#f2e3bc', (
        'wllbs', 'wlhhlbs', 'wlhhhllbs', 'wlhwlblbws', 'wllpwlbbwlbs',
        'wlppllbwllbbs', 'sblwlllpwlwpwd', 'sbbllllpplbbwd', 'spwbbblwwbbsd',
        'ssbpwbblppsd', 'dspwwwbwsd', 'dsswwsd', 'dddd')),
    'stegosaurus': ('Stegosaurus', '#b38261', '#6e483d', '#ebc699', (
        'wls', 'wlhbs', 'wlhhlls', 'wllwllbws', 'wlpwhllbbs',
        'wlppblwlbws', 'sllbwlppbswd', 'sbbpplwwlbwd', 'sblwwblppwsd',
        'swbllppwbsd', 'spwwbwlpsd', 'dspbbwsd', 'dsssd', 'ddd')),
    'brachiosaurus': ('Brachiosaurus', '#a6b198', '#647760', '#e2e3c4', (
        'wllbs', 'wlhhlbs', 'wlhhhlllbs', 'wlhllblwllbs', 'wllbplbblblbs',
        'wlhwpllbhllbws', 'sllwlllwwllbwd', 'sbllllbpwbblwd', 'sblwwbblbbwwsd',
        'sblpwbllwbbwd', 'dsbbllbbwbsd', 'dssbbwwsd', 'dssssd', 'dddd')),
    'velociraptor': ('Velociraptor', '#dac9a9', '#7b5e48', '#f5edda', (
        'ls', 'wlls', 'whhls', 'wlhhlbs', 'wlhhlpws', 'wlhlpwlbs',
        'wllpwlblbs', 'wlpwllpbls', 'slllbpwbbd', 'sllwpwbbwd',
        'sblpwlbwd', 'sbbblwwd', 'dsbwwd', 'dssd', 'dd')),
    'pteranodon': ('Pteranodon', '#aab6b2', '#627477', '#e7e9db', (
        'ls', 'lls', 'whlls', 'wlhhlbs', 'wlhllbbs', 'wllwllbbs',
        'wlhllwllbs', 'sllwllwwbd', 'sllblwbbwd', 'sblwbbwwd',
        'sbbllbwsd', 'dsbwwwd', 'dsswd', 'ddd')),
    'dilophosaurus': ('Dilophosaurus', '#b19892', '#76555d', '#ead1b4', (
        'wls', 'wlhls', 'wlhhlls', 'wlhplblbs', 'wllpwllbws',
        'wlphllplbbs', 'sllplwppbwd', 'sllwlplblwd', 'sbplllwwsd',
        'sbbpwlppsd', 'dspwblwsd', 'dsbwwsd', 'dsssd', 'dd')),
    'mosasaurus': ('Mosasaurus', '#749890', '#3c5961', '#c9d8bb', (
        'wllbs', 'wlhhlllbs', 'wlhhllwwlls', 'wlllwpplllbbs',
        'wlhllwwllwppws', 'sllwwllllwwbbd', 'sbppllbwpwwbsd',
        'sbwblppwblbsd', 'dsllwbbwwwsd', 'dsbbbbwsd', 'dsswsd', 'dddd')),
    'gallimimus': ('Gallimimus', '#d2c09a', '#806e48', '#f3e5c7', (
        'ls', 'wlls', 'whhls', 'wlhhlbs', 'wlhhllws', 'wllplwlbs',
        'wlhwllblbs', 'wllpwllbbs', 'sllwlplbbd', 'sblwplwbwd',
        'sblpwbbwd', 'sbbwlwwd', 'dswwwd', 'dssd', 'dd')),
}
CREATIVE_ALIASES = dict(zip(EGGS, ('bat', 'endermite', 'silverfish', 'vex', 'phantom', 'strider', 'tadpole', 'trader_llama', 'armadillo')))


def rgb(value):
    return tuple(bytes.fromhex(value.lstrip('#')))


def png(pixels):
    height, width = len(pixels), len(pixels[0])
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', binascii.crc32(kind + value) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def mix(a, b, amount):
    return tuple(round(lo + (hi - lo) * amount) for lo, hi in zip(a, b))


def egg_pixels(species):
    _, shell, pigment, chalk, rows = EGGS[species]
    shell, pigment, chalk = map(rgb, (shell, pigment, chalk))
    palette = {'d': mix(pigment, (29, 30, 29), .45), 'p': pigment, 's': mix(shell, pigment, .65),
               'w': mix(shell, pigment, .34), 'b': shell, 'l': mix(shell, chalk, .55), 'h': chalk}
    pixels = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    for y, row in enumerate(rows, start=15 - len(rows)):
        left = (16 - len(row)) // 2
        for x, ink in enumerate(row, start=left):
            pixels[y][x] = (*palette[ink], 255)
    return pixels


def encoded_json(value):
    return json.dumps(value, separators=(',', ':')).encode() + b'\n'


def assets():
    java, bedrock, atlas, definitions, names = {}, {}, {}, [], {}
    for species, (label, *_) in EGGS.items():
        name = 'egg_' + species
        key = 'arkdinos:' + name
        texture = png(egg_pixels(species))
        java['assets/arkdinos/textures/item/' + name + '.png'] = texture
        java['assets/arkdinos/items/' + name + '.json'] = encoded_json({'model': {'type': 'minecraft:model', 'model': 'arkdinos:item/' + name}})
        java['assets/arkdinos/models/item/' + name + '.json'] = encoded_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'arkdinos:item/' + name}})
        alias = CREATIVE_ALIASES[species]
        java['assets/minecraft/items/' + alias + '_spawn_egg.json'] = java['assets/arkdinos/items/' + name + '.json']
        names['item.minecraft.' + alias + '_spawn_egg'] = 'Dino-Ei · ' + label
        bedrock['textures/items/arkdinos/' + name + '.png'] = texture
        # Bedrock retains legacy egg paths as well as the current spawn_eggs folder.
        bedrock['textures/items/egg_' + alias + '.png'] = texture
        bedrock['textures/items/spawn_eggs/spawn_egg_' + alias + '.png'] = texture
        icon = 'arkdinos.' + name
        atlas[icon] = {'textures': 'textures/items/arkdinos/' + name}
        definitions.append({'type': 'definition', 'model': key, 'bedrock_identifier': key,
                            'display_name': label + (' Spawn-Ei' if species == 'mosasaurus' else '-Ei'),
                            'bedrock_options': {'icon': icon}})
    for language in ('en_us', 'de_de'):
        java['assets/minecraft/lang/' + language + '.json'] = encoded_json(names)
        bedrock['texts/' + ('en_US' if language == 'en_us' else 'de_DE') + '.lang'] = ''.join(
            'item.spawn_egg.entity.' + CREATIVE_ALIASES[species] + '.name=Dino-Ei · ' + label + '\n'
            for species, (label, *_) in EGGS.items()).encode()
    bedrock['textures/item_texture.json'] = encoded_json({'resource_pack_name': 'ark_dino_eggs', 'texture_name': 'atlas.items', 'texture_data': atlas})
    # Stable UUIDs let Bedrock update this pack while keeping the model pack independent.
    bedrock['manifest.json'] = encoded_json({'format_version': 2, 'header': {
        'name': 'ARK Dinosaur Eggs', 'description': 'Nine original species egg sprites',
        'uuid': '4b6d5dd8-9250-48cf-bc87-48251cfaf744', 'version': [1, 3, 0], 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': '708d04a5-5dc7-490e-bb47-0a282214ddc2', 'version': [1, 3, 0]}]})
    return {'java': java, 'bedrock': bedrock, 'geyser': {'format_version': 2, 'items': {'minecraft:sniffer_egg': definitions}}}


def build():
    result = assets()
    bundled = {edition: {name: base64.b64encode(content).decode() for name, content in result[edition].items()} for edition in ('java', 'bedrock')}
    bundled['geyser'] = result['geyser']
    (ROOT / 'dino-items/egg-assets.json').write_text(json.dumps(bundled, indent=2) + '\n')
    (ROOT / 'dino-plugin/src/main/resources/egg-assets.properties').write_text(''.join(name + '=' + base64.b64encode(content).decode() + '\n' for name, content in result['java'].items()))
    print('Built nine unique egg sprites, 38 Java assets and matching Creative aliases on both editions.')


def preview(folder):
    folder.mkdir(parents=True, exist_ok=True)
    scale = 16
    canvas = [[(39, 45, 36, 255) for _ in range(4 * 288)] for _ in range(((len(EGGS)+3)//4) * 288)]
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1152" height="960" viewBox="0 0 1152 960"><rect width="1152" height="960" fill="#272d24"/>']
    for index, (species, (label, *_)) in enumerate(EGGS.items()):
        x0, y0 = (index % 4) * 288 + 16, (index // 4) * 288
        pixels = egg_pixels(species)
        for y, row in enumerate(pixels):
            for x, pixel in enumerate(row):
                if pixel[3]:
                    for dy in range(scale):
                        for dx in range(scale):
                            canvas[y0 + y * scale + dy][x0 + x * scale + dx] = pixel
        data = base64.b64encode(png(pixels)).decode()
        sy = (index // 4) * 320
        svg.append(f'<image x="{x0}" y="{sy}" width="256" height="256" style="image-rendering:pixelated" href="data:image/png;base64,{data}"/><text x="{x0+128}" y="{sy+286}" fill="#f3e9ce" text-anchor="middle" font-family="monospace" font-size="22">{label}</text>')
    svg.append('</svg>')
    (folder / 'eight-eggs.png').write_bytes(png(canvas))
    (folder / 'eight-eggs.svg').write_text(''.join(svg))


if __name__ == '__main__':
    build()
    preview(ROOT / 'output/dino-round3-20261108')
