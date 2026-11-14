# Player guide

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing-DE) · [Player portal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Dino riding](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding)

Connect Java 26.3 to your own server address on port 25565, or Bedrock on port
19132. PS5/Switch may require a separate supported connection solution when direct
server entry is unavailable. The template does not include public DNS. Not every
console connection path has been independently tested.

**The webinterface offers English and German; existing adventure text and quest names remain mainly German.** Portal/module command names remain English in both interface languages. Existing world/datapack identifiers below are kept as implemented. New players start at the protected harbor at
**−479.5 / 114 / −346.5**. Survival covers 20,480 × 20,480 blocks. Items drop on
death. No client mods or additional resource packs are required.

## Atlas, villages and adventures

An ARK atlas is issued on the first survival visit. Use `/atlas` with a free
inventory slot for a replacement. Use/right-click cycles world, region and local
views. It contains 55 locations; Bedrock does not show location names at markers.

There are 16 villages with 64 houses: nine support normal pillager raids and seven
remain safe. Villagers can die in raid villages. Raids do not run automatically or
endlessly. There are also eight trader camps and six each of witch camps, pirate
camps and boss arenas. The beginner area around spawn protects against hostile
creatures; combat locations are farther away.

## Leaderboard

A floating, colored top-ten adventure-points leaderboard sits at the Survival
spawn. It refreshes every five seconds and includes offline players. Everyone
can use **`/leaderboard`** or **`/rangliste`** in any world to see the top ten
and their own position in chat. **`/trigger rang`** still shows personal rank
progress and points needed for the next rank.

## Dinosaurs

ArkDinos 1.2.1 includes eight original species: T-Rex, Triceratops, Stegosaurus, Brachiosaurus, Velociraptor, Pteranodon, Dilophosaurus and wild Mosasaurus. Accept the server resource packs on Java and Bedrock. Wild animals can appear throughout suitable loaded terrain in configured dinosaur worlds and wander without a fixed home territory. Fences already present in older worlds remain physical obstacles.

Taming in dinosaur worlds requires species food and a finishing item. **T-Rex needs twelve raw-meat feeds, twenty seconds apart, then a bone**; Raptor and Dilo need five meat feeds, ten seconds apart, then a bone. See [all food recipes](Dino-Riding.md) for fish eaters and herbivores. The legacy single-seed system only applies outside prototype worlds.

**Empty main hand + use** mounts an available adult. For its owner, **sneak + use** opens Follow, Stay, Free wander, riding permission and separately confirmed Release. Pets start private; “Reiten: öffentlich” allows guests to ride without transferring ownership or care rights. There is still one seat. Sprint accelerates and sneak dismounts. Pteranodon takes off with jump and flies at twelve blocks per second, or eighteen while sprinting.

Land dinosaurs rise to an open water surface while ridden, in Stay and with an offline owner. Wild predators hunt Survival players. Owned adults defend their owner and themselves; Stay pauses automatic combat. The attack button commands an aimed mounted attack. Owners and tame pets are protected, and server protection rules apply to other players. Animals neither load distant chunks nor teleport after you. See [riding details and limits](Dino-Riding.md).

## Claims, quests and groups

Use a golden shovel to select opposite corners of a claim. A stick shows existing
claims. Manage your land with `/claimslist`, `/trust Name`, `/untrust Name`,
`/trustlist` and `/abandonclaim`. Starting budget: 1,000 blocks, increasing by 100
per active hour up to 10,000. Public structures have separate protection.

`/quests list` shows available quests. The numbers are list positions, not quest IDs: use the **display name**, such as `/quests take Nachtwache`, rather than `/quests take 7`. Nachtwache still requires Material fürs Lager. Example: `/quests take Werkzeugmacher`, then
confirm with `Ja` (German for yes). Parties: `/party create Name`, `/party invite Name`,
`/party accept`. Every member must accept the same quest individually. Creative
actions do not count. Parties must be installed successfully for group commands.

`/trigger rang` shows adventure points. Quests, discoveries and first boss wins
count once. German rank names are Neuankömmling, Kundschafter, Abenteurer, Veteran
and ARK-Legende. Ranks do not grant combat-stat bonuses.

## Creative world

The spawn portal leads to the separate flat creative world with a return portal.
Inventory, armor, offhand, ender chest and experience stay in their world group.
Vehicles, creatures and dropped items cannot use these portals. The arrival
pavilion is protected; building is allowed outside it.

## Portal, account and optional modules

- `/web`: a one-time code for the shared root login; existing AuthMe users can also sign in directly with their server password.
- `/account`: private shared-password controls, without password arguments.
- `/web admin`: legacy alias for an ordinary `/web` code; current server OP rights control administration.
- `/wardrobe list` and `/wardrobe wear <name>`: your skins, with ArkWardrobe.
- `/support [low|normal|high] Message`: a private request, with ArkSupport.

Read [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security), [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe), [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) and [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) for permissions and limits. Optional modules do not own your core account/password/profile.

### Heal and find your own dinosaurs

In Survival, feed an injured owned animal its normal primary food to restore up to two hearts every two seconds before growth or breeding. `/dinos` lists only your tamed animals. Hold a normal compass in either hand and click **[Kompass]** to select one. It follows loaded animals and otherwise points to the last recorded position. Travel to the target world and select it again for cross-world targets. Animals already unloaded before this update are first indexed when their area next loads.

T-Rex hits deal five hearts on Normal before armor. Accepted taming food protects only the feeding player while progress remains valid; their positive melee or arrow hit clears that progress and protection. The recipe remains twelve feeds twenty seconds apart and one bone. An owned adult Pteranodon in Follow can accompany its owner flying another owned Pteranodon.

Wild Pteranodons make flights between clear, loaded perches. Hold raw cod, salmon or tropical fish in either hand to lure a nearby animal in sight to land. They remain peaceful until actually hurt, then land and retaliate against the attacker on the ground. A hit temporarily keeps them grounded.
