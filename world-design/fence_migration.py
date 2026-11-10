#!/usr/bin/env python3
"""Build an explicit, bounded scan/apply/undo pack for the original 12 habitats.

Input is the ORIGINAL ark_renewal/manifest.json, not world-release.json. This
program only writes a new local output directory. It never contacts a server.
The generated functions have no load/tick hooks and never rebuild a habitat.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re


NAMESPACE = "ark_fence_migration"
STORAGE = NAMESPACE + ":state"
SOURCE_COMMIT = "360677edc18725b0b1673d86b32aafb895c8acc6"
# Frozen original palette, independent of future generator changes.
WOODS = {"plains": "oak", "taiga": "spruce", "desert": "birch", "jungle": "jungle",
         "savanna": "acacia", "swamp": "mangrove", "snow": "spruce", "cherry": "cherry"}
FENCE_PROPERTIES = {name: ("false", "true") for name in ("east", "north", "south", "west", "waterlogged")}
GATE_PROPERTIES = {"facing": ("north", "east", "south", "west"),
                   **{name: ("false", "true") for name in ("in_wall", "open", "powered")}}


def encoded(value):
    return json.dumps(value, ensure_ascii=True, separators=(",", ":"), sort_keys=True)


def build_plan(manifest, dimension):
    """Reject incomplete/ambiguous input and describe every possible changed block."""
    if not isinstance(dimension, str) or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", dimension):
        raise ValueError("An explicit valid Minecraft dimension resource ID is required")
    if (not isinstance(manifest, dict) or manifest.get("namespace") != "ark_renewal"
            or manifest.get("format") != 1 or not isinstance(manifest.get("sites"), list)):
        raise ValueError("Use the original ark_renewal/manifest.json with namespace, format=1 and sites")
    if any(not isinstance(site, dict) for site in manifest["sites"]):
        raise ValueError("Invalid site record")
    originals = [site for site in manifest["sites"] if site.get("kind") == "habitat"]
    expected = {f"habitat_{index:02}" for index in range(1, 13)}
    if len(originals) != 12 or {site.get("id") for site in originals} != expected:
        raise ValueError("Exactly habitat_01 through habitat_12 from the original world are required")
    sites = []
    for source in sorted(originals, key=lambda item: item["id"]):
        identity, theme = source["id"], source.get("theme")
        if theme not in WOODS or any(type(source.get(axis)) is not int for axis in ("x", "y", "z")):
            raise ValueError("Original resolved theme and integer x/y/z required for " + identity)
        x, y, z = (source[axis] for axis in ("x", "y", "z"))
        if not -49 <= y <= 285 or min(x - 18, z - 18) < -10240 or max(x + 18, z + 18) > 10239:
            raise ValueError("Habitat exceeds the original world/height bounds: " + identity)
        footprint = [x - 18, z - 18, x + 18, z + 18]
        marker = "ark_renewal_" + identity
        if (source.get("radius", 18) != 18 or source.get("footprint", footprint) != footprint
                or source.get("ready_marker", marker) != marker):
            raise ValueError("Habitat geometry or readiness marker differs from the original generator: " + identity)
        if any(footprint[0] <= other["footprint"][2] + 4 and footprint[2] >= other["footprint"][0] - 4
               and footprint[1] <= other["footprint"][3] + 4 and footprint[3] >= other["footprint"][1] - 4 for other in sites):
            raise ValueError("Habitat footprints overlap or violate the original spacing")
        perimeter = {(x + dx, y + 1, z + dz) for dx in range(-16, 17) for dz in (-16, 16)}
        perimeter.update((x + dx, y + 1, z + dz) for dx in (-16, 16) for dz in range(-16, 17))
        lamps = {(x - 16, y + 1, z - 16), (x + 16, y + 1, z - 16)}
        gates = {(x + dx, y + 1, z + 16) for dx in (-1, 0, 1)}
        wood = WOODS[theme]
        targets = [{"slot": f"b{index:03}", "position": list(position),
                    "kind": "gate" if position in gates else "fence",
                    "material": "minecraft:" + wood + ("_fence_gate" if position in gates else "_fence")}
                   for index, position in enumerate(sorted(perimeter - lamps))]
        chunks = [[cx * 16, cz * 16] for cx in range((x - 16) // 16, (x + 16) // 16 + 1)
                  for cz in range((z - 16) // 16, (z + 16) // 16 + 1)]
        sites.append({"id": identity, "center": [x, y, z], "theme": theme, "wood": wood,
                      "footprint": footprint, "ready_marker": marker, "targets": targets,
                      "preserved_lamp_bases": [list(p) for p in sorted(lamps)], "chunks": chunks})
    plan = {"format": 1, "namespace": NAMESPACE, "dimension": dimension,
            "original_generator_commit": SOURCE_COMMIT,
            "manifest_sha256": hashlib.sha256(encoded(manifest).encode()).hexdigest(),
            "maximum_changes": sum(len(site["targets"]) for site in sites), "sites": sites,
            "support_guard": "Only targets with air immediately above and original grass_block immediately below are eligible.",
            "identity_limit": "Position and original material cannot distinguish a later identical player replacement.",
            "snapshot": "Actual properties are captured at scan time; changed matches or vertical supports abort apply before any block mutation."}
    plan["plan_sha256"] = hashlib.sha256(encoded(plan).encode()).hexdigest()
    return plan


def _common_functions():
    """Macros only consume generated arguments or this pack's captured properties."""
    functions = {}
    path = f"sites.$(site).blocks.$(slot)"
    position = "$(x) $(y) $(z)"
    support_guard = f"positioned {position} if block ~ ~1 ~ minecraft:air if block ~ ~-1 ~ minecraft:grass_block"
    for kind, properties in (("fence", FENCE_PROPERTIES), ("gate", GATE_PROPERTIES)):
        defaults = {name: values[0] for name, values in properties.items()}
        record = ('{site:"$(site)",slot:"$(slot)",material:"$(material)",x:$(x),y:$(y),z:$(z),'
                  'after:"minecraft:air",removed:0b,restored:0b,' +
                  ",".join(name + ":" + encoded(value) for name, value in defaults.items()) + "}")
        capture = [f"$data modify storage {STORAGE} {path} set value {record}"]
        for name, values in properties.items():
            for value in values[1:]:
                capture.append(f"$execute if block {position} $(material)[{name}={value}] run data modify storage {STORAGE} {path}.{name} set value {encoded(value)}")
        if kind == "fence":
            capture.append(f'$execute if block {position} $(material)[waterlogged=true] run data modify storage {STORAGE} {path}.after set value "minecraft:water[level=0]"')
        capture.append(f'$data modify storage {STORAGE} sites.$(site).matches append value "$(slot)"')
        functions["internal/capture_" + kind] = capture
        state = "$(material)[" + ",".join(name + "=$(" + name + ")" for name in properties) + "]"
        conflict = f'data modify storage {STORAGE} sites.$(site).conflicts append value "$(slot)"'
        functions["internal/check_" + kind] = [
            f"$execute unless block {position} {state} run {conflict}",
            f"$execute if block {position} {state} positioned {position} unless block ~ ~1 ~ minecraft:air run {conflict}",
            f"$execute if block {position} {state} positioned {position} if block ~ ~1 ~ minecraft:air unless block ~ ~-1 ~ minecraft:grass_block run {conflict}"]
        functions["internal/undo_" + kind] = [
            f"$data modify storage {STORAGE} {path}.restored set value 0b",
            f"$execute if block {position} $(after) {support_guard} store success storage {STORAGE} {path}.restored byte 1 run setblock {position} {state} replace",
            f'$execute if data storage {STORAGE} {path}{{restored:1b}} run data modify storage {STORAGE} sites.$(site).restored append value "$(slot)"',
            f"$execute if data storage {STORAGE} {path}{{restored:1b}} run data modify storage {STORAGE} {path}.removed set value 0b",
            f'$execute if data storage {STORAGE} {path}{{removed:1b}} run data modify storage {STORAGE} sites.$(site).pending append value "$(slot)"',
        ]
    functions["internal/remove"] = [
        # Preflight compares ALL captured states first. Removing a neighbor can
        # legitimately update fence connections, so mutation checks material and
        # the vertical support guard instead of the old connection properties.
        f"$execute if block {position} $(material) {support_guard} store success storage {STORAGE} {path}.removed byte 1 run setblock {position} $(after) replace",
        f'$execute if data storage {STORAGE} {path}{{removed:1b}} run data modify storage {STORAGE} sites.$(site).removed append value "$(slot)"',
        f'$execute unless data storage {STORAGE} {path}{{removed:1b}} run data modify storage {STORAGE} sites.$(site).errors append value "$(slot)"',
    ]
    return functions


