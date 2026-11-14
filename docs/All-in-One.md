# All-in-One package

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE) · [Source installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules)

The stable application release [`app-2026-11-07`](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-07) provides `Minecraft-Ark-app-2026-11-07-all-in-one.zip` with the Docker stack, web applications, setup/configuration sources and all seven verified project JARs. It does **not** embed the large world, third-party server/plugins, personal accounts or secrets. Download the ZIP and `SHA256SUMS` from that same application release.

Requirements on the host: Docker with Compose v2, Python 3.10+, an archive extractor and internet access. **No host JDK is required for the binary package.** Java 25 runs inside the game container. Allow space for the world archive, extracted world, map rendering and private backups; at least 8 GiB host RAM is a starting recommendation, not a capacity guarantee.

New binary packages built from the current sources additionally include the verified derived GeyserModelEngine bridge under `runtime-dependencies/`, separate from the seven project plugins. Setup checks its pinned checksum before installation and needs no host JDK. The original URL, checksums, source patch and rebuild program remain included. Existing published archives are unchanged.

## Fresh setup

1. Download the application ZIP and its release `SHA256SUMS`. Verify its SHA-256 before extracting.
2. Extract into a new empty directory. Do not copy an old `.env`, world or secrets into it.
3. From the extracted directory run:

```sh
python3 scripts/setup.py
```

Setup verifies the bundled project JARs, downloads and checks the **fixed `world-2026-11-06`** snapshot, installs pinned dependencies and creates `.env` plus local secrets. The default installs six project plugins; optional ArkAccessBridge remains uninstalled. A module subset is available through `--modules`; see [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules).

Setup **does not accept the EULA and does not start containers**. Afterwards:

1. Read the [Minecraft EULA](https://www.minecraft.net/en-us/eula), then set `EULA=TRUE` in the generated `.env` only if you accept it.
2. Review ports, trusted hosts, Linux file permissions and Docker socket group using [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration).
3. Start explicitly:

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose logs -f minecraft
```

The supplied `.env.example` enables the `admin` Compose profile, including the admin and isolated player apps. Leave `COMPOSE_PROFILES` empty for game-only startup. Read the initial local admin login privately in `secrets/panel-login.txt`; never publish it. Everyone signs in at `http://localhost:8090/`; player functions and current OP administration share this interface. Old `/play/` links redirect to the root.

## After startup

Check enabled plugins, inventory separation and protection before inviting players. Follow [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) for game ports and acceptance checks. Configure your own [skin-signing key or account](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing); no shared signing credentials are included. Real Microsoft/Minecraft authorization and Bedrock/console behavior require operator/device acceptance tests.

The application release and world release have different lifecycles. World downloads stay pinned to [world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/world-2026-11-06), never whichever application release is “latest”. For updates, back up privately and follow [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration); do not rerun fresh setup over a played server.
