# Administration

[Deutsch](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Security](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security)

The webinterface is at `http://localhost:8090`; its published port binds to loopback. Fresh setup creates a private local administrator account named `admin` in `secrets/panel-login.txt`. There is no shared default password. Both admin and player interfaces offer English and German.

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

## Two explicit administrator sign-ins

- **Local administrator:** use the locally generated panel credentials. Login works when Minecraft/ArkAccounts is unavailable. Change this account's password in the panel account dialog. The current browser stays signed in; previous other cookies are revoked. Local cookies have a fixed 30-day lifetime and persistent credential state in `panel-state`.
- **Registered server operator:** choose **Sign in as a server operator** and enter your known server username/password, or run `/web admin` as an authenticated actual OP and redeem its single-use code. The code lasts up to five minutes. OP sessions last at most eight hours, and every protected request rechecks current OP/account authority. De-opping, expiry or an unavailable account service denies OP access.

A normal `/play/` session never becomes an admin session automatically. Being online, having a matching name or possessing a player cookie is insufficient. OP users change their shared server password through the **Server account** link to `/play/#account`; they cannot use the local admin password dialog to change the separate bootstrap account.

## Accounts, tickets and skins

The isolated player app at `/play/` has separate cookies/secrets and no Docker socket, RCON secret or world mount. **ArkAccounts** owns player identity/password/session data; [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe) and [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) are optional extensions. Read [Modules](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules).

Under **Players**, administrators can reset a known account's shared server/portal password without its old password. The reset revokes target web sessions and retains recovery codes and identity links. This Accounts function remains available without Support or Wardrobe. [Reset instructions](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support#reset-a-known-server-password).

With ArkSupport installed, use **Support** to read, reply, close and reopen tickets. With ArkWardrobe installed, configure [skin signing](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing). Missing optional modules hide their controls; capability failures show an unavailable/retry message. Refresh status to retry.

Set `portal-url` in `data/plugins/ArkAccounts/config.yml` to your reachable `/play/` URL; restart the affected server after configuration changes. Signing credentials belong to Wardrobe, not Accounts. For an existing stopped installation, `python3 scripts/init.py --portal-only` adds missing portal secrets without replacing existing credentials. It is not an automatic legacy account-data migration.

## Private backups and updates

Stop affected services cleanly and back up `data`, `panel-state`, `secrets`, `.env` and Compose together to private separate storage. Include ArkAccounts, ArkWardrobe, ArkSupport, SkinsRestorer and any AuthMe/Floodgate state. Backups contain passwords/hashes, tokens, recovery verifiers, tickets and player data.

Fresh setup and the world archive are not updaters. Never extract a public world over an existing installation. Versions are pinned; test compatibility and a restore before upgrades. Game settings persist through `OVERRIDE_SERVER_PROPERTIES=FALSE`.

Before opening the server, test protection, inventory separation, atlas, quests and dinosaurs using a non-OP account. Also test the selected optional modules. Local fixtures do not establish real signing or console behavior; see [Troubleshooting](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting).
