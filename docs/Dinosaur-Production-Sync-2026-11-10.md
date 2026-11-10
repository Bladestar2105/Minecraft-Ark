# Produktionsnachtrag und Mosasaurus-Uferfix – 10. November 2026

Dieser Nachtrag ergänzt den [ursprünglichen Abschlussbericht](Dinosaur-Final-Verification-2026-11-10.md). Der Nutzer hat anschließend die Live-Installation ausdrücklich beauftragt, ohne vorheriges Backup, sowie Sichtweite 14 und die Übernahme des neueren Live-Stands nach GitHub. Es wird kein Merge oder Release durchgeführt. Die Geräte- und Darstellungslücken des ursprünglichen Berichts bleiben offen.

## Reproduzierter Mosasaurus-Fehler

**Fehler behoben und erneut geprüft:** Ein großer Mosasaurus konnte an einem sicheren Wasserrand stecken bleiben. Die native KI schlug eine Drehung vor, bei der der lange Modellkörper das Wasser verlassen hätte. Die korrekte Sicherheitsprüfung lehnte dies ab; der bisherige Rückzug versuchte nur die Gegenrichtung des Vorschlags oder vertikale Bewegung. Eine tatsächlich sichere horizontale Richtung wurde nicht genutzt und die sofort wieder aktive native Lenkung störte den Rückzug.

Der Controller prüft jetzt zusätzlich vorwärts, rückwärts und seitwärts mit derselben unveränderten Modell-/Wasserprüfung. Er hält eine sichere Richtung höchstens 40 Ticks, unterdrückt währenddessen die native Lenkung und aktiviert sie anschließend wieder. Jeder weitere Schritt wird geprüft; keine Teleports, zusätzlichen Chunkladungen oder Immunitäten. Ein Breach verwirft den Rückzugszustand.

Die verschärfte Regression stellt die konkrete Position und Blickrichtung wieder her, entfernt ausschließlich den zufälligen Wandergoal der Testentität und verwendet echte native Wegfindung. Das alte JAR `96d78c…` erzielt 0 Blöcke nettoeinwärts und 0 Fortschritt nach erneutem Wegbefehl. Das neue JAR `6bb0c5…` erzielt im fokussierten Nachtest 4,4986 Blöcke nettoeinwärts und 10,219 Blöcke Zielannäherung. UUID, 200 HP, vollständig nasser Träger, sichere Modellposition und null Force-Tickets bleiben erhalten. Bloßes Zittern oder vertikales Pendeln genügt nicht.

Das ist ein Bewegungsfix. Der ursprüngliche Kieferhinweis bleibt entsprechend den echten Java-Aufnahmen im [Clientmanifest](verification/2026-11-10-rendering/client-render-manifest.json) bewertet: Kiefer in den aufgenommenen Zuständen verbunden, ursprünglicher angeschnittener Zustand unbekannt. Es wird keine unbewiesene zusätzliche Kieferursache behauptet.

## Live-Abhängigkeiten nach GitHub übernommen

Der nächtliche Hostprozess hatte einen neueren Serverstand erzeugt, ohne die Repository-Pins nachzuführen. Der Abgleich übernimmt die vorhandenen neueren Versionen; die Live-Abhängigkeiten werden nicht zurückgesetzt:

- Java 25, Paper 26.3 Build 246, BetterModel 3.6.0, Geyser 2.12.1-b1298.
- Bridge 1.0.9 mit dem bereits live verwendeten Task-Publication-Patch, SHA-256 `ec18241f9a6be0cd1dfa05664cbfb27fac8c075a641973131d22e83ae1db61d8`.
- WorldEdit 7.4.6, WorldGuard 7.0.21 und GriefPrevention 16.18.8.

Die 21 verwalteten Runtime-Einträge stimmen mit den beobachteten Livebytes überein. Schutzplugin-Versionen, immutable Downloadadressen und Prüfsummen wurden gemeinsam aktualisiert und frisch heruntergeladen. Die Bridge wird aus dem unveränderten, gehashten Upstreamartefakt mit dem vorhandenen Transformer reproduziert und nochmals auf den erwarteten Outputhash geprüft. Das Binärarchiv enthält sie separat neben den sieben Projektplugins samt MIT-Lizenz; damit braucht die normale Installation kein Host-JDK. Die Quellinstallation benötigt weiterhin JDK 25. AuthMe und Chunky bleiben als vorhandene Legacyplugins inventarisiert und werden durch Fresh-Setup nicht neu konfiguriert. Öffentliche Versionen/Hashes stehen in [dependencies-production.json](../dependencies-production.json); Zugangsdaten und Hostkonfiguration werden nicht übernommen.

