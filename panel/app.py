import hashlib
import base64
import hmac
import json
import math
import os
import re
import secrets
import stat
import tempfile
import threading
import time
from collections import OrderedDict
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import quote, urlsplit

import docker
import requests
import yaml
from flask import Flask, jsonify, redirect, render_template, request, send_file, session, stream_with_context
from werkzeug.exceptions import HTTPException
from werkzeug.security import check_password_hash, generate_password_hash
from yaml.events import AliasEvent

if __package__:
    from .catalog import EXTRA_CONFIGS, SERVER_FIELDS, config_description, field_for, protected_property, rule_help, validate_field
    from .config_content import PROTECTED, redact_content, restore_content, validate_json
    from .rcon_client import command as rcon_command, RconError
else:
    from catalog import EXTRA_CONFIGS, SERVER_FIELDS, config_description, field_for, protected_property, rule_help, validate_field
    from config_content import PROTECTED, redact_content, restore_content, validate_json
    from rcon_client import command as rcon_command, RconError


SERVICES = {"minecraft": "minecraft-ark", "dns": "minecraft-dns"}
MAP_TERRAINS = {"minecraft:overworld": "admin-map/island.png", "minecraft:ark_creative": "admin-map/creative.png",
                "minecraft:ark_exploration": "admin-map/exploration.png"}
CONFIGS = {key: dict(name=name, path=path, reload_target=target, format="yaml", group=group,
                    restart_required=target is None, requires_stopped=False)
           for key, name, path, target, group in [
               ("geyser", "Geyser", "plugins/Geyser-Spigot/config.yml", "geyser", "Bedrock"),
               ("quests", "Missionen", "plugins/Quests/storage/quests.yml", "quests", "Spielinhalte"),
               ("quest-settings", "Missionseinstellungen", "plugins/Quests/config.yml", "quests", "Spielinhalte"),
               ("permissions", "Spielerrechte", "permissions.yml", None, "Zugang")]} | EXTRA_CONFIGS
RELOADS = {"geyser": "geyser-spigot:geyser reload", "quests": "quests:questsadmin reload"}
SETTINGS = {key: field["default"] for key, field in SERVER_FIELDS.items()}
PLUGIN_SETTINGS = {
    "geyser": ("geyser", {
        "motd.primary-motd": "Geyser", "motd.secondary-motd": "Another Geyser server.",
        "motd.passthrough-motd": True, "motd.max-players": 100, "motd.passthrough-player-counts": True,
        "gameplay.server-name": "Geyser", "gameplay.command-suggestions": True,
        "gameplay.show-coordinates": True, "gameplay.force-resource-packs": False,
        "gameplay.enable-integrated-pack": False, "debug-mode": False,
    }),
    "quests": ("quest-settings", {
        "max-quests": 0, "language": "en-US", "language-override-client": False,
        "clickable-prompts": True, "confirm-accept": True, "confirm-abandon": True,
        "accept-timeout": 20, "show-completed-objectives": True,
        "show-requirements": True, "show-titles": True,
    }),
}
MAX_CONFIG = 256 * 1024
MAX_OUTPUT = 128 * 1024
HOP_HEADERS = {"connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade"}


def output_text(content):
    decoded = (content or b"")[-MAX_OUTPUT:].decode("utf-8", "replace")
    return decoded.encode("utf-8")[-MAX_OUTPUT:].decode("utf-8", "ignore")


def parse_player_locations(positions, dimensions):
    """Join the two fixed RCON snapshots by name, never by output order."""
    dimension_by_name = {}
    label = " has the following entity data: "
    # rcon-cli concatenates multiple command replies without line separators.
    dimension_record = r'([^"\r\n]{1,64})' + label + r'"([a-z0-9_.-]+:[a-z0-9_./-]+)"(?=$|\r?\n|[^"\r\n]{1,64}' + label + r'")'
    for match in re.finditer(dimension_record, dimensions):
        dimension_by_name[match[1]] = match[2]
    players = {}
    position_record = r"([^\[\]\r\n]{1,64})" + label + r"\[([^\[\]\r\n]*)\](?=$|\r?\n|[^\[\]\r\n]{1,64}" + label + r"\[)"
    for match in re.finditer(position_record, positions):
        if match[1] not in dimension_by_name or any(ord(c) < 32 for c in match[1]):
            continue
        values = [value.strip() for value in match[2].split(",")]
        if len(values) != 3 or not all(re.fullmatch(r"[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?[dDfF]?", value) for value in values):
            continue
        numbers = [float(value.rstrip("dDfF")) for value in values]
        if all(math.isfinite(value) for value in numbers):
            players[match[1]] = dict(name=match[1], x=numbers[0], y=numbers[1], z=numbers[2], dimension=dimension_by_name[match[1]])
    return sorted(players.values(), key=lambda player: player["name"].casefold())


class PanelError(Exception):
    def __init__(self, message, status=400):
        self.message, self.status = message, status


class ConfigLoader(yaml.SafeLoader):
    def compose_node(self, parent, index):
        if self.check_event(AliasEvent):
            raise yaml.YAMLError("Aliase sind nicht erlaubt")
        return super().compose_node(parent, index)

    def construct_mapping(self, node, deep=False):
        result = {}
        for key_node, value_node in node.value:
            key = self.construct_object(key_node, deep=deep)
            try:
                if key in result:
                    raise yaml.YAMLError("Doppelter Schlüssel")
                result[key] = self.construct_object(value_node, deep=deep)
            except TypeError as error:
                raise yaml.YAMLError("Ungültiger Schlüssel") from error
        return result


