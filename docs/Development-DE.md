# Quellcode, Bau und Tests

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development) · [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE)

Das Projekt hat sieben Pluginquellen: `accounts-plugin`, `wardrobe-plugin`, `support-plugin`, `dino-plugin`, `atlas-plugin`, `panel-plugin` und das optionale `access-plugin` (ArkAccessBridge 1.1.1). `panel` enthält die getrennte Flask-Spieleranwendung und Verwaltung. [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE) beschreibt ihre Aufgaben; die Konto-/Sitzungsverwaltung gehört zu ArkAccounts, nicht Wardrobe.

## In einem neuen öffentlichen Checkout bauen

```sh
# JAVA_HOME/PATH muss JDK 25 verwenden.
python3 scripts/build_plugins.py
python3 -m venv .venv
.venv/bin/pip install -r panel/requirements.txt
.venv/bin/python -m unittest discover -s panel -p 'test_*.py'
.venv/bin/python -m unittest discover -s scripts -p 'test_*.py'
node panel/test_skin_editor.mjs
node panel/test_photo_skin.mjs
node panel/test_ui.mjs
```

Der Builder prüft festgelegte API-Abhängigkeiten aus `dependencies-build.json`, kompiliert mit `--release 25`, führt Java-Prüfungen ohne Testframework aus und erzeugt alle sieben JARs samt `own-plugins.json`. Lokale Abhängigkeitscaches werden nicht weiterverteilt. Identische JAR-Bytes benötigen dieselbe JDK-Version; Benutzer des Binärpakets brauchen kein Host-JDK.

Browserprüfungen mit Testdiensten sind `panel/test_ui.mjs --browser`, `panel/test_player_ui.mjs`, `panel/test_signing_ui.mjs` und `panel/test_support_ui.mjs`. Sie benötigen ein installiertes Playwright-Modul/Chromium; `PLAYWRIGHT_MODULE` zeigt auf dessen `index.mjs`, optional `PLAYWRIGHT_CHROMIUM` auf die Browserdatei. Geprüft wird die produktive UI mit lokalen APIs, darunter DE/EN-Entwürfe, Wiederherstellung, aktuelle OP-Berechtigungen, Supportrevisionen und fehlende optionale Module. Screenshots sind lokale Testnachweise.

`panel/test_photo_skin.mjs` prüft ohne weitere Abhängigkeiten rechteckige Körperteilbereiche, Farbproben, Texturmittelung für Vorder-/Rückseiten, einfarbige Ersatzflächen ohne Rückseitenfoto und gültige Classic-/Slim-UV-Flächen mit künstlichen Bildern. `panel/static/photo_skin.js` und `panel/static/photo_import.js` setzen den lokalen Fotoablauf um; die Spieler-Browserprüfung prüft Entwürfe, Abbrechen und Foto-Datenschutz. KI, ImageMagick, zusätzliche Bibliotheken oder ein Fotoverarbeitungsserver sind nicht erforderlich. Bildgrenzen, Datenschutz und Qualitätsgrenzen stehen unter [Skin-Editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor-DE).

## Anwendungsartefakte packen

Nach dem geprüften Bau:

```sh
python3 scripts/package.py --version app-YOUR_VERSION
```

Unter `dist/app-YOUR_VERSION/` entstehen `Minecraft-Ark-app-YOUR_VERSION-all-in-one.zip`, sieben einzelne Projekt-JARs und `SHA256SUMS`. Das ZIP verwendet eine ausdrückliche Quellcode-Allowlist, geprüfte JAR-Hashes und feste Archivmetadaten; private Welten/Geheimnisse/Zustände bleiben ausgeschlossen. Es verweist auf den festen Weltrelease, statt ihn einzubetten.

CI kann Build-Artefakte `app-<Commit-SHA>` mit 14 Tagen Actions-Aufbewahrung erzeugen. Ein Actions-Artefakt ist kein Weltrelease und kein Deployment. Anwendungsrelease-Assets sind erst nach Veröffentlichung verfügbar; verwende tatsächlich am gewählten Build/Release vorhandene Artefakte. Der Paketablauf akzeptiert keine EULA und startet keinen Server.

## Änderungen und Abnahme

Halte persönliche `/api/player/*` und Verwaltungs-APIs hinter der gemeinsamen Hauptanmeldung ausdrücklich getrennt. Eine gültige Spielersitzung erlaubt Verwaltung nur nach aktueller serverseitiger OP-Prüfung. `/deop` muss persönlichen Zugriff erhalten; `/op` muss Verwaltung ohne zweite Anmeldung wiederherstellen. Bewahre UUID-Besitz, Versionskonflikte und lesbare bestehende Daten bei Schreibfehlern. OP-Rechte bleiben serverseitig maßgeblich, Accounts unabhängig von Support/Wardrobe und Modulabfragen wahrheitsgemäß. Aktualisiere bei Änderungen beide Sprachdateien und die Sprachpaare dieses Wikis.

ArkAccounts 1.0.1 stellt ausschließlich für den Admin-Dienst `POST /v1/admin/auth/player-check` bereit: Ein gewöhnlicher Spielertoken liefert die maßgebliche Sitzungsidentität und den aktuellen `is_op` als echtes Boolean. Der Hauptserver prüft damit Verwaltungszugriff; kein Adminschlüssel gelangt in den Browser. Das native `GET /v1/session` bleibt unverändert. Persönliche Webrouten verwenden `/api/player/*`; `/api/session`, `/api/login`, `/api/logout` und `/api/recover` verwalten gemeinsam die Anmeldung unter der Hauptadresse. Alte HTML-Seiten unter `/play/` leiten auf `/` weiter; `/play/api/*` und `/api/player/login|logout|session|recover` liefern404. Das veraltete `/api/operator/login` bleibt nur für Altintegrationen ohne UI und ist kein Alias für gewöhnliche `/web`-Codes. Bewahre [Anfragelimits](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE#anfragelimits), `Retry-After`, exaktes Proxyvertrauen und den Geltungsbereich pro einzelnem Worker.

Standardmäßig werden sechs Module ohne ArkAccessBridge installiert. Die Access-1.1.1-Integration betrifft getrennt geprüfte bestehende Offline-/AuthMe-Installationen; eine globale Verknüpfung allein belegt keinen Zugang zu einem vorhandenen Konto. Veröffentliche keine privaten UUID-/Kontenzuordnungen.

Trenne echte Microsoft-/Minecraft-Signierung und Java-/Bedrock-/Geräteabnahme von Testdiensten. Tests belegen weder echten Minecraft-Zugang einer Betreiber-OAuth-Anwendung noch Konsolensteuerung. Der Weltstand bleibt fest; private Reset-/Deploymentwerkzeuge gehören nicht zum öffentlichen Schnellstart.

Eigener Code und Dokumentation stehen unter MIT, eigene Weltinhalte unter CC BY 4.0. Für fremde APIs/Plugins gelten deren Lizenzen.
