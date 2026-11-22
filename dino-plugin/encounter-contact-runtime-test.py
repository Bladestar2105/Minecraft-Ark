#!/usr/bin/env python3
"""Controlled real-scheduler scene contact checks in a fresh loopback Paper world.

Only this fixture disables actor AI and positions a Rex/prey pair during GRAZING.
This is contact/authorization evidence, never natural-chase or rendered-client
evidence. Positive damage must originate in the production DinoEncounter.tick.
"""
import argparse
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
HERE = ROOT / "dino-plugin/src/encounter-contact"
BASE = ROOT / "dino-plugin/src/encounter"
EXPECTED = "f08fd024c91c49e7235df4676aeaa79545cb8ef3831d0ce0bb06f562bdb40212"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--paper-cache", type=Path, required=True)
    parser.add_argument("--paper-jar", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--plugin", type=Path, default=ROOT / "dino-plugin/build/ArkDinos-1.2.2.jar")
    parser.add_argument("--expect-sha256", default=EXPECTED, help="Reject a different JAR before launching Paper")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    plugin_bytes = args.plugin.read_bytes()
    digest = hashlib.sha256(plugin_bytes).hexdigest()
    if digest != args.expect_sha256:
        parser.error("Plugin SHA mismatch: " + digest)
    with zipfile.ZipFile(io.BytesIO(plugin_bytes)) as archive:
        if archive.testzip() is not None or "de/ark/dinos/DinoEncounter.class" not in archive.namelist():
            parser.error("Require an encounter-enabled ArkDinos JAR")
    args.output.mkdir(parents=True, exist_ok=True)
    directory = Path(tempfile.mkdtemp(prefix="encounter-contact-native-", dir=args.output.resolve()))
    report_path = directory / "report.json"
    report = dict(passed=False, fixture=str(directory), plugin_sha256=digest, checks=[], commands=[],
                  limitations=["Stationary contact setup only: AI disabled and positions set once during GRAZING; no natural chase claim.",
                               "Direct protocol777 Survival viewer; no official rendered Java/Bedrock/console clients or model plugin.",
                               "External HIGH-priority fixture listener cancels the real native event; no actual WorldGuard/GriefPrevention installation.",
                               "Owned-prey setup calls native tame(); feeding/cooldown behavior is outside this test."])
    plugins = directory / "plugins"
    plugins.mkdir()
    (plugins / "ArkDinos.jar").write_bytes(plugin_bytes)
    report["frozen_plugin"] = str(plugins / "ArkDinos.jar")
    jdk = args.java_home.resolve() / "bin"
    java = jdk / "java"
    report["java_version"] = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True).stderr
    dependencies = json.loads((ROOT / "dependencies-build.json").read_text())
    classpath = os.pathsep.join(str(ROOT / ".deps" / item["name"]) for item in dependencies)
    report["fixture_source_sha256"] = {}
    for source_root, name in ((BASE, "EncounterFixture"), (HERE, "EncounterContactFixture")):
        classes = directory / (name + "-classes")
        classes.mkdir()
        sources = sorted(source_root.joinpath("java").rglob("*.java"))
        for source in [*sources, source_root / "resources/plugin.yml"]:
            report["fixture_source_sha256"][str(source.relative_to(ROOT))] = sha(source)
        subprocess.run([str(jdk / "javac"), "--release", "25", "-sourcepath", "", "-cp", classpath,
                        "-d", str(classes), *map(str, sources)], cwd=ROOT, check=True)
        subprocess.run([str(jdk / "jar"), "--create", "--file", str(plugins / (name + ".jar")),
                        "-C", str(classes), ".", "-C", str(source_root / "resources"), "."], check=True)
    for name in ("libraries", "versions", "cache"):
        shutil.copytree(args.paper_cache.resolve() / name, directory / name)
    shutil.copyfile(args.paper_jar, directory / "paper.jar")
    report["paper_sha256"] = sha(directory / "paper.jar")
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
    password = secrets.token_hex(24)
    (directory / "eula.txt").write_text("eula=true\n")
    (directory / "server.properties").write_text(
        f"level-name=fixture\nserver-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\n"
        "level-type=minecraft:flat\ngenerate-structures=false\nwhite-list=false\nenforce-whitelist=false\n"
        "view-distance=3\nsimulation-distance=3\nspawn-protection=0\nallow-flight=true\nenforce-secure-profile=false\n"
        f"enable-rcon=true\nrcon.port={rcon_port}\nrcon.password={password}\n")
    rcon = runtime.Rcon(rcon_port, password)
    server = client = log = client_log = None
    started = time.monotonic()

    def save():
        report["elapsed_seconds"] = round(time.monotonic() - started, 3)
        report_path.write_text(json.dumps(report, indent=2) + "\n")

    def command(text):
        response = rcon.command(text)
        report["commands"].append(dict(command=text, response=response))
        if "FIXTURE_ERROR:" in response:
            raise AssertionError(response)
        return response

    def status():
        value = json.loads(command("encountercontact status"))
        report["last_status"] = value
        return value

    def wait(predicate, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if server.poll() is not None:
                raise AssertionError((directory / "console.log").read_text()[-6000:])
            value = predicate()
            if value:
                return value
            time.sleep(.1)
        raise AssertionError("Native contact deadline reached")

    def check(name, condition, detail=None):
        if not condition:
            raise AssertionError((name, detail))
        report["checks"].append(dict(name=name, detail=detail))
        save()
        print("PASS " + name, flush=True)

    def stage_events(value, stage):
        return [event for event in value["events"] if event["stage"] == stage]

    try:
        log = (directory / "console.log").open("wb")
        server = subprocess.Popen([str(java), "-Xms256M", "-Xmx1G", "-Duser.language=en", "-Duser.country=US",
                                   "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", "paper.jar", "--nogui"],
                                  cwd=directory, stdin=subprocess.PIPE, stdout=log, stderr=log)
        wait(lambda: b"Done (" in (directory / "console.log").read_bytes(), 120)
        report["server_version"] = command("version")
        loaded = json.loads(command("encountercontact load"))
        check("Fresh flat fixture owns bounded tickets; scene owns none", 0 < loaded["plugin_tickets"].get("EncounterContactFixture", 0) < 160
              and not loaded["plugin_tickets"].get("ArkDinos", 0) and loaded["forced_chunks"] == 0
              and not loaded["natural_mob_spawning"], loaded["plugin_tickets"])
        client_log = (directory / "client.log").open("wb")
        client = subprocess.Popen(["node", str(BASE / "client.mjs"), str(port)], stdin=subprocess.PIPE, stdout=client_log, stderr=client_log)
        wait(lambda: b"JOINED" in (directory / "client.log").read_bytes())
        joined = json.loads(command("encountercontact join SceneNative"))
        check("Actual protocol777 viewer is Survival", len(joined["spectators"]) == 1
              and joined["spectators"][0]["mode"] == "SURVIVAL", joined["spectators"])
        check("Real scene starts using shared 12/12 budget", "GRAZING" in command("dinos scene start"))
        initial = json.loads(command("encountercontact setup"))
        check("Stationary contact arranged only during GRAZING", initial["phase"] == "GRAZING" and initial["distance_squared"] == 4
              and not initial["prey_ai"] and not initial["rex_ai"] and not initial["outside_permission"], initial)
        wait(lambda: len(stage_events(status(), "external_cancel")) >= 2, 14)
        cancelled = status()
        contacts = stage_events(cancelled, "external_cancel")
        check("External cancellation preserves health on genuine scheduled contacts", cancelled["health"] == initial["health"]
              and all(e["scheduler_scene_stack"] and e["external_cancelled"]
                      and e["cancelled_monitor"] and e["scene_phase"] == "RUNNING" for e in contacts), contacts)
        command("encountercontact allow")
        wait(lambda: bool(stage_events(status(), "scheduled_allow")), 4)
        allowed = status()
        strikes = stage_events(allowed, "scheduled_allow")
        check("Production scheduler strike causes real health loss", len(strikes) == 1 and strikes[0]["scheduler_scene_stack"]
              and strikes[0]["in_flight_permission"] and not strikes[0]["cancelled_monitor"]
              and strikes[0]["final_damage"] == 10 and initial["health"] - allowed["health"] == 10, allowed)
        check("Permission covers only in-flight exact pair", not allowed["outside_permission"]
              and all(not e["other_target_permission"] and not e["reverse_permission"] and not e["owned_victim"]
                      and e["distance_squared"] < 9 and e["line_of_sight"] for e in contacts + strikes), contacts + strikes)
        adopted = json.loads(command("encountercontact adopt SceneNative"))
        adopted_tick = adopted["scene_ticks"]
        wait(lambda: status()["scene_ticks"] >= adopted_tick + 40, 4)
        protected = status()
        check("Owned prey survives two scheduled periods without scene attack", protected["phase"] == "RUNNING"
              and protected["owned"] and not protected["scene_actor"] and protected["health"] == allowed["health"]
              and not stage_events(protected, "owned") and not protected["outside_permission"], protected)
        owned_probe = json.loads(command("encountercontact probe owned"))
        other_probe = json.loads(command("encountercontact probe other"))
        probes = stage_events(other_probe, "negative_owned_probe") + stage_events(other_probe, "negative_other_probe")
        check("Direct negative probes cannot reuse the scene authorization", len(probes) == 2 and all(
              not e["scheduler_scene_stack"] and not e["in_flight_permission"] and e["cancelled_monitor"]
              and not e["external_cancelled"] for e in probes) and owned_probe["health"] == protected["health"], probes)
        command("dinos scene stop")
        stopped = json.loads(command("encountercontact status"))
        check("Cleanup retains adopted animal and removes only temporary actors", not stopped["actor_ids"]
              and len(stopped["entities"]) == 1 and stopped["entities"][0]["uuid"] == adopted["prey_uuid"]
              and stopped["entities"][0]["persistent"] and stopped["entities"][0].get("owner"), stopped["entities"])
        released = json.loads(command("encountercontact release"))
        check("Fixture releases every chunk ticket", not released["plugin_tickets"] and released["forced_chunks"] == 0)
        report["passed"] = True
    except Exception as error:
        report["failure"] = type(error).__name__ + ": " + str(error)
        raise
    finally:
        if client is not None:
            client.terminate()
            client.wait(timeout=10)
        if server is not None and server.poll() is None:
            try:
                server.communicate(b"stop\n", timeout=45)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
            report["server_exit_code"] = server.returncode
            if server.returncode != 0:
                report["passed"] = False
        if log is not None:
            log.close()
        if client_log is not None:
            client_log.close()
        save()
        print("Report: " + str(report_path), flush=True)


if __name__ == "__main__":
    main()
