#!/usr/bin/env python3
"""Verify the derived GeyserUtils JAR against pinned Geyser APIs without a server."""
import argparse
import json
from pathlib import Path
import subprocess

from build_plugins import ROOT, download


GEYSER_BUILDS = {
    1298: "57f375b1788ff3c59016396e5ddefda1a71c51be30a3c44a30a13f14e67d0c96",
    1303: "1b0c239e97117853cc4618753e23a0e06c7dc351c81abdd46926012b9fce6710",
}


def verify(root=ROOT, maven="mvn"):
    root = Path(root).resolve()
    items = json.loads((root / "dependencies-runtime.json").read_text())
    item = next(item for item in items if item.get("project") == "geyser-utils-geyser")
    extension = download(item, root / ".deps", root=root)
    pom = root / "dino-plugin/upstream-patches/geyserutils-tests/pom.xml"
    for build, checksum in GEYSER_BUILDS.items():
        api = download({
            "name": f"Geyser-Standalone-2.12.1-b{build}.jar",
            "url": f"https://download.geysermc.org/v2/projects/geyser/versions/2.12.1/builds/{build}/downloads/standalone",
            "sha256": checksum,
        }, root / ".deps", root=root)
        print(f"Testing derived GeyserUtils with Geyser 2.12.1-b{build}", flush=True)
        subprocess.run([maven, "-B", "-ntp", "-f", str(pom),
                        f"-Dgeyser.test.jar={api}", f"-Dgeyserutils.test.jar={extension}",
                        "clean", "test"], cwd=root, check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--maven", default="mvn", help="Maven executable (default: mvn)")
    verify(maven=parser.parse_args().maven)
