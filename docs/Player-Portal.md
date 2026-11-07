# Player portal

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE) · [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules)

Open the server's webinterface and follow **Player portal**, or append `/play/` to its address. Choose English or German. **ArkAccounts** supplies login, account settings, profile and statistics; skins and tickets appear only when their optional modules are installed.

## Sign in

Existing AuthMe users sign in directly with their known server player name and AuthMe password. No earlier `/web` pairing is required. The password is shared with the game-side account system, not with Microsoft.

Automatically authenticated Java/Bedrock players without a known server password can use a code; any authenticated player may use it as an alternative:

1. Join and complete the server's normal authentication.
2. Run `/web` privately in game.
3. Redeem the single-use code on the player login page within five minutes. Unused codes expire on a server restart.
4. Open **Account** to set a password if wanted. A fresh game confirmation permits an empty current-password field for up to five minutes; see [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security).

Names alone never create or take over accounts. The server resolves ownership from the actual game identity. Existing Floodgate links remain authoritative; linking does not automatically merge libraries or inventories. Reauthenticate after link changes.

## Your data and optional features

The profile shows your identity, online status and available cumulative play time, deaths, mob kills and player kills with the observation time. Unavailable values are shown as unavailable, not invented or split into a per-world history.

- **Account:** shared password changes and optional recovery codes, available without Wardrobe/Support.
- **Wardrobe:** your skins and built-in [editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor), if ArkWardrobe is installed. [Save/activate instructions](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe).
- **Support:** your persistent private requests and replies, if ArkSupport is installed. [Limits and commands](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support).

A module-query failure displays an unavailable/retry message; refresh the profile to retry. It does not claim the optional features were installed successfully. Signing outages do not delete saved PNGs. Applying a new unsigned skin needs the operator's [signing provider](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing).

## Account and session safety

Changing the shared password or recovering/resetting the account revokes existing web sessions. Recovery does not log you in automatically. `/web logout` signs out your portal sessions. Save recovery codes privately and never include them in tickets or screenshots.

A player session has no Docker, console or administrator rights. Registered actual OPs use a separate explicit [administration login](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration#two-explicit-administrator-sign-ins), with their server password or a distinct `/web admin` code. A player cookie is never automatically upgraded.

A public/global Floodgate link alone does not unlock an existing unbound AuthMe account. Confirm the original Java password or premium ownership first. Verified local mutual linking is retained through the reviewed AuthMe/Floodgate integration; do not replace it with an unchecked global lookup.
