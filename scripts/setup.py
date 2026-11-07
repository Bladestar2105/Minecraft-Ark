#!/usr/bin/env python3
"""Set up a fresh stack without accepting the EULA or starting any containers."""
import argparse
from pathlib import Path
from build_plugins import ROOT
from fetch_world import install_world, require_fresh
from init import DEFAULT_PORTAL_URL, initialize, validate_portal_url
from install_plugins import DEFAULT_MODULES, install, selected_modules, verify_own_plugins


def setup(root=ROOT, modules=None, portal_url=DEFAULT_PORTAL_URL):
    root = Path(root).resolve()
    validate_portal_url(portal_url)
    modules = selected_modules(modules)
    require_fresh(root)
    verify_own_plugins(root, modules)
    install_world(root)
    install(root, modules)
    initialize(root, portal_url=portal_url)
    print("Review .env and the Minecraft EULA. Set EULA=TRUE only after accepting it.\n"
          "Then run: docker compose build\n"
          "          docker compose up -d\n"
          "Read your local administrator login privately in secrets/panel-login.txt.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--modules", help="Comma-separated modules; default: " + ",".join(DEFAULT_MODULES))
    parser.add_argument("--portal-url", default=DEFAULT_PORTAL_URL, help="Public player portal URL (default: " + DEFAULT_PORTAL_URL + ").")
    options = parser.parse_args()
    setup(modules=None if options.modules is None else options.modules.split(","), portal_url=options.portal_url)
