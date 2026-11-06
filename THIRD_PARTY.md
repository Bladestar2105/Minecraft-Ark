# Fremdsoftware und Credits

Dieses Repository verteilt eigenen Quellcode und eine separat veröffentlichte,
bereinigte Welt. Es enthält keine Minecraft-/Paper-Server-JAR, keine fremden Plugin-
JARs und keine fremden Datapack-ZIPs. Downloads werden von den Herausgebern bezogen.
Die Lizenz dieses Projekts ändert deren Bedingungen nicht.

| Projekt | Verwendeter Stand | Zweck / offizielle Quelle |
| --- | --- | --- |
| Minecraft / Mojang / Microsoft | Java 26.3 | Spiel und Grundressourcen; [EULA](https://www.minecraft.net/en-us/eula) |
| Paper | 26.3/241; API 26.3/236 | [Server/API und Lizenzinformationen](https://github.com/PaperMC/Paper) |
| itzg Docker Minecraft Server | Java-25-Image, Digest in Compose | [Quelle/Lizenz](https://github.com/itzg/docker-minecraft-server) |
| GeyserMC / Floodgate | 2.12.1-b1284 / 2.2.5-b141 | [Geyser](https://github.com/GeyserMC/Geyser/blob/master/LICENSE), [Floodgate](https://github.com/GeyserMC/Floodgate) |
| Multiverse Core / Inventories / Portals | 5.8.2 / 5.3.6 / 5.3.0 | [Multiverse](https://github.com/Multiverse) |
| WorldEdit / WorldGuard | 7.4.6 / 7.0.20 | [EngineHub](https://enginehub.org/) |
| GriefPrevention | 16.18.7 | [Quelle](https://github.com/TechFortress/GriefPrevention) |
| Quests / Parties | 5.3.2 / 3.2.18 | [Quests](https://github.com/PikaMug/Quests), [Parties](https://github.com/AlessioDP/Parties) |
| BlueMap | 5.29 | [Quelle](https://github.com/BlueMap-Minecraft/BlueMap) |
| Dungeons and Taverns | 6.1.0 | [NovaWostra / Projektseite](https://modrinth.com/datapack/dungeons-and-taverns); eigene Lizenz / ARR |
| Geophilic | 3.7 | [everloste / Projektseite](https://modrinth.com/datapack/geophilic/version/3.7); ARR |
| Flask, Docker SDK, PyYAML, Gunicorn | `panel/requirements.txt` | Bibliotheken separat über PyPI; Lizenztexte in den installierten Paketen erhalten |
| Adventure, Guava, Netty, Gson, Brigadier, SnakeYAML u.a. | `dependencies-build.json` | API-Buildabhängigkeiten von Maven Central, Paper, Minecraft und ihren Herausgebern |
| PacketEvents / AuthMe | 2.14.0 / 6.0.1 | Nur optionale ArkAccessBridge-Integration; [PacketEvents](https://github.com/retrooper/packetevents), [AuthMe](https://github.com/AuthMe/AuthMeReloaded) |

Geophilic und Dungeons and Taverns werden wegen ihrer eigenen Bedingungen nicht
mit dem Weltarchiv weiterverteilt. Der Installer bezieht die exakten Datapack-
Versionen von Modrinth. Auf bereits generiertem Gelände gespeicherte Weltinhalte
ersetzen keine Lizenz für die ursprünglichen Datapack-Dateien. Deren eigener Code,
Assets und Namen fallen nicht unter MIT/CC BY dieses Projekts.

Chunky 1.5.3 wurde für die abgeschlossene Vorgenerierung verwendet und muss nicht
zum Spielen installiert werden: [Quelle](https://github.com/pop4959/Chunky).
Die ursprünglichen Namen, Hinweise und Lizenzdateien in heruntergeladenen Paketen
behalten. Bei Weiterverteilung fremder Binärdateien deren Bedingungen eigenständig
prüfen; die hier verwendete Installationsweise lädt sie direkt beim Anbieter.
