# Jurassic Archipelago — reproducible test world

**Intermediate delivery, not acceptance of the complete six-island world.** The
source catalog has six masks and 37 planned landmarks. The native reference build
covers the Nublar meadow. Other islands and film structures still need generation,
detailed building and visual acceptance. Procedural shells are prototypes, not
completed film reconstructions. [Device acceptance](acceptance.md) stays separate.

`classic_adventure` combines Nublar 1993 and Sorna 1997/2001. The four outer islands
and shortened sea crossings are design choices. Nublar is additional to Las Cinco
Muertes; the 1993 Gallimimus scene belongs there. `world_2015` is disabled and
cannot add a second historical visitor center. See [sources](references.md).

## Definitions and build

`archipelago.json` defines seed, border, individual coasts, ridges and rivers.
`landmarks.json` provides stable IDs, provenance, footprints, access and proposed
cameras. **The five camera records have not been accepted as film-matching views.**
`generate.py` compiles immutable Java data/resources. The Paper generator separates
terrain, vegetation and structural passes. `plan` never marks a place generated.

```sh
python3 world-design/jurassic/generate.py compile
python3 -m unittest discover -s world-design/jurassic -p 'test_*.py'
python3 scripts/build_plugins.py
```

Builds use Java 25. The opt-in `jurassic` module produces
`jurassic-plugin/build/ArkJurassic-0.1.0.jar`; it is excluded from default fresh
installation modules. The existing `world-release.json` remains `world-2026-11-06`.

On an isolated server with the verified JAR:

```text
jurassic open
jurassic generate reference
jurassic status
jurassic pause
jurassic resume
```

Reference bounds are **x=7168..9215, z=2048..4095**: 16,384 chunks at their final
coordinates. The terrain has a 1400×800 meadow, low hill, fallen trunk and wooded
shoulders. Film likeness and sightlines still need official-client review.
`jurassic generate all` explicitly requests **4,194,304 chunks** for the complete
32,768-square border. Plan it from the measured reference; no full-square
completion is claimed.

Batches request at most four chunks. World access and checkpoints run on the
server thread; asynchronous generation modifies only ChunkData. No permanent
plugin chunk tickets are retained. Pause stops new submissions; wait for
`job.inflight:false` before a clean checkpoint. Resume rechecks the completed
prefix in slices of sixteen, rewinding at the first unsaved chunk. It also audits
a completed job. Repeated requests use saved native chunks and create no entities.

Binding includes UUID, seed, generator version, definition SHA and implementation
SHA. Existing unbound worlds and changed identities are refused. Previously
explicitly bound worlds reopen on server load by default; neither a new world nor
a generation job starts automatically. Configure reopening in
`plugins/ArkJurassic/config.yml`. **No played-terrain migration is implemented.**
Use a reviewed migration or another isolated world; do not remove binding guards.

## Native test and artifacts

```sh
python3 world-design/jurassic/runtime.py \
  --paper-cache /path/to/bootstrapped-paper \
  --paper-jar /path/to/verified-paper.jar --java-home /path/to/jdk25 \
  --output /path/to/new-test-artifacts
```

The loopback-only fixture uses cached inputs and opens no historical world. It
records actual versions, hashes, API heights, TPS/MSPT/RSS, disk use and native
blocks. Checks include clean restart and deliberately overstated-checkpoint
recovery. The latter is fault injection, **not an actual process-crash test**.
Header-entry counts are metrics; coverage requires exhaustive decompression,
matching coordinates and `full` status. Trunk and persistent leaves are checked.

Paper 26.3 stores dimensions inside the primary save. A dimension payload alone
is **not a standalone save**. The separate complete test-save archive includes
primary `level.dat`, dimensions and matching plugin/state. Keep its original JAR.
Extract to a **fresh isolated server directory**, set `level-name=fixture` in
`server.properties`,
bind loopback while testing and verify `jurassic open` / `jurassic status` before
admitting players. Never overlay its metadata/player data onto a played server.
Existing-save import remains a separate migration task. Large archives/images
belong in checksummed artifacts, not source commits.

`native_render.py` reads saved surface blocks/biomes; its map is **not a client
screenshot**. Source-model galleries are likewise not client evidence.

## Shared integration files

```sh
python3 world-design/jurassic/generate.py export \
  --receipt /path/to/native/report.json --output /path/to/new-integration-files
```

Export requires matching successful native coverage. It writes Atlas/quest site
data, a reduced square Atlas image with unknown areas transparent, a 512-pixel
panel reference map with exact bounds and a planned/generated/accepted manifest.
Unbuilt landmarks are not advertised as built. Quest output is coordinate data,
not an installed mission. Merge panel world records; retain existing maps. Follow
[Atlas configuration](../../atlas-plugin/README.md) for independent world IDs and
starter grants.

Before players enter, register the bound world in the deployment's Multiverse
setup and merge an independent inventory group into its existing groups file:

```yaml
groups:
  jurassic:
    worlds: [jurassic_archipelago]
    shares: [all]
```

Keep existing Survival and Creative groups intact. Panel validation rejects a
Jurassic group mixed with either. Native Multiverse inventory/portal/respawn tests
and inter-island boat/flight/passenger journeys remain open.

## Animals and optional scene

Gallimimus is a separate species, model, animation/export set and egg. Its final
independent design and measured bounds are tested alongside existing dinosaurs.
Wild herds graze and navigate near loaded Survival players, share a nearby Rex
threat and use separation/native pathfinding. Owner, rider, Stay and leash control
is preserved. Biomes choose first spawns; a biome crossing does not teleport or
remove an animal.

`/dinos scene start|stop|status` requires `ark.jurassic.scene`. This optional
prototype creates eight temporary Gallimimus plus one Rex only with spectators,
loaded safe positions and room in common budgets. The old defaults of eight per
player/six nearby cannot admit nine extras; explicitly budget suitable test
settings, e.g. twelve per player and twelve nearby. Existing populations count.
There is no automatic respawn loop. Timed phases, no-viewer abort, five-minute
cooldown and UUID/tag-scoped cleanup preserve animals tamed during the scene.

The pinned Paper 26.3 activation policy can otherwise stop a distant grazing
carrier. The scene keeps only its exact temporary actors active with a short
portal cooldown and restores each original value immediately on taming or
cleanup. Re-run the native over-200-tick scene checks when changing Paper.
Loaded chunks must also tick entities: the scene refuses to start, or aborts,
when an actor's chunk stops entity ticking. It adds no chunk tickets and does
not increase simulation distance. Move close enough to keep the whole scene
within the server's simulation distance.

The scene is **not accepted**. Film timing, camera composition, herds under load
and official Java/Bedrock/Switch rendering require tests on the completed terrain.
Protocol clients and source renders cannot establish those outcomes.
