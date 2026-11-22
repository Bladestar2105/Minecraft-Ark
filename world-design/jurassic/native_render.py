"""Render a north-up terrain/biome atlas from saved Overworld chunks, offline.

Only Python's standard library is required. The world is read without loading or
generating chunks. A complete render is published only when every pixel has
saved, fully generated terrain. --allow-partial is for transparent development
previews. Colors summarize surface blocks and biomes, not exact structure detail.
Use --verify-all-chunks for production: sampled pixels alone do not prove that
the entire world is fully generated.
Read a stable snapshot or stop the server so region files cannot change mid-read.
"""
import argparse
from array import array
from collections import Counter, defaultdict
import json
import os
from pathlib import Path
import re
import struct
import tempfile
import time
import zlib


MAX_CHUNK_BYTES = 16 * 1024 * 1024
STATUS_FIELDS = {'xPos': True, 'zPos': True, 'Status': True}
SURFACE_FIELDS = {
    'DataVersion': True, 'xPos': True, 'zPos': True, 'yPos': True, 'Status': True,
    'Heightmaps': {'WORLD_SURFACE': True},
    'sections': {'Y': True, 'biomes': {'palette': True, 'data': True},
                 'block_states': {'palette': {'id': True, 'Name': True, '': True}, 'data': True}},
}


