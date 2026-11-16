import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


@unittest.skipUnless(os.environ.get("QUEST_UPSTREAM_JAR") and os.environ.get("JAVA_HOME"),
                     "Set QUEST_UPSTREAM_JAR and JAVA_HOME (JDK25) for the pinned native transformer regression")
class QuestsMissionPatchTests(unittest.TestCase):
    def test_reproducible_pinned_patch_changes_only_reviewed_classes_and_adds_mit_notice(self):
        item = next(x for x in json.loads((ROOT / "dependencies-runtime.json").read_text()) if x["project"] == "quests")
        source = Path(os.environ["QUEST_UPSTREAM_JAR"])
        self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), item["source_sha256"])
        java = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        transformer = ROOT / "config/quests/upstream-patches/QuestsMissionAvailabilityFix.java"
        with tempfile.TemporaryDirectory() as temporary:
            outputs = [Path(temporary) / (name + ".jar") for name in ("first", "second")]
            for output in outputs:
                subprocess.run([java, str(transformer), str(source), str(output)], check=True, capture_output=True)
                self.assertEqual(hashlib.sha256(output.read_bytes()).hexdigest(), item["sha256"])
            self.assertEqual(outputs[0].read_bytes(), outputs[1].read_bytes())
            with zipfile.ZipFile(source) as original, zipfile.ZipFile(outputs[0]) as patched:
                notice = "META-INF/ark-mission-fix/LICENSE-Quests.txt"
                self.assertEqual(set(patched.namelist()) - set(original.namelist()), {notice})
                self.assertEqual(patched.read(notice), (transformer.parent / "LICENSE-Quests.txt").read_bytes())
                changed = {name for name in original.namelist() if original.read(name) != patched.read(name)}
                self.assertEqual(changed, {"me/pikamug/quests/BukkitQuestsPlugin.class",
                                           "me/pikamug/quests/player/BukkitQuester.class",
                                           "me/pikamug/quests/convo/misc/NpcOfferQuestPrompt.class"})
            # Existing output and unexpected input are refused, without overwriting either file.
            sentinel = Path(temporary) / "sentinel.jar"
            sentinel.write_bytes(b"keep")
            for input_path, output_path in ((source, sentinel), (sentinel, Path(temporary) / "wrong.jar"), (source, source)):
                result = subprocess.run([java, str(transformer), str(input_path), str(output_path)], capture_output=True)
                self.assertNotEqual(result.returncode, 0)
            self.assertEqual(sentinel.read_bytes(), b"keep")
            self.assertFalse((Path(temporary) / "wrong.jar").exists())
            self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(), item["source_sha256"])


if __name__ == "__main__":
    unittest.main()
