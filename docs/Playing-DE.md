# Spieleranleitung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Playing) · [Spielerportal](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Player-Portal-DE) · [Dinos reiten](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Dino-Riding-DE)

Verbinde Java 26.3 mit deiner Serveradresse auf Port 25565 oder Bedrock auf Port 19132. PS5/Switch benötigen gegebenenfalls einen geeigneten zusätzlichen Verbindungsweg; öffentlicher DNS wird nicht mitgeliefert. Echte Konsolenwege sind getrennt zu prüfen.

Das Webinterface bietet Deutsch und Englisch; vorhandene Abenteuertexte, Missionsnamen und Rangbezeichnungen sind überwiegend deutsch. Neue Portal-/Modulbefehle bleiben in beiden Sprachen Englisch. Neue Spieler beginnen am geschützten Hafen bei **−479,5 / 114 / −346,5**. Survival umfasst 20.480 × 20.480 Blöcke. Gegenstände fallen beim Tod herunter; Client-Mods sind nicht nötig.

## Atlas und Abenteuer

Beim ersten Survivalbesuch erhältst du einen Atlas. `/atlas` gibt bei freiem Inventarplatz Ersatz. Benutzen/Rechtsklick wechselt Welt-, Regions- und Nahansicht. 55 Orte sind eingetragen; Bedrock zeigt keine Ortsnamen an Markierungen.

Die Welt enthält 16 Dörfer mit 64 Häusern: neun mit normalen Plündererüberfällen und sieben geschützte Dörfer. Dorfbewohner können in Überfalldörfern sterben. Überfälle laufen nicht endlos automatisch. Es gibt acht Händlerlager sowie je sechs Hexenlager, Piratenlager und Bossarenen. Das Anfängergebiet schützt vor feindlichen Kreaturen.

## Rangliste

Am Survival-Spawn schwebt eine farbige Top-10-Rangliste nach Abenteuerpunkten.
Sie wird alle fünf Sekunden aktualisiert und berücksichtigt auch Offline-Spieler.
Mit **`/rangliste`** oder **`/leaderboard`** zeigt jeder Spieler dieselbe Top 10
und den eigenen Platz im Chat, auch in anderen Welten. **`/trigger rang`** zeigt
weiterhin den persönlichen Rang und die fehlenden Punkte zum nächsten Rang.

## Dinos

ArkDinos 1.2.1 hat acht eigene Arten: T-Rex, Triceratops, Stegosaurus, Brachiosaurus, Velociraptor, Pteranodon, Dilophosaurus und den wilden Mosasaurus. Nimm die Server-Ressourcenpakete auf Java und Bedrock an. Wilde Tiere können in geladenen, geeigneten Gebieten der eingerichteten Dino-Welten entstehen und ohne festes Heimrevier weiterwandern. In alten Weltständen vorhandene Zäune bleiben echte Hindernisse.

In Dino-Welten zähmst du nach Art mit mehreren Fütterungen und einem Abschlussitem: **T-Rex zwölfmal rohes Fleisch im Abstand von zwanzig Sekunden, dann ein Knochen**; Raptor und Dilo fünfmal Fleisch im Abstand von zehn Sekunden, dann ein Knochen. Für Fisch- und Pflanzenfresser gelten die [vollständigen Futterregeln](Dino-Riding-DE.md). Nur außerhalb der Prototypwelten bleibt das alte Ein-Samen-System aktiv.

**Leere Haupthand + Benutzen** steigt auf ein freies erwachsenes Reittier. **Schleichen + Benutzen** öffnet für den Besitzer das Menü mit Folgen, Bleiben, Freilauf, Reitfreigabe und bestätigtem Freilassen. Standardmäßig reitet nur der Besitzer; „Reiten: öffentlich“ erlaubt auch Gästen das Reiten, ohne ihnen Besitz oder Pflege zu übertragen. Es gibt weiterhin einen Sitz. Sprinten beschleunigt; Schleichen steigt ab. Pteranodon hebt mit Springen ab und fliegt mit zwölf, beim Sprinten mit achtzehn Blöcken pro Sekunde.

Landdinos steigen in freiem Wasser zur Oberfläche, auch mit Reiter, in Bleiben oder bei ausgeloggtem Besitzer. Wilde Jäger greifen Survival-Spieler an. Eigene erwachsene Tiere verteidigen ihren Besitzer und sich selbst; Bleiben pausiert automatische Kämpfe. Beim Reiten löst die Angriffstaste einen gezielten Angriff aus. Eigentümer und gezähmte Haustiere sind geschützt, gegen andere Spieler gelten die Server-Schutzregeln. Tiere laden keine entfernten Chunks und teleportieren sich nicht hinterher. [Reitregeln und Grenzen](Dino-Riding-DE.md).

