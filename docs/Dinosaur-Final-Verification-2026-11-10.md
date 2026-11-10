# Abschließende Dino-Prüfung – 10. November 2026

> Historischer Prüfstand vor der später ausdrücklich beauftragten Live-Installation und Abhängigkeitssynchronisierung. Der aktuelle Stand und der zusätzliche Mosasaurus-Uferfix stehen im [Produktionsnachtrag](Dinosaur-Production-Sync-2026-11-10.md).

## Ergebnis und Kandidat

**Keine vollständige Freigabe.** Serverseitige Regressionen und die zusätzliche echte Clientprüfung sind unten getrennt ausgewiesen. Bedrock- und Konsolenabnahme bleiben erforderlich. Bis zum Abschluss dieses ursprünglichen Prüfstands gab es keinen produktiven Pluginwechsel, Merge oder Release; die spätere Live-Installation ist im Produktionsnachtrag getrennt dokumentiert.

Ausgangspunkt ist PR #7, Branch `fix/dinosaur-gameplay-20261110`, Commit `15e8debae2232e8711ff433e31ad5d40d73d1033`. Dessen Checks-Run `42775483340` und das abgeschlossene Codex-Review wurden erneut bestätigt; keine offenen Reviewthreads. Neuere Arbeit wurde nicht zurückgesetzt. Der schmutzige ursprüngliche Checkout blieb erhalten; gearbeitet wurde im bereits vorhandenen sauberen Repository-Checkout.

ArkDinos **1.2.0**, SHA-256 des tatsächlich installierten Testartefakts:

```text
96d78caca7d4360c78a0748d940ca14fb0a3f53926c2ea35122d134d45c8b922
```

Der erste vollständige Neubuild erzeugte dieselbe JAR wie die Referenz. Danach wurden ein Wachstums-/Neustartfehler und im tatsächlichen Java-Renderer ungültige Mosasaurus-Teilmodelle reproduziert. Der abschließend korrigierte Kandidat und seine erneuten Prüfungen werden getrennt vom ersten Kandidaten ausgewiesen; die direkte Modelladapterprobe ist ausdrücklich eine zusätzliche Prüfung kompilierter Adapterquellen.

Der abschließende **Codecommit ist `d849ecff7613eee8282e96378e46b1437e187255`**. Nachfolgende Dokumentationscommits ändern das getestete Plugin und seine Modellquellen nicht. Der CI-Nachweis des tatsächlichen abschließenden PR-Heads wird im PR-Übergabenachweis festgehalten. Die [maschinenlesbare Zusammenfassung](verification/2026-11-10-final-runtime.json) bindet Ergebnisse und Artefakte aneinander.

## Neun Nutzerpunkte

