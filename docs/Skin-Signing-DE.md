# Skin-Signierung: Einrichtung durch Betreiber

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Garderobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE)

Diese Anleitung richtet **neue Skin-Signaturen** ein. Gestaltung und Verwaltung bleiben in unserem Webinterface. **ArkAccounts** stellt Konten und Passwörter bereit; für Skins müssen zusätzlich **ArkWardrobe** und **SkinsRestorer** installiert sein. Melde dich auf der Hauptseite mit Verwaltungsrechten an und öffne oben **Skin-Signierung**. Bei einem Spielerkonto werden aktuelle OP-Rechte automatisch erkannt.

| Weg | Du benötigst | Verarbeitung |
| --- | --- | --- |
| MineSkin | MineSkin-Konto und eigener API-Schlüssel | Der Server sendet die fertige Skin-PNG zur Signierung an MineSkin. |
| Eigener Generator | Eigene Microsoft-Anwendung mit Minecraft-API-Zugang und geeignetes Minecraft-Java-Konto | Unser Server erledigt Anmeldung, Upload und Abholung der Signatur direkt bei Microsoft/Minecraft. |

Bei beiden Wegen stellt Mojang die gültige Signatur aus. Eigener Betrieb erzeugt keinen eigenen Mojang-Signaturschlüssel und funktioniert nicht ohne Microsoft/Minecraft. Originalfotos aus dem [Fotoimport](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor-DE) bleiben im Browser; bei MineSkin wird die fertige Skin-PNG weitergegeben.

## A. MineSkin Schritt für Schritt

### 1. Konto und API-Schlüssel anlegen

