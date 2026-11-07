#!/usr/bin/env python3
"""Install verified selected modules and pinned dependencies into a fresh world."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
from build_plugins import ROOT, download
from fetch_world import safe_path

PLUGINS = {"dino": ("ArkDinos", "1.0.0"), "atlas": ("ArkAtlas", "1.0.0"),
           "panel": ("ArkPanelBridge", "1.0.0"), "accounts": ("ArkAccounts", "1.0.2"),
           "wardrobe": ("ArkWardrobe", "1.0.0"), "support": ("ArkSupport", "1.0.1"),
           "access": ("ArkAccessBridge", "1.1.1")}
DEFAULT_MODULES = [module for module in PLUGINS if module != "access"]


def selected_modules(modules):
    modules = DEFAULT_MODULES.copy() if modules is None else list(modules)
    if len(modules) != len(set(modules)) or set(modules) - PLUGINS.keys():
        raise ValueError("Choose each known module once: " + ",".join(PLUGINS))
    if {"wardrobe", "support"} & set(modules) and "accounts" not in modules:
        raise ValueError("Wardrobe and Support require the Accounts module.")
    return modules


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        while block := source.read(1024 * 1024):
            digest.update(block)
    return digest.hexdigest()


def verify_own_plugins(root=ROOT, modules=None):
    """Validate the builder/bundle manifest and every selected binary before use."""
    modules = selected_modules(modules)
    path = safe_path(root, "own-plugins.json")
    if not path.is_file():
        raise ValueError("Missing own-plugins.json; build first: python3 scripts/build_plugins.py")
    manifest = json.loads(path.read_text())
    if not isinstance(manifest, dict) or manifest.get("format") != 1 or not isinstance(manifest.get("plugins"), list):
        raise ValueError("Invalid own-plugin manifest.")
    entries = {}
    for item in manifest["plugins"]:
        if not isinstance(item, dict) or set(item) != {"module", "name", "version", "path", "sha256"}:
            raise ValueError("Invalid own-plugin entry.")
        module = item["module"]
        if not isinstance(module, str) or module not in PLUGINS or module in entries:
            raise ValueError("Unknown or duplicate own-plugin module.")
        name, version = PLUGINS[module]
        if (item["name"] != name or item["version"] != version
                or item["path"] != f"{module}-plugin/build/{name}-{version}.jar"
                or not isinstance(item["sha256"], str) or not re.fullmatch(r"[0-9a-f]{64}", item["sha256"])):
            raise ValueError("Invalid native own-plugin path, version or checksum.")
        entries[module] = item
    if entries.keys() != PLUGINS.keys():
        raise ValueError("Manifest must describe all seven own plugins.")
    for module in modules:
        item = entries[module]
        source = safe_path(root, item["path"])
        if not source.is_file() or sha256(source) != item["sha256"]:
            raise ValueError("Own-plugin checksum mismatch: " + item["path"])
    return [entries[module] for module in modules]


def install(root=ROOT, modules=None):
    root = Path(root).resolve()
    modules = selected_modules(modules)
    if not safe_path(root, "data/ark/level.dat").is_file():
        raise ValueError("Install the world first; keep Minecraft stopped during installation.")
    own = verify_own_plugins(root, modules)
    copies = [("config/geyser.yml", "data/plugins/Geyser-Spigot/config.yml"),
              ("config/permissions.yml", "data/permissions.yml")]
    if "wardrobe" in modules:
        copies.append(("config/skinsrestorer.yml", "data/plugins/SkinsRestorer/config.yml"))
    items = json.loads(safe_path(root, "dependencies-runtime.json").read_text())
    items = [item for item in items if "wardrobe" in modules or item.get("project") != "skinsrestorer"]
    for item in items:
        if item.get("kind") not in {"datapack", "plugin"} or not isinstance(item.get("name"), str) or Path(item["name"]).name != item["name"]:
            raise ValueError("Unexpected dependency kind or filename.")
        folder = "data/ark/datapacks/" if item["kind"] == "datapack" else "data/plugins/"
        safe_path(root, folder + item["name"])
    targets = ["data/server.properties", "data/dependencies-installed.json"]
    targets += [target for _, target in copies]
    targets += ["data/plugins/" + Path(item["path"]).name for item in own]
    for target in targets:
        path = safe_path(root, target)
        if path.exists():
            raise ValueError("Existing installation settings/artifact; refusing to replace " + target)
    for source, _ in copies:
        if not safe_path(root, source).is_file():
            raise ValueError("Missing configuration template: " + source)
    for item in items:
        folder = root / ("data/ark/datapacks" if item["kind"] == "datapack" else "data/plugins")
        download(item, folder)
    (root / "data/plugins").mkdir(parents=True, exist_ok=True)
    for item in own:
        shutil.copyfile(root / item["path"], root / "data/plugins" / Path(item["path"]).name)
    for source, target in copies:
        destination = root / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / source, destination)
    (root / "data/dependencies-installed.json").write_text(json.dumps(items, indent=2) + "\n")
    print("Selected own plugins and pinned dependencies installed. Keep data/ private.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", help="Comma-separated modules; default: " + ",".join(DEFAULT_MODULES))
    options = parser.parse_args()
    install(modules=None if options.modules is None else options.modules.split(","))
