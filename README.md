# Minecraft ARK 🦕

Eine fertige Survival-Abenteuerwelt mit getrenntem Kreativbereich und
Java-/Bedrock-Crossplay. Deutsch steht im Spiel und in dieser Anleitung im Vordergrund.
**Minecraft Java 26.3 · Paper 26.3/241 · Java 25 · keine Clientmods.**

[Welt herunterladen](https://github.com/Bladestar2105/Minecraft-Ark/releases/latest) ·
[Wiki](https://github.com/Bladestar2105/Minecraft-Ark/wiki) ·
[Installation](docs/Installation.md) · [Spielanleitung](docs/Playing.md)

- **20.480 × 20.480 Blöcke**, Seed `20261103`; 1.638.400 Survival-Chunks vorgeneriert.
- **16 Dörfer, 64 Häuser:** neun Raid-Dörfer und sieben sichere Dörfer.
- Zwölf Dino-Reviere mit 24 Sniffern/Ravagern, acht Händlerlager sowie je sechs Hexenlager, Piratencamps und Bossarenen.
- Zehn Quests, Abenteuer-Ränge, Grundstücksschutz und Atlas mit 55 Orten.
- Eigene flache Kreativwelt; Inventar, Endertruhe und Erfahrung bleiben von Survival getrennt.
- Eigenes Verwaltungs-Panel, Kartenansicht und lesende Laufzeit-Bridge.

Die Dinos nutzen Minecraft-Standardmodelle. Zähmen und Folgen/Warten sind enthalten;
Reiten und neue Modelle gibt es nicht. Beim Tod fallen Gegenstände herunter.

## Schnellstart auf einem eigenen Server

Voraussetzungen: Docker mit Compose v2, Python 3.10+, JDK 25 und `curl`.
Mindestens 8 GiB Host-RAM empfohlen; genügend SSD-Platz für entpackte Welt,
Archiv und Sicherungen einplanen. Der Spielcontainer nutzt höchstens 5 GiB.

```sh
git clone https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
cp .env.example .env
# Release-Assets herunterladen, Prüfsummen prüfen und Welt nach ./data entpacken:
# genaue Befehle: docs/World-Download.md
python3 scripts/build_plugins.py
python3 scripts/install_plugins.py
python3 scripts/init.py
# Minecraft-EULA lesen und danach in .env EULA=TRUE setzen.
docker compose config --quiet
docker compose up -d minecraft
docker compose logs -f minecraft
```

**Vor dem Start die Welt installieren.** Ohne `data/ark/level.dat` verweigert die
Initialisierung den Startplan. Keine Bau-/Resetfunktionen aufrufen: alle Orte sind
bereits gespeichert. Der Download enthält weder Minecraft-Server-JARs noch
Spielerkonten, private Schlüssel oder Zugangsdaten. Abhängigkeiten werden beim
Installieren von ihren Herausgebern geladen; Minecraft braucht eine eigene Lizenz.

Java: `localhost:25565` beziehungsweise deine Serveradresse. Bedrock: deine
Serveradresse, Port `19132`. Die Vorlage prüft Java-Sitzungen mit `online-mode=true`
und Bedrock-Konten über Floodgate. Lokale `/login`-Konten des privaten Originalservers
werden nicht übertragen. PS5/Switch benötigen gegebenenfalls eine eigene
Konsolen-Verbindungslösung; der Download richtet keinen öffentlichen DNS ein.

Panel optional: `docker compose --profile admin up -d --build` und
[http://localhost:8090](http://localhost:8090) öffnen. Das lokal erzeugte erste
Kennwort steht ausschließlich in `secrets/panel-login.txt`. Siehe
[Administration](docs/Administration.md) für Docker-Socket-Gruppe, Dateirechte und Fernzugriff.

## Quellcode und Lizenz

`dino-plugin`, `atlas-plugin`, `panel-plugin`, `access-plugin` enthalten eigene
Java-Plugins; `panel` enthält die Flask-Verwaltung. Der öffentliche Standardstack
installiert ArkDinos, ArkAtlas und ArkPanelBridge. ArkAccessBridge ist die getrennte
Integration für bestehende Offline-/AuthMe-Installationen und gehört **nicht** in
den frischen Online-Stack. [Build und Tests](docs/Development.md).

Eigener Code und Dokumentation: [MIT](LICENSE). Eigene Weltinhalte:
[CC BY 4.0](WORLD-LICENSE.md), auch kommerziell mit Namensnennung.
[Fremdsoftware und Credits](THIRD_PARTY.md) behalten ihre eigenen Lizenzen.
Kein offizielles Minecraft-/Mojang-/Microsoft-Produkt.