class NBTReader:
    """Bounded NBT reader; unneeded arrays remain unallocated and are skipped."""
    def __init__(self, data):
        if len(data) > MAX_CHUNK_BYTES:
            raise ValueError('NBT chunk exceeds 16 MiB limit')
        self.data = memoryview(data)
        self.offset = 0

    def take(self, size):
        if size < 0 or self.offset + size > len(self.data):
            raise ValueError('Truncated NBT data')
        start = self.offset
        self.offset += size
        return self.data[start:self.offset]

    def number(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def string(self, wanted=True):
        data = self.take(self.number('>H'))
        return bytes(data).decode('utf-8') if wanted else None

    def value(self, kind, fields, depth=0):
        if depth > 64:
            raise ValueError('NBT nesting exceeds 64 levels')
        fixed = {1: '>b', 2: '>h', 3: '>i', 4: '>q', 5: '>f', 6: '>d'}
        if kind in fixed:
            data = self.take(struct.calcsize(fixed[kind]))
            return struct.unpack(fixed[kind], data)[0] if fields is not None else None
        if kind == 8:
            return self.string(fields is not None)
        if kind in (7, 11, 12):
            count = self.number('>i')
            if count < 0:
                raise ValueError('Negative NBT array length')
            data = self.take(count * {7: 1, 11: 4, 12: 8}[kind])
            return data if fields is not None else None
        if kind == 9:
            item_kind, count = self.number('>B'), self.number('>i')
            minimum = {0: 0, 1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8, 7: 4, 8: 2, 9: 5, 10: 1, 11: 4, 12: 4}
            if count < 0 or count > 65536 or item_kind not in minimum or (item_kind == 0 and count):
                raise ValueError('Invalid or excessive NBT list length/type')
            if count * minimum[item_kind] > len(self.data) - self.offset:
                raise ValueError('Truncated NBT list')
            result = [] if fields is not None else None
            for _ in range(count):
                value = self.value(item_kind, fields, depth + 1)
                if result is not None:
                    result.append(value)
            return result
        if kind == 10:
            result = {} if fields is not None else None
            while True:
                child_kind = self.number('>B')
                if child_kind == 0:
                    return result
                name = self.string()
                wanted = True if fields is True else fields.get(name) if isinstance(fields, dict) else None
                value = self.value(child_kind, wanted, depth + 1)
                if wanted is not None:
                    result[name] = value
        raise ValueError(f'Invalid NBT tag type {kind}')


def parse_chunk(data, fields=SURFACE_FIELDS):
    reader = NBTReader(data)
    if reader.number('>B') != 10:
        raise ValueError('NBT root must be a compound')
    reader.string(False)
    result = reader.value(10, fields)
    if reader.offset != len(reader.data):
        raise ValueError('Trailing data after NBT root')
    return result


def decompress_chunk(payload, compression):
    if compression == 4:
        raise ValueError('LZ4 Anvil compression is unsupported by this stdlib renderer; save chunks using zlib first')
    if compression == 3:
        if len(payload) > MAX_CHUNK_BYTES:
            raise ValueError('Raw chunk exceeds 16 MiB limit')
        return payload
    if compression not in (1, 2):
        raise ValueError(f'Unsupported Anvil compression {compression}')
    decoder = zlib.decompressobj(31 if compression == 1 else 15)
    try:
        result = decoder.decompress(payload, MAX_CHUNK_BYTES + 1)
    except zlib.error as error:
        raise ValueError(f'Invalid compressed chunk: {error}') from error
    if len(result) > MAX_CHUNK_BYTES or decoder.unconsumed_tail:
        raise ValueError('Decompressed chunk exceeds 16 MiB limit')
    if not decoder.eof or decoder.unused_data:
        raise ValueError('Truncated or trailing compressed chunk data')
    return result


class RegionFile:
    """One open region at a time, with each sampled chunk decoded only once."""
    def __init__(self, path):
        self.path = Path(path)
        match = re.fullmatch(r'r\.(-?\d+)\.(-?\d+)\.mca', self.path.name)
        if not match:
            raise ValueError(f'Invalid region filename: {self.path.name}')
        self.rx, self.rz = map(int, match.groups())

    def __enter__(self):
        self.stream = self.path.open('rb')
        try:
            size = os.fstat(self.stream.fileno()).st_size
            header = self.stream.read(8192)
            if len(header) != 8192:
                raise ValueError(f'Truncated region header: {self.path.name}')
            self.locations = struct.unpack('>1024I', header[:4096])
            occupied = {0, 1}
            for location in self.locations:
                if not location:
                    continue
                start, count = location >> 8, location & 255
                # Paper may leave only the final sector's unused padding unwritten.
                if start < 2 or not count or start + count > (size + 4095) // 4096:
                    raise ValueError(f'Invalid region chunk location: {self.path.name}')
                extent = set(range(start, start + count))
                if occupied & extent:
                    raise ValueError(f'Overlapping region chunks: {self.path.name}')
                occupied.update(extent)
            return self
        except Exception:
            self.stream.close()
            raise

    def __exit__(self, *exc):
        self.stream.close()

    def read_chunk(self, cx, cz, fields=SURFACE_FIELDS):
        if (cx // 32, cz // 32) != (self.rx, self.rz):
            raise ValueError('Chunk does not belong to this region')
        location = self.locations[(cx & 31) + 32 * (cz & 31)]
        if not location:
            return None
        start, count = location >> 8, location & 255
        self.stream.seek(start * 4096)
        header = self.stream.read(5)
        if len(header) != 5:
            raise ValueError('Truncated region chunk header')
        length, flag = struct.unpack('>IB', header)
        if not 1 <= length <= count * 4096 - 4:
            raise ValueError(f'Invalid chunk payload length in {self.path.name}')
        if flag & 128:
            if length != 1:
                raise ValueError('Invalid external chunk record length')
            external = self.path.with_name(f'c.{cx}.{cz}.mcc')
            with external.open('rb') as stream:
                payload = stream.read(MAX_CHUNK_BYTES + 1)
        else:
            payload = self.stream.read(length - 1)
            if len(payload) != length - 1:
                raise ValueError('Truncated region chunk payload')
        if len(payload) > MAX_CHUNK_BYTES:
            raise ValueError('Compressed chunk exceeds 16 MiB limit')
        chunk = parse_chunk(decompress_chunk(payload, flag & 127), fields)
        if (chunk.get('xPos'), chunk.get('zPos')) != (cx, cz):
            raise ValueError(f'Chunk coordinates disagree with region slot: {cx}, {cz}')
        return chunk


def packed_value(data, index, bits, count):
    per_word = 64 // bits
    expected = ((count + per_word - 1) // per_word) * 8
    if len(data) != expected or not 0 <= index < count:
        raise ValueError('Invalid packed palette/heightmap data length or index')
    word = struct.unpack_from('>Q', data, (index // per_word) * 8)[0]
    return (word >> ((index % per_word) * bits)) & ((1 << bits) - 1)


def palette_value(container, index, count, minimum_bits):
    palette = container.get('palette', [])
    if not palette:
        raise ValueError('Missing surface palette')
    entry = 0 if len(palette) == 1 else packed_value(container.get('data', b''), index, max(minimum_bits, (len(palette) - 1).bit_length()), count)
    if entry >= len(palette):
        raise ValueError('Packed palette index exceeds palette')
    value = palette[entry]
    if isinstance(value, dict):
        value = value.get('id', value.get('Name', value.get('')))
    if not isinstance(value, str):
        raise ValueError('Invalid surface palette identifier')
    return value


class MissingSurface(ValueError):
    pass


def surface_at(chunk, x, z):
    heights = chunk.get('Heightmaps', {}).get('WORLD_SURFACE')
    if heights is None or not isinstance(chunk.get('yPos'), int):
        raise MissingSurface('Missing WORLD_SURFACE heightmap or minimum build height')
    height = packed_value(heights, x + 16 * z, 9, 256)
    if height == 0:
        raise MissingSurface('Empty surface column')
    y = height + chunk['yPos'] * 16 - 1
    section = next((s for s in chunk.get('sections', []) if s.get('Y') == y // 16), None)
    if section is None or 'biomes' not in section or 'block_states' not in section:
        raise MissingSurface('Missing surface section')
    biome = palette_value(section['biomes'], (y & 15) // 4 * 16 + z // 4 * 4 + x // 4, 64, 1)
    block = palette_value(section['block_states'], (y & 15) * 256 + z * 16 + x, 4096, 4)
    if block in ('minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'):
        raise MissingSurface('Surface heightmap points to air')
    return y, biome, block


def terrain_color(biome, block):
    # shortcut: palette colors summarize terrain; add per-block colors if structure detail is needed.
    biome, block = biome.removeprefix('minecraft:'), block.removeprefix('minecraft:')
    if block in ('water', 'bubble_column'):
        return (52, 108, 159) if 'warm' not in biome else (58, 143, 169)
    if 'snow' in block or 'ice' in block:
        return (227, 235, 233)
    if block == 'lava':
        return (214, 99, 41)
    if block in ('sand', 'sandstone', 'smooth_sandstone'):
        return (220, 204, 150)
    if 'red_sand' in block or 'terracotta' in block:
        return (176, 114, 72)
    if any(word in block for word in ('stone', 'gravel', 'andesite', 'diorite', 'granite', 'tuff', 'deepslate')):
        return (133, 140, 135)
    if any(word in block for word in ('log', 'wood', 'planks', 'dirt', 'mud')):
        return (128, 113, 77)
    if 'leaves' in block:
        return (59, 111, 59)
    for names, color in (
        (('snow', 'frozen', 'ice'), (209, 225, 219)),
        (('desert', 'beach'), (211, 195, 131)),
        (('badlands',), (177, 121, 76)),
        (('jungle', 'bamboo'), (60, 117, 58)),
        (('swamp', 'mangrove'), (78, 108, 68)),
        (('taiga', 'grove'), (75, 117, 89)),
        (('forest',), (75, 125, 65)),
        (('savanna',), (156, 160, 78)),
        (('stony', 'peaks'), (137, 148, 137)),
        (('mushroom',), (138, 105, 130)),
    ):
        if any(name in biome for name in names):
            return color
    return (110, 150, 73)


def write_png(path, width, height, pixels):
    def png_chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
    rows = b''.join(b'\0' + pixels[y * width * 4:(y + 1) * width * 4] for y in range(height))
    image = (b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
             + png_chunk(b'IDAT', zlib.compress(rows, 6)) + png_chunk(b'IEND', b''))
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=f'.{path.name}.', delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(image)
        temporary.replace(path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def render(regions, output, *, size=20480, resolution=1024, allow_partial=False, verify_all_chunks=False,
           center_x=0, center_z=0):
    if not isinstance(size, int) or size <= 0 or size % 2 or not 1 <= resolution <= 2048:
        raise ValueError('World size must be positive and even; resolution must be 1..2048')
    regions = Path(regions)
    if not regions.is_dir():
        raise ValueError(f'Region directory does not exist: {regions}')
    started = time.monotonic()
    total = resolution * resolution
    pixels = bytearray(total * 4)
    heights = array('h', [-32768]) * total
    if type(center_x) is not int or type(center_z) is not int:
        raise ValueError('Map origins must be integer block coordinates')
    def axis(center):
        result=defaultdict(lambda: defaultdict(list))
        if verify_all_chunks:
            for coordinate in range((center-size//2)//16,(center+size//2-1)//16+1):
                result[coordinate//32][coordinate]=[]
        for pixel in range(resolution):
            coordinate=center-size//2+(2*pixel+1)*size//(2*resolution)
            result[coordinate//512][coordinate//16].append((pixel,coordinate&15))
        return result
    x_axis,z_axis=axis(center_x),axis(center_z)
    report = {'status': 'incomplete', 'map_type': 'saved surface block / biome terrain with height shading',
              'bounds': [center_x-size//2,center_z-size//2,center_x+size//2,center_z+size//2], 'north': 'negative Z',
              'world_size': size, 'resolution': resolution, 'blocks_per_pixel': size / resolution,
              'total_pixels': total, 'sampled_pixels': 0, 'missing_pixels': total,
              'regions_read': 0, 'chunks_read': 0, 'full_chunks': 0, 'missing_chunks': 0,
              'unavailable_chunks': 0, 'verify_all_chunks': verify_all_chunks,
              'expected_chunks': ((center_x+size//2-1)//16-(center_x-size//2)//16+1)
                                 *((center_z+size//2-1)//16-(center_z-size//2)//16+1),
              'verified_chunks': 0, 'incomplete_chunks': 0}
    statuses = Counter()
    for rz, z_chunks in sorted(z_axis.items()):
        for rx, x_chunks in sorted(x_axis.items()):
            path = regions / f'r.{rx}.{rz}.mca'
            if not path.exists():
                missing = len(x_chunks) * len(z_chunks)
                report['missing_chunks'] += missing
                report['incomplete_chunks'] += missing
                continue
            with RegionFile(path) as region:
                report['regions_read'] += 1
                for cz, zs in z_chunks.items():
                    for cx, xs in x_chunks.items():
                        sampled = bool(xs and zs)
                        chunk = region.read_chunk(cx, cz, SURFACE_FIELDS if sampled else STATUS_FIELDS)
                        if chunk is None:
                            report['missing_chunks'] += 1
                            report['incomplete_chunks'] += 1
                            continue
                        report['chunks_read'] += 1
                        status = chunk.get('Status', 'missing')
                        statuses[status] += 1
                        if status not in ('full', 'minecraft:full'):
                            report['unavailable_chunks'] += 1
                            report['incomplete_chunks'] += 1
                            continue
                        report['full_chunks'] += 1
                        report['verified_chunks'] += 1
                        unavailable = False
                        for pz, z in zs:
                            for px, x in xs:
                                try:
                                    height, biome, block = surface_at(chunk, x, z)
                                except MissingSurface:
                                    unavailable = True
                                    continue
                                index = pz * resolution + px
                                heights[index] = height
                                pixels[index * 4:index * 4 + 4] = bytes((*terrain_color(biome, block), 255))
                                report['sampled_pixels'] += 1
                        report['unavailable_chunks'] += int(unavailable)
                        report['incomplete_chunks'] += int(unavailable)
    report['missing_pixels'] = total - report['sampled_pixels']
    report['status_counts'] = dict(statuses)
    complete = not report['missing_pixels'] and (not verify_all_chunks or not report['incomplete_chunks'])
    if complete or allow_partial:
        for index, height in enumerate(heights):
            if height == -32768:
                continue
            north = heights[index - resolution] if index >= resolution else height
            west = heights[index - 1] if index % resolution else height
            difference = sum(n - height for n in (north, west) if n != -32768)
            shade = max(.72, min(1.22, 1 + difference * .014))
            for channel in range(3):
                at = index * 4 + channel
                pixels[at] = min(255, round(pixels[at] * shade))
        write_png(output, resolution, resolution, pixels)
        report['status'] = 'passed' if complete else 'partial'
        report['output'] = str(Path(output).resolve())
    report['elapsed_seconds'] = round(time.monotonic() - started, 3)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('regions', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--size', type=int, default=20480)
    parser.add_argument('--resolution', type=int, default=1024)
    parser.add_argument('--center-x', type=int, default=0)
    parser.add_argument('--center-z', type=int, default=0)
    parser.add_argument('--allow-partial', action='store_true', help='Development only: unavailable terrain is transparent')
    parser.add_argument('--verify-all-chunks', action='store_true', help='Require every interior chunk, including unsampled chunks, to be fully generated')
    args = parser.parse_args()
    try:
        report = render(args.regions, args.output, size=args.size, resolution=args.resolution, allow_partial=args.allow_partial,
                        verify_all_chunks=args.verify_all_chunks,center_x=args.center_x,center_z=args.center_z)
    except (OSError, ValueError, struct.error) as error:
        report = {'status': 'failed', 'error': str(error)}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return 0 if report['status'] in ('passed', 'partial') else 1


if __name__ == '__main__':
    raise SystemExit(main())
