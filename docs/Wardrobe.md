# Personal wardrobe

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) · [Skin editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor) · [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules)

**ArkWardrobe** is an optional extension of ArkAccounts. Accounts/passwords/profile continue without it; skins and signing controls appear only when it is installed. SkinsRestorer applies prepared signed textures; it does not own your account authority.

Each server-resolved identity has up to **20 private skins**. Names are **1–32 characters**, unique within the library ignoring case. Edit, rename, copy or delete saved designs. Activate another skin before deleting the selected one.

## Save and activate

**Save** stores the design revision; it does not change your appearance. **Activate** asks for that saved revision to be prepared/applied. A queued request is not a completed skin change. The list reports actual preparation/application status. Offline players receive the prepared selection when they next join.

Another browser's newer save produces a revision conflict instead of being silently overwritten. Local unsaved designs survive language/library selection changes, but are not a durable server save; export PNGs or save before closing the browser.

## In-game selection

After the server's normal authentication, use the English commands in either UI language:

```text
/wardrobe
/wardrobe list
/wardrobe wear <name>
```

Names with spaces use the rest of the command. Menus/suggestions contain only your own designs. Prepared skins reuse valid signatures; new unsigned images need the operator's [signing provider](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing). Provider failure preserves stored designs and the prior usable selection.

Use the built-in editor rather than external editor/upload pages. Legacy SkinsRestorer commands are not a separate route into another player's private library. Skins affect appearance only, not inventory, health, experience or separate character saves. Java/Bedrock visibility and client settings require actual-client testing.
