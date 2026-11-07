# Deinen Dino reiten

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding) · [Spieleranleitung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing-DE)

ArkDinos 1.1.0 ergänzt sechs eigene Modelle und Pteranodon-Flug. Das Weltarchiv allein enthält weder dieses Update noch die Ressourcenpakete. Der Crossplay-Prototyp wartet noch auf die visuelle Abnahme mit echten Java-/Bedrock-/Konsolenspielern.

Zähme eines der besonderen Habitat-Tiere im Survival mit einem Fackelliliensamen. Nur der Besitzer kann es innerhalb der eingerichteten Survival-Welten reiten. Ziele beim Zähmen oder Aufsteigen auf den Rumpf; lange Schwänze und Flügel sind dekorativ.

1. Nimm nichts in die Haupthand und benutze/interagiere mit deinem eigenen unbesetzten gezähmten Dino.
2. Lenke mit der normalen Bewegungssteuerung.
3. Steige mit Schleichen beziehungsweise der Absteigen-Taste ab. Zu Fuß neben dem Dino schaltet **Schleichen + Interagieren** weiterhin zwischen **Bleiben/Folgen** um.

Ein Sattel oder eine zusätzliche Client-Mod ist nicht nötig. Andere Spieler können den Dino nicht übernehmen. Wilde Dinos und Spieler im Kreativmodus sind ausgeschlossen. Während des Reitens pausiert das Folgen; die gespeicherte Bleiben-/Folgen-Einstellung bleibt nach dem Absteigen erhalten.

## Pteranodon fliegen

Springen hebt ab und gewinnt Höhe. Bewegung lenkt relativ zur Blickrichtung; beim Vorwärtsflug nach unten schauen leitet den Sinkflug ein. Lasse Bewegung und Springen zum Schweben los. Sinke zum Landen auf eine freie Bodenfläche. Der Flug endet spätestens 48 Blöcke über dem Start und unter der Weltdecke. Lande möglichst vor dem Absteigen: Beim Absprung in der Luft sinkt das Tier kontrolliert und der Reiter erhält kurzzeitig langsamen Fall. Die anderen fünf Arten bleiben Bodenreittiere.

Dinos bleiben in ihrer Welt, laden keine entfernten Chunks und teleportieren sich nicht zu dir. Reiten schützt nicht vor Stürzen, Lava, Ertrinken oder anderen Gefahren. Meide enge Durchgänge und gefährliches Gelände. Endet die Steuerung beim Verlassen von Survival oder der Welt, kehre normal zurück und interagiere erneut.

Java und Bedrock verwenden ihre normale Bewegungs-/Absteigen-Steuerung. Bedrock-/Konsoleneingaben und Sitzdarstellung benötigen noch eine Abnahme auf einem echten Gerät; Server- und automatisierte Java-Client-Prüfungen belegen kein Konsolenverhalten.

Beide Editionen benötigen die Server-Ressourcenpakete. Automatische Prüfungen decken alle sechs Engine-Exporte, native Flugeingaben/Abbrüche sowie Geysers Fahrerreihenfolge und Interaktions-IDs ab. Sichtbares Rendern, Bedrock-/Konsolensteuerung, Kamera/Sitzdarstellung und das tatsächliche Entladen entfernt gerittener Chunks bleiben offen. Nachgebildete Paketbestätigungen belegen kein Rendern.

## Einrichtung für Betreiber

Ein neues Setup mit Modul `dino` installiert den festgelegten BetterModel-/Geyser-Bridge-Stack und alle sechs Modelle. Setze mit `scripts/setup.py --portal-url https://mc.example.org/` deine erreichbare Hauptadresse: Java lädt dort `/resources/dinos.zip`. Der Standardwert `localhost` ist nur für lokale Tests gedacht. Behalte das standardmäßige Compose-Profil `admin` bei oder hoste `data/plugins/BetterModel/build.zip` selbst und setze Papers `resource-pack`-Adresse. Bedrock erhält sein erzeugtes Paket über Geyser. Ändert sich die Portaladresse später, passe auch Papers Paketadresse an.

Die vorhandenen Habitat-Tags verteilen vier Tiere je Art, ohne native Tiere, UUIDs, Besitzer oder Bleiben-Zustände zu ersetzen. Fallen optionale Modellkomponenten aus, bleibt natives Gameplay verfügbar. Bestehende Installationen werden getrennt vom neuen Setup in einem geplanten Wartungsfenster aktualisiert; dieser Prototyp ist noch nicht in der Live-Welt installiert. Lösche nach Modelländerungen den erzeugten Paketcache der Bridge vor dem Neuerzeugen, damit bei unveränderter Modellanzahl kein altes Paket erhalten bleibt.