| Punkt | Ergebnis | Konkrete Nachweise und Grenzen |
| --- | --- | --- |
| 1. Freie Begegnungen | **Bestanden (Server)** | Sechs Wildlife-Prüfgruppen: Budgets, geladenes Gelände, WorldGuard-/Claim-Ausschluss, ausgefallene Schutzplugins, sofortige Anleinpersistenz und echter Neustart mit identischen UUIDs. Zwölf alte Habitate bleiben gemäß vorhandenem Audit zaunfrei: 180.708 geprüfte Positionen, null Änderungen. |
| 2. Tempo und Flug | **Bestanden (Server)** | Sieben Arten mit Gehen, Verfolgung, gerittenem Gehen/Sprint, Rückkehr und Stillstand. Flug: gemessen 12/18/12 Blöcke/s, Schweben, Sprung, Sinkflug/Landung und Schleichen. Sichtbare Bedrock-/Konsolenbedienung: **Blockiert**. |
| 3. Kampf | **Bestanden (Server)** | 35 native Prüfgruppen: autonom, Fahrerangriff, vergrößerte Reichweite, Besitzer/Haustiere/Mitfahrer, PvP/Teams/Claims, Null-/abgebrochener Schaden, Dilo-Schüsse bei Fahrer- und Rechtewechsel. Mitfahrersteuerung zusätzlich in Zweitreitersuite abgewiesen. |
| 4. Öffentliche Freigabe | **Bestanden (Server)** | 17 Steuerungsprüfgruppen und Zweitreitersuite: alte Tiere privat, Speichern/Neustart, Besitzerrechte, Widerruf mit Besitzer vorne und hinten. |
| 5. Zwei Reiter | **Bestanden (Server)** | Neue 19-Gruppen-Suite: vier adulte Arten, getrennte Sitze, eindeutiger Fahrer, dritter Reiter/Jungtiere abgewiesen, Teleport/Disconnect/Tod/Abstieg/Disable/Neustart. Echte Chunk-/Welt-Unloads und ungeladene Grenze ergänzt. Visuelle Matrix siehe unten. |
| 6. Proportionen | **Bestanden (Exporte/Server)** | 38 Modelltests; acht Modelle, Wachstumsfaktoren, Träger-/Sitzkalibrierung und Schlupfraum. Keine Änderung der vereinbarten Spielproportionen. Vollständige sichtbare Abnahme aller Entwicklungsstufen: **Nicht geprüft**. |
| 7. Oberflächen-Schwimmen | **Bestanden (Server)** | Sieben Schwimm-Prüfgruppen: 28 Art-/Moduskombinationen je 60 Sekunden, Jungtier, sieben Arten geritten, Luft/Gesundheit, Pflanzen/Fluidhöhe, Decke/Lava und Lifecycle. Zwei Reiter mit Kopfraum zusätzlich in Zweitreitersuite. Keine allgemeine Schadensimmunität. |
| 8. Modelle/Animationen | **Fehler behoben und erneut geprüft (Mosa-Teilmodelle); sichtbare Abnahme teilweise offen** | 38 Export-/Hierarchie-/Kontaktprüfungen, vollständiger Stack und tatsächlicher Neuaufbau separat dokumentiert. Kein `sit`-/`rest`-Clip. Der zusätzlich gemeldete Mosasaurus-Kiefer wird unten getrennt ausgewiesen: neue echte Java-Seitenansichten zeigen Verbindung bei geöffnetem und geschlossenem Kiefer; der ursprüngliche angeschnittene Bildzustand bleibt unbekannt. |
| 9. Rex-Zähmen/Schaden | **Bestanden (Server)** | 19 Pflege-Prüfgruppen inklusive echter zwölf Fleischgaben über **222,895 Sekunden**, ohne Friständerungen. Zu früh/falsch/zu früher Knochen: kein Verbrauch; abschließender Knochen: genau einer. Spawn-Tick-Abkürzung und 20 Basisschaden in nativen Pflege-/Kampfchecks. |

Die zusätzlichen 21 aquatischen Prüfgruppen decken Mosasaurus-Bewegung, Luft, Ziel-/Schadensschutz, Ufer/Breaches, Weltgrenzen und echte Neustarts ab. Ihre kontrollierten Bewegungs-/Grenzstimuli sind keine sichtbare Geräteprüfung.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis auf dem korrigierten Kandidaten |
| --- | --- |
| Native Gameplay-Suiten | **128/128 Bestanden**, neun Suiten, überall JAR `96d78c…`, null übersprungene Gruppen |
| Pflege / Kampf / Steuerung / Zweitreiter | **19 / 35 / 17 / 19 Bestanden** |
| Wildlife / Schwimmen / Mosa / Tempo / Flug | **6 / 7 / 21 / 1 / 3 Bestanden**; die Tempogruppe enthält alle sieben Arten |
| Python Modelle | **38 Bestanden** nach endgültiger Mosa-Hierarchie; alte Zwischenfassung mit Kontaktprüfungsfehlern nicht als bestanden gezählt |
| Python Welt / Skripte und Distribution | **20 / 41 Bestanden** |
| Native begrenzte Zaunmigration | **15 Bestanden**, einschließlich tatsächlichem WorldEdit-Schematic-Undo, ohne Live-Eingriff |
| Paket-/Zielschnittstellen | Neun Modell-Sitzgruppen, optionale fehlende Engines, tatsächlicher gepinnter Geyser-Übersetzer und Zielauflösung **Bestanden** |
| Vollständiger Modellstack und Pakete | Ergebnis, Eingabefingerprints und vollständige Paketzuordnung in der JSON-Zusammenfassung; keine Gerätefreigabe daraus abgeleitet |

