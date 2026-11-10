# All-in-One-Paket

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One) · [Installation aus Quellcode](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE)

Der stabile Anwendungsrelease [`app-2026-11-07`](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-07) stellt `Minecraft-Ark-app-2026-11-07-all-in-one.zip` mit Docker-Stack, Webanwendungen, Setup-/Konfigurationsquellen und allen sieben geprüften Projekt-JARs bereit. Die große Welt, fremde Server-/Plugin-JARs, persönliche Konten und Geheimnisse sind nicht eingebettet. Lade das ZIP und `SHA256SUMS` aus demselben Anwendungsrelease.

Voraussetzungen auf dem Host: Docker mit Compose v2, Python 3.10+, ein Programm zum Entpacken und Internetzugang. **Das Binärpaket benötigt kein Host-JDK.** Java 25 läuft im Spielcontainer. Plane Platz für Weltarchiv, entpackte Welt, Kartenberechnung und private Backups ein; mindestens 8 GiB Host-RAM sind eine erste Empfehlung, keine Kapazitätszusage.

Neu aus dem aktuellen Quellstand erzeugte Binärpakete enthalten zusätzlich die geprüfte, abgeleitete GeyserModelEngine-Bridge unter `runtime-dependencies/`. Sie ist getrennt von den sieben Projektplugins gekennzeichnet und wird vor der Installation auf ihre festgelegte Prüfsumme geprüft; dafür ist kein Host-JDK nötig. Original-URL, Prüfsummen, Quellpatch und Neubauprogramm bleiben enthalten. Bereits veröffentlichte Archive werden dadurch nicht verändert.

## Neues Setup

1. Lade das Anwendungs-ZIP und dessen `SHA256SUMS`. Prüfe vor dem Entpacken die SHA-256-Prüfsumme.
2. Entpacke in ein neues leeres Verzeichnis. Kopiere keine alte `.env`, Welt oder Zugangsdaten hinein.
3. Führe im entpackten Verzeichnis aus:

```sh
python3 scripts/setup.py
```

Das Setup prüft die mitgelieferten Projekt-JARs, lädt und prüft den **festen Weltstand `world-2026-11-06`**, installiert festgelegte Abhängigkeiten und erzeugt `.env` sowie lokale Geheimnisse. Standardmäßig werden sechs Projektplugins installiert; ArkAccessBridge bleibt optional und uninstalliert. Eine Teilmenge lässt sich mit `--modules` auswählen; siehe [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE).

Das Setup **akzeptiert keine EULA und startet keine Container**. Anschließend:

1. Lies die [Minecraft-EULA](https://www.minecraft.net/en-us/eula). Setze danach nur bei Zustimmung `EULA=TRUE` in der erzeugten `.env`.
2. Prüfe Ports, erlaubte Hosts, Linux-Dateirechte und Docker-Socket-Gruppe nach [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE).
3. Starte ausdrücklich:

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose logs -f minecraft
```

Die mitgelieferte `.env.example` aktiviert das Compose-Profil `admin` mit Verwaltung und getrennter Spieleranwendung. Mit leerem `COMPOSE_PROFILES` startet nur der Spielserver. Lies den ersten lokalen Adminzugang privat aus `secrets/panel-login.txt`; veröffentliche ihn nicht. Alle melden sich unter `http://localhost:8090/` an; Spielerfunktionen und aktuelle OP-Verwaltung verwenden dieselbe Oberfläche. Alte `/play/`-Links leiten zur Hauptadresse weiter.

## Nach dem Start

Prüfe Plugins, Inventartrennung und Schutz, bevor Spieler eingeladen werden. [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) beschreibt Spielports und Abnahmeprüfungen. Richte einen eigenen [Signierungsschlüssel oder ein eigenes Konto](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) ein; gemeinsame Betreiberzugänge sind nicht enthalten. Echte Microsoft-/Minecraft-Anmeldung und Bedrock-/Konsolenverhalten benötigen eine Prüfung mit eigenen Zugängen und Geräten.

Anwendungs- und Weltrelease sind getrennt. Der Weltbezug bleibt auf [world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/world-2026-11-06) festgelegt, unabhängig vom neuesten Anwendungsrelease. Sichere Updates privat nach [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE); führe das neue Setup nicht über einem bespielten Server aus.
