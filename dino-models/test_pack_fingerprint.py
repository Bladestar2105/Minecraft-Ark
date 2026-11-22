"""Distinguish cache-relevant pack content from ZIP container metadata."""
import tempfile
import unittest
import zipfile
from pathlib import Path
try:
    import pack_fingerprint
except ModuleNotFoundError:
    pack_fingerprint = None


def archive(path, items, date=(2026, 1, 1, 0, 0, 0)):
    with zipfile.ZipFile(path, 'w') as output:
        for name, data in items:
            output.writestr(zipfile.ZipInfo(name, date_time=date), data)


class PackFingerprintTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(pack_fingerprint, "Native packs need canonical content fingerprints")

    def test_timestamp_changes_keep_canonical_content_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b = Path(directory)/'a.zip', Path(directory)/'b.zip'
            archive(a, [('a.json', b'one'), ('b.json', b'two')])
            archive(b, [('a.json', b'one'), ('b.json', b'two')], (2026, 2, 2, 0, 0, 0))
            before, after = pack_fingerprint.fingerprint(a), pack_fingerprint.fingerprint(b)
            self.assertNotEqual(before['zip_sha256'], after['zip_sha256'])
            self.assertEqual(before['canonical_content_sha256'], after['canonical_content_sha256'])
            difference = pack_fingerprint.compare(before, after)
            self.assertTrue(difference['only_zip_timestamps_changed'])
            self.assertEqual(difference['changed_content_entries'], [])

    def test_sorted_content_identity_ignores_zip_order_but_detects_payload_changes(self):
        with tempfile.TemporaryDirectory() as directory:
            a, b, c = (Path(directory)/name for name in ['a.zip','b.zip','c.zip'])
            archive(a, [('a.json', b'one'), ('b.json', b'two')])
            archive(b, [('b.json', b'two'), ('a.json', b'one')])
            archive(c, [('a.json', b'changed'), ('b.json', b'two')])
            before, reordered, changed = map(pack_fingerprint.fingerprint, [a,b,c])
            self.assertEqual(before['canonical_content_sha256'], reordered['canonical_content_sha256'])
            self.assertFalse(pack_fingerprint.compare(before, reordered)['only_zip_timestamps_changed'])
            self.assertEqual(pack_fingerprint.compare(before, changed)['changed_content_entries'], ['a.json'])


if __name__ == '__main__':
    unittest.main()
