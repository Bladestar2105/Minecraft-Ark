"""Small fixed-origin client for the private, scoped ArkWardrobe API."""
import http.client
from datetime import datetime
import json
import math
import os
from pathlib import Path
import re
import stat
import time
from urllib.parse import urlsplit

if __package__:
    from .i18n import translate
else:
    from i18n import translate

MAX_BODY = 384 * 1024
MAX_RESPONSE = 8 * 1024 * 1024
SKIN_ID = r"[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"
ERRORS = {
    "invalid_request": "Ungültige Anfrage.",
    "unauthorized": "Bitte zuerst anmelden.",
    "forbidden": "Diese Aktion ist nicht erlaubt.",
    "not_found": "Nicht gefunden.",
    "stale_revision": "Die Daten wurden inzwischen geändert. Bitte neu laden.",
    "duplicate_name": "Dieser Skinname ist bereits vergeben.",
    "skin_limit": "Es sind höchstens 20 Skins pro Spieler erlaubt.",
    "active_skin": "Vor dem Löschen bitte einen anderen Skin aktivieren.",
    "busy": "Der Dienst ist ausgelastet. Bitte später erneut versuchen.",
    "signing_unconfigured": "Die Skin-Signierung ist noch nicht eingerichtet.",
    "signing_failed": "Die Skin-Signierung ist fehlgeschlagen. Die bisherige Auswahl bleibt erhalten.",
    "provider_rate_limited": "Der Signierdienst begrenzt Anfragen. Bitte später erneut versuchen.",
    "storage_unavailable": "Die Garderobe kann derzeit nicht sicher gespeichert werden.",
    "upstream_unavailable": "Die Garderobe ist derzeit nicht erreichbar.",
    "upstream_invalid": "Die Garderobe hat ungültige Daten geliefert.",
    "origin_denied": "Die Anfrage stammt nicht von dieser Seite.",
    "csrf_failed": "Sicherheitsprüfung fehlgeschlagen. Bitte neu anmelden.",
    "payload_too_large": "Die Anfrage ist zu groß.",
    "method_not_allowed": "Methode nicht erlaubt.",
    "internal_error": "Die Aktion konnte nicht sicher abgeschlossen werden.",
    "invalid_credentials": "Spielername oder Passwort stimmen nicht.",
    "invalid_recovery_code": "Der Wiederherstellungscode ist ungültig oder bereits verbraucht.",
    "invalid_password": "Das neue Passwort muss 12 bis 64 Zeichen ohne Steuerzeichen enthalten; die Bestätigung muss übereinstimmen.",
    "game_proof_required": "Bitte die Einrichtung zuerst mit /web im Spiel bestätigen.",
    "session_limit": "Zu viele Anmeldeversuche. Bitte später erneut versuchen.",
    "invalid_provider": "Bitte einen angebotenen Signierdienst auswählen.",
    "invalid_client_id": "Die Microsoft-Client-ID ist ungültig.",
    "invalid_api_key": "Der MineSkin-API-Schlüssel ist ungültig.",
    "client_id_required": "Bitte zuerst eine eigene Microsoft-Client-ID einrichten.",
    "provider_invalid_response": "Der Signierdienst hat ungültige Daten geliefert.",
    "provider_unavailable": "Der Signierdienst ist derzeit nicht erreichbar.",
    "provider_unauthorized": "Die Anmeldung beim Signierdienst ist abgelaufen oder wurde abgelehnt. Bitte erneut verbinden.",
    "provider_not_ready": "Der Signierdienst bereitet den Skin noch vor. Bitte später erneut versuchen.",
    "cancelled": "Die Signierung wurde durch eine neuere Änderung abgebrochen.",
    "invalid_signature": "Die Skin-Signatur konnte nicht sicher geprüft werden.",
    "invalid_signature_keys": "Die Skin-Signatur konnte nicht sicher geprüft werden.",
    "invalid_texture": "Die signierten Skin-Daten sind ungültig.",
    "invalid_revision": "Die Skin-Revision ist ungültig. Bitte neu laden.",
    "invalid_content_type": "Eine JSON-Anfrage wird erwartet.",
    "body_too_large": "Die Anfrage ist zu groß.",
    "service_unavailable": "Die Garderobe ist derzeit nicht erreichbar.",
    "password_change_failed": "Das Serverpasswort konnte nicht sicher geändert werden. Bitte erneut versuchen.",
    "game_login_required": "Bitte zuerst im Spiel anmelden.",
    "authentication_unavailable": "Die Serveranmeldung ist derzeit nicht erreichbar.",
    "server_unavailable": "Minecraft ist derzeit nicht erreichbar.",
    "game_join_required": "Bitte mit diesem Konto einmal dem Server beitreten, damit die bestehende Spieler-ID zugeordnet werden kann.",
    "invalid_priority": "Bitte eine angebotene Priorität auswählen.",
    "invalid_message": "Die Nachricht muss 1 bis 2000 Zeichen enthalten; Zeilenumbrüche sind erlaubt.",
    "invalid_status": "Bitte einen angebotenen Ticketstatus auswählen.",
    "support_cooldown": "Bitte vor einem neuen Ticket eine Minute warten.",
    "support_open_limit": "Es sind höchstens fünf offene Tickets pro Spieler erlaubt.",
    "support_message_limit": "Dieses Ticket hat die Grenze von 50 Nachrichten erreicht.",
    "support_closed": "Dieses Ticket ist geschlossen. Die Verwaltung kann es wieder öffnen.",
    "support_open": "Bitte das Ticket vor dem Löschen schließen.",
    "support_capacity": "Der Supportspeicher ist ausgelastet. Bitte die Serververwaltung kontaktieren.",
    "account_not_found": "Dieses Serverkonto ist nicht vorhanden.",
    "operator_required": "Für diese Anmeldung sind aktuelle OP-Rechte erforderlich.",
    "invalid_code": "Der Anmeldecode ist ungültig, abgelaufen oder für einen anderen Anmeldebereich bestimmt.",
    "operator_local_password_denied": "Das Serverpasswort wird im Spielerbereich geändert. Das lokale Administratorkonto ist ein getrenntes Konto.",
}


