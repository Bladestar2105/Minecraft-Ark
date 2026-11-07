"""Advanced editor protection without Flask dependencies.

YAML callers must first run the panel's strict validate_yaml on BOTH source
and submitted text (aliases, duplicate keys, tags, depth and size). Edits use
compose marks so ordinary YAML text/comments are retained exactly.
"""
import copy
import json
import math
import re

import yaml

PROTECTED = "__ARK_PROTECTED__"
MAX_BYTES = 256 * 1024
_SECRET = re.compile(r"password|secret|token|api[-_]key|private[-_]key", re.I)
_MISSING = object()
_IDENTITIES = {
    "geyser": ("java.auth-type", "java.address", "java.port", "bedrock.address", "bedrock.port", "bedrock.transport", "bedrock.signaling.mode", "bedrock.signaling.advertise-addresses", "advanced.floodgate-key-file"),
    "floodgate": ("key-file", "username-prefix", "replace-spaces", "player-link.allowed"),
    "ark-atlas": ("map-id",),
    "ark-accounts": ("bind", "port"),
    "bluemap-core": ("data",),
    "bluemap-webserver": ("ip", "port", "webroot"),
    "bluemap-webapp": ("webroot", "map-data-root", "live-data-root"),
    "bluemap-storage": ("root", "storage-type"),
    "bluemap-map-ark": ("world", "dimension", "storage"),
    "bluemap-map-creative": ("world", "dimension", "storage"),
}


def _check_text(text):
    try:
        if not isinstance(text, str) or len(text.encode("utf-8")) > MAX_BYTES:
            raise ValueError
    except (UnicodeError, ValueError):
        raise ValueError("Konfiguration muss gültiger UTF-8-Text mit höchstens 256 KiB sein.") from None


def validate_json(text):
    """Accept bounded plain JSON objects; HOCON expressions are not evaluated."""
    _check_text(text)

    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError
            result[key] = value
        return result

    def constant(_value):
        raise ValueError

    try:
        value = json.loads(text, object_pairs_hook=pairs, parse_constant=constant)
        if not isinstance(value, dict) or not value:
            raise ValueError
        count, pending = 0, [(value, 0)]
        while pending:
            item, depth = pending.pop()
            count += 1
            if isinstance(item, (dict, list)):
                depth += 1
            if count > 20000 or depth > 40 or (isinstance(item, float) and not math.isfinite(item)):
                raise ValueError
            if isinstance(item, str):
                item.encode("utf-8")
            elif isinstance(item, dict):
                pending.extend((child, depth) for pair in item.items() for child in pair)
            elif isinstance(item, list):
                pending.extend((child, depth) for child in item)
        return value
    except (ValueError, UnicodeError, RecursionError):
        raise ValueError("Ungültiges JSON; nur eine nicht leere Zuordnung ohne doppelte Schlüssel oder Sonderausdrücke ist erlaubt (Tiefe 40, höchstens 20.000 Werte).") from None


def _parse(kind, text):
    if kind == "json":
        return validate_json(text), {}
    if kind != "yaml":
        raise ValueError("Unbekanntes Konfigurationsformat.")
    _check_text(text)
    try:
        value, root = yaml.safe_load(text), yaml.compose(text, Loader=yaml.SafeLoader)
        if not isinstance(value, dict):
            raise ValueError
        nodes = {}

        def visit(node, path):
            nodes[path] = node
            if isinstance(node, yaml.MappingNode):
                for key, child in node.value:
                    if not isinstance(key, yaml.ScalarNode):
                        raise ValueError
                    name = key.value if key.tag == "tag:yaml.org,2002:str" else yaml.safe_load(text[key.start_mark.index:key.end_mark.index])
                    visit(child, path + (name,))
            elif isinstance(node, yaml.SequenceNode):
                for index, child in enumerate(node.value):
                    visit(child, path + (index,))

        visit(root, ())
        return value, nodes
    except (yaml.YAMLError, ValueError, TypeError, RecursionError):
        raise ValueError("Ungültige YAML-Konfiguration; die strenge Prüfung muss vor dem Bearbeiten erfolgen.") from None


def _secrets(value, path=()):
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = path + (key,)
            if isinstance(key, str) and _SECRET.search(key):
                yield child_path
            else:
                yield from _secrets(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _secrets(child, path + (index,))


def _get(value, path):
    try:
        for key in path:
            value = value[key]
        return value
    except (KeyError, IndexError, TypeError):
        return _MISSING


def _put(value, path, replacement):
    _get(value, path[:-1])[path[-1]] = replacement


def _same(left, right):
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        a = {(type(key), key): value for key, value in left.items()}
        b = {(type(key), key): value for key, value in right.items()}
        return a.keys() == b.keys() and all(_same(value, b[key]) for key, value in a.items())
    if isinstance(left, list):
        return len(left) == len(right) and all(_same(a, b) for a, b in zip(left, right))
    return left == right


def _encoded(value):
    try:
        encoded = json.dumps(value, ensure_ascii=False, allow_nan=False)
        if not _same(value, yaml.safe_load(encoded)):
            raise ValueError
        return encoded
    except (TypeError, ValueError, UnicodeError, yaml.YAMLError):
        raise ValueError("Dieser geschützte Wert kann nicht sicher bearbeitet werden.") from None


def _yaml_edits(text, edits):
    for node, replacement in sorted(edits, key=lambda edit: edit[0].start_mark.index, reverse=True):
        start, end = node.start_mark.index, node.end_mark.index
        if start == end and start > 0 and text[start - 1] == ":":
            replacement = " " + replacement
        # Block scalars/containers can include the newline before the next key.
        tail = re.search(r"(\r?\n[ \t]*)$", text[start:end])
        text = text[:start] + replacement + (tail[1] if tail else "") + text[end:]
    _check_text(text)
    return text


def _json_text(value):
    text = json.dumps(value, ensure_ascii=False, allow_nan=False, indent=2) + "\n"
    if len(text.encode("utf-8")) > MAX_BYTES:
        text = json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":"))
    _check_text(text)
    return text


def redact_content(kind, config_id, text):
    """Hide secret values; non-secret managed identities remain viewable."""
    value, nodes = _parse(kind, text)
    paths = list(_secrets(value))
    if kind == "yaml":
        return _yaml_edits(text, [(nodes[path], _encoded(PROTECTED)) for path in paths])
    for path in paths:
        _put(value, path, PROTECTED)
    return _json_text(value)


def restore_content(kind, config_id, original, submitted):
    """Restore masks or reject protected changes, without mutating any file."""
    before, _ = _parse(kind, original)
    after, nodes = _parse(kind, submitted)
    secrets = set(_secrets(before))
    if set(_secrets(after)) - secrets:
        raise ValueError("Neue geheime Felder dürfen hier nicht angelegt werden.")
    edits = []
    for path in secrets:
        previous, current = _get(before, path), _get(after, path)
        if current == PROTECTED:
            _put(after, path, copy.deepcopy(previous))
            if kind == "yaml":
                edits.append((nodes[path], _encoded(previous)))
        elif not _same(previous, current):
            raise ValueError("Geheime Felder dürfen nicht geändert oder entfernt werden.")
    for field in _IDENTITIES.get(config_id, ()):
        path = tuple(field.split("."))
        previous, current = _get(before, path), _get(after, path)
        if previous is _MISSING and current is _MISSING:
            continue
        if not _same(previous, current):
            raise ValueError("Verwaltete Verbindungs- und Identitätsfelder dürfen nicht geändert, ergänzt oder entfernt werden.")
    return _yaml_edits(submitted, edits) if kind == "yaml" else _json_text(after)