## Neue Fehler und Regressionen

1. **Auslieferung unvollständig – Fehler behoben und erneut geprüft.** Die ausdrückliche Paketliste ließ 23 bereits vorhandene Dino-Quellen und Prüfprogramme aus, darunter `DinoSeats`, `DinoSwimming`, `DinoWildSpawns` und die benötigten Regelklassen. Das Archiv ließ sich deshalb nicht vollständig aus seinen eigenen Quellen nachbauen. Ein neuer Distributionstest ist am alten Listenstand rot und mit ergänzter Liste grün. Alle neuen Modellfixtures sind ebenfalls enthalten; private Dateien und lokale Planungsartefakte bleiben ausgeschlossen. Der vollständige Neubuild aller sieben Plugins aus dem entpackten Archiv erzeugte bytegleiche JARs.
2. **WorldEdit-Test abhängig von Hostsprache – Fehler behoben und erneut geprüft.** Auf dem deutschen macOS antwortet WorldEdit `Anzahl: 120`; der Test erwartete `Counted: 120`. Der erste Lauf brach nach neun bestandenen Prüfgruppen ab. Nur die Testsprache der wegwerfbaren JVM ist jetzt Englisch; alle 15 nativen Assertions einschließlich Schematic-Undo bestehen. Produktionskonfiguration und Migration bleiben unverändert.
3. **Falscher Dimensionsname in Fixture – Fehler behoben und erneut geprüft.** Der Hauptweltname `ark_model_test` ist kein Native-Dimensionsschlüssel. Der Rückweg verwendet jetzt `minecraft:overworld` und verlangt eine bestätigte Teleportantwort. Echte Unload-/Wiederladeprüfungen decken den zuvor verdeckten Pfad ab.
4. **Neue Fixtures unterscheiden idempotente Antworten und echte Höhe.** Bereits vorhandener Stein liefert `No blocks were filled`; ein tatsächlich nicht geladener Zielbereich bleibt ein Fehler. Modellierte Träger sind höher als native Fallbacktiere: Decken- und Fallschutzassertions verwenden gemessene Sitz-/Ausstiegshöhe und behalten strenge räumliche und zeitliche Grenzen. Fehlgeschlagene Vorläufe werden nicht als Produktfehler oder bestandene Tests gezählt.
5. **Jungtier-Grundgröße nach Modellneustart – reproduzierter Produktfehler.** Beim sauberen Stoppen wurde die native Größe mit der damaligen Wachstumszeit gespeichert und der aktive Modellmarker entfernt. Beim nächsten Start teilte der Adapter diese alte Größe durch den inzwischen höheren Wachstumsfaktor: eine adulte Basis von 1,0 wurde beispielsweise zu 0,9832. Der spätere native Fallback blieb dadurch zu klein; weitere Neustarts konnten den Fehler verstärken. Ein separater gespeicherter Wachstumsbasiswert erhält jetzt die validierte adulte Grundgröße über Entladen/Neustart, ohne den aktiven Modellmarker zu behalten. Native Wachstumsticks verwenden dieselbe Basis und räumen den Zusatzwert nach Wachstumsende auf. Die echte Neustart-/Fallback-Regression war vor dem Fix rot; die endgültige erneute Prüfung ist im JSON nachzulesen.
6. **Mosasaurus-Platzhalter im echten Java-Client – reproduzierter Produktfehler.** Nicht fehlende Texturdateien, sondern ungültige Elementgrenzen zweier kompilierter Schwanz-Teilmodelle lösen den Client-Platzhalter aus. Vier Koordinaten überschritten die Java-Grenze 32. Vier nicht animierte lokale Mesh-Knochen halten die kompilierten Würfel im zulässigen Bereich; die angrenzenden Schwanzknochen bleiben über diese neutralen Knoten verbunden. Ein unabhängiger Vergleich von 232.056 animierten Eckpunkten ergab maximal 0 Abweichung, außerdem unveränderte Würfel, Collider und Animationskanäle. Die ursprüngliche Kontaktassertion bleibt unverändert und erkennt eine absichtlich getrennte Schwanz-Gegenprobe weiter. Der tatsächliche Renderer-Nachtest ist dem neuen Paket zugeordnet.
7. **Zeitbezug beim Modell-Neustart.** Ein wachsendes Tier wächst auch während eines echten Serverneustarts. Die Regression muss den gespeicherten Wachstumsendpunkt und die tatsächliche Zeit verwenden; ein Vergleich zweier alter Größensnapshots mit festem Toleranzband ist kein korrekter Persistenztest.

