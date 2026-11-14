# One-time world building sources

`renewal.py` generates explicit Minecraft building functions and a manifest for
the original ARK villages, spawn, habitats and trader camps. It uses only the
Python standard library. These functions do not run automatically at startup.

Village merchants have separate stock by role: farmers sell food, tool merchants
sell iron tools and shields, seed merchants sell seeds, and cartographers sell
maps, compasses and paper.

Run the generator checks with:

```sh
python3 -m unittest discover -s world-design -p 'test_*.py'
```

Changing these sources does not update merchants in an existing world or the
pinned downloadable world release. Existing merchants need a separately reviewed
Offers-only migration. **Do not rerun building functions to repair trades:** they
reconstruct terrain, houses and entities.

## Removing original habitat perimeter fences

`fence_migration.py` prepares a separate, explicit migration from the original
`ark_renewal/manifest.json`. It only targets the 123 original fence positions and
three gate positions per habitat, preserving both northern lamp bases. Actual
matching block states are captured before removal and retained for guarded undo.
No build/reset function is used and no function runs automatically.

The alternative `worldedit_bundle(...)` API prepares ordinary console commands
for installed WorldEdit 7.4.6, including a bounded Sponge schematic backup and
an undo mask that preserves subsequent builds. Both paths are documented in
[Fence-Migration-DE](../docs/Fence-Migration-DE.md).

The [2026-11-10 live audit](fence-audit-20261110.json) found zero fences or fence
gates in each of the twelve original habitat areas. It changed no world blocks.
The [sanitized console journal](fence-audit-20261110.commands.jsonl) records the
read-only counts, temporary chunk tickets and confirmed cleanup.

The native integration check starts only a new loopback Paper fixture:

```sh
JAVA_HOME=/path/to/jdk25 \
FENCE_PAPER_CACHE=/path/to/cached-paper-runtime \
FENCE_PAPER_JAR=/path/to/paper-26.3-246.jar \
FENCE_WORLDEDIT_JAR=/path/to/worldedit-bukkit-7.4.6.jar \
python3 world-design/fence_runtime_test.py
```

It downloads nothing and opens no historical world. `FENCE_TEST_OUTPUT` selects
the temporary fixture/report parent directory. Without `FENCE_WORLDEDIT_JAR`,
only the datapack path is exercised.
