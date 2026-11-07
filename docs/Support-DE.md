# Support und Passwortzurücksetzungen durch die Verwaltung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support) · [Spielerportal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE)

**ArkSupport** ergänzt dauerhafte private Anfragen. Dafür muss es gemeinsam mit ArkAccounts installiert sein. Ohne Support blendet der Browser die Ticketbedienung aus. Passwortzurücksetzungen durch die Verwaltung sind eine getrennte **ArkAccounts**-Funktion unter **Spieler** und bleiben ohne Support oder Wardrobe verfügbar.

## Hilfe anfordern

Öffne im Spielerportal **Support**, wähle eine Priorität und schreibe deine Nachricht. Nach der Anmeldung im Spiel kannst du ebenfalls eine Anfrage erstellen:

```text
/support [low|normal|high] Message
/support high I cannot use the creative portal
```

Ohne Auswahl gilt `normal`. Nachrichten haben 1–2000 Zeichen; im Browser sind Zeilenumbrüche erlaubt. Teile keine Passwörter, Anmelde-/Wiederherstellungscodes oder Signierungszugänge. Du kannst ausschließlich eigene Anfragen lesen und beantworten. Geschlossene Anfragen bleiben bis zur Wiedereröffnung durch die Verwaltung schreibgeschützt.

Angemeldete Operatoren erhalten im Spiel einen privaten Aktivitätshinweis. Antworten benachrichtigen die betroffenen anwesenden Spieler/Operatoren; der Hinweis enthält eine Referenz statt der vollständigen Unterhaltung. Browserlisten werden bei sichtbarer Seite und aktiver Sitzung aktualisiert. E-Mails oder externe Ticketsysteme sind nicht beteiligt.

## Anfragen als Administrator bearbeiten

Melde dich an der [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) an, öffne **Support**, wähle eine Anfrage und antworte. Anschließend kannst du sie schließen oder wieder öffnen. Die Liste zeigt Besitzer, Priorität, Status und Zeit; die Detailansicht zeigt die gespeicherte Unterhaltung. Bei einer neueren Änderung durch jemand anderen entsteht ein Konflikt statt einer stillen Überschreibung. Lies/aktualisiere die Unterhaltung vor einem erneuten Versuch; ungesendeter Antworttext bleibt erhalten.

Zwischen neuen Anfragen liegen mindestens 60 Sekunden. Pro Spieler sind bis zu fünf offene Anfragen und pro Anfrage bis zu 50 Nachrichten erlaubt. Listen zeigen die neuesten 100 Anfragen. Die Ablage ist auf 1.000 Tickets und 16 MiB begrenzt; bei Erschöpfung werden weitere Schreibvorgänge sichtbar abgelehnt und keine alten Datensätze heimlich gelöscht. Sichere `data/plugins/ArkSupport/` privat.

## Ein bekanntes Serverpasswort zurücksetzen

Wähle unter **Verwaltung → Spieler** das bekannte Serverkonto oder gib dessen Namen ein. Trage ein neues Passwort und die passende Bestätigung mit **12–64 Zeichen** ein. Ein angemeldeter Administrator benötigt das bisherige Spielerpasswort nicht. Unbekannte Konten werden abgelehnt; das Formular registriert keine beliebigen Browserkonten.

Die Zurücksetzung ändert das gemeinsame Spiel-/Portalpasswort, beendet die Websitzungen des Zielkontos und behält Wiederherstellungscodes sowie Identitätsverknüpfungen. Mit AuthMe läuft die Änderung über dessen Kontoverwaltung; eine erneute Spielanmeldung kann erforderlich sein. Microsoft-Zugangsdaten und das getrennte lokale Adminpasswort bleiben unverändert. Das Audit enthält Aktion, Ziel und Zeit ohne das Passwort. Siehe [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE) und [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE).
