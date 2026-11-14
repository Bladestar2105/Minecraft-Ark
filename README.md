# Minecraft ARK 🦕

A prepared survival adventure world, separate creative area and Java/Bedrock crossplay. **Minecraft Java 26.3 · Paper 26.3/246 · Java 25 · no client mods.** Both webinterfaces offer English and German; existing adventure content is mainly German.

[Official wiki](https://github.com/Bladestar2105/Minecraft-Ark/wiki) · [Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Home-DE) · [All-in-One guide](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One) · [Fixed world download](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/world-2026-11-06)

- 20,480 × 20,480 survival blocks, seed `20261103`, 1,638,400 pregenerated chunks.
- 16 villages/64 houses, 12 dinosaur habitats, trader/witch/pirate camps, boss arenas, quests and an atlas with 55 locations.
- ArkDinos 1.2.1: owner feeding heals injured pets, `/dinos` offers compass tracking, T-Rex taming has feeding protection, and Pteranodons can follow their flying owner or land for fish. Eight models, surface swimming, public riding and second seats remain available. Real Java/Bedrock/console visual acceptance remains open. See the [dinosaur review and upgrade notes](docs/Dinosaur-Review-2026-11-10.md).
- Separate creative inventories, ender chest and experience; items drop on death in survival.
- Local administration plus an isolated player portal with shared server passwords, optional recovery and own statistics.
- Optional built-in skin editor/wardrobe/signing and persistent private support requests.

## Choose an installation

**Version note:** the stable `app-2026-11-14-2` binary contains ArkDinos 1.2.1, ArkSupport 1.0.2 with direct in-game replies and the 30-minute daylight/10-minute night pack. For another source revision, build below or use a matching successful [Checks workflow artifact](https://github.com/Bladestar2105/Minecraft-Ark/actions/workflows/checks.yml). Check `version ArkDinos` on an existing server before diagnosing its behavior. [Existing-server update guide](docs/Update-2026-11-14.md).

**Binary All-in-One:** [`app-2026-11-14-2`](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-14-2) provides `Minecraft-Ark-app-2026-11-14-2-all-in-one.zip` with the Docker stack and all seven project JARs. Verify its release `SHA256SUMS`, extract into a new empty directory, then run:

```sh
python3 scripts/setup.py
```

Host requirements are Docker with Compose v2, Python 3.10+, an archive extractor and internet. **No host JDK is needed for this binary package.** Setup verifies the JARs, downloads/checks the separate fixed `world-2026-11-06` snapshot, installs pinned dependencies and generates `.env` and private local secrets. It does not accept the EULA or start containers. Do not create `.env`, `data`, `secrets` or `panel-state` before fresh setup.

**From source:** use JDK 25 as well, then:

```sh
git clone https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
python3 scripts/build_plugins.py
python3 scripts/setup.py
```

After either route, read the [Minecraft EULA](https://www.minecraft.net/en-us/eula), set `EULA=TRUE` in the generated `.env` only if accepted, and review ports/hosts/permissions. Start explicitly:

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose logs -f minecraft
```

The default `admin` Compose profile starts Minecraft, the panel and the isolated player app. It does not include AdGuard/public DNS. Leave `COMPOSE_PROFILES` empty for game-only startup. The local panel is `http://localhost:8090`, all accounts use the same root login and OP menus appear automatically; read the locally generated recovery admin login privately in `secrets/panel-login.txt`. At least 8 GiB host RAM is a starting recommendation; allow separate disk space for archive, world, renders and backups. [Installation and checks](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration).

Application releases and world snapshots are separate. New app releases must not change the world's fixed download tag. A world archive alone does not install new plugins. Never extract over a played server or treat fresh setup as an updater.

## Independent modules and sign-ins

The seven project plugins are ArkAccounts, ArkWardrobe, ArkSupport, ArkDinos, ArkAtlas, ArkPanelBridge and optional ArkAccessBridge **1.1.1**. Six install by default; ArkAccessBridge is built/bundled but excluded from the fresh online-mode stack. `setup.py --modules dino,atlas,panel,accounts,support` selects a subset; Wardrobe/Support require Accounts. [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules).

ArkAccounts owns passwords, recovery, profile and sessions. All players use the root login with their server name/password; `/web` is an alternative for authenticated players. A fresh `/web` check allows first-password/unknown-password setup for five minutes. Registered OPs use the same login and personal pages; administration menus appear after a current server-side OP check and disappear when those rights are removed. The separate local recovery admin remains usable without Minecraft and does not receive player features.

ArkWardrobe adds the own editor/library, `/wardrobe list` and `/wardrobe wear <name>`. Operators configure their own MineSkin key or self-hosted Microsoft/Minecraft account; no shared credentials are supplied. ArkSupport adds `/support [low|normal|high] Message`, private tickets and replies. Admin password resets under **Players** belong to Accounts and stay available without either optional module.

[Player portal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) · [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) · [Skin editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor) · [Signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) · [Dino riding](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding).

## Security, acceptance and licenses

The fresh stack uses Java `online-mode=true` and Floodgate, not mandatory AuthMe/ArkAccessBridge. Existing offline/AuthMe servers need reviewed migration; a global link alone cannot unlock an existing unbound AuthMe account. Preserve verified local mutual linking and private identity data. [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security).

Java defaults to port 25565, Bedrock to 19132. PS5/Switch may need a separate connection method. Real OAuth/Minecraft access, client skin display and Bedrock/console controls require actual account/device acceptance; fixture tests do not establish them. No production deployment is implied by these instructions.

Original code/docs: [MIT](LICENSE). Original world design: [CC BY 4.0](WORLD-LICENSE.md), including commercial use with attribution. [Third-party software and credits](THIRD_PARTY.md) retain their own licenses. [Build and tests](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development). This is not an official Minecraft, Mojang or Microsoft product.
