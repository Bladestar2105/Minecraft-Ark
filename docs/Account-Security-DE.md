# Gemeinsames Passwort und optionale Wiederherstellung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security) · [Spielerportal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE) · [Administrativer Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE)

**ArkAccounts** verwaltet unabhängig von Wardrobe oder Support das gemeinsame **Server-/Portalpasswort**. Änderungen im Spiel oder Browser wirken für beide Bereiche; mit installiertem AuthMe bleibt dessen Passwortverwaltung maßgeblich. Microsoft-/Xbox-Passwort und das getrennte lokale Panel-Adminpasswort bleiben unverändert.

## Passwort festlegen oder ändern

Bestehende AuthMe-Benutzer melden sich direkt mit Servername und Serverpasswort im Portal an. Andere angemeldete Spieler nutzen `/web`. Gib unter **Konto** das aktuelle Passwort, ein neues Passwort mit **12–64 Zeichen** und die passende Bestätigung ein. Kürzere bisherige Passwörter können zur Änderung weiterhin geprüft werden.

Bis zu **fünf Minuten nach einer frischen Spielbesitzbestätigung über `/web`** darf das aktuelle Passwort optional bleiben. Ein leeres Feld setzt das erste Passwort oder ersetzt ein unbekanntes bestehendes Passwort, etwa ein automatisch erzeugtes AuthMe-Passwort. Die Oberfläche erklärt diese Möglichkeit. Eine normale Passwortanmeldung erteilt die Ausnahme nicht; ein eingegebenes aktuelles Passwort muss weiterhin stimmen.

Führe im Spiel `/account` ohne Argumente aus, um private Passwortfelder zu öffnen. Hänge keine Passwörter an Befehle an und schreibe sie nicht in den Chat. Kann ein Client die privaten Felder nicht anzeigen, nutze das Portal; die Abnahme echter Bedrock-/Konsolendialoge steht separat aus.

Erfolgreiche Änderungen beenden Websitzungen einschließlich der OP-Sitzungen dieses Kontos. AuthMe kann eine erneute Spielanmeldung verlangen. Vorhandene Wiederherstellungscodes und Identitätsverknüpfungen bleiben erhalten.

## Optionale Wiederherstellungscodes

Aktiviere Wiederherstellung unter **Konto** mit dem aktuellen Passwort. Codes werden **einmal** angezeigt und lassen sich kopieren/herunterladen. Bewahre sie privat außerhalb des geschützten Kontos/Geräts auf. Der Server speichert gehashte Prüfwerte.

Ist Wiederherstellung bereits aktiv, benötigen Austausch oder Abschaltung das aktuelle Passwort **und einen vorhandenen Wiederherstellungscode**. Eine frische Spielbestätigung ersetzt diese Prüfung der Wiederherstellungseinstellungen nicht. Neue Codes ersetzen den bisherigen Satz.

Über **Passwort vergessen?** auf der Spieleranmeldung setzt du mit Name, unbenutztem Wiederherstellungscode und neuem Passwort samt Bestätigung zurück. Erfolg verbraucht den Code und beendet Websitzungen; er meldet dich nicht automatisch an. Fehlt ein eingerichteter gültiger Code, bitte die Verwaltung um [Zurücksetzung eines bekannten Kontos](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE#ein-bekanntes-serverpasswort-zurücksetzen).

## Getrennter Verwaltungszugang

Ein registrierter tatsächlicher OP meldet sich ausdrücklich an der [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) an, mit gemeinsamem Serverpasswort oder `/web admin`. Geschützte Anfragen prüfen die OP-Rechte erneut. Ein Spielercookie erzeugt diese Berechtigung nicht. Der lokale Panelzugang `admin` bleibt getrennt und funktioniert auch bei nicht erreichbarem Spielserver.

Veröffentliche keine Codes, Hashes, Kontodateien, privaten Verknüpfungen oder Passwörter in Screenshots oder GitHub-Issues. Wiederherstellung repariert kein kompromittiertes Microsoft-Konto und überträgt keine fremde Identität.

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.
