#!/usr/bin/env python3
"""Create reproducible public binary artifacts from the explicit source allowlist."""
import argparse
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import tempfile
import zipfile
from build_plugins import ROOT, BUNDLED_BRIDGE, download
from fetch_world import safe_path
from install_plugins import PLUGINS, sha256, verify_own_plugins

TIMESTAMP = (2026, 11, 7, 0, 0, 0)
PRIVATE = {".git", ".env", "secrets", "data", "panel-state", ".cache", ".deps", ".venv",
           "dist", "build", "__pycache__", "node_modules", "test-fixtures", "fixtures", "output"}
TEXT_SUFFIXES = {".py", ".java", ".json", ".bbmodel", ".patch", ".yml", ".yaml", ".md", ".txt", ".html", ".css", ".js", ".mjs", ".sh", ".properties", ".xml", ".toml"}
TEXT_NAMES = {".env.example", ".gitignore", ".dockerignore", "Dockerfile", "LICENSE", "requirements.txt"}


def source_files(root):
    names = json.loads(safe_path(root, "distribution-files.json").read_text())
    if (not isinstance(names, list) or not all(isinstance(name, str) for name in names)
            or names != sorted(set(names)) or "distribution-files.json" not in names):
        raise ValueError("Source allowlist must be a sorted unique list including itself.")
    for name in names:
        parts = PurePosixPath(name).parts
        if (set(parts) & PRIVATE or name == "own-plugins.json" or any(part.startswith(".env") and part != ".env.example" for part in parts)
                or not parts or Path(name).suffix not in TEXT_SUFFIXES and parts[-1] not in TEXT_NAMES):
            raise ValueError("Private or non-source allowlist path: " + name)
        source = safe_path(root, name)
        if not source.is_file():
            raise ValueError("Missing source allowlist file: " + name)
        content = source.read_bytes()
        try:
            content.decode("utf-8")
        except UnicodeDecodeError:
            raise ValueError("Source allowlist contains a binary file: " + name) from None
        if b"\x00" in content:
            raise ValueError("Source allowlist contains a binary file: " + name)
    return names


def package(root=ROOT, version=None):
    if not isinstance(version, str) or not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", version):
        raise ValueError("Provide a safe release version, for example app-2026-11-07.")
    root = Path(root).resolve()
    own = verify_own_plugins(root, list(PLUGINS))
    names = source_files(root) + ["own-plugins.json"] + [item["path"] for item in own]
    dist = safe_path(root, "dist")
    target = safe_path(root, "dist/" + version)
    if target.exists():
        raise ValueError("Release output already exists: " + str(target))
    bundled = {}
    if "dependencies-runtime.json" in names:
        for item in json.loads((root / "dependencies-runtime.json").read_text()):
            if "bundled_path" not in item:
                continue
            if item["bundled_path"] != BUNDLED_BRIDGE or BUNDLED_BRIDGE in bundled:
                raise ValueError("Unexpected or duplicate bundled dependency")
            source = download(item, root / ".deps", root=root)
            if sha256(source) != item["sha256"]:
                raise ValueError("Bundled dependency checksum mismatch")
            bundled[BUNDLED_BRIDGE] = source
    dist.mkdir(exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=".package-", dir=dist))
    try:
        archive = staging / f"Minecraft-Ark-{version}-all-in-one.zip"
        with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
            for name in sorted(names + list(bundled)):
                source = bundled.get(name) or safe_path(root, name)
                info = zipfile.ZipInfo(name, TIMESTAMP)
                info.create_system = 3
                info.external_attr = (stat.S_IFREG | 0o644) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                with source.open("rb") as content, bundle.open(info, "w") as output:
                    shutil.copyfileobj(content, output, 1024 * 1024)
        for item in own:
            shutil.copyfile(root / item["path"], staging / Path(item["path"]).name)
        artifacts = sorted(staging.iterdir())
        (staging / "SHA256SUMS").write_text("".join(sha256(path) + "  " + path.name + "\n" for path in artifacts))
        if target.exists() or target.is_symlink():
            raise ValueError("Release output appeared during packaging.")
        os.rename(staging, target)
    finally:
        if staging.exists():
            shutil.rmtree(staging)
    print("Public binary artifacts ready: " + str(target))
    return target


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="New release output directory under dist/")
    package(version=parser.parse_args().version)
