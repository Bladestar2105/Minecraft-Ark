import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("jurassic_generate", HERE / "generate.py")
generate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generate)


class DefinitionTests(unittest.TestCase):
    def test_unique_ids_and_safe_border(self):
        config, landmarks = generate.load()
        self.assertEqual(config["border"]["size"], 32768)
        self.assertEqual(len(config["islands"]), 6)
        self.assertEqual(len({s["id"] for s in landmarks["landmarks"]}), len(landmarks["landmarks"]))
        self.assertIn("nublar_gallimimus", {s["id"] for s in landmarks["landmarks"]})

    def test_compilation_deterministic(self):
        config, landmarks = generate.load()
        self.assertEqual(generate.java_source(config, landmarks), generate.java_source(config, landmarks))
        changed = json.loads(json.dumps(config))
        changed["seed"] += 1
        self.assertNotEqual(generate.identity(config, landmarks), generate.identity(changed, landmarks))

    def test_rejects_duplicate_landmark_and_wrong_profile(self):
        config, landmarks = generate.load()
        invalid = json.loads(json.dumps(landmarks))
        invalid["landmarks"].append(invalid["landmarks"][0])
        with self.assertRaises(ValueError):
            generate.validate(config, invalid)
        config["profile"] = "world_2015"
        with self.assertRaises(ValueError):
            generate.validate(config, landmarks)

    def test_manifest_never_claims_generated_from_plan(self):
        config, landmarks = generate.load()
        manifest = generate.manifest(config, landmarks)
        self.assertEqual(manifest["generated_regions"], [])
        self.assertTrue(all(s["status"] == "planned" for s in manifest["landmarks"]))

    def test_reference_receipt_does_not_accept_unbuilt_islands(self):
        config, sites=generate.load()
        receipt={"passed":True,"initial":{"definition_sha256":generate.identity(config,sites),
                 "implementation_sha256":generate.implementation_identity()},
                 "full_chunk_validation":{"status":"passed","verified_chunks":16384,
                     "bounds":[7168,2048,9216,4096]},"inputs":{"plugin_sha256":"a"*64},
                 "archive":{"filename":"dimension.tar.gz","sha256":"b"*64,"bytes":100}}
        result=generate.coverage_manifest(config,sites,receipt)
        self.assertEqual(result["accepted_regions"],[])
        self.assertEqual([s["id"] for s in result["landmarks"] if s["status"]=="generated"],["nublar_gallimimus"])
        self.assertEqual(next(s for s in result["islands"] if s["id"]=="sorna")["status"],"planned")
        receipt["full_chunk_validation"]["verified_chunks"]=16383
        with self.assertRaises(ValueError):
            generate.coverage_manifest(config,sites,receipt)


if __name__ == "__main__":
    unittest.main()
