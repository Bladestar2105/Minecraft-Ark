#!/usr/bin/env python3
"""Download pinned build dependencies, test and build the own plugins."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]


def download(item, folder):
    folder.mkdir(parents=True, exist_ok=True)
    if Path(item["name"]).name != item["name"] or item["name"] in {".", ".."}:
        raise ValueError("Unsafe artifact filename")
    target = folder / item["name"]
    if target.is_symlink():
        raise ValueError("Artifact symlink")
    algorithm = "sha512" if "sha512" in item else "sha256"
    if not target.exists():
        request = Request(item["url"], headers={"User-Agent": "Minecraft-Ark/1.0"})
        with urlopen(request, timeout=120) as response:
            content = response.read()
        if hashlib.new(algorithm, content).hexdigest() != item[algorithm]:
            raise ValueError("Checksum mismatch: " + item["name"])
        temporary = target.with_suffix(target.suffix + ".tmp")
        temporary.write_bytes(content)
        temporary.replace(target)
    if hashlib.new(algorithm, target.read_bytes()).hexdigest() != item[algorithm]:
        raise ValueError("Cached checksum mismatch: " + str(target))
    return target


def build():
    dependencies = []
    for item in json.loads((ROOT / "dependencies-build.json").read_text()):
        dependencies.append(download(item, ROOT / ".deps"))
    java_home = os.environ.get("JAVA_HOME")
    def java_tool(name):
        return str(Path(java_home, "bin", name)) if java_home else name
    tests = {"dino": ["TamingRulesTest", "RidingRulesTest"], "atlas": ["AtlasRulesTest"],
             "panel": ["BridgeProtocolTest", "RuntimeSettingsTest"],
             "access": ["AccessRulesTest", "PremiumRulesTest", "PremiumIdentitiesTest"],
             "accounts": ["SessionTest", "AccountsTest", "OperatorTest", "SecretCommandTest", "CommandPolicyTest", "ModuleRegistryTest", "PrivateApiTest", "PortalInfoTest"],
             "wardrobe": ["WardrobeTest", "SignersTest", "CommandPolicyTest", "WardrobeRoutesTest"],
             "support": ["SupportTest", "SupportRoutesTest", "SupportDeleteTest"]}
    versions = {"dino": "ArkDinos-1.0.0", "atlas": "ArkAtlas-1.0.0",
                "panel": "ArkPanelBridge-1.0.0", "access": "ArkAccessBridge-1.1.1", "accounts": "ArkAccounts-1.0.2",
                "wardrobe": "ArkWardrobe-1.0.0", "support": "ArkSupport-1.0.1"}
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
                 f"de.ark.{packages[module]}.{main}"])
        run([java_tool("jar"), "--create", "--date", "2026-11-03T00:00:00Z", "--file",
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
