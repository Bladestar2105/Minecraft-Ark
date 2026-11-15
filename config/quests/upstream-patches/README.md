# Quests 5.3.2 mission availability patch

Upstream: [PikaMug/Quests 5.3.2](https://github.com/PikaMug/Quests/releases/tag/5.3.2), [source archive](https://codeload.github.com/PikaMug/Quests/zip/refs/tags/5.3.2), [MIT license](https://github.com/PikaMug/Quests/blob/5.3.2/LICENSE.txt). The unmodified MIT notice is included in `LICENSE-Quests.txt` and added to the derived JAR under `META-INF/ark-mission-fix/`.

The native loader reads YAML in definition order, but its concurrent hash set loses that order. Native player lists also color quests as available without checking their prerequisites. NPC menus independently alphabetize their entries and use unconditional available colors.

This pinned transformer makes three bounded changes:

- `BukkitQuestsPlugin`: use a thread-safe, insertion-ordered `CopyOnWriteArraySet` for the loaded quests and its setter. Existing add/clear/reload operations and uniqueness are retained.
- `BukkitQuester.canAcceptOffer`: test native requirements before offering; explain unmet requirements using the existing localized requirement renderer. The list and offer paths share this check. Native confirmed acceptance still rechecks and consumes requirements itself.
- `NpcOfferQuestPrompt`: retain the supplied order and show uncompleted, unavailable choices in gray using the same native availability predicate. Native choice handling and completed colors remain intact.

The patch changes only those three classes. Other JAR entries retain their original contents. It adds no plugin, quest, permission, player data migration or new prerequisite policy. Definition order is authoritative: keep prerequisite missions before their dependents in `quests.yml`. Missing prerequisites and cycles do not bypass native checks.

Pinned input SHA-256:
`d969b77c5beb8f08d54538e43cdb4765507d7faf3bf08e5ffb03d1ebe39140c0`

Derived SHA-256:
`638c0dc94c052d18c573c60752e06a2fcd6ba986eb400e00daaa56e1619d5f04`

Rebuild with Java 25 (verified with 25.0.5+8):

```sh
java config/quests/upstream-patches/QuestsMissionAvailabilityFix.java \
  upstream-Quests-5.3.2.jar Quests-5.3.2.jar
```

The transformer rejects other upstream hashes and refuses to overwrite an existing output. The installer independently verifies upstream and derived checksums. Binary distributions bundle the verified derived JAR so setup does not need a host JDK.

Run the deterministic rebuild, unchanged-entry, license and refusal checks:

```sh
QUEST_UPSTREAM_JAR=/absolute/path/to/upstream-Quests-5.3.2.jar \
JAVA_HOME=/absolute/path/to/jdk25 \
python3 -m unittest discover -s scripts -p test_quests_missions.py
```

For the native smoke test, provide a disposable Paper 26.3 build 252 JAR/cache, the patched Quests JAR and the pinned build dependencies in the checkout's `.deps` directory. Existing builds already have those dependencies. To download them without rebuilding plugins:

```sh
PYTHONPATH=scripts python3 - <<'PY'
import json
from build_plugins import ROOT, download
for dependency in json.loads((ROOT / 'dependencies-build.json').read_text()):
    download(dependency, ROOT / '.deps')
PY
```

Alternatively, set `QUEST_TEST_CLASSPATH` to an explicit Paper API and dependency classpath. The Paper cache must contain `libraries`, `versions` and `cache`. The harness binds loopback ports, creates a fresh peaceful flat world and a synthetic Survival non-OP actor, and shuts down its own server. It never opens a production world or profile. `MissionFixture.java` is test-only and must never be installed on a player server.

```sh
JAVA_HOME=/absolute/path/to/jdk25 \
DINO_PAPER_JAR=/absolute/path/to/paper.jar \
DINO_PAPER_CACHE=/absolute/path/to/disposable-paper-cache \
QUEST_JAR=/absolute/path/to/patched-Quests-5.3.2.jar \
QUEST_TEST_OUTPUT=/absolute/path/to/new-test-output \
node config/quests/upstream-patches/native-mission-smoke.mjs
```

The native test uses real protocol 777 command and plain-chat packets. It checks all ten definitions and prerequisite edges, both root acceptances, German confirmation/decline, locked colors and explanations, console/NPC order and choice, read-only requirement display, native item consumption only after confirmation, excluded-world pause/resume and saved state after a real restart. Prerequisite history is seeded only for the disposable actor to exercise unlocks without granting rewards. This is server/client-protocol coverage, not a rendered console or Bedrock-client test.
