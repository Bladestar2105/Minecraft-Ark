# Species egg graphics

Eight hand-pixeled 16×16 inventory sprites share their shell and pigment palettes with the hatchery models.

| Species | Shell |
| --- | --- |
| T-Rex | Ochre-olive shell with broken umber thumbprints |
| Triceratops | Broad sandstone shell with cinnamon speckles at the blunt end |
| Stegosaurus | Copper clay with scalloped rust-colored markings |
| Brachiosaurus | Broad celadon shell with soft moss-green patches |
| Velociraptor | Elongated ivory shell with drawn-out brown streaks |
| Pteranodon | Pointed slate-gray shell with fine marbling |
| Dilophosaurus | Warm mauve shell with paired wine-colored streaks |
| Mosasaurus | Wide sea-green spawn icon with dark, broken ripple bands |
| Gallimimus | Slender pale sand shell with fine olive-brown streaks |

Run `python3 dino-items/build_eggs.py` to regenerate the text-only asset bundle and embedded plugin resource, then `python3 dino-items/test_eggs.py` to verify all references. The generator uses the Python standard library. Preview files are written to local `output/` and are not release inputs. The sprites and generator are original project assets; see the repository license.

Every egg remains a `minecraft:sniffer_egg` with `minecraft:item_model` set to `arkdinos:egg_<species>`. The gameplay plugin controls egg placement, ownership and hatching. The Mosasaurus item is a creative spawn tool, because Mosasaurus does not use nest hatching.

Java uses a flat generated item model, visible in inventories, hands and dropped items. `BetterModelAdapter` subscribes to BetterModel 3.6.0's supported `PluginStartReloadEvent`, adding 27 species assets, nine vanilla Creative egg item models and English/German egg names from the embedded `egg-assets.properties`. BetterModel generates its normal `plugins/BetterModel/build.zip`; the existing portal route continues serving that pack.

The normal Creative Spawn Eggs tab and search expose the nine dinosaur eggs without a command. The pack replaces Bat, Endermite, Silverfish, Vex, Phantom, Strider, Tadpole Trader Llama and Armadillo egg graphics/names, in the species order above. Selecting one in Creative inside a configured dinosaur world produces the corresponding tagged egg, retaining the chosen quantity and binding it to the selecting player. Existing tagged/transferred eggs keep their owner. Outside that context the alias items retain vanilla server behavior; their pack appearance still uses the dinosaur icon. Default dinosaur worlds are `ark`, `ark_creative` and the separate `jurassic_archipelago`; Survival-only care/riding remains limited to `survival-worlds`.

Bedrock uses Geyser v2 item-model definitions in `plugins/Geyser-Spigot/custom_mappings/ark-dino-eggs.json` plus `plugins/Geyser-Spigot/packs/ark-dino-eggs.mcpack`. Native Creative aliases keep Geyser's ordinary vanilla item mappings, with resource-pack replacements for their legacy/current egg texture paths and English/German names. The standard installer creates these alongside GeyserModelEngine's generated pack. The egg pack uses stable independent UUIDs and does not replace dinosaur models, other packs, mappings or caches. Geyser 2.12.1 build 1298 supports the held-item mapping format; see the [official custom item documentation](https://geysermc.org/wiki/geyser/custom-items/). Bedrock players must reconnect and accept the updated resource packs. Actual rendering on physical Bedrock consoles still requires device checks.

Existing egg stacks refresh their item-model component when players join or open an inventory, preserving quantity, custom names, custom lore and persistent species/owner data. Placed nests remain vanilla blocks; breaking a nest returns an egg with the current item model. These sprites do not override the ordinary vanilla sniffer egg or its block texture.

Placed dinosaur nests have eight species shells. Inclined, opaque facets round the flanks and form a tapered crown. The pigment pattern follows the surface across every part. A fitted inner shell encloses Mojang 26.3's native Sniffer Egg mesh by 0.02 pixels on every side. The wider flanks fit inside one block horizontally. Run `python3 dino-items/build_placed_eggs.py` and `python3 dino-items/test_placed_eggs.py` to regenerate and check the BetterModel models and matching Bedrock geometry, animations, textures and GeyserModelEngine configurations in `models/ark_egg_<species>/`.

A nonpersistent marker actor carries the model while its nest is loaded. The original egg remains selectable and retains its collision, protections and incubation data. Breaking, hatching, unloading and disabling remove the presentation. Reloads rebuild it from the existing nest. Without the optional model stack the ordinary native egg remains visible and functional. The Mosasaurus creative spawn tool has no nest.

When upgrading dinosaur or placed-egg geometry, animation or textures with pinned GeyserModelEngine 1.0.9, stop the server and move only `generated_pack/` and `generated_pack.zip` from `plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/` into a backup outside that directory before restarting. The generator otherwise reuses cached geometry and textures: the native Gallimimus upgrade control retained the old 59-cube geometry despite installed 179-cube inputs when this step was omitted. Keep the input model/texture folders, other packs and mappings in place. Verify the regenerated archive with `dino-models/verify_bedrock_pack.py` and compare its manifest UUID/version and canonical content hash with `dino-models/pack_fingerprint.py` before reconnecting clients. A server-side cache check does not establish actual cold/warm client-cache acceptance.
