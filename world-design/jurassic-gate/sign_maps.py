"""Build the Jurassic Ark lettering as native 128 px filled maps for both editions."""
import argparse
from hashlib import sha256
import json
from math import floor
from pathlib import Path
import struct
from uuid import NAMESPACE_URL, uuid5
import zlib


HERE = Path(__file__).resolve().parent
V5_HASH = '555f7ac08ef8e3a3e32d9749fcbca53f1f682dac0d82940c57623b0ba75c349a'
FIRST_MAP = 9221100
LEFT, TOP, FRAME_Z = -487, 134, -373
COLUMNS, ROWS, SIDE = 15, 5, 128
TAG = 'ark_jurassic_gate_map_v6'
# Java 26.3 MapColor IDs and their visible RGB values at these packed shades.
BACKGROUND, GOLD, RED = 84, 121, 210
PALETTE = {BACKGROUND: (53, 53, 53), GOLD: (215, 205, 66), RED: (189, 48, 49)}


def build_sign(model):
    words = json.loads((HERE / 'glyphs.json').read_bytes())
    width, height = COLUMNS * SIDE, ROWS * SIDE
    ink = [bytearray(width) for _ in range(height)]
    labels, displays = model['labels'], model['entities']
    if len(displays) != 5 * len(labels):
        raise ValueError('Expected four outline layers and one face per word')
    for index, label in enumerate(labels):
        glyph = words[label['text']]
        for layer, display in enumerate(displays[index * 5:index * 5 + 5]):
            x, y, _ = display['position']
            sx, sy, _ = [n / 40 for n in display['nbt']['transformation']['scale']]
            # Original local -Z facing rotates 180° at the spawn. Reading east is +world X.
            left = -479 - x - (len(glyph[0]) / 2 - 1) * sx
            top = 113 + y + 9 * sy
            # Sample each map pixel at its world-space center in the original glyph mask.
            xs = [floor((LEFT + (px + .5) / SIDE - left) / sx) for px in range(width)]
            columns = [[px for px, gx in enumerate(xs) if 0 <= gx < len(row) and row[gx] == '#'] for row in glyph]
            color = RED if layer == 4 else GOLD
            for py, canvas_row in enumerate(ink):
                gy = floor((top - TOP + (py + .5) / SIDE) / sy)
                if 0 <= gy < len(glyph):
                    for px in columns[gy]:
                        canvas_row[px] = color

    # Geyser represents hanging frames as Bedrock block entities. All candidate
    # frame cells must be air, including the positions in front of the stone lip.
    blocks = {(-480 - x, 113 + y, -372 - z) for x, y, z, _ in model['blocks']}
    tiles, colors = [], {}
    # Geyser does not hide the underlying frame. Cover its wood only on occupied tiles.
    opaque_background = bytes.maketrans(b'\x00', bytes([BACKGROUND]))
    for row in range(ROWS):
        for column in range(COLUMNS):
            pos = (LEFT + column, TOP - 1 - row, FRAME_Z)
            if pos in blocks:
                raise ValueError(f'Frame would cover an existing block at {pos}')
            tile = b''.join(line[column * SIDE:(column + 1) * SIDE] for line in ink[row * SIDE:(row + 1) * SIDE])
            if not any(tile):
                continue
            map_id = FIRST_MAP + row * COLUMNS + column
            pixels = tile.translate(opaque_background)
            colors[map_id] = pixels
            tiles.append({'mapId': map_id, 'uuid': str(uuid5(NAMESPACE_URL, f'ark:jurassic_gate_map_v6:{map_id}')),
                          'position': list(pos), 'row': row, 'column': column, 'file': f'map_{map_id}.colors',
                          'sha256': sha256(pixels).hexdigest()})
    spec = {'sourceModelSha256': V5_HASH, 'words': [label['text'] for label in labels],
            'dimension': 'minecraft:overworld', 'firstMapId': FIRST_MAP, 'columns': COLUMNS, 'rows': ROWS,
            'topLeftCell': [LEFT, TOP - 1, FRAME_Z], 'tilePixels': SIDE, 'tag': TAG,
            'frameType': 'minecraft:glow_item_frame', 'facing': 'south', 'itemRotation': 0,
            'fixed': True, 'invisible': True, 'invulnerable': True, 'locked': True,
            'background': 'opaque', 'backgroundColor': BACKGROUND,
            'palette': {'background': BACKGROUND, 'outline': GOLD, 'face': RED},
            'legacyTextDisplays': {'action': 'clearText', 'keepUuids': True, 'count': len(displays),
                                   'tag': 'jurassic_ark_spawn_v4_20261115'}, 'tiles': tiles}
    return spec, colors


def write_tiles(directory, spec, colors):
    directory.mkdir(parents=True, exist_ok=True)
    width, height = COLUMNS * SIDE, ROWS * SIDE
    # Each PNG scanline starts with filter byte 0, followed by the native map color indices.
    rows = [bytearray(1 + width) for _ in range(height)]
    for tile in spec['tiles']:
        pixels = colors[tile['mapId']]
        (directory / tile['file']).write_bytes(pixels)
        left = 1 + tile['column'] * SIDE
        for dy in range(SIDE):
            rows[tile['row'] * SIDE + dy][left:left + SIDE] = pixels[dy * SIDE:(dy + 1) * SIDE]
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    palette = bytes(channel for index in range(max(PALETTE) + 1) for channel in PALETTE.get(index, (0, 0, 0)))
    header = struct.pack('>IIBBBBB', width, height, 8, 3, 0, 0, 0)
    (directory / 'mosaic.png').write_bytes(
        b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', header) + chunk(b'PLTE', palette) + chunk(b'tRNS', b'\x00')
        + chunk(b'IDAT', zlib.compress(b''.join(rows))) + chunk(b'IEND', b''))
    (directory / 'manifest.json').write_bytes((json.dumps(spec, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=HERE / 'generated', help='Directory for the maps and PNG preview')
    args = parser.parse_args()
    sign, colors = build_sign(json.loads((HERE / 'gate-model.json').read_bytes()))
    write_tiles(args.output, sign, colors)
    print(f'{len(colors)} native map tiles in {args.output}')
