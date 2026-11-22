import hashlib
import json
from pathlib import Path
import subprocess
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch


HERE = Path(__file__).resolve().parent


class GateSignTest(unittest.TestCase):
    def test_manifest_hash_survives_windows_text_io_rules(self):
        from sign_maps import build_sign, write_tiles
        spec,colors=build_sign(json.loads((HERE/'gate-model.json').read_bytes()))
        def windows_write(path,text,*args,**kwargs):
            return path.write_bytes(text.replace('\n','\r\n').encode('utf-8'))
        with TemporaryDirectory() as directory, patch.object(Path,'write_text',windows_write):
            write_tiles(Path(directory),spec,colors)
            self.assertEqual(hashlib.sha256((Path(directory)/'manifest.json').read_bytes()).hexdigest(),
                             'a85a75a17cf40f6a5094f68f0cbf1404769a2f15a160f1a28d19bb2283218d8c')

    def test_standard_library_cli_reproduces_the_confirmed_maps(self):
        expected = {}
        for line in (HERE / 'live-sha256.txt').read_text().splitlines():
            digest, name = line.split()
            expected[name] = digest
        self.assertEqual(expected['manifest.json'], 'a85a75a17cf40f6a5094f68f0cbf1404769a2f15a160f1a28d19bb2283218d8c')

        with TemporaryDirectory() as directory:
            output = Path(directory) / 'tiles'
            result = subprocess.run(
                [sys.executable, '-I', '-S', '-B', str(HERE / 'sign_maps.py'), '--output', str(output)],
                cwd=directory, capture_output=True, text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual({p.name for p in output.iterdir()}, set(expected) | {'mosaic.png'})
            for name, digest in expected.items():
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), digest, name)

            manifest = json.loads((output / 'manifest.json').read_bytes())
            self.assertEqual(manifest, json.loads((HERE / 'gate-model.json').read_bytes())['mapSign'])
            self.assertEqual(len(manifest['tiles']), 56)
            canvas = [bytearray(1920) for _ in range(640)]
            for tile in manifest['tiles']:
                colors = (output / tile['file']).read_bytes()
                self.assertEqual(len(colors), 128 * 128)
                self.assertLessEqual(set(colors), {84, 121, 210})
                self.assertTrue(set(colors) - {84})
                x, y = tile['column'] * 128, tile['row'] * 128
                for dy in range(128):
                    canvas[y + dy][x:x + 128] = colors[dy * 128:(dy + 1) * 128]

            # Exclusive bounds of the red faces at 128 pixels per world block.
            for low, high, bounds in [(0, 320, (134, 18, 1786, 274)), (320, 640, (637, 385, 1283, 594))]:
                rows = [(y, canvas[y]) for y in range(low, high) if 210 in canvas[y]]
                self.assertEqual((min(row.find(210) for _, row in rows), rows[0][0],
                                  max(row.rfind(210) for _, row in rows) + 1, rows[-1][0] + 1), bounds)

    def test_map_sign_preserves_v5_gate_plaza_and_clearance(self):
        model = json.loads((HERE / 'gate-model.json').read_bytes())
        sign = model.pop('mapSign')
        self.assertEqual(model['version'], 6)
        model['version'] = 5
        reference = (json.dumps(model, indent=2, ensure_ascii=False) + '\n').encode()
        self.assertEqual(hashlib.sha256(reference).hexdigest(), '555f7ac08ef8e3a3e32d9749fcbca53f1f682dac0d82940c57623b0ba75c349a')
        frozen = json.loads((HERE / 'expected-v5.json').read_bytes())
        cells = {(x, y, z): state for x, y, z, state in frozen['cells']}
        self.assertEqual(len(cells), 5211)
        self.assertEqual(sum((x, 113, z) in cells for x in range(-491, -468) for z in range(-380, -371)), 207)
        passage = [cells[x, y, z] for x in range(-486, -473) for y in range(114, 129) for z in range(-380, -371)]
        self.assertEqual(passage, ['minecraft:air'] * 1755)
        frame_plane = {(x, y, -373) for x in range(-487, -472) for y in range(129, 134)}
        self.assertEqual({cells[pos] for pos in frame_plane}, {'minecraft:air'})
        self.assertEqual(len(frozen['displays']), 10)
        self.assertTrue(all(tuple(tile['position']) in frame_plane for tile in sign['tiles']))


if __name__ == '__main__':
    unittest.main()
