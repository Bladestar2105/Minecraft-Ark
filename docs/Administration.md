# Administration

The panel is optional. First install the game and generate local secrets with
`python3 scripts/init.py`. There is no shared default password. The initial
password is stored privately in `secrets/panel-login.txt`; the username is `admin`.
The current panel interface is primarily German.

On Linux, the panel process (UID 1000) needs write access to `panel-state` and
`data`. Configure its Docker socket group in `.env`:

```sh
stat -c %g /var/run/docker.sock
# Put the resulting group ID in DOCKER_SOCKET_GID in .env.
sudo chown -R 1000:1000 panel-state data
docker compose --profile admin up -d --build
```

[Open the panel](http://localhost:8090). Its published port binds only to loopback.
For remote access, use your own SSH tunnel, for example:

```sh
ssh -L 8090:127.0.0.1:8090 your-user@your-server
```

Then open `http://localhost:8090` locally. If you intentionally use another
hostname, set `PANEL_TRUSTED_HOSTS` accordingly. Docker socket access gives the
panel extensive host privileges; protect its account like a server administrator.
RCON is not published on the host. The DNS menu requires a separately operated
AdGuard service; it is not included in the public base stack.

The panel provides status, service actions, console, settings and maps. Change
the password in its account area. Current credentials persist in `panel-state`;
the bootstrap file stays unchanged. Login cookies have a fixed **30-day lifetime**.
After a password change, the current browser remains signed in and all other
previous login cookies are revoked. Preserve this state across updates and never
attach it to public issue reports.

## Backups and updates

Before updates, inform players and stop affected services cleanly. Back up
`data`, `panel-state`, `secrets`, `.env` and Compose together, privately, to separate
storage. Backups contain accounts, keys and other confidential data. A sanitized
public release is not a complete private backup.

```sh
docker compose stop
# Create a private backup on separate storage here.
docker compose --profile admin up -d
```

Versions are intentionally pinned. A Minecraft version upgrade needs plugin/
datapack compatibility checks and a restorable backup first. This public base
stack does not install an automatic update timer. Game settings persist in
`data/server.properties` through `OVERRIDE_SERVER_PROPERTIES=FALSE`. Never put
passwords in `.env`, Compose, issues or screenshots.

Before inviting players, use a fresh non-operator account to check portals,
inventory separation, protection, atlas, dinosaur taming and one quest. Historical
checks on the original server do not replace verification on your own hardware.
