"""Player-only web service. No admin, Docker, RCON or world-filesystem access."""
import base64
import binascii
from collections import OrderedDict
from datetime import timedelta
import hmac
import json
import math
import os
import re
import secrets
import time
import threading

from flask import Flask, g, jsonify, render_template, request, session
from werkzeug.exceptions import HTTPException
from werkzeug.middleware.proxy_fix import ProxyFix

if __package__:
    from .i18n import locale, translate, WIKI_BASE
    from .wardrobe_client import configured_client, ERRORS, MAX_BODY, SKIN_ID, WardrobeError, read_secret, support_payload, support_response, module_status
else:
    from i18n import locale, translate, WIKI_BASE
    from wardrobe_client import configured_client, ERRORS, MAX_BODY, SKIN_ID, WardrobeError, read_secret, support_payload, support_response, module_status


def _identity(value, token=False):
    if (not isinstance(value, dict) or not isinstance(value.get("uuid"), str) or not re.fullmatch(SKIN_ID, value["uuid"]) or
            not isinstance(value.get("name"), str) or not 1 <= len(value["name"]) <= 64 or any(ord(c) < 32 for c in value["name"]) or
            type(value.get("expires_at")) not in (int, float) or not math.isfinite(value["expires_at"])):
        raise WardrobeError("upstream_invalid", 502)
    if token and (not isinstance(value.get("token"), str) or not re.fullmatch(r"[A-Za-z0-9._~-]{16,512}", value["token"])):
        raise WardrobeError("upstream_invalid", 502)
    if value["expires_at"] <= time.time():
        raise WardrobeError("unauthorized", 401)
    return value


def _skin(value):
    if (not isinstance(value, dict) or not isinstance(value.get("id"), str) or not re.fullmatch(SKIN_ID, value["id"]) or
            not isinstance(value.get("name"), str) or not 1 <= len(value["name"]) <= 32 or
            value.get("model") not in ("classic", "slim") or type(value.get("revision")) is not int or value["revision"] < 1 or
            not isinstance(value.get("png"), str) or len(value["png"]) > 350000 or
            value.get("status") not in ("draft", "preparing", "ready", "saved", "applied", "failed") or
            type(value.get("ready")) is not bool or type(value.get("selected")) is not bool):
        raise WardrobeError("upstream_invalid", 502)
    result = {key: value[key] for key in ("id", "name", "model", "revision", "png", "ready", "selected", "status")}
    if value.get("error"):
        code = value["error"] if isinstance(value["error"], str) and value["error"] in ERRORS else "signing_failed"
        result.update(error=translate(ERRORS[code]), error_code=code)
    return result


