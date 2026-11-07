# Player area

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE) · [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules)

Open the server's webinterface at its root address `/`. Everyone uses the same sign-in form and English/German interface: profile, wardrobe, account and support belong to your own player account. **ArkAccounts** supplies login, account settings, profile and statistics; skins and tickets appear only when their optional modules are installed. Old `/play/` links redirect to `/`; no second portal or OP login is needed.

## Find the portal address in game

A floating notice at the main spawn shows the portal address and `/web`. A chat notice with the same URL also appears every five minutes by default. Java supports clicking the chat link; Bedrock players can use the written address. Operators can disable notices or change the interval. After game authentication, `/web` still supplies your private single-use code and the address; do not share that code.

The address belongs to this server. Operators configure it under [Portal address and in-game notices](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration#portal-address-and-in-game-notices).

## Sign in

Existing AuthMe users sign in directly with their known server player name and AuthMe password. No earlier `/web` pairing is required. The password is shared with the game-side account system, not with Microsoft.

Automatically authenticated Java/Bedrock players without a known server password can use a code; any authenticated player may use it as an alternative:

1. Join and complete the server's normal authentication.
2. Run `/web` privately in game.
3. Redeem the single-use code in the root sign-in form within five minutes. Unused codes expire on a server restart.
4. Open **Account** to set a password if wanted. A fresh game confirmation permits an empty current-password field for up to five minutes; see [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security).

Names alone never create or take over accounts. The server resolves ownership from the actual game identity. Existing Floodgate links remain authoritative; linking does not automatically merge libraries or inventories. Reauthenticate after link changes.

## Share one Java and Bedrock profile

Using the same Microsoft account for both editions does **not** automatically give both connections the same server player ID. Use Floodgate's local account linking to prove control of both game accounts; no Microsoft password or email address belongs in the portal or a ticket.

Before linking, tell the operator which existing progress you want to keep. Linking normally selects the **Java profile**, including its inventory and location. A separate Bedrock profile is not merged or deleted. If you want to keep Bedrock progress, ask the operator for a backed-up transfer **before** linking. Survival and creative inventory groups remain separate.

1. Join with Java and finish the server's authentication; join with your own Bedrock account too.
2. On Java, run `/linkaccount Bedrockname` and read the private code and instructions. Use the Bedrock/Xbox name requested by the command.
3. On Bedrock, run `/linkaccount Javaname code` within five minutes, or the operator's configured timeout.
4. Disconnect Java and reconnect Bedrock. Check inventory, ender chest, experience and location in each relevant world; sign in to the portal again.

After linking, both editions use the Java identity and cannot play simultaneously as that identity. `/unlinkaccount` removes this server's local link; it does not combine profiles or remove an independently configured global link. [Official Floodgate linking guide](https://geysermc.org/wiki/floodgate/linking/).

### Enable local linking as an operator

Fresh setup downloads the pinned SQLite linking extension into `data/plugins/floodgate/` and enables local linking. Linking is optional: Bedrock players can still join without a Java account. The package contains no existing links, private keys or player data; Floodgate creates these privately on first startup.

Existing installations are never overwritten by the fresh installer. To enable local linking there, download the SQLite extension through the [official guide](https://geysermc.org/wiki/floodgate/linking/#local-linking), place `floodgate-sqlite-database.jar` inside `data/plugins/floodgate/` rather than the top-level plugins directory, and review this section in `data/plugins/floodgate/config.yml`:

```yaml
player-link:
  enabled: true
  enable-own-linking: true
  allowed: true
  link-code-timeout: 300
  type: sqlite
```

Keep unrelated existing settings, including your chosen global-linking policy. Back up accounts and player/plugin data consistently while Minecraft is stopped, then restart and test mutual linking with accounts you own. Local links take precedence over global links. Existing AuthMe/offline installations also need their reviewed identity protection; a global lookup or matching name must never replace proof of ownership.

Transferring an established Bedrock profile requires a separate operator migration while both accounts are offline. Back up both profiles and relevant plugin databases first, resolve conflicting Java progress deliberately, and include per-world inventories and plugin-owned progress rather than copying only one player file. Do not publish account mappings or manually insert an unverified link.

## Your data and optional features

The profile shows your identity, online status and available cumulative play time, deaths, mob kills and player kills with the observation time. Unavailable values are shown as unavailable, not invented or split into a per-world history.

- **Account:** shared password changes and optional recovery codes, available without Wardrobe/Support.
- **Wardrobe:** your skins and built-in [editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor), if ArkWardrobe is installed. [Save/activate instructions](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe).
- **Support:** your persistent private requests and replies, if ArkSupport is installed. [Limits and commands](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support).

A module-query failure displays an unavailable/retry message; refresh the profile to retry. It does not claim the optional features were installed successfully. Signing outages do not delete saved PNGs. Applying a new unsigned skin needs the operator's [signing provider](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing).

## Account and session safety

Changing the shared password or recovering/resetting the account revokes existing web sessions. Recovery does not log you in automatically. `/web logout` signs out your portal sessions. Save recovery codes privately and never include them in tickets or screenshots.

Actual server OPs automatically see the additional [administration menus](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration#one-sign-in-current-server-permissions) after the same login. Every administrative request checks current server-side OP authority; a browser flag cannot grant it. `/deop` removes administrative access while retaining a valid personal session; `/op` restores access without another login. `/web admin` is only a legacy alias for the ordinary `/web` code and grants no extra rights. Cookies now apply at `/`; an upgrade may require signing in once again.

A public/global Floodgate link alone does not unlock an existing unbound AuthMe account. Confirm the original Java password or premium ownership first. Verified local mutual linking is retained through the reviewed AuthMe/Floodgate integration; do not replace it with an unchecked global lookup.
