# Skin signing: operator setup

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) · [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe)

With optional **ArkWardrobe** installed, open **Administration → Skin signing** and choose **MineSkin** or **Self-hosted generator**. Only authenticated administrators configure signing. The account/profile/password core is ArkAccounts and works without this feature. Neither provider opens an external editor for players.

## MineSkin

Enter your own API key. A saved key is reported as configured, never returned in clear text. Blank keeps it; the explicit clear option removes it. Rate limits/outages apply. There is no automatic provider fallback.

## Self-hosted generator

Configure your own Microsoft public-client application ID and connect your own Minecraft-Java-entitled operator account with the device code shown by the panel. Enter Microsoft credentials only on Microsoft's official page. No shared project account or third-party launcher client ID is supplied.

The full flow must succeed through Microsoft/Xbox authentication, Minecraft profile access and an actual skin upload. **Microsoft sign-in alone does not prove a newly registered application's Minecraft Services access.** Verify the current eligibility requirements and your own account before relying on this mode. Local HTTP/OAuth fixtures do not establish real acceptance.

While signing, the generator changes the connected account's **global Minecraft skin** to obtain Mojang-signed texture properties. Choose an account suitable for that side effect. Tokens stay private on your server; disconnect removes the stored account credentials, and expired/revoked authorization may require reconnecting.

Self-hosting removes reliance on MineSkin for generation, but Mojang still issues valid signatures. Microsoft/Minecraft reachability and limits remain relevant. See [MineSkin's process description](https://docs.mineskin.org/docs/wiki/faq/).

## Queues, failure and backups

Preparing a new image/model is queued. Cached valid signatures can be reused. Changing provider affects future signing work while preserving saved PNGs, selected skins and valid cached signatures; it does not silently send work to another provider. A failure does not turn a queued activation into success.

Back up the private installation together, including `data/plugins/ArkAccounts/`, `data/plugins/ArkWardrobe/`, `data/plugins/SkinsRestorer/`, any AuthMe/Floodgate data, `secrets` and panel state. Account state is not owned by Wardrobe. Never publish keys, device codes, tokens or raw account files in support requests/issues.

After setup, test a new skin and model with a real Java client and the intended Bedrock clients. Fixture checks and cached already-signed skins are not proof of a new live signature or console display.
