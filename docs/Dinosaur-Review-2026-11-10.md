# Dino-Review und Korrekturen – 10. November 2026

## Ergebnis und Prüfstand

Geprüft wurde der Repository-Stand [`d2acdad87c842dc961c1175bba63ff89396b971a`](https://github.com/Bladestar2105/Minecraft-Ark/commit/d2acdad87c842dc961c1175bba63ff89396b971a), einschließlich der bereits zusammengeführten Dino-Änderungen aus PR 6. Die Korrekturen tragen die unterscheidbare Pluginversion **ArkDinos 1.2.0**. Die bestehenden Welten, Tiere und Besitzer werden nicht durch neue Weltdateien ersetzt.

Es gab reproduzierbare Fehler bei Selbstverteidigung, Haustierschutz, Schwimmen, einer Zähmungsabkürzung direkt beim Spawn und mehreren Modellverbindungen. Weltweite natürliche Begegnungen und eine öffentliche Reitfreigabe fehlten. Die bestehenden Kampf- und Eingabehandler waren im Ausgangsstand vorhanden; die Korrekturen setzen an den reproduzierten Laufzeitfällen an. Die gemeldeten Ausfälle werden an der bestätigten Version 1.1.0 und der freigeschalteten Livewelt bewertet.

**Bestätigte Live-Baseline:** Die gemeldeten Probleme traten laut Betreiber bereits mit **1.1.0** in der Livewelt auf, nachdem die Testweltbegrenzung entfernt wurde. Der anschließende autorisierte Portalzugriff bestätigt `version ArkDinos` = **1.1.0** und `ark` in **beiden** Weltlisten. Ein alter Pluginstand oder eine nicht freigegebene Livewelt erklärt diese Meldungen daher nicht. Das historische Binary-Release [`app-2026-11-07`](https://github.com/Bladestar2105/Minecraft-Ark/releases/tag/app-2026-11-07) enthält zwar **1.0.0** und bleibt eine allgemeine Auslieferungsfalle, ist aber nicht die beobachtete Live-Baseline.

## Die neun gemeldeten Punkte

| Wunsch/Meldung | Befund | Änderung bzw. Grenze |
|---|---|---|
| Überall umherziehende Dinos | Wildes Wandern hatte schon keinen festen Heimatanker, aber die Welt erzeugte Tiere nur an festen Orten; der Generator umzäunte sie. | Begrenzte natürliche Begegnungen in geladenem Oberweltgelände, Auswahl nach Biom, offene neue Lichtungen. Für die alten Gehegezäune gibt es eine gesonderte, begrenzte [Migration](Fence-Migration-DE.md) mit Sicherung und Wiederherstellung. |
| Schnellere Tiere und Flugsaurier | Alte Spielwerte lagen deutlich niedriger, Flug bei 7 Blöcken/s ohne Sprintunterschied. | Artspezifisch höhere Geh-/Lauftempi; Flug 12/18. Native Bewegungsmessungen bestätigen die Werte. |
| Angriffe geritten und ungeritten | Eigener Schaden am gezähmten Tier löste keine verlässliche Verteidigung aus; gewöhnliche gezähmte Haustiere fehlten im Schutzfilter. Größere Sitze und öffentliche Fahrer erfordern zusätzliche Ziel-/Berechtigungslogik. | Eigene Verteidigung, Haustierschutz, tatsächlich berechtigter Fahrer als Angreifer, geschützter Eigentümer/Passagiere, korrigierte Zielstrahlen und Dilo-Mündung. Bleiben pausiert weiterhin automatische Kämpfe. |
| Eigene Tiere öffentlich reitbar | Nur Besitzer durften aufsteigen. | Persistenter Menüschalter; Gäste erhalten Reit- und zugehörige Kampfsteuerung. Pflege, Benennen, Leinen, Verhaltensbefehle und Freilassen bleiben beim Besitzer. Widerruf beendet einen laufenden Gastritt. |
| Zwei Reiter, sofern möglich | Zwei direkte native Passagiere überlagern sich. Eine unabhängig bewegte Sitzentität benötigt eigene Berechtigungen, Kollisionsprüfung und Modellsynchronisierung. | Erster separater Mitfahrplatz für erwachsene T-Rex, Triceratops, Stegosaurus und Brachiosaurus; Fahrer bleibt eindeutig. 13 native Prüfgruppen und getrennte Modell-/Geyser-Paketproben bestanden. Die sichtbare Crossplay-Abnahme bleibt offen. |
| Größere, passende Arten | Rex und Raptor hatten fast dieselbe Modelllänge. | Zentrale Artfaktoren für Rendergröße, Rücken/Sitz und Wachstum; größerer Platzbedarf beim Schlüpfen und Spawnen. Größen sind Spielproportionen, keine wissenschaftliche Rekonstruktion. |
| Ertrinken im Wasser | Gezähmter Sniffer im Bleiben-Modus sank im nativen Basistest bis zum Boden und verlor Luft/Gesundheit. Ravager-Träger werfen außerdem untergetauchte Reiter automatisch ab. | Vom Verhalten unabhängiger Auftrieb für sieben Arten, einschließlich Offline-Besitzer/Bleiben/Reiten. Gezielte Behandlung des nativen Unterwasserabwurfs; Schleichen und Lifecycle-Ausstiege funktionieren. |
| Sitz-/Kieferanimation und andere Verbindungen | Kein `sit`-/`rest`-Clip vorhanden. Nachgewiesen sind getrennte Raptor-Arme, Ptera-Hals/Hüften sowie auseinanderlaufende Kopf-/Kieferuhren nach bestimmten Angriffen. | Nachgewiesene Fehler korrigiert; alle exportierten Clips und Wachstumsstufen erweitert geprüft. Der genaue ursprüngliche „sitzende Kiefer“-Clientfall bleibt visuell zu reproduzieren. |
| Rex schwerer zähmbar und gefährlicher | Fünf Fleischgaben alle zehn Sekunden; zwölf Basisschaden. Zusätzlich erlaubte die erste Spawn-Tick-Lücke Zähmen mit einem Samen. | Zwölf Fleischgaben alle zwanzig Sekunden, dann Knochen; zwanzig Basisschaden. Erste Spawn-Tick-Lücke geschlossen und nativ rot/grün belegt. |

## Konkretes Spielverhalten

### Bewegung

Geschwindigkeiten in Blöcken pro Sekunde. Gehen gilt für normalen Ritt und entspanntes Wandern; das schnellere Tempo für Sprint, schnelles Folgen und Verfolgung. Hindernisse, Kollisionen und Effekte wirken weiterhin.

| Art | Alt gehen/laufen | Neu gehen/laufen | Gemessen ungeritten, schnell |
|---|---:|---:|---:|
| Brachiosaurus | 1 / 2 | 1,4 / 3,5 | 3,501 |
| Stegosaurus | 1 / 2,8 | 1,5 / 4,5 | 4,503 |
| Triceratops | 1,4 / 4,6 | 2 / 7 | 7,007 |
| T-Rex | 1,5 / 6 | 2,5 / 9 | 9,009 |
| Velociraptor | 2 / 7 | 3 / 12 | 12,013 |
| Dilophosaurus | 1,8 / 6 | 2,8 / 10 | 10,010 |
| Pteranodon am Boden | 1,4 / 3 | 2 / 4,5 | 4,503 |

Geritten wurden dieselben Sollwerte gemessen, einschließlich Rückkehr zum Gehen nach Loslassen von Sprint und Stillstand ohne Nachgleiten. Beim Flug ergab der tatsächliche Eingabetest **12 → 18 → 12** Blöcke/s; Schweben, Schleichen zum Absteigen, Landen, Schwerkraft, Gesundheit und Identität blieben erhalten. Die historische JAR scheiterte am gleichen Flugtest mit genau sieben Blöcken/s.

Quellen: [GroundSpeeds](../dino-plugin/src/main/java/de/ark/dinos/GroundSpeeds.java), [FlightRules](../dino-plugin/src/main/java/de/ark/dinos/FlightRules.java), [native Steuerungs-/Tempotests](../dino-plugin/controls-runtime-test.mjs).

### Größen

| Art | Neuer adulter Modellfaktor | Ungefähre adulte Länge | Ungefähre adulte Höhe |
|---|---:|---:|---:|
| T-Rex | 2,10 | 11,96 | 5,28 |
| Triceratops | 1,45 | 7,86 | 3,39 |
| Stegosaurus | 1,55 | 9,22 | 4,29 |
| Brachiosaurus | 2,15 | 14,98 | 12,50 |
| Velociraptor | 1,00 | 5,57 | 2,06 |
| Pteranodon | 1,00 | 5,44 | 2,66 |
| Dilophosaurus | 1,15 | 5,47 | 2,54 |
| Mosasaurus | 1,00 | 24,00 | 5,00 |

Die Abmessungen sind aus Modellgeometrie berechnet und konservativ auf Hundertstel gerundet, in Blöcken. Für Platzierungsprüfungen wird der größere Raum über sämtliche Animationen verwendet. Der Modellursprung liegt nicht in der Mitte von Kopf und Schwanz; deshalb genügt halbe Gesamtlänge als Abstand vom Träger nicht. Wachstum multipliziert die Artgröße genau einmal. Der native Träger bleibt ein kompakter Vanilla-Körper; lange Schwänze und Flügel sind nicht vollständig zusätzliche Land-Kollisionskörper.

Quellen: [DinoSizes](../dino-plugin/src/main/java/de/ark/dinos/DinoSizes.java), [Größenregression](../dino-plugin/src/test/java/de/ark/dinos/DinoSizesTest.java), [Modelladapter](../dino-plugin/src/main/java/de/ark/dinos/BetterModelAdapter.java).

### Reitfreigabe und Kämpfe

Als Besitzer zu Fuß mit leerer Hand schleichen und das Tier benutzen. Der neue Sattelknopf wechselt zwischen **„Reiten: nur Besitzer“** und **„Reiten: öffentlich“**. Bestehende Tiere beginnen privat. Die Einstellung wird gespeichert. Gäste müssen weiterhin im erlaubten Survival-Modus und in derselben Welt sein. Jungtiere bleiben ausgeschlossen; die vier großen Bodenarten erhalten einen zusätzlichen Mitfahrplatz. Der Besitzer kann die Freigabe während eines Gastritts widerrufen.

Bei einem Gastangriff gelten dessen Team-/PvP-/Claim-Berechtigungen. Der eigentliche Eigentümer bleibt unverändert geschützt, auch wenn er offline oder im Kreativmodus ist. Bereits fliegende Dilo-Projektile behalten ihren ursprünglichen Akteur und verlieren ihre Berechtigung bei Fahrerwechsel oder Entzug; ein neuer Fahrer übernimmt keine alten Schüsse.

Selbstverteidigung reagiert auf tatsächlich akzeptierten Schaden. Abgebrochene Treffer und Nullschaden lösen keine Vergeltung aus. Bleiben beendet automatische Verteidigung; der berechtigte Fahrer kann weiterhin ausdrücklich angreifen. Der T-Rex verursacht zwanzig Basisschaden vor Rüstung/Effekten, entsprechend zehn Herzen ohne Minderung. Seine zwölf Fütterungen dauern vom ersten bis zum zwölften erfolgreichen Füttern mindestens **220 Sekunden**, anschließend folgt der Knochen. Ein früher Klick oder falsches Futter verbraucht nichts.

Quellen: [DinoControls](../dino-plugin/src/main/java/de/ark/dinos/DinoControls.java), [DinoCombat](../dino-plugin/src/main/java/de/ark/dinos/DinoCombat.java), [DinoSpit](../dino-plugin/src/main/java/de/ark/dinos/DinoSpit.java), [CareRules](../dino-plugin/src/main/java/de/ark/dinos/CareRules.java).

### Weltpopulation und Wasser

Die neue Population arbeitet ausschließlich in geladenen Oberweltbereichen konfigurierter Prototypwelten. Standardmäßig gelten acht wilde Tiere pro aktivem Survival-Spieler, 64 pro Welt und sechs in einer Umgebung von 96 Blöcken. Alle zehn Sekunden erfolgen höchstens sechzehn Platzierungsversuche und zwei erfolgreiche Spawns je Welt. Benannte WorldGuard-Regionen, GriefPrevention-Claims, fehlender Raum und Weltgrenzen schließen Platzierungen aus. Ein installiertes, aber ausgefallenes Schutzplugin führt zur Ablehnung neuer Spawns. Kein Chunk wird dafür geladen.

Natürliche Begegnungen werden vorübergehend gehalten; entfernte unbenannte Tiere verschwinden und werden nicht als dauerhafte Weltpopulation gespeichert. Zähmen, Benennen oder ein zulässiges Anleinen bewahrt genau dieses Tier. Der Anleinfall wird sofort beim akzeptierten Ereignis gespeichert, nicht erst beim nächsten Zehn-Sekunden-Durchlauf. Alte Tiere, gezähmte Begleiter und Eier werden nicht ersetzt. Neue Generator-Lichtungen sind offen; der Code entfernt keine bestehenden Spielerbauten oder Zäune.

Der Wasserregler benötigt keine aktive KI oder anwesenden Besitzer. Er sucht begrenzt nach Wasseroberflächen und verwendet bei tiefem Wasser Zwischenziele zum Aufsteigen. Er berücksichtigt Wasserpflanzen, wassergefüllte Blöcke, tatsächliche Fluidhöhe, geladene Chunks und den Platz des Reiters. Mosa bleibt unter seinem eigenen Aquatikcontroller. Es gibt keine allgemeine Schadens- oder Ertrinkungsimmunität: Unter einer geschlossenen Decke oder in Lava bleiben Gefahren bestehen.

Paper liefert beim Absteigen keinen Ursachencode. Während vollständigen Untertauchens kann deshalb auch ein administratives `/ride … dismount` ohne Schleichen am gezielten Ravager-Abwurfschutz hängenbleiben. Normale Schleicheingabe, Widerruf, Tod, Moduswechsel, Ausloggen und Plugin-Abschaltung lösen den Ritt weiterhin.

Quellen: [DinoWildSpawns](../dino-plugin/src/main/java/de/ark/dinos/DinoWildSpawns.java), [DinoSwimming](../dino-plugin/src/main/java/de/ark/dinos/DinoSwimming.java), [Wildlife-Test](../dino-plugin/wildlife-runtime-test.mjs), [Schwimmtest](../dino-plugin/swimming-runtime-test.mjs).

## Animationen: was belegt ist

Die Baseline hatte bereits verbundene ideale Kieferposen. Ein blindes Verschieben aller Kiefer wäre deshalb kein begründeter Fix gewesen. Neue Kontaktprüfungen fanden tatsächlich getrennte Raptor-Oberarme im Angriff sowie Lücken an Ptera-Hals und -Hüften; diese Geometrien wurden minimal korrigiert.

Bei Stegosaurus und Brachiosaurus überschrieb der Angriff den Kiefer, aber nicht den Kopf. Eine isolierte Probe mit echten `RenderedBone`-/`AnimationStateHandler`-Klassen aus dem gepinnten BetterModel 3.6.0 bestätigte anschließend verschiedene Animationsuhren: 119 von 120 geprüften Folgebildern wichen ab, maximal 0,5 Grad. Eine explizite ruhende Kopfspur schaltet beide zusammen um; dieselbe Probe zeigte danach 0 von 120 Abweichungen. Das ist begrenzte Engine-/Transformations-Evidenz mit nachgebildeter Anzeigeinterpolation, kein offizieller Minecraft-Clientfilm.

Die dauerhafte Regression prüft Kieferkontakt in allen exportierten Clips und anwendbaren Wachstumsstufen sowie anatomische Verbindungen und gemeinsam umgeschaltete Kopf-/Kieferzweige. Die Modellsuite ist von 35 auf **37 bestandene Tests** erweitert; Exportparität und Gangprüfung decken alle acht Arten ab. Unabhängige Bounds-Proben umfassten über 17 Millionen transformierte Eckpunkte. Ein `sit`-/`rest`-Clip existiert im geprüften Projekt nicht. Eine weiterhin sichtbare Sitzmeldung benötigt den konkreten Zustand und tatsächlich geladenen Ressourcenpaketstand.

Quellen: [Generator](../dino-models/build_models.py), [Posen-/Kontaktregressionen](../dino-models/test_model_poses.py), [Wachstumsdetails](../dino-models/test_growth_details.py).

## Verifikation

Der Ausgangsstand wurde zuerst geprüft: 199 Paneltests, 40 Skript-/Distributionstests, elf Weltgeneratortests, 35 Modelltests und der vollständige Build der sieben eigenen Plugins bestanden. Die fehlenden Fehlerfälle wurden anschließend als Regressionen ergänzt; mehrere waren vor der jeweiligen Korrektur nachweislich rot.

| Prüfung | Ergebnis |
|---|---|
| Vollständiger finaler Build aller sieben Plugins | Bestanden, einschließlich zwölf Dino-Regeltestprogramme und der Tests anderer Module |
| Skript-/Distributionstests nach Versionswechsel | 40 bestanden |
| Weltgenerator | Zwölf bestanden, einschließlich offener Dino-Lichtungen |
| Modelle | 37 bestanden; acht Arten mit Export-/Gangparität |
| Nativer Kampf | 35 bestanden, inklusive Selbstverteidigung, Haustierschutz, Gastfahrer, PvP/Claims, Projektilwechsel und größere Zielgeometrien |
| Native Pflege | 17 bestanden einschließlich echtem Neustart; zusätzlich erster Spawn-Tick separat rot/grün belegt |
| Native Steuerung | 17 bestanden, einschließlich Freigabe, Gaststeuerung, Widerruf, Modi, Freilassen und Neustart |
| Native Bodentempi | Alle sieben Arten: gehen, laufen, geritten, Rückkehr vom Sprint und Stillstand |
| Nativer Flug | 12/18/12 mit echten Eingaben, Schweben, Schleichen, Landung und Identität |
| Natives Schwimmen | Sieben Szenariogruppen: 28 Art-/Moduskombinationen je 60 Sekunden, Jungtier, alle sieben Arten beritten, Decke, Lava, Pflanzen, Wasserhöhe, Ausschlüsse und Lifecycle |
| Native Weltpopulation | Sechs Prüfgruppen, einschließlich Schutzplugin-Ausfall, sofortiger Anleinpersistenz, Mosa-Grenzen/Überschneidung und echtem Neustart |
| Modellziel-Auflösung und Bedrock-Sitzprobe | Mit aktualisierten unabhängigen Größen-/Sitzerwartungen bestanden; Probe ist keine echte Geräteabnahme |
| Vollständige native BetterModel-/GeyserModelEngine-Abnahme | **Nicht bestanden; Infrastrukturblocker.** Letzter Lauf: null bestandene, eine fehlgeschlagene Registrierungsprüfung. Die nachfolgenden Größen-, Sitz-, Wachstums- und Reload-Prüfungen wurden nicht erreicht. |

Beim vollständigen Modellstack starteten Paper, BetterModel 3.6.0 und die Bridge 1.0.9; BetterModel registrierte alle acht Modelle und der Bedrock-Pack wurde erzeugt. Geyser 2.12.1-b1295 brach jedoch seine Initialisierung mit `Unable to set up encryption` ab: Der HTTPS-Aufruf an `client.discovery.minecraft-services.net` überschritt das feste Fünf-Sekunden-Zeitlimit. Ein normaler Java-Abruf über den vorhandenen Proxy und den System-Truststore scheiterte genauso. Daher blieben die Bedrock-Registries leer und die strenge Registrierungsassertion schlug fehl. Es wurde weder die Assertion abgeschwächt noch eine Sicherheitsfunktion deaktiviert. Dieser Lauf belegt keine Produktregression, aber auch keine erfolgreiche gemeinsame Modellruntime-Abnahme; diese bleibt vor einer Freigabe offen.

Native Spieltests verwendeten frische lokale Paper-26.3/241-Welten und direkte Java-Protokollclients. Pflegezeitpunkte wurden teilweise gezielt durch Fixture-PDC vorgerückt; manche Lifecycle-Ereignisse werden ausdrücklich simuliert. Diese Spieltests laufen nicht auf dem Produktionsserver. Der spätere autorisierte Livezugriff dient der Baselineprüfung und der gesonderten Zaunmigration. Nicht jeder frühere vollständige Testlauf verwendete denselben späteren Packaging-Hash: Kampf/Pflege sowie die ersten Steuerungs-/Schwimmläufe prüften ihre jeweiligen vollständigen Entwicklungsstände; die ersten finalen Spawn-, Flug- und Wildlife-Regressionen verwendeten den folgenden 1.2.0-Build. Reproduktionsskripte liegen im Repository.

ArkDinos-1.2.0-JAR-SHA-256 des ersten Reviews, vor der Erweiterung um den zweiten Reiter:

```text
649e89397d3ddd6ffa1b19981777e1056eade0faa8e412c58b46169466fcdedf
```

Der unabhängige Review fand zusätzlich veraltete explizite Modell-Testquellen/Sitzerwartungen sowie die Schutzplugin- und Anleinpersistenzlücken. Diese wurden aufgenommen, statt sie hinter bestandenen älteren Tests zu verbergen. Die CI führt jetzt auch die vollständige Python-Modellsuite aus.

### Nachtrag: zweiter Reiter

Die Anschlussumsetzung, der neue Buildhash, 13 bestandene native Prüfgruppen,
die Modell-/Geyser-Paketproben und die beim unabhängigen Review gefundenen
Ausstiegsfehler stehen im [Bericht zum zweiten Reiter](Second-Rider-2026-11-10.md).
Die oben genannten ersten Gesamtläufe bleiben als ursprünglicher Prüfstand
erkennbar. Der Nachtrag ersetzt sie nicht durch eine pauschale Behauptung,
dass bereits sämtliche Geräte- und Modellruntimeprüfungen abgeschlossen seien.

## Einführung in eine bestehende Installation

1. Auf dem tatsächlichen Server `version ArkDinos`, Paper-Version, `survival-worlds` und `prototype-worlds` prüfen. Gewünschte Dino-Welten müssen passend freigeschaltet sein. Außerhalb der Prototypwelten bleibt das bisherige passive Basissystem absichtlich aktiv.
2. Den geprüften Quellstand bzw. ein dazugehöriges erfolgreiches Buildartefakt verwenden. Das historische November-7-Binary und das feste Weltarchiv enthalten diesen Fix nicht.
3. Vor der Aktualisierung die bestehende Welt samt Plugin-Konfigurationen sichern. Im normalen gestoppten Wartungsfenster die bisherige ArkDinos-JAR durch 1.2.0 ersetzen; keine zweite Version daneben liegen lassen und kein Plugin-Hot-Reload verwenden.
4. Die zugehörigen vier geänderten Artenexports in BetterModel und in die Geyser-Modelleingaben übernehmen. Java- und Bedrock-Ressourcenpakete neu erzeugen, den generierten Bridge-Cache entsprechend der bestehenden Anleitung erneuern und neu verbinden. Nur die JAR zu tauschen repariert alte Clientgeometrien nicht.
5. Die größeren Tiere auf geeignetem freiem Gelände prüfen, einschließlich bestehender Eier/Nester. Vorhandene enge Gehege bleiben eng. Neue natürliche Begegnungen lassen sich über `wild-spawning` einstellen oder abschalten.
6. Zuerst die noch offene vollständige native Modellstack-Prüfung in einer Umgebung mit funktionierender Geyser-Discovery abschließen. Anschließend auf tatsächlichen Java-/Bedrock-/Konsolenclients sichtbare Kopf-/Kieferübergänge, beide Sitzpositionen/Kameras, Flug, Wasser und Eingaben abnehmen.

Das Plugin bleibt auf dem Entwurfs-PR. Der gesondert beauftragte Eingriff in die Livewelt betrifft die originalen Gehegezäune. Es wird keine neue Welt installiert und kein Tier ersetzt.