## Präzisierte Testfixtures

Die native Pferde-KI heilt zufällig 1 HP mit REGEN auch bei NoAI und deaktivierter Spielerregeneration. Das verursachte eine falsche Schutzhaustier-Schadensmeldung 499→500. Ausschließlich explizit markierte Fixturehaustiere unterdrücken jetzt REGEN. Die Regression verwendet 16 markierte und 16 unmarkierte echte Pferde, beobachtet native Heilereignisse und prüft weiterhin exakte HP: markierte bleiben 499, Kontrollen heilen tatsächlich auf 500. Der rote Vorlauf wurde erhalten. Produktions-Kampfregeln wurden nicht geändert.

Ein enger Wasserpool erzeugte außerdem eine falsche Meldung „nur vertikales Setzen“: 18,63 Blöcke horizontale Strecke und 3,35 Blöcke X-Auslenkung, jedoch nach fünf Sekunden wieder nahe am Start. Die Assertion misst jetzt horizontale Strecke und zusätzliche räumliche Auslenkung über 1 Block gegenüber dem Zustand vor dem Annähern. Die ursprüngliche 3D-Streckenprüfung bleibt bestehen. Die separate Eckregression verlangt weiterhin gerichteten Nettofortschritt. Der ursprüngliche rote Lauf und der neue vollständige Aquaticlauf werden getrennt zugeordnet.

## Abschließende Nachweise

**Bestanden:** 129/129 native Prüfgruppen auf JAR `6bb0c5efce44532ea66c18e3cfbffa1c973c893d3fcfbd93d29a5115f47df970`, null übersprungene Gruppen: Pflege 19, Kampf 36, Steuerung 17, Zweitreiter 19, Wildlife 6, Schwimmen 7, Mosasaurus 21, Tempo 1, Flug 3. Die Pflegeprüfung verwendet die echten Fütterungsfristen. Der separate vollständige 21-Gruppen-Aquaticnachlauf ersetzt die gesamte Aquatic-Teilsuite des ursprünglichen Gesamtlaufs. Der ursprüngliche Rohbericht bleibt mit neun bestandenen Gruppen, einer falsch gemeldeten Poolgruppe und elf nicht erreichten Gruppen rot erhalten.

**Bestanden:** vollständiger echter Modellstack 6/6 einschließlich WorldEdit/WorldGuard/GriefPrevention, aller acht Dino- und sieben Eiermodelle, tatsächlicher Bedrock-Registries, vier nativer Sitzpaare, realer Modellneuaufbauten und sauberem Neustart mit Wachstums-/Fallbackprüfung. Serverseitige Metadaten belegen kein Bedrock-Rendering.

**Bestanden:** Build aller sieben Projektplugins, 45 Skript-/Distributionstests, 38 Modelltests und 20 Weltgeneratortests. Der unabhängige Ponytail-Code-Nachreview hat keine offenen materiellen Befunde. Der getrennte Neubau aus dem entpackten Prüfarchiv erzeugt alle sieben JARs bytegleich. Die echte Standardinstallation aus diesem Binärarchiv funktioniert mit leerem PATH und ohne JAVA_HOME: sechs standardmäßig ausgewählte Projektplugins, 21 Fremdabhängigkeiten und 77 Assets geprüft; eine manipulierte Bridge wird abgewiesen. Die Ridingprobe mit acht Prüfgruppen aus dem Quellarchiv besteht auf Paper 246; ihr synthetisches Unloadevent bleibt separat vom echten Lifecycle-Nachweis ausgewiesen. Das Prüfarchiv vor den abschließenden reinen Dokumentationsänderungen hat einen eigenen Hash und wird nicht mit der finalen Übergabe verwechselt.

