# Native temporary-encounter regression

Run `dino-plugin/encounter-runtime-test.py` with a cached Paper 26.3 runtime,
Java 25 and a built encounter-enabled ArkDinos JAR. The harness creates a fresh
private directory and binds its server to loopback. It never opens an existing
world. `EncounterFixture.jar` is a test helper, not a production plugin.

```sh
python3 dino-plugin/encounter-runtime-test.py \
  --java-home /path/to/jdk25 \
  --paper-cache /path/to/cached-server \
  --paper-jar /path/to/paper-26.3-258.jar \
  --output output/scene-checks
```

The default expected ArkDinos SHA256 is the reviewed `f08fd024…` build. Testing a
different candidate requires an explicit `--plugin-sha256` value, which is
recorded with the actual JAR hash.

Run again with `--chase-only` to observe the whole unmodified encounter through
its natural completion. Both modes load the complete travel corridor with
temporary fixture tickets. Chase mode records native positions, velocity, path state
and grazing state, and requires the Rex to reach the 56-block threat radius,
multiple native away-motion samples, nonpersistent actors throughout and full
automatic cleanup. No actor is teleported or driven by the fixture in this mode.
Positive chase runs must reach all 440 scene ticks, with all eight prey observed
past native age 400. An earlier safe boundary abort does not count as a completed
positive chase.
After initial path settling, an aware prey animal with a native path must not
remain stationary for 40 ticks on the unobstructed flat fixture. This catches
Paper activation pauses even when a path request reports success.
The scene activation cooldown must cover only its nine exact actors, retain its
original values and disappear on cleanup. An event monitor verifies that adopting
an actor restores its original cooldown within the taming interaction itself.

Add `--far-spectator` together with `--chase-only` for the admitted-distance edge:
the only Survival spectator remains 200 blocks south of the scene center, outside
the ordinary 96-block ecology scan. All eight animals must still run away during
the admitted encounter. Both modes also verify that an unrelated named animal
with the same public scene tag retains persistence and ordinary movement.

These lifecycle and chase cases hold a bounded corridor with fixture-only chunk
tickets. Those tickets make the scene chunks entity ticking; the far-viewer
results prove native activation and movement within that condition. A viewer's
render distance alone does not guarantee entity ticking.

Run `--lazy-chunks` separately to test that boundary without any fixture tickets.
The only Survival viewer stands 160 blocks south with view distance 20 and
simulation distance 3. The harness waits for all nine scene chunks to be loaded,
then confirms that a native cow in the first scene chunk reports
`Entity.isTicking() == false`. The scene must explicitly refuse its first actor
and clean up all scene state, while the owned pet and unrelated tagged animal
keep their ownership, persistence and cooldown. This case allocates no plugin
or permanent chunk tickets.

The fixture generates a flat `jurassic_archipelago` at scene coordinates,
temporarily loads a bounded set of scene/support chunks, disables natural
spawning and configures normal shared population/nearby budgets of 12/12.
One direct protocol 777 Survival client acts as the spectator. Tests cover:

- refusal without spectators and independent population/nearby refusals;
- eight Gallimimus plus one Rex, unique actor IDs, no duplicate start;
- real scheduler transitions through Grazing, Alert and Running;
- scene adoption through an actual item-interaction packet;
- no-viewer abort, cooldown and preservation of an existing pet, the adopted
  actor and an unrelated animal carrying the scene tag;
- empty actor/herd trackers and removal of every fixture ticket; ArkDinos itself
  must allocate no plugin or permanent chunk tickets.

The nearby-only refusal temporarily raises the world/per-player allowance to 24
while retaining the nearby limit 12. The normal scene runs after restoring 12/12.
The existing pet uses the real native `tame` method. For the scene adoption test,
the fixture primes five completed feeds and tests the actual seed-finisher
interaction within the 22-second encounter. This does not verify real feeding
cooldowns or a full timed taming sequence.

The JSON report records exact Paper/ArkDinos JAR hashes, native state snapshots,
commands, loaded chunks, TPS and MSPT. This is a native mechanics check with no model
stack, official rendering client, Bedrock client or physical console. It does
not approve film-scene appearance or console stability.
