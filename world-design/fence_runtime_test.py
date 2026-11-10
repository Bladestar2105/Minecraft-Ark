#!/usr/bin/env python3
"""Exercise the migration on a fresh, loopback-only Paper world; never a live world.

Requires FENCE_PAPER_CACHE, FENCE_PAPER_JAR and JAVA_HOME pointing to an already
available Paper 26.3/Java 25 runtime. No server/download dependency is installed.
"""
import importlib.util
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import struct
import subprocess
import tempfile
import time

from test_fence_migration import manifest_fixture


def main():
    source = Path(__file__).with_name("fence_migration.py")
    assert source.is_file(), "The bounded habitat fence migration is missing"
    spec = importlib.util.spec_from_file_location("fence_migration", source)
    migration = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(migration)
    cache = Path(os.environ["FENCE_PAPER_CACHE"]).resolve()
    jar = Path(os.environ["FENCE_PAPER_JAR"]).resolve()
    java = Path(os.environ["JAVA_HOME"]) / "bin/java"
    output = Path(os.environ.get("FENCE_TEST_OUTPUT", tempfile.gettempdir())).resolve()
    output.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="fence-migration-", dir=output))
    report = {"fixture": str(directory), "checks": [], "commands": []}
    report_path = directory / "report.json"
    server = None
    log = None

    def free_port():
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            return listener.getsockname()[1]

    port, rcon_port = free_port(), free_port()
    password = secrets.token_hex(24)

    def command(text):
        def packet(identity, kind, message):
            body = struct.pack("<ii", identity, kind) + message.encode() + b"\0\0"
            return struct.pack("<i", len(body)) + body

        with socket.create_connection(("127.0.0.1", rcon_port), timeout=10) as connection:
            def receive(count):
                data = b""
                while len(data) < count:
                    chunk = connection.recv(count - len(data))
                    if not chunk:
                        raise RuntimeError("RCON closed before replying")
                    data += chunk
                return data

            def response():
                length = struct.unpack("<i", receive(4))[0]
                assert 10 <= length <= 4 * 1024 * 1024, "Invalid RCON response length"
                data = receive(length)
                return (*struct.unpack("<ii", data[:8]), data[8:-2].decode())

            connection.sendall(packet(1, 3, password))
            while True:
                identity, kind, _ = response()
                assert identity != -1, "Local fixture RCON authentication failed"
                if identity == 1 and kind == 2:
                    break
            connection.sendall(packet(2, 2, text))
            identity, _, value = response()
            assert identity == 2
            value = re.sub(r"§[0-9a-fklmnorx]", "", value)
            report["commands"].append({"command": text, "response": value})
            return value

    def eventually(check, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if check():
                return
            time.sleep(.1)
        raise AssertionError("Timed out waiting for the local fixture")

    def check(name):
        report["checks"].append(name)
        report_path.write_text(json.dumps(report, indent=2) + "\n")
        print("PASS " + name, flush=True)

    def block(position, expected):
        response = command(f'execute if block {position} {expected}')
        assert "Test passed" in response, (position, expected, response)

    def summary():
        # Paper's /data component printer abbreviates larger compounds with ... .
        # Read scalar paths so that no assertion can silently inspect truncated NBT.
        fields = []
        for field in ("phase", "matched", "protected", "removed", "remaining", "conflicts"):
            value = command("data get storage ark_fence_migration:state sites.habitat_01.summary." + field)
            fields.append(field + ": " + value.split("contents: ")[-1])
        return ", ".join(fields)

    def run(action):
        return command(f"function ark_fence_migration:{action}_habitat_01")

    def start():
        nonlocal server, log
        log = (directory / "console.log").open("ab")
        offset = (directory / "console.log").stat().st_size
        # Assertions parse WorldEdit's English console replies on every host locale.
        server = subprocess.Popen([str(java), "-Xms256M", "-Xmx1G", "-Duser.language=en", "-Duser.country=US", "-Dterminal.jline=false",
                                   "-Dterminal.ansi=false", "-jar", "paper.jar", "--nogui"],
                                  cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=log)

        def ready():
            assert server.poll() is None, (directory / "console.log").read_text()[-8000:]
            with (directory / "console.log").open("rb") as content:
                content.seek(offset)
                return b"Done (" in content.read()

        eventually(ready, 90)

    def stop():
        nonlocal server, log
        command("save-all flush")
        server.communicate(b"stop\n", timeout=30)
        assert server.returncode == 0
        server = None
        log.close()
        log = None

    try:
        for name in ("libraries", "versions", "cache"):
            (directory / name).symlink_to(cache / name, target_is_directory=True)
        shutil.copyfile(jar, directory / "paper.jar")
        if os.environ.get("FENCE_WORLDEDIT_JAR"):
            (directory / "plugins").mkdir()
            shutil.copyfile(os.environ["FENCE_WORLDEDIT_JAR"], directory / "plugins/WorldEdit.jar")
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=ark\n"
            "level-type=minecraft:flat\ngenerator-settings={\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1},"
            "{\"block\":\"minecraft:dirt\",\"height\":2},{\"block\":\"minecraft:grass_block\",\"height\":1}],"
            "\"biome\":\"minecraft:plains\"}\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n"
            f"spawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
            "enforce-secure-profile=false\n")
        plan = migration.write_pack(directory / "ark/datapacks/fence_migration", manifest_fixture(), "minecraft:overworld")
        start()
        assert "fence_migration" in command("datapack list enabled")
        assert "scanned" not in run("status")
        run("scan")
        assert 'phase: "scanned"' not in summary()
        check("Unloaded habitat refuses capture instead of creating an empty backup")
        command("forceload add 112 -16")
        run("load")
        eventually(lambda: "Test passed" in command("execute if loaded 144 65 16"))
        run("scan")
        assert "scanned" not in summary()
        check("Missing original readiness marker prevents capture")
        # Keep the fixture's original grass floor stable while testing water;
        # deliberately changed floors are exercised explicitly below.
        command("gamerule minecraft:random_tick_speed 0")
        command("fill 110 64 -18 146 64 18 grass_block")
        command("fill 110 65 -18 146 69 18 air")
        for target in plan["sites"][0]["targets"]:
            command("setblock " + " ".join(map(str, target["position"])) + " " + target["material"])
        for x in (112, 144):
            command(f"fill {x} 65 -16 {x} 67 -16 jungle_fence")
            command(f"setblock {x} 68 -16 lantern")
        command("setblock 113 65 -16 gold_block")
        command("setblock 114 65 -16 oak_fence")
        command("setblock 128 65 17 jungle_fence")
        command("setblock 128 65 0 jungle_fence")
        command("setblock 112 66 0 jungle_fence")
        command("setblock 117 66 -16 lantern[hanging=false]")
        block("117 66 -16", "lantern[hanging=false]")
        command("setblock 119 64 -16 lantern[hanging=true]")
        block("119 64 -16", "lantern[hanging=true]")
        # Same-wood replacements in the wrong original slot are not targets.
        command("setblock 120 65 -16 jungle_fence_gate")
        command("setblock 127 65 16 jungle_fence")
        command("setblock 128 65 -16 jungle_fence[waterlogged=true]")
        gate = "jungle_fence_gate[facing=north,in_wall=false,open=true,powered=false]"
        command("setblock 128 65 16 " + gate)
        command('summon marker 128.5 65 0.5 {Tags:["ark_renewal_habitat_01"]}')
        time.sleep(.5)
        run("scan")
        assert 'phase: "scanned"' in summary(), summary()
        assert "matched: 119" in summary() and "protected: 3" in summary(), summary()
        block("128 65 16", gate)
        check("Dry run captures exact material/state matches without removing a block")
        command("setblock 128 65 16 jungle_fence_gate[facing=north,open=false]")
        run("apply")
        assert "conflicts: 1" in summary(), summary()
        block("115 65 -16", "jungle_fence")
        check("Any matched block changed after capture aborts the entire habitat apply")
        command("setblock 128 65 16 " + gate)
        command("setblock 118 66 -16 lantern[hanging=false]")
        command("setblock 121 64 -16 stone")
        run("apply")
        assert "conflicts: 2" in summary(), summary()
        block("115 65 -16", "jungle_fence")
        block("118 66 -16", "lantern[hanging=false]")
        check("New upper support or changed original floor aborts the entire habitat apply")
        command("setblock 118 66 -16 air")
        command("setblock 121 64 -16 grass_block")
        run("apply")
        assert 'phase: "applied"' in summary() and "removed: 119" in summary(), summary()
        block("115 65 -16", "air")
        block("128 65 -16", "water[level=0]")
        block("117 66 -16", "lantern[hanging=false]")
        block("119 64 -16", "lantern[hanging=true]")
        for position, material in (("113 65 -16", "gold_block"), ("114 65 -16", "oak_fence"),
                                   ("128 65 17", "jungle_fence"), ("128 65 0", "jungle_fence"),
                                   ("112 66 0", "jungle_fence"), ("112 65 -16", "jungle_fence"),
                                   ("112 65 0", "jungle_fence"), ("117 65 -16", "jungle_fence"),
                                   ("119 65 -16", "jungle_fence"), ("120 65 -16", "jungle_fence_gate"),
                                   ("127 65 16", "jungle_fence"),
                                   ("144 65 -16", "jungle_fence"), ("112 68 -16", "lantern")):
            block(position, material)
        check("Apply preserves standing/hanging player lanterns, original floor edits, lamps, water and other builds")
        before = summary()
        run("apply"); run("scan")
        assert summary() == before
        check("Repeated apply/capture is idempotent and cannot overwrite the original backup")
        stop(); start()
        eventually(lambda: "Test passed" in command("execute if loaded 144 65 16"))
        assert 'phase: "applied"' in summary()
        block("115 65 -16", "air")
        check("Snapshot and applied state survive a real Paper restart")
        command("setblock 115 65 -16 diamond_block")
        run("undo")
        block("115 65 -16", "diamond_block")
        block("128 65 16", gate)
        block("128 65 -16", "jungle_fence[waterlogged=true]")
        assert 'phase: "undo_partial"' in summary() and "remaining: 1" in summary(), summary()
        run("undo")
        block("115 65 -16", "diamond_block")
        command("setblock 115 65 -16 air")
        run("undo")
        block("115 65 -16", "jungle_fence")
        assert 'phase: "undone"' in summary() and "remaining: 0" in summary(), summary()
        before = summary(); run("undo"); assert summary() == before
        check("Undo restores observed states, skips later builds and safely resumes/idempotently finishes")
        if os.environ.get("FENCE_WORLDEDIT_JAR"):
            assert hasattr(migration, "worldedit_bundle"), "WorldEdit console snapshot migration is missing"
            bundle = migration.worldedit_bundle(plan, "habitat_01", "ark", "fence_test_20261110")
            for text in bundle["select"]:
                result = command(text)
                assert not re.search(r"Unknown|Invalid|Usage:|A player is|No world", result), (text, result)
            inspected = [command(text) for text in bundle["inspect"]]
            counts = [int(re.search(r"Counted:\s*(\d+)", response).group(1)) for response in inspected[1:]]
            assert counts == [120, 2, 116, 1, 2], counts
            check("WorldEdit reports all original-slot candidates and separately exposes three protected targets")

            def clipboard_ready():
                response = command("worldedit:/size -c")
                return all(expected in response for expected in (
                    "Offset: (112, 65, -16)", "Size: (33, 1, 33)", "# of blocks: 1089"))

            for text in bundle["backup"]:
                command(text)
            schematic = directory / "plugins/WorldEdit/schematics/fence_test_20261110/habitat_01.schem"
            eventually(lambda: schematic.is_file() and schematic.stat().st_size > 100)
            for text in bundle["verify_backup"]:
                response = command(text)
                assert not re.search(r"Unknown|Invalid|Usage:", response), (text, response)
                if text == "worldedit:clearclipboard":
                    assert "1089" not in command("worldedit:/size -c"), "Clipboard was not cleared before verifying disk backup"
                if text.startswith("worldedit:/schem load"):
                    eventually(clipboard_ready)
            check("WorldEdit console saves the actual selected block states as a bounded schematic")
            command("setblock 118 66 -16 lantern[hanging=false]")
            for text in bundle["apply"]:
                command(text)
            residuals = [int(re.search(r"Counted:\s*(\d+)", command(text)).group(1)) for text in bundle["verify"]]
            assert residuals == [4, 0], residuals
            for mask in bundle["masks"].values():
                assert "Counted: 0" in command("worldedit:/count " + mask)
            block("115 65 -16", "air")
            block("128 65 -16", "water[level=0]")
            block("128 65 16", "air")
            for position, material in (("113 65 -16", "gold_block"), ("114 65 -16", "oak_fence"),
                                       ("128 65 17", "jungle_fence"), ("128 65 0", "jungle_fence"),
                                       ("112 66 0", "jungle_fence"), ("112 65 -16", "jungle_fence"),
                                       ("112 65 0", "jungle_fence"), ("117 65 -16", "jungle_fence"),
                                       ("117 66 -16", "lantern[hanging=false]"),
                                       ("118 65 -16", "jungle_fence"), ("118 66 -16", "lantern[hanging=false]"),
                                       ("119 65 -16", "jungle_fence"), ("119 64 -16", "lantern[hanging=true]"),
                                       ("120 65 -16", "jungle_fence_gate"), ("127 65 16", "jungle_fence"),
                                       ("144 65 -16", "jungle_fence")):
                block(position, material)
            for text in bundle["apply"]:
                command(text)
            check("WorldEdit protects standing/hanging lanterns, rechecks new attachments, and reports protected residuals")
            command("setblock 115 65 -16 diamond_block")
            for text in bundle["undo_prepare"]:
                response = command(text)
                assert not re.search(r"Unknown|Invalid|Usage:", response), (text, response)
                if text == "worldedit:clearclipboard":
                    assert not clipboard_ready(), "Undo must clear the old clipboard before loading its backup"
            assert bundle["undo_verify"] == ["worldedit:/size -c"]
            eventually(clipboard_ready)
            check("WorldEdit undo waits for separately verified disk load, size, origin and block count before restore")
            for text in bundle["undo_restore"]:
                response = command(text)
                assert not re.search(r"Unknown|Invalid|Usage:|clipboard is empty", response), (text, response)
            block("115 65 -16", "diamond_block")
            block("116 65 -16", "jungle_fence")
            block("128 65 16", gate)
            block("128 65 -16", "jungle_fence[waterlogged=true]")
            block("128 65 0", "jungle_fence")
            block("117 66 -16", "lantern[hanging=false]")
            block("118 66 -16", "lantern[hanging=false]")
            block("119 64 -16", "lantern[hanging=true]")
            for text in bundle["cleanup"]:
                command(text)
            check("Schematic undo preserves later builds and restores observed gate/water states")
        run("release")
        assert "marked for force loading" in command("forceload query 112 -16")
        assert "not marked" in command("forceload query 144 16")
        check("Chunk cleanup preserves pre-existing force-load tickets")
        stop()
        report["passed"] = True
    finally:
        if server is not None and server.poll() is None:
            try:
                server.communicate(b"stop\n", timeout=15)
            except subprocess.TimeoutExpired:
                server.kill(); server.wait()
        if log is not None:
            log.close()
        report_path.write_text(json.dumps(report, indent=2) + "\n")
        print("Report: " + str(report_path), flush=True)


if __name__ == "__main__":
    main()
