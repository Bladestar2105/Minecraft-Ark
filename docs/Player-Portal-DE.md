# Spielerportal

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) · [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE)

Öffne im Webinterface des Servers **Spielerportal** oder ergänze `/play/` an dessen Adresse. Wähle Deutsch oder Englisch. **ArkAccounts** stellt Anmeldung, Kontoeinstellungen, Profil und Statistiken bereit; Skins und Tickets erscheinen nur mit den jeweiligen optionalen Modulen.

## Anmelden

Bestehende AuthMe-Benutzer melden sich direkt mit bekanntem Spielernamen und AuthMe-Serverpasswort an. Eine vorherige `/web`-Kopplung ist nicht erforderlich. Das Passwort wird mit der Kontoverwaltung im Spiel geteilt, nicht mit Microsoft.

Automatisch angemeldete Java-/Bedrock-Spieler ohne bekanntes Serverpasswort können einen Code nutzen; jeder angemeldete Spieler darf diesen Weg alternativ verwenden:

1. Tritt bei und schließe die normale Serveranmeldung ab.
2. Führe privat im Spiel `/web` aus.
3. Löse den einmaligen Code innerhalb von fünf Minuten an der Spieleranmeldung ein. Nicht eingelöste Codes verfallen beim Serverneustart.
4. Öffne **Konto**, um bei Bedarf ein Passwort festzulegen. Eine frische Spielbestätigung erlaubt bis zu fünf Minuten lang ein leeres aktuelles Passwortfeld; siehe [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE).

Ein Name allein erstellt oder übernimmt kein Konto. Der Server bestimmt den Besitzer anhand der tatsächlichen Spielidentität. Bestehende Floodgate-Verknüpfungen bleiben maßgeblich; sie führen Bibliotheken und Inventare nicht automatisch zusammen. Melde dich nach Verknüpfungsänderungen erneut an.

## Eigene Daten und optionale Funktionen

Das Profil zeigt Identität, Online-Status sowie verfügbare gesamte Spielzeit, Tode, besiegte Kreaturen und Spieler mit Abfragezeitpunkt. Fehlende Werte werden als nicht verfügbar angezeigt und weder erfunden noch als weltgetrennte Historie ausgegeben.

- **Konto:** gemeinsames Passwort und optionale Wiederherstellungscodes, auch ohne Wardrobe/Support.
- **Garderobe:** eigene Skins und eingebauter [Editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor-DE), falls ArkWardrobe installiert ist. [Speichern/Aktivieren](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE).
- **Support:** eigene dauerhafte private Anfragen und Antworten, falls ArkSupport installiert ist. [Grenzen und Befehle](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE).

Schlägt die Modulabfrage fehl, erscheint eine Fehlermeldung mit Wiederholungsmöglichkeit. Aktualisiere das Profil für einen neuen Versuch; eine erfolgreiche Installation optionaler Funktionen wird dadurch nicht behauptet. Signierungsausfälle löschen keine gespeicherten PNGs. Ein neuer unsignierter Skin benötigt den [Signierungsanbieter](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) des Betreibers.

## Konto und Sitzungen schützen

Passwortänderung, Wiederherstellung und Zurücksetzung beenden bestehende Websitzungen. Wiederherstellung meldet dich nicht automatisch an. `/web logout` beendet deine Portalsitzungen. Sichere Wiederherstellungscodes privat und teile sie nicht in Tickets oder Screenshots.

Eine Spielersitzung hat keine Docker-, Konsolen- oder Verwaltungsrechte. Registrierte tatsächliche OPs verwenden eine getrennte ausdrückliche [Verwaltungsanmeldung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE#zwei-ausdrückliche-verwaltungsanmeldungen) mit Serverpasswort oder gesondertem `/web admin`-Code. Ein Spielercookie wird nicht automatisch aufgewertet.

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.