## Grundstücke, Missionen und Kreativ

Wähle mit einer Goldschaufel gegenüberliegende Grundstücksecken; ein Stock zeigt vorhandene Grundstücke. Befehle: `/claimslist`, `/trust Name`, `/untrust Name`, `/trustlist`, `/abandonclaim`. Anfangs gibt es 1.000 Anspruchsblöcke, weitere 100 je aktiver Stunde bis 10.000. Öffentliche Bauten haben getrennten Schutz.

`/quests list` zeigt verfügbare Missionen. Die Nummern sind nur Listenplätze, keine Quest-IDs: `/quests take` erwartet den **angezeigten Namen**, zum Beispiel `/quests take Nachtwache`, nicht `/quests take 7`. Nachtwache setzt weiterhin Material fürs Lager voraus. Beispiel: `/quests take Werkzeugmacher`, danach mit `Ja` bestätigen. Gruppen: `/party create Name`, `/party invite Name`, `/party accept`. Jeder muss dieselbe Mission selbst annehmen. Kreativaktionen zählen nicht. `/trigger rang` zeigt die bestehenden Abenteuerpunkte des Welt-Datapacks; Ränge verleihen keine Kampfboni.

Das Spawnportal führt zur getrennten flachen Kreativwelt und zurück. Inventar, Rüstung, Nebenhand, Endertruhe und Erfahrung bleiben in ihrer Weltgruppe. Fahrzeuge, Kreaturen und gedroppte Gegenstände nutzen die Portale nicht. Der Ankunftspavillon ist geschützt; außerhalb darf gebaut werden.

## Portal, Konto und optionale Module

- `/web`: Einmalcode für die gemeinsame Anmeldung unter `/`; bestehende AuthMe-Benutzer verwenden auch direkt ihr Serverpasswort.
- `/account`: private gemeinsame Passwortfelder ohne Passwortargumente.
- `/web admin`: alter Alias für einen gewöhnlichen `/web`-Code; aktuelle Server-OP-Rechte bestimmen den Verwaltungszugriff.
- `/wardrobe list` und `/wardrobe wear <name>`: eigene Skins mit ArkWardrobe.
- `/support [low|normal|high] Message`: private Support-Anfrage mit ArkSupport.

[Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE), [Garderobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE), [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE) und [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE) erklären Berechtigungen und Grenzen. Fehlende optionale Module ändern nicht dein Kontopasswort oder Profil.

### Eigene Dinos heilen und finden

Füttere ein verletztes eigenes Tier im Survival mit seinem normalen Lieblingsfutter: Das heilt bis zu zwei Herzen alle zwei Sekunden, bevor Wachstum oder Paarung ausgelöst werden. `/dinos` zeigt ausschließlich eigene gezähmte Tiere. Halte einen normalen Kompass in Haupt- oder Nebenhand und klicke auf **[Kompass]**, um ein Tier auszuwählen. Der Kompass folgt geladenen Tieren und zeigt sonst auf die zuletzt erfasste Position. Bei einem Ziel in einer anderen Welt wechsle dorthin und wähle es erneut. Schon vor dem Update entladene Tiere werden beim nächsten Laden ihrer Umgebung erfasst.

Der T-Rex verursacht auf Normal ohne Rüstung fünf Herzen Schaden. Nach angenommenem Zähmfutter greift er den fütternden Spieler während des gültigen Zähmfortschritts nicht an; ein eigener positiver Treffer oder ein eigener Pfeiltreffer beendet diesen Schutz und setzt den Fortschritt zurück. Zwölf Fütterungen mit 20 Sekunden Abstand und anschließend ein Knochen bleiben erforderlich. Ein erwachsener eigener Pteranodon im Folgen-Modus kann den Besitzer beim Flug auf einem zweiten eigenen Pteranodon begleiten.

Wilde Pteranodons fliegen bei freiem, geladenem Luftraum zwischen Landestellen. Halte rohen Kabeljau, Lachs oder Tropenfisch in einer Hand, um ein nahes Tier in Sichtweite zur Landung anzulocken. Sie sind friedlich; ein tatsächlicher Treffer veranlasst sie zu landen und sich am Boden gegen den Angreifer zu wehren. Ein Treffer hält sie vorübergehend am Boden.
