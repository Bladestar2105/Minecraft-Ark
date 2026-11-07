# Modules

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One)

The package contains seven independently built project plugins. The fresh stack installs six by default; **ArkAccessBridge is excluded**.

| CLI name | Plugin | Responsibility | Dependency |
| --- | --- | --- | --- |
| `accounts` | ArkAccounts | Shared passwords, recovery, profile/statistics, shared player login, current OP checks and private API | Core for Wardrobe/Support |
| `wardrobe` | ArkWardrobe | Personal skins, editor storage, activation and signing | ArkAccounts; SkinsRestorer |
| `support` | ArkSupport | Persistent tickets, replies and in-game notifications | ArkAccounts |
| `dino` | ArkDinos | Taming, following and owner-only riding | Separate gameplay plugin |
| `atlas` | ArkAtlas | In-game atlas | Separate gameplay plugin |
| `panel` | ArkPanelBridge | Runtime data and management bridge | Separate administration plugin |
| `access` | ArkAccessBridge | Existing offline/AuthMe integration (1.1.1) | Separate reviewed migration; not fresh-stack default |

ArkAccounts owns account state and the private listener on port 8101. Wardrobe and Support register optional features there; they do not replace account authority or open another public port. With AuthMe installed, its existing password checks remain authoritative. The fresh online-mode stack does not require AuthMe.

## Select modules for a fresh setup

With verified prebuilt JARs, select comma-separated names:

```sh
python3 scripts/setup.py --modules dino,atlas,panel,accounts,support
```

This example keeps Support and omits Wardrobe. `wardrobe` or `support` without `accounts` is rejected. Run setup in a fresh directory before creating `.env`, `data`, `secrets` or `panel-state`. For source builds, run `python3 scripts/build_plugins.py` first. Selection controls installation, not which of the seven JARs the ZIP carries.

The browser reads actual server capabilities. Missing Wardrobe hides skins/signing; missing Support hides tickets. Profile/account and administrator password resets under **Players** belong to Accounts and remain available. A failed capability query shows an unavailable/retry message, rather than claiming a module is installed.

Changing an existing installation needs a stopped server and a private backup. Fresh setup is not an updater. Keep each module's existing data; removing a JAR does not authorize deleting accounts, skins or tickets. See [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security), [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security), [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe) and [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support).
