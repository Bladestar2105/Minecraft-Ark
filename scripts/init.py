#!/usr/bin/env python3
"""Create fresh local credentials; never print or overwrite them."""
import hashlib
import json
import os
from ipaddress import IPv4Address, IPv6Address
from pathlib import Path
import re
import secrets
import shutil
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_PORTAL_URL = "http://localhost:8090/"


def validate_portal_url(value):
    """Validate before downloading a world or creating local credentials."""
    try:
        if (not isinstance(value, str) or not 1 <= len(value) <= 512
                or any(c.isspace() or ord(c) < 32 or 127 <= ord(c) <= 159 for c in value)
                or any(c in value for c in '\\<>"{}|^`#') or re.search(r'%(?![0-9a-fA-F]{2})', value)):
            raise ValueError
        value.encode('utf-8')
        url = urlsplit(value)
        host = url.hostname
        if (url.scheme.lower() not in ('http', 'https') or not host or url.username is not None
                or url.password is not None or url.netloc.endswith(':')
                or url.port is not None and not 1 <= url.port <= 65535
                or '[' in url.path + url.query or ']' in url.path + url.query):
            raise ValueError
        if ':' in host:
            if '%' in host:
                raise ValueError
            IPv6Address(host)
        else:
            host = host.removesuffix('.')
            if len(host) > 253 or not all(re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?', label) for label in host.split('.')):
                raise ValueError
            if '.' in host and host.rsplit('.', 1)[1].isdigit():
                IPv4Address(host)
    except (ValueError, UnicodeError):
        raise ValueError("Portal URL must be an absolute HTTP(S) address with a valid host, no credentials, controls or fragment (512 characters maximum).") from None


def initialize_portal(root=ROOT):
    """Add missing role-separated portal secrets without replacing operator data."""
    root = Path(root)
    private = root / "secrets"
    if not (root / "data/ark/level.dat").is_file() or private.is_symlink():
        raise ValueError("An initialized world and a real private secrets/ directory are required.")
    private.mkdir(mode=0o700, exist_ok=True)
    if not private.is_dir() or private.stat().st_mode & 0o077:
        raise ValueError("Portal secrets/ must be a private directory (chmod0700).")
    names = ["wardrobe-player-token", "wardrobe-admin-token", "player.json"]
    for name in names:
        path = private / name
        if path.is_symlink() or path.exists() and not path.is_file():
            raise ValueError("Portal secret must be a regular file: " + name)
        if path.exists():
            value = path.read_text().strip()
            if name == "player.json":
                try:
                    value = json.loads(value)["session_secret"]
                except (ValueError, KeyError, TypeError):
                    raise ValueError("Invalid player session secret.") from None
            if not isinstance(value, str) or len(value) < 48 or len(value) > 4096:
                raise ValueError("Invalid portal secret: " + name)
    for name in names:
        path = private / name
        if path.exists():
            continue
        value = json.dumps({"session_secret": secrets.token_hex(32)}) if name == "player.json" else secrets.token_urlsafe(48)
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        with os.fdopen(descriptor, "w") as target:
            target.write(value + "\n")
            target.flush()
            os.fsync(target.fileno())
            # The private host directory stays0700; mounted files are readable by the unprivileged containers.
            os.fchmod(target.fileno(), 0o644)
    if (private / names[0]).read_text().strip() == (private / names[1]).read_text().strip():
        raise ValueError("Player and administrator service tokens must differ.")


def initialize(root=ROOT, portal_url=DEFAULT_PORTAL_URL):
    root = Path(root)
    validate_portal_url(portal_url)
    if not (root / "data/ark/level.dat").is_file():
        raise ValueError("Install and verify the released world in data/ first.")
    private = root / "secrets"
    if private.exists():
        raise ValueError("secrets/ already exists; keep existing credentials or restore its backup.")
    properties = root / "data/server.properties"
    if properties.exists():
        raise ValueError("Existing server.properties: refusing to replace a running installation.")
    config = root / "data/plugins/ArkAccounts/config.yml"
    if config.is_symlink() or config.exists() and not config.is_file():
        raise ValueError("ArkAccounts config must be a regular file.")
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
        initialize_portal(root)
        if not config.exists():
            for directory in [config.parent.parent, config.parent]:
                if not directory.exists():
                    directory.mkdir()
                    directory.chmod(0o755)
            with config.open("x") as target:
                target.write("# Public portal URL; editable in the panel after stopping Minecraft.\n"
                             "bind: 0.0.0.0\nport: 8101\n"
                             "player-token-file: /run/secrets/wardrobe-player-token\n"
                             "admin-token-file: /run/secrets/wardrobe-admin-token\n"
                             "portal-url: " + json.dumps(portal_url, ensure_ascii=False) + "\n"
                             "portal-announcement-seconds: 300\nportal-spawn-display: true\n")
            config.chmod(0o644)
    finally:
        os.umask(old_mask)
    print("Local credentials created. Read secrets/panel-login.txt privately; keep secrets/ out of Git.")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--portal-only", action="store_true", help="Add missing portal secrets to an existing stopped installation.")
    parser.add_argument("--portal-url", help="Public player portal URL for a fresh installation (default: " + DEFAULT_PORTAL_URL + ").")
    options = parser.parse_args()
    if options.portal_only:
        if options.portal_url is not None:
            parser.error("--portal-url applies to fresh installation; edit an existing URL in the panel.")
        initialize_portal()
        print("Portal secrets ready; existing credentials preserved.")
    else:
        initialize(portal_url=options.portal_url or DEFAULT_PORTAL_URL)