def validate_yaml(config_id, content):
    if not isinstance(content, str):
        raise PanelError("Konfiguration muss Text enthalten.")
    if len(content.encode("utf-8")) > MAX_CONFIG:
        raise PanelError("Konfiguration darf höchstens 256 KiB groß sein.", 413)
    try:
        depth = 0
        for count, event in enumerate(yaml.parse(content), 1):
            if isinstance(event, (yaml.events.MappingStartEvent, yaml.events.SequenceStartEvent)):
                depth += 1
            elif isinstance(event, (yaml.events.MappingEndEvent, yaml.events.SequenceEndEvent)):
                depth -= 1
            if count > 20000 or depth > 40:
                raise yaml.YAMLError("Zu komplex")
        value = yaml.load(content, Loader=ConfigLoader)
    except (yaml.YAMLError, RecursionError, ValueError):
        raise PanelError("Ungültiges YAML; Tags, Aliase und doppelte Schlüssel sind nicht erlaubt.") from None
    if not isinstance(value, dict) or (not value and config_id not in ["quest-actions", "quest-conditions"]):
        raise PanelError("Die Konfiguration muss eine nicht leere YAML-Zuordnung sein.")
    if config_id == "geyser":
        bedrock = value.get("bedrock")
        if not isinstance(bedrock, dict) or not isinstance(value.get("java"), dict) or type(bedrock.get("port")) is not int or not 1 <= bedrock["port"] <= 65535:
            raise PanelError("Geyser benötigt bedrock.port (1–65535) und einen java-Abschnitt.")
    elif config_id == "quests":
        quests = value.get("quests")
        if not isinstance(quests, dict) or not quests:
            raise PanelError("Es fehlt eine nicht leere quests-Zuordnung.")
        for quest in quests.values():
            if not isinstance(quest, dict) or not isinstance(quest.get("name"), str) or not isinstance(quest.get("stages"), dict):
                raise PanelError("Jede Mission benötigt name und stages.ordered.")
            stages = quest["stages"].get("ordered")
            if not isinstance(stages, dict) or not stages or any(not isinstance(stage, dict) or not stage for stage in stages.values()):
                raise PanelError("Jede Mission benötigt nicht leere stages.ordered-Zuordnungen.")
    elif config_id == "quest-settings":
        if type(value.get("max-quests")) is not int or value["max-quests"] < 0:
            raise PanelError("Missionseinstellungen benötigen max-quests als nicht negative Ganzzahl.")
        for key in ["clickable-prompts", "allow-command-questing", "trial-save"]:
            if key in value and type(value[key]) is not bool:
                raise PanelError("Missionsschalter müssen true oder false sein.")
        if "storage-method" in value and not isinstance(value["storage-method"], dict):
            raise PanelError("storage-method muss eine Zuordnung sein.")
    elif config_id == "permissions":
        for node in value.values():
            if not isinstance(node, dict) or ("children" in node and (not isinstance(node["children"], dict) or any(type(v) is not bool for v in node["children"].values()))):
                raise PanelError("Rechte benötigen Zuordnungen und boolesche children-Werte.")
    elif config_id == "mv-groups":
        groups, membership = value.get("groups"), {}
        if not isinstance(groups, dict) or not groups:
            raise PanelError("Inventargruppen benötigen eine groups-Zuordnung.")
        protected_worlds = {"ark", "ark_nether", "ark_the_end", "ark_creative"}
        for name, group in groups.items():
            if not isinstance(group, dict) or not isinstance(group.get("worlds"), list) or not isinstance(group.get("shares"), list):
                raise PanelError("Jede Inventargruppe benötigt worlds und shares als Listen.")
            for world in group["worlds"]:
                if not isinstance(world, str) or not re.fullmatch(r"[A-Za-z0-9_][A-Za-z0-9_. -]*", world):
                    raise PanelError("Inventargruppen dürfen nur wörtliche Weltnamen ohne Muster oder Ausschlüsse enthalten.")
                if world in protected_worlds:
                    if world in membership or group["shares"] != ["all"] or group.get("disabled-shares", []) != []:
                        raise PanelError("ARK-Welten benötigen eindeutige Inventargruppen mit exakt shares: [all] und ohne deaktivierte Anteile.")
                    membership[world] = name
        if (membership.keys() != protected_worlds or len({membership.get(world) for world in ["ark", "ark_nether", "ark_the_end"]}) != 1
                or membership.get("ark_creative") == membership.get("ark")):
            raise PanelError("Survival, Nether und Ende müssen gemeinsam und von Kreativ getrennt bleiben.")
    return value


def validate_plugin_settings(plugin, changes):
    defaults = PLUGIN_SETTINGS[plugin][1]
    if not isinstance(changes, dict) or not changes or changes.keys() - defaults.keys():
        raise PanelError("Nur die angezeigten Plugin-Einstellungen dürfen geändert werden.")
    for key, value in changes.items():
        if type(defaults[key]) is bool:
            valid = type(value) is bool
        elif type(defaults[key]) is int:
            minimum, maximum = {"max-quests": (0, 20), "accept-timeout": (5, 300), "motd.max-players": (1, 1000)}[key]
            valid = type(value) is int and minimum <= value <= maximum
        elif key == "language":
            valid = isinstance(value, str) and value in ["de-DE", "en-US"]
        else:
            minimum, maximum = (1, 80) if key == "gameplay.server-name" else (0, 160)
            valid = isinstance(value, str) and minimum <= len(value) <= maximum and not any(char in value for char in "\r\n\x00")
            if valid:
                value.encode("utf-8")
        if not valid:
            raise PanelError("Ungültiger Wert für " + key + ". Für eigene Sonderwerte den erweiterten Editor verwenden.")


def edit_plugin_scalar(text, field, value):
    # Edit only fixed scalar spans; custom flow/multiline styles stay in the advanced editor.
    node = yaml.compose(text, Loader=ConfigLoader)
    parts = field.split(".")
    newline = "\r\n" if "\r\n" in text else "\n"
    for index, part in enumerate(parts):
        if not isinstance(node, yaml.MappingNode) or node.flow_style:
            raise PanelError("Diese YAML-Schreibweise benötigt den erweiterten Editor; die Datei wurde nicht geändert.")
        found = next((child for key, child in node.value if isinstance(key, yaml.ScalarNode) and key.value == part), None)
        if found is None:
            indent = node.start_mark.column
            lines = []
            for offset, missing in enumerate(parts[index:]):
                last = index + offset == len(parts) - 1
                lines.append(" " * (indent + offset * 2) + missing + ":" + (" " + json.dumps(value, ensure_ascii=False) if last else ""))
            insertion = node.end_mark.index
            prefix = "" if insertion == 0 or text[insertion - 1] in "\r\n" else newline
            return text[:insertion] + prefix + newline.join(lines) + newline + text[insertion:]
        if index == len(parts) - 1:
            if not isinstance(found, yaml.ScalarNode) or found.style in ["|", ">"] or found.start_mark.line != found.end_mark.line:
                raise PanelError("Mehrzeilige oder zusammengesetzte Werte bitte im erweiterten Editor ändern.")
            return text[:found.start_mark.index] + json.dumps(value, ensure_ascii=False) + text[found.end_mark.index:]
        node = found


def unescape_property(value):
    def replace(match):
        escaped = match.group(1)
        if escaped.startswith("u") and len(escaped) == 5:
            return chr(int(escaped[1:], 16))
        return {"t": "\t", "r": "\r", "n": "\n", "f": "\f"}.get(escaped, escaped)
    text = re.sub(r"\\(u[0-9a-fA-F]{4}|.)", replace, value)
    return text.encode("utf-16", "surrogatepass").decode("utf-16")


def escape_property(value, key=False):
    text = str(value).lower() if type(value) is bool else str(value)
    result = []
    for char in text:
        if char == "\\":
            result.append("\\\\")
        elif char == " ":
            result.append("\\ ")
        elif char == "\t":
            result.append("\\t")
        elif key and char in ":=#!":
            result.append("\\" + char)
        elif ord(char) > 126 or ord(char) < 32:
            encoded = char.encode("utf-16-be")
            result.extend("\\u" + encoded[i:i + 2].hex() for i in range(0, len(encoded), 2))
        else:
            result.append(char)
    return "".join(result)


def property_records(text):
    lines = text.splitlines(keepends=True)
    index = 0
    records = []
    while index < len(lines):
        start = index
        logical = lines[index].rstrip("\r\n")
        while (len(logical) - len(logical.rstrip("\\"))) % 2 and index + 1 < len(lines):
            index += 1
            logical = logical[:-1] + lines[index].rstrip("\r\n").lstrip(" \t\f")
        match = re.match(r"^[ \t\f]*((?:\\.|[^\\\s:=#!])+)(?:[ \t\f]*[:=][ \t\f]*|[ \t\f]+|$)(.*)$", logical)
        if match:
            records.append((unescape_property(match[1]), match[2], start, index + 1))
        index += 1
    return lines, records


def validate_settings(changes, fields=None):
    fields = fields if fields is not None else SERVER_FIELDS
    if not isinstance(changes, dict) or not changes or set(changes) - fields.keys():
        raise PanelError("Nur die angezeigten Minecraft-Einstellungen dürfen geändert werden.")
    for key, value in changes.items():
        try:
            if protected_property(key):
                raise ValueError("Geschützte Minecraft-Eigenschaften dürfen nicht geändert werden.")
            validate_field(fields[key], value)
        except ValueError as error:
            raise PanelError(str(error)) from None


def minecraft_fields(text):
    values, fields = dict(SETTINGS), {key: field_for(key, value) for key, value in SETTINGS.items()}
    _, records = property_records(text)
    for key, raw, _, _ in records:
        if protected_property(key):
            continue
        value = unescape_property(raw)
        field = field_for(key, value)
        if field["type"] == "boolean":
            value = value.lower() == "true"
        elif field["type"] == "integer":
            try:
                value = int(value)
            except ValueError:
                raise PanelError("Minecraft enthält einen ungültigen Zahlenwert.") from None
        values[key], fields[key] = value, field
    return values, fields