class WardrobeError(Exception):
    def __init__(self, code="upstream_unavailable", status=502, retry_after=None):
        self.code = code if isinstance(code, str) and code in ERRORS else "upstream_invalid"
        self.status = status if status in (400, 401, 403, 404, 405, 409, 413, 422, 429, 500, 502, 503) else 502
        if isinstance(retry_after, str) and re.fullmatch(r"[0-9]{1,4}", retry_after):
            retry_after = int(retry_after)
        self.retry_after = retry_after if self.status == 429 and type(retry_after) is int and 1 <= retry_after <= 3600 else None
        super().__init__(self.code)

    @property
    def message(self):
        return translate(ERRORS[self.code])


def read_secret(path, limit=4096):
    try:
        descriptor = os.open(Path(path), os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, "rb") as source:
            info = os.fstat(source.fileno())
            if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
                raise ValueError
            value = source.read(limit + 1)
        if len(value) > limit:
            raise ValueError
        return value.decode("utf-8")
    except (OSError, ValueError, UnicodeError, TypeError):
        raise RuntimeError("Required wardrobe credentials are missing or invalid.") from None


def fixed_origin(value):
    try:
        parsed = urlsplit(value)
        if (parsed.scheme not in ("http", "https") or not parsed.hostname or parsed.username or parsed.password
                or parsed.path not in ("", "/") or parsed.query or parsed.fragment or parsed.port == 0
                or any(c.isspace() for c in value)):
            raise ValueError
        return parsed
    except (ValueError, TypeError):
        raise RuntimeError("Invalid fixed wardrobe service origin.") from None


