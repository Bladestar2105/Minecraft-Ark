# Security and private state

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) · [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security)

The local panel administrator can control Docker and server configuration. Keep the panel on loopback or behind your own protected access path; remote browser access needs HTTPS or a private tunnel. Publish only the game ports you need. Never expose RCON, the private Accounts API or the isolated player service directly.

## Keep the roles separate

ArkAccounts owns player authentication and actual OP checks. Player and administrator services use different internal tokens, cookies and CSRF/origin checks. A player cookie cannot grant administrator privileges. OP login is explicit and current OP status is rechecked for every protected request; loss of OP status or account-service reachability denies it. Local panel login remains separate and usable without Minecraft.

Existing AuthMe passwords remain authoritative where AuthMe is installed. The public default uses online Java verification and Floodgate; ArkAccessBridge is an optional existing-server integration, not a reason to disable online verification. Never merge identities based on a supplied name, UUID or IP address.

## Passwords, codes and support

Use [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) for shared server/portal passwords and optional one-use recovery codes. `/account` opens private password controls; do not append secrets as command arguments or put them in chat. `/web` and `/web admin` codes are short-lived credentials for different sign-in areas.

An administrator may reset a known player password under **Players**, revoking target web sessions while retaining recovery codes/links. It does not reset Microsoft credentials. [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) conversations are private records accessible to their owner and administrators; do not paste passwords, tokens or account-recovery codes into them.

## Backups and release hygiene

Back up the entire private installation together: `data`, `panel-state`, `secrets`, `.env` and Compose. Accounts state belongs to `data/plugins/ArkAccounts/`; skins/signing to ArkWardrobe/SkinsRestorer; tickets to ArkSupport. Preserve any AuthMe/Floodgate data and world identities. Keep backups access-controlled and test restoring them.

The public package contains reviewed code/configuration and project binaries, not private state. Exclude account/session databases, signing credentials, player inventories, private claims, logs, map caches and local secrets from public releases or issue reports. Generated credentials are local; no project's operator account or shared API key is supplied.

Fixture tests check boundaries but do not establish live OAuth authorization, client skin rendering or console controls. See [Skin signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing), [Dino riding](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding) and [Development](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development) for the remaining acceptance checks.

A public/global Floodgate link alone does not unlock an existing unbound AuthMe account. Confirm the original Java password or premium ownership first. Verified local mutual linking is retained through the reviewed AuthMe/Floodgate integration; do not replace it with an unchecked global lookup.
