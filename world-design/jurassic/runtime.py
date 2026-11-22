#!/usr/bin/env python3
"""Generate and measure a fresh loopback Paper fixture using cached dependencies.

No existing world is opened. No client rendering or console-device test is
performed. The resulting world and reports remain under a new output directory.
"""
import argparse
from datetime import datetime, timezone
import hashlib
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
import tarfile
import tempfile
import time


def rcon_packet(identity, kind, message):
    body = struct.pack("<ii", identity, kind) + message.encode("utf-8") + b"\0\0"
    return struct.pack("<i", len(body)) + body


class Rcon:
    def __init__(self, port, password, timeout=30):
        self.port, self.password, self.timeout = port, password, timeout

    def command(self, command):
        with socket.create_connection(("127.0.0.1", self.port), self.timeout) as connection:
            def read(size):
                result = bytearray()
                while len(result) < size:
                    piece = connection.recv(size - len(result))
                    if not piece:
                        raise RuntimeError("RCON closed before the complete response")
                    result.extend(piece)
                return bytes(result)

            def response():
                size = struct.unpack("<i", read(4))[0]
                if not 10 <= size <= 4 * 1024 * 1024:
                    raise ValueError("Invalid RCON frame length")
                body = read(size)
                if body[-2:] != b"\0\0":
                    raise ValueError("Invalid RCON frame terminator")
                return (*struct.unpack("<ii", body[:8]), body[8:-2])

            connection.sendall(rcon_packet(1, 3, self.password))
            while True:
                identity, kind, _ = response()
                if identity == -1:
                    raise RuntimeError("Local fixture RCON authentication failed")
                if identity == 1 and kind == 2:
                    break
            # A different request ID is the end marker. A single recv/packet is
            # insufficient: Minecraft splits long replies into multiple frames.
            connection.sendall(rcon_packet(2, 2, command))
            parts = []
            sentinel_sent = False
            while True:
                identity, _, value = response()
                if identity == 3:
                    break
                if identity != 2:
                    raise ValueError("Unexpected RCON response identity")
                parts.append(value)
                if not sentinel_sent:
                    # Minecraft's reader can reject two request frames arriving
                    # in one read. Its first reply proves it consumed request 2.
                    connection.sendall(rcon_packet(3, 2, "list"))
                    sentinel_sent = True
                if sum(map(len, parts)) > 32 * 1024 * 1024:
                    raise ValueError("RCON response exceeds fixture budget")
            return re.sub(r"§[0-9a-fklmnorx]", "", b"".join(parts).decode("utf-8"))


def parse_status(value):
    try:
        result = json.loads(value)
        job = result.get("job")
        if (not result["world_uuid"] or not result["definition_sha256"]
                or result["min_height"] >= result["max_height"]):
            raise ValueError("Impossible Jurassic status")
        if job is not None and (type(job["done"]) is not int or type(job["total"]) is not int
                                or not 0 <= job["done"] <= job["total"]
                                or type(job["active"]) is not bool):
            raise ValueError("Impossible Jurassic job progress")
        return result
    except (json.JSONDecodeError, KeyError, TypeError) as error:
        raise ValueError("Expected one complete Jurassic status JSON response") from error


def region_chunk_count(world, bounds=None):
    count = 0
    for path in (world / "region").glob("r.*.*.mca"):
        match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", path.name)
        if not match:
            continue
        rx, rz = map(int, match.groups())
        with path.open("rb") as content:
            locations = content.read(4096)
        if len(locations) != 4096:
            raise ValueError("Incomplete region location table: " + str(path))
        for index in range(1024):
            if locations[index * 4:index * 4 + 4] != bytes(4):
                cx, cz = rx * 32 + index % 32, rz * 32 + index // 32
                if bounds is None or bounds[0] <= cx <= bounds[2] and bounds[1] <= cz <= bounds[3]:
                    count += 1
    return count


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as content:
        for piece in iter(lambda: content.read(1024 * 1024), b""):
            digest.update(piece)
    return digest.hexdigest()


