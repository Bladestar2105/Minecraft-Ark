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

## Frühere Spieler zur Whitelist hinzufügen

Unter **Spielerverwaltung → Bisher verbundene Spieler** stehen auch frühere, aktuell abgemeldete Spieler. **Zur Whitelist hinzufügen** übernimmt den gewählten Namen direkt; der Status wird anschließend vom Server neu geladen. Bedrock-Namen behalten ihren führenden Punkt. Das Hinzufügen aktiviert die Whitelist nicht automatisch.

Die Liste verwendet erfolgreiche Beitritte aus erhaltenen Serverlogs einschließlich komprimierter Archive sowie zwischengespeicherte Namen mit gespeicherten Spielerdaten. Ein Name allein im Namenscache oder ein abgewiesener Verbindungsversuch gilt nicht als Beitritt. Gelöschte Historie kann fehlen; unlesbare Dateien erzeugen einen Hinweis.

## Konten, Anfragen und Skins

Der Spielerprozess bleibt intern auf Port8091 getrennt, ohne Docker-Socket, RCON-Schlüssel, Weltzugriff oder Admin-Dienstschlüssel. Die gemeinsame Oberfläche unter der Hauptadresse nutzt seine erlaubten persönlichen Routen über `/api/player/*`. **ArkAccounts** verwaltet Spieleridentität, Passwort und Sitzungen; [Wardrobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) und [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE) sind optionale Erweiterungen. Siehe [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE).

