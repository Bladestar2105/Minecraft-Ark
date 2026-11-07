# Vorbereitete Welt herunterladen

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE)

Die Welt ist ein eigener fester Release: **[world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/world-2026-11-06)**. Anwendungsreleases und CI-Artefakte können neuer sein. Halte den Weltdownload unabhängig vom neuesten Anwendungsrelease festgelegt.

## Empfohlen: geprüftes Setup

Führe im neuen Binärpaket `python3 scripts/setup.py` aus; baue im Quellcode-Checkout vorher die Projekt-JARs. Das Setup lädt die sieben Teile des festen Weltrelease, prüft festgelegte Größen und SHA-256-Hashes, das zusammengesetzte Archiv und die begrenzte Extraktion. Anschließend liegt die Welt unter `data/`. Heruntergeladene Teile bleiben zur geprüften Wiederverwendung unter `.cache/world/`. Lege `.env`/`data`/`secrets`/`panel-state` nicht vor dem neuen Setup an.

Der festgelegte Stand hat 12.555.565.183 komprimierte Bytes und entpackt 17.092.079.344 Bytes in 5.459 Dateien. Plane zusätzlich Platz für Abhängigkeiten, Karten und private Backups ein.

## Manueller Download für erfahrene Betreiber

Lade alle sieben Teile `minecraft-ark-world-2026-11-06.tar.gz.part-000` bis `...part-006` sowie `SHA256SUMS`, `world-manifest.json` und Lizenz-/Quellennachweise aus demselben festen Release. Prüfe **jeden** Teil vor dem Entpacken:

```sh
# Linux:
sha256sum -c SHA256SUMS
# macOS:
shasum -a 256 -c SHA256SUMS
# Erst nach erfolgreicher Prüfung, in ein neues Zielverzeichnis:
cat minecraft-ark-world-2026-11-06.tar.gz.part-* | tar -xz
```

Die dreistelligen Namen bestimmen die Reihenfolge. Überspringe keine fehlenden oder fehlerhaften Teile. Unter Windows eignen sich WSL oder ein geordnetes Zusammenfügen nach Einzelprüfung der SHA-256-Hashes und anschließendes Entpacken der `.tar.gz` mit einem geeigneten Archivprogramm. Manuelles Entpacken ist kein `setup.py`: Installiere die gebauten Plugins/Abhängigkeiten und führe `scripts/init.py` getrennt aus, bevor du der EULA zustimmst und etwas startest.

Das Archiv enthält `data/ark/level.dat`, den gesamten Baum `data/ark/dimensions/minecraft/` mit Oberwelt, Nether, Ende und Kreativ sowie geprüfte Spielkonfiguration. Bewahre die jeweiligen `data/minecraft/world_gen_settings.dat`, Entitäten, POI und Kartendaten auf; nur `region/` zu kopieren verliert Weltzustand. Dies ist das Paper-26.3-Format, keine Bedrock-`.mcworld`.

Der öffentliche Stand enthält keine Spielerkonten, Inventare/Fortschritte, privaten Grundstücke, Schlüssel, RCON-Einstellungen, Logs oder gerenderten Kartencaches. Fremde JARs und getrennt lizenzierte Dungeons-and-Taverns-/Geophilic-Pakete werden bei der Installation aus festgelegten Quellen bezogen. Neue Anwendungsmodule sind nicht automatisch Bestandteil eines Weltdownloads.

Entpacke niemals über einer laufenden oder bespielten Installation. Halte private Backups bereit. Eigener Code steht unter MIT, eigene Weltinhalte unter CC BY 4.0; siehe [Weltlizenz](https://github.com/Bladestar2105/Minecraft-Ark/blob/main/WORLD-LICENSE.md).
