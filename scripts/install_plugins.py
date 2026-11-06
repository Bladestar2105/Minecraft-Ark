#!/usr/bin/env python3
"""Install hash-pinned dependencies into a fresh, stopped world directory."""
import json
import shutil
from build_plugins import ROOT, download


def install():
    if not (ROOT / "data/ark/level.dat").is_file():
        raise ValueError("Install the world first; keep Minecraft stopped during installation.")
    config = ROOT / "data/plugins/Geyser-Spigot/config.yml"
    if config.exists():
        raise ValueError("Existing Geyser config; refusing to replace operator settings.")
    own_plugins = [(ROOT / (module + "-plugin") / "build" / jar, jar)
                   for module, jar in [("dino", "ArkDinos-1.0.0.jar"),
                                       ("atlas", "ArkAtlas-1.0.0.jar"),
                                       ("panel", "ArkPanelBridge-1.0.0.jar")]]
    if any(not source.is_file() for source, _ in own_plugins):
        raise ValueError("Build own plugins first: python3 scripts/build_plugins.py")
    items = json.loads((ROOT / "dependencies-runtime.json").read_text())
    for item in items:
        if item["kind"] not in {"datapack", "plugin"}:
            raise ValueError("Unexpected dependency kind")
        folder = ROOT / ("data/ark/datapacks" if item["kind"] == "datapack" else "data/plugins")
        download(item, folder)
    for source, jar in own_plugins:
        shutil.copyfile(source, ROOT / "data/plugins" / jar)
    config.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(ROOT / "config/geyser.yml", config)
    shutil.copyfile(ROOT / "config/permissions.yml", ROOT / "data/permissions.yml")
    (ROOT / "data/dependencies-installed.json").write_text(json.dumps(items, indent=2) + "\n")
    print("Plugins and datapacks downloaded with verified pinned hashes. Keep data/ private.")


if __name__ == "__main__":
    install()
