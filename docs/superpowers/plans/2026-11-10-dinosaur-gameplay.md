# Dinosaur gameplay implementation plan

> **For agentic workers:** Use superpowers:subagent-driven-development or superpowers:executing-plans to implement the tasks with regression checks.

**Goal:** Resolve the reproducible dinosaur defects and requested gameplay gaps.

**Architecture:** Retain Paper carrier entities and BetterModel/Geyser adapters.
Centralize pure movement/size/access/balance rules; integrate them into existing
entity lifecycle handlers. Bound new world population work to loaded chunks.

**Tech Stack:** Java 25, Paper 26.3, BetterModel 3.6.0, Geyser 2.12.1-b1295,
Python model generators and dependency-free Java rule tests.

**Spec:** `docs/superpowers/specs/2026-11-10-dinosaur-gameplay-design.md`

## Global constraints

- Preserve UUID ownership and old saves; public riding defaults to false.
- No world replacement, production deployment, merge or paid services.
- Apply world scopes consistently; no forced chunk loads for roaming/spawning.
- Keep guard plugins, friendly pets, owner and passengers protected.
- Treat direct protocol tests separately from official-client acceptance.

## Review focus

- A public driver is not the owner; damage attribution and permissions use the correct identity.
- A rider dismounting must not corrupt another seat or leave flight/gravity state behind.
- Underwater Stay and offline owners still permit physical surface recovery.
- Loading/reloading a grown dinosaur never multiplies its size a second time.
- New spawns cannot bypass species taming before their first scheduled restore.

## Task 1: Combat and taming

Files: `DinoCombat.java`, `DinoProtection.java`, `DinoSpit.java`, `CareRules.java`,
`DinoCare.java`, combat/care regression tests. Root owns `ArkDinos.java`.

Interface consumed: `ArkDinos.driver(Mob)` returns authorized controlling player
or null. Persistent owner remains available separately via `owner(Entity)`.

- [x] Reproduce tame-victim defense, friendly-pet targeting and fast Rex taming.
- [x] Add regressions, then fix defense/target checks and driver attribution.
- [x] Add species-specific feed cooldown and Rex 12 feeds/20 seconds/20 damage.
- [x] Verify rule tests, compile and relevant native fixtures.

## Task 2: Movement and surface swimming

Files: `GroundSpeeds.java`, `FlightRules.java`, `DinoPtera.java`, new
`DinoSwimming.java`, `SwimRules.java`, rule tests. Root integrates lifecycle.

Interfaces produced: `DinoSwimming(ArkDinos)`, `restore(Entity)`, `unload(Entity)`,
`tick()` and `close()`. Root invokes `tick()` after ride control each tick.
FlightRules retains its old overload and adds a sprint-aware overload.

- [x] Pin finite speed/flight limits and water-surface behavior with regressions.
- [x] Implement species speeds and bounded loaded-water surface buoyancy.
- [x] Verify species exclusions, no blanket damage immunity and lifecycle cleanup.

## Task 3: Sizes and animation anatomy

Files: `dino-models/build_models.py`, pose tests and affected generated exports;
new `DinoSizes.java` and `DinoSizesTest.java`. Root owns adapters.

Interfaces produced: `DinoSizes.adultScale(String modelName)` and
`DinoSizes.backHeight(String modelName)` (scaled adult back height).

- [x] Record failing real anatomy checks and all-clip jaw/contact coverage.
- [x] Repair demonstrated detached limbs/head; regenerate affected exports.
- [x] Add pure species-size factors and verify growth, seats and hatch integration.

## Task 4: Access, world population and integration

Files: `ArkDinos.java`, `DinoControls.java`, `RidingRules.java`, model/seat adapters,
new bounded wild-spawn controller/rules, config, tests and bilingual docs.

- [x] Test private/public owner access and first-tick taming fail-closed behavior.
- [x] Implement persistent public riding and integrate movement/size/combat helpers.
- [x] Evaluate separate-seat capability before enabling two-rider behavior. Current native/Geyser seats share one position; the optional second-seat feature remains deferred and is not advertised as implemented.
- [x] Add bounded world spawns in loaded appropriate terrain; preserve old sites.
- [x] Add generator no-fence behavior for future habitat placement without modifying existing builds.
- [x] Update build test registration and focused CI model coverage.
- [x] Run the relevant suites, review the complete diff and document both verified results and blocked checks.

## Verification and handoff

Implemented tasks were checked by native RED/GREEN regressions, twelve pure Java test programs, all seven project-plugin builds, model/export/gait checks, Python distribution/world tests and a separate code review. Exact test scope, snapshots, remaining official-client acceptance and the conditional second-seat gap are recorded in `docs/Dinosaur-Review-2026-11-10.md`. The full model-stack runtime remains blocked by Geyser's external encryption-discovery HTTPS timeout: the final run reached eight BetterModel registrations but failed its Bedrock registry assertion, before size/growth/reload acceptance. This is an explicit open release check, not a passed test. No merge or production deployment is authorized by this plan.
