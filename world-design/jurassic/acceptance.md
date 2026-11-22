# Jurassic archipelago: acceptance protocol

This protocol records requirements, not passing results. Runtime generation,
automated checks and measured outputs belong in dated reports and the world
manifest. The client matrix below is **NOT RUN** for this new archipelago until
an actual observation with evidence is entered. Previous-world tests and PR
descriptions do not transfer a pass to this world or its final resource pack.

## Device matrix

| Check | Java rendered client | Windows Bedrock | Mobile Bedrock | Physical Switch / Switch 2 |
| --- | --- | --- | --- | --- |
| Join empty vanilla test region | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Load final pack with no nearby custom entities | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| One stationary custom model | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Each animation in isolation | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| One egg, then multiple nests | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Gallimimus herd and optional Rex scene | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Driver, passenger, dismount and owner rights | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Death, respawn and correct inventory/world | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| World change, reconnect and repeated join | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Cold pack cache, then warm cache | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Fast movement and inter-island travel | NOT RUN | NOT RUN | NOT RUN | NOT RUN |
| Five Gallimimus scene camera views | NOT RUN | NOT RUN | NOT RUN | NOT RUN |

A Java protocol bot can establish connectivity and server behavior. It cannot
establish Java rendering, Bedrock rendering, console memory stability or a
physical console application's successful operation. Without a physical Switch,
that device's acceptance remains open.

## Capture one controlled run

1. Record the exact source commit, generator version, world UUID, seed, profile,
   generated bounds, Minecraft/Paper build and Java version. Read actual minimum
   and maximum height from the running server API. Keep the historical world
   release identity separate.
2. Record installed JAR SHA-256 values and the final resource-pack SHA-256,
   manifest UUIDs/versions, compressed bytes and client-reported cache state.
   A matching version label or merged PR does not prove the correct JAR is loaded.
3. Record device model/OS, game build, render/simulation distance, resource pack
   settings and network route. Use the same route and scene for comparisons.
4. Start with the empty region. Change one variable per comparison: pack load,
   one idle species, one animation, one egg, nests, then herd and Rex. Record the
   count and model/bone/cube/texture budgets at each step.
5. For a cold-cache run, explicitly remove only the test pack through the
   client's supported cache controls and record that action. Rejoin and capture
   the download/prompt and time to control. A reconnect alone is not proof of a
   cold cache. Repeat without clearing for the warm-cache comparison.
6. Capture process outcome precisely: normal join, disconnect/timeout, frozen
   client, or application-process crash. Preserve visible message and timestamp;
   correlate server logs. A server timeout is not a confirmed process crash.
7. Record scene start, abort after all spectators leave, cooldown and restart.
   Check that only this scene's temporary actors were removed. Record entity,
   tracker and chunk-ticket counts before, during and after, including plugin
   disable/reload and world unload where supported.

## Runtime and generation measurements

Record samples rather than an unqualified “fast” or “stable”. An initial bounded
run informs the larger job; a short run does not establish long-term stability.

| Field | Required evidence |
| --- | --- |
| Generated chunks | Planned rectangle and successfully saved chunk count, including chunk-edge convention |
| Generation time | Wall time, CPU time if available, warm/cold fixture state, and chunks/second |
| Storage | Saved world bytes after clean stop; archive bytes and SHA-256 |
| Memory | JVM arguments, sampled/max RSS, heap usage and sampling method |
| Tick performance | TPS and MSPT distribution, sample interval and duration; report generation and traversal separately |
| Loaded environment | Active chunks, loaded entities, custom trackers and visible models per view |
| Client behavior | Join-to-control times, observed FPS/stutters and device/process outcome |
| Model budget | Cubes, bones, texture dimensions/bytes, animation channels and concurrent instances from the delivered pack |
| Extrapolation | Measured base, formula and uncertainty for 4,194,304 chunks; no unmeasured total claimed |

Pause/resume must keep world UUID, seed, generator version and configuration
hash bound to the same job. Interrupt at a saved boundary, resume, then compare
with an uninterrupted fixture. A fresh world with a matching name is not the
same job. Test regeneration order independently; compare terrain, vegetation
and structures, not unstable timestamps or compression bytes alone.

## World and integration checks

- Confirm all six island identities and separation, no accidental land bridges,
  no structure footprints outside the border, and the correct Nublar Gallimimus
  assignment. Distinguish planned, generated and visually accepted features.
- Visit every mandatory landmark's access points; inspect silhouette,
  proportions, central interiors and camera views. A sign is not a completed set.
- Verify safe spawns/respawns and walkable paths, boats and mounted flight
  through the intended routes. Test driver and second-passenger world changes.
- Test old atlas maps and starter allocation as well as new maps; world identity,
  origin, extent, zoom, MapView IDs and leaderboard positions must agree.
- Test survival/creative inventory separation, portals, permissions and relevant
  protection rules with ordinary players, not only an administrator bypass.
- Test a wild animal crossing into another suitable area without relocation,
  removal or forced return. Test tame ownership, named animals, nests, feeding,
  growth and existing ride/passenger behavior separately from scene actors.
- Compare runtime clearance with the final animated geometry, including the
  entire Mosasaurus cycle. Bedrock `visible_bounds` alone is not a clearance test.
- Verify PR #14's actual installed receiver hash before its reproduction tests;
  the property-validation fix is not proof of a resolved Switch crash. Keep
  egg/nest geometry load experiments independent of that earlier defect.

## Release evidence

Keep large worlds outside normal source commits. A world artifact requires a
clean-stop receipt, exact source/config identity, world UUID, generated chunk
bounds/counts, per-file or archive checksums, import instructions and backup /
rollback procedure. Never overlay generated terrain onto a played world without
an explicit migration. Preserve existing players, inventories and the separate
`world-2026-11-06` release identity.

The archipelago is complete only after all six islands and required locations
have both generation evidence and recorded acceptance. A playable Nublar
reference region is a useful intermediate delivery, not final world acceptance.
