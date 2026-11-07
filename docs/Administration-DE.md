# Verwaltung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE)

Das Webinterface liegt unter `http://localhost:8090`; der veröffentlichte Port bindet an Loopback. Das neue Setup erzeugt einen privaten lokalen Administrator `admin` in `secrets/panel-login.txt`. Es gibt kein gemeinsames Standardpasswort. Verwaltung und Spielerportal bieten Deutsch und Englisch.

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

## Zwei ausdrückliche Verwaltungsanmeldungen

- **Lokaler Administrator:** Verwende die lokal erzeugten Panelzugänge. Die Anmeldung funktioniert auch ohne erreichbaren Minecraft-/ArkAccounts-Dienst. Ändere dieses Passwort im Kontodialog der Verwaltung. Der aktuelle Browser bleibt angemeldet; andere bisherige Cookies werden ungültig. Lokale Cookies gelten fest 30 Tage; die Zugangsdaten bleiben in `panel-state` gespeichert.
- **Registrierter Server-Operator:** Wähle **Als Server-Operator anmelden** und verwende bekannten Servernamen und Serverpasswort. Alternativ führt ein angemeldeter tatsächlicher OP `/web admin` aus und löst den einmaligen Code ein. Der Code gilt höchstens fünf Minuten. OP-Sitzungen gelten maximal acht Stunden; jede geschützte Anfrage prüft die aktuellen OP-/Kontorechte erneut. De-Op, Ablauf oder nicht erreichbare Kontoverwaltung verhindern den OP-Zugriff.

Eine normale `/play/`-Sitzung wird niemals automatisch zur Verwaltungssitzung. Online-Status, gleicher Name oder ein Spielercookie reichen nicht. OP-Benutzer ändern das gemeinsame Serverpasswort über **Serverkonto** unter `/play/#account`; der lokale Adminpasswortdialog steht ihnen nicht zum Ändern des getrennten Bootstrapkontos zur Verfügung.

## Konten, Anfragen und Skins

Die getrennte Spieleranwendung unter `/play/` hat eigene Cookies/Geheimnisse und keinen Docker-Socket, RCON-Schlüssel oder Weltzugriff. **ArkAccounts** verwaltet Spieleridentität, Passwort und Sitzungen; [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) und [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE) sind optionale Erweiterungen. Siehe [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE).

Unter **Spieler** setzt die Verwaltung das gemeinsame Server-/Portalpasswort eines bekannten Kontos ohne altes Passwort zurück. Dabei enden die Websitzungen des Zielkontos; Wiederherstellungscodes und Identitätsverknüpfungen bleiben erhalten. Diese Accounts-Funktion bleibt ohne Support oder Wardrobe verfügbar. [Anleitung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE#ein-bekanntes-serverpasswort-zurücksetzen).

Mit ArkSupport werden Anfragen unter **Support** gelesen, beantwortet, geschlossen und wieder geöffnet. Mit ArkWardrobe wird die [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) eingerichtet. Fehlende optionale Module blenden ihre Bedienung aus; eine fehlgeschlagene Modulabfrage zeigt eine Fehlermeldung. Aktualisiere den Status für einen neuen Versuch.

Setze `portal-url` in `data/plugins/ArkAccounts/config.yml` auf die erreichbare `/play/`-Adresse und starte den betroffenen Server nach Konfigurationsänderungen neu. Signierungszugänge gehören zu Wardrobe, nicht Accounts. Für bestehende gestoppte Installationen ergänzt `python3 scripts/init.py --portal-only` fehlende Portalgeheimnisse, ohne bestehende Zugänge zu ersetzen. Dies ist keine automatische Migration alter Kontodaten.

## Private Backups und Updates

Stoppe betroffene Dienste ordentlich und sichere `data`, `panel-state`, `secrets`, `.env` und Compose gemeinsam auf privaten getrennten Speicher. Beziehe ArkAccounts, ArkWardrobe, ArkSupport, SkinsRestorer und vorhandene AuthMe-/Floodgate-Daten ein. Backups enthalten Passwörter/Hashes, Tokens, Wiederherstellungsprüfwerte, Tickets und Spielerdaten.

Neues Setup und Weltarchiv sind keine Updater. Entpacke keine öffentliche Welt über eine bestehende Installation. Versionen sind festgelegt; prüfe Kompatibilität und Wiederherstellung vor Upgrades. Spieleinstellungen bleiben durch `OVERRIDE_SERVER_PROPERTIES=FALSE` erhalten.

Teste Schutz, Inventartrennung, Atlas, Missionen und Dinos vor der Freigabe mit einem Nicht-OP-Konto. Prüfe außerdem die gewählten optionalen Module. Lokale Testdienste belegen keine echte Signierung und kein Konsolenverhalten; siehe [Fehlerbehebung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting-DE).
