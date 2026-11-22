#!/usr/bin/env python3
"""Native scene checks in a fresh loopback flat world, using a protocol777 client.

No models, rendered Java/Bedrock clients or console devices are used. The tame
finisher uses explicitly primed feeding progress; real feed cooldown timing is
outside this scene-lifetime check. Never install EncounterFixture on a server.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / "dino-plugin/src/encounter"
DEFAULT_PLUGIN_SHA256 = "f08fd024c91c49e7235df4676aeaa79545cb8ef3831d0ce0bb06f562bdb40212"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-cache", type=Path, default=os.environ.get("SCENE_PAPER_CACHE"))
    parser.add_argument("--paper-jar", type=Path, default=os.environ.get("SCENE_PAPER_JAR"))
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--plugin", type=Path, default=ROOT / "dino-plugin/build/ArkDinos-1.2.2.jar")
    parser.add_argument("--plugin-sha256", default=DEFAULT_PLUGIN_SHA256, help="Require the reviewed JAR hash; override explicitly for another candidate")
    parser.add_argument("--output", type=Path, default=os.environ.get("SCENE_TEST_OUTPUT", tempfile.gettempdir()))
    parser.add_argument("--chase-only", action="store_true", help="Observe the whole unchanged encounter; require real chase/flee and automatic cleanup")
    parser.add_argument("--far-spectator", action="store_true", help="Keep the only Survival spectator 200 blocks south of the scene center")
    parser.add_argument("--lazy-chunks", action="store_true", help="Use player view20/simulation3 with no fixture tickets; require loaded non-ticking refusal")
    args = parser.parse_args()
    if args.far_spectator and not args.chase_only:
        parser.error("--far-spectator requires --chase-only; lifecycle adoption moves the spectator to the animal")
    if args.lazy_chunks and (args.chase_only or args.far_spectator):
        parser.error("--lazy-chunks is a separate no-ticket refusal check")
    if not args.paper_cache or not args.paper_jar or not args.java_home:
        parser.error("Pass cached Paper runtime/JAR and Java25 home")
    args.output.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="encounter-native-", dir=args.output.resolve()))
    report = dict(passed=False, checks=[], commands=[], samples=[], fixture=str(directory),
                  limitations=["Direct protocol777 client; no rendered official Java, Bedrock or console client.",
                               "Flat native fixture, no custom model renderer or film-landscape visual acceptance.",
                               "Existing pet uses native tame(); scene adoption primes five feeds, then sends the real finisher interaction packet.",
                               "Nearby-limit refusal temporarily raises the world/per-player limit to24; normal scene uses12/12."])
    report["far_spectator"] = args.far_spectator
    report["chase_only"] = args.chase_only
    report["lazy_chunks"] = args.lazy_chunks
    report_path = directory / "report.json"
    plugin_bytes = args.plugin.read_bytes()
    with zipfile.ZipFile(io.BytesIO(plugin_bytes)) as archive:
        if archive.testzip() is not None or "de/ark/dinos/DinoEncounter.class" not in archive.namelist():
            raise ValueError("Require the current valid encounter-enabled ArkDinos JAR")
    report["plugin_sha256"] = hashlib.sha256(plugin_bytes).hexdigest()
    if report["plugin_sha256"] != args.plugin_sha256:
        raise ValueError(f"ArkDinos JAR hash mismatch: {report['plugin_sha256']} != {args.plugin_sha256}")
    plugins = directory / "plugins"
    plugins.mkdir()
    (plugins / "ArkDinos.jar").write_bytes(plugin_bytes)
    jdk = args.java_home.resolve() / "bin"
    java = jdk / "java"
    report["java_version"] = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True).stderr
    dependencies = json.loads((ROOT / "dependencies-build.json").read_text())
    classpath = os.pathsep.join(str(ROOT / ".deps" / item["name"]) for item in dependencies)
    classes = directory / "fixture-classes"
    classes.mkdir()
    sources = sorted(str(path) for path in (HERE / "java").rglob("*.java"))
    subprocess.run([str(jdk / "javac"), "--release", "25", "-sourcepath", "", "-cp", classpath,
                    "-d", str(classes), *sources], cwd=ROOT, check=True)
    subprocess.run([str(jdk / "jar"), "--create", "--file", str(plugins / "EncounterFixture.jar"),
                    "-C", str(classes), ".", "-C", str(HERE / "resources"), "."], check=True)
    for name in ("libraries", "versions", "cache"):
        shutil.copytree(args.paper_cache.resolve() / name, directory / name)
    shutil.copyfile(args.paper_jar, directory / "paper.jar")
    report["paper_sha256"] = hashlib.sha256((directory / "paper.jar").read_bytes()).hexdigest()
    config = plugins / "ArkDinos/config.yml"
    config.parent.mkdir()
    config.write_text("survival-worlds: [jurassic_archipelago]\nprototype-worlds: [jurassic_archipelago]\n"
                      "wild-spawning:\n  enabled: false\n  per-player: 12\n  world-limit: 12\n  nearby-limit: 12\n")
    spec = importlib.util.spec_from_file_location("jurassic_runtime", ROOT / "world-design/jurassic/runtime.py")
    runtime = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(runtime)
    port, rcon_port = runtime.free_port(), runtime.free_port()
    while port == rcon_port:
        rcon_port = runtime.free_port()
    import secrets
    password = secrets.token_hex(24)
    (directory / "eula.txt").write_text("eula=true\n")
    (directory / "server.properties").write_text(
        f"level-name=fixture\nserver-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nwhite-list=false\nenforce-whitelist=false\n"
        f"view-distance={20 if args.lazy_chunks else 3}\nsimulation-distance=3\nspawn-protection=0\nallow-flight=true\nenforce-secure-profile=false\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n")
    (directory / "config").mkdir()
    (directory / "config/paper-world-defaults.yml").write_text("chunks:\n  delay-chunk-unloads-by: 0s\n")
    rcon = runtime.Rcon(rcon_port, password)
    server = client = log = client_log = None
    started = time.monotonic()

    def save():
        report["elapsed_seconds"] = round(time.monotonic() - started, 3)
        report_path.write_text(json.dumps(report, indent=2) + "\n")

    def command(text):
        value = rcon.command(text)
        report["commands"].append(dict(command=text, response=value))
        if "FIXTURE_ERROR:" in value:
            raise AssertionError(value)
        return value

    def status():
        value = json.loads(command("encounterfixture status"))
        if value["plugin_tickets"].get("ArkDinos", 0) or value["forced_chunks"]:
            raise AssertionError(("Scene allocated chunk tickets", value))
        report["samples"].append(value)
        return value

    def wait(predicate, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if server.poll() is not None:
                raise AssertionError((directory / "console.log").read_text()[-6000:])
            result = predicate()
            if result:
                return result
            time.sleep(.1)
        raise AssertionError("Native fixture deadline reached")

    def check(name, condition=True, detail=None):
        if not condition:
            raise AssertionError((name, detail))
        report["checks"].append(dict(name=name, detail=detail))
        save()
        print("PASS " + name, flush=True)

    def entities(value):
        return {row["uuid"]: row for row in value["entities"]}

    try:
        log = (directory / "console.log").open("wb")
        server = subprocess.Popen([str(java), "-Xms256M", "-Xmx1G", "-Duser.language=en", "-Duser.country=US",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", "paper.jar", "--nogui"],
                                  cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=log)
        wait(lambda: b"Done (" in (directory / "console.log").read_bytes(), 120)
        report["server_version"] = command("version")
        if args.lazy_chunks:
            client_log = (directory / "client.log").open("wb")
            client = subprocess.Popen(["node", str(HERE / "client.mjs"), str(port), "20"], stdin=subprocess.PIPE, stdout=client_log, stderr=client_log)
            wait(lambda: b"JOINED" in (directory / "client.log").read_bytes())
            command("encounterfixture lazy SceneNative")
            def scene_loaded():
                current = status()
                return current if all(chunk["loaded"] for chunk in current["scene_chunks"]) else False
            loaded = wait(scene_loaded, 90)
            check("Player view loads all nine scene chunks without fixture tickets", not loaded["plugin_tickets"]
                  and loaded["forced_chunks"] == 0 and loaded["view_distance"] == 20 and loaded["simulation_distance"] == 3,
                  loaded["scene_chunks"])
            probe = json.loads(command("encounterfixture lazyprobe"))
            check("Actual native entity is loaded but not entity ticking", probe["lazy_probe"]["valid"]
                  and probe["lazy_probe"]["chunk_loaded"] and not probe["lazy_probe"]["is_ticking"], probe["lazy_probe"])
            before = json.loads(command("encounterfixture sentinels SceneNative"))
            sentinels = {label: entities(before)[uuid] for label, uuid in before["fixtures"].items() if label in ("pet", "foreign")}
            def clear_scene():
                current = json.loads(command("encounterfixture lazycheck"))
                return current if all(point["clear"] for point in current["lazy_checks"]) else False
            clear = wait(clear_scene, 60)
            check("Full animation envelopes are clear before lazy admission test", len(clear["lazy_checks"]) == 9, clear["lazy_checks"])
            refused = command("dinos scene start")
            after = status()
            remaining = entities(after)
            check("Loaded lazy scene refuses with explicit entity-ticking reason", "require entity-ticking chunks" in refused, refused)
            check("Lazy refusal removes all attempted actors and restores bookkeeping", not after["actor_ids"]
                  and not after["scene_portal_originals"] and after["phase"] == "COOLDOWN"
                  and set(remaining) == {row["uuid"] for row in sentinels.values()})
            check("Lazy refusal leaves foreign animal and owned pet unchanged", all(
                uuid in remaining and remaining[uuid].get("owner") == row.get("owner")
                and remaining[uuid]["persistent"] == row["persistent"] and remaining[uuid]["portal_cooldown"] == row["portal_cooldown"]
                for row in sentinels.values() for uuid in [row["uuid"]]))
            check("Lazy refusal allocates no plugin or permanent tickets", not after["plugin_tickets"] and after["forced_chunks"] == 0)
            report["lazy_measurements"] = {"refusal": refused, "probe": probe["lazy_probe"], "scene_chunks": loaded["scene_chunks"],
                                           "plugin_tickets": after["plugin_tickets"], "forced_chunks": after["forced_chunks"],
                                           "view_distance": loaded["view_distance"], "simulation_distance": loaded["simulation_distance"]}
            report["passed"] = True
            return
        prepared = json.loads(command("encounterfixture load"))
        check("Bounded fixture chunks loaded without scene tickets", 0 < prepared["plugin_tickets"].get("EncounterFixture", 0) < 160)
        corridor = json.loads(command("encounterfixture corridor"))
        check("Complete native scene corridor loaded", corridor["plugin_tickets"].get("EncounterFixture", 0) < 400)
        refusal = command("dinos scene start")
        check("No-viewer start refused without actors", "survival spectator" in refusal and not status()["actor_ids"], refusal)
        client_log = (directory / "client.log").open("wb")
        client = subprocess.Popen(["node", str(HERE / "client.mjs"), str(port)], stdin=subprocess.PIPE, stdout=client_log, stderr=client_log)
        wait(lambda: b"JOINED" in (directory / "client.log").read_bytes())
        command("encounterfixture join SceneNative" + (" 200" if args.far_spectator else ""))
        if args.far_spectator:
            spectator = status()["spectators"]
            check("Only spectator is Survival and 200 blocks from scene center", len(spectator) == 1
                  and spectator[0]["mode"] == "SURVIVAL" and 199 < math.hypot(spectator[0]["x"] - 8192, spectator[0]["z"] - 3072) < 202, spectator)
        command("encounterfixture population far")
        refused = command("dinos scene start")
        check("World population shares the scene budget", "Shared world/nearby budget" in refused and not status()["actor_ids"], refused)
        command("encounterfixture clearbudget")
        command("encounterfixture budget 24 12")
        command("encounterfixture population near")
        refused = command("dinos scene start")
        check("Nearby population independently refuses nine extra actors", "Shared world/nearby budget" in refused and not status()["actor_ids"], refused)
        command("encounterfixture clearbudget")
        command("encounterfixture budget 12 12")
        sentinels = json.loads(command("encounterfixture sentinels SceneNative"))
        foreign_id = sentinels["fixtures"]["foreign"]
        foreign_start = entities(sentinels)[foreign_id]

        def foreign_metrics():
            foreign = [row for sample in report["samples"] for row in sample["entities"] if row["uuid"] == foreign_id]
            return dict(persistent=bool(foreign) and all(row["persistent"] for row in foreign),
                        max_displacement=max((math.hypot(row["x"] - foreign_start["x"], row["z"] - foreign_start["z"]) for row in foreign), default=0))
        check("Fresh encounter starts", "GRAZING" in command("dinos scene start"))
        initial = status()
        actor_ids = set(initial["actor_ids"])
        actors = [row for row in initial["entities"] if row["uuid"] in actor_ids]
        check("Exactly eight Gallimimus and one Rex own temporary UUIDs",
              len(actor_ids) == 9 and sum(row["species"] == "gallimimus" for row in actors) == 8
              and sum(row["species"] == "trex" for row in actors) == 1 and all(not row["persistent"] for row in actors), actors)
        check("Only exact nine actors receive scene activation cooldown", set(initial["scene_portal_originals"]) == actor_ids
              and all(row["portal_cooldown"] > 0 for row in actors)
              and all(row["portal_cooldown"] == 0 for row in initial["entities"] if row["uuid"] not in actor_ids))
        check("Active start cannot duplicate actors", "Already active" in command("dinos scene start") and len(status()["actor_ids"]) == 9)
        wait(lambda: status()["phase"] == "ALERT", 8)
        check("Real scheduler advances GRAZING to ALERT")
        wait(lambda: status()["phase"] == "RUNNING", 5)
        check("Real scheduler advances ALERT to RUNNING")
        if args.chase_only:
            observations = [initial]
            while True:
                sample = status()
                observations.append(sample)
                if sample["phase"] == "COOLDOWN":
                    break
                if sample["ticks"] > 500:
                    raise AssertionError("Scene failed to complete within its native tick deadline")
                time.sleep(.25)
            metrics = dict(min_pair_distance=None, rex_displacement=0, moving_away_samples=0,
                           persistent_temporary_samples=0, activation_cooldown_missing_samples=0,
                           foreign_cooldown_samples=0, native_samples=len(observations))
            fleeing = {}
            stationary_paths = {}
            frozen = set()
            rex_start = next(row for row in actors if row["species"] == "trex")
            for sample in observations:
                current = [row for row in sample["entities"] if row["uuid"] in actor_ids]
                rexes = [row for row in current if row["species"] == "trex"]
                gallis = [row for row in current if row["species"] == "gallimimus"]
                metrics["persistent_temporary_samples"] += sum(row["persistent"] for row in current)
                metrics["activation_cooldown_missing_samples"] += sum(row["portal_cooldown"] <= 0 for row in current)
                metrics["foreign_cooldown_samples"] += sum(row["portal_cooldown"] != 0 for row in sample["entities"] if row["uuid"] not in actor_ids)
                if not rexes:
                    continue
                rex = rexes[0]
                metrics["rex_displacement"] = max(metrics["rex_displacement"], math.hypot(rex["x"] - rex_start["x"], rex["z"] - rex_start["z"]))
                herd_threat = any(math.hypot(galli["x"] - rex["x"], galli["z"] - rex["z"]) < 56 for galli in gallis)
                for galli in gallis:
                    uuid = galli["uuid"]
                    if sample["phase"] == "RUNNING" and sample["ticks"] >= 160 and sample["players"]:
                        # The flat fixture has no path obstacles; path=true alone does not prove native activation.
                        previous = stationary_paths.get(uuid)
                        if not galli["path"] or not galli["aware"]:
                            stationary_paths.pop(uuid, None)
                        elif previous and math.hypot(galli["x"] - previous[1], galli["z"] - previous[2]) < .05:
                            if sample["ticks"] - previous[0] >= 40:
                                frozen.add(uuid)
                        else:
                            stationary_paths[uuid] = (sample["ticks"], galli["x"], galli["z"])
                    dx, dz = galli["x"] - rex["x"], galli["z"] - rex["z"]
                    distance = math.hypot(dx, dz)
                    metrics["min_pair_distance"] = min(metrics["min_pair_distance"] or distance, distance)
                    vx, _, vz = galli["velocity"]
                    if herd_threat and distance > 0 and (vx * dx + vz * dz) / distance > .12:
                        metrics["moving_away_samples"] += 1
                        fleeing[uuid] = fleeing.get(uuid, 0) + 1
            metrics["moving_away_actor_ids"] = sorted(uuid for uuid, count in fleeing.items() if count >= 2)
            metrics["moving_away_samples_by_actor"] = fleeing
            metrics["stationary_path_actor_ids"] = sorted(frozen)
            native_ages = {}
            for sample in observations:
                for row in sample["entities"]:
                    if row["uuid"] in actor_ids and row["species"] == "gallimimus":
                        native_ages[row["uuid"]] = max(native_ages.get(row["uuid"], 0), row["native_activation"]["age_ticks"])
            metrics["prey_native_max_ages"] = sorted(native_ages.values())
            metrics["full_scene_ticks"] = observations[-1]["ticks"]
            report["chase_measurements"] = metrics
            report["foreign_tag_measurements"] = foreign_metrics()
            save()
            print("Chase measurements: " + json.dumps(metrics), flush=True)
            finished = entities(observations[-1])
            check("Whole scene naturally removes every unowned actor", all(uuid not in finished for uuid in actor_ids))
            released = json.loads(command("encounterfixture release"))
            check("Full chase leaves no plugin or permanent tickets", not released["plugin_tickets"] and released["forced_chunks"] == 0)
            check("Rex actually reaches Gallimimus flight range", metrics["min_pair_distance"] is not None and metrics["min_pair_distance"] < 56, metrics)
            check("Positive chase completes all 440 ticks with eight native ages beyond 400",
                  metrics["full_scene_ticks"] == 440 and len(native_ages) == 8 and min(native_ages.values()) > 400, metrics)
            check("Running native prey paths never freeze for forty ticks", not frozen, metrics)
            check("All eight animals actually move away from Rex", len(metrics["moving_away_actor_ids"]) == 8, metrics)
            check("Temporary scene actors stay nonpersistent", metrics["persistent_temporary_samples"] == 0, metrics)
            check("Activation cooldown affects only exact actors and clears at completion",
                  metrics["activation_cooldown_missing_samples"] == 0 and metrics["foreign_cooldown_samples"] == 0
                  and not observations[-1]["scene_portal_originals"], metrics)
            check("Foreign scene tag preserves persistence and ordinary control", report["foreign_tag_measurements"]["persistent"]
                  and report["foreign_tag_measurements"]["max_displacement"] > 1, report["foreign_tag_measurements"])
            report["passed"] = True
            return
        restored_scene = status()
        check("Native restore keeps scene actors nonpersistent", all(not row["persistent"] for row in restored_scene["entities"] if row["uuid"] in actor_ids))
        primed = json.loads(command("encounterfixture prime SceneNative"))
        adopted = primed["fixtures"]["adopted"]
        original_cooldown = primed["scene_portal_originals"][adopted]
        check("Scene actor unowned before native finisher", entities(primed)[adopted].get("owner") is None)
        client.stdin.write((json.dumps(dict(action="interact", uuid=adopted)) + "\n").encode())
        client.stdin.flush()
        def adopted_owned():
            current = status()
            return current if entities(current).get(adopted, {}).get("owner") else False
        owned = wait(adopted_owned, 8)
        owner = entities(owned)[adopted]["owner"]
        check("Actual protocol finisher tames the scene actor", entities(owned)[adopted]["persistent"], owner)
        adoption_event = owned["adoption_event"]
        check("Adoption restores original cooldown during the interaction event",
              adoption_event.get("uuid") == adopted and adoption_event["portal_cooldown"] == original_cooldown
              and "ark_jurassic_scene" not in adoption_event["tags"]
              and adopted not in owned["scene_portal_originals"], adoption_event)
        client.terminate(); client.wait(timeout=10); client = None
        def aborted():
            current = status()
            return current if current["phase"] == "COOLDOWN" and not current["actor_ids"] else False
        finished = wait(aborted, 3)
        remaining = entities(finished)
        check("No-viewer abort clears only its temporary actors", all(uuid not in remaining for uuid in actor_ids - {adopted}))
        check("Adopted animal survives with ownership and persistence", remaining[adopted].get("owner") == owner
              and remaining[adopted]["persistent"] and "ark_jurassic_scene" not in remaining[adopted]["tags"])
        check("Abort clears scene cooldown bookkeeping without touching other animals", not finished["scene_portal_originals"]
              and all(row["portal_cooldown"] == 0 for row in remaining.values()))
        pet = finished["fixtures"]["pet"]; foreign = finished["fixtures"]["foreign"]
        check("Existing owned pet and unrelated tagged animal survive", remaining[pet].get("owner") == owner
              and remaining[pet]["persistent"] and foreign in remaining and "foreign_scene_token" in remaining[foreign]["tags"])
        report["foreign_tag_measurements"] = foreign_metrics()
        check("Foreign scene tag preserves persistence and ordinary control", report["foreign_tag_measurements"]["persistent"]
              and report["foreign_tag_measurements"]["max_displacement"] > 1, report["foreign_tag_measurements"])
        check("Cooldown refuses a second encounter", finished["cooldown_ms"] > 290000 and "cooling down" in command("dinos scene start"))
        wait(lambda: status()["herd_trackers"] == 0, 3)
        check("No spectator leaves no herd trackers")
        released = json.loads(command("encounterfixture release"))
        check("Every fixture and scene chunk ticket released", not released["plugin_tickets"] and released["forced_chunks"] == 0, released["plugin_tickets"])
        report["passed"] = True
    except Exception as error:
        report["failure"] = type(error).__name__ + ": " + str(error)
        raise
    finally:
        if client is not None:
            client.terminate(); client.wait(timeout=10)
        if server is not None and server.poll() is None:
            try:
                server.communicate(b"stop\n", timeout=45)
            except subprocess.TimeoutExpired:
                server.kill(); server.wait()
            report["server_exit_code"] = server.returncode
            if server.returncode != 0:
                report["passed"] = False
                report["shutdown_failure"] = "Native server did not stop cleanly"
        if log is not None:
            log.close()
        if client_log is not None:
            client_log.close()
        save()
        print("Report: " + str(report_path), flush=True)


if __name__ == "__main__":
    main()