Ein weiterer Vorlauf der Wall-Clock-Prüfung endete nach acht Fütterungen, weil ein wandernder Rex den Protokollspieler vom kleinen Fixturepodest führte und Paper ihn wegen fehlender Fallphysik trennte. Die Timing-Fixture pausiert jetzt ausschließlich diese native Tier-KI auf dem Podest; alle echten Fütterungsfristen, Inventar- und Verbindungsassertions bleiben aktiv. Dieser Vorlauf zählt nicht als bestanden.

Die neue Wall-Clock-Zähmungsprüfung wurde außerdem gegen eine ausschließlich lokal erzeugte, absichtlich falsche Zehn-Sekunden-JAR geprüft: Sie schlägt schon an der ersten Fristassertion fehl. Diese Mutante ist kein Auslieferungsartefakt.

## Echte Unloads und ungeladene Grenze

Vor jedem Unload verlassen beide echten Protokollspieler den Bereich: `nearbyPlayers` wechselt von zwei auf null. Erst danach fordert die Fixture über Papers reguläre API Entladen an und wartet auf **ChunkUnloadEvent, EntitiesUnloadEvent und `isChunkLoaded=false`**. Es wird kein Unloadereignis synthetisch erzeugt. Absteigen, Disconnect, Weltwechsel und Entfernen des Trägertiers werden getrennt geprüft. Anschließend lädt ein tatsächlich zurückkehrender Spieler den Chunk; Besitzer/UUID bleiben erhalten oder das entfernte Tier bleibt entfernt. Vor erneutem Aufsteigen sind Sitz-, Marker-, Gast-, Fahrt- und Reservierungszuordnungen leer, kein Fahrer geerbt und keine Plugin-/Force-Tickets vorhanden. Eine besetzte Welt lässt sich nicht entladen; die verlassene Zusatzwelt erzeugt ein echtes WorldUnloadEvent.

Die Grenzprobe verwendet eine wirklich ungeladene Nachbarfläche und bestehende native Fahrer-/Mitfahrerbindungen. Innerhalb eines synchronen Fixtureblocks werden Anbindungen an den vorgeladenen Rand versetzt, Bewegung/Drehung/Kollision und Sitzpositionierung abgefragt und wiederhergestellt. Eine zulässige kleine Bewegung bleibt möglich; grenzüberschreitende Sitz-/Schwenkräume werden abgewiesen. Unabhängiger ChunkLoadEvent-Zähler: **null**, exakte geladene Chunkmenge unverändert, Abstand **1,2 Blöcke**. Zwischen den gemessenen Abfragen läuft kein normaler Spielertick: Das isoliert Plugin-Nachladen von den regulären Spieler-Tickets. Es ist kein Langstreckenfilm an einer dynamischen Streaminggrenze und kein Nachweis, dass aktive Spieler ihre Chunks regulär freigeben müssten.

## Modellstack und Discovery

Der frühere Timeout bleibt dem damaligen Linux-/Proxy-Prüflauf zugeordnet. Auf dem aktuellen macOS-Host sind keine HTTP-/HTTPS-/ALL-Proxyvariablen gesetzt. Die native EncryptionUtils-Initialisierung der gepinnten Geyser-JAR erreicht den echten Microsoft-Discovery-Endpunkt mit Standard-JDK-TLS und normalem DNS; der Abruf antwortet HTTP 200 in ungefähr 330 ms. Es wurden keine TLS-, Authentifizierungs- oder Verschlüsselungsfunktionen deaktiviert und keine Dependencies gewechselt. Die frühere Fünf-Sekunden-Überschreitung ist in dieser Umgebung nicht reproduziert; daraus folgt keine nachträgliche sichere Diagnose des früheren Netzpfads.

