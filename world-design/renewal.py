"""One-shot native-world buildings; never installed as a load or tick hook.

generate(sites) returns {function_name: [mcfunction commands]}. write_pack(path,
sites) writes those functions plus manifest.json and returns that manifest. Each
site needs id, kind, x/y/z (y is sampled ground top), theme; biome and habitat mob
are optional. The manifest records absolute footprints, readiness marker tags,
NPC tags/counts, house geometry and exact block-state probes. Run verify_<id>
only while that site's footprint is loaded; its failed probes append to storage
ark_renewal:verification failed. The caller owns Multiverse/WorldGuard settings.
"""
import json
import math
from pathlib import Path
import re


RADII = {"spawn": 28, "village": 36, "habitat": 18, "camp": 16}
VILLAGE_TRADES = (
    (("bread", 6, 1), ("cooked_beef", 3, 6), ("apple", 4, 3)),
    (("iron_pickaxe", 1, 16), ("iron_axe", 1, 16), ("shield", 1, 12)),
    (("torchflower_seeds", 8, 1), ("wheat_seeds", 16, 1), ("pumpkin_seeds", 8, 1)),
    (("map", 1, 4), ("compass", 1, 2), ("paper", 24, 1)),
)
# wall, wood, roof wood, masonry, flowers, villager biome
PALETTES = {
    "plains": ("oak_planks", "oak", "spruce", "stone_bricks", "poppy", "plains"),
    "taiga": ("spruce_planks", "spruce", "spruce", "mossy_cobblestone", "lily_of_the_valley", "taiga"),
    "desert": ("smooth_sandstone", "birch", "birch", "cut_sandstone", "dead_bush", "desert"),
    "jungle": ("jungle_planks", "jungle", "dark_oak", "mossy_stone_bricks", "orange_tulip", "jungle"),
    "savanna": ("acacia_planks", "acacia", "dark_oak", "terracotta", "dandelion", "savanna"),
    "swamp": ("mangrove_planks", "mangrove", "dark_oak", "mud_bricks", "blue_orchid", "swamp"),
    "snow": ("stone_bricks", "spruce", "dark_oak", "polished_andesite", "cornflower", "snow"),
    "cherry": ("cherry_planks", "cherry", "dark_oak", "stone_bricks", "pink_tulip", "plains"),
}


def _prepare(sites):
    result, ids = [], set()
    for source in sites:
        site = dict(source)
        identity, kind = site.get("id", ""), site.get("kind")
        if not isinstance(identity, str) or not re.fullmatch(r"[a-z0-9_]{1,48}", identity) or identity in ids:
            raise ValueError("Site IDs must be unique lowercase resource names")
        if kind not in RADII:
            raise ValueError("Unknown site kind: " + str(kind))
        if any(type(site.get(axis)) is not int for axis in ("x", "y", "z")):
            raise ValueError("Site coordinates must be integers")
        x, y, z, radius = site["x"], site["y"], site["z"], RADII[kind]
        approaches = site.get("approach_heights", {})
        if approaches and (kind not in ("spawn", "village") or set(approaches) != {"north", "east", "south", "west"}
                           or any(type(h) is not int or abs(h-y) > 24 or not -49 <= h <= 285 for h in approaches.values())):
            raise ValueError("Four measured approach heights within 24 blocks of the platform are required")
        extent = radius + (24 if approaches else 0)
        if not -49 <= y <= 285 or min(x - extent, z - extent) < -10240 or max(x + extent, z + extent) > 10239:
            raise ValueError("Site exceeds safe build height or the 20480-block world border")
        footprint = [x - extent, z - extent, x + extent, z + extent]
        if any(footprint[0] <= other["footprint"][2] + 4 and footprint[2] >= other["footprint"][0] - 4 and
               footprint[1] <= other["footprint"][3] + 4 and footprint[3] >= other["footprint"][1] - 4 for other in result):
            raise ValueError("Custom sites overlap or leave less than four blocks between platforms")
        if kind == "habitat" and site.get("mob", "sniffer") not in ("sniffer", "ravager"):
            raise ValueError("Habitats support sniffer or ravager")
        theme = str(site.get("theme", "plains")).lower()
        biome = str(site.get("biome", theme)).removeprefix("minecraft:")
        if theme not in PALETTES:
            theme = next((name for name in PALETTES if name in theme or name in biome), "plains")
            if any(word in biome for word in ("snow", "frozen", "mountain", "peak")):
                theme = "snow"
        site.update(radius=radius, footprint=footprint, theme=theme, houses=[], npcs=[], checks=[], approaches=[],
                    function="site_" + identity, ready_marker="ark_renewal_" + identity,
                    marker_name="ARK erneuert: " + identity)
        ids.add(identity)
        result.append(site)
    if not result:
        raise ValueError("At least one sampled site is required")
    if sum(site["kind"] == "spawn" for site in result) > 1:
        raise ValueError("Only one survival spawn is supported")
    return result


