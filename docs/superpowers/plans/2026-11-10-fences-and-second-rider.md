# Open habitats and second rider implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development for the independent migration, native seat and model synchronization tasks.

**Goal:** Remove the original live habitat fences and provide a tested second
seat for the four large adult ground species.

**Architecture:** Retain the current dinosaur carrier and controlling rider.
Add one independently positioned marker seat, with lifecycle and permissions
owned by a small seat controller. Extend the optional per-viewer model clock.
Audit the existing habitats and apply the separate bounded migration only
where original fences remain. All twelve live audit areas were already open.

**Tech Stack:** Java 25, Paper 26.3/241, BetterModel 3.6.0, Geyser 2.12.1-b1295,
Python migration generation, authenticated portal/RCON administration.

**Spec:** `docs/superpowers/specs/2026-11-10-fences-and-second-rider.md`

## Global constraints

- Never record account passwords or session cookies in the repository.
- Preserve animals, owners, existing player builds and original world archive.
- Do not deploy the new plugin or merge the draft PR as part of live fence work.
- The guest cannot steer, attack as the dinosaur, become driver implicitly, or
  receive owner-only rights.
- Do not force-load chunks for seats; maintenance tickets are temporary and
  pre-existing tickets are preserved.

## Review focus

- No overlapping seats or repeated player teleport corrections during travel.
- Independent guest look direction and cancellation-safe teardown.
- Guest collision during turns, low ceilings and water ascent.
- Public revocation with the owner in either seat; no old spit targets a rider.
- Conditional fence apply/undo and exact protection of northern lamp posts.

## Tasks

- [x] Implement and test the manifest-based fence migration in `world-design/`
  with scan/apply/undo, a console export and precise documentation.
- [x] Obtain verified live positions and inspect through the authorized
  portal. All twelve original sites were already open: zero fences/gates in
  their 37 x 11 x 37 audit boxes. No block removal or replacement was needed.
  Release introduced maintenance tickets and clear the console selection.
- [x] Add `SeatRules`, `DinoSeats` and native API compatibility checks. Prove
  the separate marker position and absence of repeated player teleport packets.
- [x] Extend `DinoModels`/`BetterModelAdapter` with `seat(Mob, Entity, double,
  double)` and `clearSeat(Mob)`; test clock fallback, offsets and teardown.
- [x] Integrate into `ArkDinos`, combat and swimming. Add a focused two-client
  native harness covering permissions, driving, both dismount paths, movement,
  collision, water, mode/death/unload/disable and persistence cleanup.
- [x] Correct the prior review's version/world-scope assumptions, update player
  documentation, build, obtain an independent review and update draft PR #7.
