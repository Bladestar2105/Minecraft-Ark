# Jurassic-Ark-Tor

Das Spawn-Tor trägt **JURASSIC** und **ARK** in roten Buchstaben mit Goldrand. Auf der PS5 erschienen die ursprünglichen Java-Textanzeigen winzig und überlagert, weil Geyser 2.12.1-b1306 ihre Skalierung beim Übersetzen verliert. Seit dem 22. November 2026 stellen 56 gesperrte Vanilla-Karten in fest montierten Leuchtrahmen die beiden Schriftzüge dar. Ein Spieler bestätigte die neue Beschriftung auf der PS5 als groß und gut lesbar.

## Karten lokal erzeugen

Im Projektstamm mit Python 3 ausführen. Die Kartenerzeugung verwendet ausschließlich die Standardbibliothek.

```sh
python3 world-design/jurassic-gate/sign_maps.py
```

Unter `world-design/jurassic-gate/generated/` liegen die 56 Farbdateien mit je 128 × 128 Pixeln, das Rahmenmanifest `manifest.json` und die zusammengesetzte Vorschau `mosaic.png`. Mit `--output VERZEICHNIS` lässt sich ein anderes Ausgabeziel wählen. `glyphs.json` enthält die beiden Wortmasken. Der Generator übernimmt Platzierung und Größe aus `gate-model.json`. Jede belegte Karte hat einen deckenden grauen Grund, der auf Bedrock das Holz des Rahmens abdeckt.

## Prüfung und natives Paket

Die Tests vergleichen Manifest und Kartenbytes mit den SHA-256-Summen der eingespielten Fassung in `live-sha256.txt`. Die vollständige Testsuite und die optionale lokale Paketerzeugung laufen unter Linux oder macOS. Für das Paket zuerst die Karten im Standardverzeichnis erzeugen.

```sh
python3 -m unittest discover -s world-design/jurassic-gate -v
python3 world-design/jurassic-gate/prepare.py
```

`prepare.py` schreibt die 56 nativen Kartendateien, das temporäre Datapack und das Paketmanifest nach `payload/`. Die Servermigration mit `deploy.py` ist ein separater manueller Vorgang für dieses bestehende Tor auf Paper 26.3. Das Skript kennt die ursprünglichen zehn Anzeigen-UUIDs, die reservierten Karten-IDs und die festen Serverpfade. Es prüft die 5.211 Tor- und Portalzellen aus `expected-v5.json` und erhält die Anzeigen-UUIDs beim Leeren der alten Texte. Die 207 Bodenblöcke, die 13 × 15 Blöcke freie Durchfahrt, die Pylonen und die Holzflügel entsprechen der vorherigen Torfassung.
