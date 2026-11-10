"""Renewal geometry is checked as final block states, including every roof column."""
import importlib.util
import json
from pathlib import Path
import re
import tempfile
import unittest


ROOT = Path(__file__).resolve().parent
SITES = [
    {"id": "spawn", "kind": "spawn", "x": 0, "y": 78, "z": 0, "theme": "jungle"},
    {"id": "village", "kind": "village", "x": 1400, "y": 90, "z": -1900, "theme": "taiga"},
    {"id": "habitat", "kind": "habitat", "x": -3000, "y": 72, "z": 1600, "theme": "jungle"},
    {"id": "camp", "kind": "camp", "x": 2200, "y": 70, "z": 3500, "theme": "desert"},
]


def load_module(test):
    source = ROOT / "renewal.py"
    test.assertTrue(source.exists(), "renewal generator is missing")
    spec = importlib.util.spec_from_file_location("renewal", source)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def block_at(lines, position):
    """Resolve emitted fill/setblock writes without discarding block states."""
    for line in reversed(lines):
        words = line.split()
        if words[:1] == ["setblock"] and tuple(map(int, words[1:4])) == position:
            return words[4].split("{")[0].removeprefix("minecraft:")
        if words[:1] == ["fill"]:
            coords = list(map(int, words[1:7]))
            if all(coords[i] <= position[i] <= coords[i + 3] for i in range(3)):
                return words[7].split("{")[0].removeprefix("minecraft:")
    return None


