# Riding your dinosaur

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE) · [Player guide](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing)

ArkDinos 1.1.0 adds six original models and Pteranodon flight. The world archive alone does not include this update or its resource packs. This crossplay prototype still needs visual acceptance on actual Java/Bedrock/console clients.

Tame a special habitat animal with one torchflower seed in Survival. Only its owner can ride it, within the configured Survival worlds. Aim at its torso when taming or mounting; long tails and wings are decorative.

1. Empty your main hand and use/interact with your own unoccupied tamed dinosaur.
2. Use the normal movement controls to steer.
3. Sneak/use the dismount control to get off. When standing beside the pet, sneak+interact still toggles **stay/follow**.

No saddle or additional client mod is needed. Other players cannot take the mount. Wild dinosaurs and Creative players are not eligible. A rider suspends following; the saved stay/follow setting remains available after dismounting.

## Pteranodon flight

Jump takes off and gains height. Movement steers relative to your view; look down while moving forward to descend. Release movement and jump to hover. Descend onto open ground to land. Flight is capped at 48 blocks above takeoff and below the world ceiling. Land before dismounting where possible: an in-air dismount starts the animal's controlled descent and gives temporary slow falling to its rider. The other five species remain ground mounts.

Pets stay in their world and do not load distant chunks or teleport to you. Riding does not protect against falls, lava, drowning or other world hazards. Avoid narrow passages and dangerous terrain. If control stops after leaving Survival or the world, return normally and interact again.

Java and Bedrock use their normal movement/dismount controls. Bedrock/console input and seat appearance still require a real-device acceptance check; server and automated Java-client checks do not establish console behavior.

Both editions require the server resource packs. Automated checks cover all six engine exports, native flight inputs/lifecycle, Geyser rider ordering and interaction IDs. Actual rendering, Bedrock/console controls, camera/seat appearance and distant ridden chunk unloading remain open. Emulated pack acknowledgments are not visual rendering evidence.

## Operator setup

Fresh setup with module `dino` installs the pinned BetterModel/Geyser bridge stack and all six model inputs. Use `scripts/setup.py --portal-url https://mc.example.org/` with your reachable root URL: Java downloads `/resources/dinos.zip` from that portal. Default `localhost` is for local tests only. Keep the default `admin` Compose profile, or host `data/plugins/BetterModel/build.zip` yourself and set Paper's `resource-pack` URL. Bedrock receives its generated pack through Geyser. If the portal URL changes later, update Paper's pack URL as well.

Within model worlds, existing habitat tags assign four animals to each species without replacing native entities, UUIDs, owners or stay states. Without `prototype-worlds`, the configured `survival-worlds` list is the default. Optional model components failing leaves native gameplay available. Updating an existing installation is separate from fresh setup and needs a planned maintenance window. After changing models, clear the bridge's generated pack cache before regenerating it, since an unchanged model count can otherwise keep an old pack.