def allowed_request(method, path, admin=False):
    if admin:
        return (path == "/v1/admin/signing" and method in ("GET", "PUT") or
                path in ("/v1/admin/signing/connect", "/v1/admin/signing/disconnect", "/v1/admin/accounts/password",
                         "/v1/admin/auth/login", "/v1/admin/auth/check", "/v1/admin/auth/logout", "/v1/admin/auth/player-check") and method == "POST" or
                path in ("/v1/admin/accounts", "/v1/admin/modules", "/v1/admin/support") and method == "GET" or
                bool(re.fullmatch("/v1/admin/support/" + SKIN_ID, path)) and method in ("GET", "DELETE") or
                bool(re.fullmatch("/v1/admin/support/" + SKIN_ID + "/reply", path)) and method == "POST" or
                bool(re.fullmatch("/v1/admin/support/" + SKIN_ID + "/status", path)) and method == "PUT")
    if path in ("/v1/session", "/v1/profile", "/v1/account", "/v1/modules"):
        return method == "GET"
    if path in ("/v1/pair/redeem", "/v1/logout", "/v1/login", "/v1/password", "/v1/recover"):
        return method == "POST"
    if path == "/v1/recovery":
        return method in ("POST", "DELETE")
    if path == "/v1/skins":
        return method in ("GET", "POST")
    if path == "/v1/support":
        return method in ("GET", "POST")
    return bool(re.fullmatch("/v1/skins/" + SKIN_ID, path) and method in ("PUT", "DELETE") or
                re.fullmatch("/v1/skins/" + SKIN_ID + "/(?:copy|activate)", path) and method == "POST" or
                re.fullmatch("/v1/support/" + SKIN_ID, path) and method == "GET" or
                re.fullmatch("/v1/support/" + SKIN_ID + "/reply", path) and method == "POST")


def allowed_player_path(method, path):
    if path in ("/", "/healthz") or path.startswith("/static/"):
        return method in ("GET", "HEAD")
    if not path.startswith("/api/"):
        return False
    native = "/v1/" + path[5:]
    if path == "/api/me":
        native = "/v1/profile"
    return allowed_request("GET" if method == "HEAD" else method, native)


def bounded_text(value, minimum=1, maximum=64, newlines=False, controls=False):
    if (not isinstance(value, str) or not minimum <= len(value) <= maximum or
            not controls and any((ord(char) < 32 or 127 <= ord(char) <= 159) and not (newlines and char in "\r\n") for char in value)):
        return False
    try:
        value.encode("utf-8")
    except UnicodeError:
        return False
    return True


def support_payload(value, action):
    fields = {"create": {"priority", "message"}, "reply": {"revision", "message"}, "status": {"revision", "status"}, "delete": {"revision"}}[action]
    if not isinstance(value, dict) or set(value) != fields:
        raise WardrobeError("invalid_request", 400)
    if "revision" in fields and (type(value["revision"]) is not int or not 1 <= value["revision"] <= 2147483647):
        raise WardrobeError("invalid_revision", 400)
    if "priority" in fields and value["priority"] not in ("low", "normal", "high"):
        raise WardrobeError("invalid_priority", 400)
    if "status" in fields and value["status"] not in ("open", "closed"):
        raise WardrobeError("invalid_status", 400)
    if "message" in fields and (not bounded_text(value["message"], maximum=2000, newlines=True) or not value["message"].strip()):
        raise WardrobeError("invalid_message", 400)
    return value


def _date(value):
    try:
        return isinstance(value, str) and len(value) <= 50 and datetime.fromisoformat(value).utcoffset() is not None
    except ValueError:
        return False


def support_ticket(value, summary=False, owner_uuid=None):
    if (not isinstance(value, dict) or any(not isinstance(value.get(key), str) or not re.fullmatch(SKIN_ID, value[key]) for key in ("id", "player_uuid")) or
            owner_uuid is not None and value["player_uuid"] != owner_uuid or not bounded_text(value.get("player_name")) or
            value.get("priority") not in ("low", "normal", "high") or value.get("status") not in ("open", "closed") or
            type(value.get("revision")) is not int or value["revision"] < 1 or not _date(value.get("created_at")) or not _date(value.get("updated_at"))):
        raise WardrobeError("upstream_invalid", 502)
    result = {key: value[key] for key in ("id", "player_uuid", "player_name", "priority", "status", "revision", "created_at", "updated_at")}
    if summary and "messages" not in value:
        if type(value.get("message_count")) is not int or not 1 <= value["message_count"] <= 50:
            raise WardrobeError("upstream_invalid", 502)
        return dict(result, message_count=value["message_count"])
    messages = value.get("messages")
    if not isinstance(messages, list) or not 1 <= len(messages) <= 50:
        raise WardrobeError("upstream_invalid", 502)
    clean = []
    for item in messages:
        if (not isinstance(item, dict) or item.get("author") not in ("player", "admin") or
                not bounded_text(item.get("message"), maximum=2000, newlines=True) or not _date(item.get("created_at"))):
            raise WardrobeError("upstream_invalid", 502)
        clean.append({key: item[key] for key in ("author", "message", "created_at")})
    return dict(result, messages=clean)


