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

**Bestanden:** 129/129 native Prüfgruppen auf JAR `6bb0c5efce44532ea66c18e3cfbffa1c973c893d3fcfbd93d29a5115f47df970`, null übersprungene Gruppen: Pflege 19, Kampf 36, Steuerung 17, Zweitreiter 19, Wildlife 6, Schwimmen 7, Mosasaurus 21, Tempo 1, Flug 3. Die Pflegeprüfung verwendet die echten Fütterungsfristen. Der separate vollständige 21-Gruppen-Aquaticnachlauf ersetzt ausdrücklich nur die falsch gemeldete Poolgruppe des ursprünglichen Gesamtlaufs; dessen Rohbericht bleibt rot erhalten.

**Bestanden:** vollständiger echter Modellstack 6/6 einschließlich WorldEdit/WorldGuard/GriefPrevention, aller acht Dino- und sieben Eiermodelle, tatsächlicher Bedrock-Registries, vier nativer Sitzpaare, realer Modellneuaufbauten und sauberem Neustart mit Wachstums-/Fallbackprüfung. Serverseitige Metadaten belegen kein Bedrock-Rendering.

**Bestanden:** Build aller sieben Projektplugins, 45 Skript-/Distributionstests, 38 Modelltests und 20 Weltgeneratortests. Der unabhängige Ponytail-Code-Nachreview hat keine offenen materiellen Befunde.

Die erste Live-Installation mit JAR `96d78c…` und Sichtweite 14 ist bestätigt; das zusätzliche, oben geprüfte Mosasaurus-JAR wird anschließend separat installiert und sein Ergebnis ergänzt. Kein Merge/Release und keine vollständige Gerätefreigabe.