Der endgültige Gameplaylauf besteht **6/6** Prüfgruppen gegen die korrigierte JAR: acht echte Dino- und zusätzlich sieben platzierte Eiermodelle sind sowohl in Java als auch den tatsächlichen Bedrock-Registries vorhanden. Für jede der vier großen Arten werden alte Tracker beim unterstützten BetterModel-Reload geschlossen, neue Tracker/Actor-IDs erzeugt und beide nativen Fahrzeuge für bestehende sowie neue Betrachter mit echten Bridge-Poseströmen synchronisiert. Der echte Neustart erhält UUIDs und Wachstum; anschließender Fallback, ein weiterer nativer Wachstumstick und erneute Modellanbindung erhalten die adulte Basis. Die ergänzende direkte Adapterprobe besteht 19 Gruppen.

Die drei Übergabepakete stammen aus allen 15 Installer-Eingaben (acht Dinos und sieben platzierte Eier), einschließlich separatem Bedrock-Inventareierpaket. Java-SHA256 `40d6d781ff132d9cfa085ec99f6894c41297c4de59a8985cf28d421fa3e78440`; Bedrock-Modelle `e8ecb2f145eeea6ea8d23b4e745c3a03a862beb6762d4693723aa23ce4465a89`; Bedrock-Inventareier `6a620a56e7bae1e536844990abc7588b586303b78aa1d13b34f39b9b1eef0f67`. Die Bridge erzeugt bei Starts neue UUIDs/Dateinamen: der erste Bedrock-Packhash 4d06… ist deshalb ausdrücklich vom geprüften Übergabestand nach Neustart e8ec… getrennt. Vollständige Eingabefingerprints, beide Phasen und Artefakthashes stehen in der maschinenlesbaren Zusammenfassung. Die realen Bridge-Poseströme werden mit Java-Protokollspielern in den tatsächlichen Bridge-Betrachterlisten geprüft; dabei entsteht keine echte Geyser-/Bedrock-Spielersitzung. Eine erfolgreiche Registrierungs-/Paketprüfung beweist kein Bedrock-Rendering oder Bedrock-Verbindungsaufbau. Die Loopback-Tests zeigen außerdem STUN-/NetherNet-Bindungswarnungen; sie sind kein Nachweis für eine von außen erreichbare Bedrock-Verbindung.

`verify_java_pack.py` prüft im tatsächlich ausgelieferten Java-ZIP 2.702 Modelldateien und 6.060 Würfel auf endliche, zulässige Grenzen und gültige Texturreferenzen. `verify_bedrock_pack.py` prüft im ausgelieferten Bedrock-Modellpaket alle 15 Cliententities, 30 Geometrien und 124 Dateien, einschließlich exakter Quellgeometrien, Animationsdaten, Texturbytes und Referenzen. Ein historisches Paket mit nur acht Modellen fällt durch diese vollständige Prüfung. Das grafisch erzeugte Java-Pack `c228aed…` und das Übergabepack `40d6d…` besitzen dieselben 3.025 Pfade und für jeden Eintrag identische unkomprimierte Bytes; nur der ZIP-Container unterscheidet sich. Dieser Vergleich ist separat protokolliert.

## Rendernde Clients und verbleibende Abnahme

Drei echte Java-26.3-Clients mit getrennten lokalen Offline-Testprofilen wurden aus den vorhandenen offiziellen Client-Jars gestartet: zwei Reiter und ein zusätzlicher Außenbeobachter. Das ist eine isolierte Loopback-Testumgebung und keine Online-Kontoprüfung. Der Client-Jar-SHA-1 stimmt mit dem vorhandenen Launcher-Manifest überein: `e877b6a07acd633fb3bb475002175cec036e7b87`. Client-JVM: Microsoft OpenJDK 25.0.1; tatsächlich verwendeter Renderer: LWJGL/OpenGL auf Metal.

