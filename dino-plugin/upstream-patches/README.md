# Reviewed upstream fixes

## GeyserModelEngine task publication fix

GeyserModelEngine1.0.9 starts a zero-delay task inside its constructor, before `BetterModelEntityData.entityTask` is assigned. A visible Bedrock viewer can be recorded before its actor spawn throws on the null task, preventing a retry. The accompanying source patch makes that field volatile and defers an unpublished or replaced handler before it changes viewers.

Rebuild with Java25 from the exact shipped upstream JAR (SHA-256 `ce4295d05517c6bcdc1d5bc0c6e51b2daf4d07a45bc834e239c1d6019df035cc`):

```sh
java dino-plugin/upstream-patches/GeyserModelEngineTaskPublicationFix.java /path/to/GeyserModelEngine-1.0.9.jar /private/output/GeyserModelEngine-1.0.9.jar
```

The standard Java ClassFile transformer changes only the two affected classes and preserves every other entry's content; recompression may change ZIP storage metadata. It needs no Gradle dependency downloads, rejects a different or signed baseline, never overwrites an input/output, and prints the rebuilt SHA-256. Java25.0.5+8 produces `ec18241f9a6be0cd1dfa05664cbfb27fac8c075a641973131d22e83ae1db61d8` (verified with two independent rebuilds); compare that hash before using the output. Apply the source patch instead when building a complete upstream source checkout.

The deterministic native regression uses the installed Paper/BetterModel/Geyser stack, executes the first task before its constructor returns, checks the initial actor's actual outgoing spawn, then checks that a replaced handler cannot update viewers:

```sh
DINO_BRIDGE_RACE=1 DINO_GME_JAR=/private/output/GeyserModelEngine-1.0.9.jar DINO_MODEL_OUTPUT=/private/evidence node dino-plugin/model-runtime-test.mjs
```

The runtime manifest pins the reviewed output SHA-256 and original input SHA-256 separately. A source checkout reproduces the bridge automatically with Java25 before installation; set `JAVA_HOME` to the Java25 JDK. A verified cached output is reused.

New binary packages contain the verified derived JAR separately at `runtime-dependencies/GeyserModelEngine-1.0.9.jar`, alongside the seven project plugins. Binary setup verifies its exact manifest checksum before copying it and needs no host JDK or bridge download. A changed bundled JAR is rejected rather than silently replaced. No binary is stored in Git, and existing release archives are unchanged.

Upstream source and original artifact: [GeyserModelEngine](https://github.com/GeyserExtensionists/GeyserModelEngine), with its [MIT license](LICENSE-GeyserModelEngine.txt). The derived JAR retains all upstream entries except the two patched class payloads, including their original licensing information. The original download URL, both checksums, license, source patch and transformer remain in the binary archive for provenance. This does not deploy anything or modify existing installations.

## GeyserUtils entity-property validation

ARK's model configuration sets `disable_part_visibility: true`, so GeyserModelEngine
omits the corresponding `modelengine:boneN` properties from the Bedrock entity
registration. Its BetterModel sender still produces those updates. The pinned
GeyserUtils receiver passed them to Geyser's internal property manager without
checking the declaration; an unknown name becomes property index `-1`.

The accompanying [source patch](geyser-utils-entity-property-validation.patch)
validates the current entity's registered definitions, canonicalizes identifiers
(including the default `minecraft:` namespace), rejects unknown names and type
mismatches, and uses the declared Boolean/Integer property object for accepted
updates. A rejected or malformed update neither mutates nor flushes pending
properties. The source baseline is GeyserUtils commit
`fff5e1f7fafe0ecf926c9bda9325ab5236b4283e`.

Rebuild the pinned original extension with Java25:

```sh
java dino-plugin/upstream-patches/GeyserUtilsEntityPropertyValidationFix.java /path/to/upstream/geyserutils-geyser-1.0-SNAPSHOT.jar /private/output/geyserutils-geyser-1.0-SNAPSHOT.jar
```

- Original SHA-256: `8ed7b7dbece8341277652d4ee01706913e725cdf19b9664025bbb870299ec12a`.
- Derived SHA-256 with Temurin25.0.5+8: `3d764418b47dfbaba0fb20bd3d3fa471b02e77a4a32c73d147a6e366bfe1214a`.

The standalone transformer changes only `GeyserUtils.class`. It preserves the old
receiver and its stack maps under a private name, adds a small dispatcher for
`EntityPropertyPacket`, and implements the source patch's validated handling in a
private helper. Every other packet type still uses the unchanged original body.
This avoids rebuilding unrelated dependencies or requiring a server classpath
while applying the patch. All 3,423 JAR entries are retained, and repeated builds
produce identical bytes. The original input, pre-existing outputs, signed inputs
and unexpected input hashes are protected in the same way as the other patches.

The runtime manifest keeps the upstream URL and input checksum alongside the
new output checksum. Source setup applies the transformer automatically. Binary
packages include the verified extension at
`runtime-dependencies/geyserutils-geyser-1.0-SNAPSHOT.jar`; fresh setup installs it
under `data/plugins/Geyser-Spigot/extensions` without a host JDK or download.
The separate Spigot-side GeyserUtils plugin and the Geyser version pin are unchanged.
`dependencies-production.json` remains an observation of the previous deployment;
this PR does not assert that the new extension is already installed.

### Regression checks

Run with Java25 and Maven on PATH:

```sh
python3 scripts/verify_geyser_properties.py
python3 -m unittest discover -s scripts -p 'test_distribution.py'
```

If an older checkout cached the pristine extension at
`.deps/geyserutils-geyser-1.0-SNAPSHOT.jar`, checksum validation rejects that stale
build-cache entry. Remove that one cached file and rerun to rebuild the pinned patch.

The receiver suite loads the actual checksum-verified, classfile-patched JAR with
Geyser **b1298** (the repository pin) and **b1303** (the reported deployed build).
There is no upstream source project's `target/classes` on the test classpath.
The real receiver, identifiers and property manager are used; the session/entity
lookup and final network-send boundary are substituted. Tests cover undeclared
names, type mismatches, valid indices, pending changes, absent/empty definitions,
null/malformed names or values, default namespaces, and delegation of a different
packet type. Maven dependencies and the startup instrumentation agent are test-only.
The installation fixture also checks the extension destination, checksum rejection,
and operation from a bundled artifact without host Java or network access.

These are offline regression checks. The observed Switch 2 crash has not been
reproduced in the test suite or proven fixed. A controlled client join, including
nearby models and animations, is still required. For an existing server, stop
Minecraft, back up and replace only the Geyser-side extension, then fully start it.
Keep the previous JAR outside the loaded extension directory for rollback. No
pack, DNS, authentication, player-data or world migration is part of this change.

Upstream: [GeyserUtils](https://github.com/GeyserExtensionists/GeyserUtils), with its
[MIT license](LICENSE-GeyserUtils.txt). The source patch, transformer, license and
regression sources are included in the distribution allowlist; no binary is committed.