1. Öffne [MineSkin Account](https://account.mineskin.org/) und melde dich an. Falls du noch kein Konto hast, nutze die dort angebotene Registrierung.
2. Öffne **API Keys** oder direkt [die Schlüsselverwaltung](https://account.mineskin.org/keys/).
3. Erstelle einen eigenen API-Schlüssel für diese Installation. Vergib, sofern angeboten, einen Namen wie `Ark Wardrobe Server` und kopiere den vollständigen geheimen Schlüssel. Die genaue Beschriftung der Erstellen-Schaltfläche kann sich ändern.
4. Beachte die aktuellen MineSkin-Kontingente. Ein API-Schlüssel bedeutet kein unbegrenztes Kontingent. Ein Minecraft-Konto zum Signierungspool beizusteuern ist ein anderer Vorgang und für das Eintragen eines Schlüssels hier nicht erforderlich.

Die [offizielle Einstiegsanleitung](https://docs.mineskin.org/docs/guides/getting-started/) führt ebenfalls zur Konto- und Schlüsselverwaltung.

### 2. Im Webinterface einbinden

1. Öffne **Skin-Signierung** und wähle unter **Signierungsanbieter** den Eintrag **MineSkin**.
2. Füge den Wert in **MineSkin-API-Schlüssel** ein: ohne `Bearer`, Anführungszeichen oder zusätzliche URL.
3. Lass **Gespeicherten API-Schlüssel löschen** ausgeschaltet und klicke **Signierung speichern**.
4. Prüfe **API-Schlüssel eingerichtet.** Das bestätigt die Speicherung, noch keinen erfolgreichen API-Aufruf. Führe anschließend den Funktionstest unter Abschnitt C aus.

Eine API-URL oder Microsoft-Client-ID musst du für MineSkin nicht eintragen. ArkWardrobe ruft `https://api.mineskin.org/v2/queue` auf, setzt den Bearer-Header und fragt den Auftrag selbst ab. Der Schlüssel bleibt serverseitig. Ein leeres Schlüsselfeld behält den gespeicherten Wert; zum Ersetzen einen neuen Wert eintragen und speichern, zum Entfernen die ausdrückliche Löschoption verwenden.

### 3. Optionale Schlüsselbeschränkungen

MineSkin unterstützt Beschränkungen nach IP, Origin und User-Agent. Unsere Anfragen kommen vom **Server**: Eine Browser-Origin-Beschränkung passt daher nicht zu dieser Anbindung. Verwende bei einer IP-Beschränkung die öffentliche ausgehende Server-IP, nicht die private LAN-Adresse. Bei einer User-Agent-Beschränkung muss dieser gesendete Wert passen:

```text
ArkWardrobe/1.0 (+https://github.com/Bladestar2105/Minecraft-Ark)
```

Diese Zuordnung zur Serveranbindung ergibt sich aus den [dokumentierten MineSkin-Fehlern](https://docs.mineskin.org/docs/wiki/common-errors/). Nach einem Server-, Anschluss- oder Pluginwechsel erneut prüfen. Schlüssel gehören weder in Browser-JavaScript noch ins GitHub-Repository.

## B. Eigener Microsoft-Generator Schritt für Schritt

**Zuerst beachten:** Eine Microsoft-Anwendungsregistrierung allein erteilt keinen Minecraft-Services-Zugang. Für neue App-IDs gibt es einen eigenen Prüfprozess. Die Freigabe deiner Anwendung ist nicht zugesichert. Gerätecode-Anmeldung, Kontoverbindung und echter Skinupload sind getrennte Prüfschritte.

### 1. Voraussetzungen vorbereiten

- Ein geeignetes Microsoft-Entra-Verzeichnis und Berechtigung zur App-Registrierung. Microsoft nennt in seiner [Registrierungsanleitung](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app) auch ein Azure-Konto mit aktivem Abonnement. Ein Minecraft-Konto allein stellt dieses Verzeichnis nicht automatisch bereit. Nutze ein vorhandenes geeignetes Verzeichnis oder prüfe Microsofts aktuelle Registrierungsbedingungen; ein kostenfreies neues Azure-Konto wird hier nicht versprochen.
- Ein persönliches Microsoft-Konto mit nutzbarer **Minecraft Java Edition**, eingerichtetem Java-Spielerprofil und Xbox-Profil. Prüfe, dass du Java regulär starten und deinen Skin ändern kannst. Das Entra-Verwaltungskonto und das später verbundene Java-Konto müssen nicht identisch sein.
- Ein geeignetes Betreiberkonto: Neue Signierungen ändern dessen **globalen Java-Skin**, auch außerhalb unseres Servers. Wähle kein Konto, dessen Aussehen unverändert bleiben soll.

### 2. Anwendung registrieren

1. Öffne das [Microsoft Entra Admin Center](https://entra.microsoft.com/) und wähle dein vorgesehenes Verzeichnis.
2. Gehe zu **Entra ID → App registrations → New registration**. Deutsche Portalbezeichnungen können abweichen.
3. Vergib einen Namen wie `Ark Wardrobe Signer`; beachte auch die Namensregeln im Minecraft-Prüfformular.
4. Wähle **Personal accounts only**. Falls du zusätzlich Organisationskonten erlaubst, müssen persönliche Microsoft-Konten ausdrücklich eingeschlossen sein. Eine rein organisationsinterne Registrierung passt hier nicht.
5. Lass die optionale Weiterleitungsadresse leer und schließe die Registrierung ab.
6. Notiere in der Übersicht **Application (client) ID** und die Verzeichnis-/Mandanten-ID. Beide braucht der Minecraft-Prüfantrag; unser Webinterface benötigt nur die Client-ID. Objekt-ID und Mandanten-ID sind kein Ersatz dafür.

### 3. Gerätecode-Anmeldung erlauben

1. Öffne bei dieser Anwendung die Authentifizierungseinstellungen.
2. Aktiviere in den erweiterten Einstellungen **Allow public client flows** und speichere. Je nach Portalansicht heißt die Option sinngemäß „mobile und Desktop-Anmeldeabläufe aktivieren“.
3. Lege für diesen Gerätecode-Ablauf weder ein Client-Secret noch eine Callback-/Redirect-URL an.

Microsoft beschreibt die Einstellung unter [Public-Client-Ablauf aktivieren](https://learn.microsoft.com/en-us/entra/identity-platform/scenario-desktop-app-configuration#enable-public-client-flow). ArkWardrobe fordert `XboxLive.signin offline_access` selbst an; dafür gibt es kein Scope-Feld im Webinterface. Microsoft-Graph-Berechtigungen allein gewähren keinen Minecraft-API-Zugang.

### 4. Minecraft-Zugang für die App-ID prüfen lassen

1. Öffne Microsofts [AppID-Prüfformular](https://aka.ms/mce-reviewappid). Lies die dortigen Bedingungen, EULA und Nutzungsrichtlinien.
2. Wähle den Antrag für eine neue App-ID. Trage die verlangten Angaben ein: Kontakt-E-Mail, Anwendungsname, Client-ID, Mandanten-ID, zugehörige Webseite und tatsächlicher Einsatzzweck. Das Formular nennt einen Abgleich der Kontaktadresse mit Azure.
3. Beschreibe wahrheitsgemäß den selbst betriebenen Skin-Generator: Anmeldung mit einem Java-berechtigten Betreiberkonto, Skinupload und Abholung signierter Texturen. Das Formular verlangt Authentifizierung und Prüfung des Spielbesitzes. Die Genehmigung dieses Einsatzzwecks ist nicht garantiert.
4. Beachte die Namensvorgaben: Neue App-Namen dürfen unter anderem Minecraft, Microsoft, Mojang und Xbox nicht enthalten. Warte auf die Entscheidung. Laut Formular werden Anträge wöchentlich geprüft; Mehrfacheinsendungen beschleunigen dies nicht.

Verwende keine fremde Launcher-ID. Ohne erteilten Minecraft-Zugang ist deine Entra-Registrierung noch keine nutzbare Alternative zu MineSkin.

### 5. Im Webinterface eintragen und Konto verbinden

1. Öffne **Skin-Signierung** und wähle **Eigener Generator**.
2. Trage deine Client-ID in **Microsoft-Anwendungs-ID (Client-ID)** ein und klicke **Signierung speichern**. Ungespeicherte Änderungen deaktivieren die Verbindungsschaltfläche.
3. Klicke **Konto verbinden** und bestätige den Hinweis zur globalen Skinänderung.
4. Öffne **Microsoft-Anmeldung öffnen ↗**. Gib den im Panel gezeigten Gerätecode auf der offiziellen Microsoft-Seite ein und melde dich mit dem vorgesehenen Java-Konto an. Prüfe den Anwendungsnamen vor deiner Zustimmung. Microsoft-Passwort und MFA-Code gehören ausschließlich auf die Microsoft-Seite.
5. Lass das Panel geöffnet. Es fragt den Status automatisch ab; alternativ **Status aktualisieren** verwenden. Nach erfolgreicher Anmeldung einschließlich Profilabruf erscheint **Verbunden: …** mit dem Java-Namen. Ist der Code abgelaufen, starte eine neue Verbindung.
6. Führe Abschnitt C aus. **Verbunden** beweist noch keinen erfolgreichen neuen Skinupload. Der Hinweis zur erforderlichen Betreiberprüfung ist kein Prüfen-Button und verschwindet nicht automatisch nach einem Test.

Für den Gerätecode sind ausgehende HTTPS-Verbindungen nötig, keine zusätzliche eingehende OAuth-Portfreigabe. Tokens bleiben privat auf dem Server und werden erneuert, solange Microsoft dies erlaubt. **Konto trennen** entfernt gespeicherte Kontozugänge. Ändern der Client-ID, abgelaufene oder widerrufene Berechtigungen können eine neue Verbindung verlangen.

## C. Funktionstest für beide Anbieter

1. Melde dich mit einem Spielerkonto an und öffne **Garderobe**. Auch ein OP-Spielerkonto besitzt seine eigene Garderobe; das lokale Betreiberkonto hat keine Spielerbibliothek.
2. Erstelle einen neuen, eindeutig veränderten Skin und wähle das passende Modell **Classic** oder **Slim**. Verwende eine neue Bild-/Modellkombination, damit keine Signatur aus dem Cache den Test ersetzt.
3. Klicke **Speichern**, danach getrennt **Aktivieren**. Speichern allein fordert keine Signierung an.
4. Warte auf den tatsächlichen Status. **Wird vorbereitet …** bestätigt nur den Auftrag. Ein Fehler ist kein erfolgreicher Skinwechsel.
5. Prüfe den Skin mit einem echten Java-Client und den vorgesehenen Bedrock-Clients: Vorderseite, Rückseite und Armmodell. Abwesende Spieler erhalten eine erfolgreich vorbereitete Auswahl beim nächsten Beitritt.

Erst eine neue erfolgreiche Signierung mit passender Darstellung bestätigt die Einrichtung. Lokale HTTP-/OAuth-Testdienste, gespeicherte Schlüssel und bereits signierte Skins reichen nicht aus.

## D. Häufige Probleme

| Problem | Nächster Schritt |
| --- | --- |
| **Skin-Signierung** fehlt | ArkWardrobe/SkinsRestorer und Verwaltungsrechte prüfen; Modulstatus aktualisieren. |
| MineSkin-Schlüssel fehlt oder ist ungültig | Vollständigen Schlüssel erneut eintragen und speichern; im MineSkin-Konto auf Widerruf prüfen. |
| MineSkin verweigert Client, Origin oder Agent | Schlüsselbeschränkungen mit öffentlicher Server-IP und obigem User-Agent abgleichen. Die genaue Anbietermeldung muss nicht im Webinterface erscheinen. |
| HTTP 429 / Anfragelimit | Wartezeit beachten. Unser API-Schutz und der Anbieter können begrenzen; MineSkin-Kontingente prüfen. |
| Microsoft verlangt ein Client-Secret / AADSTS7000218 | Public-Client-Einstellung und App-ID prüfen; kein Secret in ein anderes Feld eintragen. [Microsoft-Fehlerhilfe](https://learn.microsoft.com/en-us/troubleshoot/entra/entra-id/app-integration/confidential-client-application-authentication-error-aadsts7000218). |
| Gerätecode klappt, Verbindung oder Upload scheitert | Persönlichen Kontotyp, Java-/Xbox-Profil, Kontoberechtigung und Minecraft-Zulassung der App prüfen. Entra-Registrierung oder Graph-Consent beheben fehlenden Minecraft-Zugang nicht automatisch. |
| Erneute Anmeldung erforderlich | **Konto verbinden** erneut ausführen; eine geänderte Client-ID zuerst speichern. |
| Anbieter nicht erreichbar | Ausgehendes HTTPS, DNS und Anbieterstatus prüfen. Es gibt keine automatische Anbieterumschaltung. |

## E. Anbieterwechsel und private Backups

Ein Anbieterwechsel betrifft künftige Signierungen und erhält PNGs, ausgewählte Skins und gültige Signaturen im Cache. Fehler werden nicht als erfolgreicher Wechsel gemeldet; Aufträge gehen auch nicht heimlich an den anderen Anbieter.

Sichere gemeinsam `data/plugins/ArkAccounts/`, `data/plugins/ArkWardrobe/`, `data/plugins/SkinsRestorer/`, vorhandene AuthMe-/Floodgate-Daten, `secrets` und Panelzustand. Insbesondere `data/plugins/ArkWardrobe/signing-secrets.json` enthält Geheimnisse und gehört nicht auf GitHub. Kontodaten gehören zum Accounts-Kern. Veröffentliche keine Schlüssel, Gerätecodes, Tokens oder Kontodateien in Support-Anfragen/Issues.

Externe Einrichtung zuletzt geprüft: **7. November 2026**. Portalseiten und Freigabebedingungen können sich ändern. Weitere Primärquellen: [Microsoft-Gerätecode](https://learn.microsoft.com/en-us/entra/identity-platform/v2-oauth2-device-code), [MineSkin-Limits](https://docs.mineskin.org/docs/guides/rate-limits/), [MineSkin-Änderungen](https://docs.mineskin.org/docs/wiki/changelog/), [Signaturerzeugung](https://docs.mineskin.org/docs/wiki/faq/).