def support_response(value, listing=False, owner_uuid=None):
    if not isinstance(value, dict):
        raise WardrobeError("upstream_invalid", 502)
    if listing:
        if not isinstance(value.get("tickets"), list) or len(value["tickets"]) > 100:
            raise WardrobeError("upstream_invalid", 502)
        return dict(tickets=[support_ticket(ticket, summary=True, owner_uuid=owner_uuid) for ticket in value["tickets"]])
    return dict(ticket=support_ticket(value.get("ticket"), owner_uuid=owner_uuid))


def account_list(value):
    if not isinstance(value, dict) or not isinstance(value.get("accounts"), list) or len(value["accounts"]) > 10000:
        raise WardrobeError("upstream_invalid", 502)
    clean = []
    for item in value["accounts"]:
        if (not isinstance(item, dict) or not bounded_text(item.get("name")) or not isinstance(item.get("uuid"), str) or not re.fullmatch(SKIN_ID, item["uuid"])):
            raise WardrobeError("upstream_invalid", 502)
        clean.append(dict(uuid=item["uuid"], name=item["name"]))
    return dict(accounts=clean)


def password_reset_payload(value):
    if (not isinstance(value, dict) or set(value) != {"username", "new_password", "confirmation"} or not bounded_text(value["username"])):
        raise WardrobeError("invalid_request", 400)
    if not bounded_text(value["new_password"], minimum=12, maximum=64) or value["new_password"] != value["confirmation"]:
        raise WardrobeError("invalid_password", 400)
    return value


def module_status(value):
    if not isinstance(value, dict) or value.get("accounts") is not True or any(type(value.get(key)) is not bool for key in ("wardrobe", "support")):
        raise WardrobeError("upstream_invalid", 502)
    return {key: value[key] for key in ("accounts", "wardrobe", "support")}


def operator_login_payload(value):
    if isinstance(value, dict) and set(value) == {"code"}:
        if isinstance(value["code"], str) and re.fullmatch(r"[A-Za-z0-9_-]{6,32}", value["code"]):
            return value
    elif (isinstance(value, dict) and set(value) == {"username", "password"} and bounded_text(value["username"]) and
            bounded_text(value["password"], maximum=1024, controls=True)):
        return value
    raise WardrobeError("invalid_request", 400)


def operator_identity(value, token=False):
    if (not isinstance(value, dict) or not isinstance(value.get("uuid"), str) or not re.fullmatch(SKIN_ID, value["uuid"]) or
            not bounded_text(value.get("name")) or type(value.get("expires_at")) not in (int, float) or
            not math.isfinite(value["expires_at"]) or value["expires_at"] <= time.time()):
        raise WardrobeError("upstream_invalid", 502)
    result = {key: value[key] for key in ("uuid", "name", "expires_at")}
    if token:
        if not isinstance(value.get("token"), str) or not re.fullmatch(r"[A-Za-z0-9._~-]{16,512}", value["token"]):
            raise WardrobeError("upstream_invalid", 502)
        result["token"] = value["token"]
    return result


