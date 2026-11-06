#!/usr/bin/env python3
"""Create fresh local credentials; never print or overwrite them."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil

ROOT = Path(__file__).resolve().parents[1]


def initialize(root=ROOT):
    root = Path(root)
    if not (root / "data/ark/level.dat").is_file():
        raise ValueError("Install and verify the released world in data/ first.")
    private = root / "secrets"
    if private.exists():
        raise ValueError("secrets/ already exists; keep existing credentials or restore its backup.")
    properties = root / "data/server.properties"
    if properties.exists():
        raise ValueError("Existing server.properties: refusing to replace a running installation.")
    old_mask = os.umask(0o077)
    try:
        private.mkdir(mode=0o700)
        password, salt = secrets.token_urlsafe(24), secrets.token_hex(16)
        digest = hashlib.scrypt(password.encode(), salt=salt.encode(), n=32768, r=8, p=1,
                                maxmem=128 * 1024 * 1024, dklen=64).hex()
        credentials = {"username": "admin", "password_hash": f"scrypt:32768:8:1${salt}${digest}",
                       "session_secret": secrets.token_hex(32)}
        (private / "panel.json").write_text(json.dumps(credentials) + "\n")
        (private / "panel-login.txt").write_text("Username: admin\nPassword: " + password + "\n")
        rcon = secrets.token_urlsafe(32)
        (private / "rcon-password").write_text(rcon + "\n")
        properties.write_text("level-name=ark\nonline-mode=true\nenforce-secure-profile=false\n"
            "enable-rcon=true\nrcon.port=25575\nrcon.password=" + rcon + "\n"
            "difficulty=normal\ngamemode=survival\nmax-players=10\nspawn-protection=0\n"
            "motd=Minecraft ARK\nallow-flight=true\n")
        for name in ["data", "panel-state"]:
            (root / name).mkdir(exist_ok=True)
        # Docker's unprivileged panel reads these files; the host directory remains private.
        (private / "panel.json").chmod(0o644)
        (private / "rcon-password").chmod(0o644)
        (root / "panel-state").chmod(0o770)
        if hasattr(os, "geteuid") and os.geteuid() == 0:
            os.chown(root / "panel-state", 1000, 1000)
        if not (root / ".env").exists():
            shutil.copyfile(root / ".env.example", root / ".env")
    finally:
        os.umask(old_mask)
    print("Local credentials created. Read secrets/panel-login.txt privately; keep secrets/ out of Git.")


if __name__ == "__main__":
    initialize()