**Live-Installation: Bestanden.** Codecommit `e859534570da549632f3449c12a3a5aa33c1beb8`, ArkDinos 1.2.0 mit SHA-256 `6bb0c5efce44532ea66c18e3cfbffa1c973c893d3fcfbd93d29a5115f47df970` ist produktiv installiert. Der Minecraft-Container startete am 10.11.2026 um 16:59:59 UTC neu und meldet healthy, Sichtweite 14/Simulationsdistanz 4 und null Startfehler. Vier Dimensionen wurden regulär gespeichert und ihre I/O-Scheduler beendet. Es wurden ausschließlich die JAR-Bytes dieses zweiten Updates atomar ersetzt; 24 andere Plugin-JARs, Dino-Konfiguration und server.properties blieben bytegleich. Panel, DNS und Spielerverzeichnis liefen mit unveränderten Startzeiten weiter. Es wurde ausdrücklich kein Backup erstellt. Künftige nötige Neustarts werden bei Online-Spielern vorher im Spielchat angekündigt.

Das weiterhin öffentlich mit HTTP 200 ausgelieferte Java-Pack hat SHA-256 `eb579657fb0f8f16e6ced972e89239ccb1a90050ece63d45be8a87fa1e03742f`: seine 3025 Einträge sind alle bytegleich zum finalen QA-Pack. Das nach dem Live-Neustart neu erzeugte Bedrock-Pack hat SHA-256 `fa7add3a943a1d23b8129b36ac391ee8c074afbc5d534739debc99b4f3dadd97`; die strikte Quellprüfung besteht für 15 Modelle/30 Geometrien/122 Dateien. Die QA-Pakete haben eigene eindeutig zugeordnete Containerhashes; Bridge-UUIDs und zusätzliche Fixtureassets erklären unterschiedliche ZIP-Fingerprints. Die Inventareier bleiben SHA-256 `6a620a56e7bae1e536844990abc7588b586303b78aa1d13b34f39b9b1eef0f67`. Der reine Bewegungsfix ändert keine Modellinputs und erfordert keinen weiteren Paketwechsel.

Alle aktuellen Einzelprüfungsnamen, Skript-/Artefakthashes, roten/grünen Eckproben und der Livebeleg stehen im [Prüfmanifest](verification/2026-11-10-production-sync.json). Der [öffentliche 21-Pin-Abgleich](verification/2026-11-10-dependency-audit.json) enthält ausschließlich Softwaremetadaten. Rohberichte bleiben unter dem ausdrücklich lokalen Ausgabeverzeichnis; ihre Hashes sind im Manifest enthalten. Dokumentationscommits nach dem Codecommit ändern das geprüfte JAR nicht. Die CI des tatsächlichen abschließenden PR-Heads wird im PR nach Abschluss verlinkt.

## Aktuelle Matrix der neun Nutzerpunkte

| Nutzerpunkt | Ergebnis dieses Kandidaten | Nachweis und Grenze |
| --- | --- | --- |
| Freie Begegnungen | **Bestanden (Server)** | Wildlife 6: geladenes Gelände, Budgets, Schutzplugins/Ausfall, persistente Tiere und Neustart. Kein neuer Zauneingriff. |
| Tempo/Flug | **Bestanden (Server)** | Tempo 1 mit sieben Arten und Flug 3; tatsächliche Nativebewegung. Vollständige sichtbare Bedienmatrix offen. |
| Kampf | **Bestanden (Server)** | Kampf 36 inklusive strenger Schutzhaustierprüfung und zusätzlicher nativer REGEN-Gegenprobe; keine Mitfahrersteuerung. |
| Öffentliche Freigabe | **Bestanden (Server)** | Steuerung 17 und Zweitreiter 19 einschließlich Persistenz, Fahrerwechsel und Widerruf beider Besitzerrollen. |
| Zwei Reiter | **Bestanden (Server)** | Zweitreiter 19 mit echtem Lifecycle/Unload und ungeladener Grenze; Fullstack 4 Sitzpaare samt realem Modellneuaufbau. Historische Java-Standbilder bleiben ihrem ursprünglichen Build zugeordnet. |
| Proportionen/Wachstum | **Bestanden (Exporte/Server)** | Modelle 38 und vollständiger Modellstack; Größen unverändert. Vollständige sichtbare Wachstumsabnahme: **Nicht geprüft**. |
| Oberflächen-Schwimmen | **Bestanden (Server)** | Schwimmen 7 mit sieben Land-/Flugarten; Mosasaurus separat 21 einschließlich Ecke, Breaches und Restart. Vollständige grafische Wasser-/Kopfraummatrix: **Nicht geprüft**. |
| Modelle/Animationen | **Fehler behoben und erneut geprüft; Darstellung teilweise offen** | Vorheriger Mosa-Exportfix ausgeliefert; Fullstack 6 und echte Registry-/Paketprüfung. Kiefer in vorhandenen Java-Seitenbildern verbunden; Bedrock/PS5 und ursprünglicher unbekannter Bildzustand: **Blockiert/Nicht geprüft**. |
| T-Rex-Zähmung/Schaden | **Bestanden (Server)** | Pflege 19 mit zwölf Fleischgaben im Abstand von jeweils mindestens 20 Sekunden, anschließend Knochen; elf gemessene Abstände und insgesamt 222,904 Sekunden. Kampf 36 mit 20 Basisschaden sowie Schutz-/Effektregeln. |