def generate_functions(plan):
    functions = _common_functions()
    digest = encoded(plan["plan_sha256"])
    binding = [f"execute if data storage {STORAGE} plan unless data storage {STORAGE} {{plan:{digest}}} run return fail"]
    initialize = [f"execute unless data storage {STORAGE} plan run data modify storage {STORAGE} plan set value {digest}"]
    for site in plan["sites"]:
        identity, (x, y, z) = site["id"], site["center"]
        path = "sites." + identity
        ready = [f'execute unless entity @e[type=minecraft:marker,tag={site["ready_marker"]},x={x + .5},y={y + 1},z={z + .5},distance=..1,limit=1] run return fail']
        loaded = [f"execute unless loaded {cx} {y + 1} {cz} run return fail" for cx, cz in site["chunks"]]

        def count(field, array):
            return f"execute store result storage {STORAGE} {path}.summary.{field} int 1 run data get storage {STORAGE} {path}.{array}"

        scan = binding + loaded + ready + [f"execute if data storage {STORAGE} {path} run return 0"] + initialize + [
            f'data modify storage {STORAGE} {path} set value {{summary:{{phase:"scanning",matched:0,protected:0,removed:0,remaining:0,conflicts:0}},blocks:{{}},matches:[],protected:[],removed:[],errors:[],conflicts:[],pending:[],restored:[]}}']
        apply = binding + loaded + ready + [
            f'execute unless data storage {STORAGE} {path}.summary{{phase:"scanned"}} run return 0',
            f"data modify storage {STORAGE} {path}.conflicts set value []"]
        undo = binding + loaded + [
            f'execute unless data storage {STORAGE} {path}.summary{{phase:"applied"}} unless data storage {STORAGE} {path}.summary{{phase:"partial"}} unless data storage {STORAGE} {path}.summary{{phase:"undo_partial"}} run return 0',
            f"data modify storage {STORAGE} {path}.pending set value []",
            f"data modify storage {STORAGE} {path}.restored set value []"]
        removals = []
        for target in site["targets"]:
            tx, ty, tz = target["position"]
            block = f"{tx} {ty} {tz}"
            slot, kind = target["slot"], target["kind"]
            record = path + ".blocks." + slot
            arguments = {"site": identity, "slot": slot, "material": target["material"], "x": tx, "y": ty, "z": tz}
            match = f'execute if block {block} {target["material"]}'
            above, below = f"{tx} {ty + 1} {tz}", f"{tx} {ty - 1} {tz}"
            protect = f'data modify storage {STORAGE} {path}.protected append value "{slot}"'
            scan += [f"{match} unless block {above} minecraft:air run {protect}",
                     f"{match} if block {above} minecraft:air unless block {below} minecraft:grass_block run {protect}",
                     f"{match} if block {above} minecraft:air if block {below} minecraft:grass_block run function {NAMESPACE}:internal/capture_{kind} " + encoded(arguments)]
            apply.append(f"execute if data storage {STORAGE} {record} run function {NAMESPACE}:internal/check_{kind} with storage {STORAGE} {record}")
            removals.append(f"execute if data storage {STORAGE} {record} run function {NAMESPACE}:internal/remove with storage {STORAGE} {record}")
            undo.append(f"execute if data storage {STORAGE} {record}{{removed:1b}} run function {NAMESPACE}:internal/undo_{kind} with storage {STORAGE} {record}")
        scan += [count("matched", "matches"), count("protected", "protected"),
                 f'data modify storage {STORAGE} {path}.summary.phase set value "scanned"']
        apply += [count("conflicts", "conflicts"), f"execute if data storage {STORAGE} {path}.conflicts[0] run return fail"]
        # Mark partial BEFORE the first block write, so an interrupted apply can undo.
        apply += [f'data modify storage {STORAGE} {path}.summary.phase set value "partial"'] + removals + [
            count("removed", "removed"), f'execute unless data storage {STORAGE} {path}.errors[0] run data modify storage {STORAGE} {path}.summary.phase set value "applied"']
        undo += [count("restored_last_run", "restored"), count("remaining", "pending"),
                 f'data modify storage {STORAGE} {path}.summary.phase set value "undo_partial"',
                 f'execute unless data storage {STORAGE} {path}.pending[0] run data modify storage {STORAGE} {path}.summary.phase set value "undone"']
        tickets = "tickets." + identity
        load = binding + initialize + [
            f"execute if data storage {STORAGE} {tickets}{{active:1b}} run return 0",
            f"data modify storage {STORAGE} {tickets} set value {{active:1b,chunks:{{}}}}"]
        release = binding + [f"execute unless data storage {STORAGE} {tickets}{{active:1b}} run return 0"]
        for index, (cx, cz) in enumerate(site["chunks"]):
            ticket = tickets + f".chunks.c{index:02}"
            load += [f"data modify storage {STORAGE} {ticket} set value {{existed:0b,owned:0b}}",
                     f"execute store success storage {STORAGE} {ticket}.existed byte 1 run forceload query {cx} {cz}",
                     f"execute if data storage {STORAGE} {ticket}{{existed:0b}} store success storage {STORAGE} {ticket}.owned byte 1 run forceload add {cx} {cz}"]
            release += [f"execute if data storage {STORAGE} {ticket}{{owned:1b}} run forceload remove {cx} {cz}",
                        f"data modify storage {STORAGE} {ticket}.owned set value 0b"]
        release.append(f"data modify storage {STORAGE} {tickets}.active set value 0b")
        operations = {"scan": scan, "apply": apply, "undo": undo, "load": load, "release": release,
                      "status": binding + [f"data get storage {STORAGE} {path}.summary"]}
        for operation, commands in operations.items():
            internal = f"internal/{operation}/{identity}"
            functions[internal] = commands
            functions[f"{operation}_{identity}"] = [f'execute in {plan["dimension"]} run function {NAMESPACE}:{internal}']
    return functions


