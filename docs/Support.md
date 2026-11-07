# Support and administrator password resets

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE) · [Player portal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration)

**ArkSupport** adds persistent private requests. Your server must install it alongside ArkAccounts. The browser hides ticket controls when Support is absent. Administrator password resets are a separate **ArkAccounts** function under **Players**, available without Support or Wardrobe.

## Ask for help

In the player portal, open **Support**, choose a priority and enter your message. You can also create a request after signing in to the game:

```text
/support [low|normal|high] Message
/support high I cannot use the creative portal
```

Priority defaults to `normal`. Messages contain 1–2000 characters; browser messages may include line breaks. Do not include passwords, login/recovery codes or signing credentials. You can read and reply only to your own requests. A closed request is read-only until an administrator reopens it.

Online authenticated operators receive a private activity notification. Replies notify the relevant online player/operators; notifications contain a reference rather than the full private conversation. Browser lists refresh while the page is visible and the session is active. No email or external ticket service is involved.

## Handle requests as an administrator

Sign in through [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration), open **Support**, select a request, reply, then close or reopen it. The list shows owner, priority, status and time; details show the saved conversation. Another writer's newer revision causes a conflict rather than overwriting it. Refresh/read the conversation before retrying; unsent reply text is preserved.

There is a 60-second creation cooldown, up to five open requests per player and up to 50 messages per request. Lists show the latest 100 requests. Storage is bounded to 1,000 tickets and 16 MiB; exhaustion rejects further writes with a visible error and does not silently delete old records. Back up `data/plugins/ArkSupport/` privately.

## Reset a known server password

In **Administration → Players**, choose or enter the known server account, then enter a new password and matching confirmation, **12–64 characters**. An authenticated administrator does not need the player's previous password. Unknown accounts fail; this form does not register arbitrary browser accounts.

The reset updates the same game/portal password, revokes the target's web sessions and retains recovery codes and identity links. With AuthMe, the change goes through its authority and a new game login may be required. It never changes Microsoft credentials or the separate local admin password. The audit records action, target and time without the password. See [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) and [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security).