def portable_tar_info(member):
    member.uid = member.gid = 0
    member.uname = member.gname = ""
    return member


def checked_leaf_state(entry):
    if isinstance(entry, str):
        name, properties = entry, {}
    elif isinstance(entry, dict):
        name = entry.get("Name", entry.get("id", ""))
        properties = entry.get("Properties", entry.get("properties", {}))
    else:
        raise ValueError("Invalid native block palette entry")
    if name not in ("minecraft:jungle_leaves", "minecraft:spruce_leaves"):
        return None
    if properties.get("persistent") != "true":
        raise ValueError("Generated foliage is not persistent: " + str(entry))
    return entry


def free_port():
    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        return listener.getsockname()[1]


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=os.environ.get("JURASSIC_TEST_OUTPUT", tempfile.gettempdir()))
    parser.add_argument("--paper-cache", type=Path, default=os.environ.get("JURASSIC_PAPER_CACHE"))
    parser.add_argument("--paper-jar", type=Path, default=os.environ.get("JURASSIC_PAPER_JAR"))
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--plugin", type=Path, default=Path(__file__).resolve().parents[2] / "jurassic-plugin/build/ArkJurassic-0.1.0.jar")
    parser.add_argument("--timeout", type=float, default=3600)
    parser.add_argument("--pause-after", type=int, default=32)
    parser.add_argument("--heap", default="2G")
    args = parser.parse_args(argv)
    if not args.paper_cache or not args.paper_jar or not args.java_home:
        parser.error("Set JAVA_HOME, JURASSIC_PAPER_CACHE and JURASSIC_PAPER_JAR, or pass their options")
    if args.timeout <= 0 or not 1 <= args.pause_after < 16384 or not re.fullmatch(r"\d+[GM]", args.heap):
        parser.error("Require positive timeout, pause-after 1..16383 and heap such as 2G")
    java = args.java_home.resolve() / "bin/java"
    cache, jar, plugin = args.paper_cache.resolve(), args.paper_jar.resolve(), args.plugin.resolve()
    for path in (java, jar, plugin):
        if not path.is_file():
            parser.error("Required cached input missing: " + str(path))
    for name in ("libraries", "versions", "cache"):
        if not (cache / name).is_dir():
            parser.error("Required Paper cache directory missing: " + str(cache / name))
    args.output.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="jurassic-native-", dir=args.output.resolve()))
    report_path = directory / "report.json"
    report = dict(started_at=datetime.now(timezone.utc).isoformat(), fixture=str(directory),
                  passed=False, client_rendering_tested=False, physical_console_tested=False,
                  inputs=dict(paper_sha256=sha256(jar), plugin_sha256=sha256(plugin)),
                  commands=[], samples=[], checks=[])
    started = time.monotonic()
    deadline = started + args.timeout
    server = None
    log = None
    password = secrets.token_hex(24)
    port, rcon_port = free_port(), free_port()
    while rcon_port == port:
        rcon_port = free_port()
    rcon = Rcon(rcon_port, password)

    def save_report():
        report["elapsed_seconds"] = round(time.monotonic() - started, 3)
        temporary = report_path.with_suffix(".tmp")
        temporary.write_text(json.dumps(report, indent=2) + "\n")
        temporary.replace(report_path)

    def command(text):
        if server is None or server.poll() is not None:
            raise RuntimeError("Fixture process exited; inspect console.log")
        value = rcon.command(text)
        report["commands"].append(dict(command=text, response=value))
        if text.startswith("jurassic ") and "Jurassic refused:" in value:
            raise RuntimeError(value.strip())
        return value

    def status():
        result = parse_status(command("jurassic status"))
        sample = dict(elapsed_seconds=round(time.monotonic() - started, 3), **result)
        rss = subprocess.run(["ps", "-o", "rss=", "-p", str(server.pid)], text=True, capture_output=True)
        if rss.returncode == 0 and rss.stdout.strip().isdigit():
            sample["rss_kib"] = int(rss.stdout.strip())
        report["samples"].append(sample)
        return result

    def check(name):
        report["checks"].append(name)
        save_report()
        print("PASS " + name, flush=True)

    def wait_until(predicate, timeout=180, interval=.2):
        until = min(deadline, time.monotonic() + timeout)
        while time.monotonic() < until:
            if server.poll() is not None:
                raise RuntimeError("Fixture exited: " + (directory / "console.log").read_text(errors="replace")[-6000:])
            result = predicate()
            if result:
                return result
            time.sleep(interval)
        raise TimeoutError("Native fixture deadline reached; partial results retained at " + str(directory))

    def start():
        nonlocal server, log
        log_path = directory / "console.log"
        offset = log_path.stat().st_size if log_path.exists() else 0
        log = log_path.open("ab")
        server = subprocess.Popen([str(java), "-Xms256M", "-Xmx" + args.heap,
                                   "-Duser.language=en", "-Duser.country=US",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false",
                                   "-jar", "paper.jar", "--nogui"], cwd=directory,
                                  stdin=subprocess.PIPE, stdout=log, stderr=log)

        def ready():
            with log_path.open("rb") as content:
                content.seek(offset)
                return b"Done (" in content.read()
        wait_until(ready)

    def stop():
        nonlocal server, log
        command("save-all flush")
        server.communicate(b"stop\n", timeout=90)
        if server.returncode != 0:
            raise RuntimeError("Paper did not stop cleanly: " + str(server.returncode))
        server = None
        log.close()
        log = None

    try:
        for name in ("libraries", "versions", "cache"):
            shutil.copytree(cache / name, directory / name)
        shutil.copyfile(jar, directory / "paper.jar")
        (directory / "plugins").mkdir()
        shutil.copyfile(plugin, directory / "plugins/ArkJurassic.jar")
        (directory / "config").mkdir()
        (directory / "config/paper-world-defaults.yml").write_text("chunks:\n  delay-chunk-unloads-by: 0s\n")
        (directory / "eula.txt").write_text("eula=true\n")
        (directory / "server.properties").write_text(
            f"server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=fixture\n"
            'level-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},'
            '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
            '"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=2\nsimulation-distance=2\n'
            f"spawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n"
            "enforce-secure-profile=false\nallow-flight=true\n")
        version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
        report["java_version"] = version.stdout + version.stderr
        start()
        report["version_command"] = command("version")
        native_versions = re.findall(r"This server is running Paper version[^\n]+", (directory / "console.log").read_text())
        if not native_versions:
            raise AssertionError("Paper startup did not report its actual server/API version")
        report["server_version"] = native_versions[-1]
        command("jurassic open")
        initial = status()
        if initial["seed"] != 19930611:
            raise AssertionError(("Unexpected world seed", initial))
        report["initial"] = initial
        for key in ("world_folder", "definition_sha256", "implementation_sha256", "generator_version", "world_uuid", "seed"):
            report[key] = initial[key]
        check("Fresh test world opens with native UUID, seed and API build-height binding")
        generation_started = time.monotonic()
        command("jurassic generate reference")

        def reached_pause():
            current = status()
            return current if current["job"]["done"] >= args.pause_after else False
        wait_until(reached_pause, timeout=args.timeout, interval=.5)
        command("jurassic pause")

        def drained():
            current = status()
            return current if not current["job"]["active"] and not current["job"]["inflight"] else False
        paused = wait_until(drained, timeout=60)
        if not args.pause_after <= paused["job"]["done"] < paused["job"]["total"]:
            raise AssertionError(("Pause did not interrupt an unfinished job", paused))
        report["paused"] = paused
        stop()
        world = Path(paused["world_folder"]).resolve()
        if not world.is_relative_to(directory) or not world.is_dir():
            raise AssertionError(("Native world folder escaped the isolated fixture", world))
        report["saved_chunks_at_pause"] = region_chunk_count(world, paused["job"]["bounds"])
        start()
        command("jurassic open")
        restored = status()
        for key in ("world_uuid", "definition_sha256", "implementation_sha256", "generator_version", "seed", "min_height", "max_height"):
            if restored[key] != paused[key]:
                raise AssertionError(("World binding changed after restart", key, paused[key], restored[key]))
        if restored["job"]["done"] != paused["job"]["done"] or restored["job"]["active"]:
            raise AssertionError(("Paused checkpoint changed after restart", paused, restored))
        report["restored"] = restored
        check("Pause, clean stop and restart preserve the exact world-bound checkpoint")
        stop()
        checkpoint = directory / "plugins/ArkJurassic/job.json"
        state = json.loads(checkpoint.read_text())
        state["done"] = 1024
        checkpoint.write_text(json.dumps(state) + "\n")
        report["fault_injection"] = dict(kind="overstated persisted cursor, not a process-crash test",
                                        actual_completed_before_injection=restored["job"]["done"], stored_done=1024)
        start()
        command("jurassic open")
        injected = status()
        if injected["job"]["done"] != 1024:
            raise AssertionError(("Fault-injection checkpoint was not loaded", injected))
        command("jurassic resume")

        def rewound():
            current = status()
            return current if current["job"]["done"] < 1024 else False
        rewind = wait_until(rewound, timeout=30, interval=.1)
        report["fault_injection"]["observed_rewound_done"] = rewind["job"]["done"]
        check("Resume detects an overstated checkpoint and rewinds to missing native terrain")
        last_notice = time.monotonic()

        def completed():
            nonlocal last_notice
            current = status()
            if time.monotonic() - last_notice >= 15:
                print(f"Progress {current['job']['done']}/{current['job']['total']} chunks", flush=True)
                save_report()
                last_notice = time.monotonic()
            return current if current["job"]["done"] == current["job"]["total"] and not current["job"]["active"] else False
        final = wait_until(completed, timeout=args.timeout, interval=2)
        if final["job"]["total"] != 16384 or final["job"]["bounds"] != [448, 128, 575, 255]:
            raise AssertionError(("Reference chunk rectangle does not match 2048 x 2048 blocks", final))
        report["generation_elapsed_seconds_including_restart"] = round(time.monotonic() - generation_started, 3)
        report["final"] = final
        check("All 16,384 reference chunks finish through bounded generation after resume")
        probes = []
        for x, z in ((8060, 3000), (8120, 3072), (8192, 3072), (8260, 3072), (8220, 3130), (7790, 2790)):
            probe = json.loads(command(f"jurassic probe {x} {z}"))
            if not initial["min_height"] <= probe["height"] < initial["max_height"]:
                raise AssertionError(("Probe outside native build height", probe))
            if probe["x"] != x or probe["z"] != z:
                raise AssertionError(("Probe position changed", probe))
            probes.append(probe)
        report["probes"] = probes
        center = next(probe for probe in probes if (probe["x"], probe["z"]) == (8192, 3072))
        if center["ground"] != "GRAVEL" or center["above"] != "AIR":
            raise AssertionError(("Gallimimus access trail is not its expected gravel/air", center))
        for at in ((8060, 3000), (8220, 3130)):
            field = next(probe for probe in probes if (probe["x"], probe["z"]) == at)
            if field["ground"] != "GRASS_BLOCK" or field["above"] not in ("AIR", "SHORT_GRASS"):
                raise AssertionError(("Gallimimus field is not safe open grassland", field))
        log_ground = json.loads(command("jurassic probe 8082 3036"))
        log_block = json.loads(command(f"jurassic block 8082 {log_ground['height'] + 3} 3036"))
        if "dark_oak_log" not in log_block["block_data"]:
            raise AssertionError(("Fallen trunk is absent at its native scene position", log_ground, log_block))
        report["fallen_trunk_probe"] = log_block
        check("Native terrain probes return in-range heights and actual block/biome data")
        stop()
        saved = region_chunk_count(world, final["job"]["bounds"])
        if saved != 16384:
            raise AssertionError(("Saved region headers disagree with completed reference job", saved))
        report["saved_reference_chunks"] = saved
        report["saved_world_chunks"] = region_chunk_count(world)
        renderer_path = Path(__file__).with_name("native_render.py")
        renderer_spec = importlib.util.spec_from_file_location("jurassic_native_render", renderer_path)
        renderer = importlib.util.module_from_spec(renderer_spec)
        renderer_spec.loader.exec_module(renderer)
        report["native_renderer_sha256"] = sha256(renderer_path)
        report["native_render"] = renderer.render(world / "region", directory / "reference-native.png",
                                                  size=2048, resolution=512, center_x=8192, center_z=3072,
                                                  verify_all_chunks=True)
        report["full_chunk_validation"] = report["native_render"]
        report["reference_image"] = str(directory / "reference-native.png")
        if report["native_render"]["status"] != "passed" or report["native_render"]["verified_chunks"] != 16384:
            raise AssertionError(("Full native chunk verification failed", report["native_render"]))
        leaf_states = set()
        palette_fields = {'xPos': True, 'zPos': True, 'sections': {'block_states': {'palette': True}}}
        for region_path in sorted((world / "region").glob("r.*.*.mca")):
            with renderer.RegionFile(region_path) as region:
                for cz in range(max(region.rz * 32, 128), min(region.rz * 32 + 32, 256)):
                    for cx in range(max(region.rx * 32, 448), min(region.rx * 32 + 32, 576)):
                        chunk = region.read_chunk(cx, cz, palette_fields)
                        if chunk is None:
                            continue
                        for section in chunk.get("sections", []):
                            for entry in section.get("block_states", {}).get("palette", []):
                                if checked_leaf_state(entry) is not None:
                                    leaf_states.add(json.dumps(entry, sort_keys=True))
        if not leaf_states:
            raise AssertionError("No generated jungle/spruce leaf palette was found in the saved reference")
        report["persistent_leaf_palette_states"] = [json.loads(value) for value in sorted(leaf_states)]
        check("Saved NBT confirms all reference chunks are FULL and generated leaves are persistent")
        world_files = {str(path.relative_to(world)): dict(bytes=path.stat().st_size, sha256=sha256(path))
                       for path in sorted(world.rglob("*")) if path.is_file()}
        report["world_bytes"] = sum(value["bytes"] for value in world_files.values())
        (directory / "world-files.json").write_text(json.dumps(world_files, indent=2) + "\n")
        archive = directory / "jurassic_archipelago-reference.tar.gz"
        with tarfile.open(archive, "w:gz") as output:
            output.add(world, arcname="jurassic_archipelago", filter=portable_tar_info)
        report["archive"] = dict(filename=archive.name, kind="native_dimension_fragment",
                                 bytes=archive.stat().st_size, sha256=sha256(archive))
        native_save = directory / "fixture"
        if not (native_save / "level.dat").is_file() or not world.is_relative_to(native_save):
            raise AssertionError("The complete native save is missing its shared level.dat")
        complete_archive = directory / "jurassic_archipelago-isolated-save.tar.gz"
        with tarfile.open(complete_archive, "w:gz") as output:
            output.add(native_save, arcname="fixture", filter=portable_tar_info)
            output.add(directory / "plugins/ArkJurassic.jar", arcname="plugins/ArkJurassic.jar", filter=portable_tar_info)
            output.add(directory / "plugins/ArkJurassic/job.json", arcname="plugins/ArkJurassic/job.json", filter=portable_tar_info)
        report["isolated_save_archive"] = dict(filename=complete_archive.name, kind="complete_isolated_native_save",
                                               level_name="fixture", bytes=complete_archive.stat().st_size,
                                               sha256=sha256(complete_archive))
        check("Cleanly saved region headers and archived world checksum confirm the generated artifact")
        report["passed"] = True
    except Exception as error:
        report["failure"] = type(error).__name__ + ": " + str(error)
        raise
    finally:
        if server is not None and server.poll() is None:
            try:
                server.communicate(b"stop\n", timeout=60)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
        if log is not None:
            log.close()
        save_report()
        print("Report: " + str(report_path), flush=True)
    return report_path


if __name__ == "__main__":
    main()
