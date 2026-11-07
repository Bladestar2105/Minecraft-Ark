# Eingebauter Skin-Editor

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Editor) · [Spielerportal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE)

Diese Funktion benötigt das optionale **ArkWardrobe** und dessen Abhängigkeit ArkAccounts. Konten, Profil und Passwörter funktionieren ohne Editor weiter. Siehe [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE).

Öffne **Meine Skins → Neuer Skin** oder wähle einen gespeicherten Entwurf. Dein Betreiber liefert den Editor aus; es wird keine fremde Editor- oder Upload-Webseite geöffnet.

1. Einen Namen vergeben. **Classic** verwendet vier Pixel breite Arme, **Slim** drei Pixel.
2. Körperteil, Fläche und Grund-/Außenebene wählen. Mit dem Pinsel malen, eine Farbe wählen oder Pixel der Außenebene radieren.
3. Die unmittelbare Vorder-/Rückansicht prüfen. Zoom sowie Rückgängig/Wiederholen helfen bei Details.
4. **Speichern** sichert den Entwurf. **Aktivieren** wendet diese gespeicherte Version anschließend an.

PNG-Import und -Export verwenden das Standardformat **64×64**. Dieser Import nimmt ausschließlich fertige Skin-PNGs an; größere Bilder, andere Formate und beschädigte PNGs werden abgewiesen. Für ein Porträt oder einen Charakter-Screenshot verwende die getrennte Foto-Aktion unten. Exportierte PNGs eignen sich als zusätzliche eigene Sicherung.

Mit der Tastatur das Pixelraster fokussieren, mit den Pfeiltasten Pixel wählen und mit Leertaste/Enter malen. Beschriftungen folgen der gewählten Sprache. Sprachwechsel oder Auswahl eines anderen Skins erhalten den offenen Entwurf; neuere Änderungen eines anderen Browsers führen zu einem Konflikthinweis statt still überschrieben zu werden.

Die erste Fassung bietet Pixelbearbeitung und Vorder-/Rückansichten. Sie ist kein frei begehbarer 3D-Baueditor. [Garderobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) beschreibt den Wechsel im Spiel; [Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) erklärt wartende oder fehlgeschlagene Aktivierungen.

## Skin aus Foto

Die Fotoübernahme ist ab der [Foto-Vorschauversion](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-07-photo-preview) enthalten.

**Skin aus Foto** erstellt aus getrennten Vorder- und Rückseitenfotos einen bearbeitbaren Entwurf für den ganzen Skin einschließlich Kleidungstexturen. Die gesamte Verarbeitung bleibt in deinem Browser. KI, ein zusätzlicher Dienst oder eine dedizierte GPU sind nicht erforderlich. Es gibt keine automatische Körpererkennung oder Pose-Segmentierung: Du richtest die Bildbereiche selbst aus.

1. **Skin aus Foto** öffnen und ein Vorderseitenfoto wählen. Für Details wie die Rückseite eines Trikots, Name oder Rückennummer ein getrenntes Rückseitenfoto ergänzen. Jedes lokale **JPEG-, PNG- oder WebP-Bild** darf höchstens **8 MiB**, **16.000.000 Pixel** und **8192 Pixel pro Seite** haben. Andere Formate, beschädigte und vom Browser nicht dekodierbare Bilder werden abgewiesen. Am besten eignen sich gerade Vorder-/Rückansichten mit vollständig abgebildeter, nicht angeschnittener Person.
2. Quellansicht und einen der sechs Körperteile wählen: Kopf, Rumpf, linker/rechter Arm oder linkes/rechtes Bein. Mit der Sechsteile-Hilfe beginnen, dann jeden rechteckigen Bereich per Zeiger oder beschrifteten x-/y-/Breiten-/Höheneingaben positionieren und skalieren. Alle Vorderseitenbereiche und bei vorhandenem Rückseitenfoto auch alle Rückseitenbereiche am jeweiligen Körperteil ausrichten.
3. **Classic** oder **Slim** ausdrücklich wählen. Haut-, Haar-, Oberteil- und Hosenfarbe im Bild aufnehmen oder über die Farbfelder ändern. Diese Farben dienen als Ersatzfarben und als Hintergrund für transparente Bildpixel.
4. Vorder- und Rückansicht prüfen. Die gewählten Bereiche liefern die vorderen/hinteren Körper- und Kleidungsflächen des Skins; Seiten, Ober- und Unterseiten erhalten Farben von den Bereichsrändern. Ohne Rückseitenfoto bleiben die hinteren Flächen einfarbig in den Ersatzfarben. Die Trikotvorderseite wird niemals auf den Rücken kopiert. Bei Bedarf Bereiche oder Farben korrigieren.
5. **Als neuen Entwurf öffnen** übernimmt das Ergebnis in den Editor und erhält deine anderen unfertigen Entwürfe. Dabei wird noch nichts gespeichert oder aktiviert. Bei Bedarf Pixel nachbearbeiten, dann wie gewohnt **Speichern** und getrennt **Aktivieren** verwenden.

Muster, Name oder Nummer eines Fußballtrikots lassen sich über die passenden vorderen/hinteren Rumpfbereiche übernehmen. Der Rumpf hat nur **8×12 Pixel**, jeder Arm **4×12** (Classic) oder **3×12** (Slim), die Kopfvorderseite **8×8**. Kleine Schrift und Nummern bleiben nicht garantiert lesbar; Ähnlichkeit und Kleidungsdetails sind durch diese Auflösung begrenzt. Nicht sichtbare Flächen sowie schlecht ausgerichtete Bereiche oder Posen müssen gegebenenfalls von Hand nachbearbeitet werden.

Die Anwendung hält Originalfotos und Zwischenbilder nur vorübergehend im Browser-Arbeitsspeicher; sie lädt sie nicht hoch und legt sie weder im dauerhaften Browser-Speicher noch in Serverlogs oder Backups ab. Abbrechen verwirft Fotos und Vorschau; auch nach Übernahme des Entwurfs werden sie freigegeben. Erst bewusstes Speichern sendet die erzeugte **64×64-RGBA-Skin-PNG** und das gewählte Modell an die vorhandene Skin-API. Ein konfigurierter [Signierdienst](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) kann anschließend wie bei anderen gespeicherten Skins diesen fertigen Skin erhalten. Originalfotos und deren Metadaten werden nicht übertragen.
