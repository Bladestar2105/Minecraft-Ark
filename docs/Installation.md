# Installation from source

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Binary All-in-One package](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules)

Use Docker with Compose v2, Python 3.10+ and **JDK 25** for the source build. Provide internet access to the pinned dependency publishers. An SSD and at least 8 GiB host RAM are starting recommendations; the ten-player default is not a tested capacity claim. Keep space for downloads, the extracted world, render data and private backups.

## Fresh source checkout

```sh
git clone --branch app-2026-11-07-photo-preview https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
# Set JAVA_HOME to JDK 25 if PATH uses another JDK.
python3 scripts/build_plugins.py
python3 scripts/setup.py
```

Do **not** create `.env`, `data`, `secrets` or `panel-state` before fresh setup. It refuses existing installations. The build creates all seven project JARs and their hash manifest. Setup installs six default modules, downloads the fixed [world-2026-11-06 snapshot](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download), installs pinned third-party dependencies and generates local `.env`/secrets. [Module selection](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules) can omit Wardrobe or Support; both require Accounts.

If you use a verified binary ZIP, skip the JDK/build step and follow [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One). Setup never accepts the EULA or starts services.

## Review and start

Read the [Minecraft EULA](https://www.minecraft.net/en-us/eula), then set `EULA=TRUE` in the generated `.env` only after accepting it. Review `JAVA_PORT`, `BEDROCK_PORT`, trusted hosts, the Compose profile and Linux permissions using [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration).

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose logs -f minecraft
docker compose exec minecraft rcon-cli plugins
```

The generated configuration enables the admin/player applications through `COMPOSE_PROFILES=admin`. The panel is `http://localhost:8090`, the player portal `/play/`. The initial local admin credentials are in `secrets/panel-login.txt`; read them privately. For game-only startup, leave `COMPOSE_PROFILES` empty.

The fresh stack uses Java `online-mode=true` and Floodgate for Bedrock; AuthMe/ArkAccessBridge are not installed by default. Existing offline/AuthMe servers need a separate identity migration. Never switch an active server's online/offline mode casually or copy private account links into a public package.

## Before inviting players

Check Multiverse-Core/-Inventories/-Portals, protection, quests, Geyser/Floodgate and selected project modules. Keep the server closed if inventory separation or protection is unavailable. Use a fresh non-OP test player to visit both worlds, recover separate inventories, build outside protected areas and try an atlas, dinosaur and quest.

Java uses TCP 25565 by default. Bedrock uses 19132; Compose publishes TCP and UDP. Forward only needed game ports. Administrative/private API ports stay internal or loopback. PS5/Switch need a suitable connection method; no public DNS service is supplied.

Set the generated `data/plugins/ArkAccounts/config.yml` `portal-url` to your externally reachable `/play/` URL. Configure [your own signing provider](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) only if Wardrobe is installed. Verify real account/device behavior on your installation; local fixtures do not establish Microsoft/Minecraft authorization or Bedrock acceptance.

The world is already built. Do not invoke build/reset functions or replace only `region/`; preserve the full dimension tree. First startup may download server files and take several minutes. See [Troubleshooting](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting), [Backups](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration#private-backups-and-updates) and [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security).

A public/global Floodgate link alone does not unlock an existing unbound AuthMe account. Confirm the original Java password or premium ownership first. Verified local mutual linking is retained through the reviewed AuthMe/Floodgate integration; do not replace it with an unchecked global lookup.