def properties_content(original, submitted=None):
    lines, before = property_records(original)
    protected = {}
    newline = "\r\n" if "\r\n" in original else "\n"
    for key, raw, start, end in before:
        if protected_property(key):
            protected.setdefault(key, []).append((raw, lines[start:end]))
    if submitted is None:
        for key, _, start, end in reversed(before):
            if protected_property(key):
                escaped = escape_property(key, key=True)
                lines[start:end] = [escaped + "=" + PROTECTED + newline]
        return "".join(lines)
    output, records = property_records(submitted)
    counts, edits = {}, []
    for key, raw, start, end in records:
        if protected_property(key):
            index = counts.get(key, 0)
            if key not in protected or index >= len(protected[key]):
                raise PanelError("Neue geschützte Eigenschaften sind nicht erlaubt.")
            old, exact = protected[key][index]
            if unescape_property(raw) != PROTECTED and raw != old and unescape_property(raw) != unescape_property(old):
                raise PanelError("Geschützte Eigenschaften dürfen nicht geändert werden.")
            counts[key] = index + 1
            edits.append((start, end, exact))
    if any(counts.get(key, 0) != len(records) for key, records in protected.items()):
        raise PanelError("Geschützte Eigenschaften dürfen nicht entfernt werden.")
    for start, end, exact in reversed(edits):
        output[start:end] = exact
    return "".join(output)