class RenewalTest(unittest.TestCase):
    def test_measured_cardinal_ramps_connect_raised_platforms_to_terrain(self):
        site = dict(SITES[1], approach_heights={'north': 77, 'east': 98, 'south': 90, 'west': 84})
        pack, manifest = self.pack([site])
        built = manifest['sites'][0]
        self.assertEqual(built['footprint'], [1340, -1960, 1460, -1840])
        self.assertEqual(len(built['approaches']), 4)
        lines = pack[built['function']]
        self.assertEqual(block_at(lines, (1400, 66, -1900)), 'stone')
        for approach in built['approaches']:
            self.assertEqual(len(approach['steps']), 24)
            self.assertEqual(approach['steps'][-1]['y'], site['approach_heights'][approach['direction']])
            heights = [site['y']] + [step['y'] for step in approach['steps']]
            self.assertTrue(all(abs(a-b) <= 1 for a,b in zip(heights, heights[1:])))
            for step in approach['steps']:
                self.assertNotIn(block_at(lines, (step['x'], step['y'], step['z'])), (None, 'air'))
                self.assertEqual(block_at(lines, (step['x'], step['y']+2, step['z'])), 'air')

    def test_runtime_checks_allow_residents_to_open_doors_and_use_beds(self):
        pack, _ = self.pack()
        checks = pack['verify_village']
        doors = [line for line in checks if '_door[' in line]
        beds = [line for line in checks if '_bed[' in line]
        self.assertEqual(len(doors), 8)
        self.assertEqual(len(beds), 8)
        self.assertTrue(all('half=' in line and 'facing=south' in line and 'open=' not in line for line in doors))
        self.assertTrue(all('part=' in line and 'facing=north' in line and 'occupied=' not in line for line in beds))

    def pack(self, sites=SITES):
        renewal = load_module(self)
        with tempfile.TemporaryDirectory() as directory:
            manifest = renewal.write_pack(directory, sites)
            self.assertEqual(json.loads((Path(directory) / "manifest.json").read_text()), manifest)
            self.assertFalse((Path(directory) / "data/minecraft").exists())
            self.assertEqual(json.loads((Path(directory) / "pack.mcmeta").read_text())["pack"]["min_format"], [121, 0])
        return renewal.generate(sites), manifest

    def test_every_house_has_sealed_roof_columns_gables_doors_and_beds(self):
        pack, manifest = self.pack()
        village = next(s for s in manifest["sites"] if s["id"] == "village")
        self.assertEqual(len(village["houses"]), 4)
        lines = pack[village["function"]]
        for house in village["houses"]:
            x, y, z = house["origin"]
            self.assertEqual((house["width"], house["depth"]), (9, 9))
            probes = {(p["x"], p["y"], p["z"]): p["block"] for p in house["checks"]}
            # Hand-checked gable profile, including both overhangs and the ridge.
            for dx, height in zip(range(-1, 10), (5, 6, 7, 8, 9, 10, 9, 8, 7, 6, 5)):
                for dz in range(-1, 10):
                    pos = (x + dx, y + height, z + dz)
                    self.assertEqual(block_at(lines, pos), "spruce_planks", pos)
                    self.assertEqual(probes[pos], "spruce_planks", pos)
                    top = block_at(lines, (x + dx, y + height + 1, z + dz))
                    self.assertEqual(top, "spruce_slab[type=bottom,waterlogged=false]" if dx == 4 else
                                     "spruce_stairs[facing=" + ("east" if dx < 4 else "west") + ",half=bottom,shape=straight,waterlogged=false]")
                if 0 <= dx <= 8:
                    for dz in (0, 8):
                        for dy in range(5, height):
                            pos = (x + dx, y + dy, z + dz)
                            self.assertEqual(block_at(lines, pos), "spruce_planks", pos)
                            self.assertEqual(probes[pos], "spruce_planks", pos)
            for half, dy in (("lower", 1), ("upper", 2)):
                pos = (x + 4, y + dy, z + 8)
                state = "spruce_door[facing=south,half=" + half + ",hinge=left,open=false,powered=false]"
                self.assertEqual(block_at(lines, pos), state)
                self.assertEqual(probes[pos], state)
            self.assertEqual(block_at(lines, (x + 4, y + 3, z + 8)), "spruce_planks")
            self.assertEqual(block_at(lines, (x + 4, y + 1, z + 7)), "air")
            self.assertEqual(block_at(lines, (x + 4, y + 1, z + 9)), "air")
            for part, dz in (("head", 2), ("foot", 3)):
                self.assertEqual(block_at(lines, (x + 2, y + 1, z + dz)),
                                 "white_bed[facing=north,occupied=false,part=" + part + "]")
            self.assertEqual(block_at(lines, (x + 1, y + 1, z + 1)), "air")
            self.assertEqual(block_at(lines, (x + 3, y + 2, z)), "glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]")
            self.assertEqual(block_at(lines, (x + 4, y + 4, z + 4)), "lantern[hanging=true,waterlogged=false]")
            # Tree removal reaches world height over the complete roof footprint.
            self.assertEqual(block_at(lines, (x - 1, 319, z + 9)), "air")

    def test_spawn_and_creative_arrivals_have_clear_supported_routes(self):
        pack, manifest = self.pack()
        spawn = next(s for s in manifest["sites"] if s["kind"] == "spawn")
        self.assertEqual(manifest["survival_arrival"], [0.5, 79, 5.5])
        self.assertEqual(manifest["survival_portal"], [-1, 79, -12, 1, 82, -12])
        self.assertEqual(manifest["creative_arrival"], [0.5, -60, 2.5])
        self.assertEqual(manifest["creative_portal"], [-1, -60, -6, 1, -57, -6])
        for lines, y, start, end in ((pack[spawn["function"]], 78, -12, 6), (pack["creative"], -61, -6, 5)):
            for z in range(start, end + 1):
                self.assertNotIn(block_at(lines, (0, y, z)), (None, "air", "water"))
                for dy in (1, 2, 3):
                    self.assertEqual(block_at(lines, (0, y + dy, z)), "air", (y, z, dy))
        self.assertIn("execute in minecraft:ark_creative run function ark_renewal:creative_load", pack["creative_build"])
        self.assertTrue(any("setworldspawn 0 79 5" in line for line in pack[spawn["function"]]))
        self.assertIn("Tod: Items fallen", "\n".join(pack[spawn["function"]]))
        self.assertNotIn("keepInventory: an", "\n".join(pack[spawn["function"]]))

    def test_build_has_no_startup_hook_and_releases_every_staged_load(self):
        pack, manifest = self.pack()
        self.assertTrue(any("{complete:1b}" in line and "return 0" in line for line in pack["build"]))
        self.assertTrue(any("{running:1b}" in line and "return 0" in line for line in pack["build"]))
        for index, site in enumerate(manifest["sites"]):
            load, place = pack[f"step_{index:03d}_load"], pack[f"step_{index:03d}_place"]
            self.assertIn("forceload add " + " ".join(map(str, site["footprint"])), load)
            self.assertIn("forceload remove " + " ".join(map(str, site["footprint"])), place)
            self.assertTrue(any("schedule function ark_renewal:cleanup" in line for line in load))
            self.assertTrue(any("forceload remove " + " ".join(map(str, site["footprint"])) in line for line in pack["cleanup"]))
            self.assertTrue(any("ark_renewal_" + site["id"] in line and "return 0" in line for line in pack[site["function"]]))
        for lines in pack.values():
            for line in lines:
                words = line.split()
                if words[:1] == ["fill"]:
                    c = list(map(int, words[1:7]))
                    self.assertLessEqual((c[3] - c[0] + 1) * (c[4] - c[1] + 1) * (c[5] - c[2] + 1), 32768, line)
                    self.assertTrue(-64 <= c[1] <= c[4] <= 319, line)
        self.assertFalse(any("remove all" in line for line in pack["cleanup"]))

    def test_npcs_persist_with_ai_and_dinos_are_tagged(self):
        pack, manifest = self.pack()
        for site in manifest["sites"]:
            lines = pack[site["function"]]
            summons = [line for line in lines if line.startswith("summon ") and "minecraft:marker " not in line]
            self.assertEqual(len(summons), len(site["npcs"]))
            for command in summons:
                self.assertNotIn("NoAI:1", command)
                self.assertIn("PersistenceRequired:1b", command)
            for npc in site["npcs"]:
                self.assertIn(npc["tag"], "\n".join(summons))
            if site["kind"] == "habitat":
                self.assertEqual(len(summons), 2)
                self.assertTrue(all("ark_dino" in command and "ark_dino_wild" in command for command in summons))
            if site["kind"] == "camp":
                self.assertTrue(any("minecraft:wandering_trader" in line and "DespawnDelay:2147483647" in line for line in summons))

    def test_village_merchants_sell_distinct_role_appropriate_stock(self):
        pack, manifest = self.pack()
        required = {"Bäuerin": {"bread"}, "Werkzeughändler": {"iron_pickaxe", "iron_axe"},
                    "Saatguthändler": {"torchflower_seeds"}, "Kartografin": {"map", "compass"}}
        for site in manifest["sites"]:
            if site["kind"] != "village":
                continue
            merchants = {npc["name"]: npc for npc in site["npcs"]}
            self.assertEqual(set(merchants), set(required))
            inventories = []
            for name, items in required.items():
                with self.subTest(site=site["id"], merchant=name):
                    npc = merchants[name]
                    sold = frozenset(trade[0] for trade in npc["trades"])
                    inventories.append(sold)
                    self.assertTrue(items <= sold, (name, sold))
                    if name == "Werkzeughändler":
                        self.assertNotIn("torchflower_seeds", sold)
                    summons = [line for line in pack[site["function"]]
                               if line.startswith("summon minecraft:villager ") and '"' + npc["tag"] + '"' in line]
                    self.assertEqual(len(summons), 1)
                    trades = re.findall(r'buy:\{id:"minecraft:emerald",count:(\d+)\},sell:\{id:"minecraft:([^"]+)",count:(\d+)\}', summons[0])
                    self.assertEqual([[item, int(count), int(cost)] for cost, item, count in trades], npc["trades"])
            self.assertEqual(len(set(inventories)), len(required))

    def test_every_biome_palette_preserves_all_manifest_checks(self):
        palettes = (("plains", "oak_planks", "spruce"), ("taiga", "spruce_planks", "spruce"),
                    ("desert", "smooth_sandstone", "birch"), ("jungle", "jungle_planks", "dark_oak"),
                    ("savanna", "acacia_planks", "dark_oak"), ("swamp", "mangrove_planks", "dark_oak"),
                    ("snow", "stone_bricks", "dark_oak"), ("cherry", "cherry_planks", "dark_oak"))
        renewal = load_module(self)
        for theme, wall, roof in palettes:
            with self.subTest(theme=theme), tempfile.TemporaryDirectory() as directory:
                sites = [dict(SITES[1], theme=theme)]
                pack = renewal.generate(sites)
                manifest = renewal.write_pack(directory, sites)
                site = manifest["sites"][0]
                for check in site["checks"]:
                    self.assertEqual(block_at(pack[site["function"]], (check["x"], check["y"], check["z"])), check["block"])
                for house in site["houses"]:
                    x, y, z = house["origin"]
                    self.assertEqual(block_at(pack[site["function"]], (x + 4, y + 9, z)), wall)
                    self.assertEqual(block_at(pack[site["function"]], (x + 4, y + 10, z + 4)), roof + "_planks")

    def test_ravager_habitat_is_explicit_and_unknown_mobs_are_rejected(self):
        renewal = load_module(self)
        pack = renewal.generate([dict(SITES[2], mob="ravager")])
        dinos = [line for line in pack["site_habitat"] if line.startswith("summon minecraft:ravager ")]
        self.assertEqual(len(dinos), 2)
        self.assertTrue(all('"ark_dino_wild"' in line for line in dinos))
        with self.assertRaises(ValueError):
            renewal.generate([dict(SITES[2], mob="wither")])

    def test_placement_waits_for_every_chunk_not_only_a_fixed_timer(self):
        pack, manifest = self.pack()
        for index, site in enumerate(manifest["sites"]):
            name = f"step_{index:03d}_place"
            commands = pack[name]
            geometry = commands.index("function ark_renewal:" + site["function"])
            guards = [line.split() for line in commands[:geometry] if line.startswith("execute unless loaded ")]
            chunks = {(int(g[3]) // 16, int(g[5]) // 16) for g in guards}
            min_x, min_z, max_x, max_z = site["footprint"]
            expected = {(cx, cz) for cx in range(min_x // 16, max_x // 16 + 1)
                        for cz in range(min_z // 16, max_z // 16 + 1)}
            self.assertEqual(chunks, expected)
            self.assertTrue(all(g[6:] == ["run", "return", "run", "schedule", "function", "ark_renewal:" + name, "1s", "replace"] for g in guards))

    def test_rejects_overlap_ids_height_and_world_border_before_writing(self):
        renewal = load_module(self)
        for sites in ([SITES[0], dict(SITES[1], x=10, z=10)],
                      [SITES[0], dict(SITES[1], id="spawn")],
                      [dict(SITES[0], id="../escape")],
                      [dict(SITES[0], y=310)],
                      [dict(SITES[0], x=10239)],
                      [dict(SITES[0], x=True)]):
            with self.subTest(sites=sites), self.assertRaises(ValueError):
                renewal.generate(sites)


if __name__ == "__main__":
    unittest.main()
