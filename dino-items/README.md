# Species egg graphics

Eight original 16×16 sprites use different shell shapes, colors and markings:

| Species | Shell |
| --- | --- |
| T-Rex | Olive green, dark broken slashes |
| Triceratops | Broad sandstone shell, three brown chevrons |
| Stegosaurus | Copper red, central diamond plates |
| Brachiosaurus | Large sage shell, grouped green spots |
| Velociraptor | Slender ochre shell, diagonal stripes |
| Pteranodon | Pointed blue shell, dark wing chevrons |
| Dilophosaurus | Plum shell, paired dark bands |
| Mosasaurus | Wide teal shell, navy wave bands |

Run `python3 dino-items/build_eggs.py` to regenerate the text-only asset bundle and embedded plugin resource, then `python3 dino-items/test_eggs.py` to verify all references. The generator uses the Python standard library. Preview files are written to local `output/` and are not release inputs. The sprites and generator are original project assets; see the repository license.

Every egg remains a `minecraft:sniffer_egg` with `minecraft:item_model` set to `arkdinos:egg_<species>`. The gameplay plugin controls egg placement, ownership and hatching. The Mosasaurus item is a creative spawn tool, because Mosasaurus does not use nest hatching.

Java uses a flat generated item model, visible in inventories, hands and dropped items. `BetterModelAdapter` subscribes to BetterModel 3.6.0's supported `PluginStartReloadEvent`, adding only the 24 `assets/arkdinos/` files from the embedded `egg-assets.properties`. BetterModel generates its normal `plugins/BetterModel/build.zip`; the existing portal route continues serving that pack.

Bedrock uses Geyser v2 item-model definitions in `plugins/Geyser-Spigot/custom_mappings/ark-dino-eggs.json` plus `plugins/Geyser-Spigot/packs/ark-dino-eggs.mcpack`. The standard installer creates these alongside GeyserModelEngine's generated pack. The egg pack uses its own namespace and UUIDs and does not replace dinosaur models, other packs, mappings or caches. Geyser 2.12.1 build 1298 supports this mapping format; see the [official custom item documentation](https://geysermc.org/wiki/geyser/custom-items/). Bedrock players must reconnect and accept the updated resource packs. Actual console rendering still requires a PS5 check.

Existing egg stacks refresh their item-model component when players join or open an inventory, preserving quantity, custom names, custom lore and persistent species/owner data. Placed nests remain vanilla blocks; breaking a nest returns an egg with the current item model. These sprites do not override the ordinary vanilla sniffer egg or its block texture.

Placed dinosaur nests also have seven distinct opaque species shells. Run `python3 dino-items/build_placed_eggs.py` and `python3 dino-items/test_placed_eggs.py` to regenerate and check `models/ark_egg_<species>/`. Each folder contains the editable BetterModel model and its matching Bedrock geometry, animation, texture and GeyserModelEngine configuration. The shell encloses Mojang 26.3's native Sniffer Egg mesh by 0.02 pixels on every side. A nonpersistent marker actor carries the model only while its nest is loaded; it has no hitbox, so the original egg remains selectable and retains its collision, protections and incubation data. Breaking, hatching, unloading and disabling remove the presentation; reloads rebuild it from the existing nest. Without the optional model stack the ordinary native egg remains visible and functional. The Mosasaurus creative spawn tool has no nest.
