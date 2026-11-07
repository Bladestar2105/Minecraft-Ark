#!/usr/bin/env python3
"""Set up a fresh stack without accepting the EULA or starting any containers."""
import argparse
from pathlib import Path
from build_plugins import ROOT
from fetch_world import install_world, require_fresh
from init import initialize
from install_plugins import DEFAULT_MODULES, install, selected_modules, verify_own_plugins


def setup(root=ROOT, modules=None):
    root = Path(root).resolve()
    modules = selected_modules(modules)
    require_fresh(root)
    verify_own_plugins(root, modules)
    install_world(root)
    install(root, modules)
    initialize(root)
    print("Review .env and the Minecraft EULA. Set EULA=TRUE only after accepting it.\n"
          "Then run: docker compose build\n"
          "          docker compose up -d\n"
          "Read your local administrator login privately in secrets/panel-login.txt.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", help="Comma-separated modules; default: " + ",".join(DEFAULT_MODULES))
    options = parser.parse_args()
    setup(modules=None if options.modules is None else options.modules.split(","))
