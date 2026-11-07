# Download the prepared world

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download-DE) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation)

The world is a separate fixed release: **[world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/world-2026-11-06)**. Application releases and CI artifacts may be newer. Keep world downloads pinned independently of application releases.

## Recommended: verified setup

In a fresh binary package, run `python3 scripts/setup.py`; in a source checkout, build the project JARs first. Setup fetches the seven parts from the fixed world release, verifies pinned sizes and SHA-256 hashes, checks the combined archive and bounded extraction, then installs the world into `data/`. Downloaded parts remain under `.cache/world/` for checked reuse. Do not create `.env`/`data`/`secrets`/`panel-state` before fresh setup.

The pinned snapshot is 12,555,565,183 compressed bytes, expanding to 17,092,079,344 bytes across 5,459 files. Allow additional space for dependencies, map data and private backups.

## Manual download for advanced installation

Download all seven `minecraft-ark-world-2026-11-06.tar.gz.part-000` through `...part-006`, plus `SHA256SUMS`, `world-manifest.json` and the license/credit files from that same fixed release. Verify **every** part before extracting:

```sh
# Linux:
sha256sum -c SHA256SUMS
# macOS:
shasum -a 256 -c SHA256SUMS
# Only after successful verification, in a fresh destination:
cat minecraft-ark-world-2026-11-06.tar.gz.part-* | tar -xz
```

The three-digit filenames define the order. Do not skip a missing or failed part. On Windows, use WSL for these commands or join parts in order after verifying each SHA-256 and extract the joined `.tar.gz` with an appropriate archive tool. A manual world extraction is not `setup.py`: install the built plugins/dependencies and run `scripts/init.py` separately before accepting the EULA and starting anything.

The archive contains `data/ark/level.dat`, the entire `data/ark/dimensions/minecraft/` tree (overworld, Nether, End and creative) and reviewed gameplay configuration. Preserve per-dimension `data/minecraft/world_gen_settings.dat`, entities, POI and map data; copying only `region/` loses world state. This is the Paper 26.3 layout, not a Bedrock `.mcworld`.

The public snapshot excludes player accounts, inventories/progress, private claims, keys, RCON settings, logs and rendered map caches. Third-party JARs and separately licensed Dungeons and Taverns/Geophilic packs are obtained from pinned publishers during installation. It does not include the new application modules merely because you downloaded the world.

Never extract over a running or already played installation. Keep private backups. Original code is MIT, original world content CC BY 4.0; see the [world license](https://github.com/Bladestar2105/Minecraft-Ark/blob/main/WORLD-LICENSE.md).
