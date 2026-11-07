# Module

[English](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Modules) · [Installation](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Installation-DE) · [All-in-One](https://github.com/Bladestar2105/Minecraft-Ark/wiki/All-in-One-DE)

Das Paket enthält sieben getrennt gebaute Projektplugins. Der neue Standardstack installiert sechs; **ArkAccessBridge ist ausgenommen**.

| CLI-Name | Plugin | Aufgabe | Abhängigkeit |
| --- | --- | --- | --- |
| `accounts` | ArkAccounts | Gemeinsame Passwörter, Wiederherstellung, Profil/Statistiken, gemeinsame Spieleranmeldung, aktuelle OP-Prüfung und private API | Grundlage für Wardrobe/Support |
| `wardrobe` | ArkWardrobe | Eigene Skins, Editorablage, Aktivierung und Signierung | ArkAccounts; SkinsRestorer |
| `support` | ArkSupport | Dauerhafte Anfragen, Antworten und Hinweise im Spiel | ArkAccounts |
| `dino` | ArkDinos | Zähmen, Folgen und Reiten durch den Besitzer | Getrenntes Spielplugin |
| `atlas` | ArkAtlas | Atlas im Spiel | Getrenntes Spielplugin |
| `panel` | ArkPanelBridge | Laufzeitdaten und Verwaltungsbrücke | Getrenntes Verwaltungsplugin |
| `access` | ArkAccessBridge | Integration bestehender Offline-/AuthMe-Installationen (1.1.1) | Getrennt geprüfte Migration; kein Standard im neuen Stack |

ArkAccounts verwaltet die Kontodaten und den privaten Dienst auf Port 8101. Wardrobe und Support registrieren dort optionale Funktionen; sie ersetzen keine Kontoverwaltung und öffnen keinen weiteren öffentlichen Port. Ist AuthMe installiert, bleiben dessen bestehende Passwortprüfungen maßgeblich. Der neue Online-Mode-Stack benötigt kein AuthMe.

## Module für ein neues Setup auswählen

Bei geprüften vorgebauten JARs werden die Namen durch Kommas getrennt:

```sh
python3 scripts/setup.py --modules dino,atlas,panel,accounts,support
```

Dieses Beispiel behält Support und lässt Wardrobe weg. `wardrobe` oder `support` ohne `accounts` wird abgelehnt. Führe das Setup in einem neuen Verzeichnis aus, bevor `.env`, `data`, `secrets` oder `panel-state` angelegt werden. Beim Quellcodebau zuerst `python3 scripts/build_plugins.py` ausführen. Die Auswahl bestimmt die Installation; das ZIP enthält trotzdem alle sieben JARs.

Der Browser fragt die tatsächlich verfügbaren Serverfunktionen ab. Ohne Wardrobe werden Skins und Signierung ausgeblendet, ohne Support die Anfragen. Profil/Konto und Passwortzurücksetzungen der Verwaltung unter **Spieler** gehören zu Accounts und bleiben verfügbar. Eine fehlgeschlagene Modulabfrage zeigt eine Fehlermeldung mit Wiederholungsmöglichkeit statt eine erfolgreiche Installation zu behaupten.

Änderungen an bestehenden Installationen benötigen einen gestoppten Server und ein privates Backup. Das neue Setup ist kein Updater. Bewahre die Daten jedes Moduls auf; ein entferntes JAR ist keine Freigabe zum Löschen von Konten, Skins oder Tickets. Siehe [Sicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Security-DE), [Kontosicherheit](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE), [Garderobe](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Wardrobe-DE) und [Support](https://github.com/Bladestar2105/Minecraft-Ark/wiki/Support-DE).
