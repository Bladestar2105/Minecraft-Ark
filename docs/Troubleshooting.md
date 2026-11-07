# Troubleshooting

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting-DE) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Administration](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration)

**Fresh setup refuses `.env`, `data`, `secrets` or `panel-state`:** it deliberately does not overwrite an installation. Use a new directory. Let setup generate `.env`/secrets, then edit them. Back up existing servers privately; do not delete their state to make setup pass.

**World download/checksum failure:** use the fixed [world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download) parts and `world-release.json`, not the latest application release. Missing/changed hashes stop installation. Do not bypass verification or extract partial downloads.

**Build failure:** source builds require JDK 25. Binary All-in-One packages already contain project JARs and need no host JDK. Check connectivity to the exact pinned publisher artifacts; never substitute an unverified JAR. If a pinned third-party download changes, report it with the dependency name/version/hash.

**Server will not start:** check EULA acceptance, disk space and `docker compose logs minecraft`. Use the pinned Minecraft/Paper 26.3 and Java 25 container. Do not downgrade the world. Initial dependency downloads can take several minutes.

**Missing datapacks or creative separation:** keep the server closed. Check installed Dungeons and Taverns/Geophilic packs, all three Multiverse extensions and the complete world/dimension tree. Restore a private backup rather than creating lookalike worlds or overwriting inventory groups.

**Player login rejected:** existing AuthMe users use their existing server name/password directly. For auto-authenticated players, run `/web` after game login. Codes are short-lived; redeem them in the shared root login. `/web admin` is a legacy alias for an ordinary code, not a separate OP login. Unknown/unresolved accounts cannot be created just by entering a name. See [Account security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security).

**Administration menus disappear:** current server OP rights, account authority and session validity are checked. `/deop` removes only administrative access while retaining a valid personal session; `/op` restores administration without another login. Account-service failure denies player-based administration. The local emergency admin uses the same root form for repairs. An upgrade may require one new login because player cookies now use path `/`.

**Skins or Support absent:** inspect actual installed [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules). Accounts-only still supports profile/account and admin password resets. A failed module query is an availability error: retry through status/profile refresh. For activation problems read [Skin signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing); a queued job is not a completed skin change.

**HTTP429:** respect `Retry-After` and stop automatic retry loops. API budgets are per client IP and service process, with additional native session/UUID/write limits. Behind a proxy, verify its exact `FORWARDED_ALLOW_IPS` entry before trusting client IP headers. Read [Request budgets](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security#request-budgets).

**Support conflict/closed/limit:** refresh and read newer replies before retrying. Draft text is preserved on conflict. Closed tickets require administrator reopening; cooldown, open-ticket, message and storage bounds are intentional. See [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support).

**Bedrock connection/control problems:** check Geyser/Floodgate, port 19132, firewall and client compatibility. Keep Java online verification enabled. Some consoles need a separate connection method; no public DNS/AdGuard service is bundled. Actual console movement, dialogs and skin visibility require device testing.

**Panel 400/403 or cannot save:** check `PANEL_TRUSTED_HOSTS`, current server OP rights, UID-1000 access to `data`/`panel-state` and Docker socket group. DNS unavailable is expected without separate AdGuard. BlueMap rebuilds its cache from saved chunks; check render progress.

Provide versions, the relevant error and reproduction steps in issues. Remove passwords, tokens, recovery codes, player profiles, private addresses and unrelated private logs. [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security) explains backup/release boundaries.