Die detaillierten Szenarien und neun echten Java-Screenshots aus dem ursprünglichen Bericht bleiben gültige historische Belege; sie werden nicht auf den neuen Code-/Abhängigkeitsstand umetikettiert. Kein Bedrock-Client oder PS5-Gerät wurde verfügbar. Die noch offenen Kamerabewegungen, niedrigen Decken, Wachstums-/Betrachterkombinationen sowie die Brachiosaurus-Halsverdeckung bleiben dort ausdrücklich beschrieben. **Keine vollständige Freigabereife.**

## Reproduzierbare Aufrufe

Die Java 25-Installation, ein zu Paper 246 passender lokaler Paperclipcache (`libraries`, `versions`, `cache`, `paper.jar`) und die exakt gepinnten Plugin-JARs müssen vorhanden sein. `DINO_PAPER_CACHE` und die folgenden Variablen bezeichnen absolute lokale Pfade; keine Livewelt wird geöffnet. Die Skripte ermitteln ihre Parameter aus diesen Variablen, erzeugen jeweils eigene Wegwerfwelten und melden ausgeführte Prüfgruppen.

```sh
rtk proxy python3 scripts/build_plugins.py
rtk proxy python3 -m unittest discover -s scripts -p 'test_*.py'
rtk proxy python3 -m unittest discover -s dino-models -p 'test_*.py'
rtk proxy python3 -m unittest discover -s world-design -p 'test_*.py'

# JAVA_HOME, DINO_PAPER_CACHE und DINO_PLUGIN_JAR zuvor absolut setzen.
export DINO_PAPER_JAR="$DINO_PAPER_CACHE/paper.jar"
export DINO_WORLDEDIT_JAR="$DINO_PAPER_CACHE/plugins/worldedit-bukkit-7.4.6.jar"
export DINO_WORLDGUARD_JAR="$DINO_PAPER_CACHE/plugins/worldguard-bukkit-7.0.21.jar"
export DINO_GRIEFPREVENTION_JAR="$DINO_PAPER_CACHE/plugins/GriefPrevention.jar"
rtk proxy node dino-plugin/care-runtime-test.mjs
rtk proxy node dino-plugin/combat-runtime-test.mjs
rtk proxy node dino-plugin/swimming-runtime-test.mjs
rtk proxy node dino-plugin/aquatic-runtime-test.mjs
rtk proxy node dino-plugin/controls-runtime-test.mjs
rtk proxy node dino-plugin/second-rider-runtime-test.mjs
rtk proxy node dino-plugin/wildlife-runtime-test.mjs
rtk proxy env DINO_SPEED_ONLY=1 node dino-plugin/controls-runtime-test.mjs
rtk proxy env DINO_FLIGHT_SPEED_ONLY=1 node dino-plugin/controls-runtime-test.mjs

rtk proxy env DINO_MODEL_GAMEPLAY=1 DINO_MODEL_STACK_CACHE="$DINO_PAPER_CACHE" \
  DINO_GME_JAR="$DINO_PAPER_CACHE/plugins/GeyserModelEngine-1.0.9.jar" \
  node dino-plugin/model-runtime-test.mjs
rtk proxy python3 dino-models/verify_bedrock_pack.py /absolute/path/to/generated_pack.zip
```

Für voneinander getrennte Berichte sind zusätzlich die jeweiligen `DINO_*_OUTPUT`-Variablen zu setzen. Der Modelllauf benötigt BetterModel, PacketEvents, Geyser, die gepatchte Bridge samt Extensions und GeyserUtils im selben Cache sowie Standard-TLS-Zugriff auf Microsoft-Discovery. Es wurden keine Sicherheitsfunktionen deaktiviert. Ein fehlerhafter Cacheaufbau ohne Assertions zählt nicht als bestandene Integration.
