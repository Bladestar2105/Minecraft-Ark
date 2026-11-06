# Installation

Benötigt werden Docker/Compose v2, Python 3.10+, JDK 25 sowie Internetzugriff auf
GitHub, Modrinth, Paper und Geyser. Für kleine private Gruppen mindestens 8 GiB
Host-RAM und SSD verwenden. Die konfigurierte Grenze von zehn Spielern ist keine
nachgewiesene Lastkapazität. Welt, Archiv, Renderdaten und Backups brauchen getrennt Platz.

```sh
git clone https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
cp .env.example .env
```

Die [Release-Welt](World-Download.md) zuerst nach `./data` entpacken. Der Wurzelpfad
muss `data/ark/level.dat` sein. `data/server.properties` gehört nicht zum öffentlichen
Archiv. Die gesamte `dimensions/`-Struktur erhalten; die Kreativwelt ist darin gespeichert.

JDK 25 in `JAVA_HOME` setzen, falls `java`/`javac` nicht bereits darauf zeigen.

```sh
python3 scripts/build_plugins.py
python3 scripts/install_plugins.py
python3 scripts/init.py
```

Der Build lädt geprüfte Entwicklungsabhängigkeiten und führt die Java-Regeltests aus.
Die Installation lädt exakt benannte Plugin-/Datapack-Versionen bei den Herausgebern.
Die Initialisierung erzeugt eigene RCON-/Panelgeheimnisse und frische Servereinstellungen.
Vorhandene Zugangsdaten und `server.properties` werden nicht überschrieben.

Die [Minecraft-EULA](https://www.minecraft.net/en-us/eula) lesen, dann in `.env`
`EULA=TRUE` setzen. Auf Linux die Daten-/Panelrechte kontrollieren; siehe Administration.

```sh
docker compose config --quiet
docker compose up -d minecraft
docker compose logs -f minecraft
docker compose exec minecraft rcon-cli version
docker compose exec minecraft rcon-cli plugins
```

Erst Spieler einladen, wenn der Log den vollständigen Start meldet und insbesondere
Multiverse-Core/-Inventories/-Portals, WorldGuard, GriefPrevention, Quests, ArkDinos,
ArkAtlas, Geyser und Floodgate aktiviert sind. Ohne Inventartrennung und Gebietsschutz
keine öffentliche Spielrunde starten. Ein neuer Testspieler ohne OP muss beide
Tore durchlaufen, seine getrennten Inventare zurückerhalten und außerhalb geschützter
Orte bauen können. Diese Installation erzeugt keine neuen Bauten und ruft keine
Reset-/Baufunktionen auf.

Java nutzt TCP 25565. Bedrock nutzt Port 19132; die Vorlage veröffentlicht UDP und TCP.
Nur benötigte Spielports am eigenen Router/Firewall weiterleiten. RCON und BlueMap
bleiben intern. Eigene Adressen eintragen; der Download enthält keine Serveradresse.

Die frische Vorlage verwendet `online-mode=true` für Java und Floodgate für Bedrock.
ArkAccessBridge/AuthMe werden nicht installiert. Die Offline-Migration einer bestehenden
Instanz erfordert eine separate Planung und private Konten; keine Online-/Offline-
Umstellung nach Beginn einer Spielrunde ohne Identitätsmigration vornehmen.

Offizielle Grundlagen: [Geyser-Installation](https://geysermc.org/wiki/geyser/setup/),
[Floodgate](https://geysermc.org/wiki/floodgate/setup/),
[Docker-Server](https://docker-minecraft-server.readthedocs.io/).
