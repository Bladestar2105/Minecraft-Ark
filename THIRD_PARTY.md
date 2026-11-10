# Third-party software and credits

This repository distributes original source code and a separately published,
sanitized world. It does not bundle Minecraft/Paper server JARs, third-party plugin
JARs or third-party datapack ZIPs. Dependencies are downloaded from publishers.
This project's licenses do not replace their terms.

| Project | Pinned version | Purpose / official source |
| --- | --- | --- |
| Minecraft / Mojang / Microsoft | Java 26.3 | Game and base resources; [EULA](https://www.minecraft.net/en-us/eula) |
| Paper | 26.3/246; API 26.3/236 | [Server/API and license information](https://github.com/PaperMC/Paper) |
| itzg Docker Minecraft Server | Java 25 image, digest in Compose | [Source/license](https://github.com/itzg/docker-minecraft-server) |
| GeyserMC / Floodgate | 2.12.1-b1298 / 2.2.5-b141 | [Geyser license](https://github.com/GeyserMC/Geyser/blob/master/LICENSE), [Floodgate](https://github.com/GeyserMC/Floodgate) |
| Floodgate SQLite linking extension | 2.2.5-b138 | Downloaded into Floodgate's own folder; [official linking setup](https://geysermc.org/wiki/floodgate/linking/#local-linking) |
| Multiverse Core / Inventories / Portals | 5.8.2 / 5.3.6 / 5.3.0 | [Multiverse](https://github.com/Multiverse) |
| WorldEdit / WorldGuard | 7.4.6 / 7.0.21 | [EngineHub](https://enginehub.org/) |
| GriefPrevention | 16.18.8 | [Source](https://github.com/TechFortress/GriefPrevention) |
| Quests / Parties | 5.3.2 / 3.2.18 | [Quests](https://github.com/PikaMug/Quests), [Parties](https://github.com/AlessioDP/Parties) |
| BlueMap | 5.29 | [Source](https://github.com/BlueMap-Minecraft/BlueMap) |
| Dungeons and Taverns | 6.1.0 | [NovaWostra / project page](https://modrinth.com/datapack/dungeons-and-taverns); separate terms / ARR |
| Geophilic | 3.7 | [everloste / project page](https://modrinth.com/datapack/geophilic/version/3.7); ARR |
| Flask, Docker SDK, PyYAML, Gunicorn | `panel/requirements.txt` | Downloaded separately from PyPI; retain licenses in installed packages |
| Adventure, Guava, Netty, Gson, Brigadier, SnakeYAML and others | `dependencies-build.json` | API build dependencies from Maven Central, Paper, Minecraft and their publishers |
| PacketEvents / AuthMe | 2.14.0 / 6.0.1 | Optional ArkAccessBridge integration only; [PacketEvents](https://github.com/retrooper/packetevents), [AuthMe](https://github.com/AuthMe/AuthMeReloaded) |

Geophilic and Dungeons and Taverns are not redistributed with the world archive
because they retain their own terms. The installer downloads the exact datapack
versions from Modrinth. Saved generated terrain does not grant a license to the
original datapack files. Their code, assets and names are not covered by this
project's MIT/CC BY licenses.

Chunky 1.5.3 was used for completed pregeneration and is not needed to play:
[source](https://github.com/pop4959/Chunky).
Preserve original notices and license files in downloaded packages. Review upstream
terms before redistributing third-party binaries. The supplied installer downloads
them directly from the listed providers instead.
# Skin support

The player-portal update downloads **SkinsRestorer15.12.6** from its official publisher with a SHA-256 pin. It is installed separately, not bundled into our own JAR. See [upstream source and license](https://github.com/SkinsRestorer/SkinsRestorer/tree/15.12.6). MineSkin is an optional signing service; the operator supplies their own API key. The alternate local generator uses the operator's own Microsoft/Minecraft authorization. No third-party account credentials or application IDs are redistributed.

