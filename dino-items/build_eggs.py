#!/usr/bin/env python3
"""Build original 16px egg art and matching edition assets, using only stdlib."""
import base64
import binascii
import json
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
# Each egg has its own silhouette, shell palette and hand-placed markings.
EGGS = {
    'trex': ('T-Rex', '#83935b', '#334d36', '#c2c58b', [2, 3, 4, 5, 5, 6, 6, 6, 6, 5, 4, 2]),
    'triceratops': ('Triceratops', '#c6aa78', '#76573d', '#eee0b6', [2, 4, 5, 6, 6, 6, 6, 6, 5, 4, 2]),
    'stegosaurus': ('Stegosaurus', '#bc7253', '#743f3c', '#ebbc7c', [2, 3, 4, 5, 5, 5, 5, 5, 5, 4, 3, 2]),
    'brachiosaurus': ('Brachiosaurus', '#a4b68b', '#627d60', '#e5dfae', [2, 4, 5, 6, 6, 7, 7, 7, 6, 6, 5, 3]),
    'velociraptor': ('Velociraptor', '#c5a35c', '#67523e', '#e9d79d', [1, 2, 3, 4, 4, 4, 4, 4, 4, 3, 3, 2, 1]),
    'pteranodon': ('Pteranodon', '#91aebb', '#455b7d', '#d8e0da', [1, 2, 3, 3, 4, 4, 4, 4, 4, 4, 3, 2, 1]),
    'dilophosaurus': ('Dilophosaurus', '#9b829e', '#5c476d', '#d5bbac', [1, 3, 4, 5, 5, 5, 5, 5, 5, 4, 3, 2]),
    'mosasaurus': ('Mosasaurus', '#528e99', '#284d65', '#b4d7c3', [2, 4, 5, 6, 6, 7, 7, 6, 6, 5, 3]),
}


def rgb(value):
    return tuple(bytes.fromhex(value.lstrip('#')))


def png(pixels):
    height, width = len(pixels), len(pixels[0])
    def chunk(kind, value):
        return struct.pack('>I', len(value)) + kind + value + struct.pack('>I', binascii.crc32(kind + value) & 0xffffffff)
    raw = b''.join(b'\0' + bytes(channel for pixel in row for channel in pixel) for row in pixels)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))


def egg_pixels(species):
    _, shell, dark, light, widths = EGGS[species]
    top = 15 - len(widths)
    mask = {(x, y + top) for y, width in enumerate(widths) for x in range(8 - width, 8 + width)}
    pixels = [[(0, 0, 0, 0) for _ in range(16)] for _ in range(16)]
    for x, y in mask:
        edge = any((x + dx, y + dy) not in mask for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        marking = {
            'trex': (x + y // 2) % 6 in (0, 1) and y in (5, 6, 9, 10, 11),
            'triceratops': (x, y) in {(4, 8), (5, 7), (5, 9), (8, 5), (8, 6), (8, 7), (10, 8), (11, 7), (11, 9), (7, 12), (8, 12)},
            'stegosaurus': abs(x - 7) + abs((y - 3) % 5 - 2) <= 2,
            'brachiosaurus': (x, y) in {(4, 6), (5, 6), (4, 7), (9, 8), (10, 8), (10, 9), (5, 11), (6, 11), (6, 12), (11, 12)},
            'velociraptor': (x + y // 2) % 5 == 0 and 4 <= y <= 12,
            'pteranodon': abs(x - 7) == (y - 3) // 3 + 1 and 5 <= y <= 12,
            'dilophosaurus': x in (5, 6, 9, 10) and y in (6, 7, 8, 11, 12),
            'mosasaurus': y in (7 + (x // 3) % 2, 11 + (x // 3) % 2),
        }[species]
        color = dark if edge or marking else light if x < 6 and y < top + 6 else shell
        channels = rgb(color)
        shade = -15 if x > 9 or y > 11 else 7 if (x * 3 + y * 5) % 11 == 0 else 0
        pixels[y][x] = tuple(max(0, min(255, c + shade)) for c in channels) + (255,)
    return pixels


def encoded_json(value):
    return json.dumps(value, separators=(',', ':')).encode() + b'\n'


def assets():
    java, bedrock, atlas, definitions = {}, {}, {}, []
    for species, (label, *_) in EGGS.items():
        name = 'egg_' + species
        key = 'arkdinos:' + name
        texture = png(egg_pixels(species))
        java['assets/arkdinos/textures/item/' + name + '.png'] = texture
        java['assets/arkdinos/items/' + name + '.json'] = encoded_json({'model': {'type': 'minecraft:model', 'model': 'arkdinos:item/' + name}})
        java['assets/arkdinos/models/item/' + name + '.json'] = encoded_json({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'arkdinos:item/' + name}})
        bedrock['textures/items/arkdinos/' + name + '.png'] = texture
        icon = 'arkdinos.' + name
        atlas[icon] = {'textures': 'textures/items/arkdinos/' + name}
        definitions.append({'type': 'definition', 'model': key, 'bedrock_identifier': key,
                            'display_name': label + (' Spawn-Ei' if species == 'mosasaurus' else '-Ei'),
                            'bedrock_options': {'icon': icon}})
    bedrock['textures/item_texture.json'] = encoded_json({'resource_pack_name': 'ark_dino_eggs', 'texture_name': 'atlas.items', 'texture_data': atlas})
    # Stable UUIDs let Bedrock update this pack while keeping the model pack independent.
    bedrock['manifest.json'] = encoded_json({'format_version': 2, 'header': {
        'name': 'ARK Dinosaur Eggs', 'description': 'Eight original species egg sprites',
        'uuid': '4b6d5dd8-9250-48cf-bc87-48251cfaf744', 'version': [1, 0, 0], 'min_engine_version': [1, 21, 0]},
        'modules': [{'type': 'resources', 'uuid': '708d04a5-5dc7-490e-bb47-0a282214ddc2', 'version': [1, 0, 0]}]})
    return {'java': java, 'bedrock': bedrock, 'geyser': {'format_version': 2, 'items': {'minecraft:sniffer_egg': definitions}}}


def build():
    result = assets()
    bundled = {edition: {name: base64.b64encode(content).decode() for name, content in result[edition].items()} for edition in ('java', 'bedrock')}
    bundled['geyser'] = result['geyser']
    (ROOT / 'dino-items/egg-assets.json').write_text(json.dumps(bundled, indent=2) + '\n')
    (ROOT / 'dino-plugin/src/main/resources/egg-assets.properties').write_text(''.join(name + '=' + base64.b64encode(content).decode() + '\n' for name, content in result['java'].items()))
    print('Built eight unique egg sprites, 24 Java assets and matching Geyser/Bedrock assets.')


def preview(folder):
    folder.mkdir(parents=True, exist_ok=True)
    scale = 16
    canvas = [[(39, 45, 36, 255) for _ in range(4 * 288)] for _ in range(2 * 288)]
    svg = ['<svg xmlns="http://www.w3.org/2000/svg" width="1152" height="640" viewBox="0 0 1152 640"><rect width="1152" height="640" fill="#272d24"/>']
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
