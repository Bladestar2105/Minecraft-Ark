# Quellcode, Build und Tests

Eigene Komponenten: `ArkDinos` (Zähmung), `ArkAtlas` (Kartengegenstand),
`ArkPanelBridge` (lesende Laufzeitdaten), `ArkAccessBridge` (separate private
Offline-/AuthMe-Migration) und das Flask-Panel. MIT gilt für eigenen Code.
Weltgestaltung und Bauten stehen unter CC BY 4.0. Fremde Abhängigkeiten bleiben separat.

```sh
# JDK 25 in JAVA_HOME oder PATH
python3 scripts/build_plugins.py
# Python-Werkzeugkompatibilität und Initialisierungsgrenzen
python3 -m venv .venv
.venv/bin/pip install -r panel/requirements.txt
.venv/bin/python -m unittest discover -s panel -p 'test_*.py'
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
```

Der Build verwendet ausschließlich veröffentlichte API-Abhängigkeiten und
`dependencies-build.json` mit SHA-256-Pins. Er kompiliert mit `--release 25`, führt
die kleinen Java-Regeltests aus und erzeugt JARs unter `<modul>/build/` mit festem
Zeitstempel. Die Abhängigkeiten liegen lokal unter `.deps/` und werden nicht
veröffentlicht. Bytegleiche Builds setzen dieselbe JDK-Version voraus.
Die historische private `artifacts/`-Umgebung wird nicht benötigt.

Die Standardinstallation kopiert nur ArkDinos, ArkAtlas und ArkPanelBridge.
ArkAccessBridge 1.1.0 wird mitgebaut, aber absichtlich nicht installiert: seine
Sitzungsprüfung erwartet AuthMe 6.0.1, PacketEvents 2.14.0 und einen separat
geprüften Offline-Server. `legacy-identities: {}` bleibt leer. Keine privaten
UUID-Zuordnungen oder Accountdaten in öffentliche Konfigurationen aufnehmen.

Die Welt ist ein fertiger Release-Stand; Bau-, Deployment- und Resetwerkzeuge der
privaten Infrastruktur sind nicht Teil der öffentlichen Schnellinstallation.
Für Rückmeldungen kleine reproduzierbare Schritte und passende Tests beilegen.
