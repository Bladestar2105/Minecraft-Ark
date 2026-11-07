# Shared password and optional recovery

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) · [Player portal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Administrator support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support)

**ArkAccounts**, independently of Wardrobe or Support, manages the shared **server/portal password**. A change in game or browser updates both; where AuthMe is installed, its password authority is used. Your Microsoft/Xbox password and the separate local panel-admin password are unaffected.

To share game progress between editions, follow [Java and Bedrock account linking](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal#share-one-java-and-bedrock-profile). A shared Microsoft login alone does not link server profiles; arrange any Bedrock progress transfer with the operator before linking.

## Set or change a password

Existing AuthMe users can log into the portal directly with their server name/password. Other authenticated players can use `/web`. In **Account**, enter the current password, a new **12–64-character** password and matching confirmation. Existing shorter passwords can still be verified so you can replace them.

For up to **five minutes after a fresh `/web` game-ownership check**, the form may leave the current password optional. Leaving it empty sets a first password or resets an unknown existing one, including an automatically generated AuthMe password. The interface explains when this is allowed. Ordinary password login does not grant that exception; an entered current password must still verify.

In game, run `/account` without arguments to open private password controls. Do not append passwords to commands or put them in chat. If a client cannot display the private controls, use the portal; real Bedrock/console dialog acceptance still needs testing.

Successful changes revoke web sessions, including operator sessions tied to that account. AuthMe may require a new in-game login. Existing recovery codes and identity links remain intact.

## Optional recovery codes

Enable recovery in **Account** using the current password. Codes are shown **once** and can be copied/downloaded; save them privately outside the account/device being protected. The server stores hashed verification values.

When recovery is already enabled, replacing or disabling it requires the current password **and one existing recovery code**. A fresh game proof does not waive that recovery-settings check. New codes replace the old set.

Choose **Forgot your password?** on the player login screen to use your name, an unused recovery code and a new password/confirmation. Success consumes the code and revokes web sessions; it does not automatically sign you in. Without a configured valid code, ask the server administrator for [a known-account reset](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support#reset-a-known-server-password).

## Current administration rights

All players use the same login at `/`, with the server password or an ordinary `/web` code. Actual registered OPs automatically see [administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) controls; the server rechecks current OP status on each administrative request. `/deop` retains a valid personal session and removes only administration; `/op` restores it without signing in again. `/web admin` is only a legacy alias for an ordinary code. The local `admin` emergency account remains a separate credential in the same form and works while the game server is unavailable.

Never publish codes, hashes, account files, private links or passwords in screenshots or GitHub issues. Recovery cannot restore a compromised Microsoft account or transfer someone else's identity.

A public/global Floodgate link alone does not unlock an existing unbound AuthMe account. Confirm the original Java password or premium ownership first. Verified local mutual linking is retained through the reviewed AuthMe/Floodgate integration; do not replace it with an unchecked global lookup.
