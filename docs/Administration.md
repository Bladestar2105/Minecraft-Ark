# Administration

Das Panel ist optional. Erst das Spiel installieren und die lokalen Geheimnisse
mit `python3 scripts/init.py` erzeugen. Es gibt kein gemeinsames Standardkennwort.
Das erste Kennwort steht privat in `secrets/panel-login.txt`; Benutzer ist `admin`.

Auf Linux benötigt der Panelprozess UID 1000 Schreibrechte auf `panel-state` und
`data`. Die Docker-Socket-Gruppe in `.env` einstellen:

```sh
stat -c %g /var/run/docker.sock
# Ergebnis als DOCKER_SOCKET_GID in .env eintragen.
sudo chown -R 1000:1000 panel-state data
docker compose --profile admin up -d --build
```

[Panel öffnen](http://localhost:8090). Der Port bindet nur an Loopback.
Fernzugriff beispielsweise über einen eigenen SSH-Tunnel:

```sh
ssh -L 8090:127.0.0.1:8090 dein-benutzer@dein-server
```

Dann lokal `http://localhost:8090` öffnen. Bei einem bewusst eingerichteten anderen
Hostnamen `PANEL_TRUSTED_HOSTS` passend setzen. Das Panel besitzt über den Docker-
Socket weitreichende Hostrechte; Zugang wie einen Serveradministrator behandeln.
RCON wird nicht am Host veröffentlicht. Das DNS-Menü benötigt einen separat
betriebenen AdGuard-Dienst; der öffentliche Basisstack enthält diesen nicht.

Das Panel kann Status, Dienstaktionen, Konsole, Einstellungen und Karten anzeigen.
Passwörter im Bereich Konto ändern; die aktuellen Anmeldedaten werden unter
`panel-state` gespeichert, die Bootstrap-Datei bleibt unverändert. Bestehende
Anmeldecookies gelten fest 30 Tage. Nach einer Passwortänderung bleibt der aktuelle
Browser angemeldet; alle anderen bisherigen Anmeldecookies werden ungültig. Persistenten Zustand nach
Updates erhalten und niemals in öffentliche Fehlerberichte hochladen.

## Sichern und aktualisieren

Vor Updates Spieler informieren und die betroffenen Dienste geordnet stoppen.
`data`, `panel-state`, `secrets`, `.env` und Compose gemeinsam privat sichern;
das Archiv enthält Konten, Schlüssel und weitere vertrauliche Daten. Anschließend
wieder starten. Ein öffentlich bereinigter Release ersetzt kein vollständiges Backup.

```sh
docker compose stop
# hier private Sicherung auf ein getrenntes Medium erstellen
docker compose --profile admin up -d
```

Versionen sind bewusst festgelegt. Ein Wechsel der Minecraft-Version braucht vorher
Plugin-/Datapack-Prüfung und eine rückspielbare Sicherung. Dieser öffentliche
Basisstack installiert keinen automatischen Update-Timer. Spieleinstellungen werden
in `data/server.properties` erhalten (`OVERRIDE_SERVER_PROPERTIES=FALSE`). Neue
Passwörter nie in `.env`, Compose, Issues oder Screenshots eintragen.

Vor Freigabe mit frischem Nicht-OP-Konto Portalwechsel, Inventartrennung,
Gebietsschutz, Atlas, Dinozähmung und eine Quest prüfen. Die historischen Prüfungen
der Originalinstanz ersetzen diese Prüfung auf eigener Hardware nicht.
