# Sicherheit und private Daten

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE)

Der lokale Paneladministrator steuert Docker und Serverkonfiguration. Halte das Panel an Loopback oder hinter einem eigenen geschützten Zugangsweg; entfernter Browserzugriff benötigt HTTPS oder einen privaten Tunnel. Veröffentliche nur nötige Spielports. Stelle RCON, die private Accounts-API und die getrennte Spieleranwendung niemals direkt ins Netz.

## Rollen getrennt halten

ArkAccounts verwaltet Spieleranmeldungen und tatsächliche OP-Prüfungen. Spieler- und Verwaltungsdienste haben unterschiedliche interne Tokens, Cookies sowie CSRF-/Origin-Prüfungen. Ein Spielercookie erteilt keine Administratorrechte. Die OP-Anmeldung erfolgt ausdrücklich; jede geschützte Anfrage prüft den aktuellen OP-Status erneut. Fehlende OP-Rechte oder eine nicht erreichbare Kontoverwaltung verhindern den Zugriff. Die lokale Panelanmeldung bleibt getrennt und funktioniert ohne Minecraft.

Wo AuthMe installiert ist, bleiben dessen Passwörter maßgeblich. Der öffentliche Standard nutzt Onlineprüfung für Java und Floodgate; ArkAccessBridge ist eine optionale Integration bestehender Server und kein Anlass, die Onlineprüfung abzuschalten. Führe keine Identitäten allein anhand eines übergebenen Namens, einer UUID oder IP-Adresse zusammen.

## Passwörter, Codes und Support

[Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) erklärt gemeinsame Server-/Portalpasswörter und optionale Einmalcodes zur Wiederherstellung. `/account` öffnet private Passwortfelder; hänge keine Geheimnisse als Befehlsargumente an und schreibe sie nicht in den Chat. Codes aus `/web` und `/web admin` sind kurzlebige Zugangsdaten für verschiedene Anmeldebereiche.

Die Verwaltung setzt unter **Spieler** bekannte Spielerpasswörter zurück, beendet deren Websitzungen und behält Wiederherstellungscodes/Verknüpfungen. Microsoft-Zugangsdaten bleiben unberührt. [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE)-Unterhaltungen sind private Datensätze für Besitzer und Verwaltung; füge keine Passwörter, Tokens oder Wiederherstellungscodes ein.

## Backups und Veröffentlichung

Sichere die gesamte private Installation gemeinsam: `data`, `panel-state`, `secrets`, `.env` und Compose. Kontodaten liegen in `data/plugins/ArkAccounts/`, Skins/Signierung in ArkWardrobe/SkinsRestorer, Tickets in ArkSupport. Bewahre vorhandene AuthMe-/Floodgate-Daten und Weltidentitäten auf. Schütze Backups und teste ihre Wiederherstellung.

Das öffentliche Paket enthält geprüften Code, Konfiguration und Projektbinärdateien, keine privaten Daten. Konten-/Sitzungsdatenbanken, Signierungszugänge, Inventare, private Grundstücke, Logs, Kartencaches und lokale Geheimnisse gehören nicht in öffentliche Releases oder Fehlerberichte. Zugangsdaten werden lokal erzeugt; ein gemeinsames Betreiberkonto oder ein gemeinsamer API-Schlüssel wird nicht mitgeliefert.

Testdienste prüfen Grenzen, belegen aber keine echte OAuth-Berechtigung, Skinanzeige oder Konsolensteuerung. [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE), [Dinos reiten](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE) und [Entwicklung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Development-DE) nennen die noch nötigen Abnahmeprüfungen.

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.
