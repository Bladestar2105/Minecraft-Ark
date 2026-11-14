#!/usr/bin/env python3
"""Install verified selected modules and pinned dependencies into a fresh world."""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import shutil
import uuid
import zipfile
from build_plugins import ROOT, download
from fetch_world import safe_path

PLUGINS = {"dino": ("ArkDinos", "1.2.1"), "atlas": ("ArkAtlas", "1.0.1"),
           "panel": ("ArkPanelBridge", "1.0.0"), "accounts": ("ArkAccounts", "1.0.2"),
           "wardrobe": ("ArkWardrobe", "1.0.0"), "support": ("ArkSupport", "1.0.2"),
           "access": ("ArkAccessBridge", "1.1.1")}
DEFAULT_MODULES = [module for module in PLUGINS if module != "access"]
DINO_MODELS = ("trex", "triceratops", "stegosaurus", "brachiosaurus", "velociraptor", "pteranodon", "dilophosaurus", "mosasaurus")
DINO_DEPENDENCIES = {"bettermodel", "geyser-model-engine", "geyser-model-engine-extension",
                     "geyser-utils-spigot", "geyser-utils-geyser"}
LONG_DAY_FILES = ("pack.mcmeta", "data/minecraft/tags/function/load.json",
                  "data/ark_long_days/function/load.mcfunction", "data/ark_long_days/function/tick.mcfunction",
                  "data/ark_long_days/function/day.mcfunction", "data/ark_long_days/function/night.mcfunction")



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


def dino_model_files(root):
    """Use the editable source's embedded texture for both editions."""
    files = []
    models = [('dino-models', 'ark_' + species) for species in DINO_MODELS]
    models += [('dino-items', 'ark_egg_' + species) for species in DINO_MODELS if species != 'mosasaurus']
    for directory, model in models:
        source = directory + "/models/" + model + "/"
        target = "data/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/" + model + "/"
        bbmodel = safe_path(root, source + model + ".bbmodel").read_bytes()
        texture_source = json.loads(bbmodel)["textures"][0]["source"]
        prefix = "data:image/png;base64,"
        if not isinstance(texture_source, str) or not texture_source.startswith(prefix) or len(texture_source) > 1024 * 1024:
            raise ValueError("Invalid embedded dinosaur texture: " + model)
        texture = base64.b64decode(texture_source[len(prefix):], validate=True)
        if not texture.startswith(b"\x89PNG\r\n\x1a\n"):
            raise ValueError("Dinosaur texture must be PNG: " + model)
        files += [("data/plugins/BetterModel/models/" + model + ".bbmodel", bbmodel),
                  (target + model + ".png", texture)]
        for name in (model + ".geo.json", model + ".animation.json", "config.json"):
            files.append((target + name, safe_path(root, source + name).read_bytes()))
    return files


def dino_item_files(root):
    """Install a separate static egg pack beside GME's generated entity pack."""
    assets = json.loads(safe_path(root, 'dino-items/egg-assets.json').read_text())
    expected = {'manifest.json', 'textures/item_texture.json'} | {
        'textures/items/arkdinos/egg_' + species + '.png' for species in DINO_MODELS}
    if set(assets['bedrock']) != expected:
        raise ValueError('Unexpected dinosaur egg resource paths')
    output = io.BytesIO()
    with zipfile.ZipFile(output, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, encoded in sorted(assets['bedrock'].items()):
            content = base64.b64decode(encoded, validate=True)
            info = zipfile.ZipInfo(name, (2026, 11, 8, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, content)
    target = 'data/plugins/Geyser-Spigot/'
    return [(target + 'packs/ark-dino-eggs.mcpack', output.getvalue()),
            (target + 'custom_mappings/ark-dino-eggs.json', (json.dumps(assets['geyser'], indent=2) + '\n').encode())]


def install(root=ROOT, modules=None):
    root = Path(root).resolve()
    modules = selected_modules(modules)
    if not safe_path(root, "data/ark/level.dat").is_file():
        raise ValueError("Install the world first; keep Minecraft stopped during installation.")
    own = verify_own_plugins(root, modules)
    day_pack = []
    for name in LONG_DAY_FILES:
        source = safe_path(root, "world-design/long_days/" + name)
        if not source.is_file():
            raise ValueError("Missing bundled day-cycle source: " + name)
        day_pack.append(("data/ark/datapacks/ark_long_days/" + name, source.read_bytes()))
    if safe_path(root, "data/ark/datapacks/ark_long_days").exists():
        raise ValueError("Existing day-cycle pack; refusing to replace data/ark/datapacks/ark_long_days")
    models = dino_model_files(root) + dino_item_files(root) if "dino" in modules else []
    copies = [("config/geyser.yml", "data/plugins/Geyser-Spigot/config.yml"),
              ("config/floodgate.yml", "data/plugins/floodgate/config.yml"),
              ("config/permissions.yml", "data/permissions.yml"),
              ("config/prism/prism.conf", "data/plugins/prism/prism.conf"),
              ("config/prism/storage.conf", "data/plugins/prism/storage.conf")]
    if "wardrobe" in modules:
        copies.append(("config/skinsrestorer.yml", "data/plugins/SkinsRestorer/config.yml"))
    items = json.loads(safe_path(root, "dependencies-runtime.json").read_text())
    items = [item for item in items if "wardrobe" in modules or item.get("project") != "skinsrestorer"]
    items = [item for item in items if "dino" in modules or item.get("project") not in DINO_DEPENDENCIES]
    items = [item for item in items if {"dino", "access"} & set(modules) or item.get("project") != "packetevents"]
    folders = {"datapack": "data/ark/datapacks", "plugin": "data/plugins",
               "floodgate-extension": "data/plugins/floodgate",
               "geyser-extension": "data/plugins/Geyser-Spigot/extensions"}
    for item in items:
        if item.get("kind") not in folders or not isinstance(item.get("name"), str) or Path(item["name"]).name != item["name"]:
            raise ValueError("Unexpected dependency kind or filename.")
        safe_path(root, folders[item["kind"]] + "/" + item["name"])
    targets = ["data/server.properties", "data/dependencies-installed.json"]
    targets += [target for _, target in copies]
    targets += ["data/plugins/" + Path(item["path"]).name for item in own]
    targets += [target for target, _ in models + day_pack]
    for target in targets:
        path = safe_path(root, target)
        if path.exists():
            raise ValueError("Existing installation settings/artifact; refusing to replace " + target)
    for source, _ in copies:
        if not safe_path(root, source).is_file():
            raise ValueError("Missing configuration template: " + source)
    for item in items:
        download(item, root / folders[item["kind"]], root=root)
    (root / "data/plugins").mkdir(parents=True, exist_ok=True)
    for item in own:
        shutil.copyfile(root / item["path"], root / "data/plugins" / Path(item["path"]).name)
    for target, content in models + day_pack:
        destination = safe_path(root, target)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(content)
    for source, target in copies:
        destination = root / target
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / source, destination)
        if source == "config/floodgate.yml":
            destination.write_text(destination.read_text().replace("${metrics.uuid}", str(uuid.uuid4())))
        if source == "config/geyser.yml" and "dino" in modules:
            destination.write_text(destination.read_text().replace("force-resource-packs: false", "force-resource-packs: true"))
    (root / "data/dependencies-installed.json").write_text(json.dumps(items, indent=2) + "\n")
    print("Selected own plugins and pinned dependencies installed. Keep data/ private.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", help="Comma-separated modules; default: " + ",".join(DEFAULT_MODULES))
    options = parser.parse_args()
    install(modules=None if options.modules is None else options.modules.split(","))
