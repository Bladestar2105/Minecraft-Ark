import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

from build_plugins import PATCHES, ROOT


@unittest.skipUnless(os.environ.get("WORLDGUARD_UPSTREAM_JAR") and os.environ.get("JAVA_HOME"),
                     "Set WORLDGUARD_UPSTREAM_JAR and JAVA_HOME (JDK 25) for the native WorldGuard patch")
class OwnedHatchPatchTests(unittest.TestCase):
    def test_only_the_pinned_listener_changes_and_the_bundle_is_reproducible(self):
        item = next(entry for entry in json.loads((ROOT / "dependencies-runtime.json").read_text()) if entry["project"] == "worldguard")
        transformer, bundled = PATCHES[item["patch"]]
        self.assertEqual(item["bundled_path"], bundled)
        upstream = Path(os.environ["WORLDGUARD_UPSTREAM_JAR"])
        self.assertEqual(hashlib.sha256(upstream.read_bytes()).hexdigest(), item["source_sha256"])
        java = Path(os.environ["JAVA_HOME"]) / "bin/java"
        with tempfile.TemporaryDirectory() as temporary:
            for number in range(2):
                target = Path(temporary) / f"derived-{number}.jar"
                subprocess.run([java, ROOT / transformer, upstream, target], check=True, capture_output=True)
                self.assertEqual(hashlib.sha256(target.read_bytes()).hexdigest(), item["sha256"])
            with zipfile.ZipFile(upstream) as original, zipfile.ZipFile(target) as patched:
                changed = {name for name in original.namelist() if original.read(name) != patched.read(name)}
                self.assertEqual(changed, {"com/sk89q/worldguard/bukkit/listener/WorldGuardEntityListener.class"})
                extra = set(patched.namelist()).difference(original.namelist())
                notices = ["README.md", "LICENSE-WorldGuard.txt", "LICENSE-GPL-3.0.txt"]
                self.assertEqual(extra, {"META-INF/ark-owned-egg-hatch/" + name for name in notices})
                for name in notices:
                    self.assertEqual(patched.read("META-INF/ark-owned-egg-hatch/" + name),
                                     (ROOT / transformer).with_name(name).read_bytes())
            rejected = Path(temporary) / "rejected.jar"
            self.assertNotEqual(subprocess.run([java, ROOT / transformer, target, rejected], capture_output=True).returncode, 0)
            self.assertFalse(rejected.exists())