def _geometry(site):
    x, y, z, radius, kind = (site[key] for key in ("x", "y", "z", "radius", "kind"))
    wall, wood, roof, masonry, flower, villager_type = PALETTES[site["theme"]]
    lines = [f'# {site["id"]}: explicit one-shot placement',
             f'execute if entity @e[type=minecraft:marker,tag={site["ready_marker"]}] run return 0']

    def block(px, py, pz, material):
        lines.append(f"setblock {px} {py} {pz} minecraft:{material}")

    def fill(x1, y1, z1, x2, y2, z2, material):
        lo = [min(a, b) for a, b in zip((x1, y1, z1), (x2, y2, z2))]
        hi = [max(a, b) for a, b in zip((x1, y1, z1), (x2, y2, z2))]
        sizes = [hi[i] - lo[i] + 1 for i in range(3)]
        if math.prod(sizes) > 32768:
            axis = sizes.index(max(sizes))
            middle = (lo[axis] + hi[axis]) // 2
            first, second = hi[:], lo[:]
            first[axis], second[axis] = middle, middle + 1
            fill(*lo, *first, material)
            fill(*second, *hi, material)
        else:
            lines.append("fill " + " ".join(map(str, lo + hi)) + " minecraft:" + material)

    def check(px, py, pz, material, part, house=None):
        probe = {"x": px, "y": py, "z": pz, "block": material, "part": part}
        site["checks"].append(probe)
        if house is not None:
            house["checks"].append(probe)

    def sign(px, py, pz, messages):
        block(px, py - 1, pz, masonry)
        text = ",".join("{text:" + json.dumps(message, ensure_ascii=False) + "}" for message in (messages + [""] * 4)[:4])
        block(px, py, pz, wood + "_sign[rotation=0]{front_text:{messages:[" + text + "]},is_waxed:1b}")

    def lamp(px, pz):
        fill(px, y + 1, pz, px, y + 3, pz, wood + "_fence")
        block(px, y + 4, pz, "lantern[hanging=false,waterlogged=false]")

    def frame(px, pz):
        fill(px - 2, y, pz, px + 2, y + 5, pz, "crying_obsidian")
        fill(px - 1, y + 1, pz, px + 1, y + 4, pz, "air")
        block(px, y + 5, pz, "sea_lantern")
        for dx in range(-1, 2):
            for dy in range(1, 5):
                check(px + dx, y + dy, pz, "air", "portal_opening")

    def merchant(role, px, pz, name, profession, trades, trader=False, home=None):
        tag = "ark_renewal_" + site["id"] + "_" + role
        recipes = ",".join('{buy:{id:"minecraft:emerald",count:' + str(cost) + '},sell:{id:"minecraft:' + item + '",count:' + str(count) + '},maxUses:999999,rewardExp:0b,priceMultiplier:0.0f}' for item, count, cost in trades)
        mob = "wandering_trader" if trader else "villager"
        nbt = '{Tags:["ark_renewal_npc","' + tag + '"],CustomName:{text:' + json.dumps(name, ensure_ascii=False) + '},CustomNameVisible:1b,Invulnerable:1b,PersistenceRequired:1b,Offers:{Recipes:[' + recipes + ']}'
        if trader:
            nbt += f',DespawnDelay:2147483647,WanderTarget:{{X:{x},Y:{y + 1},Z:{z}}}'
        else:
            nbt += ',VillagerData:{type:"minecraft:' + villager_type + '",profession:"minecraft:' + profession + '",level:5}'
            if home:
                hx, hy, hz = home
                nbt += ',Brain:{memories:{"minecraft:home":{value:{dimension:"minecraft:overworld",pos:[I;' + f'{hx},{hy},{hz}' + ']}}}}'
        lines.append(f"summon minecraft:{mob} {px + .5} {y + 1} {pz + .5} " + nbt + "}")
        site["npcs"].append({"tag": tag, "mob": mob, "role": role, "name": name,
                             "x": px + .5, "y": y + 1, "z": pz + .5, "trades": [list(t) for t in trades]})

    def house(hx, hz):
        home = {"origin": [hx, y, hz], "width": 9, "depth": 9,
                "door_floor": [hx + 4, y, hz + 8], "bed": [hx + 2, y + 1, hz + 2], "checks": []}
        site["houses"].append(home)
        # Clear the entire roof footprint to sky; tall trees cannot intersect it.
        fill(hx - 1, y + 1, hz - 1, hx + 9, 319, hz + 9, "air")
        fill(hx, y - 3, hz, hx + 8, y - 1, hz + 8, masonry)
        fill(hx, y, hz, hx + 8, y, hz + 8, wood + "_planks")
        fill(hx, y + 1, hz, hx + 8, y + 4, hz + 8, wall)
        fill(hx + 1, y + 1, hz + 1, hx + 7, y + 4, hz + 7, "air")
        for dx in (0, 8):
            for dz in (0, 8):
                fill(hx + dx, y + 1, hz + dz, hx + dx, y + 4, hz + dz, "stripped_" + wood + "_log[axis=y]")
        for dx in range(-1, 10):
            height = 10 - abs(dx - 4)
            # Full blocks seal the attic beneath decorative stairs, including the ridge.
            fill(hx + dx, y + 5, hz - 1, hx + dx, y + height, hz + 9, roof + "_planks")
            top = (roof + "_slab[type=bottom,waterlogged=false]" if dx == 4 else roof +
                   "_stairs[facing=" + ("east" if dx < 4 else "west") + ",half=bottom,shape=straight,waterlogged=false]")
            fill(hx + dx, y + height + 1, hz - 1, hx + dx, y + height + 1, hz + 9, top)
            for dz in range(-1, 10):
                check(hx + dx, y + height, hz + dz, roof + "_planks", "roof_seal", home)
                check(hx + dx, y + height + 1, hz + dz, top, "roof_surface", home)
            if 0 <= dx <= 8:
                for dz in (0, 8):
                    for dy in range(5, height):
                        block(hx + dx, y + dy, hz + dz, wall)
                        check(hx + dx, y + dy, hz + dz, wall, "gable", home)
        for dx in (3, 5):
            for dz in (0, 8):
                block(hx + dx, y + 2, hz + dz, "glass_pane[north=false,east=true,south=false,west=true,waterlogged=false]")
        for dx in (0, 8):
            for dz in (3, 5):
                block(hx + dx, y + 2, hz + dz, "glass_pane[north=true,east=false,south=true,west=false,waterlogged=false]")
        # Upper first keeps its neighbour update valid when the supported lower half arrives.
        for half, dy in (("upper", 2), ("lower", 1)):
            state = wood + "_door[facing=south,half=" + half + ",hinge=left,open=false,powered=false]"
            block(hx + 4, y + dy, hz + 8, state)
            check(hx + 4, y + dy, hz + 8, state, "door", home)
        for part, dz in (("head", 2), ("foot", 3)):
            state = "white_bed[facing=north,occupied=false,part=" + part + "]"
            block(hx + 2, y + 1, hz + dz, state)
            check(hx + 2, y + 1, hz + dz, state, "bed", home)
        block(hx + 6, y + 1, hz + 2, "crafting_table")
        block(hx + 6, y + 1, hz + 3, "furnace[facing=west,lit=false]")
        block(hx + 2, y + 1, hz + 5, "barrel[facing=up,open=false]")
        block(hx + 6, y + 1, hz + 6, wood + "_stairs[facing=west,half=bottom,shape=straight,waterlogged=false]")
        block(hx + 4, y + 4, hz + 4, "lantern[hanging=true,waterlogged=false]")
        check(hx + 4, y + 4, hz + 4, "lantern[hanging=true,waterlogged=false]", "light", home)
        check(hx + 4, y, hz + 4, wood + "_planks", "floor", home)

    fill(x - radius, max(-63, y - 24), z - radius, x + radius, y - 1, z + radius, "stone")
    fill(x - radius, y, z - radius, x + radius, y, z + radius, "grass_block")
    fill(x - radius, y + 1, z - radius, x + radius, y + 18, z + radius, "air")
    for edge in (-radius, radius):
        fill(x - radius, y, z + edge, x + radius, y, z + edge, masonry)
        fill(x + edge, y, z - radius, x + edge, y, z + radius, masonry)

    if kind == "village":
        for dx, dz in ((-25, -25), (17, -25), (-25, 17), (17, 17)):
            house(x + dx, z + dz)
            door_x, _, door_z = site["houses"][-1]["door_floor"]
            road_z = z - 12 if dz < 0 else z + 29
            fill(door_x - 1, y, door_z + 1, door_x + 1, y, road_z, "gravel")
        for dz in (-12, 29):
            fill(x - 30, y, z + dz, x + 30, y, z + dz, "gravel")
        fill(x - 1, y, z - radius, x + 1, y, z + radius, "gravel")
        fill(x - 10, y, z - 6, x + 10, y, z + 7, masonry)
        # Roofed village well and an irrigated kitchen garden.
        fill(x + 5, y, z - 3, x + 9, y + 1, z + 1, masonry)
        fill(x + 6, y + 1, z - 2, x + 8, y + 1, z, "water")
        for dx in (5, 9):
            for dz in (-3, 1):
                fill(x + dx, y + 2, z + dz, x + dx, y + 4, z + dz, wood + "_fence")
        fill(x + 4, y + 5, z - 4, x + 10, y + 5, z + 2, roof + "_slab[type=bottom,waterlogged=false]")
        fill(x - 12, y, z + 8, x - 5, y, z + 15, "farmland[moisture=7]")
        fill(x - 9, y, z + 8, x - 9, y, z + 15, "water")
        for dx in (-12, -11, -10, -8, -7, -6, -5):
            fill(x + dx, y + 1, z + 8, x + dx, y + 1, z + 15, "wheat[age=7]")
        block(x - 7, y + 1, z - 2, "composter[level=0]")
        block(x + 6, y + 1, z + 5, "smithing_table")
        for index, home in enumerate(site["houses"]):
            hx, _, hz = home["origin"]
            merchant("resident_" + str(index), hx + 4, hz + 5,
                     ("Bäuerin", "Werkzeughändler", "Saatguthändler", "Kartografin")[index],
                     ("farmer", "toolsmith", "farmer", "cartographer")[index],
                     VILLAGE_TRADES[index], home=home["bed"])
        for dx, dz in ((-5, -10), (5, -10), (-5, 26), (5, 26), (-30, -10), (30, -10)):
            lamp(x + dx, z + dz)
        sign(x + 4, y + 2, z + 33, ["DORF " + site["id"][:12], "Bewohnte Häuser", "Handel: Rechtsklick", "Wege führen hinaus"])

    elif kind in ("spawn", "creative"):
        creative = kind == "creative"
        fill(x - radius + 1, y, z - radius + 1, x + radius - 1, y, z + radius - 1,
             "quartz_bricks" if creative else "mossy_stone_bricks")
        fill(x - 2, y, z - radius, x + 2, y, z + radius, "polished_andesite")
        fill(x - radius, y, z + 7, x + radius, y, z + 9, "polished_andesite")
        gate_z = z - (6 if creative else 12)
        frame(x, gate_z)
        for dx in (-11, 11) if creative else (-20, 20):
            for dz in (-11, 12) if creative else (-20, 20):
                fill(x + dx - 2, y, z + dz - 2, x + dx + 2, y, z + dz + 2, "grass_block")
                for fx, fz in ((-1, -1), (1, 1), (0, 0)):
                    block(x + dx + fx, y + 1, z + dz + fz, "poppy" if creative else "orange_tulip")
                fill(x + dx - 3, y + 1, z + dz - 3, x + dx + 3, y + 1, z + dz - 3,
                     "flowering_azalea_leaves[persistent=true,distance=1,waterlogged=false]")
        for dx in (-9, 9):
            for dz in (-3, 10):
                lamp(x + dx, z + dz)
        for dx in (-7, 7):
            for dz in (11, 12, 13):
                block(x + dx, y + 1, z + dz, "spruce_stairs[facing=" + ("east" if dx < 0 else "west") + ",half=bottom,shape=straight,waterlogged=false]")
        if creative:
            for dx in (-6, 6):
                for dz in (-9, 6):
                    fill(x + dx, y + 1, z + dz, x + dx, y + 7, z + dz, "quartz_pillar[axis=y]")
            fill(x - 7, y + 8, z - 10, x + 7, y + 8, z + 7, "waxed_cut_copper")
            fill(x - 8, y + 9, z - 11, x + 8, y + 9, z + 8, "waxed_cut_copper_slab[type=bottom,waterlogged=false]")
            for dx in (-4, 4):
                block(x + dx, y + 7, z + 4, "lantern[hanging=true,waterlogged=false]")
            sign(x - 4, y + 2, z + 1, ["KREATIVWELT", "Eigenes Inventar", "Außerhalb bauen", "Rücktor vor dir"])
            sign(x + 4, y + 2, z + 1, ["ZURÜCK ZU ARK", "Durch dieses Tor", "Survival-Inventar", "kommt dort zurück"])
        else:
            # A fountain beside the unobstructed spawn-to-gate axis.
            fill(x + 10, y, z - 1, x + 16, y + 1, z + 5, "stone_bricks")
            fill(x + 11, y + 1, z, x + 15, y + 1, z + 4, "water")
            fill(x + 13, y + 1, z + 2, x + 13, y + 3, z + 2, "chiseled_stone_bricks")
            block(x + 13, y + 4, z + 2, "sea_lantern")
            sign(x - 5, y + 2, z + 4, ["WILLKOMMEN AUF ARK", "Spawn ist geschützt", "Draußen: Survival", "Tod: Items fallen"])
            sign(x + 5, y + 2, z + 4, ["KREATIVWELT", "Tor geradeaus", "Eigenes Inventar", "Survival bleibt hier"])
            sign(x - 5, y + 2, z + 10, ["ABENTEUER", "/quests list", "Dörfer & Händler", "in allen Richtungen"])
            sign(x + 5, y + 2, z + 10, ["DINOS ZÄHMEN", "Fackelliliensamen", "Schleichen + Klick:", "Folgen / Bleiben"])
            merchant("guide", x - 6, z - 3, "Wegkundige Mira", "cartographer", (("compass", 1, 2), ("bread", 8, 1)))
            merchant("seeds", x + 6, z - 3, "Urzeitgärtner", "farmer", (("torchflower_seeds", 8, 1), ("bread", 8, 1)))
            lines += [f"setworldspawn {x} {y + 1} {z + 5} 180 0", "gamerule minecraft:respawn_radius 0"]

    elif kind == "habitat":
        fill(x - 14, y, z - 14, x + 14, y, z + 14, "moss_block")
        for edge in (-16, 16):
            fill(x - 16, y + 1, z + edge, x + 16, y + 1, z + edge, wood + "_fence")
            fill(x + edge, y + 1, z - 16, x + edge, y + 1, z + 16, wood + "_fence")
        for dx in (-1, 0, 1):
            block(x + dx, y + 1, z + 16, wood + "_fence_gate[facing=south,in_wall=false,open=false,powered=false]")
        fill(x + 7, y, z + 4, x + 11, y, z + 8, "water")
        for dx, dz in ((-9, -8), (10, -9), (-9, 9)):
            block(x + dx, y + 1, z + dz, "mossy_cobblestone")
            block(x + dx + 1, y + 1, z + dz, "fern")
        mob = site.get("mob", "sniffer")
        for index, dx in enumerate((-5, 5)):
            tag = "ark_renewal_" + site["id"] + "_dino_" + str(index)
            name = "Wildes Urzeittier" if mob == "sniffer" else "Wilder Urzeitjäger"
            lines.append(f"summon minecraft:{mob} {x + dx + .5} {y + 1} {z + .5} " +
                         '{Tags:["ark_dino","ark_dino_wild","' + tag + '"],PersistenceRequired:1b,CustomName:{text:' + json.dumps(name, ensure_ascii=False) + '}}')
            site["npcs"].append({"tag": tag, "mob": mob, "role": "dino", "name": name,
                                 "x": x + dx + .5, "y": y + 1, "z": z + .5})
        sign(x + 4, y + 2, z + 17, ["URZEIT-REVIER", "Fackelliliensamen", "zähmen per Klick", "Vorsicht: wilde Jäger"])
        for dx in (-16, 16):
            lamp(x + dx, z - 16)

    elif kind == "camp":
        fill(x - 9, y, z - 7, x + 9, y, z + 7, "coarse_dirt")
        for dx in (-6, 6):
            for dz in (-4, 4):
                fill(x + dx, y + 1, z + dz, x + dx, y + 4, z + dz, wood + "_fence")
        for dx in range(-7, 8):
            fill(x + dx, y + 5, z - 5, x + dx, y + 5, z + 5, "orange_wool" if dx % 2 else "white_wool")
        for dx in (-4, 4):
            block(x + dx, y + 1, z + 3, "barrel[facing=up,open=false]")
            block(x + dx, y + 4, z, "lantern[hanging=true,waterlogged=false]")
        merchant("traveler", x - 3, z, "Reisende Samira", "none",
                 (("torchflower_seeds", 8, 1), ("lead", 2, 1), ("bread", 8, 1)), trader=True)
        merchant("cartographer", x + 3, z, "Wanderkartograf", "cartographer",
                 (("compass", 1, 2), ("torch", 16, 1), ("torchflower_seeds", 8, 1)))
        sign(x + 4, y + 2, z + 10, ["REISENDE HÄNDLER", "Handel: Rechtsklick", "Saatgut für Dinos", "Unterwegs auf ARK"])
        lamp(x - 10, z + 8)
        lamp(x + 10, z + 8)

    directions = {"north": (0, -1, "north", "south"), "east": (1, 0, "east", "west"),
                  "south": (0, 1, "south", "north"), "west": (-1, 0, "west", "east")}
    for direction, target in site.get("approach_heights", {}).items():
        dx, dz, outward, inward = directions[direction]
        heights = [y] + [y + round((target-y)*step/24) for step in range(1, 25)]
        approach = {"direction": direction, "steps": []}
        for step in range(1, 25):
            px, pz, py = x+dx*(radius+step), z+dz*(radius+step), heights[step]
            fill(px-abs(dz), max(-63, min(y, target)-24), pz-abs(dx), px+abs(dz), py-1, pz+abs(dx), "stone")
            material = masonry
            if target != y and (heights[step-1] != py or (step < 24 and heights[step+1] != py)):
                material = "stone_brick_stairs[facing=" + (outward if target > y else inward) + ",half=bottom,shape=straight,waterlogged=false]"
            fill(px-abs(dz), py, pz-abs(dx), px+abs(dz), py, pz+abs(dx), material)
            fill(px-abs(dz), py+1, pz-abs(dx), px+abs(dz), py+4, pz+abs(dx), "air")
            approach["steps"].append({"x": px, "y": py, "z": pz})
            check(px, py, pz, material, "approach_floor")
            check(px, py+2, pz, "air", "approach_clearance")
        site["approaches"].append(approach)

    site["npc_count"] = len(site["npcs"])
    lines.append(f'summon minecraft:marker {x + .5} {y + 1} {z + .5} ' +
                 '{Tags:["ark_renewal_ready","' + site["ready_marker"] + '"],CustomName:{text:' + json.dumps(site["marker_name"], ensure_ascii=False) + '}}')
    return lines


