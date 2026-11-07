# Installation aus Quellcode

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation) · [Binäres All-in-One-Paket](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE) · [Module](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE)

Für den Quellcodebau werden Docker mit Compose v2, Python 3.10+ und **JDK 25** benötigt. Erlaube Internetzugriff auf die festgelegten Abhängigkeitsquellen. SSD und mindestens 8 GiB Host-RAM sind erste Empfehlungen; zehn Spieler sind eine Voreinstellung und keine geprüfte Kapazitätszusage. Plane Platz für Downloads, Welt, Kartenberechnung und private Backups ein.

## Neuer Quellcode-Checkout

```sh
git clone --branch app-2026-11-07-photo-preview https://github.com/Bladestar2105/Minecraft-Ark.git
cd Minecraft-Ark
# JAVA_HOME auf JDK 25 setzen, falls PATH ein anderes JDK verwendet.
python3 scripts/build_plugins.py
python3 scripts/setup.py
```

Lege `.env`, `data`, `secrets` oder `panel-state` **nicht** vor dem neuen Setup an. Vorhandene Installationen werden abgelehnt. Der Bau erzeugt alle sieben Projekt-JARs und deren Hashmanifest. Das Setup installiert sechs Standardmodule, lädt den festen [Weltstand world-2026-11-06](https://github.com/Bladestar2105/Minecraft-Ark/wiki/World-Download-DE), installiert festgelegte Fremdabhängigkeiten und erzeugt lokale `.env`-/Geheimnisdateien. Über die [Modulauswahl](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules-DE) können Wardrobe oder Support entfallen; beide benötigen Accounts.

Bei einem geprüften Binär-ZIP entfällt der JDK-/Bauschritt; siehe [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE). Das Setup akzeptiert keine EULA und startet keine Dienste.

## Prüfen und starten

Lies die [Minecraft-EULA](https://www.minecraft.net/en-us/eula) und setze erst nach Zustimmung `EULA=TRUE` in der erzeugten `.env`. Prüfe `JAVA_PORT`, `BEDROCK_PORT`, erlaubte Hosts, Compose-Profil und Linux-Rechte nach [Verwaltung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE).

```sh
docker compose config --quiet
docker compose build
docker compose up -d
docker compose logs -f minecraft
docker compose exec minecraft rcon-cli plugins
```

Die erzeugte Konfiguration aktiviert Verwaltung und Spieleranwendung mit `COMPOSE_PROFILES=admin`. Das Panel liegt unter `http://localhost:8090`, das Spielerportal unter `/play/`. Der erste lokale Adminzugang steht in `secrets/panel-login.txt`; lies ihn privat. Mit leerem `COMPOSE_PROFILES` startet nur der Spielserver.

Der neue Stack verwendet Java-`online-mode=true` und Floodgate für Bedrock; AuthMe/ArkAccessBridge werden nicht standardmäßig installiert. Bestehende Offline-/AuthMe-Server benötigen eine getrennte Identitätsmigration. Ändere den Identitätsmodus eines aktiven Servers nicht beiläufig und übernimm keine privaten Kontoverknüpfungen in öffentliche Pakete.

## Vor der Einladung von Spielern

Prüfe Multiverse-Core/-Inventories/-Portals, Schutz, Missionen, Geyser/Floodgate und die gewählten Projektmodule. Bleibt Inventartrennung oder Schutz aus, halte den Server geschlossen. Teste mit einem neuen Nicht-OP-Spieler beide Welten, getrennte Inventare, Bauen außerhalb geschützter Bereiche sowie Atlas, Dino und eine Mission.

Java nutzt standardmäßig TCP 25565. Bedrock nutzt 19132; Compose veröffentlicht TCP und UDP. Leite nur nötige Spielports weiter. Verwaltungs-/API-Ports bleiben intern oder an Loopback. PS5/Switch benötigen einen geeigneten Verbindungsweg; ein öffentlicher DNS-Dienst wird nicht mitgeliefert.

Setze `portal-url` in der erzeugten `data/plugins/ArkAccounts/config.yml` auf die extern erreichbare `/play/`-Adresse. Richte nur mit installiertem Wardrobe einen [eigenen Signierungsanbieter](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Skin-Signing-DE) ein. Prüfe echte Konten und Geräte selbst; lokale Testdienste belegen weder Microsoft-/Minecraft-Zugang noch Bedrock-Abnahme.

Die Welt ist bereits gebaut. Führe keine Build-/Reset-Funktionen aus und ersetze nicht nur `region/`; bewahre den gesamten Dimensionsbaum. Der erste Start lädt gegebenenfalls Serverdateien und kann mehrere Minuten dauern. Siehe [Fehlerbehebung](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Troubleshooting-DE), [Backups](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Administration-DE#private-backups-und-updates) und [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE).

Eine öffentliche/globale Floodgate-Verknüpfung allein öffnet kein bestehendes ungebundenes AuthMe-Konto. Bestätige zuerst das ursprüngliche Java-Passwort oder den Premiumbesitz. Geprüfte lokale gegenseitige Verknüpfungen bleiben über die geprüfte AuthMe-/Floodgate-Integration erhalten; ersetze sie nicht durch eine ungeprüfte globale Abfrage.