**Mosasaurus-Kiefer: in den ausgeführten neuen Zuständen nicht als abgetrennter Kiefer reproduziert.** Das ursprüngliche Nutzerreferenzbild `2026-11-10_16.13.51.png` zeigt den Kopf angeschnitten; der genaue Animationszustand wurde damals nicht erfasst. Neue vollständige Seitenansichten zeigen den verbundenen Kiefer nach frischem Anzeigen, weit geöffnet bei wiederholten/unterbrochenen Angriffen und wieder geschlossen nach Abbruch. Das ist kein Beweis für alle Zustände oder Bedrock. Es wurde kein vermuteter Kieferfix eingebaut.

Die ergänzende reale Engineprobe prüft sieben Zustände mit je 61 Frames: erstes Anzeigen, Idle, Angriff aus Idle, Schwimmen, Angriff aus Schwimmen, unterbrochene Angriffe und expliziter Abbruch. Alle 427 Kopf-/Kieferpaare behalten den Gelenkpunkt und synchrone Uhren. Zusätzlich besteht die permanente Assertion für die tatsächlich zuerst veröffentlichten Spawn-/Metadaten eines frischen Tiers beim bereits verbundenen Betrachter: Gelenkfehler rund `2e-8` Blöcke. Ein interner Interpolations-Diagnosewert beginnt beim Erstaufbau zwar bei Null; der erste tatsächlich gesendete Kopf-/Kiefertransform ist bereits korrekt. Diese Engine-/Paketprobe wird nicht als gerenderte Clientinterpolation ausgegeben.

| Kombination | Status |
| --- | --- |
| Java fährt / Java fährt mit | **Bestanden für die dokumentierten Standbilder und Kamerastichproben** aller vier adulten Arten; übrige Szenarien unten **Nicht geprüft**. |
| Java / Bedrock | **Blockiert**: kein ausführbarer lizenzierter Bedrock-Client verfügbar. |
| Bedrock / Java | **Blockiert**: gleiche Voraussetzung. |
| Bedrock / Bedrock | **Blockiert**: zwei tatsächliche Bedrock-Clients erforderlich. |
| PS5/Konsole | **Blockiert**: kein angeschlossenes Konsolengerät bzw. nutzbarer Konsolenclient. |

Die unveränderten PNGs und ihre Zuordnung stehen im [Clientmanifest](verification/2026-11-10-rendering/client-render-manifest.json):

| Sichtbarer Nachweis | Ergebnis und Beleg |
| --- | --- |
| T-Rex, zwei tatsächliche Reiter | **Bestanden**: getrennte Sitzpositionen, kein sichtbarer Helfer; unabhängig mit der Maus bediente Mitfahrerkamera. [Seitenaufnahme](verification/2026-11-10-rendering/java26.3-trex-final15-two-real-riders-third-person-side.png). |
| Triceratops, zwei tatsächliche Reiter | **Bestanden**: normale R-Eingaben, getrennte Spieler in der Aufsicht und freie Mitfahrerkamerastichprobe. [Aufnahme](verification/2026-11-10-rendering/java26.3-triceratops-release40-two-real-riders-third-person-overhead.png). |
| Stegosaurus, zwei tatsächliche Reiter | **Bestanden**: beide Spieler auf dem Rücken; dritter rendernder Client als Außenbeobachter. [Aufnahme](verification/2026-11-10-rendering/java26.3-stegosaurus-release40-two-real-riders-independent-outside-client.png). |
| Brachiosaurus, zwei tatsächliche Reiter | **Bestanden für getrennte Sitze** aus unabhängiger Außenperspektive. [Aufnahme](verification/2026-11-10-rendering/java26.3-brachiosaurus-release40-two-real-riders-independent-outside-client.png). **Kameragrenze**: der hohe Hals verdeckt die zentrale Fahrer-Vorwärtssicht deutlich; [Erstpersonbeleg](verification/2026-11-10-rendering/java26.3-brachiosaurus-release40-driver-forward-neck-occlusion-first-person.png). Keine uneingeschränkte Kameraabnahme. |
| Mosasaurus, korrigierter Export | **Fehler behoben und erneut geprüft**: [ganzer Körper ohne Platzhalter](verification/2026-11-10-rendering/java26.3-mosasaurus-final15-cuboid-fix-green-full-body.png). |
| Mosasaurus, zusätzlicher Kieferhinweis | Neue Seitenansichten: [offen bei unterbrochenen Angriffen](verification/2026-11-10-rendering/java26.3-mosasaurus-final15-interrupted-attack-open-head-side.png), [geschlossen nach Abbruch](verification/2026-11-10-rendering/java26.3-mosasaurus-final15-after-cancel-idle-head-side.png), jeweils verbunden. Der [ursprüngliche angeschnittene Zustand](verification/2026-11-10-rendering/java26.3-mosasaurus-baseline-user-reference.png) bleibt unbekannt; kein erfundener Kieferfix. |