class WardrobeClient:
    def __init__(self, base_url, token_file, admin=False):
        self.origin = fixed_origin(base_url)
        self.token = read_secret(token_file).strip()
        if not re.fullmatch(r"[A-Za-z0-9._~-]{32,512}", self.token):
            raise RuntimeError("Required wardrobe service token is invalid.")
        self.admin = admin

    def request(self, method, path, payload=None, session_token=None, admin=False):
        if admin != self.admin or not allowed_request(method, path, admin):
            raise WardrobeError("forbidden", 403)
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/json"}
        if not admin and path not in ("/v1/pair/redeem", "/v1/login", "/v1/recover"):
            if not isinstance(session_token, str) or not re.fullmatch(r"[A-Za-z0-9._~-]{16,512}", session_token):
                raise WardrobeError("unauthorized", 401)
            headers["X-Player-Session"] = session_token
        body = None
        if payload is not None:
            try:
                body = json.dumps(payload, ensure_ascii=True, allow_nan=False).encode("utf-8")
            except (ValueError, TypeError):
                raise WardrobeError("invalid_request", 400) from None
            if len(body) > MAX_BODY:
                raise WardrobeError("payload_too_large", 413)
            headers["Content-Type"] = "application/json"
        cls = http.client.HTTPSConnection if self.origin.scheme == "https" else http.client.HTTPConnection
        connection = cls(self.origin.hostname, self.origin.port, timeout=12)
        try:
            connection.request(method, path, body=body, headers=headers)
            remote = connection.getresponse()
            raw = remote.read(MAX_RESPONSE + 1)
            if len(raw) > MAX_RESPONSE or remote.getheader("Content-Type", "").split(";")[0].lower() != "application/json":
                raise WardrobeError("upstream_invalid", 502)
            try:
                value = json.loads(raw)
            except (ValueError, UnicodeError, RecursionError):
                raise WardrobeError("upstream_invalid", 502) from None
            if not isinstance(value, dict):
                raise WardrobeError("upstream_invalid", 502)
            if not 200 <= remote.status < 300:
                raise WardrobeError(value.get("code", "upstream_invalid"), remote.status, remote.getheader("Retry-After"))
            return value
        except (OSError, http.client.HTTPException, TimeoutError):
            raise WardrobeError("upstream_unavailable", 502) from None
        finally:
            connection.close()


def configured_client(config, admin=False):
    injected = config.get("WARDROBE_CLIENT")
    if injected is not None:
        return injected
    key = "WARDROBE_ADMIN_TOKEN_FILE" if admin else "WARDROBE_PLAYER_TOKEN_FILE"
    try:
        return WardrobeClient(config["WARDROBE_BASE_URL"], config.get(key), admin=admin)
    except RuntimeError:
        raise WardrobeError("upstream_unavailable", 503) from None


def signing_settings(value):
    if (not isinstance(value, dict) or value.get("provider") not in ("mineskin", "selfhosted") or
            not isinstance(value.get("client_id"), str) or not re.fullmatch(r"[A-Za-z0-9-]{0,128}", value["client_id"]) or
            type(value.get("key_configured")) is not bool or not isinstance(value.get("account"), dict) or
            type(value["account"].get("connected")) is not bool):
        raise WardrobeError("upstream_invalid", 502)
    account = {"connected": value["account"]["connected"]}
    for key in ("name", "uuid"):
        item = value["account"].get(key)
        if isinstance(item, str) and len(item) <= 64 and not any(ord(c) < 32 for c in item):
            account[key] = item
    status = value.get("status")
    statuses = {"connecting", "configured", "unconfigured", "connected", "disconnected", "authorization_expired",
                "authorization_failed", "reauthentication_required"} | ERRORS.keys()
    result = dict(provider=value["provider"], client_id=value["client_id"], key_configured=value["key_configured"],
                  account=account, status=status if isinstance(status, str) and status in statuses else "unavailable")
    if value["provider"] == "selfhosted":
        result["message"] = translate("Der eigene Generator ist noch nicht mit einer Betreiber-Client-ID abgenommen.")
    return result


def signing_connection(value):
    if not isinstance(value, dict):
        raise WardrobeError("upstream_invalid", 502)
    code, uri = value.get("user_code"), value.get("verification_uri")
    try:
        destination = urlsplit(uri) if isinstance(uri, str) else None
        valid_uri = (destination and destination.scheme == "https" and
            destination.hostname in ("microsoft.com", "www.microsoft.com", "login.microsoftonline.com") and
            not destination.username and not destination.password and not destination.query and not destination.fragment and destination.port in (None, 443))
    except ValueError:
        valid_uri = False
    if (not isinstance(code, str) or not re.fullmatch(r"[A-Z0-9-]{4,32}", code) or not valid_uri or
            type(value.get("expires_at")) not in (int, float) or not math.isfinite(value["expires_at"]) or value["expires_at"] <= 0 or
            type(value.get("interval")) not in (int, float) or not 0 < value["interval"] <= 60):
        raise WardrobeError("upstream_invalid", 502)
    return {key: value[key] for key in ("user_code", "verification_uri", "expires_at", "interval")}
