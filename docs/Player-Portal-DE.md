# Spielerbereich

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal) · [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) · [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE)

Öffne das Webinterface des Servers direkt unter seiner Hauptadresse `/`. Alle verwenden dasselbe Anmeldeformular und dieselbe deutsch-/englischsprachige Oberfläche: Profil, Garderobe, Konto und Support gehören zum eigenen Spielerkonto. **ArkAccounts** stellt Anmeldung, Kontoeinstellungen, Profil und Statistiken bereit; Skins und Tickets erscheinen nur mit den jeweiligen optionalen Modulen. Alte `/play/`-Links leiten auf `/` weiter; ein zweites Portal oder eine OP-Anmeldung ist nicht nötig.

## Portaladresse im Spiel finden

Am Hauptspawn zeigt ein schwebender Hinweis die Portaladresse und `/web`. Zusätzlich erscheint standardmäßig alle fünf Minuten eine Chat-Nachricht mit derselben URL. Auf Java lässt sich der Chat-Link anklicken; auf Bedrock kannst du die ausgeschriebene Adresse verwenden. Der Betreiber kann die Hinweise abschalten oder das Intervall ändern. `/web` gibt nach der Spielanmeldung weiterhin deinen privaten Einmalcode und die Adresse aus; teile diesen Code nicht.