Unter **Spieler** setzt die Verwaltung das gemeinsame Server-/Portalpasswort eines bekannten Kontos ohne altes Passwort zurück. Dabei enden die Websitzungen des Zielkontos; Wiederherstellungscodes und Identitätsverknüpfungen bleiben erhalten. Diese Accounts-Funktion bleibt ohne Support oder Wardrobe verfügbar. [Anleitung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE#ein-bekanntes-serverpasswort-zurücksetzen).

Mit ArkSupport werden Anfragen unter **Support** gelesen, beantwortet, geschlossen und wieder geöffnet. Mit ArkSupport 1.0.1 und der passenden Web-Aktualisierung können Administratoren außerdem [geschlossene Tickets endgültig löschen](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE#alte-tickets-löschen). Mit ArkWardrobe wird die [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) eingerichtet. Fehlende optionale Module blenden ihre Bedienung aus; eine fehlgeschlagene Modulabfrage zeigt eine Fehlermeldung. Aktualisiere den Status für einen neuen Versuch.

## Portaladresse und Hinweise im Spiel

Die gemeinsame Adresse für `/web`, den Hinweis am Hauptspawn und die regelmäßigen Chat-Nachrichten steht in `data/plugins/ArkAccounts/config.yml`. Richte zuerst einen tatsächlich erreichbaren Webzugang ein; eine URL einzutragen erstellt weder DNS noch HTTPS oder Portfreigaben.

1. Melde dich mit Verwaltungsrechten an und stoppe Minecraft kontrolliert über das Webinterface. Nutze dafür bei Bedarf das lokale Betreiberkonto, das auch bei gestopptem Spiel erreichbar bleibt.
2. Öffne **Weitere Einstellungen → Spielerportal (ArkAccounts)**.
3. Setze `portal-url` auf die vollständige Hauptadresse einschließlich `https://`, beispielsweise `https://portal.example.org/`. Verwende keinen alten `/play/`-Pfad. Die Adresse darf keine Zugangsdaten oder Fragmentkennung enthalten.
4. Setze `portal-announcement-seconds`: `300` bedeutet fünf Minuten, `0` schaltet Chat-Hinweise aus; sonst sind `30` bis `86400` Sekunden zulässig.
5. `portal-spawn-display: true` zeigt den Hinweis am Hauptspawn; `false` deaktiviert ihn. Die Anzeige verändert keine Gebäude oder Blöcke.
6. Speichere und starte Minecraft wieder. Prüfe die Adresse am Spawn und mit `/web` im Spiel.

```yaml
portal-url: https://portal.example.org/
portal-announcement-seconds: 300
portal-spawn-display: true
```

Netzwerkbindung, interne Ports und geheime Tokenpfade sind im Editor geschützt. Bei einer Neuinstallation kann `python3 scripts/setup.py --portal-url https://portal.example.org/` die Adresse vorbelegen. Bei bestehenden Installationen die vorhandene Konfiguration bearbeiten; die Einrichtung überschreibt sie nicht. Falls du selbst `ARK_ACCOUNTS_PLAYER_URL` als Umgebungsvariable setzt, hat dieser Wert Vorrang: entferne oder ändere ihn ebenfalls, sonst wirkt die URL-Änderung in der Datei nicht.

Signierungszugänge gehören zu Wardrobe, nicht Accounts. Für bestehende gestoppte Installationen ergänzt `python3 scripts/init.py --portal-only` fehlende Portalgeheimnisse, ohne bestehende Zugänge zu ersetzen. Dies ist keine automatische Migration alter Kontodaten.

## Browserkarten und Dimensionen

Unter **Karte** wechselst du über die Weltwahl direkt in BlueMap zwischen den eingerichteten Survival-, Kreativ-, Nether- und End-Karten. Die Auswahl enthält die tatsächlich von BlueMap geladenen Karten. Eine Karte berechnet nur bereits von Minecraft erzeugte und gespeicherte Chunks; sie erzeugt keine neuen Weltbereiche und startet keine Vorberechnung einer Dimension. Unbesuchte Bereiche können deshalb leer bleiben.

Fehlen Nether oder End in der Auswahl, prüfe `data/plugins/BlueMap/maps/`. Ein vorhandenes Verzeichnis mit nur zwei manuell gepflegten Kartendateien erhält nicht automatisch alle weiteren Dimensionen. Ergänze ausdrückliche Kartenkonfigurationen mit eindeutiger ID und passendem Speicherpfad/Dimensionsschlüssel gemäß der [BlueMap-Konfigurationsanleitung](https://bluemap.bluecolored.de/wiki/configs/Maps.html). In der vorbereiteten nativen Welt verwenden beide `world: "ark"`, mit `dimension: "minecraft:the_nether"` beziehungsweise `"minecraft:the_end"`; ihre Karten-IDs sind `ark_nether` und `ark_end`. Übernimm Oberwelt-Flächengrenzen oder Höhlen-/Lichteinstellungen nicht unverändert in eine Nether-Karte.

Nach Sicherung und Speichern der Dateien kann ein Administrator `/bluemap reload light` ausführen, `/bluemap maps` prüfen und die Browserkarte aktualisieren. `/bluemap update ark_nether` und `/bluemap update ark_end` planen die Aktualisierung gespeicherter Chunks ein. Falls die Berechnung absichtlich gestoppt wurde, prüfe vor `/bluemap start` den Grund. Diese [BlueMap-Befehle](https://bluemap.bluecolored.de/wiki/getting-started/Commands.html) benötigen die jeweiligen Rechte; in der Panel-Konsole entfällt der führende `/`. Eine Neueinrichtung prüft und installiert den festen Weltstand; sie repariert keine BlueMap-Einstellungen eines bestehenden Servers.

## Private Backups und Updates

Stoppe betroffene Dienste ordentlich und sichere `data`, `panel-state`, `secrets`, `.env` und Compose gemeinsam auf privaten getrennten Speicher. Beziehe ArkAccounts, ArkWardrobe, ArkSupport, SkinsRestorer und vorhandene AuthMe-/Floodgate-Daten ein. Backups enthalten Passwörter/Hashes, Tokens, Wiederherstellungsprüfwerte, Tickets und Spielerdaten.

Neues Setup und Weltarchiv sind keine Updater. Entpacke keine öffentliche Welt über eine bestehende Installation. Versionen sind festgelegt; prüfe Kompatibilität und Wiederherstellung vor Upgrades. Spieleinstellungen bleiben durch `OVERRIDE_SERVER_PROPERTIES=FALSE` erhalten.

Teste Schutz, Inventartrennung, Atlas, Missionen und Dinos vor der Freigabe mit einem Nicht-OP-Konto. Prüfe außerdem die gewählten optionalen Module. Lokale Testdienste belegen keine echte Signierung und kein Konsolenverhalten; siehe [Fehlerbehebung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting-DE).
