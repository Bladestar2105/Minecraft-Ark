import hashlib
import io
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
import unittest
from unittest.mock import patch

import fetch_world


def archive(entries):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w:gz") as target:
        for name, value, kind in entries:
            info = tarfile.TarInfo(name)
            info.type = kind
            if kind == tarfile.REGTYPE:
                info.size = len(value)
                target.addfile(info, io.BytesIO(value))
            else:
                info.linkname = value.decode()
                if kind == tarfile.DIRTYPE:
                    info.size = len(value)
                target.addfile(info)
    return output.getvalue()


def fixture(root, entries=None):
    entries = entries or [("data/ark/level.dat", b"world", tarfile.REGTYPE)]
    content = archive(entries)
    parts = [content[i * len(content) // 7:(i + 1) * len(content) // 7] for i in range(7)]
    manifest = {"release_tag": "world-2026-11-06", "base_url": fetch_world.BASE_URL,
                "archive": {"sha256_concatenated": hashlib.sha256(content).hexdigest(),
                            "compressed_bytes": len(content),
                            "expanded_bytes": sum(len(value) for _, value, kind in entries if kind == tarfile.REGTYPE),
                            "file_count": sum(kind == tarfile.REGTYPE for _, _, kind in entries), "parts": []}}
    cache = root / ".cache/world"
    cache.mkdir(parents=True)
    for index, part in enumerate(parts):
        name = f"minecraft-ark-world-2026-11-06.tar.gz.part-{index:03}"
        manifest["archive"]["parts"].append({"filename": name, "bytes": len(part),
                                               "sha256": hashlib.sha256(part).hexdigest()})
        (cache / name).write_bytes(part)
    (root / "world-release.json").write_text(json.dumps(manifest))
    return manifest, parts


class WorldSetupTests(unittest.TestCase):
    def test_cached_split_archive_is_streamed_and_published_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root)
            with patch.object(fetch_world, "urlopen", side_effect=AssertionError("cache should be used")):
                fetch_world.install_world(root)
            self.assertEqual((root / "data/ark/level.dat").read_bytes(), b"world")
            with self.assertRaises(ValueError):
                fetch_world.install_world(root)
            self.assertEqual((root / "data/ark/level.dat").read_bytes(), b"world")

    def test_missing_parts_download_from_fixed_urls_and_cache_verified_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, parts = fixture(root)
            first = root / ".cache/world" / manifest["archive"]["parts"][0]["filename"]
            first.unlink()
            urls = []
            def response(request, timeout):
                urls.append(request.full_url)
                return io.BytesIO(parts[0])
            with patch.object(fetch_world, "urlopen", side_effect=response):
                fetch_world.install_world(root)
            self.assertEqual(urls, [fetch_world.BASE_URL + first.name])
            self.assertEqual(first.read_bytes(), parts[0])

    def test_tampered_or_oversized_download_never_publishes_data(self):
        for value in (b"wrong", b"x" * 1000):
            with self.subTest(value=len(value)), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                manifest, _ = fixture(root)
                first = root / ".cache/world" / manifest["archive"]["parts"][0]["filename"]
                first.unlink()
                with patch.object(fetch_world, "urlopen", return_value=io.BytesIO(value)):
                    with self.assertRaises(ValueError):
                        fetch_world.install_world(root)
                self.assertFalse((root / "data").exists())
                self.assertFalse(first.exists())
                self.assertFalse(list(root.glob(".world-stage-*")))

    def test_rejects_unsafe_entries_without_partial_world(self):
        cases = [("../outside", b"bad", tarfile.REGTYPE),
                 ("data/../outside", b"bad", tarfile.REGTYPE),
                 ("/data/outside", b"bad", tarfile.REGTYPE),
                 ("other/file", b"bad", tarfile.REGTYPE),
                 ("data/link", b"../outside", tarfile.SYMTYPE),
                 ("data/link", b"data/ark/level.dat", tarfile.LNKTYPE),
                 ("data/device", b"", tarfile.CHRTYPE),
                 ("data//alias", b"", tarfile.DIRTYPE),
                 ("data/ark/level.dat", b"duplicate", tarfile.REGTYPE)]
        for entry in cases:
            with self.subTest(entry=entry), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                fixture(root, [("data/ark/level.dat", b"world", tarfile.REGTYPE), entry])
                with self.assertRaises(ValueError):
                    fetch_world.install_world(root)
                self.assertFalse((root / "data").exists())
                self.assertFalse(list(root.glob(".world-stage-*")))
                self.assertTrue((root / ".cache/world").exists())

    def test_rejects_combined_hash_size_count_and_nonfixed_downloads(self):
        for change in (lambda m: m["archive"].update(sha256_concatenated="0" * 64),
                       lambda m: m["archive"].update(expanded_bytes=1),
                       lambda m: m.update(base_url="https://example.org/"),
                       lambda m: m["archive"]["parts"][0].update(filename="../world")):
            with self.subTest(change=change), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                manifest, _ = fixture(root)
                change(manifest)
                (root / "world-release.json").write_text(json.dumps(manifest))
                with self.assertRaises(ValueError):
                    fetch_world.install_world(root)
                self.assertFalse((root / "data").exists())

    def test_symlink_cache_and_existing_installation_are_rejected(self):
        for name in ("data", "secrets", ".env", "panel-state"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                fixture(root)
                (root / name).symlink_to(root / "missing")
                with self.assertRaises(ValueError):
                    fetch_world.install_world(root)
        with tempfile.TemporaryDirectory() as tmp, tempfile.TemporaryDirectory() as outside:
            root = Path(tmp)
            fixture(root)
            shutil.rmtree(root / ".cache")
            (root / ".cache").symlink_to(outside, target_is_directory=True)
            with self.assertRaises(ValueError):
                fetch_world.install_world(root)
            self.assertEqual(list(Path(outside).iterdir()), [])

    def test_file_count_limit_is_enforced(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest, _ = fixture(root, [("data/ark/level.dat", b"world", tarfile.REGTYPE),
                                         ("data/extra", b"extra", tarfile.REGTYPE)])
            manifest["archive"]["file_count"] = 1
            (root / "world-release.json").write_text(json.dumps(manifest))
            with self.assertRaises(ValueError):
                fetch_world.install_world(root)
            self.assertFalse((root / "data").exists())

    def test_cached_part_tampering_and_symlinks_are_never_reused(self):
        for symlink in (False, True):
            with self.subTest(symlink=symlink), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                manifest, _ = fixture(root)
                part = root / ".cache/world" / manifest["archive"]["parts"][0]["filename"]
                if symlink:
                    part.unlink()
                    part.symlink_to(root / "world-release.json")
                else:
                    part.write_bytes(b"corrupt")
                with self.assertRaises(ValueError):
                    fetch_world.install_world(root)
                self.assertFalse((root / "data").exists())

    def test_directory_tar_headers_with_payloads_are_rejected(self):
        # Non-file payloads must not bypass the pinned expanded-byte limit.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            fixture(root, [("data/ark/level.dat", b"world", tarfile.REGTYPE),
                           ("data/large-directory", b"x", tarfile.DIRTYPE)])
            with self.assertRaises(ValueError):
                fetch_world.install_world(root)