def create_player_app(test_config=None):
    app = Flask(__name__)
    app.config.update(
        WARDROBE_BASE_URL=os.environ.get("WARDROBE_BASE_URL", "http://minecraft-ark:8101"),
        WARDROBE_PLAYER_TOKEN_FILE=os.environ.get("WARDROBE_PLAYER_TOKEN_FILE"),
        PLAYER_CREDENTIALS_FILE=os.environ.get("PLAYER_CREDENTIALS_FILE", "/run/secrets/player.json"),
        TRUSTED_HOSTS=[host.strip() for host in os.environ.get("PLAYER_TRUSTED_HOSTS", os.environ.get("PANEL_TRUSTED_HOSTS", "localhost,127.0.0.1")).split(",") if host.strip()],
        MAX_CONTENT_LENGTH=MAX_BODY, SESSION_COOKIE_NAME="ark_player_session", SESSION_COOKIE_PATH="/play/",
        SESSION_COOKIE_HTTPONLY=True, SESSION_COOKIE_SAMESITE="Strict",
        SESSION_COOKIE_SECURE=os.environ.get("PLAYER_COOKIE_SECURE") == "1",
        PERMANENT_SESSION_LIFETIME=timedelta(days=30), SESSION_REFRESH_EACH_REQUEST=False,
        TRUST_PROXY_HEADERS=os.environ.get("PLAYER_TRUST_PROXY") == "1",
    )
    if test_config:
        app.config.update(test_config)
    if not app.config.get("SECRET_KEY"):
        try:
            value = json.loads(read_secret(app.config["PLAYER_CREDENTIALS_FILE"]))
            if not isinstance(value, dict) or set(value) != {"session_secret"}:
                raise ValueError
            app.config["SECRET_KEY"] = value["session_secret"]
        except (RuntimeError, ValueError, KeyError, TypeError):
            raise RuntimeError("Valid player session credentials are required.") from None
    if not isinstance(app.config["SECRET_KEY"], str) or len(app.config["SECRET_KEY"]) < 32:
        raise RuntimeError("Player session secret must contain at least 32 characters.")
    try:
        wardrobe = configured_client(app.config)
    except WardrobeError:
        raise RuntimeError("Valid player wardrobe service credentials are required.") from None
    if app.config["TRUST_PROXY_HEADERS"]:
        app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_port=0, x_prefix=0)
    # shortcut: per-process limits; share this state before running multiple player workers.
    auth_attempts = OrderedDict()
    auth_lock = threading.Lock()

    def reserve_auth_attempt():
        source, now, reservation = request.remote_addr or "unknown", time.monotonic(), object()
        with auth_lock:
            recent = [(at, item) for at, item in auth_attempts.get(source, ()) if now - at < 60]
            if len(recent) >= 10:
                raise WardrobeError("session_limit", 429)
            auth_attempts[source] = recent + [(now, reservation)]
            auth_attempts.move_to_end(source)
            if len(auth_attempts) > 4096:
                auth_attempts.popitem(last=False)
        g.auth_attempt = (source, reservation)

    def call(method, path, body=None):
        return wardrobe.request(method, path, payload=body, session_token=session.get("player_token"))

    def payload(fields, optional=()):
        value = request.get_json(silent=True)
        if not isinstance(value, dict) or not set(fields) <= set(value) or set(value) - set(fields) - set(optional):
            raise WardrobeError("invalid_request", 400)
        return value

    def text(value, minimum=1, maximum=1024, allow_controls=False):
        if (not isinstance(value, str) or not minimum <= len(value) <= maximum or
                not allow_controls and any(ord(c) < 32 or 127 <= ord(c) <= 159 for c in value)):
            raise WardrobeError("invalid_request", 400)
        try:
            value.encode("utf-8")
        except UnicodeError:
            raise WardrobeError("invalid_request", 400) from None
        return value

    def new_password(body):
        try:
            text(body.get("new_password"), 12, 64)
        except WardrobeError:
            raise WardrobeError("invalid_password", 400) from None
        if body.get("confirmation") != body["new_password"]:
            raise WardrobeError("invalid_password", 400)

    def recovery_proof(body):
        text(body["current_password"], allow_controls=True)
        if "recovery_code" in body:
            text(body["recovery_code"], maximum=128)

    def authenticated():
        if (not isinstance(session.get("player_token"), str) or type(session.get("expires_at")) not in (int, float) or
                session["expires_at"] <= time.time()):
            session.clear()
            raise WardrobeError("unauthorized", 401)
        try:
            value = _identity(call("GET", "/v1/session"))
        except WardrobeError as error:
            if error.status in (401, 403):
                session.clear()
            raise
        if value["uuid"] != session.get("uuid"):
            session.clear()
            raise WardrobeError("unauthorized", 401)
        g.player = value

    @app.before_request
    def protect():
        if request.method not in ("GET", "HEAD", "OPTIONS"):
            if request.headers.get("Origin") != request.host_url.rstrip("/"):
                raise WardrobeError("origin_denied", 403)
        if request.method == "POST" and request.path in ("/api/login", "/api/recover"):
            reserve_auth_attempt()
        if request.path.startswith("/api/") and request.path not in ("/api/session", "/api/login", "/api/recover"):
            authenticated()
            if request.method not in ("GET", "HEAD", "OPTIONS"):
                expected, supplied = session.get("csrf"), request.headers.get("X-CSRF-Token", "")
                if (not isinstance(expected, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,128}", supplied) or
                        not hmac.compare_digest(expected.encode(), supplied.encode())):
                    raise WardrobeError("csrf_failed", 403)

    @app.after_request
    def headers(response):
        if 200 <= response.status_code < 300 and getattr(g, "auth_attempt", None):
            source, reservation = g.auth_attempt
            with auth_lock:
                recent = [(at, item) for at, item in auth_attempts.get(source, ()) if item is not reservation]
                if recent:
                    auth_attempts[source] = recent
                else:
                    auth_attempts.pop(source, None)
        response.headers.update({"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
            "Referrer-Policy": "same-origin", "X-Frame-Options": "SAMEORIGIN", "Content-Language": locale()})
        return response

    @app.errorhandler(WardrobeError)
    def wardrobe_error(error):
        return jsonify(error=error.message, code=error.code), error.status

    @app.errorhandler(HTTPException)
    def http_error(error):
        code = {404: "not_found", 405: "method_not_allowed", 413: "payload_too_large"}.get(error.code, "invalid_request")
        return jsonify(error=translate(ERRORS[code]), code=code), error.code

    @app.errorhandler(Exception)
    def internal_error(error):
        app.logger.error("Player operation failed; no request or upstream data logged.")
        return jsonify(error=translate(ERRORS["internal_error"]), code="internal_error"), 500

    @app.get("/")
    def index():
        return render_template("player.html", language=locale(), wiki_base=WIKI_BASE)

    @app.get("/healthz")
    def health():
        return jsonify(status="ok")

    @app.get("/api/session")
    def session_info():
        logged_in = False
        if session.get("player_token"):
            try:
                authenticated()
                logged_in = True
            except WardrobeError as error:
                if error.status not in (401, 403):
                    raise
        return jsonify(authenticated=logged_in, csrf=session.get("csrf") if logged_in else None,
                       username=g.player["name"] if logged_in else None, language=locale(), wiki_base=WIKI_BASE)

    @app.post("/api/login")
    def login():
        raw = request.get_json(silent=True)
        if isinstance(raw, dict) and set(raw) == {"username", "password"}:
            body = payload(("username", "password"))
            text(body["username"], maximum=64)
            text(body["password"], allow_controls=True)
            path = "/v1/login"
        elif isinstance(raw, dict) and set(raw) == {"code"}:
            body = payload(("code",))
            if not isinstance(body["code"], str) or not re.fullmatch(r"[A-Za-z0-9_-]{6,32}", body["code"]):
                raise WardrobeError("invalid_request", 400)
            path = "/v1/pair/redeem"
        else:
            raise WardrobeError("invalid_request", 400)
        value = _identity(wardrobe.request("POST", path, payload=body), token=True)
        session.clear()
        session.permanent = True
        session.update(player_token=value["token"], uuid=value["uuid"], expires_at=min(value["expires_at"], time.time() + 30 * 86400),
                       csrf=secrets.token_urlsafe(32))
        return jsonify(ok=True, csrf=session["csrf"], username=value["name"])

    @app.post("/api/logout")
    def logout():
        payload(())
        call("POST", "/v1/logout", {})
        session.clear()
        return jsonify(ok=True)

    @app.get("/api/me")
    def profile():
        value = call("GET", "/v1/profile")
        if (not isinstance(value, dict) or value.get("uuid") != g.player["uuid"] or not isinstance(value.get("name"), str) or
                type(value.get("online")) is not bool or not isinstance(value.get("stats"), dict) or
                not isinstance(value.get("observed_at"), (str, int, float))):
            raise WardrobeError("upstream_invalid", 502)
        stats = {key: value["stats"].get(key) for key in ("play_time_ticks", "deaths", "mob_kills", "player_kills")}
        if any(type(item) is not int or item < 0 for item in stats.values()):
            raise WardrobeError("upstream_invalid", 502)
        selected = value.get("active_skin_id")
        if selected is not None and (not isinstance(selected, str) or not re.fullmatch(SKIN_ID, selected)):
            raise WardrobeError("upstream_invalid", 502)
        return jsonify(uuid=value["uuid"], name=value["name"], online=value["online"], stats=stats,
                       observed_at=value["observed_at"], active_skin_id=selected)

    @app.get("/api/account")
    def account():
        value = call("GET", "/v1/account")
        if (not isinstance(value, dict) or type(value.get("password_configured")) is not bool or
                type(value.get("password_reset_allowed")) is not bool or
                type(value.get("recovery_enabled")) is not bool or type(value.get("recovery_codes_remaining")) is not int or
                not 0 <= value["recovery_codes_remaining"] <= 100):
            raise WardrobeError("upstream_invalid", 502)
        return jsonify({key: value[key] for key in ("password_configured", "password_reset_allowed", "recovery_enabled", "recovery_codes_remaining")})

    @app.get("/api/modules")
    def modules():
        return jsonify(module_status(call("GET", "/v1/modules")))

    @app.route("/api/support", methods=["GET", "POST"])
    def support():
        body = support_payload(request.get_json(silent=True), "create") if request.method == "POST" else None
        value = call(request.method, "/v1/support", body)
        return jsonify(support_response(value, listing=request.method == "GET", owner_uuid=g.player["uuid"])), 201 if request.method == "POST" else 200

    @app.get("/api/support/<ticket_id>")
    @app.post("/api/support/<ticket_id>/reply")
    def support_detail(ticket_id):
        if not re.fullmatch(SKIN_ID, ticket_id):
            raise WardrobeError("invalid_request", 400)
        body = support_payload(request.get_json(silent=True), "reply") if request.method == "POST" else None
        value = call(request.method, "/v1/support/" + ticket_id + ("/reply" if request.method == "POST" else ""), body)
        return jsonify(support_response(value, owner_uuid=g.player["uuid"]))

    @app.post("/api/password")
    def change_password():
        body = payload(("current_password", "new_password", "confirmation"))
        text(body["current_password"], minimum=0, allow_controls=True)
        new_password(body)
        call("POST", "/v1/password", body)
        session.clear()
        return jsonify(ok=True)

    @app.route("/api/recovery", methods=["POST", "DELETE"])
    def manage_recovery():
        body = payload(("current_password",), ("recovery_code",))
        recovery_proof(body)
        value = call(request.method, "/v1/recovery", body)
        if request.method == "DELETE":
            return jsonify(ok=True)
        codes = value.get("codes") if isinstance(value, dict) else None
        if (not isinstance(codes, list) or not 1 <= len(codes) <= 100 or
                any(not isinstance(code, str) or not re.fullmatch(r"[A-Za-z0-9_-]{8,128}", code) for code in codes)):
            raise WardrobeError("upstream_invalid", 502)
        return jsonify(codes=codes)

    @app.post("/api/recover")
    def recover_account():
        body = payload(("username", "recovery_code", "new_password", "confirmation"))
        text(body["username"], maximum=64)
        text(body["recovery_code"], maximum=128)
        new_password(body)
        wardrobe.request("POST", "/v1/recover", payload=body)
        session.clear()
        return jsonify(ok=True)

    def skin_body(fields):
        body = payload(fields)
        if "revision" in fields and (type(body["revision"]) is not int or not 1 <= body["revision"] <= 2147483647):
            raise WardrobeError("invalid_request", 400)
        if "name" in fields and (not isinstance(body["name"], str) or not 1 <= len(body["name"].strip()) <= 32 or
                                  body["name"] != body["name"].strip() or any(ord(c) < 32 for c in body["name"])):
            raise WardrobeError("invalid_request", 400)
        if "name" in fields:
            text(body["name"], maximum=32)
        if "png" in fields:
            if body.get("model") not in ("classic", "slim") or not isinstance(body["png"], str) or len(body["png"]) > 350000:
                raise WardrobeError("invalid_request", 400)
            try:
                decoded = base64.b64decode(body["png"], validate=True)
                if not 8 <= len(decoded) <= 256 * 1024 or not decoded.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise ValueError
            except (ValueError, binascii.Error):
                raise WardrobeError("invalid_request", 400) from None
        return body

    @app.route("/api/skins", methods=["GET", "POST"])
    def skins():
        if request.method == "GET":
            value = call("GET", "/v1/skins")
            if not isinstance(value, dict) or not isinstance(value.get("skins"), list) or len(value["skins"]) > 20:
                raise WardrobeError("upstream_invalid", 502)
            return jsonify(skins=[_skin(skin) for skin in value["skins"]], limit=20)
        value = call("POST", "/v1/skins", skin_body(("name", "model", "png")))
        return jsonify(skin=_skin(value.get("skin"))), 201

    @app.route("/api/skins/<skin_id>", methods=["PUT", "DELETE"])
    @app.post("/api/skins/<skin_id>/<action>")
    def skin_action(skin_id, action=None):
        if not re.fullmatch(SKIN_ID, skin_id):
            raise WardrobeError("invalid_request", 400)
        if action not in (None, "copy", "activate"):
            raise WardrobeError("not_found", 404)
        fields = ("name", "model", "png", "revision") if request.method == "PUT" else ("name", "revision") if action == "copy" else ("revision",)
        value = call(request.method, "/v1/skins/" + skin_id + ("/" + action if action else ""), skin_body(fields))
        if request.method == "DELETE":
            return jsonify(ok=True)
        skin = _skin(value.get("skin"))
        return jsonify(skin=skin), 201 if action == "copy" else 202 if action == "activate" and skin["status"] == "preparing" else 200

    return app