Beide Reiter sind pro Art auf derselben Fußhöhe und 1,2 Blöcke voneinander entfernt; diese Messung ergänzt die sichtbaren Belege. Nach erneutem Verbinden haben beide Reiterclients exakt das ausgelieferte Java-Pack `40d6d…` heruntergeladen. Trike-/Stego-/Brach-Aufnahmen stammen aus dieser Phase; frühere Bilder tragen ihren tatsächlichen eigenen Packhash, dessen komplette Inhalte bytegleich abgeglichen wurden.

Vorhandene Android-Emulatoren enthalten keine nachgewiesene Minecraft-Installation, ADB meldet kein Gerät; es wurde keine fremde Windows-Sitzung dafür verwendet. **Nicht geprüft** bleiben die vollständige sichtbare Bewegungsmatrix (Anfahren, längeres Gehen/Sprinten, enge Kurven/Stoppen, regelmäßige Korrekturen), Schwimmen/Ufer, niedrige Decken und erzwungener Ausstieg sowie alle acht Modelle in sämtlichen Wachstums-/Beobachter-/Reloadzuständen. Kurze W-Tastenimpulse ergaben 0,0 Blöcke und erzeugten keine verlässliche gehaltene Bewegungseingabe; dieser Versuch zählt nicht als bestandene Bewegungsprüfung. Die erfolgreichen nativen Eingabetests ersetzen diesen sichtbaren Nachweis nicht. Der Versuch eines echten Schleichen-Abstiegs ist zusätzlich **Blockiert**: die UI-Automation weist eine reine Shift-Taste als Modifier ohne Haupttaste zurück (`keyPressIncludedNoNonModifierKeys`). Dieser Versuch besteht ebenfalls nicht. Die Brachiosaurus-Erstperson-Sichtgrenze ist vor einer umfassenden Bedienfreigabe ausdrücklich zu beurteilen.

## Reproduktion

Voraussetzungen aus den Skripten: JDK 25, Python mit PyYAML für Modellstack, Node, gepinnte JARs, vollständiger Paper-26.3/241-Cache (`libraries`, `versions`, `cache`) und gebaute Projekt-JARs. `DINO_MODEL_TEST_LIBS` kann den vorhandenen Mockito-/Byte-Buddy-/Objenesis-Cache auswählen. Alle Server binden ausschließlich Loopback; Zugangsdaten werden nicht in veröffentlichte Nachweise übernommen.

