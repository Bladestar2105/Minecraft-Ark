# Riding your dinosaur

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE) · [Player guide](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing)

ArkDinos 1.2.0 adds eight original models and Pteranodon flight. The world archive alone does not include this update or its resource packs. This crossplay prototype still needs visual acceptance on actual Java/Bedrock/console clients.

Outside prototype worlds, tame a special habitat animal with one torchflower seed in Survival. Prototype worlds use the feeding rules below. Within the configured Survival worlds, riding starts private; the owner can enable public riding in a prototype world. Aim at its torso when taming or mounting; long tails and wings are decorative.

1. Empty your main hand and use/interact with your own or publicly shared unoccupied adult dinosaur.
2. Use the normal movement controls to steer; sprint to run faster.
3. Sneak/use the dismount control to get off. In a configured dinosaur world, sneak+interact on foot with an empty hand opens **Follow**, **Stay**, **Free wander**, **Reiten: nur Besitzer/öffentlich** (private/public riding), and a separately confirmed **Release** action. Free wander retains ownership and defense. Released adults remain peaceful toward their latest former owner. Outside prototype worlds, sneak+interact retains the legacy stay/follow toggle.

No saddle or additional client mod is needed. Guests receive riding access only, without rights to name, feed, leash, change behavior or release the animal. The owner can revoke access while a guest rides, which dismounts the guest. The setting survives restarts; old pets start private. Wild dinosaurs and Creative players are not eligible. A rider suspends following; the saved stay/follow setting remains available after dismounting.

## Riding together

Adult **T-Rex, Triceratops, Stegosaurus and Brachiosaurus** have a second seat in configured dinosaur worlds. The owner enables **Riding: public**. Once the first player is aboard, a second player can interact with the same dinosaur using an empty main hand. Both must be eligible Survival players. Smaller species, Pteranodon and juveniles do not gain a second seat; Mosasaurus remains unrideable.

The front rider steers and commands dinosaur attacks. The passenger sits behind, keeps an independent view and can Sneak to dismount alone. The driver's exit or loss of permission ends both seats, without automatically promoting the passenger. Revoking public access preserves an owner driving the dinosaur but ejects a public driver together with their passenger. Swimming checks both players' headroom. An obstacle along the rear seat's turning arc can prevent rotation as well as movement.

This initial implementation requires Paper 26.3. Visible rendering of both seats with Java, Bedrock and console clients still needs acceptance before live release.

## Pteranodon flight

Jump takes off and gains height. Movement steers relative to your view; look down while moving forward to descend. Release movement and jump to hover. Descend onto open ground to land. Flight is capped at 48 blocks above takeoff and below the world ceiling. Land before dismounting where possible: an in-air dismount starts the animal's controlled descent and gives temporary slow falling to its rider. The other six rideable species remain ground mounts.

Pets stay in their world and do not load distant chunks or teleport to you. The seven land/flying species rise to an open water surface while ridden, unmounted or in Stay. Deep, covered or obstructed water can still be dangerous; there is no blanket immunity to drowning, falls or lava. Avoid narrow passages and dangerous terrain. If control stops after leaving Survival or the world, return normally and interact again.

Java and Bedrock use their normal movement/dismount controls. Bedrock/console input and seat appearance still require a real-device acceptance check; server and automated Java-client checks do not establish console behavior.

Both editions require the server resource packs. Automated checks cover all eight engine exports, native flight inputs/lifecycle, Geyser rider ordering and interaction IDs. Real chunk/world unloading after both riders leave and queries at unloaded boundaries are verified. The [current verification report](Dinosaur-Final-Verification-2026-11-10.md) records actual Java rendering evidence and the remaining Bedrock/console acceptance. Emulated pack acknowledgments are not visual rendering evidence.

## Taming, eggs and combat in prototype worlds

T-Rex needs twelve raw-meat feeds, twenty seconds apart, then a bone. The first-to-twelfth-feed interval is at least three minutes forty seconds. Velociraptor and Dilophosaurus still need five raw-meat feeds, ten seconds apart, then a bone. Pteranodon needs five raw-fish feeds, ten seconds apart, then seagrass. Brachiosaurus, Triceratops and Stegosaurus need eight feeds of wheat, fern or dandelion in any combination, fifteen seconds apart, then a torchflower seed. Wrong food and early clicks consume nothing. Visible progress stays reserved for you for five minutes after the latest feed.

Feed two owned adults of the same species within six blocks. A successful pair puts a named species egg into your inventory; overflow drops beside you. Parents then have a five-minute breeding cooldown. In Creative mode, `/dinoeier` opens all eight egg choices and gives a real inventory item. Transferred eggs retain their original owner. Every species has its own inventory image. The seven hatchable species also have different placed shell models over the unchanged native sniffer-egg block.

