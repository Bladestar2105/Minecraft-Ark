import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import tempfile
import unittest

from init import initialize
from build_plugins import download


class SetupTests(unittest.TestCase):
    def test_fresh_credentials_are_private_and_never_overwritten(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "data/ark").mkdir(parents=True)
            (root / "data/ark/level.dat").write_bytes(b"fixture")
            (root / ".env.example").write_text("EULA=FALSE\n")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                initialize(root)
            credentials = json.loads((root / "secrets/panel.json").read_text())
            password = (root / "secrets/panel-login.txt").read_text().split("Password: ")[1].strip()
            self.assertNotIn(password, output.getvalue())
            method, salt, digest = credentials["password_hash"].split("$")
            self.assertEqual(method, "scrypt:32768:8:1")
            actual = hashlib.scrypt(password.encode(), salt=salt.encode(), n=32768,
                                    r=8, p=1, maxmem=128 * 1024 * 1024, dklen=64).hex()
            self.assertEqual(actual, digest)
            if importlib.util.find_spec("werkzeug"):
                from werkzeug.security import check_password_hash
                self.assertTrue(check_password_hash(credentials["password_hash"], password))
            self.assertEqual((root / "secrets").stat().st_mode & 0o777, 0o700)
            self.assertEqual((root / "secrets/panel-login.txt").stat().st_mode & 0o777, 0o600)
            before = (root / "secrets/panel.json").read_bytes()
            with self.assertRaises(ValueError):
                initialize(root)
            self.assertEqual(before, (root / "secrets/panel.json").read_bytes())
            self.assertIn("online-mode=true", (root / "data/server.properties").read_text())

    def test_world_required_and_cached_artifact_tampering_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            with self.assertRaises(ValueError):
                initialize(root)
            self.assertFalse((root / "secrets").exists())
            (root / "library.jar").write_bytes(b"tampered")
            with self.assertRaises(ValueError):
                download({"name": "library.jar", "sha256": "0" * 64}, root)

    def test_runtime_manifest_preserves_world_enabled_datapack_names(self):
        root = Path(__file__).resolve().parents[1]
        items = json.loads((root / "dependencies-runtime.json").read_text())
        packs = [item["name"] for item in items if item["kind"] == "datapack"]
        self.assertEqual(sorted(packs), ["Dungeons and Taverns v6.1.0.zip", "Geophilic v3.7.dp.zip"])
        self.assertEqual(len(items), len({item["name"] for item in items}))
        for item in items:
            self.assertTrue(item["url"].startswith("https://"))
            self.assertEqual(Path(item["name"]).name, item["name"])
            self.assertTrue("sha256" in item or "sha512" in item)
