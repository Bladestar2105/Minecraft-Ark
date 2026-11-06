# Download the world

The prepared world is available separately from source under
[GitHub Releases](https://github.com/Bladestar2105/Minecraft-Ark/releases/latest).
For the **2026-11-06** release, download every
`minecraft-ark-world-2026-11-06.tar.gz.part-000`, `...part-001`, etc., together with
`SHA256SUMS`, `world-manifest.json`, `WORLD-LICENSE.md`, `THIRD_PARTY.md` and
`LICENSE` when supplied. Each part is at most 1,800 MiB; only the final part may be
smaller. The release description states the part count and total size. All parts
must come from the same release. Place the files in the freshly cloned repository.

```sh
# Verify the downloaded release assets before extracting.
# Linux:
sha256sum -c SHA256SUMS
# macOS:
shasum -a 256 -c SHA256SUMS
```

After successful verification, extract from the repository directory on Linux/macOS:

```sh
cat minecraft-ark-world-2026-11-06.tar.gz.part-* | tar -xz
```

The three-digit numbering preserves order. Do not extract until every part passes
verification; do not skip missing parts. On Windows, join the files with PowerShell:

```powershell
$parts = Get-ChildItem minecraft-ark-world-2026-11-06.tar.gz.part-* | Sort-Object Name
$out = [IO.File]::Create((Join-Path $PWD 'minecraft-ark-world-2026-11-06.tar.gz'))
try {
  foreach ($part in $parts) {
    $inputFile = [IO.File]::OpenRead($part.FullName)
    try { $inputFile.CopyTo($out) } finally { $inputFile.Dispose() }
  }
} finally { $out.Dispose() }
```

First compare each part's `Get-FileHash -Algorithm SHA256 FILENAME` result with
`SHA256SUMS`. Then use 7-Zip to extract `.tar.gz`, followed by the enclosed `.tar`,
into the repository. WSL can use the Linux commands instead.

The archive starts with the relative directory `data/`:

```text
data/
  ark/
    level.dat
    datapacks/                    # original content; installer downloads third-party packs
    dimensions/minecraft/
      overworld/
      the_nether/
      the_end/
      ark_creative/
  plugins/                       # reviewed gameplay configuration and atlas data
```

The release is prepared from a consistent server copy and cleaned for publication.
Player profiles, inventories, progress, private claims, accounts, keys, logs and
RCON settings do not belong in the download. Third-party JARs and the ARR datapacks
Dungeons and Taverns/Geophilic are not bundled. The installer downloads those packs
separately before first startup. BlueMap rebuilds its web map from saved chunks;
the private server's rendered web cache is not included.

Preserve each dimension's `data/minecraft/world_gen_settings.dat` file. Transfer
the complete primary world, including all dimensions, map data, entities and POI.
Copying only `region/` loses other world state and the creative world. This is the
Paper 26.3 layout, not a Bedrock `.mcworld`. Singleplayer does not provide all plugin mechanics.

Never extract over a running or already played installation. Use a separate
directory and keep private backups. Original code is MIT; original world design is
CC BY 4.0. See [WORLD-LICENSE.md](https://github.com/Bladestar2105/Minecraft-Ark/blob/main/WORLD-LICENSE.md).