```sh
python3 scripts/build_plugins.py
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 -m unittest discover -s world-design -p 'test_*.py'
python3 -m unittest discover -s dino-models -p 'test_*.py'
# JAVA_HOME, DINO_PAPER_CACHE und DINO_PAPER_JAR auf die genannten lokalen Caches setzen.
# DINO_PLUGIN_JAR wählt exakt die oben gehashte JAR; jedes *_OUTPUT ist ein eigenes Verzeichnis.
node dino-plugin/care-runtime-test.mjs
node dino-plugin/combat-runtime-test.mjs
node dino-plugin/controls-runtime-test.mjs
node dino-plugin/second-rider-runtime-test.mjs
node dino-plugin/wildlife-runtime-test.mjs
node dino-plugin/swimming-runtime-test.mjs
node dino-plugin/aquatic-runtime-test.mjs
DINO_SPEED_ONLY=1 node dino-plugin/controls-runtime-test.mjs
DINO_FLIGHT_SPEED_ONLY=1 node dino-plugin/controls-runtime-test.mjs
DINO_SECOND_RIDER_MODELS=1 node dino-plugin/second-rider-runtime-test.mjs
DINO_MODEL_GAMEPLAY=1 node dino-plugin/model-runtime-test.mjs
DINO_MODEL_JOINTS_ONLY=1 node dino-plugin/model-runtime-test.mjs
node dino-plugin/model-runtime-test.mjs
python3 dino-plugin/model-seat-test.py
python3 dino-plugin/bedrock-seat-test.py
python3 dino-plugin/model-target-test.py
python3 dino-models/verify_java_pack.py /path/to/ArkDinos-java-1.2.0.zip
python3 dino-models/verify_bedrock_pack.py /path/to/ArkDinos-bedrock-models-1.2.0.mcpack
# FENCE_PAPER_CACHE, FENCE_PAPER_JAR, FENCE_WORLDEDIT_JAR und FENCE_TEST_OUTPUT setzen.
python3 world-design/fence_runtime_test.py
python3 scripts/package.py --version verified-candidate-20261110
```

Kampf/Mosa verwenden zusätzlich die vorhandenen `DINO_WORLDEDIT_JAR`, `DINO_WORLDGUARD_JAR` und `DINO_GRIEFPREVENTION_JAR`; für vollständige Modellläufe `DINO_MODEL_STACK_CACHE`, `DINO_MODEL_PAPER`, `DINO_MODEL_CACHE` und `DINO_GME_JAR`. Die JARs werden gegen die Repository-Pins geprüft. Die ausgeführten Varianten, Ergebniszahlen und Hashes stehen in der JSON-Zusammenfassung; frühere/fehlgeschlagene Probevarianten werden dort getrennt geführt.

## Renderdistanz

Auf zusätzlichen Nutzerwunsch schreibt das frische Setup ausdrücklich `view-distance=14`; vorhandene Eigenschaften werden wie bisher nicht überschrieben. Alle drei tatsächlichen Java-Prüfclients und die grafische Testwelt verwenden ebenfalls 14 Chunks. Die Simulationsweite bleibt unverändert. Für eine bestehende Installation ist `view-distance=14` in deren `server.properties` im vorgesehenen Wartungsfenster zu übernehmen; es wurde kein produktiver Neustart ausgelöst.

## Aktualisierung vorbereiten

Bestehende Welt, Tiere und Besitzer sichern. Nach Abschluss der noch offenen Geräteabnahme im gestoppten Wartungsfenster genau eine ArkDinos-1.2.0-JAR installieren. Die vollständigen 15 Installer-Modellinputs mitnehmen: acht Dinos und sieben platzierte Eier. Java-Pack, Bedrock-Modellpack und separates Bedrock-Inventareierpaket gemeinsam aus denselben Eingaben neu erzeugen und Bridge-Cache nach dokumentiertem Verfahren erneuern. Unterstützter Modellreload ist BetterModels eigener Modellreload, kein Bukkit-/Plugin-Hot-Reload. Ein vollständiger Neustart mit neuen Paketständen und anschließendem Reconnect gehört zur Einführung. Die vorhandenen Zäune benötigen keine pauschale Löschung.

## Unabhängige Abschlussprüfung

**Bestanden:** Ein unabhängiger Reviewer hat mit `ponytail:ponytail-review` den vollständigen Änderungsumfang einschließlich angrenzendem unverändertem Gameplaycode, Testergebnissen, Paketphasen und allen neun PNGs geprüft. Bestätigte Befunde wurden korrigiert und erneut unabhängig geprüft; keine materiellen Codebefunde bleiben offen. Dies ist die Freigabe zur PR-Aktualisierung, keine vollständige Geräte-/Bedienfreigabe. Die CI des tatsächlich gepushten Dokumentationsheads wird zusätzlich im PR verknüpft. Alle drei QA-Clients und die isolierten Server-/HTTP-Prozesse wurden danach ordentlich beendet.