def create_app(test_config=None):
    app = Flask(__name__)
    app.config.update(
        DATA_DIR=os.environ.get("DATA_DIR", "/game"), STATE_DIR=os.environ.get("STATE_DIR", "/state"),
        AGH_BASE_URL=os.environ.get("AGH_BASE_URL", "http://dns:3000"),
        MAP_BASE_URL=os.environ.get("MAP_BASE_URL", "http://minecraft-ark:8100"),
        TRUSTED_HOSTS=[host.strip() for host in os.environ.get("PANEL_TRUSTED_HOSTS", "127.0.0.1,localhost").split(",") if host.strip()],
        MAX_CONTENT_LENGTH=1024 * 1024, MAX_FORM_MEMORY_SIZE=256 * 1024, MAX_FORM_PARTS=20,
        SESSION_COOKIE_NAME="ark_panel_session", SESSION_COOKIE_HTTPONLY=True,
        SESSION_COOKIE_SAMESITE="Strict", PERMANENT_SESSION_LIFETIME=timedelta(days=30),
        SESSION_REFRESH_EACH_REQUEST=False,
    )
    if test_config:
        app.config.update(test_config)
    if not all(app.config.get(key) for key in ["AUTH_USERNAME", "AUTH_PASSWORD_HASH", "SECRET_KEY"]):
        try:
            credentials = json.loads(Path(os.environ.get("PANEL_CREDENTIALS_FILE", "/run/secrets/panel.json")).read_text())
            for source, target in [("username", "AUTH_USERNAME"), ("password_hash", "AUTH_PASSWORD_HASH"), ("session_secret", "SECRET_KEY")]:
                app.config[target] = credentials[source]
        except (OSError, ValueError, KeyError, TypeError):
            raise RuntimeError("Gültige Panel-Zugangsdaten fehlen.") from None
    if not all(isinstance(app.config[key], str) and app.config[key] for key in ["AUTH_USERNAME", "AUTH_PASSWORD_HASH", "SECRET_KEY"]) or len(app.config["SECRET_KEY"]) < 32:
        raise RuntimeError("Panel-Zugangsdaten oder Sitzungsgeheimnis sind ungültig.")
    base_url = app.config["AGH_BASE_URL"].rstrip("/")
    parsed_base = urlsplit(base_url)
    if parsed_base.scheme not in ["http", "https"] or not parsed_base.hostname or parsed_base.username or parsed_base.password or parsed_base.path or parsed_base.query or parsed_base.fragment:
        raise RuntimeError("Ungültiges internes DNS-Verwaltungsziel.")
    map_base = app.config["MAP_BASE_URL"].rstrip("/")
    parsed_map = urlsplit(map_base)
    if (parsed_map.scheme not in ["http", "https"] or not parsed_map.hostname or parsed_map.username
            or parsed_map.password or parsed_map.path or parsed_map.query or parsed_map.fragment):
        raise RuntimeError("Ungültiges internes Kartenziel.")
    data_dir = Path(app.config["DATA_DIR"]).resolve()
    state_dir = Path(app.config["STATE_DIR"])
    client = app.config.get("DOCKER_CLIENT")
    if client is None:
        client = docker.from_env(timeout=120)
    write_lock = threading.RLock()
    login_lock = threading.Lock()
    failures = OrderedDict()
    map_lock = threading.Lock()
    map_cache = {"expires": 0, "data": None}
    auth_path = state_dir / "panel-auth.json"

    def auth_hash():
        try:
            descriptor = os.open(auth_path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
            with os.fdopen(descriptor, "r", encoding="utf-8") as source:
                metadata = os.fstat(source.fileno())
                if not stat.S_ISREG(metadata.st_mode) or metadata.st_size > 4096:
                    raise ValueError("invalid credentials file")
                value = json.loads(source.read(4097))
            password_hash = value["password_hash"]
            if (set(value) != {"password_hash"} or not isinstance(password_hash, str)
                    or not re.fullmatch(r"(?:scrypt:\d+:\d+:\d+|pbkdf2:sha256:\d+)\$[A-Za-z0-9]+\$[a-f0-9]{64,128}", password_hash)):
                raise ValueError("invalid password hash")
            return password_hash
        except (OSError, ValueError, KeyError, TypeError):
            raise PanelError("Die gespeicherten Zugangsdaten sind nicht sicher lesbar.", 503) from None

    def save_auth_hash(password_hash):
        temporary = None
        try:
            state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
            if auth_path.is_symlink():
                raise OSError("linked credentials")
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=state_dir, prefix=".panel-auth-", delete=False) as output:
                temporary = Path(output.name)
                os.fchmod(output.fileno(), 0o600)
                json.dump({"password_hash": password_hash}, output)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, auth_path)
        except OSError:
            raise PanelError("Das Passwort konnte nicht sicher gespeichert werden. Es bleibt unverändert.", 503) from None
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)

    if not auth_path.exists() and not auth_path.is_symlink():
        save_auth_hash(app.config["AUTH_PASSWORD_HASH"])

    def credential_revision(password_hash):
        return hashlib.sha256(password_hash.encode("utf-8")).hexdigest()

    def signed_in(password_hash):
        session.clear()
        session.permanent = True
        session["authenticated"] = True
        session["credential_revision"] = credential_revision(password_hash)
        session["csrf"] = secrets.token_urlsafe(32)
        return jsonify(ok=True, csrf=session["csrf"], username=app.config["AUTH_USERNAME"])

    def login_attempt():
        address = request.remote_addr or "unknown"
        now = time.monotonic()
        for key in list(failures):
            failures[key] = [attempt for attempt in failures[key] if now - attempt < 60]
            if not failures[key]:
                del failures[key]
        if len(failures.get(address, [])) >= 5:
            raise PanelError("Zu viele Anmeldeversuche. Bitte eine Minute warten.", 429)
        return address, now

    def failed_attempt(address, now):
        failures.setdefault(address, []).append(now)
        if len(failures) > 4096:
            failures.popitem(last=False)

    def payload():
        value = request.get_json(silent=True)
        if not isinstance(value, dict):
            raise PanelError("Eine JSON-Zuordnung wird erwartet.")
        return value

    def container(service_id):
        if service_id not in SERVICES:
            raise PanelError("Unbekannter Dienst.", 404)
        item = client.containers.get(SERVICES[service_id])
        item.reload()
        labels = item.attrs.get("Config", {}).get("Labels") or {}
        if labels.get("com.docker.compose.project") != "minecraft-ark" and labels.get("com.docker.stack.namespace") != "minecraft-ark":
            raise PanelError("Der Dienst gehört nicht zum Minecraft-Stack.", 403)
        return item

    def rcon(command):
        item = container("minecraft")
        if item.status != "running":
            raise PanelError("Minecraft ist nicht gestartet.", 409)
        try:
            properties = {key: unescape_property(raw) for key, raw, _, _ in
                          property_records(file_path("server.properties").read_text(encoding="utf-8"))[1]}
            password = properties.get("rcon.password", "")
            if not password:
                raise ValueError("Missing RCON credentials")
            return rcon_command(SERVICES["minecraft"], int(properties.get("rcon.port", "25575")), password, command)
        except (RconError, OSError, UnicodeError, ValueError):
            raise PanelError("Der Minecraft-Befehl konnte nicht ausgeführt werden.", 502)

    def bridge(command):
        output = rcon("arkpanel " + command)
        marker = output.find("ARKPANEL ")
        if marker < 0:
            raise PanelError("Die erweiterte Serververwaltung ist noch nicht erreichbar.", 503)
        try:
            value, _ = json.JSONDecoder().raw_decode(output[marker + 9:])
        except (ValueError, TypeError):
            raise PanelError("Der Server hat unvollständige Verwaltungsdaten geliefert.", 502) from None
        if not isinstance(value, dict):
            raise PanelError("Ungültige Verwaltungsdaten vom Server.", 502)
        if value.get("error"):
            raise PanelError("Die Serverabfrage konnte nicht abgeschlossen werden. Bitte erneut versuchen.", 503)
        return value

    def observed_at():
        return datetime.now(timezone.utc).isoformat()

    def runtime_settings(component):
        observed = observed_at()
        unavailable = dict(available=False, observed_at=observed, settings={}, scopes={},
                           message="Live-Werte derzeit nicht verfügbar.")
        try:
            snapshot = bridge("settings").get(component)
        except (PanelError, docker.errors.DockerException):
            return unavailable
        if not isinstance(snapshot, dict) or snapshot.get("available") is not True or not isinstance(snapshot.get("settings"), dict):
            return unavailable
        allowed = SERVER_FIELDS if component == "minecraft" else PLUGIN_SETTINGS[component][1]
        values = {}
        for key, value in snapshot["settings"].items():
            if key not in allowed:
                continue
            kind = SERVER_FIELDS[key]["type"] if component == "minecraft" else type(allowed[key])
            valid = ((kind in ("boolean", bool) and type(value) is bool)
                     or (kind in ("integer", int) and type(value) is int)
                     or (kind in ("choice", "string", str) and isinstance(value, str) and len(value) <= 4096))
            if valid:
                values[key] = value
        if not values:
            return unavailable
        scopes = {key: value for key, value in snapshot.get("scopes", {}).items()
                  if key in values and value == "primary_world"} if isinstance(snapshot.get("scopes"), dict) else {}
        return dict(available=True, observed_at=observed, settings=values, scopes=scopes)

    def world_record(world_id):
        if not isinstance(world_id, str) or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", world_id):
            raise PanelError("Ungültige Welt.")
        worlds = bridge("worlds").get("worlds", [])
        world = next((world for world in worlds if world.get("key") == world_id), None)
        if world is None:
            raise PanelError("Die Welt ist nicht geladen.", 404)
        return world

    def native(command):
        answer = rcon(command)
        plain = re.sub(r"\x1b\[[0-9;]*m|§.", "", answer)
        failure = (r"(?im)^(?:\[[^\]\r\n]{1,80}\]\s*)*(?:unknown(?: or incomplete)? command\b|"
                   r"incorrect argument\b|no (?:player|entity|profile|targets?) (?:was |were )?(?:found|matched)\b|"
                   r"could not\b|couldn't\b|error:|failed(?: to)?\b|invalid argument\b|"
                   r"(?:integer|double|float) must not\b|this teleport (?:would be|is) unsafe\b|"
                   r"the new spawn location is unsafe\b)")
        if re.search(failure, plain):
            raise PanelError("Minecraft hat die Änderung abgelehnt: " + plain.strip()[:600], 422)
        return plain.strip()

    def world_name(world):
        name = world.get("name")
        if not isinstance(name, str) or not re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", name):
            raise PanelError("Der Weltname kann für diese Verwaltungsaktion nicht verwendet werden.", 502)
        return name

    def number(value, minimum, maximum):
        if type(value) not in [int, float] or not math.isfinite(value) or not minimum <= value <= maximum:
            raise PanelError("Koordinate oder Zahlenwert liegt außerhalb des erlaubten Bereichs.")
        return format(value, ".12g")

    def file_path(relative):
        intended = data_dir / relative
        if intended.resolve() != intended or not intended.resolve().is_relative_to(data_dir):
            raise PanelError("Verknüpfte Konfigurationspfade sind nicht erlaubt.")
        if not intended.is_file():
            raise PanelError("Die Konfigurationsdatei ist noch nicht vorhanden.", 404)
        return intended

    def read_file(path, limit):
        with path.open("rb") as source:
            content = source.read(limit + 1)
        if len(content) > limit:
            raise PanelError("Die Konfigurationsdatei ist zu groß.", 413)
        return content

    def save_file(path, original, content):
        backups = state_dir / "config-backups"
        backups.mkdir(parents=True, exist_ok=True, mode=0o700)
        backup = backups / (path.name + "." + str(time.time_ns()) + "." + secrets.token_hex(4) + ".bak")
        descriptor = os.open(backup, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, "wb") as output:
            output.write(original)
            output.flush()
            os.fsync(output.fileno())
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".ark-panel-", delete=False) as output:
                temporary = output.name
                os.fchmod(output.fileno(), path.stat().st_mode & 0o777)
                output.write(content)
                output.flush()
                os.fsync(output.fileno())
            os.replace(temporary, path)
        finally:
            if temporary and os.path.exists(temporary):
                os.unlink(temporary)

    @app.before_request
    def protect():
        if session.get("authenticated"):
            revision = session.get("credential_revision")
            if not isinstance(revision, str) or not hmac.compare_digest(revision.encode("utf-8"), credential_revision(auth_hash()).encode("utf-8")):
                session.clear()
        private = ((request.path.startswith("/api/") and request.path not in ["/api/session", "/api/login"])
                   or request.path in ["/dns", "/map"] or request.path.startswith(("/dns/", "/map/")))
        if private and not session.get("authenticated"):
            raise PanelError("Bitte zuerst anmelden.", 401)
        if request.method not in ["GET", "HEAD", "OPTIONS"]:
            if request.headers.get("Origin") != request.host_url.rstrip("/"):
                raise PanelError("Die Anfrage stammt nicht von dieser Verwaltungsseite.", 403)
            if request.path.startswith("/api/") and request.path != "/api/login":
                expected = session.get("csrf", "")
                supplied = request.headers.get("X-CSRF-Token", "")
                if not expected or not hmac.compare_digest(expected.encode("utf-8"), supplied.encode("utf-8")):
                    raise PanelError("Sicherheitsprüfung fehlgeschlagen. Bitte neu anmelden.", 403)

    @app.after_request
    def headers(response):
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "same-origin"
        response.headers["X-Frame-Options"] = "SAMEORIGIN"
        return response

    @app.errorhandler(PanelError)
    def panel_error(error):
        return jsonify(error=error.message), error.status

    @app.errorhandler(HTTPException)
    def http_error(error):
        messages = {400: "Ungültige Anfrage oder nicht erlaubter Host.", 404: "Nicht gefunden.", 405: "Methode nicht erlaubt.", 413: "Die Anfrage ist zu groß."}
        return jsonify(error=messages.get(error.code, "Die Anfrage konnte nicht verarbeitet werden.")), error.code

    @app.errorhandler(docker.errors.DockerException)
    def docker_error(error):
        return jsonify(error="Der Containerdienst ist derzeit nicht verfügbar."), 503

    @app.errorhandler(UnicodeError)
    def unicode_error(error):
        return jsonify(error="Der Text muss gültige Unicode-Zeichen enthalten."), 400

    @app.errorhandler(Exception)
    def internal_error(error):
        app.logger.error("Interner Verwaltungsfehler; keine Anfragedaten protokolliert.")
        return jsonify(error="Die Aktion konnte nicht sicher abgeschlossen werden."), 500

    @app.get("/")
    def index():
        return render_template("index.html")

    @app.get("/healthz")
    def health():
        return jsonify(status="ok")

    @app.get("/api/session")
    def session_info():
        authenticated = bool(session.get("authenticated"))
        return jsonify(authenticated=authenticated, csrf=session.get("csrf") if authenticated else None, username=app.config["AUTH_USERNAME"] if authenticated else None)

    @app.post("/api/login")
    def login():
        value = payload()
        username, password = value.get("username"), value.get("password")
        if not isinstance(username, str) or not isinstance(password, str) or len(username) > 128 or len(password) > 1024:
            raise PanelError("Benutzername oder Passwort sind ungültig.")
        with login_lock:
            address, now = login_attempt()
            password_hash = auth_hash()
            valid_password = check_password_hash(password_hash, password)
            if not hmac.compare_digest(username.encode("utf-8"), app.config["AUTH_USERNAME"].encode("utf-8")) or not valid_password:
                failed_attempt(address, now)
                raise PanelError("Benutzername oder Passwort stimmen nicht.", 401)
            failures.pop(address, None)
            return signed_in(password_hash)

    @app.post("/api/password")
    def change_password():
        value = payload()
        current, new, confirmation = value.get("current_password"), value.get("new_password"), value.get("confirm_password")
        if not isinstance(current, str) or not 1 <= len(current) <= 1024:
            raise PanelError("Bitte das aktuelle Passwort eingeben (höchstens 1024 Zeichen).")
        if not isinstance(new, str) or not 12 <= len(new) <= 1024:
            raise PanelError("Das neue Passwort muss 12 bis 1024 Zeichen lang sein.")
        if not isinstance(confirmation, str) or new != confirmation:
            raise PanelError("Die neuen Passwörter stimmen nicht überein.")
        with login_lock:
            address, now = login_attempt()
            password_hash = auth_hash()
            if session.get("credential_revision") != credential_revision(password_hash):
                session.clear()
                raise PanelError("Bitte erneut anmelden.", 401)
            if not check_password_hash(password_hash, current):
                failed_attempt(address, now)
                raise PanelError("Das aktuelle Passwort stimmt nicht.", 403)
            new_hash = generate_password_hash(new)
            save_auth_hash(new_hash)
            failures.pop(address, None)
            return signed_in(new_hash)

    @app.post("/api/logout")
    def logout():
        session.clear()
        return jsonify(ok=True)

    @app.get("/api/status")
    def status():
        services = []
        minecraft_running = False
        for service_id, name in SERVICES.items():
            try:
                item = container(service_id)
                state = item.attrs.get("State", {})
                services.append(dict(id=service_id, name=name, state=item.status, health=state.get("Health", {}).get("Status"), started_at=state.get("StartedAt"), restart_count=item.attrs.get("RestartCount", 0)))
                if service_id == "minecraft":
                    minecraft_running = item.status == "running"
            except (docker.errors.DockerException, PanelError):
                services.append(dict(id=service_id, name=name, state="unavailable", health=None, started_at=None, restart_count=0))
        players = "Minecraft ist derzeit offline oder nicht erreichbar."
        if minecraft_running:
            try:
                players = rcon("list")
            except (docker.errors.DockerException, PanelError):
                players = "Spielerliste derzeit nicht erreichbar."
        return jsonify(services=services, players=players)

    def map_metadata():
        keys = ("min_x", "max_x", "min_z", "max_z")

        def bounds(value):
            if not isinstance(value, dict):
                return None
            numbers = [value.get(key) for key in keys]
            if (not all(type(number) in (int, float) and abs(number) <= 30000000 and math.isfinite(number) for number in numbers)
                    or numbers[0] >= numbers[1] or numbers[2] >= numbers[3]):
                return None
            return dict(zip(keys, numbers))

        def date(value):
            return value if isinstance(value, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}(?:T[0-9:.+Z-]+)?", value) else None

        worlds = {key: dict(id=key, name=name, terrain=False, min_x=-10240, max_x=10240, min_z=-10240, max_z=10240)
                  for key, name in [("minecraft:overworld", "ARK Survival"), ("minecraft:ark_creative", "Kreativwelt"),
                                    ("minecraft:ark_exploration", "Erkundungswelt")]}
        document = {}
        try:
            loaded = json.loads(read_file(file_path("admin-map/worlds.json"), 16384))
            if isinstance(loaded, dict):
                document = loaded
        except (OSError, PanelError, ValueError):
            pass
        configured = document.get("worlds", [])
        for world in configured[:10] if isinstance(configured, list) else []:
            if (not isinstance(world, dict) or world.get("id") not in worlds or not bounds(world)
                    or not isinstance(world.get("name"), str) or not 1 <= len(world["name"]) <= 80
                    or any(ord(char) < 32 for char in world["name"])):
                continue
            worlds[world["id"]].update(name=world["name"], **bounds(world))
            worlds[world["id"]]["terrain_bounds"] = bounds(world.get("terrain_bounds"))
            worlds[world["id"]]["terrain_updated_at"] = date(world.get("terrain_updated_at"))
        for key, world in worlds.items():
            rendered = world.get("terrain_bounds")
            if not rendered or not all(rendered[name] >= world[name] if name.startswith("min_") else rendered[name] <= world[name] for name in keys):
                world["terrain_bounds"] = {name: world[name] for name in keys}
            if not world.get("terrain_updated_at"):
                world["terrain_updated_at"] = date(document.get("terrain_updated_at"))
            try:
                modified = file_path(MAP_TERRAINS[key]).stat().st_mtime
                world["terrain"] = True
                if not world["terrain_updated_at"]:
                    world["terrain_updated_at"] = time.strftime("%Y-%m-%d", time.gmtime(modified))
            except (OSError, PanelError):
                pass
        return list(worlds.values()), worlds["minecraft:overworld"]["terrain_updated_at"]

    @app.get("/api/map/terrain")
    def map_terrain():
        world = request.args.get("world", "minecraft:overworld")
        if world not in MAP_TERRAINS:
            raise PanelError("Für diese Welt ist keine Geländekarte freigegeben.", 404)
        return send_file(file_path(MAP_TERRAINS[world]), mimetype="image/png", conditional=False)

    @app.get("/api/map/players")
    def map_players():
        worlds, terrain_updated = map_metadata()
        with map_lock:
            now = time.monotonic()
            try:
                if container("minecraft").status != "running":
                    raise PanelError("Minecraft ist derzeit offline.", 409)
                if map_cache["data"] is not None and now < map_cache["expires"]:
                    return jsonify(map_cache["data"])
                players = parse_player_locations(rcon("execute as @a run data get entity @s Pos"), rcon("execute as @a run data get entity @s Dimension"))
                names = {world["id"]: world["name"] for world in worlds}
                names.update({"minecraft:the_nether": "Nether", "minecraft:the_end": "End"})
                for player in players:
                    player["world"] = names.get(player["dimension"], "Unbekannte Dimension")
                result = dict(online=True, players=players, worlds=worlds, updated_at=time.time(), terrain_updated_at=terrain_updated)
                map_cache.update(data=result, expires=now + 2.5)
            except (docker.errors.DockerException, PanelError) as error:
                map_cache.update(data=None, expires=0)
                result = dict(online=False, players=[], worlds=worlds, updated_at=time.time(), terrain_updated_at=terrain_updated,
                              error=error.message if isinstance(error, PanelError) else "Minecraft ist derzeit nicht erreichbar.")
        return jsonify(result)

    @app.post("/api/services/<service_id>/<action>")
    def service_action(service_id, action):
        if action not in ["start", "stop", "restart"] or service_id not in SERVICES:
            raise PanelError("Unbekannte Dienstaktion.", 404)
        payload()
        with write_lock:
            item = container(service_id)
            stop_timeout = 120 if service_id == "minecraft" else 20
            if action == "start":
                item.start()
            elif action == "stop":
                item.stop(timeout=stop_timeout)
            else:
                item.restart(timeout=stop_timeout)
        return jsonify(ok=True)

    @app.get("/api/logs/<service_id>")
    def logs(service_id):
        try:
            tail = int(request.args.get("tail", "200"))
        except ValueError:
            raise PanelError("Die Logzeilenzahl ist ungültig.") from None
        if not 20 <= tail <= 500:
            raise PanelError("Bitte 20 bis 500 Logzeilen wählen.")
        content = container(service_id).logs(tail=tail, timestamps=True)
        return jsonify(text=output_text(content))

    @app.post("/api/console")
    def console():
        command = payload().get("command")
        if not isinstance(command, str) or not command.strip() or len(command) > 500 or any(char in command for char in "\r\n\x00"):
            raise PanelError("Bitte einen einzeiligen Befehl mit höchstens 500 Zeichen eingeben.")
        command.encode("utf-8")
        return jsonify(output=rcon(command))

    @app.post("/api/reload/<target>")
    def reload_plugin(target):
        if target not in RELOADS:
            raise PanelError("Unbekanntes Neuladeziel.", 404)
        payload()
        return jsonify(ok=True, output=rcon(RELOADS[target]))

    @app.route("/api/settings/minecraft", methods=["GET", "PUT"])
    def minecraft_settings():
        body = payload() if request.method == "PUT" else None
        changes = body.get("settings") if body else None
        with write_lock:
            path = file_path("server.properties")
            original = read_file(path, 1024 * 1024)
            try:
                text = original.decode("utf-8")
                lines, records = property_records(text)
                values, fields = minecraft_fields(text)
            except (UnicodeError, ValueError):
                raise PanelError("server.properties enthält ungültige Text- oder Unicode-Escapes.") from None
            if request.method == "GET":
                present = {key for key, _, _, _ in records}
                result = dict(settings=values, fields=list(fields.values()), version=hashlib.sha256(original).hexdigest(),
                              sources={key: "file" if key in present else "default" for key in values}, observed_at=observed_at())
            else:
                result = None
            if request.method == "PUT" and "version" in body and body["version"] != hashlib.sha256(original).hexdigest():
                raise PanelError("Die Datei wurde inzwischen geändert. Bitte neu laden.", 409)
            if request.method == "PUT":
                validate_settings(changes, fields)
                newline = "\r\n" if "\r\n" in text else "\n"
                seen = set()
                for key, _, start, end in reversed(records):
                    if key in changes:
                        lines[start:end] = [escape_property(key, key=True) + "=" + escape_property(changes[key]) + newline]
                        seen.add(key)
                updated = "".join(lines)
                for key in changes.keys() - seen:
                    if updated and not updated.endswith(("\r", "\n")):
                        updated += newline
                    updated += escape_property(key, key=True) + "=" + escape_property(changes[key]) + newline
                save_file(path, original, updated.encode("utf-8"))
        if result is not None:
            result["runtime"] = runtime_settings("minecraft")
            return jsonify(result)
        return jsonify(ok=True, restart_required=True, version=hashlib.sha256(updated.encode("utf-8")).hexdigest())

    @app.get("/api/configs")
    def configs():
        entries = []
        for key, metadata in CONFIGS.items():
            try:
                file_path(metadata["path"])
                available = True
            except PanelError:
                available = False
            entries.append(dict(id=key, available=available, description=config_description(key), **{field: value for field, value in metadata.items() if field not in ["path", "description"]}))
        return jsonify(configs=entries)

    @app.route("/api/settings/<plugin>", methods=["GET", "PUT"])
    def plugin_settings(plugin):
        if plugin not in PLUGIN_SETTINGS:
            raise PanelError("Unbekannte Plugin-Einstellungen.", 404)
        config_id, defaults = PLUGIN_SETTINGS[plugin]
        body = payload() if request.method == "PUT" else None
        changes = body.get("settings") if body else None
        if request.method == "PUT":
            validate_plugin_settings(plugin, changes)
        with write_lock:
            path = file_path(CONFIGS[config_id]["path"])
            original = read_file(path, MAX_CONFIG)
            text = original.decode("utf-8")
            document = validate_yaml(config_id, text)
            if plugin == "geyser" and document.get("config-version") != 12:
                raise PanelError("Das Geyser-Formular benötigt Konfigurationsversion 12; bitte den erweiterten Editor verwenden.")
            if request.method == "GET":
                values, sources = {}, {}
                for field, default in defaults.items():
                    current = document
                    sources[field] = "file"
                    for part in field.split("."):
                        if not isinstance(current, dict):
                            raise PanelError("Eigene YAML-Strukturen bitte im erweiterten Editor bearbeiten.")
                        if part not in current:
                            current = default
                            sources[field] = "default"
                            break
                        current = current[part]
                    values[field] = current
                validate_plugin_settings(plugin, values)
                result = dict(settings=values, version=hashlib.sha256(original).hexdigest(),
                              sources=sources, observed_at=observed_at())
            else:
                result = None
            if request.method == "PUT" and "version" in body and body["version"] != hashlib.sha256(original).hexdigest():
                raise PanelError("Die Datei wurde inzwischen geändert. Bitte neu laden.", 409)
            if request.method == "PUT":
                for field, value in changes.items():
                    text = edit_plugin_scalar(text, field, value)
                validate_yaml(config_id, text)
                try:
                    text = restore_content("yaml", config_id, original.decode("utf-8"), text)
                except ValueError as error:
                    raise PanelError(str(error)) from None
                save_file(path, original, text.encode("utf-8"))
        if result is not None:
            result["runtime"] = runtime_settings(plugin)
            return jsonify(result)
        return jsonify(ok=True, restart_required=False, reload_target=plugin, version=hashlib.sha256(text.encode("utf-8")).hexdigest())

    @app.route("/api/configs/<config_id>", methods=["GET", "PUT"])
    def config_file(config_id):
        if config_id not in CONFIGS:
            raise PanelError("Unbekannte Konfiguration.", 404)
        metadata = CONFIGS[config_id]
        body = payload() if request.method == "PUT" else None
        with write_lock:
            path = file_path(metadata["path"])
            original = read_file(path, MAX_CONFIG)
            version = hashlib.sha256(original).hexdigest()
            if request.method == "PUT":
                if metadata["requires_stopped"] and container("minecraft").status not in ["exited", "created", "dead"]:
                    raise PanelError("Diese Konfiguration bitte nur bei vollständig gestopptem Minecraft speichern.", 409)
                if "version" in body and body["version"] != version:
                    raise PanelError("Die Datei wurde inzwischen geändert. Bitte neu laden.", 409)
            try:
                text = original.decode("utf-8")
                kind = metadata["format"]
                if kind == "yaml":
                    validate_yaml(config_id, text)
                elif kind == "json":
                    validate_json(text)
                else:
                    property_records(text)
                if request.method == "GET":
                    content = properties_content(text) if kind == "properties" else redact_content(kind, config_id, text)
                    return jsonify(id=config_id, name=metadata["name"], content=content, version=version, format=kind)
                content = body.get("content")
                if not isinstance(content, str):
                    raise PanelError("Konfiguration muss Text enthalten.")
                if len(content.encode("utf-8")) > MAX_CONFIG:
                    raise PanelError("Konfiguration darf höchstens 256 KiB groß sein.", 413)
                if kind == "properties":
                    content = properties_content(text, content)
                    values, fields = minecraft_fields(content)
                    actual = {key: values[key] for key, _, _, _ in property_records(content)[1] if not protected_property(key)}
                    if actual:
                        validate_settings(actual, fields)
                else:
                    validate_yaml(config_id, content) if kind == "yaml" else validate_json(content)
                    content = restore_content(kind, config_id, text, content)
                save_file(path, original, content.encode("utf-8"))
            except ValueError as error:
                raise PanelError(str(error)) from None
        return jsonify(ok=True, restart_required=metadata["restart_required"], reload_target=metadata["reload_target"], version=hashlib.sha256(content.encode("utf-8")).hexdigest())

    @app.post("/api/console/complete")
    def complete_console():
        data = payload()
        text, cursor = data.get("input"), data.get("cursor")
        if not isinstance(text, str) or any(char in text for char in "\r\n\x00") or type(cursor) is not int:
            raise PanelError("Ungültige Konsoleneingabe.")
        encoded = text.encode("utf-16-le")
        if len(encoded) > 1000 or not 0 <= cursor <= len(encoded) // 2:
            raise PanelError("Ungültige Cursorposition oder zu langer Befehl.")
        encoded[:cursor * 2].decode("utf-16-le")
        token = base64.urlsafe_b64encode(text.encode("utf-8")).decode().rstrip("=")
        try:
            result = bridge(f"complete {cursor} {token}".rstrip())
            deadline = time.monotonic() + 2
            while result.get("ready") is False and time.monotonic() < deadline:
                job = result.get("id")
                if not isinstance(job, str) or not re.fullmatch(r"[a-f0-9-]{36}", job):
                    raise PanelError("Ungültige Antwort der Befehlsvorschläge.", 502)
                time.sleep(.1)
                result = bridge("result " + job)
        except PanelError as error:
            if error.status in [409, 502, 503]:
                return jsonify(available=False, ready=True, suggestions=[], message=error.message)
            raise
        if result.get("ready") is False:
            return jsonify(available=False, ready=True, suggestions=[], message="Die Vorschläge brauchen noch einen Moment.")
        start, end = result.get("start"), result.get("end")
        if type(start) is not int or type(end) is not int or not 0 <= start <= end <= len(encoded) // 2:
            raise PanelError("Ungültiger Ersetzungsbereich der Befehlsvorschläge.", 502)
        suggestions = result.get("suggestions")
        if not isinstance(suggestions, list):
            raise PanelError("Ungültige Befehlsvorschläge.", 502)
        safe = []
        for suggestion in suggestions[:50]:
            if (not isinstance(suggestion, dict) or not isinstance(suggestion.get("text"), str)
                    or len(suggestion["text"]) > 500 or any(ord(c) < 32 for c in suggestion["text"])):
                continue
            safe.append({key: value for key, value in suggestion.items()
                         if key in ["text", "tooltip"] and isinstance(value, str)})
        return jsonify(available=True, ready=True, start=start, end=end, suggestions=safe)

    @app.get("/api/worlds")
    def worlds():
        result = bridge("worlds")
        for world in result.get("worlds", []):
            for rule in world.get("rules", []):
                rule.update(rule_help(rule["name"]))
        result["observed_at"] = observed_at()
        return jsonify(result)

    @app.put("/api/worlds/<path:world_id>")
    def update_world(world_id):
        data = payload()
        with write_lock:
            world = world_record(world_id)
            action, value = data.get("action"), data.get("value")
            prefix = f"execute in {world_id} run "
            commands = []
            if action == "rule":
                key = data.get("name")
                rule = next((rule for rule in world.get("rules", []) if rule.get("name") == key), None)
                if rule is None or not re.fullmatch(r"[a-z0-9_.-]+:[a-z0-9_./-]+", key):
                    raise PanelError("Diese Weltregel ist nicht vorhanden.")
                if ((rule.get("type") == "boolean" and type(value) is not bool)
                        or (rule.get("type") == "integer" and (type(value) is not int or not -2147483648 <= value <= 2147483647))
                        or rule.get("type") not in ["boolean", "integer"]):
                    raise PanelError("Der Wert passt nicht zum Typ der Weltregel.")
                literal = str(value).lower()
                if key == "minecraft:pvp":
                    commands = [f"mv modify {world_name(world)} set pvp {literal}"]
                else:
                    commands = [prefix + f"gamerule {key} {literal}"]
            elif action == "difficulty":
                if value not in ["peaceful", "easy", "normal", "hard"]:
                    raise PanelError("Ungültige Schwierigkeit.")
                commands = [f"mv modify {world_name(world)} set difficulty {value}"]
            elif action == "time":
                if type(value) is not int or not 0 <= value <= 23999:
                    raise PanelError("Die Tageszeit muss zwischen 0 und 23999 liegen.")
                commands = [prefix + "time set " + str(value)]
            elif action == "weather":
                if value not in ["clear", "rain", "thunder"]:
                    raise PanelError("Ungültiges Wetter.")
                commands = [prefix + "weather " + value]
            elif action == "spawn":
                if not isinstance(value, dict):
                    raise PanelError("Bitte einen vollständigen Spawnpunkt angeben.")
                xyz = [number(value.get(key), *bounds) for key, bounds in
                       [("x", (-29999984, 29999984)), ("y", (-64, 2048)), ("z", (-29999984, 29999984)),
                        ("yaw", (-360, 360)), ("pitch", (-90, 90))]]
                # Multiverse stores the spawn itself and would replace a vanilla-only edit on restart.
                commands = [f"mv setspawn {world_name(world)}:" + ",".join(xyz)]
            elif action == "border":
                if not isinstance(value, dict):
                    raise PanelError("Bitte Größe und Mittelpunkt der Weltgrenze angeben.")
                size = number(value.get("size"), 1, 59999968)
                x = number(value.get("centerX"), -29999984, 29999984)
                z = number(value.get("centerZ"), -29999984, 29999984)
                commands = [prefix + f"worldborder center {x} {z}", prefix + f"worldborder set {size}"]
            else:
                raise PanelError("Unbekannte Weltaktion.")
            outputs = [native(command) for command in commands]
        return jsonify(ok=True, output="\n".join(outputs))

    @app.get("/api/players")
    def player_admin():
        return jsonify(bridge("players"))

    @app.post("/api/players/action")
    def player_action():
        data = payload()
        action, player = data.get("action"), data.get("player")
        if not isinstance(action, str):
            raise PanelError("Ungültige Spieleraktion.")
        if not isinstance(player, str) or not re.fullmatch(r"[A-Za-z0-9_.]{1,64}", player):
            raise PanelError("Ungültiger Spielername. Bedrock-Namen dürfen mit einem Punkt beginnen.")
        simple = {"kick": "kick", "ban": "ban", "pardon": "pardon", "whitelist-add": "whitelist add",
                  "whitelist-remove": "whitelist remove", "op": "op", "deop": "deop"}
        with write_lock:
            if action in simple:
                command = simple[action] + " " + player
                if action in ["kick", "ban"]:
                    reason = data.get("reason", "")
                    if (not isinstance(reason, str) or len(reason) > 160 or any(ord(c) < 32 for c in reason)
                            or len(reason.splitlines()) > 1):
                        raise PanelError("Der Grund muss ein kurzer einzeiliger Text sein.")
                    reason.encode("utf-8")
                    if reason.strip():
                        command += " " + reason.strip()
            elif action == "gamemode":
                mode = data.get("value")
                if mode not in ["survival", "creative", "adventure", "spectator"]:
                    raise PanelError("Ungültiger Spielmodus.")
                current = next((p for p in bridge("players").get("players", []) if p.get("name") == player), None)
                if current is None:
                    raise PanelError("Der Spieler ist nicht online.", 404)
                if mode == "creative":
                    target = world_name(world_record("minecraft:ark_creative"))
                    command = f"mv tp {player} {target}"
                elif mode == "survival" and current.get("key") == "minecraft:ark_creative":
                    target = world_name(world_record("minecraft:overworld"))
                    command = f"mv tp {player} {target}"
                else:
                    command = f"gamemode {mode} {player}"
            elif action == "world":
                destination = world_name(world_record(data.get("value")))
                command = f"mv tp {player} {destination}"
            elif action == "teleport":
                xyz = [number(data.get(key), *bounds) for key, bounds in
                       [("x", (-29999984, 29999984)), ("y", (-64, 2048)), ("z", (-29999984, 29999984))]]
                command = f"minecraft:tp {player} " + " ".join(xyz)
            else:
                raise PanelError("Unbekannte Spieleraktion.")
            output = native(command)
        return jsonify(ok=True, output=output)

    @app.get("/api/map/viewer")
    def map_viewer():
        upstream = requests.Session()
        upstream.trust_env = False
        remote = None
        try:
            remote = upstream.get(map_base + "/", stream=True, timeout=(3, 5), allow_redirects=False)
            available = remote.status_code == 200
        except requests.RequestException:
            available = False
        finally:
            if remote is not None:
                remote.close()
            upstream.close()
        return jsonify(available=available, url="/map/", provider="BlueMap", status="online" if available else "preparing",
                       notice="Gespeicherte Bauänderungen erscheinen nach der Kartenberechnung. Neue Gebiete können länger dauern."
                       if available else "Die 2D-Karte wird vorbereitet oder ist gerade nicht erreichbar.")

    @app.get("/map")
    def map_redirect():
        return redirect("/map/")

    @app.route("/map/", defaults={"path": ""}, methods=["GET", "HEAD"])
    @app.route("/map/<path:path>", methods=["GET", "HEAD"])
    def map_proxy(path):
        if "\\" in path or "\x00" in path or any(part in [".", ".."] for part in path.split("/")):
            raise PanelError("Ungültiger Kartenpfad.")
        url = map_base + "/" + quote(path, safe="/")
        if request.query_string:
            url += "?" + request.query_string.decode("ascii")
        allowed = {"accept", "accept-language", "range", "if-range", "if-none-match", "if-modified-since", "last-event-id"}
        outgoing = {key: value for key, value in request.headers if key.lower() in allowed}
        outgoing["Accept-Encoding"] = "identity"
        upstream = requests.Session()
        upstream.trust_env = False
        try:
            remote = upstream.get(url, headers=outgoing, stream=True, timeout=(5, 90), allow_redirects=False)
        except requests.RequestException:
            upstream.close()
            raise PanelError("Die 2D-Karte ist derzeit nicht erreichbar.", 502) from None
        headers = {key: value for key, value in remote.headers.items()
                   if key.lower() not in HOP_HEADERS | {"content-length", "content-encoding", "set-cookie", "cache-control"}}
        location = next((value for key, value in headers.items() if key.lower() == "location"), None)
        if location:
            destination = urlsplit(location)
            if destination.netloc and (destination.scheme, destination.netloc) != (parsed_map.scheme, parsed_map.netloc):
                remote.close()
                upstream.close()
                raise PanelError("Die Karte meldet ein unerlaubtes Weiterleitungsziel.", 502)
            headers = {key: value for key, value in headers.items() if key.lower() != "location"}
            headers["Location"] = "/map/" + destination.path.lstrip("/")
            if destination.query:
                headers["Location"] += "?" + destination.query
        headers["X-Accel-Buffering"] = "no"
        if request.method == "HEAD":
            remote.close()
            upstream.close()
            return app.response_class(status=remote.status_code, headers=headers)

        def chunks():
            try:
                sse = remote.headers.get("Content-Type", "").startswith("text/event-stream")
                # Small SSE events must arrive immediately; an 8-KiB buffer can postpone tile updates indefinitely.
                for chunk in remote.iter_content(chunk_size=1 if sse else 65536):
                    if chunk:
                        yield chunk
            finally:
                remote.close()
                upstream.close()
        return app.response_class(stream_with_context(chunks()), status=remote.status_code, headers=headers)

    @app.get("/dns")
    def dns_redirect():
        return redirect("/dns/")

    @app.route("/dns/", defaults={"path": ""}, methods=["GET", "POST", "PUT", "PATCH", "DELETE"])
    @app.route("/dns/<path:path>", methods=["GET", "POST", "PUT", "PATCH", "DELETE"])
    def dns_proxy(path):
        url = base_url + "/" + quote(path, safe="/")
        if request.query_string:
            url += "?" + request.query_string.decode("ascii")
        blocked = HOP_HEADERS | {"host", "cookie", "authorization", "x-csrf-token", "content-length", "forwarded", "accept-encoding"}
        blocked |= {item.strip().lower() for item in request.headers.get("Connection", "").split(",")}
        outgoing = {key: value for key, value in request.headers if key.lower() not in blocked and not key.lower().startswith("x-forwarded-")}
        outgoing["Accept-Encoding"] = "identity"
        try:
            with requests.Session() as upstream:
                upstream.trust_env = False
                remote = upstream.request(request.method, url, headers=outgoing, data=request.get_data(), timeout=15, allow_redirects=False)
        except requests.RequestException:
            raise PanelError("Die DNS-Verwaltung ist derzeit nicht erreichbar.", 502) from None
        blocked_response = HOP_HEADERS | {"content-length", "content-encoding", "set-cookie"}
        blocked_response |= {item.strip().lower() for item in remote.headers.get("Connection", "").split(",")}
        response = app.response_class(remote.content, status=remote.status_code)
        for key, value in remote.headers.items():
            if key.lower() in blocked_response:
                continue
            if key.lower() == "location":
                parsed = urlsplit(value)
                if parsed.netloc and (parsed.scheme, parsed.netloc) != (parsed_base.scheme, parsed_base.netloc):
                    raise PanelError("Die DNS-Verwaltung meldet ein unerlaubtes Weiterleitungsziel.", 502)
                value = "/dns/" + parsed.path.lstrip("/")
                if parsed.query:
                    value += "?" + parsed.query
                if parsed.fragment:
                    value += "#" + parsed.fragment
            response.headers[key] = value
        if remote.raw is not None and hasattr(remote.raw.headers, "getlist"):
            cookies = remote.raw.headers.getlist("Set-Cookie")
        else:
            cookies = [remote.headers["Set-Cookie"]] if "Set-Cookie" in remote.headers else []
        for cookie in cookies:
            response.headers.add("Set-Cookie", re.sub(r"(?i)(;\s*path=)/", r"\1/dns/", cookie))
        return response

    return app
