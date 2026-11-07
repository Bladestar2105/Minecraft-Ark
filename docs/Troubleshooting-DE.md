# Fehlerbehebung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE)

**Neues Setup lehnt `.env`, `data`, `secrets` oder `panel-state` ab:** Bestehende Installationen werden absichtlich nicht überschrieben. Nutze ein neues Verzeichnis. Lasse `.env`/Geheimnisse vom Setup erzeugen und bearbeite sie anschließend. Sichere vorhandene Server privat; lösche ihren Zustand nicht, damit das Setup durchläuft.

**Weltdownload/Prüfsumme fehlerhaft:** Nutze die festen Teile von [world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download-DE) und `world-release.json`, nicht den neuesten Anwendungsrelease. Fehlende/geänderte Hashes stoppen die Installation. Umgehe die Prüfung nicht und entpacke keine unvollständigen Downloads.

**Bau schlägt fehl:** Quellcodebau benötigt JDK 25. Binäre All-in-One-Pakete enthalten bereits Projekt-JARs und brauchen kein Host-JDK. Prüfe den Zugriff auf die exakt festgelegten Quellen; ersetze keine Datei durch ein ungeprüftes JAR. Melde geänderte Fremddownloads mit Name, Version und Hash der Abhängigkeit.

**Server startet nicht:** Prüfe EULA-Zustimmung, Speicherplatz und `docker compose logs minecraft`. Verwende festgelegtes Minecraft/Paper 26.3 und den Java-25-Container. Führe kein Welt-Downgrade aus. Erste Abhängigkeitsdownloads können mehrere Minuten dauern.

**Datapacks oder Kreativtrennung fehlen:** Halte den Server geschlossen. Prüfe Dungeons-and-Taverns-/Geophilic-Pakete, alle drei Multiverse-Erweiterungen und den vollständigen Welt-/Dimensionsbaum. Stelle ein privates Backup wieder her, statt ähnliche Ersatzwelten anzulegen oder Inventargruppen zu überschreiben.

**Spieleranmeldung abgelehnt:** Bestehende AuthMe-Spieler nutzen direkt ihren Servernamen und ihr Passwort. Automatisch angemeldete Spieler führen nach der Spielanmeldung `/web` aus. Codes sind kurzlebig und bereichsgebunden; `/web admin` ist für die getrennte Anmeldung tatsächlicher OPs. Ein unbekanntes/nicht zugeordnetes Konto entsteht nicht allein durch Namenseingabe. Siehe [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE).

**OP-Verwaltungszugriff funktioniert nicht mehr:** Tatsächliche OP-Rechte, Kontoverwaltung und das feste Achtstundenlimit werden geprüft. De-Op oder Dienstausfall verhindern den Zugriff. Der getrennte lokale Paneladministrator bleibt für Reparaturen nutzbar. Ein Spielercookie ist kein Verwaltungszugang.

**Skins oder Support fehlen:** Prüfe die installierten [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE). Accounts allein stellt weiterhin Profil/Konto und administrative Passwortzurücksetzungen bereit. Eine fehlgeschlagene Modulabfrage ist ein Erreichbarkeitsfehler: Wiederhole über Status-/Profilaktualisierung. Bei Aktivierungsproblemen hilft [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE); eine Warteschlange bedeutet noch keinen Skinwechsel.

**Supportkonflikt/geschlossen/Grenze:** Aktualisiere und lies neue Antworten vor einem weiteren Versuch. Entwurfstext bleibt bei Konflikten erhalten. Geschlossene Tickets benötigen Wiedereröffnung durch die Verwaltung; Wartezeit, offene Tickets, Nachrichten und Speicher sind bewusst begrenzt. Siehe [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE).

**Bedrock-Verbindung oder Steuerung problematisch:** Prüfe Geyser/Floodgate, Port 19132, Firewall und Clientkompatibilität. Behalte die Java-Onlineprüfung bei. Manche Konsolen benötigen einen getrennten Verbindungsweg; öffentlicher DNS/AdGuard wird nicht mitgeliefert. Echte Konsolensteuerung, Dialoge und Skinanzeige benötigen Gerätetests.

**Panel 400/403 oder Speichern nicht möglich:** Prüfe `PANEL_TRUSTED_HOSTS`, die richtige Anmelderolle, UID-1000-Zugriff auf `data`/`panel-state` und die Docker-Socket-Gruppe. Ohne eigenen AdGuard ist die DNS-Seite nicht verfügbar. BlueMap erstellt seinen Cache aus gespeicherten Chunks neu; prüfe den Fortschritt.

Nenne in Fehlerberichten Versionen, relevante Fehlermeldung und Reproduktionsschritte. Entferne Passwörter, Tokens, Wiederherstellungscodes, Spielerprofile, private Adressen und unbeteiligte private Logs. [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE) erklärt Backup-/Veröffentlichungsgrenzen.
