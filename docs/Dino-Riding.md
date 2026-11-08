# Riding your dinosaur

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE) · [Player guide](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing)

ArkDinos 1.1.0 adds eight original models and Pteranodon flight. The world archive alone does not include this update or its resource packs. This crossplay prototype still needs visual acceptance on actual Java/Bedrock/console clients.

Outside prototype worlds, tame a special habitat animal with one torchflower seed in Survival. Prototype worlds use the feeding rules below. Only its owner can ride it, within the configured Survival worlds. Aim at its torso when taming or mounting; long tails and wings are decorative.

1. Empty your main hand and use/interact with your own unoccupied tamed dinosaur.
2. Use the normal movement controls to steer; sprint to run faster.
3. Sneak/use the dismount control to get off. In the prototype test world, sneak+interact on foot with an empty hand opens **Follow**, **Stay**, **Free wander**, and a separately confirmed **Release** action. Free wander retains ownership and defense. Released adults remain peaceful toward their latest former owner. Outside prototype worlds, sneak+interact retains the legacy stay/follow toggle.

No saddle or additional client mod is needed. Other players cannot take the mount. Wild dinosaurs and Creative players are not eligible. A rider suspends following; the saved stay/follow setting remains available after dismounting.

## Pteranodon flight

Jump takes off and gains height. Movement steers relative to your view; look down while moving forward to descend. Release movement and jump to hover. Descend onto open ground to land. Flight is capped at 48 blocks above takeoff and below the world ceiling. Land before dismounting where possible: an in-air dismount starts the animal's controlled descent and gives temporary slow falling to its rider. The other six rideable species remain ground mounts.

Pets stay in their world and do not load distant chunks or teleport to you. Riding does not protect against falls, lava, drowning or other world hazards. Avoid narrow passages and dangerous terrain. If control stops after leaving Survival or the world, return normally and interact again.

Java and Bedrock use their normal movement/dismount controls. Bedrock/console input and seat appearance still require a real-device acceptance check; server and automated Java-client checks do not establish console behavior.

Both editions require the server resource packs. Automated checks cover all eight engine exports, native flight inputs/lifecycle, Geyser rider ordering and interaction IDs. Actual rendering, Bedrock/console controls, camera/seat appearance and distant ridden chunk unloading remain open. Emulated pack acknowledgments are not visual rendering evidence.

## Taming, eggs and combat in prototype worlds

T-Rex, Velociraptor and Dilophosaurus need five raw-meat feeds, ten seconds apart, then a bone. Pteranodon needs five raw-fish feeds, ten seconds apart, then seagrass. Brachiosaurus, Triceratops and Stegosaurus need eight feeds of wheat, fern or dandelion in any combination, fifteen seconds apart, then a torchflower seed. Wrong food and early clicks consume nothing. Visible progress stays reserved for you for five minutes after the latest feed.

Feed two owned adults of the same species within six blocks. A successful pair puts a named species egg into your inventory; overflow drops beside you. Parents then have a five-minute breeding cooldown. In Creative mode, `/dinoeier` opens all eight egg choices and gives a real inventory item. Transferred eggs retain their original owner. Every species has its own inventory image. The seven hatchable species also have different placed shell models over the unchanged native sniffer-egg block.

Place an egg where block placement is allowed. Test-world incubation and growth each take five minutes; normal worlds retain twenty-minute incubation and fifteen-minute growth. A blocked hatch waits for space. Hatchlings start small. Triceratops horns, Stegosaurus plates and tail spikes, Pteranodon crest, and Dilophosaurus crests and frill have hatchling, juvenile and adult detail stages; the juvenile stage starts halfway through growth. Growth food advances these features too. Accepted food removes one minute from the remaining growth time, with thirty seconds between feeds. Young owned animals can follow and wander. Only adults can be ridden or released, fight or breed. Hatchlings already inherit the egg owner and initially Stay; they do not need taming again. Breaking an egg in Survival returns it, restarting incubation when placed again. Explosions destroy it.

All seven wild land/flying species roam independently on reachable terrain in loaded areas. Wild T-Rex, Raptors and Dilophosaurs also attack Survival players. Owned adults defend you and attack your opponent. While riding, swing to attack a visible target in range. Dilophosaurus bites and scratches, and spits black slime at distant targets to briefly blind and slow them. Other species bite, thrust horns or beaks, swing their tails or stomp. **Stay** stops combat immediately. Owners, owned pets and Creative/Spectator players are protected; attacks on other players require the server's PvP and protection rules to allow them.

## Wild Mosasaurus

The eighth species is a gigantic Mosasaurus for large bodies of water. It stays wild: no taming, riding, leashing or breeding. Its Creative menu egg creates a wild animal only in the test world when used in deep, open water. The test basin permits egg use underwater at X128–144, Y48–56, Z−8–8. It swims, bites and occasionally breaches the surface when the whole body has room and a safe water landing; only Survival players in water are eligible targets. The other seven species keep their existing features. Initially, one specimen lives in a separate large test basin beside the arena. Main-world oceans are not automatically populated yet. Its size is deliberately cinematic.

## Operator setup

Fresh setup with module `dino` installs the pinned BetterModel/Geyser bridge stack and all eight model inputs. Use `scripts/setup.py --portal-url https://mc.example.org/` with your reachable root URL: Java downloads `/resources/dinos.zip` from that portal. Default `localhost` is for local tests only. Keep the default `admin` Compose profile, or host `data/plugins/BetterModel/build.zip` yourself and set Paper's `resource-pack` URL. Bedrock receives its generated pack through Geyser. If the portal URL changes later, update Paper's pack URL as well.

`prototype-worlds` limits models, flight, advanced taming, eggs, growth and combat to selected worlds. Existing habitat tags still assign four animals to each original species without replacing native entities, UUIDs, owners or stay states. Dilophosaurus is the seventh species, available through its own persisted species and eggs. Without `prototype-worlds`, the configured `survival-worlds` list is the default. Optional model components failing leaves native gameplay available. Updating an existing installation is separate from fresh setup and needs a planned maintenance window. After changing models, clear the bridge's generated pack cache before regenerating it, since an unchanged model count can otherwise keep an old pack.


Adult wild Pteranodon and owned Free-mode animals fly short routes and land at safe nearby sites. A successful player hit brings a wild adult down for at least sixty seconds; accepted fish feeds keep it grounded during taming. Young animals and Follow mode remain grounded.

Name tags show the saved custom name above the dinosaur, including after renaming or reconnecting. Ground walk/sprint paces vary by species: Brachiosaurus 1/2, Stegosaurus 1/2.8, Triceratops 1.4/4.6, T-Rex 1.3/5, Raptor 2/7, Dilophosaurus 1.8/6 and Pteranodon 1.4/3 blocks per second. These are conservative gameplay approximations; Pteranodon flight remains 7 blocks per second.