def _chunk_guards(footprint, y, function, dimension=""):
    x1, z1, x2, z2 = footprint
    return [f"execute {dimension}unless loaded {cx * 16} {y} {cz * 16} run return run schedule function ark_renewal:{function} 1s replace"
            for cx in range(x1 // 16, x2 // 16 + 1) for cz in range(z1 // 16, z2 // 16 + 1)]


def _assemble(sites):
    sites = _prepare(sites)
    pack = {"build": ["# Run explicitly once after reviewing the sampled-site manifest.",
                      "execute if data storage ark_renewal:state {complete:1b} run return 0",
                      "execute if data storage ark_renewal:state {running:1b} run return 0",
                      "data modify storage ark_renewal:state running set value 1b",
                      "data modify storage ark_renewal:state complete set value 0b",
                      "function ark_renewal:step_000_load"],
            "cleanup": ["# Watchdog: cancel pending stages and remove only this pack's own chunk tickets."]}
    for index, site in enumerate(sites):
        area = " ".join(map(str, site["footprint"]))
        load, place = f"step_{index:03d}_load", f"step_{index:03d}_place"
        pack[load] = ["forceload add " + area, "schedule function ark_renewal:cleanup 120s replace",
                      f"schedule function ark_renewal:{place} 5s replace"]
        pack[place] = _chunk_guards(site["footprint"], site["y"], place)
        pack[place] += [f'function ark_renewal:{site["function"]}', "forceload remove " + area]
        pack[place] += ([f"function ark_renewal:step_{index + 1:03d}_load"] if index + 1 < len(sites) else
                        ["data modify storage ark_renewal:state complete set value 1b",
                         "data modify storage ark_renewal:state running set value 0b", "schedule clear ark_renewal:cleanup"])
        pack["cleanup"] += ["execute in minecraft:overworld run forceload remove " + area,
                            "schedule clear ark_renewal:" + load, "schedule clear ark_renewal:" + place]
        pack[site["function"]] = _geometry(site)
        verification = ["# Load this site's footprint before running these exact-state probes."]
        for probe in site["checks"]:
            position = f'{probe["x"]} {probe["y"]} {probe["z"]}'
            description = f'{site["id"]}:{probe["part"]}:{position}'
            state = probe["block"]
            if probe["part"] in ("door", "bed"):
                material, properties = state[:-1].split("[", 1)
                # Residents may open doors or occupy beds while the build is inspected.
                state = material + "[" + ",".join(p for p in properties.split(",") if p.split("=")[0] not in ("open", "powered", "occupied")) + "]"
            verification.append(f'execute unless block {position} minecraft:{state} run data modify storage ark_renewal:verification failed append value ' + json.dumps(description))
        verification.append(f'execute unless entity @e[type=minecraft:marker,tag={site["ready_marker"]}] run data modify storage ark_renewal:verification failed append value "{site["id"]}:marker"')
        pack["verify_" + site["id"]] = verification

    creative = {"id": "creative", "kind": "creative", "x": 0, "y": -61, "z": 0, "radius": 16,
                "theme": "plains", "footprint": [-16, -16, 16, 16], "houses": [], "npcs": [], "checks": [],
                "ready_marker": "ark_renewal_creative", "marker_name": "ARK erneuert: creative", "function": "creative"}
    pack["creative"] = _geometry(creative)
    pack["creative_build"] = ["execute in minecraft:ark_creative run function ark_renewal:creative_load"]
    pack["creative_load"] = ["execute if entity @e[type=minecraft:marker,tag=ark_renewal_creative] run return 0",
                             "forceload add -16 -16 16 16", "schedule function ark_renewal:creative_cleanup 120s replace",
                             "schedule function ark_renewal:creative_place 5s replace"]
    pack["creative_place"] = _chunk_guards(creative["footprint"], -61, "creative_place", "in minecraft:ark_creative ")
    pack["creative_place"] += ["execute in minecraft:ark_creative run function ark_renewal:creative",
                              "execute in minecraft:ark_creative run forceload remove -16 -16 16 16",
                              "schedule clear ark_renewal:creative_cleanup"]
    pack["creative_cleanup"] = ["execute in minecraft:ark_creative run forceload remove -16 -16 16 16",
                                "schedule clear ark_renewal:creative_place"]
    pack["cleanup"] += ["data modify storage ark_renewal:state running set value 0b"]
    spawn = next((site for site in sites if site["kind"] == "spawn"), None)
    manifest = {"namespace": "ark_renewal", "format": 1, "sites": sites, "creative": creative,
                "creative_portal": [-1, -60, -6, 1, -57, -6], "creative_arrival": [.5, -60, 2.5],
                "survival_portal": ([spawn["x"] - 1, spawn["y"] + 1, spawn["z"] - 12,
                                      spawn["x"] + 1, spawn["y"] + 4, spawn["z"] - 12] if spawn else None),
                "survival_arrival": ([spawn["x"] + .5, spawn["y"] + 1, spawn["z"] + 5.5] if spawn else None)}
    return pack, manifest


def generate(sites):
    return _assemble(sites)[0]


def write_pack(path, sites):
    pack, manifest = _assemble(sites)
    destination = Path(path)
    functions = destination / "data/ark_renewal/function"
    functions.mkdir(parents=True, exist_ok=True)
    (destination / "pack.mcmeta").write_text(json.dumps({"pack": {"description": "ARK — neue Dörfer, Urzeit-Reviere und sichere Tore",
                                                               "min_format": [121, 0], "max_format": [121, 0]}}, ensure_ascii=False) + "\n")
    for name, lines in pack.items():
        (functions / (name + ".mcfunction")).write_text("\n".join(lines) + "\n")
    (destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
    return manifest
