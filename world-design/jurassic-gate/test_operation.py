from hashlib import sha256
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import deploy
from prepare import HERE, functions, map_file, read_map


class SignOperationTests(unittest.TestCase):
    def test_native_map_reproduces_the_live_gzip_bytes(self):
        from sign_maps import build_sign
        _,colors=build_sign(json.loads((HERE/'gate-model.json').read_bytes()))
        self.assertEqual(sha256(map_file(colors[9221101])).hexdigest(),
                         '1f878242294456fe46ee96fa1d09318b072e710e4c9f0c044946f00e4e3ebc6a')

    def test_native_unsigned_palette_roundtrips_in_locked_map(self):
        colors=bytes([84,121,210,0])*4096
        data=read_map(map_file(colors))
        self.assertEqual(data['colors'],colors)
        self.assertEqual((data['scale'],data['locked'],data['trackingPosition']), (0,1,0))
        self.assertEqual(data['dimension'],'minecraft:overworld')

    def test_invalid_map_dimensions_are_rejected(self):
        with self.assertRaises(ValueError):map_file(b'wrong')

    def test_publish_is_append_only_even_for_an_existing_reserved_name(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();source=root/'source';target=root/'9221101.dat'
            source.write_bytes(b'new');target.write_bytes(b'player map')
            with patch.object(deploy.os,'chown'):
                with self.assertRaises(FileExistsError):deploy.publish(source,target,sha256(b'new').hexdigest())
            self.assertEqual(target.read_bytes(),b'player map')
            self.assertEqual(set(root.iterdir()),{source,target})

    def test_publish_new_map_checks_digest_and_does_not_leave_temp(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory).resolve();source=root/'source';target=root/'9221101.dat'
            source.write_bytes(b'new')
            with self.assertRaises(AssertionError):deploy.publish(source,target,'0'*64)
            self.assertFalse(target.exists())
            with patch.object(deploy.os,'chown'):
                deploy.publish(source,target,sha256(b'new').hexdigest())
            self.assertEqual(target.read_bytes(),b'new')
            self.assertEqual(set(root.iterdir()),{source,target})

    def test_occupied_frame_cell_is_rejected_before_functions_exist(self):
        from sign_maps import build_sign
        spec,_=build_sign(json.loads((HERE/'gate-model.json').read_bytes()))
        baseline=json.loads((HERE/'expected-v5.json').read_bytes())
        target=spec['tiles'][0]['position']
        for cell in baseline['cells']:
            if cell[:3]==target:cell[3]='minecraft:stone';break
        else:self.fail('Frame is outside guarded region')
        with self.assertRaises(AssertionError):functions(spec,baseline)


if __name__=='__main__':unittest.main()
