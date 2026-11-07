# Verwaltung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE)

Das Webinterface liegt unter `http://localhost:8090`; der veröffentlichte Port bindet an Loopback. Das neue Setup erzeugt einen privaten lokalen Administrator `admin` in `secrets/panel-login.txt`. Es gibt kein gemeinsames Standardpasswort. Die gemeinsame Oberfläche bietet Deutsch und Englisch.

## Start und Zugriff

Das Setup erzeugt `.env` mit aktiviertem Compose-Profil `admin`. Starte nach eigener EULA-/Konfigurationsprüfung ausdrücklich. Richte unter Linux zunächst UID-1000-Zugriff auf `data` und `panel-state` ein und trage die Docker-Socket-Gruppe als `DOCKER_SOCKET_GID` in `.env` ein:

```sh
stat -c %g /var/run/docker.sock
sudo chown -R 1000:1000 panel-state data
docker compose --profile admin up -d --build
```

Halte `secrets/` privat. Nutze für entfernte Verwaltung einen eigenen SSH-Tunnel:

```sh
ssh -L 8090:127.0.0.1:8090 your-user@your-server
```

Öffne lokal `http://localhost:8090`. Bei anderem Hostnamen muss `PANEL_TRUSTED_HOSTS` passen. Schütze das Panel wie eine Hostverwaltung: Der Docker-Socket erlaubt weitreichende Hostaktionen. RCON, BlueMap, ArkAccounts-Port 8101 und Spieler-Port 8091 bleiben intern. Die DNS-Seite benötigt einen getrennt betriebenen AdGuard-Dienst; er gehört nicht zum öffentlichen Basisstack.

## Eine Anmeldung, aktuelle Serverrechte

Alle melden sich unter `/` mit Serverspielername und Serverpasswort oder einem gewöhnlichen Einmalcode aus `/web` nach erfolgter Spielanmeldung an. Bestehende AuthMe-Passwörter funktionieren direkt. Tatsächliche Server-OPs erhalten automatisch zusätzliche Verwaltungsmenüs in derselben Oberfläche; Profil, Garderobe, Konto und Support bleiben persönlich. Es gibt keine gesonderte OP-Anmeldung. `/web admin` ist ein Kompatibilitätsalias für einen gewöhnlichen Code und kein Verwaltungsnachweis.

Jede Verwaltungsanfrage prüft den aktuellen OP-Status über ArkAccounts auf dem Server. `/deop` entzieht nur den Verwaltungszugriff einer ansonsten gültigen Spielersitzung; `/op` stellt ihn ohne Neuanmeldung wieder her. Abgelaufene/widerrufene Sitzungen benötigen eine neue Anmeldung. Bei Ausfall der Kontoverwaltung wird der spielerbasierte Verwaltungszugriff abgewiesen. Ein übergebener Name, Browserfeld oder Online-Status belegt keine OP-Rechte.

Das lokal erzeugte Betreiber-Notfallkonto verwendet dasselbe Formular und bleibt bei ausgefallenem Minecraft/ArkAccounts erreichbar. Es ist ein getrennter Verwaltungszugang und kein gemeinsames Spielerpasswort. Sein Kontodialog ändert nur das lokale Passwort; Spieler einschließlich OPs ändern ihr gemeinsames Passwort unter **Konto**. Lokale Cookies gelten weiterhin fest 30 Tage; der Zugangszustand liegt in `panel-state`. Spielercookies gelten jetzt unter `/`; alte `/play/`-Links leiten zur Hauptadresse weiter und nach dem Update kann einmaliges Neuanmelden nötig sein.

## Konten, Anfragen und Skins

Der Spielerprozess bleibt intern auf Port8091 getrennt, ohne Docker-Socket, RCON-Schlüssel, Weltzugriff oder Admin-Dienstschlüssel. Die gemeinsame Oberfläche unter der Hauptadresse nutzt seine erlaubten persönlichen Routen über `/api/player/*`. **ArkAccounts** verwaltet Spieleridentität, Passwort und Sitzungen; [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) und [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE) sind optionale Erweiterungen. Siehe [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE).

Unter **Spieler** setzt die Verwaltung das gemeinsame Server-/Portalpasswort eines bekannten Kontos ohne altes Passwort zurück. Dabei enden die Websitzungen des Zielkontos; Wiederherstellungscodes und Identitätsverknüpfungen bleiben erhalten. Diese Accounts-Funktion bleibt ohne Support oder Wardrobe verfügbar. [Anleitung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE#ein-bekanntes-serverpasswort-zurücksetzen).

Mit ArkSupport werden Anfragen unter **Support** gelesen, beantwortet, geschlossen und wieder geöffnet. Mit ArkWardrobe wird die [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) eingerichtet. Fehlende optionale Module blenden ihre Bedienung aus; eine fehlgeschlagene Modulabfrage zeigt eine Fehlermeldung. Aktualisiere den Status für einen neuen Versuch.

Setze `portal-url` in `data/plugins/ArkAccounts/config.yml` auf die erreichbare Hauptadresse `/` und starte den betroffenen Server nach Konfigurationsänderungen neu. Signierungszugänge gehören zu Wardrobe, nicht Accounts. Für bestehende gestoppte Installationen ergänzt `python3 scripts/init.py --portal-only` fehlende Portalgeheimnisse, ohne bestehende Zugänge zu ersetzen. Dies ist keine automatische Migration alter Kontodaten.

## Private Backups und Updates

Stoppe betroffene Dienste ordentlich und sichere `data`, `panel-state`, `secrets`, `.env` und Compose gemeinsam auf privaten getrennten Speicher. Beziehe ArkAccounts, ArkWardrobe, ArkSupport, SkinsRestorer und vorhandene AuthMe-/Floodgate-Daten ein. Backups enthalten Passwörter/Hashes, Tokens, Wiederherstellungsprüfwerte, Tickets und Spielerdaten.

Neues Setup und Weltarchiv sind keine Updater. Entpacke keine öffentliche Welt über eine bestehende Installation. Versionen sind festgelegt; prüfe Kompatibilität und Wiederherstellung vor Upgrades. Spieleinstellungen bleiben durch `OVERRIDE_SERVER_PROPERTIES=FALSE` erhalten.

Teste Schutz, Inventartrennung, Atlas, Missionen und Dinos vor der Freigabe mit einem Nicht-OP-Konto. Prüfe außerdem die gewählten optionalen Module. Lokale Testdienste belegen keine echte Signierung und kein Konsolenverhalten; siehe [Fehlerbehebung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting-DE).