def write_pack(destination, manifest, dimension):
    plan = build_plan(manifest, dimension)
    functions = generate_functions(plan)
    destination = Path(destination)
    # An existing output might contain the only copy of an operational backup.
    destination.mkdir(parents=True, exist_ok=False)
    (destination / "pack.mcmeta").write_text(encoded({"pack": {
        "description": "ARK: ausdrueckliche, begrenzte Gehegezaun-Migration",
        "min_format": [121, 0], "max_format": [121, 0]}}) + "\n")
    for name, commands in functions.items():
        target = destination / f"data/{NAMESPACE}/function/{name}.mcfunction"
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text("\n".join(commands) + "\n")
    (destination / "plan.json").write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n")
    return plan


def worldedit_bundle(plan, identity, world, backup_name):
    """Small console batches for installed WorldEdit 7.4.6; never execute them.

WorldEdit's quoted, space-separated masks are intersections (not FAWE's &).
The schematic captures the real 33x1x33 plane; restore masks only permit the
    original fence/gate material at eligible positions into air/source water.
    Air above and the original grass floor below are required before every edit.
    Undo is deliberately split around an explicit asynchronous-load barrier.
"""
    if not re.fullmatch(r"[a-zA-Z0-9_-]{1,64}", world or ""):
        raise ValueError("A simple verified Bukkit world name is required")
    if not re.fullmatch(r"[a-z0-9_]{1,64}", backup_name or ""):
        raise ValueError("A new simple backup name is required")
    site = next((site for site in plan["sites"] if site["id"] == identity), None)
    if site is None:
        raise ValueError("Unknown original habitat")
    x, y, z = site["center"]
    dx, dz = f"abs(x-({x}))", f"abs(z-({z}))"
    bounds = f"(y=={y + 1})*({dx}<=16)*({dz}<=16)"
    perimeter = f"{bounds}*(({dx}==16)+({dz}==16)>0)*(1-(({dx}==16)*(z=={z - 16})))"
    fence_expression = f"={perimeter}*(1-((z=={z + 16})*({dx}<=1)))"
    gate_expression = f"=(y=={y + 1})*(z=={z + 16})*({dx}<=1)"
    fence = f"minecraft:{site['wood']}_fence"
    gate = f"minecraft:{site['wood']}_fence_gate"
    # WorldEdit 7.4.6 OffsetMaskParser: '<' tests y+1, '>' tests y-1.
    support_guard = "<minecraft:air >minecraft:grass_block"
    candidate_fence_mask = f'"{fence} {fence_expression}"'
    candidate_gate_mask = f'"{gate} {gate_expression}"'
    dry_mask = f'"{fence}[waterlogged=false] {support_guard} {fence_expression}"'
    wet_mask = f'"{fence}[waterlogged=true] {support_guard} {fence_expression}"'
    gate_mask = f'"{gate} {support_guard} {gate_expression}"'
    selection = [x - 16, y + 1, z - 16, x + 16, y + 1, z + 16]
    schematic = backup_name + "/" + identity
    bundle = {"site": identity, "dimension": plan["dimension"], "world": world,
              "plan_sha256": plan["plan_sha256"], "maximum_changes": 126,
              "selection": selection, "schematic": schematic + ".schem",
              "clipboard_expectation": {"origin": selection[:3], "size": [33, 1, 33], "blocks": 1089},
              "inspection_counts": ["candidate_fences", "candidate_gates", "eligible_dry_fences", "eligible_wet_fences", "eligible_gates"],
              "verification_counts": ["remaining_fences", "remaining_gates"],
              "masks": {"dry": dry_mask, "wet": wet_mask, "gate": gate_mask},
              "select": [f"worldedit:/world {world}", "worldedit:/sel cuboid",
                         f"worldedit:/pos1 {x - 16},{y + 1},{z - 16}",
                         f"worldedit:/pos2 {x + 16},{y + 1},{z + 16}",
                         "worldedit:/placement pos1", "worldedit:/gmask"],
              "inspect": ["worldedit:/size", "worldedit:/count " + candidate_fence_mask,
                          "worldedit:/count " + candidate_gate_mask, "worldedit:/count " + dry_mask,
                          "worldedit:/count " + wet_mask, "worldedit:/count " + gate_mask],
              "backup": ["worldedit:/copy", "worldedit:/schem save " + schematic + " sponge"],
              "verify_backup": ["worldedit:clearclipboard", "worldedit:/schem load " + schematic + ".schem",
                                "worldedit:/size -c"],
              "apply": ["worldedit:/replace " + wet_mask + " minecraft:water",
                        "worldedit:/replace " + dry_mask + " minecraft:air",
                        "worldedit:/replace " + gate_mask + " minecraft:air"],
              "verify": ["worldedit:/count " + mask for mask in (candidate_fence_mask, candidate_gate_mask)],
              "undo_prepare": ["worldedit:clearclipboard", "worldedit:/schem load " + schematic + ".schem"],
              "undo_verify": ["worldedit:/size -c"],
              "undo_restore": [f'worldedit:/gmask "minecraft:air {support_guard} {fence_expression}"',
                       f"worldedit:/paste -a -o -m {fence}[waterlogged=false]",
                       f'worldedit:/gmask "minecraft:water[level=0] {support_guard} {fence_expression}"',
                       f"worldedit:/paste -a -o -m {fence}[waterlogged=true]",
                       f'worldedit:/gmask "minecraft:air {support_guard} {gate_expression}"',
                       f"worldedit:/paste -a -o -m {gate}"],
              "cleanup": ["worldedit:/gmask", "worldedit:/sel", "worldedit:/world"],
              "limits": ["Select the verified world and region before every batch; only one console operator may use this WorldEdit session.",
                         "Save to a NEW schematic path and verify save/load success before apply. Never overwrite the original backup.",
                         "After undo_prepare, repeat undo_verify until clipboard origin, size and block count match clipboard_expectation; only then run undo_restore.",
                         "Air immediately above and original grass_block immediately below are required; protected positions remain. Candidate counts minus eligible counts identify them.",
                         "Keep players out of the affected chunks during capture/apply; remote commands are not one atomic server tick.",
                         "Do not use unmasked //undo or //paste: they can overwrite subsequent builds."]}
    for key in ("select", "inspect", "backup", "verify_backup", "apply", "verify", "undo_prepare", "undo_verify", "undo_restore", "cleanup"):
        if any(len(command) > 500 or "\n" in command for command in bundle[key]):
            raise ValueError("Generated command exceeds the authenticated console limit")
    return bundle


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="Original ark_renewal/manifest.json")
    parser.add_argument("--dimension", required=True, help="Verified live resource ID; usually minecraft:overworld for ark")
    parser.add_argument("--output", required=True, type=Path, help="New local datapack directory (must not exist)")
    args = parser.parse_args()
    try:
        plan = write_pack(args.output, json.loads(args.manifest.read_text()), args.dimension)
    except (OSError, ValueError, TypeError) as error:
        parser.error(str(error))
    print(f'Prepared {len(plan["sites"])} habitats, at most {plan["maximum_changes"]} matching blocks.')
    print("Plan SHA-256: " + plan["plan_sha256"])
    print("Review plan.json, then install this pack and explicitly run load/scan/status/apply per habitat.")
    print("No server was contacted and no world block was changed.")


if __name__ == "__main__":
    main()
