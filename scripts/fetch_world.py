#!/usr/bin/env python3
"""Verify the pinned split world release and install it into a fresh checkout."""
import hashlib
import io
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import tarfile
import tempfile
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
TAG = "world-2026-11-06"
BASE_URL = "https://github.com/Bladestar2105/Minecraft-Ark/releases/download/" + TAG + "/"
CHUNK = 1024 * 1024


def safe_path(root, relative):
    """Reject symlink components, including dangling links, before file access."""
    parts = PurePosixPath(relative).parts
    if not parts or relative.startswith("/") or "\\" in relative or any(p in {"", ".", ".."} for p in relative.split("/")):
        raise ValueError("Unsafe path: " + relative)
    target = Path(root)
    for part in parts:
        target /= part
        if target.is_symlink():
            raise ValueError("Symlink path: " + relative)
    return target


def require_fresh(root):
    for name in ("data", "secrets", ".env", "panel-state"):
        path = Path(root) / name
        if path.exists() or path.is_symlink():
            raise ValueError("Fresh setup only; existing " + name + ". Use a separate directory and keep the server stopped.")


def release(root):
    manifest = json.loads(safe_path(root, "world-release.json").read_text())
    if manifest.get("release_tag") != TAG or manifest.get("base_url") != BASE_URL:
        raise ValueError("World release must use the pinned GitHub release.")
    item = manifest["archive"]
    for key, maximum in (("compressed_bytes", 12_555_565_183), ("expanded_bytes", 17_092_079_344), ("file_count", 5459)):
        if type(item.get(key)) is not int or not 0 < item[key] <= maximum:
            raise ValueError("Invalid world archive limit: " + key)
    if not re.fullmatch(r"[0-9a-f]{64}", item.get("sha256_concatenated", "")) or len(item.get("parts", [])) != 7:
        raise ValueError("Invalid world archive hash or part count.")
    for index, part in enumerate(item["parts"]):
        if (part.get("filename") != f"minecraft-ark-{TAG}.tar.gz.part-{index:03}"
                or type(part.get("bytes")) is not int or not 0 < part["bytes"] <= 1_887_436_800
                or not re.fullmatch(r"[0-9a-f]{64}", part.get("sha256", ""))):
            raise ValueError("Invalid pinned world part.")
    if sum(part["bytes"] for part in item["parts"]) != item["compressed_bytes"]:
        raise ValueError("World part sizes do not match the archive.")
    return item


def verify_part(path, item, combined):
    digest, size = hashlib.sha256(), 0
    with path.open("rb") as source:
        while block := source.read(CHUNK):
            size += len(block)
            if size > item["bytes"]:
                raise ValueError("Oversized world part: " + path.name)
            digest.update(block)
            combined.update(block)
    if size != item["bytes"] or digest.hexdigest() != item["sha256"]:
        raise ValueError("World part checksum/size mismatch: " + path.name)


def cache_parts(root, item):
    cache = safe_path(root, ".cache/world")
    cache.mkdir(parents=True, exist_ok=True)
    paths, combined = [], hashlib.sha256()
    for part in item["parts"]:
        target = safe_path(root, ".cache/world/" + part["filename"])
        if not target.exists():
            descriptor, temporary = tempfile.mkstemp(prefix=".download-", dir=cache)
            try:
                request = Request(BASE_URL + part["filename"], headers={"User-Agent": "Minecraft-Ark/1.0"})
                size, digest = 0, hashlib.sha256()
                with os.fdopen(descriptor, "wb") as output, urlopen(request, timeout=120) as source:
                    while block := source.read(CHUNK):
                        size += len(block)
                        if size > part["bytes"]:
                            raise ValueError("Oversized downloaded world part.")
                        digest.update(block)
                        output.write(block)
                if size != part["bytes"] or digest.hexdigest() != part["sha256"]:
                    raise ValueError("Downloaded world part checksum/size mismatch.")
                os.rename(temporary, target)
            finally:
                Path(temporary).unlink(missing_ok=True)
        if not target.is_file():
            raise ValueError("World cache part must be a regular file.")
        verify_part(target, part, combined)
        paths.append(target)
    if combined.hexdigest() != item["sha256_concatenated"]:
        raise ValueError("Combined world archive checksum mismatch.")
    return paths


class PartsReader(io.RawIOBase):
    """Read consecutive cached parts without a concatenated archive copy."""
    def __init__(self, paths):
        self.paths = iter(paths)
        self.source = None

    def readable(self):
        return True

    def readinto(self, buffer):
        while True:
            if self.source is None:
                path = next(self.paths, None)
                if path is None:
                    return 0
                self.source = path.open("rb")
            count = self.source.readinto(buffer)
            if count:
                return count
            self.source.close()
            self.source = None

    def close(self):
        if self.source is not None:
            self.source.close()
        super().close()


class BoundedTarInfo(tarfile.TarInfo):
    def _proc_pax(self, archive):
        if self.size > 65536:
            raise ValueError("Oversized tar metadata.")
        return super()._proc_pax(archive)

    def _proc_gnulong(self, archive):
        if self.size > 4096:
            raise ValueError("Oversized tar path.")
        return super()._proc_gnulong(archive)


def extract(paths, destination, item):
    seen, files, size = set(), 0, 0
    with PartsReader(paths) as source, tarfile.open(fileobj=source, mode="r|gz", tarinfo=BoundedTarInfo) as archive:
        for entry in archive:
            name = entry.name.rstrip("/")
            parts = PurePosixPath(name).parts
            if (not parts or parts[0] != "data" or len(parts) > 32 or len(name) > 4096
                    or name in seen or len(seen) >= item["file_count"] * 4 + 64
                    or not (entry.isdir() or entry.isreg()) or entry.sparse is not None
                    or entry.isdir() and entry.size != 0):
                raise ValueError("Unsafe, duplicate or excessive world archive entry: " + name)
            target = safe_path(destination, name)
            seen.add(name)
            if entry.isdir():
                target.mkdir(parents=True, exist_ok=True)
                continue
            files += 1
            size += entry.size
            if entry.size < 0 or size > item["expanded_bytes"] or files > item["file_count"]:
                raise ValueError("World archive exceeds its pinned limits.")
            target.parent.mkdir(parents=True, exist_ok=True)
            with archive.extractfile(entry) as content, target.open("xb") as output:
                shutil.copyfileobj(content, output, CHUNK)
    if not (destination / "data/ark/level.dat").is_file():
        raise ValueError("Incomplete world archive.")


def install_world(root=ROOT):
    root = Path(root).resolve()
    require_fresh(root)
    lock = root / ".world-setup.lock"
    try:
        lock.mkdir(mode=0o700)
    except FileExistsError:
        raise ValueError("Another world setup is active; inspect .world-setup.lock before retrying.") from None
    staging = None
    try:
        item = release(root)
        paths = cache_parts(root, item)
        staging = Path(tempfile.mkdtemp(prefix=".world-stage-", dir=root))
        extract(paths, staging, item)
        require_fresh(root)
        os.rename(staging / "data", root / "data")
    finally:
        if staging is not None:
            shutil.rmtree(staging)
        lock.rmdir()
    print("Pinned world installed. Cached release parts remain in .cache/world/.")


if __name__ == "__main__":
    install_world()