Place an egg where block placement is allowed. Incubation takes twenty minutes and growth takes fifteen minutes in every dinosaur world. A blocked hatch waits for space. Hatchlings start small. Triceratops horns, Stegosaurus plates and tail spikes, Pteranodon crest, and Dilophosaurus crests and frill have hatchling, juvenile and adult detail stages; the juvenile stage starts halfway through growth. Growth food advances these features too. Accepted food removes one minute from the remaining growth time, with thirty seconds between feeds. Young owned animals can follow and wander. Only adults can be ridden or released, fight or breed. Hatchlings already inherit the egg owner and initially Stay; they do not need taming again. Breaking an egg in Survival returns it, restarting incubation when placed again. Explosions destroy it.

All seven wild land/flying species roam independently on reachable terrain in loaded areas. Wild T-Rex, Raptors and Dilophosaurs also attack Survival players. Owned adults defend you and themselves and attack your opponent. While riding, the attack button plays the species attack, including misses; only an aimed, visible target in range takes damage. Dilophosaurus bites and scratches, and spits black slime at distant targets to briefly blind and slow them. Other species bite, thrust horns or beaks, swing their tails or stomp. **Stay** stops autonomous combat immediately. An authorized mounted driver can still command an attack; dismounting preserves Stay. T-Rex deals twenty instead of twelve base damage before armor and other mitigation. Owners, passengers, tame pets and Creative/Spectator players are protected; attacks on other players require the server's PvP and protection rules to allow them.

## Wild Mosasaurus

The eighth species is a gigantic Mosasaurus for large bodies of water. It stays wild: no taming, riding, leashing or breeding. Its Creative menu egg creates a wild animal in a configured dinosaur world when used in deep, open water where normal placement is allowed. It swims, bites and occasionally breaches the surface when the whole body has room and a safe water landing; only Survival players in water are eligible targets. The other seven species keep their existing features. Its size is deliberately cinematic.

## World encounters and sizes

Natural encounters appear in already loaded Overworld terrain in prototype worlds, independently of old habitats. Species selection uses the biome; Mosasaurus needs deep, unobstructed ocean water. Defaults allow eight wild animals per active Survival player, at most 64 per world and six within a 96-block neighborhood. Every ten seconds, at most two animals per world are created using no more than sixteen placement attempts. Placement checks protection regions and claims and never loads chunks.

Naturally created wild animals are not saved permanently. Distant unnamed animals disappear after a minute without a nearby Survival player; unloaded natural encounters are also temporary. Taming, naming or leashing makes the same animal persistent. Eggs, existing pets and named legacy habitat animals are not replaced. Set `wild-spawning.enabled: false` to disable the new population. Newly generated clearings have no enclosing fences; existing world builds are retained.

Adult model factors increase to 2.1 for Rex, 1.45 for Triceratops, 1.55 for Stegosaurus, 2.15 for Brachiosaurus and 1.15 for Dilo. Raptor, Ptera and Mosa retain their previous sizes. Rex is approximately twelve blocks long and Brachiosaurus approximately 12.5 blocks tall. Rendering, back/seat height and growth share these factors; hatching and new placements check the larger space. Long tails and wings remain partly decorative: the native collision body does not represent every visible part. These are gameplay proportions.

## Operator setup

Fresh setup with module `dino` installs the pinned BetterModel/Geyser bridge stack and all eight model inputs. Use `scripts/setup.py --portal-url https://mc.example.org/` with your reachable root URL: Java downloads `/resources/dinos.zip` from that portal. Default `localhost` is for local tests only. Keep the default `admin` Compose profile, or host `data/plugins/BetterModel/build.zip` yourself and set Paper's `resource-pack` URL. Bedrock receives its generated pack through Geyser. If the portal URL changes later, update Paper's pack URL as well.

`prototype-worlds` limits models, flight, advanced taming, eggs, growth and combat to selected worlds. Existing habitat tags still assign four animals to each original species without replacing native entities, UUIDs, owners or stay states. Dilophosaurus is the seventh species, available through its own persisted species and eggs. Without `prototype-worlds`, the configured `survival-worlds` list is the default. Optional model components failing leaves native gameplay available. Updating an existing installation is separate from fresh setup and needs a planned maintenance window. After changing models, clear the bridge's generated pack cache before regenerating it, since an unchanged model count can otherwise keep an old pack.


Adult wild Pteranodon and owned Free-mode animals fly short routes and land at safe nearby sites. A successful player hit brings a wild adult down for at least sixty seconds; accepted fish feeds keep it grounded during taming. Young animals and Follow mode remain grounded.

Name tags show the saved custom name above the dinosaur, including after renaming or reconnecting. Ground walk/sprint paces vary by species: Brachiosaurus 1.4/3.5, Stegosaurus 1.5/4.5, Triceratops 2/7, T-Rex 2.5/9, Raptor 3/12, Dilophosaurus 2.8/10 and Pteranodon 2/4.5 blocks per second. Pteranodon flies at 12/18 blocks per second. These are gameplay values on open flat ground, not scientific claims; obstacles and effects influence actual motion.
