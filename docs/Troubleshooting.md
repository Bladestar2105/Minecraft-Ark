# Troubleshooting

**Server does not start:** Check EULA acceptance, free disk space and
`docker compose logs minecraft`. Use Java 25 and Minecraft/Paper 26.3. Never write
an older server version over this world. First startup downloads server files and
may take several minutes, especially while legacy item support initializes.

**Build/download failure:** Check access to official sources. Downloads fail if an
exact artifact is unavailable or its hash differs. Do not substitute an unverified
JAR. Parties 3.2.18 uses a version-checked Spiget artifact. If its changing CDN file
no longer matches the pin, obtain the exact version from
[Parties](https://alessiodp.com/parties) and check the same expected hash in
`dependencies-runtime.json`.

**Missing datapack:** Install Dungeons and Taverns 6.1.0 and Geophilic 3.7 with the
installer before first startup. They are not bundled because they retain their own
licenses. Do not ignore missing world-generation data warnings. Keep their exact
filenames: `Dungeons and Taverns v6.1.0.zip` and `Geophilic v3.7.dp.zip`.

**Creative portal/inventory separation is missing:** Close the server to players.
Restore the complete world and Multiverse configuration. RCON `plugins` must show
all three Multiverse extensions enabled. Do not create similarly named replacement
worlds or overwrite inventory groups after players have started using the server.

**Bedrock cannot connect:** Check Geyser/Floodgate, client compatibility, port 19132
and firewall rules. Keep Java online verification enabled. Some consoles lack
arbitrary server entry; follow official Geyser guidance for your own console
solution. Use your own internal or public server address.

**Panel 400/403:** Open `localhost:8090` and check `PANEL_TRUSTED_HOSTS`.
**Panel cannot save/reach Docker:** Check UID-1000 write permissions and the Docker
socket group. `/state` must be writable and persistent.
**DNS page unavailable:** AdGuard is not included in the base stack.
**Browser map is empty:** BlueMap builds the local web map from saved chunks. Check
logs and its internal status for rendering progress.

For an issue, provide versions, the relevant error and reproduction steps. Remove
passwords, keys, account information, player profiles, private addresses and
unrelated private log content before sharing it.