Die Adresse gehört zu diesem Server. Betreiber richten sie wie unter [Portaladresse und Hinweise](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE#portaladresse-und-hinweise-im-spiel) beschrieben ein.

## Anmelden

Bestehende AuthMe-Benutzer melden sich direkt mit bekanntem Spielernamen und AuthMe-Serverpasswort an. Eine vorherige `/web`-Kopplung ist nicht erforderlich. Das Passwort wird mit der Kontoverwaltung im Spiel geteilt, nicht mit Microsoft.

Automatisch angemeldete Java-/Bedrock-Spieler ohne bekanntes Serverpasswort können einen Code nutzen; jeder angemeldete Spieler darf diesen Weg alternativ verwenden:

1. Tritt bei und schließe die normale Serveranmeldung ab.
2. Führe privat im Spiel `/web` aus.
3. Löse den einmaligen Code innerhalb von fünf Minuten im Anmeldeformular der Hauptadresse ein. Nicht eingelöste Codes verfallen beim Serverneustart.
4. Öffne **Konto**, um bei Bedarf ein Passwort festzulegen. Eine frische Spielbestätigung erlaubt bis zu fünf Minuten lang ein leeres aktuelles Passwortfeld; siehe [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE).

Ein Name allein erstellt oder übernimmt kein Konto. Der Server bestimmt den Besitzer anhand der tatsächlichen Spielidentität. Bestehende Floodgate-Verknüpfungen bleiben maßgeblich; sie führen Bibliotheken und Inventare nicht automatisch zusammen. Melde dich nach Verknüpfungsänderungen erneut an.

## Einen Spielstand mit Java und Bedrock nutzen

Dasselbe Microsoft-Konto für beide Editionen gibt beiden Verbindungen **nicht** automatisch dieselbe Spieler-ID auf dem Server. Floodgates lokale Kontoverknüpfung bestätigt den Besitz beider Spielkonten. Microsoft-Passwort oder E-Mail-Adresse gehören dafür weder ins Portal noch in ein Ticket.

Kläre vor dem Verknüpfen mit dem Betreiber, welchen bisherigen Fortschritt du behalten möchtest. Die Verknüpfung wählt normalerweise den **Java-Spielstand** einschließlich Inventar und Position. Ein eigener Bedrock-Spielstand wird weder zusammengeführt noch gelöscht. Soll der Bedrock-Fortschritt erhalten bleiben, bitte **vorher** um eine gesicherte Datenübernahme. Survival- und Kreativ-Inventargruppen bleiben getrennt.

1. Tritt mit Java bei und schließe die Serveranmeldung ab; tritt außerdem mit deinem eigenen Bedrock-Konto bei.
2. Führe auf Java `/linkaccount Bedrockname` aus und lies den privaten Code samt Anleitung. Verwende den vom Befehl verlangten Bedrock-/Xbox-Namen.
3. Führe auf Bedrock innerhalb von fünf Minuten beziehungsweise der eingestellten Frist `/linkaccount Javaname code` aus.
4. Melde Java ab und verbinde Bedrock erneut. Prüfe Inventar, Endertruhe, Erfahrung und Position in jeder betroffenen Welt; melde dich anschließend neu im Portal an.

Danach nutzen beide Editionen die Java-Identität und können damit nicht gleichzeitig spielen. `/unlinkaccount` entfernt die lokale Verknüpfung dieses Servers; der Befehl führt keine Spielstände zusammen und entfernt keine unabhängig eingerichtete globale Verknüpfung. [Offizielle Floodgate-Anleitung](https://geysermc.org/wiki/floodgate/linking/).

### Lokale Verknüpfung als Betreiber aktivieren

Das neue Setup lädt die festgelegte SQLite-Erweiterung nach `data/plugins/floodgate/` und aktiviert lokale Verknüpfungen. Diese bleiben freiwillig: Bedrock-Spieler können weiterhin ohne Java-Konto beitreten. Das Paket enthält keine vorhandenen Verknüpfungen, privaten Schlüssel oder Spielerdaten; Floodgate erzeugt diese beim ersten Start privat.

Bestehende Installationen werden vom neuen Setup niemals überschrieben. Um dort lokale Verknüpfungen zu aktivieren, lade die SQLite-Erweiterung über die [offizielle Anleitung](https://geysermc.org/wiki/floodgate/linking/#local-linking), lege `floodgate-sqlite-database.jar` in `data/plugins/floodgate/` statt direkt im Plugin-Hauptverzeichnis ab und prüfe diesen Abschnitt in `data/plugins/floodgate/config.yml`:

```yaml
player-link:
  enabled: true
  enable-own-linking: true
  allowed: true
  link-code-timeout: 300
  type: sqlite
```

Erhalte andere vorhandene Einstellungen einschließlich deiner Entscheidung zur globalen Verknüpfung. Sichere Konten und Spieler-/Plugin-Daten konsistent bei gestopptem Minecraft, starte anschließend neu und prüfe die gegenseitige Verknüpfung mit eigenen Konten. Lokale Verknüpfungen haben Vorrang vor globalen. Bestehende AuthMe-/Offline-Installationen benötigen zusätzlich ihren geprüften Identitätsschutz; eine globale Abfrage oder ein gleicher Name ersetzt keinen Besitznachweis.

Die Übernahme eines bespielten Bedrock-Profils ist eine getrennte Betreiber-Migration, während beide Konten abgemeldet sind. Sichere zuerst beide Profile und betroffene Plugin-Datenbanken, entscheide bewusst über widersprüchlichen Java-Fortschritt und berücksichtige weltgetrennte Inventare sowie Fortschritt der Plugins statt nur einer Spielerdatei. Veröffentliche keine Kontozuordnungen und trage keine ungeprüfte Verknüpfung manuell ein.

## Eigene Daten und optionale Funktionen

Das Profil zeigt Identität, Online-Status sowie verfügbare gesamte Spielzeit, Tode, besiegte Kreaturen und Spieler mit Abfragezeitpunkt. Fehlende Werte werden als nicht verfügbar angezeigt und weder erfunden noch als weltgetrennte Historie ausgegeben.

- **Konto:** gemeinsames Passwort und optionale Wiederherstellungscodes, auch ohne Wardrobe/Support.
- **Garderobe:** eigene Skins und eingebauter [Editor](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor-DE), falls ArkWardrobe installiert ist. [Speichern/Aktivieren](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE).
- **Support:** eigene dauerhafte private Anfragen und Antworten, falls ArkSupport installiert ist. [Grenzen und Befehle](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE).

Schlägt die Modulabfrage fehl, erscheint eine Fehlermeldung mit Wiederholungsmöglichkeit. Aktualisiere das Profil für einen neuen Versuch; eine erfolgreiche Installation optionaler Funktionen wird dadurch nicht behauptet. Signierungsausfälle löschen keine gespeicherten PNGs. Ein neuer unsignierter Skin benötigt den [Signierungsanbieter](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) des Betreibers.

## Konto und Sitzungen schützen

Passwortänderung, Wiederherstellung und Zurücksetzung beenden bestehende Websitzungen. Wiederherstellung meldet dich nicht automatisch an. `/web logout` beendet deine Portalsitzungen. Sichere Wiederherstellungscodes privat und teile sie nicht in Tickets oder Screenshots.

Tatsächliche Server-OPs sehen nach derselben Anmeldung automatisch zusätzliche [Verwaltungsmenüs](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE#eine-anmeldung-aktuelle-serverrechte). Jede Verwaltungsanfrage prüft die aktuellen OP-Rechte auf dem Server; ein Browserflag erteilt sie nicht. `/deop` entzieht nur den Verwaltungszugriff und erhält eine gültige persönliche Sitzung; `/op` stellt den Zugriff ohne erneute Anmeldung wieder her. `/web admin` bleibt lediglich ein alter Alias für einen gewöhnlichen `/web`-Code und gewährt keine zusätzlichen Rechte. Cookies gelten jetzt unter `/`; nach dem Update kann einmaliges Neuanmelden nötig sein.

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.
