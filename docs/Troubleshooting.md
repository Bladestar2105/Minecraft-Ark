# Probleme lösen

**Server startet nicht:** Erst EULA, freien Speicher und `docker compose logs minecraft`
prüfen. Java 25 und Minecraft/Paper 26.3 verwenden. Keine ältere Serverversion über
die Welt schreiben. Die erste Ausführung lädt Serverdateien und kann länger dauern.

**Build-/Downloadfehler:** Internetzugriff auf offizielle Quellen prüfen. Der Installer
bricht bei fehlenden exakten Versionen oder falschen Prüfsummen ab. Keine ungeprüfte
Ersatz-JAR verwenden. Parties 3.2.18 stammt aus dem versiongeprüften Spiget-Artefakt;
wenn dessen wechselnde CDN-Datei nicht mehr zum Hash passt, die exakte Version über
[Parties](https://alessiodp.com/parties) beschaffen und denselben Hash prüfen.
Das veröffentlichte `dependencies-runtime.json` nennt den erwarteten Wert.

**Datapack fehlt:** Dungeons and Taverns 6.1.0 und Geophilic 3.7 vor dem ersten
Start mit dem Installer beziehen. Sie sind wegen ihrer eigenen Lizenz nicht im
Weltarchiv. Keine Warnung über fehlende Weltgenerierungsdaten ignorieren.

**Kreativtor/Inventartrennung fehlt:** Server für Spieler schließen. Vollständigen
Weltordner und Multiverse-Konfigurationen wiederherstellen. `plugins` in RCON muss
alle drei Multiverse-Erweiterungen aktiv zeigen. Keine neuen Kreativwelten unter
ähnlichen Namen erzeugen und Inventargruppen nicht nachträglich pauschal ersetzen.

**Bedrock kann nicht verbinden:** Geyser/Floodgate-Status, passende Clientversion,
Port 19132 und Firewall prüfen. Java-Onlineprüfung aktiviert lassen. Konsolen bieten
teilweise keine freie Adresseingabe; eigene Konsolenlösung nach offizieller Geyser-
Anleitung wählen. Die interne/öffentliche Serveradresse deiner Umgebung verwenden.

**Panel 400/403:** Über `localhost:8090` öffnen und `PANEL_TRUSTED_HOSTS` kontrollieren.
**Panel kann nichts speichern/Docker nicht erreichen:** UID-1000-Dateirechte und
Docker-Socket-Gruppe prüfen. `/state` muss dauerhaft schreibbar sein.
**DNS-Seite fehlt:** AdGuard ist im Basisstack nicht enthalten.
**Browserkarte leer:** BlueMap rendert die neue lokale Webkarte erst aus den
vorhandenen Chunks; in Logs und dem internen BlueMap-Status den Fortschritt prüfen.

Bei einem Issue Versionen, Fehlermeldung und Schritte angeben. Kennwörter, Schlüssel,
Konten, Spielerprofile, private Adressen und vollständige private Logs vorher entfernen.
