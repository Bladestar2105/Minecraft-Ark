# Skin-Signierung: Einrichtung durch Betreiber

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) · [Garderobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE)

Öffne mit installiertem optionalem **ArkWardrobe** die **Verwaltung → Skin-Signierung** und wähle **MineSkin** oder **Eigener Generator**. Nur angemeldete Administratoren richten die Signierung ein. Konten, Profil und Passwörter gehören zum unabhängigen ArkAccounts-Kern. Kein Anbieter öffnet eine fremde Editorseite für Spieler.

## MineSkin

Trage einen eigenen API-Schlüssel ein. Ein gespeicherter Schlüssel wird als eingerichtet gemeldet und nicht im Klartext zurückgesendet. Leer behält ihn bei; die ausdrückliche Löschoption entfernt ihn. Anfragelimits und Ausfälle gelten weiterhin. Es gibt keine automatische Anbieterumschaltung.

## Eigener Generator

Konfiguriere eine eigene Microsoft-Public-Client-Anwendungs-ID und verbinde ein eigenes Konto mit Minecraft-Java-Berechtigung über den im Panel gezeigten Gerätecode. Microsoft-Zugangsdaten gehören nur auf die offizielle Microsoft-Seite. Ein gemeinsames Projektkonto oder eine fremde Launcher-Client-ID wird nicht mitgeliefert.

Der vollständige Ablauf muss Microsoft-/Xbox-Anmeldung, Minecraft-Profilzugriff und einen tatsächlichen Skinupload erfolgreich durchlaufen. **Eine Microsoft-Anmeldung allein belegt keinen Minecraft-Services-Zugang einer neu registrierten Anwendung.** Prüfe die aktuellen Zugangsvoraussetzungen und dein Konto vor dem Einsatz. Lokale HTTP-/OAuth-Testdienste belegen keine echte Abnahme.

Der Generator verändert beim Signieren den **globalen Minecraft-Skin des verbundenen Kontos**, um Mojang-signierte Textureigenschaften zu erhalten. Wähle ein dafür geeignetes Konto. Tokens bleiben privat auf dem Server; Trennen entfernt gespeicherte Kontozugänge. Abgelaufene oder widerrufene Berechtigungen können eine erneute Verbindung verlangen.

Eigener Betrieb ersetzt MineSkin bei der Erzeugung; gültige Signaturen stellt weiterhin Mojang aus. Erreichbarkeit und Limits von Microsoft/Minecraft bleiben wichtig. Siehe [MineSkins Beschreibung des Ablaufs](https://docs.mineskin.org/docs/wiki/faq/).

## Warteschlange, Fehler und Backups

Neue Bild-/Modellkombinationen werden eingereiht. Gültige zwischengespeicherte Signaturen können wiederverwendet werden. Ein Anbieterwechsel betrifft künftige Signierungen und behält PNGs, ausgewählte Skins und gültige Signaturen bei; er sendet Arbeiten nicht heimlich an einen anderen Anbieter. Ein Fehler macht eine eingereihte Aktivierung nicht zum Erfolg.

Sichere die private Installation gemeinsam einschließlich `data/plugins/ArkAccounts/`, `data/plugins/ArkWardrobe/`, `data/plugins/SkinsRestorer/`, vorhandener AuthMe-/Floodgate-Daten, `secrets` und Panelzustand. Kontodaten gehören nicht Wardrobe. Veröffentliche keine Schlüssel, Gerätecodes, Tokens oder Kontodateien in Support-Anfragen/Issues.

Teste nach der Einrichtung einen neuen Skin und das Modell mit einem echten Java-Client und den vorgesehenen Bedrock-Clients. Testdienste und bereits signierte Skins beweisen weder eine neue echte Signatur noch die Konsolenanzeige.
