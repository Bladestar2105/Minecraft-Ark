# Administration

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security)

The webinterface is at `http://localhost:8090`; its published port binds to loopback. Fresh setup creates a private local administrator account named `admin` in `secrets/panel-login.txt`. There is no shared default password. The shared interface offers English and German.

## Start and access

Setup creates `.env` with the `admin` Compose profile enabled. After your EULA/configuration review, start explicitly. On Linux, first arrange UID-1000 access to `data` and `panel-state`, and set `DOCKER_SOCKET_GID` in `.env` to the Docker socket's group:

```sh
stat -c %g /var/run/docker.sock
sudo chown -R 1000:1000 panel-state data
docker compose --profile admin up -d --build
```

Keep `secrets/` private. For remote administration, use your own SSH tunnel:

```sh
ssh -L 8090:127.0.0.1:8090 your-user@your-server
```

Open `http://localhost:8090` locally. If using another hostname, configure `PANEL_TRUSTED_HOSTS`. Protect the panel as host administration: its Docker socket permits extensive host control. RCON, BlueMap, ArkAccounts port 8101 and the player app port 8091 stay internal. The DNS page needs a separately operated AdGuard service; the public base stack does not include it.

## One sign-in, current server permissions

Everyone signs in at `/` with the server player name/password, or a normal single-use `/web` code obtained after game authentication. Existing AuthMe passwords work directly. Actual server OPs automatically receive the additional administration menus in the same interface; their profile, wardrobe, account and support remain personal. There is no separate OP sign-in. `/web admin` is a compatibility alias for an ordinary code, not an administrator credential.

Every administrative request checks current OP status with ArkAccounts on the server. `/deop` removes only administrative access from an otherwise valid player session; `/op` restores it without another login. Expired/revoked sessions require authentication again. An unavailable account service denies player-based administrative access. Never infer OP rights from a supplied name, browser field or being online.

The locally generated operator bootstrap account uses the same form and remains available when Minecraft/ArkAccounts is down. It is a separate emergency administrator credential, not a player's shared server password. Its account dialog changes only that local password; players including OPs change their shared password under **Account**. Local cookies retain the fixed 30-day lifetime and persistent credential state in `panel-state`. Player cookies now use path `/`; old `/play/` links redirect to the root and an upgrade may require one new login.

## Accounts, tickets and skins

The player service remains isolated internally on port8091, without a Docker socket, RCON secret, world mount or admin service key. The shared root interface calls its allowed personal routes through `/api/player/*`. **ArkAccounts** owns player identity/password/session data; [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe) and [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) are optional extensions. Read [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules).

Under **Players**, administrators can reset a known account's shared server/portal password without its old password. The reset revokes target web sessions and retains recovery codes and identity links. This Accounts function remains available without Support or Wardrobe. [Reset instructions](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support#reset-a-known-server-password).

With ArkSupport installed, use **Support** to read, reply, close and reopen tickets. Administrators can also [permanently delete closed tickets](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support#delete-old-tickets) with ArkSupport 1.0.1 and the matching web update. With ArkWardrobe installed, configure [skin signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing). Missing optional modules hide their controls; capability failures show an unavailable/retry message. Refresh status to retry.

## Portal address and in-game notices

The shared address for `/web`, the main-spawn display and regular chat notices is configured in `data/plugins/ArkAccounts/config.yml`. Set up reachable web access first; entering a URL does not create DNS, HTTPS or port forwarding.

1. Sign in with administrative rights and stop Minecraft cleanly through the web interface. Use the local owner account if necessary; it remains accessible when the game is stopped.
2. Open **More settings → Player portal (ArkAccounts)**.
3. Set `portal-url` to the full root address including `https://`, for example `https://portal.example.org/`. Do not use the old `/play/` path. The address must not contain credentials or a fragment.
4. Set `portal-announcement-seconds`: `300` means five minutes, `0` disables chat notices; other permitted values are `30` through `86400` seconds.
5. `portal-spawn-display: true` enables the main-spawn notice; `false` disables it. The display changes no buildings or blocks.
6. Save and start Minecraft again. Check the address at spawn and using `/web` in game.

```yaml
portal-url: https://portal.example.org/
portal-announcement-seconds: 300
portal-spawn-display: true
```

Network binding, internal ports and secret token paths are protected in the editor. A new installation can initialize the address with `python3 scripts/setup.py --portal-url https://portal.example.org/`. Edit the existing configuration on established installations; setup does not overwrite it. If you set the `ARK_ACCOUNTS_PLAYER_URL` environment variable yourself, it takes precedence: remove or change it as well, or the file's URL change will not take effect.

Signing credentials belong to Wardrobe, not Accounts. For an existing stopped installation, `python3 scripts/init.py --portal-only` adds missing portal secrets without replacing existing credentials. This is not an automatic migration of old account data.

## Browser maps and dimensions

Under **Map**, use the world selector inside BlueMap to switch between the configured survival, creative, Nether and End maps. The selector lists the maps actually loaded by BlueMap. A map only renders chunks already generated and saved by Minecraft; rendering does not generate or pre-generate a dimension. Unvisited areas can therefore remain blank.

If Nether or End is missing from the selector, check `data/plugins/BlueMap/maps/`. An existing directory with only two manually maintained map files does not gain every other dimension automatically. Add explicit map configs with unique IDs and the correct save path/dimension, following the [BlueMap map-config guide](https://bluemap.bluecolored.de/wiki/configs/Maps.html). In the prepared native world, both use `world: "ark"`, with `dimension: "minecraft:the_nether"` or `"minecraft:the_end"`; their map IDs are `ark_nether` and `ark_end`. Do not copy the overworld's surface bounds or cave-light settings unchanged into a Nether map.

After backing up and saving the configs, an administrator can run `/bluemap reload light`, then check `/bluemap maps` and refresh the browser map. `/bluemap update ark_nether` and `/bluemap update ark_end` schedule updates of saved chunks. If rendering was intentionally stopped, check why before resuming it with `/bluemap start`. These [BlueMap commands](https://bluemap.bluecolored.de/wiki/getting-started/Commands.html) need the corresponding permissions; in the panel console, omit the leading `/`. Fresh setup verifies and installs the fixed world snapshot; it does not repair BlueMap settings in an existing server.

## Private backups and updates

Stop affected services cleanly and back up `data`, `panel-state`, `secrets`, `.env` and Compose together to private separate storage. Include ArkAccounts, ArkWardrobe, ArkSupport, SkinsRestorer and any AuthMe/Floodgate state. Backups contain passwords/hashes, tokens, recovery verifiers, tickets and player data.

Fresh setup and the world archive are not updaters. Never extract a public world over an existing installation. Versions are pinned; test compatibility and a restore before upgrades. Game settings persist through `OVERRIDE_SERVER_PROPERTIES=FALSE`.

Before opening the server, test protection, inventory separation, atlas, quests and dinosaurs using a non-OP account. Also test the selected optional modules. Local fixtures do not establish real signing or console behavior; see [Troubleshooting](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting).
