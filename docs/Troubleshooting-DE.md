# Fehlerbehebung

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE)

**Neues Setup lehnt `.env`, `data`, `secrets` oder `panel-state` ab:** Bestehende Installationen werden absichtlich nicht überschrieben. Nutze ein neues Verzeichnis. Lasse `.env`/Geheimnisse vom Setup erzeugen und bearbeite sie anschließend. Sichere vorhandene Server privat; lösche ihren Zustand nicht, damit das Setup durchläuft.

**Weltdownload/Prüfsumme fehlerhaft:** Nutze die festen Teile von [world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download-DE) und `world-release.json`, nicht den neuesten Anwendungsrelease. Fehlende/geänderte Hashes stoppen die Installation. Umgehe die Prüfung nicht und entpacke keine unvollständigen Downloads.

**Bau schlägt fehl:** Quellcodebau benötigt JDK 25. Binäre All-in-One-Pakete enthalten bereits Projekt-JARs und brauchen kein Host-JDK. Prüfe den Zugriff auf die exakt festgelegten Quellen; ersetze keine Datei durch ein ungeprüftes JAR. Melde geänderte Fremddownloads mit Name, Version und Hash der Abhängigkeit.

**Server startet nicht:** Prüfe EULA-Zustimmung, Speicherplatz und `docker compose logs minecraft`. Verwende festgelegtes Minecraft/Paper 26.3 und den Java-25-Container. Führe kein Welt-Downgrade aus. Erste Abhängigkeitsdownloads können mehrere Minuten dauern.

**Datapacks oder Kreativtrennung fehlen:** Halte den Server geschlossen. Prüfe Dungeons-and-Taverns-/Geophilic-Pakete, alle drei Multiverse-Erweiterungen und den vollständigen Welt-/Dimensionsbaum. Stelle ein privates Backup wieder her, statt ähnliche Ersatzwelten anzulegen oder Inventargruppen zu überschreiben.

**Spieleranmeldung abgelehnt:** Bestehende AuthMe-Spieler nutzen direkt ihren Servernamen und ihr Passwort. Automatisch angemeldete Spieler führen nach der Spielanmeldung `/web` aus. Codes sind kurzlebig und werden in der gemeinsamen Hauptanmeldung eingelöst. `/web admin` ist ein alter Alias für einen gewöhnlichen Code und keine getrennte OP-Anmeldung. Ein unbekanntes/nicht zugeordnetes Konto entsteht nicht allein durch Namenseingabe. Siehe [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE).

**Verwaltungsmenüs verschwinden:** Aktuelle Server-OP-Rechte, Kontoverwaltung und Sitzungsgültigkeit werden geprüft. `/deop` entzieht nur Verwaltung und erhält eine gültige persönliche Sitzung; `/op` stellt die Verwaltung ohne Neuanmeldung wieder her. Ein Ausfall der Kontoverwaltung verhindert spielerbasierte Verwaltung. Der lokale Notfalladministrator nutzt zur Reparatur dasselbe Hauptformular. Nach dem Update kann einmaliges Neuanmelden nötig sein, da Spielercookies jetzt unter `/` gelten.

**Skins oder Support fehlen:** Prüfe die installierten [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE). Accounts allein stellt weiterhin Profil/Konto und administrative Passwortzurücksetzungen bereit. Eine fehlgeschlagene Modulabfrage ist ein Erreichbarkeitsfehler: Wiederhole über Status-/Profilaktualisierung. Bei Aktivierungsproblemen hilft [Skin-Signierung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE); eine Warteschlange bedeutet noch keinen Skinwechsel.

**HTTP429:** Beachte `Retry-After` und beende automatische Wiederholungsschleifen. API-Budgets gelten je Client-IP und Dienstprozess, ergänzt um native Sitzungs-/UUID-/Schreibgrenzen. Prüfe hinter einem Proxy dessen exakten `FORWARDED_ALLOW_IPS`-Eintrag, bevor Client-IP-Header vertraut werden. Siehe [Anfragelimits](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE#anfragelimits).

**Supportkonflikt/geschlossen/Grenze:** Aktualisiere und lies neue Antworten vor einem weiteren Versuch. Entwurfstext bleibt bei Konflikten erhalten. Geschlossene Tickets benötigen Wiedereröffnung durch die Verwaltung; Wartezeit, offene Tickets, Nachrichten und Speicher sind bewusst begrenzt. Siehe [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE).

**Bedrock-Verbindung oder Steuerung problematisch:** Prüfe Geyser/Floodgate, Port 19132, Firewall und Clientkompatibilität. Behalte die Java-Onlineprüfung bei. Manche Konsolen benötigen einen getrennten Verbindungsweg; öffentlicher DNS/AdGuard wird nicht mitgeliefert. Echte Konsolensteuerung, Dialoge und Skinanzeige benötigen Gerätetests.

**Bedrock-Schlafen oder falsche Position:** Festgelegt ist Geyser **2.12.1-b1295** mit der offiziellen [Korrektur der Teleport-Schleife beim Schlafen](https://github.com/GeyserMC/Geyser/pull/6892) unter Java 26.x. Bestehende Server müssen das ältere Geyser-JAR durch den geprüften Build ersetzen und neu starten; eine geänderte Downloadliste aktualisiert keine bereits installierte Datei. Prüfe nach erneutem Verbinden das Hinlegen und Aufstehen auf der betroffenen Konsole. Damit ist nicht jede mögliche falsche Bettdarstellung als behoben bestätigt.

**Bedrock kann Erde nicht umgraben oder Holz nicht entrinden:** Diese Aktionen verwenden mit Hacke beziehungsweise Axt die Steuerung **Benutzen/Platzieren**, nicht Angreifen/Abbauen. Prüfe außerhalb geschützter Spawn-/Claim-Bereiche und notiere Welt, Koordinaten, Werkzeug, Gegenstand in der Nebenhand und verwendete Taste. Gewöhnliches Abbauen und Werkzeugbenutzung sind unterschiedliche Aktionen. Lasse den Schutz aktiv; ein erfolgreicher Java-OP-Versuch beweist keine Freigabe für normale Spieler. Der offizielle [26.3-Hackenfehler](https://github.com/GeyserMC/Geyser/issues/6753) war bei Vorbereitung dieses Builds noch offen; die Schlafkorrektur behauptet dafür keine Behebung.

**Panel 400/403 oder Speichern nicht möglich:** Prüfe `PANEL_TRUSTED_HOSTS`, die aktuellen Server-OP-Rechte, UID-1000-Zugriff auf `data`/`panel-state` und die Docker-Socket-Gruppe. Ohne eigenen AdGuard ist die DNS-Seite nicht verfügbar. BlueMap erstellt seinen Cache aus gespeicherten Chunks neu; prüfe den Fortschritt.

Nenne in Fehlerberichten Versionen, relevante Fehlermeldung und Reproduktionsschritte. Entferne Passwörter, Tokens, Wiederherstellungscodes, Spielerprofile, private Adressen und unbeteiligte private Logs. [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE) erklärt Backup-/Veröffentlichungsgrenzen.
