# Native reference delivery — 22 November 2026

This is a reviewable **intermediate delivery**. The complete six-island world,
the finished Gallimimus film landscape and milestone B's visual acceptance are
not complete. No mandatory film set is declared finished. No production world,
player inventory or existing world release was replaced.

The branch builds on PR #15, including its latest gate correction at
`edf95c041bc3315948686bc4199a3ac5fcae3140`. PR #14's verified receiver property
validation patch is included as source and a checksummed dependency. Neither PR
being open nor a JAR's version name is treated as deployment evidence. See
[versions.json](versions.json) for the distinct source, fixture and observed
server versions.

## What exists

* Native Paper-generated Nublar reference: **2,048 × 2,048 blocks**, at
  x=7168..9215 and z=2048..4095, seed 19930611. All **16,384** requested chunks
  were decompressed and checked for matching coordinates and `minecraft:full`.
  The terrain contains grassland, wooded shoulders, a low hill, persistent
  foliage, a fallen trunk and an access path. The oval clearing and straight
  gravel path need further landscape design. The five camera positions are
  provisional.
* Six distinct island definitions, 37 landmark records, documented interpretations
  and a 32,768-square border. These records are not evidence that the other
  islands or film buildings have been generated or accepted. `world_2015` is
  reserved and disabled.
* An opt-in native generator with four-chunk batches, pause/resume, a bounded
  saved-prefix audit and UUID/seed/version/definition/implementation binding.
  Existing played-terrain migration is not implemented. Reopening a valid bound
  world does not start a generation job.
* A separate Gallimimus species, original model and Java/Bedrock exports, egg,
  care/growth/riding integration, loose herds and an optional nine-animal scene.
  The final design was assigned to **GPT-6.2 Sol, High**: 179 cubes, 12 bones,
  a small toothless beak, S-shaped neck, long running legs and balancing tail.
  Feathering and colour are explicitly reconstruction choices.
* Shared six-axis bounds from the final animated geometry. In particular the
  Mosasaurus upper extent is about 3.634845 blocks; water and size checks now
  consume the same generated limits instead of the old 3.5-block assumption.
* Per-world Atlas identity, MapView IDs and starter grants with legacy support;
  shared landmark/map exports and strict panel validation for a separate
  Jurassic inventory group. These are not native Multiverse acceptance results.

## Measurements and checks

The [reference receipt](reports/reference-native-20261122.json) records Paper
26.3/258, API heights -64..320 and Java 25.0.5+8. Generation, including the
pause/restart and checkpoint experiment, took **1,059.658 seconds**. The custom
dimension occupied **92,584,387 bytes**. Sampled rolling-average MSPT had median
0.509 and p95 1.358; these are not raw tick percentiles. No players or custom
models were present in that terrain measurement. Peak sampled RSS was about
1.26 GB. A linear full-square estimate is roughly 75 hours and 23.7 GB of
dimension data; differing terrain, structures and active players can change it.

The final binding JAR reopened the saved reference and audited all 16,384 chunks.
Copied UUID bindings and a wrong-UUID checkpoint were refused. Clean pause/restart
preserved the cursor. An intentionally overstated checkpoint rewound from 1,024
to the actual 36 saved chunks; this was fault injection, not a process-crash test.

Verification completed on the relevant scopes:

| Scope | Result and actual boundary |
|---|---|
| Model source/export/riding/gait/bounds suite | 49 Python tests; 26,004,552 transformed corner samples across all nine dinosaurs |
| Dinosaur rules | 16 Java test programs, including herd/scene budgets, ownership, growth, riding, movement and shared bounds |
| Full custom-plugin build | All eight modules built; final affected Dino, Atlas and generator JARs rebuilt and checked separately after review fixes |
| Jurassic source/runtime parsers | 29 Python tests and four Java test programs |
| Existing world-design suite on latest PR15 base | 21 tests, including the new gate wrapper |
| Panel | 203 tests with its installed Flask/requests environment |
| Installer/package scripts | 55 passed, two existing optional upstream-fixture skips |
| Atlas | 47 Java assertions; native Paper257 protocol fixture 17/17 including restart, old cards, per-world grants and atomic rejected reloads |
| Real model engines | Seven native gameplay groups on Paper246: all nine registrations, actual packs, Gallimimus single rider/run clip, four existing passenger species and restart |
| Water bounds | Paper246 aquatic 21/21 and swimming 10/10 with the new shared bounds; historical swimming cases do not establish Gallimimus swimming acceptance |
| Wildlife | Seven native groups including biome crossing without UUID/species replacement and preserved owned/named/leashed animals |
| Encounter | Final f08 on Paper258: 90 successful lifecycle/pathfinding/ticking-boundary checks and 11 controlled-contact checks. A separate correctly guard-aborted 410-tick edge run is excluded from full-duration positives; see the exact cohorts and limits in the scene/contact receipts |
| Receiver property guard | Original dependency reproduced failures; derived dependency passed the 12-case regression on Geyser1298 and1303; this does not prove a Switch crash fixed |

Native model, wildlife and water receipts deliberately identify their own
earlier frozen JARs. Later scene-only fixes were checked against the final scene
binary; the receipts are not relabelled as one identical full-stack run.

The actual generated Bedrock pack, manifest UUID/version, ZIP and canonical
content hashes are in the model receipt. A negative control proved that the
pinned bridge can reuse old 59-cube geometry even after installing the 179-cube
inputs. Follow the stopped-server cache backup procedure in
[dino-items/README.md](../../dino-items/README.md); actual cold/warm device-cache
acceptance is still open. Source complexity counts and repeated nests are not
FPS or console-load measurements.

## Usable files and remaining acceptance

The checksummed reference archive includes a complete isolated native save and
its final matching generator JAR/state. Its primary level is named `fixture`.
Follow the [import instructions](README.md#native-test-and-artifacts), use a fresh
isolated directory and never overlay it onto a played server. Generated Atlas
and panel PNGs are maps of saved block/biome data. Gallimimus galleries are source
model renders. **Neither is an actual Minecraft client screenshot.** Large
archives, images and integration exports are separate release artifacts.

No official Java rendered client, Windows/mobile Bedrock client or physical
Switch was used for this delivery. Protocol clients established native gameplay
and packet/server behaviour only. Device acceptance remains `NOT RUN` in
[acceptance.md](acceptance.md). Film framing, many visible nests, client joins
and cache states, protection/portal/respawn/inventory integration, inter-island
boat/flight journeys and second-passenger travel require their own acceptance.

Next required world work is the organic Gallimimus valley and five real camera
views, then Nublar's mandatory architecture/interiors, Sorna's film regions and
the four additional islands. The [manifest](world-manifest.json) keeps generated
and accepted coverage separate; its accepted-region list is empty. The full
4,194,304-chunk square has not been pregenerated.
