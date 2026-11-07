"""Shared local dictionaries; request language never changes global metadata."""
from functools import lru_cache
import json
from pathlib import Path
from types import MappingProxyType

from flask import has_request_context, request

WIKI_BASE = "https://github.com/Bladestar2105/Minecraft-Ark/wiki"


def locale():
    if not has_request_context():
        return "de"
    saved = request.cookies.get("ark_language")
    # API clients send their current selection explicitly after a language switch.
    header = request.headers.get("Accept-Language")
    if saved in ("de", "en") and not request.path.startswith(("/api/", "/play/api/")):
        return saved
    if header:
        return "de" if request.accept_languages.best_match(["de", "en"], default="en") == "de" else "en"
    return saved if saved in ("de", "en") else "de"


@lru_cache(maxsize=2)
def _dictionary(language):
    path = Path(__file__).parent / "static" / "locales" / (language + ".json")
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(document, dict) or any(not isinstance(key, str) or not isinstance(value, str) for key, value in document.items()):
            raise ValueError("invalid dictionary")
        return MappingProxyType(document)
    except (OSError, ValueError):
        return MappingProxyType({})


def translate(text, language=None, **params):
    result = _dictionary(language if language in ("de", "en") else locale()).get(text, text)
    for key, value in params.items():
        result = result.replace("{" + key + "}", str(value))
    return result


def metadata(value):
    return {key: translate(item) if key in ("label", "description", "group") and isinstance(item, str) else item
            for key, item in value.items()}
