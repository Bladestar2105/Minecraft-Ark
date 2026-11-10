# GeyserModelEngine task publication fix

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

This rebuild does not change public dependency pins or deploy anything.
