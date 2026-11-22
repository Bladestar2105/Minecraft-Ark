"""Atlas tests use tiny real Anvil/NBT fixtures, without Minecraft or image libraries."""
import gzip
import inspect
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zlib

try:
    import native_render as atlas
except ModuleNotFoundError:
    atlas = None


def string(value):
    raw = value.encode()
    return struct.pack('>H', len(raw)) + raw


def tag(kind, name, payload):
    return bytes([kind]) + string(name) + payload


def compound(*tags):
    return b''.join(tags) + b'\0'


def longs(values):
    return struct.pack('>i', len(values)) + b''.join(struct.pack('>Q', n) for n in values)


def chunk_nbt(cx=0, cz=0, block='minecraft:grass_block', height=65, status='minecraft:full'):
    # A 9-bit heightmap holds seven columns per long, never spanning words.
    heightmap = [sum(height << (9 * j) for j in range(7))] * 36 + [sum(height << (9 * j) for j in range(4))]
    section = compound(
        tag(1, 'Y', struct.pack('>b', (height - 65) // 16)),
        tag(10, 'biomes', compound(tag(9, 'palette', b'\x08' + struct.pack('>i', 1) + string('minecraft:plains')))),
        tag(10, 'block_states', compound(tag(9, 'palette', b'\x08' + struct.pack('>i', 1) + string(block)))),
    )
    return b'\x0a\0\0' + compound(
        tag(3, 'DataVersion', struct.pack('>i', 5023)),
        tag(3, 'xPos', struct.pack('>i', cx)),
        tag(3, 'zPos', struct.pack('>i', cz)),
        tag(3, 'yPos', struct.pack('>i', -4)),
        tag(8, 'Status', string(status)),
        tag(10, 'Heightmaps', compound(tag(12, 'WORLD_SURFACE', longs(heightmap)))),
        tag(9, 'sections', b'\x0a' + struct.pack('>i', 1) + section),
        tag(7, 'unused_blob', struct.pack('>i', 4096) + bytes(4096)),
    )


def write_region(directory, rx, rz, chunks, compression=2, external=False):
    header = bytearray(8192)
    records = bytearray()
    for (cx, cz), nbt in chunks.items():
        payload = {1: gzip.compress, 2: zlib.compress, 3: lambda b: b}[compression](nbt)
        flag = compression | (128 if external else 0)
        if external:
            (directory / f'c.{cx}.{cz}.mcc').write_bytes(payload)
            payload = b''
        record = struct.pack('>I', len(payload) + 1) + bytes([flag]) + payload
        sectors = (len(record) + 4095) // 4096
        start = 2 + len(records) // 4096
        struct.pack_into('>I', header, 4 * ((cx & 31) + 32 * (cz & 31)), (start << 8) | sectors)
        records.extend(record + bytes(sectors * 4096 - len(record)))
    path = directory / f'r.{rx}.{rz}.mca'
    path.write_bytes(header + records)
    return path


def read_png(path):
    raw = path.read_bytes()
    if raw[:8] != b'\x89PNG\r\n\x1a\n':
        raise AssertionError('Invalid PNG signature')
    offset, image, dimensions = 8, bytearray(), None
    while offset < len(raw):
        length = struct.unpack_from('>I', raw, offset)[0]
        kind = raw[offset + 4:offset + 8]
        data = raw[offset + 8:offset + 8 + length]
        checksum = struct.unpack_from('>I', raw, offset + 8 + length)[0]
        if checksum != zlib.crc32(kind + data):
            raise AssertionError('Invalid PNG CRC')
        if kind == b'IHDR':
            dimensions = struct.unpack('>IIBBBBB', data)
        if kind == b'IDAT':
            image.extend(data)
        offset += length + 12
    return dimensions, zlib.decompress(image)


class AtlasTests(unittest.TestCase):
    def test_reference_at_nonzero_final_coordinates(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory=Path(temporary)
            for rz in (3,4):
                for rx in (13,14):
                    chunks={(cx,cz):chunk_nbt(cx,cz) for cx in (447,448) for cz in (127,128)
                            if (cx//32,cz//32)==(rx,rz)}
                    write_region(directory,rx,rz,chunks)
            report=atlas.render(directory,directory/'reference.png',size=32,resolution=16,
                                center_x=7168,center_z=2048,verify_all_chunks=True)
            self.assertEqual(report['status'],'passed')
            self.assertEqual(report['bounds'],[7152,2032,7184,2064])
            self.assertEqual(report['verified_chunks'],4)

    def setUp(self):
        self.assertIsNotNone(atlas, 'The offline atlas renderer has not been implemented')
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def test_nbt_keeps_surface_fields_and_skips_unneeded_arrays(self):
        data = atlas.parse_chunk(chunk_nbt(-1, -2))
        self.assertEqual((data['xPos'], data['zPos'], data['yPos']), (-1, -2, -4))
        self.assertNotIn('unused_blob', data)
        self.assertEqual(atlas.surface_at(data, 8, 8), (0, 'minecraft:plains', 'minecraft:grass_block'))

    def test_heightmap_padding_and_negative_build_height(self):
        data = atlas.parse_chunk(chunk_nbt(height=1, block='minecraft:stone'))
        values = [0] * 37
        values[0] = 1 | (7 << 54)
        values[1] = 173
        data['Heightmaps']['WORLD_SURFACE'] = memoryview(b''.join(struct.pack('>Q', n) for n in values))
        self.assertEqual(atlas.packed_value(data['Heightmaps']['WORLD_SURFACE'], 6, 9, 256), 7)
        self.assertEqual(atlas.packed_value(data['Heightmaps']['WORLD_SURFACE'], 7, 9, 256), 173)
        self.assertEqual(atlas.surface_at(data, 0, 0)[0], -64)

    def test_multiple_palettes_and_new_mixed_compound_format(self):
        data = atlas.parse_chunk(chunk_nbt())
        section = data['sections'][0]
        section['block_states'] = {'palette': [{'': 'minecraft:grass_block'}, {'id': 'minecraft:water'}],
                                   'data': memoryview(struct.pack('>Q', 1 << 60) + bytes(255 * 8))}
        section['biomes'] = {'palette': ['minecraft:plains', 'minecraft:desert', 'minecraft:forest'],
                            'data': memoryview(struct.pack('>Q', 1 << 6) + bytes(8))}
        self.assertEqual(atlas.surface_at(data, 15, 0), (0, 'minecraft:desert', 'minecraft:water'))
        section['block_states']['palette'][1] = {'Name': 'minecraft:snow_block'}
        self.assertEqual(atlas.surface_at(data, 15, 0)[2], 'minecraft:snow_block')

    def test_mixed_nbt_palette_wrappers_are_parsed(self):
        palette = b'\x0a' + struct.pack('>i', 2) + compound(tag(8, '', string('minecraft:air'))) + compound(tag(8, 'id', string('minecraft:oak_leaves')))
        raw = b'\x0a\0\0' + compound(tag(9, 'sections', b'\x0a' + struct.pack('>i', 1) + compound(tag(10, 'block_states', compound(tag(9, 'palette', palette))))))
        self.assertEqual(atlas.parse_chunk(raw)['sections'][0]['block_states']['palette'], [{'': 'minecraft:air'}, {'id': 'minecraft:oak_leaves'}])

    def test_truncated_nbt_and_invalid_packed_palette_fail_clearly(self):
        with self.assertRaisesRegex(ValueError, 'NBT|truncated'):
            atlas.parse_chunk(chunk_nbt()[:-3])
        with self.assertRaisesRegex(ValueError, 'packed|palette'):
            atlas.packed_value(memoryview(bytes(8)), 255, 9, 256)

    def test_dry_ocean_biome_surface_is_land_and_air_is_unavailable(self):
        color = atlas.terrain_color('minecraft:ocean', 'minecraft:grass_block')
        self.assertGreater(color[1], color[2])
        with self.assertRaises(atlas.MissingSurface):
            atlas.surface_at(atlas.parse_chunk(chunk_nbt(block='minecraft:air')), 0, 0)

    def test_compression_bomb_is_bounded(self):
        with self.assertRaisesRegex(ValueError, '16 MiB'):
            atlas.decompress_chunk(zlib.compress(bytes(17 * 1024 * 1024)), 2)

    def test_region_negative_coordinates_and_all_stdlib_compressions(self):
        for compression in (1, 2, 3):
            with self.subTest(compression=compression):
                path = write_region(self.directory, -1, -1, {(-1, -1): chunk_nbt(-1, -1)}, compression)
                with atlas.RegionFile(path) as region:
                    self.assertEqual(region.read_chunk(-1, -1)['xPos'], -1)
                    self.assertIsNone(region.read_chunk(-2, -1))

    def test_external_mcc_payload_is_read(self):
        path = write_region(self.directory, -1, -1, {(-1, -1): chunk_nbt(-1, -1)}, external=True)
        with atlas.RegionFile(path) as region:
            self.assertEqual(region.read_chunk(-1, -1)['Status'], 'minecraft:full')

    def test_region_truncation_fails_clearly(self):
        path = write_region(self.directory, 0, 0, {(0, 0): chunk_nbt()})
        path.write_bytes(path.read_bytes()[:-4096])
        with self.assertRaisesRegex(ValueError, 'region|location|truncated'):
            with atlas.RegionFile(path) as region:
                region.read_chunk(0, 0)

    def test_unpadded_final_sector_is_valid_but_truncated_payload_is_not(self):
        path = write_region(self.directory, 0, 0, {(0, 0): chunk_nbt()})
        data = path.read_bytes()
        end = 8192 + 4 + struct.unpack_from('>I', data, 8192)[0]
        path.write_bytes(data[:end])
        with atlas.RegionFile(path) as region:
            self.assertEqual(region.read_chunk(0, 0)['Status'], 'minecraft:full')
        path.write_bytes(data[:end - 1])
        with self.assertRaisesRegex(ValueError, 'Truncated.*payload'):
            with atlas.RegionFile(path) as region:
                region.read_chunk(0, 0)

    def write_all_small_chunks(self, proto=None):
        regions = {}
        for cz in range(-2, 2):
            for cx in range(-2, 2):
                status = 'minecraft:structure_starts' if (cx, cz) == proto else 'minecraft:full'
                regions.setdefault((cx // 32, cz // 32), {})[(cx, cz)] = chunk_nbt(cx, cz, status=status)
        for (rx, rz), chunks in regions.items():
            write_region(self.directory, rx, rz, chunks)

    def test_all_chunk_verification_blocks_unsampled_protochunk(self):
        self.assertIn('verify_all_chunks', inspect.signature(atlas.render).parameters)
        self.write_all_small_chunks(proto=(0, 0))
        output = self.directory / 'strict.png'
        report = atlas.render(self.directory, output, size=64, resolution=2, verify_all_chunks=True)
        self.assertEqual(report['missing_pixels'], 0)
        self.assertEqual(report['status'], 'incomplete')
        self.assertEqual((report['verified_chunks'], report['expected_chunks'], report['incomplete_chunks']), (15, 16, 1))
        self.assertFalse(output.exists())

    def test_all_chunk_verification_accepts_only_fully_saved_interior(self):
        self.assertIn('verify_all_chunks', inspect.signature(atlas.render).parameters)
        self.write_all_small_chunks()
        report = atlas.render(self.directory, self.directory / 'complete.png', size=64, resolution=2, verify_all_chunks=True)
        self.assertEqual(report['status'], 'passed')
        self.assertEqual((report['verified_chunks'], report['expected_chunks'], report['incomplete_chunks']), (16, 16, 0))
        self.assertTrue(report['verify_all_chunks'])

    def test_all_chunk_verification_blocks_unsampled_missing_chunk(self):
        self.assertIn('verify_all_chunks', inspect.signature(atlas.render).parameters)
        self.write_all_small_chunks()
        # (0, 0) is not sampled by the 2x2 atlas, but is inside the 64-block world.
        path = self.directory / 'r.0.0.mca'
        data = bytearray(path.read_bytes())
        data[:4] = bytes(4)
        path.write_bytes(data)
        report = atlas.render(self.directory, self.directory / 'missing.png', size=64, resolution=2, verify_all_chunks=True)
        self.assertEqual(report['missing_pixels'], 0)
        self.assertEqual((report['status'], report['verified_chunks'], report['incomplete_chunks']), ('incomplete', 15, 1))

    def test_lz4_fails_clearly(self):
        path = write_region(self.directory, 0, 0, {(0, 0): chunk_nbt()})
        data = bytearray(path.read_bytes())
        data[8196] = 4
        path.write_bytes(data)
        with self.assertRaisesRegex(ValueError, 'LZ4'):
            with atlas.RegionFile(path) as region:
                region.read_chunk(0, 0)

    def test_incomplete_atlas_does_not_replace_output(self):
        output = self.directory / 'terrain.png'
        output.write_bytes(b'previous completed atlas')
        report = atlas.render(self.directory, output, size=32, resolution=2)
        self.assertEqual((report['status'], report['missing_pixels'], report['total_pixels']), ('incomplete', 4, 4))
        self.assertEqual(output.read_bytes(), b'previous completed atlas')

    def test_partial_development_output_is_transparent_unknown(self):
        write_region(self.directory, -1, -1, {(-1, -1): chunk_nbt(-1, -1, status='minecraft:structure_starts')})
        output = self.directory / 'partial.png'
        report = atlas.render(self.directory, output, size=32, resolution=2, allow_partial=True)
        dimensions, scanlines = read_png(output)
        self.assertEqual(dimensions[:4], (2, 2, 8, 6))
        self.assertEqual(scanlines, bytes(18))
        self.assertEqual(report['status_counts'], {'minecraft:structure_starts': 1})

    def test_north_up_world_projection_and_png_crc(self):
        for cx, cz, block in [(-1, -1, 'minecraft:water'), (0, -1, 'minecraft:sand'),
                              (-1, 0, 'minecraft:snow_block'), (0, 0, 'minecraft:grass_block')]:
            write_region(self.directory, cx // 32, cz // 32, {(cx, cz): chunk_nbt(cx, cz, block)})
        output = self.directory / 'terrain.png'
        report = atlas.render(self.directory, output, size=32, resolution=2)
        dimensions, scanlines = read_png(output)
        self.assertEqual(report['status'], 'passed')
        self.assertEqual((report['sampled_pixels'], report['chunks_read']), (4, 4))
        self.assertEqual(dimensions[:4], (2, 2, 8, 6))
        northwest, southwest, southeast = scanlines[1:5], scanlines[10:14], scanlines[14:18]
        self.assertGreater(northwest[2], northwest[0])
        self.assertGreater(min(southwest[:3]), 200)
        self.assertGreater(southeast[1], southeast[0])
        self.assertEqual(northwest[3], 255)


if __name__ == '__main__':
    unittest.main()
