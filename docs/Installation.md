# Installation

Install Docker/Compose v2, Python 3.10+, JDK 25 and provide internet access to
GitHub, Modrinth, Paper and Geyser. For a small private group, use an SSD and at
least 8 GiB of host RAM. The ten-player setting is not a tested capacity claim.
Allow separate space for the world, archive, render data and backups.

```sh
git clone https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
cp .env.example .env
```

First extract the [release world](World-Download.md) into `./data`.
The resulting path must be `data/ark/level.dat`. The public archive does not contain
`data/server.properties`. Preserve the entire `dimensions/` tree, which includes
creative and the per-dimension `data/minecraft/world_gen_settings.dat` metadata.

Set `JAVA_HOME` to JDK 25 unless `java` and `javac` already use it.

```sh
python3 scripts/build_plugins.py
python3 scripts/install_plugins.py
python3 scripts/init.py
```

The build downloads pinned development dependencies and runs the Java rule tests.
The installer downloads exact plugin/datapack versions with pinned hashes.
Initialization generates fresh RCON/panel secrets and server properties.
Existing secrets and `server.properties` are never overwritten.

Read the [Minecraft EULA](https://www.minecraft.net/en-us/eula), then set
`EULA=TRUE` in `.env`. On Linux, check data/panel permissions as described in Administration.

```sh
docker compose config --quiet
docker compose up -d minecraft
docker compose logs -f minecraft
docker compose exec minecraft rcon-cli version
docker compose exec minecraft rcon-cli plugins
```

Do not invite players until startup is complete and Multiverse-Core/-Inventories/
-Portals, WorldGuard, GriefPrevention, Quests, ArkDinos, ArkAtlas, Geyser and Floodgate
are enabled. Keep the server closed if inventory separation or protection is
unavailable. Before opening it, use a new non-operator test account to walk through
both portals, recover separate inventories and build outside protected locations.
Installation does not create new structures or invoke reset/build functions.
First startup can take several minutes while dependencies and legacy item support initialize.

Java uses TCP 25565. Bedrock uses port 19132; the template publishes both UDP and
TCP. Forward only required game ports through your own router/firewall. RCON and
BlueMap remain internal. Use addresses for your own network; none are supplied.

The fresh template uses `online-mode=true` for Java and Floodgate for Bedrock.
ArkAccessBridge/AuthMe are not installed. Migrating an existing offline server
requires separate planning and private account data. Do not switch an active
world between online/offline identity modes without an identity migration.

Official documentation: [Geyser setup](https://geysermc.org/wiki/geyser/setup/),
[Floodgate setup](https://geysermc.org/wiki/floodgate/setup/),
[Docker server](https://docker-minecraft-server.readthedocs.io/).
