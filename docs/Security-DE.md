# Sicherheit und private Daten

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE)

Der lokale Paneladministrator steuert Docker und Serverkonfiguration. Halte das Panel an Loopback oder hinter einem eigenen geschützten Zugangsweg; entfernter Browserzugriff benötigt HTTPS oder einen privaten Tunnel. Veröffentliche nur nötige Spielports. Stelle RCON, die private Accounts-API und die getrennte Spieleranwendung niemals direkt ins Netz.

## Rollen getrennt halten

ArkAccounts verwaltet Spieleranmeldungen und tatsächliche OP-Prüfungen. Die Hauptoberfläche hat eine Anmeldung für Spieler und OPs; persönliche API-Routen bleiben von Verwaltungsrouten getrennt. Unterschiedliche interne Dienstschlüssel bleiben auf dem Server und gelangen niemals in Browsercode. Jede Verwaltungsanfrage prüft die aktuellen OP-Rechte über ArkAccounts; ein Spielercookie oder Browserflag allein reicht nicht. De-Op entzieht nur Verwaltungsrechte und erhält eine ansonsten gültige persönliche Sitzung; erneutes OP stellt die Rechte wieder her. Bei Ausfall der Kontoverwaltung wird spielerbasierte Verwaltung abgewiesen. Der lokale Notfalladministrator verwendet dasselbe Formular und bleibt ohne Minecraft erreichbar.

Wo AuthMe installiert ist, bleiben dessen Passwörter maßgeblich. Der öffentliche Standard nutzt Onlineprüfung für Java und Floodgate; ArkAccessBridge ist eine optionale Integration bestehender Server und kein Anlass, die Onlineprüfung abzuschalten. Führe keine Identitäten allein anhand eines übergebenen Namens, einer UUID oder IP-Adresse zusammen.

## Passwörter, Codes und Support

[Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) erklärt gemeinsame Server-/Portalpasswörter und optionale Einmalcodes zur Wiederherstellung. `/account` öffnet private Passwortfelder; hänge keine Geheimnisse als Befehlsargumente an und schreibe sie nicht in den Chat. `/web` erzeugt einen kurzlebigen gewöhnlichen Anmeldecode; `/web admin` bleibt ein alter Alias für dieselbe Codeart und gewährt keine zusätzlichen Rechte.

Die Verwaltung setzt unter **Spieler** bekannte Spielerpasswörter zurück, beendet deren Websitzungen und behält Wiederherstellungscodes/Verknüpfungen. Microsoft-Zugangsdaten bleiben unberührt. [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE)-Unterhaltungen sind private Datensätze für Besitzer und Verwaltung; füge keine Passwörter, Tokens oder Wiederherstellungscodes ein.

## Anfragelimits

Beide Webdienste begrenzen API-Anfragen auf **240 je Client-IP und Minute**, **40 je zehn Sekunden** sowie **1.200 je Minute über den jeweiligen Dienstprozess**. Die Zähler gelten pro aktuellem einzelnen Gunicorn-Worker; vor mehreren Workern sind gemeinsame Zähler nötig. Am öffentlichen Haupteinstieg bestimmt `X-Forwarded-For` die Client-IP nur, wenn die direkte Gegenstelle exakt einer IP in `FORWARDED_ALLOW_IPS` entspricht; andernfalls zählt die direkte Gegenstelle. Vertraue keinen beliebigen weitergeleiteten Headern oder `*`.

Die native Accounts-API behält **240 Anfragen je Sitzung und Minute** bei und ergänzt gemeinsame Budgets pro Spieler-UUID: **600 Anfragen**, **60 Schreibzugriffe**, **drei Skinaktivierungen** und **zehn Supportantworten pro Minute**. Administrative Dienst-Schreibzugriffe außerhalb der Authentifizierungsendpunkte sind zusätzlich auf **30 pro Minute** begrenzt; das bisherige administrative Anfragebudget bleibt bestehen. Authentifizierung, Dienstschlüsselprüfungen, Größenlimits und begrenzte Warteschlangen gelten weiter; die jeweils engere Grenze greift. Eine Begrenzung liefert **HTTP429 mit `Retry-After`**: Warte, statt sofort wiederholt anzufragen.

Dies sind Anfragelimits, keine Docker-CPU-/RAM-Grenzen oder Absicherung gegen Netzwerk-DDoS. Verkehr, der die Verbindung bereits vor der Anwendung auslastet, braucht Filter beim Reverseproxy, in der Firewall oder beim vorgeschalteten Anbieter. [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) erklärt die Zugriffsgrenzen.

## Backups und Veröffentlichung

Sichere die gesamte private Installation gemeinsam: `data`, `panel-state`, `secrets`, `.env` und Compose. Kontodaten liegen in `data/plugins/ArkAccounts/`, Skins/Signierung in ArkWardrobe/SkinsRestorer, Tickets in ArkSupport. Bewahre vorhandene AuthMe-/Floodgate-Daten und Weltidentitäten auf. Schütze Backups und teste ihre Wiederherstellung.

Das öffentliche Paket enthält geprüften Code, Konfiguration und Projektbinärdateien, keine privaten Daten. Konten-/Sitzungsdatenbanken, Signierungszugänge, Inventare, private Grundstücke, Logs, Kartencaches und lokale Geheimnisse gehören nicht in öffentliche Releases oder Fehlerberichte. Zugangsdaten werden lokal erzeugt; ein gemeinsames Betreiberkonto oder ein gemeinsamer API-Schlüssel wird nicht mitgeliefert.

Testdienste prüfen Grenzen, belegen aber keine echte OAuth-Berechtigung, Skinanzeige oder Konsolensteuerung. [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE), [Dinos reiten](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE) und [Entwicklung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development-DE) nennen die noch nötigen Abnahmeprüfungen.

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.

## Dependabot und Abhängigkeitsupdates

Im offiziellen GitHub-Repository sind Dependabot-Sicherheitswarnungen, der Dependency Graph und Pull Requests für unterstützte Sicherheitsupdates aktiviert. Die Konfiguration prüft GitHub Actions, Python-Abhängigkeiten in `panel/requirements.txt`, das Panel-Dockerfile und die Docker-Compose-Images wöchentlich auf neue Versionen. Änderungen werden als Pull Requests vorgeschlagen und müssen geprüft werden; es gibt keine automatische Zusammenführung oder Installation auf einem Spielserver. Persönliche E-Mail-/Web-Benachrichtigungen richten sich nach den GitHub-Benachrichtigungseinstellungen des jeweiligen Kontos.

Die eigenen Java-/Server-Plugin-Pins in `dependencies-build.json` und `dependencies-runtime.json` sind keine von Dependabot unterstützten Paketmanifeste. Ihre Versionen, Herkunft und Prüfsummen müssen weiterhin separat geprüft werden. Docker-Versionprüfungen ersetzen außerdem keinen Schwachstellenscan der Container-Images.
