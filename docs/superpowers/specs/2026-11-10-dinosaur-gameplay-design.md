# Dinosaur gameplay repair

Baseline: `d2acdad87c842dc961c1175bba63ff89396b971a`, reviewed 2026-11-10.

## Intent

Address the reported dinosaur movement, combat, access, water survival, size,
animation and Tyrannosaurus balance problems in the existing Paper plugin.
Keep Java/Bedrock compatibility and existing ownership, world scopes and saves.

## Approach and decisions

Use the existing carriers, model stack and persistent data. Correct proven
defects and extend the existing controllers with small, testable rules. A
client-mod rewrite would defeat crossplay; replacing the existing world would
discard player progress. Neither is required.

These implementation defaults are engineering choices, not previously agreed
game balance: walk/run speeds in blocks/s are Brachio 1.4/3.5, Stego 1.5/4.5,
Trike 2/7, Rex 2.5/9, Raptor 3/12, Dilo 2.8/10, Ptera 2/4.5. Flight is 12/18
blocks/s normal/sprint. Adult model factors are Rex 2.1, Trike 1.45, Stego
1.55, Brachio 2.15, Dilo 1.15; Raptor, Ptera and Mosa retain 1. These are
gameplay proportions, not claims of scientific accuracy.

T-Rex taming uses 12 primary feeds at 20-second intervals and the existing
finisher; other species retain their existing recipes. Base Rex melee damage
becomes 20, subject to normal armor, game difficulty and protection handling.

## Required behavior

- Wild dinosaurs can appear in suitable already loaded survival terrain and
  wander without a home/revier constraint. Bounded spawn attempts and population
  limits must prevent entity accumulation or forced chunk generation. Existing
  tames and player buildings must not be deleted or modified.
- Land dinosaurs receive bounded surface buoyancy, whether wild, following,
  staying or mounted. Swimming never grants blanket damage immunity and never
  treats lava as water. Mosasaurus retains its separate aquatic controller.
- Permitted damage against a tame can trigger its own defense, respecting
  Stay, juvenile status, valid owner/driver, friendly pets and all protections.
- Mounted attacks use the current authorized driver, protect the persistent
  owner and every passenger, and retain cooldowns. Spit cannot change attacker
  attribution if its driver changes in flight.
- The owner can toggle public riding. The setting grants riding only; naming,
  care, breeding, commands, release and ownership remain owner-only. Old saves
  default to private. Revocation is checked during riding.
- A second rider is conditional on two separately positioned real seats being
  supportable and verifiable with the pinned stack. Do not ship overlapping
  passengers or claim physical-client acceptance from protocol fixtures.
- Rendering, native collision, rider height, growth and hatch clearance use
  the same species size source. Restore/reload must not compound scales.
- Repair only demonstrated pose separations. Test all exported clips and
  connected anatomy; a nonexistent sit clip is not a reproduced sitting bug.
- A newly spawned tagged prototype dinosaur cannot take the legacy one-seed
  taming path before deferred identification.

## Verification and delivery

Run the existing Java rules/build and Python model, distribution, world and
panel suites. Add focused failing regressions for the changed behavior. Run
native Paper fixtures where their pinned dependencies are available. Record
limits for real Bedrock/console rendering, controls and production server
version. Preserve the fixed world release and do not deploy or merge.
