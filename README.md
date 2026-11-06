# Minecraft ARK 🦕

A ready-made survival adventure world with a separate creative area and
Java/Bedrock crossplay. **Minecraft Java 26.3 · Paper 26.3/241 · Java 25 · no client mods.**

**Documentation is in English. In-game content and the administration panel are
currently primarily German.**

[Download the world](https://github.com/Bladestar2105/Minecraft-Ark/releases/latest) ·
[Wiki](https://github.com/Bladestar2105/Minecraft-Ark/wiki) ·
[Installation](docs/Installation.md) · [Player guide](docs/Playing.md)

- **20,480 × 20,480 blocks**, seed `20261103`; 1,638,400 survival chunks pregenerated.
- **16 villages and 64 houses:** nine raid villages and seven safe villages.
- Twelve dinosaur habitats with 24 sniffers/ravagers, eight trader camps, and six each of witch camps, pirate camps and boss arenas.
- Ten quests, adventure ranks, land claims and an atlas with 55 locations.
- A separate flat creative world; inventory, ender chest and experience stay separate from survival.
- A custom administration panel, map view and read-only runtime bridge.

Dinosaurs use vanilla Minecraft models. Taming and follow/stay are included;
riding and custom models are not. Items drop on death.

## Quick start on your own server

Requirements: Docker with Compose v2, Python 3.10+, JDK 25 and `curl`.
At least 8 GiB of host RAM is recommended. Allow enough SSD space for the extracted
world, download archive and backups. The game container is limited to 5 GiB.

```sh
git clone https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
cp .env.example .env
# Download the release assets, verify their checksums and extract the world into ./data.
# Exact commands: docs/World-Download.md
python3 scripts/build_plugins.py
python3 scripts/install_plugins.py
python3 scripts/init.py
# Read the Minecraft EULA, then set EULA=TRUE in .env.
docker compose config --quiet
docker compose up -d minecraft
docker compose logs -f minecraft
```

**Install the world before starting.** Initialization refuses to proceed without
`data/ark/level.dat`. Do not run build/reset functions: the locations are already
saved. The download contains no Minecraft server JARs, player accounts, private
keys or credentials. Dependencies are downloaded from their publishers during
installation. A valid Minecraft license is required.

Java: `localhost:25565` or your server address. Bedrock: your server address,
port `19132`. The template verifies Java sessions with `online-mode=true` and
Bedrock accounts through Floodgate. Private-server `/login` accounts are not
transferred. PS5/Switch may require a separate console connection solution;
this download does not set up a public DNS service.

Optional panel: run `docker compose --profile admin up -d --build`, then open
[http://localhost:8090](http://localhost:8090). The locally generated initial
password is stored only in `secrets/panel-login.txt`. See
[Administration](docs/Administration.md) for socket groups, file permissions and remote access.

## Source and licenses

`dino-plugin`, `atlas-plugin`, `panel-plugin` and `access-plugin` contain the custom
Java plugins; `panel` contains the Flask administration application. The standard
public stack installs ArkDinos, ArkAtlas and ArkPanelBridge. ArkAccessBridge is a
separate integration for existing offline/AuthMe installations and must **not** be
installed in this fresh online stack. See [Build and tests](docs/Development.md).

Original code and documentation: [MIT](LICENSE). Original world content:
[CC BY 4.0](WORLD-LICENSE.md), including commercial use with attribution.
[Third-party software and credits](THIRD_PARTY.md) retain their own licenses.
This is not an official Minecraft, Mojang or Microsoft product.
