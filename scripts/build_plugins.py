#!/usr/bin/env python3
"""Download pinned build dependencies, test and build the own plugins."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from urllib.request import Request, urlopen
from fetch_world import safe_path

ROOT = Path(__file__).resolve().parents[1]
BUNDLED_BRIDGE = "runtime-dependencies/GeyserModelEngine-1.0.9.jar"


def download(item, folder, *, root=ROOT):
    folder.mkdir(parents=True, exist_ok=True)
    if Path(item["name"]).name != item["name"] or item["name"] in {".", ".."}:
        raise ValueError("Unsafe artifact filename")
    target = folder / item["name"]
    if target.is_symlink():
        raise ValueError("Artifact symlink")
    algorithm = "sha512" if "sha512" in item else "sha256"
    patch = item.get("patch")
    if patch is not None and patch != "geyser-model-engine-task-publication":
        raise ValueError("Unknown artifact patch")
    if patch and (algorithm != "sha256" or not isinstance(item.get("source_sha256"), str)
                  or len(item["source_sha256"]) != 64 or any(c not in "0123456789abcdef" for c in item["source_sha256"])):
        raise ValueError("Patched artifact requires a pinned upstream SHA-256")
    if "bundled_path" in item and (not patch or item["bundled_path"] != BUNDLED_BRIDGE):
        raise ValueError("Unexpected bundled dependency path")
    bundled = safe_path(root, item["bundled_path"]) if "bundled_path" in item else None
    if not target.exists():
        if bundled is not None and bundled.exists():
            content = bundled.read_bytes()
        else:
            request = Request(item["url"], headers={"User-Agent": "Minecraft-Ark/1.0"})
            with urlopen(request, timeout=120) as response:
                content = response.read()
            if hashlib.new(algorithm, content).hexdigest() != (item["source_sha256"] if patch else item[algorithm]):
                raise ValueError("Checksum mismatch: " + item["name"])
            if patch:
                with tempfile.TemporaryDirectory(prefix=".bridge-build-", dir=folder) as temporary:
                    source, output = Path(temporary) / "upstream.jar", Path(temporary) / "patched.jar"
                    source.write_bytes(content)
                    java_home = os.environ.get("JAVA_HOME")
                    java = str(Path(java_home, "bin", "java")) if java_home else "java"
                    subprocess.run([java, str(Path(root) / "dino-plugin/upstream-patches/GeyserModelEngineTaskPublicationFix.java"),
                                    str(source), str(output)], check=True, capture_output=True, text=True)
                    content = output.read_bytes()
        if hashlib.new(algorithm, content).hexdigest() != item[algorithm]:
            raise ValueError("Artifact checksum mismatch: " + item["name"])
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_bytes(content)
        temporary.replace(target)
    if hashlib.new(algorithm, target.read_bytes()).hexdigest() != item[algorithm]:
        raise ValueError("Cached checksum mismatch: " + str(target))
    return target


def build():
    subprocess.run([sys.executable, str(ROOT / "dino-models/verify_portability.py")], cwd=ROOT, check=True)
    subprocess.run([sys.executable, str(ROOT / "dino-items/test_eggs.py")], cwd=ROOT, check=True)
    subprocess.run([sys.executable, str(ROOT / "dino-items/test_placed_eggs.py")], cwd=ROOT, check=True)
    dependencies = []
    for item in json.loads((ROOT / "dependencies-build.json").read_text()):
        dependencies.append(download(item, ROOT / ".deps"))
    java_home = os.environ.get("JAVA_HOME")
    def java_tool(name):
        return str(Path(java_home, "bin", name)) if java_home else name
    tests = {"dino": ["TamingRulesTest", "RidingRulesTest", "SeatRulesTest", "SpeciesRulesTest", "FlightRulesTest", "DinoPassengerOrderTest", "DinoGrowthTest", "CareRulesTest", "EggAssetsTest", "GroundSpeedsTest", "SwimRulesTest", "DinoSizesTest", "WildSpawnRulesTest"], "atlas": ["AtlasRulesTest"],
             "panel": ["BridgeProtocolTest", "RuntimeSettingsTest"],
             "access": ["AccessRulesTest", "PremiumRulesTest", "PremiumIdentitiesTest"],
             "accounts": ["SessionTest", "AccountsTest", "OperatorTest", "SecretCommandTest", "CommandPolicyTest", "ModuleRegistryTest", "PrivateApiTest", "PortalInfoTest"],
             "wardrobe": ["WardrobeTest", "SignersTest", "CommandPolicyTest", "WardrobeRoutesTest"],
             "support": ["SupportTest", "SupportRoutesTest", "SupportDeleteTest", "SupportNotificationsTest"]}
    versions = {"dino": "ArkDinos-1.2.1", "atlas": "ArkAtlas-1.0.1",
                "panel": "ArkPanelBridge-1.0.0", "access": "ArkAccessBridge-1.1.1", "accounts": "ArkAccounts-1.0.2",
                "wardrobe": "ArkWardrobe-1.0.0", "support": "ArkSupport-1.0.2"}
    packages = {"dino": "dinos", "atlas": "atlas", "panel": "panel", "access": "access",
                "accounts": "accounts", "wardrobe": "wardrobe", "support": "support"}
    built = []
    for module, mains in tests.items():
        folder = ROOT / (module + "-plugin")
        output = folder / "build"
        if output.exists():
            shutil.rmtree(output)
        classes, checks = output / "classes", output / "test-classes"
        classes.mkdir(parents=True)
        checks.mkdir()
        jars = [path for path in dependencies if module not in {"accounts", "support"} or not path.name.startswith("SkinsRestorer-")]
        if module in {"wardrobe", "support"}:
            jars.append(ROOT / "accounts-plugin/build/ArkAccounts-1.0.2.jar")
        cp = os.pathsep.join(str(path) for path in jars)
        run = lambda args: subprocess.run(args, cwd=ROOT, check=True)
        sources = sorted(str(path) for path in (folder / "src/main/java").rglob("*.java"))
        run([java_tool("javac"), "--release", "25", "-sourcepath", "", "-cp", cp,
             "-d", str(classes), *sources])
        sources = sorted(str(path) for path in (folder / "src/test/java").rglob("*.java"))
        run([java_tool("javac"), "--release", "25", "-sourcepath", "", "-cp", cp + os.pathsep + str(classes),
             "-d", str(checks), *sources])
        (checks / "de/ark/panel/MissingOptionalDependency.class").unlink(missing_ok=True)
        for main in mains:
            run([java_tool("java"), "-cp", os.pathsep.join([cp, str(classes), str(checks)]),
                 f"de.ark.{packages[module]}.{main}",
                 *([str(ROOT / 'dino-plugin/src/main/resources/egg-assets.properties')] if main == 'EggAssetsTest' else [])])
        run([java_tool("jar"), "--create", "--date", "2026-11-07T00:00:00Z" if module in {"accounts", "support"} else "2026-11-03T00:00:00Z", "--file",
             str(output / (versions[module] + ".jar")), "-C", str(classes), ".", "-C",
             str(folder / "src/main/resources"), "."])
        artifact = output / (versions[module] + ".jar")
        name, version = versions[module].rsplit("-", 1)
        built.append({"module": module, "name": name, "version": version,
                      "path": artifact.relative_to(ROOT).as_posix(),
                      "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest()})
    (ROOT / "own-plugins.json").write_text(json.dumps({"format": 1, "plugins": built}, indent=2) + "\n")
    print("Own plugins built; their rule tests passed.")


if __name__ == "__main__":
    build()
