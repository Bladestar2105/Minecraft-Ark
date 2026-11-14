"""The migration must stay within the original habitat fence footprint."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent


def manifest_fixture():
    """Synthetic coordinates, deliberately not claimed as the live world."""
    return {"namespace": "ark_renewal", "format": 1, "sites": [
        {"id": f"habitat_{index:02}", "kind": "habitat", "x": index * 128,
         "y": 64, "z": 0, "theme": "jungle", "radius": 18,
         "footprint": [index * 128 - 18, -18, index * 128 + 18, 18],
         "ready_marker": f"ark_renewal_habitat_{index:02}"}
        for index in range(1, 13)
    ]}


class FenceMigrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = ROOT / "fence_migration.py"
        # A missing implementation is a clear feature failure, not an import error.
        if path.is_file():
            spec = importlib.util.spec_from_file_location("fence_migration", path)
            cls.module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.module)
        else:
            cls.module = None

    def migration(self):
        self.assertIsNotNone(self.module, "The bounded habitat fence migration is missing")
        return self.module

    def test_plan_targets_only_perimeter_at_original_height_and_keeps_lamps(self):
        plan = self.migration().build_plan(manifest_fixture(), "minecraft:overworld")
        self.assertEqual(1512, plan["maximum_changes"])
        self.assertEqual(12, len(plan["sites"]))
        site = plan["sites"][0]
        positions = {tuple(item["position"]): item["material"] for item in site["targets"]}
        self.assertEqual(126, len(positions))
        self.assertEqual("minecraft:jungle_fence", positions[(113, 65, -16)])
        self.assertEqual("minecraft:jungle_fence", positions[(112, 65, 16)])
        self.assertEqual("minecraft:jungle_fence", positions[(144, 65, 16)])
        for x in (127, 128, 129):
            self.assertEqual("minecraft:jungle_fence_gate", positions[(x, 65, 16)])
        for protected in ((112, 65, -16), (144, 65, -16), (112, 66, -16),
                          (128, 65, 0), (111, 65, 0), (112, 66, 0)):
            self.assertNotIn(protected, positions)
        self.assertTrue(all(y == 65 and (x in (112, 144) or z in (-16, 16))
                            for x, y, z in positions))

    def test_each_original_palette_produces_its_own_fence_material(self):
        source = manifest_fixture()
        for site, theme in zip(source["sites"], ("plains", "taiga", "desert", "jungle", "savanna", "swamp", "snow", "cherry")):
            site["theme"] = theme
        plan = self.migration().build_plan(source, "minecraft:overworld")
        self.assertEqual(["oak", "spruce", "birch", "jungle", "acacia", "mangrove", "spruce", "cherry"],
                         [s["wood"] for s in plan["sites"][:8]])

    def test_wrong_or_incomplete_manifest_cannot_create_a_plan(self):
        module = self.migration()
        cases = []
        source = manifest_fixture(); source["sites"].pop(); cases.append(source)
        source = manifest_fixture(); source["sites"][0]["id"] = "habitat_13"; cases.append(source)
        source = manifest_fixture(); source["sites"][0]["theme"] = "unknown"; cases.append(source)
        source = manifest_fixture(); source["sites"][0]["x"] = True; cases.append(source)
        source = manifest_fixture(); source["sites"][0]["y"] = 300; cases.append(source)
        source = manifest_fixture(); source["sites"][0]["footprint"][0] -= 1; cases.append(source)
        source = manifest_fixture(); source["sites"][0]["ready_marker"] = "some_marker"; cases.append(source)
        source = manifest_fixture(); source["sites"][1] = copy.deepcopy(source["sites"][0]); cases.append(source)
        source = manifest_fixture(); source["namespace"] = "different_builder"; cases.append(source)
        for source in cases:
            with self.subTest(source=source):
                with self.assertRaises(ValueError):
                    module.build_plan(source, "minecraft:overworld")
        with self.assertRaises(ValueError):
            module.build_plan(manifest_fixture(), "minecraft:overworld run fill 0 0 0 1 1 1 air")

    def test_overlapping_habitats_are_rejected_even_with_distinct_ids(self):
        source = manifest_fixture()
        source["sites"][1].update(x=129, footprint=[111, -18, 147, 18])
        with self.assertRaises(ValueError):
            self.migration().build_plan(source, "minecraft:overworld")

    def test_plan_fingerprint_binds_snapshot_to_coordinates_and_dimension(self):
        module = self.migration()
        source = manifest_fixture()
        first = module.build_plan(source, "minecraft:overworld")
        self.assertEqual(first, module.build_plan(copy.deepcopy(source), "minecraft:overworld"))
        moved = copy.deepcopy(source)
        moved["sites"][0].update(x=129, footprint=[111, -18, 147, 18])
        self.assertNotEqual(first["plan_sha256"], module.build_plan(moved, "minecraft:overworld")["plan_sha256"])
        self.assertNotEqual(first["plan_sha256"], module.build_plan(source, "minecraft:ark")["plan_sha256"])

    def test_pack_creation_is_local_explicit_and_never_overwrites_an_existing_pack(self):
        module = self.migration()
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / "migration"
            plan = module.write_pack(destination, manifest_fixture(), "minecraft:overworld")
            self.assertEqual(plan, json.loads((destination / "plan.json").read_text()))
            self.assertFalse((destination / "data/minecraft/tags").exists())
            for name in ("scan_habitat_01", "apply_habitat_01", "undo_habitat_01", "status_habitat_01",
                         "load_habitat_01", "release_habitat_01"):
                self.assertTrue((destination / f"data/ark_fence_migration/function/{name}.mcfunction").is_file())
            sentinel = destination / "do-not-overwrite.txt"
            sentinel.write_text("backup")
            with self.assertRaises(FileExistsError):
                module.write_pack(destination, manifest_fixture(), "minecraft:overworld")
            self.assertEqual("backup", sentinel.read_text())

    def test_worldedit_console_plan_fits_console_and_validates_identity(self):
        module = self.migration()
        self.assertTrue(hasattr(module, "worldedit_bundle"), "A console-only migration needs bounded commands and schematic snapshots")
        plan = module.build_plan(manifest_fixture(), "minecraft:overworld")
        bundle = module.worldedit_bundle(plan, "habitat_01", "ark", "fence_test_20261110")
        self.assertEqual(126, bundle["maximum_changes"])
        self.assertEqual([112, 65, -16, 144, 65, 16], bundle["selection"])
        for key in ("select", "inspect", "backup", "verify_backup", "apply", "undo_prepare", "undo_verify", "undo_restore", "verify", "cleanup"):
            self.assertTrue(all(len(command) <= 500 and "\n" not in command for command in bundle[key]))
        with self.assertRaises(ValueError):
            module.worldedit_bundle(plan, "habitat_99", "ark", "fence_test_20261110")
        with self.assertRaises(ValueError):
            module.worldedit_bundle(plan, "habitat_01", "ark run stop", "fence_test_20261110")
        with self.assertRaises(ValueError):
            module.worldedit_bundle(plan, "habitat_01", "ark", "../overwrite")

    def test_worldedit_restore_exposes_a_readiness_barrier_before_any_paste(self):
        module = self.migration()
        plan = module.build_plan(manifest_fixture(), "minecraft:overworld")
        bundle = module.worldedit_bundle(plan, "habitat_01", "ark", "fence_test_20261110")
        self.assertNotIn("undo", bundle, "One undo batch must not combine asynchronous load and paste")
        for phase in ("undo_prepare", "undo_verify", "undo_restore"):
            self.assertIn(phase, bundle)
        self.assertEqual({"origin": [112, 65, -16], "size": [33, 1, 33], "blocks": 1089},
                         bundle["clipboard_expectation"])
        self.assertTrue(bundle["undo_prepare"])
        self.assertTrue(bundle["undo_verify"])
        self.assertFalse(any("/paste" in command for phase in ("undo_prepare", "undo_verify")
                             for command in bundle[phase]))
        self.assertEqual(3, sum("/paste" in command for command in bundle["undo_restore"]))
        self.assertFalse(any("/schem load" in command for command in bundle["undo_restore"]))


if __name__ == "__main__":
    unittest.main()
