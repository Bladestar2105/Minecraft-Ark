# Ursprüngliche Dino-Gehegezäune gezielt entfernen

Diese Migration öffnet die ursprünglichen zwölf Dino-Gehege einer bestehenden
Welt. Sie baut keine Landschaft neu, ersetzt kein Weltarchiv und führt keine
`ark_renewal:build`- oder `site_*`-Baufunktion aus. Ein Plugin-Update allein
verändert bereits gesetzte Weltblöcke nicht.

## Live-Ergebnis vom 10. November 2026

**Alle zwölf ursprünglichen Habitatbereiche waren zum Prüfzeitpunkt bereits
zaunfrei. Es wurden keine Weltblöcke entfernt oder ersetzt.** Der Live-Lauf
hat die Standorte geprüft und danach seine temporären Chunk-Tickets und die
WorldEdit-Konsolenauswahl wieder aufgeräumt. Eine Sicherung oder ein Apply war
bei null Treffern nicht erforderlich.

Die Atlas-XZ-Koordinaten wurden an jedem Standort durch den exakt benannten
ursprünglichen Marker bestätigt. Dessen Höhe liefert die Bodenhöhe `y`.
Geprüft wurde jeweils ein **37 × 11 × 37** großer Quader: horizontal `x ± 18`
und `z ± 18`, vertikal vom Boden `y - 4` bis `y + 6`. Darin liegen der gesamte
ursprüngliche Zaunperimeter, alle drei Tore und die oberen Lampenpfosten.
Die zwölf getrennten Auswahlen umfassen zusammen **180.708 Blockpositionen**.

| Habitat | Mittelpunkt X | Boden Y | Mittelpunkt Z | Zäune und Tore im Prüfbereich |
| --- | ---: | ---: | ---: | ---: |
| 01 | -9584 | 70 | -6212 | 0 |
| 02 | -5956 | 63 | -9256 | 0 |
| 03 | -1056 | 68 | -5844 | 0 |
| 04 | 5632 | 71 | -8432 | 0 |
| 05 | -7036 | 71 | 0 | 0 |
| 06 | -664 | 72 | -3944 | 0 |
| 07 | 5044 | 76 | -4848 | 0 |
| 08 | 8820 | 86 | 1452 | 0 |
| 09 | -9076 | 75 | 9536 | 0 |
| 10 | -4288 | 69 | 3336 | 0 |
| 11 | 544 | 82 | 8844 | 0 |
| 12 | 6936 | 68 | 3016 | 0 |

Die WorldEdit-Zaun-Tags wurden am ersten Standort zusätzlich mit ausdrücklichen
Block-IDs geprüft. Für die fortlaufende Prüfung wurde eine gemeinsame Liste
aller 25 Vanilla-Zaun- und Zauntor-IDs verwendet. Eine vermutete Holzpalette war
dadurch nicht erforderlich. Am ersten Habitat wurden außerdem die erwarteten
816 Moosblöcke und native Boden-/Luftproben bestätigt; das sichert die Zuordnung
zur ursprünglichen Struktur zusätzlich ab.

Die abschließende globale Abfrage meldete **keine Force-Load-Chunks** in
`minecraft:overworld`. WorldEdit bestätigte die entfernte globale Maske,
geleerte Auswahl und entfernte Weltüberschreibung. Es waren null Spieler
online; `save-all flush` wurde erfolgreich abgeschlossen. Die Prüfung fand
zwischen 06:53 und 07:15 UTC in der Bukkit-Welt `ark` statt.

Der maschinenlesbare [Auditbericht](../world-design/fence-audit-20261110.json)
enthält Bounds, Markerpositionen und Abschlussnachweise. Das
[bereinigte Befehlsjournal](../world-design/fence-audit-20261110.commands.jsonl)
enthält die 104 abgeschlossenen Konsolenbefehle und ihre tatsächlichen
Rückmeldungen, ohne Zugangsdaten oder Portaladresse. Die Daten belegen den
Zustand dieser zwölf Bereiche zum Prüfzeitpunkt. Sie bestimmen weder den
Zeitpunkt noch den Urheber früherer Weltänderungen und sind keine globale
Bestandsaufnahme aller Zäune in der Welt.

## Welche Blöcke gehören zum Plan?

Grundlage ist die Habitat-Geometrie aus
`world-design/renewal.py` im Commit
`360677edc18725b0b1673d86b32aafb895c8acc6`. Für den Mittelpunkt `(x, y, z)`
bezeichnet `y` den ursprünglichen Boden:

| Bereich | Genau begrenzte Positionen |
| --- | --- |
| Umzäunung | Quadrat mit `x ± 16`, `z ± 16`, ausschließlich Höhe `y + 1` |
| Tore | `(x - 1 … x + 1, y + 1, z + 16)` |
| Erhaltene Lampenbasen | `(x - 16, y + 1, z - 16)` und `(x + 16, y + 1, z - 16)` |
| Höchstens zu ändern | 123 Zäune und 3 Tore je Habitat; insgesamt 1.512 Blöcke |

Die beiden nördlichen Eckpfosten sind zugleich die Basen der höheren
Dekolampen. Auch ihre oberen Blöcke und Laternen bleiben erhalten. Andere
Materialien, andere Höhen, das Gehegeinnere und Bauten außerhalb der genannten
Positionen sind keine Löschziele.

Auch eine passende Zielposition wird geschützt, wenn direkt darüber keine
Luft oder direkt darunter nicht mehr der ursprünglich gesetzte Grasblock
liegt. So wird eine Spieler-Laterne, ein Teppich oder ein weiterer Aufbau
oberhalb nicht seiner Stütze beraubt; ein unter dem Zaun umgebauter Boden
schützt auch dort angebrachte hängende Dekoration. Das Verfahren lässt im
Zweifel einen Zaun stehen. Geschützte Treffer gehören deshalb in die Meldung
der übersprungenen beziehungsweise verbleibenden Zäune.

Die ursprüngliche Palette verwendet Eiche für `plains`, Fichte für `taiga` und
`snow`, Birke für `desert`, Tropenholz für `jungle`, Akazie für `savanna`,
Mangrove für `swamp` und Kirsche für `cherry`. Der Plan verwendet nur das zum
jeweiligen ursprünglichen Habitat gehörende Material. Ein Zaun anderer Holzart
an derselben Stelle bleibt stehen.

**Position und Material beweisen keine Eigentümerschaft.** Ein später von
Spielern gesetzter, identischer Ersatzblock an genau einer ursprünglichen
Zaunposition ist technisch nicht unterscheidbar. Prüfe bekannte Umbauten vor
der Anwendung. Bereits offene Gehege benötigen keine Änderung.

## Quellen und Vorprüfung

Verwende das originale `manifest.json` des installierten `ark_renewal`-Datapacks.
Das öffentliche `world-release.json` enthält Archivmetadaten und ist kein
Habitatmanifest. Das im Quellcode enthaltene Atlas-`sites.yml` ist ebenfalls
kein Ersatz für die tatsächlich erzeugten Höhen und Themen.

`fence_migration.py` verlangt genau die IDs `habitat_01` bis `habitat_12`, prüft
Höhen, Weltgrenzen, ursprüngliche Radien, Marker und überlappende Flächen und
bindet den Plan mit SHA-256 an Manifest und Dimensions-ID. Die Native-Dimension
und der Bukkit-Weltname sind getrennte Angaben; prüfe beide am Zielserver.
Im Standardstack gehört die Welt `ark` zur Dimension `minecraft:overworld`.

Fehlt das Manifest, lassen sich Atlas-XZ und die exakt benannten Marker
`ark_renewal_habitat_01` bis `_12` als begrenzte Diagnose verwenden. Ihr
ursprünglicher Standort ist `(x + 0.5, y + 1, z + 0.5)`. Bestätige zusätzlich
den Boden und die tatsächlich vorhandenen Zäune. Fehlende Lampen, abweichende
Geometrie oder unbekannte Materialien dürfen nicht durch geratenes Löschen
ersetzt werden. Ein größerer **nur lesender** WorldEdit-Ausschnitt kann klären,
ob der Bereich schon offen ist; er ist keine vergrößerte Löschfreigabe.

## Weg A: explizites Datapack

Der lokale Generator verbindet sich mit keinem Server. Das Zielverzeichnis
muss neu sein:

```sh
python3 world-design/fence_migration.py original-ark-renewal-manifest.json \
  --dimension minecraft:overworld --output /tmp/ark-fence-migration
```

Prüfe `plan.json` mit allen einzelnen Zielpositionen. Installiere den erzeugten
Ordner anschließend über den regulären Dateizugang in die Datapacks der
betroffenen Welt und lade die Datapacks kontrolliert neu. Das Paket hat keine
Load- oder Tick-Hooks. Es enthält pro Habitat diese ausdrücklich aufzurufenden
Funktionen, hier für Habitat 01:

```mcfunction
function ark_fence_migration:load_habitat_01
function ark_fence_migration:scan_habitat_01
function ark_fence_migration:status_habitat_01
```

`load` merkt sich bereits vorhandene Force-Load-Tickets und fordert nur fehlende
Chunks an. Warte auf ihre Verfügbarkeit; `scan` bricht bei ungeladenen Chunks
oder fehlendem Originalmarker ab. Es entfernt keinen Block und erfasst nur
passende Materialien mit freier Oberseite und ursprünglichem Grasboden.
`summary.protected` zählt die passenden, wegen dieser Umgebung geschützten
Blöcke. Zaunverbindungen, Wasserfüllung sowie Ausrichtung,
Wand-, Offen- und Redstone-Zustand der Tore werden per `execute if block`
aufgenommen. Zäune haben keine Block-Entity: `data get block` kann ihre
vollständigen Zustände daher nicht sichern.

Die Sicherung liegt dauerhaft in `ark_fence_migration:state`. `scan` überschreibt
keine bestehende Sicherung. Prüfe vor der Anwendung `summary.phase`,
`summary.matched` und gegebenenfalls einzelne gespeicherte Blöcke. Minecraft
kann lange NBT-Textausgaben mit `...` kürzen; Skalarpfade liefern eindeutige Werte:

```mcfunction
data get storage ark_fence_migration:state sites.habitat_01.summary.phase
data get storage ark_fence_migration:state sites.habitat_01.summary.matched
data get storage ark_fence_migration:state sites.habitat_01.summary.protected
save-all flush
function ark_fence_migration:apply_habitat_01
data get storage ark_fence_migration:state sites.habitat_01.summary.removed
```

`apply` vergleicht vor der ersten Blockänderung alle erfassten Zustände. Hat
sich ein Treffer oder seine Umgebungsvoraussetzung seit `scan` verändert,
bricht das ganze Habitat ab und meldet Konflikte. Anschließend verändert die
Funktion nur gesicherte Treffer. Nasse
Zäune werden zu Quellwasser, trockene Zäune und Tore zu Luft. Wiederholtes
`apply` entfernt keine späteren Ersatzbauten und überschreibt die Sicherung
nicht.

```mcfunction
function ark_fence_migration:undo_habitat_01
data get storage ark_fence_migration:state sites.habitat_01.summary.remaining
function ark_fence_migration:release_habitat_01
save-all flush
```

`undo` arbeitet nur auf tatsächlich erfolgreich entfernten Blöcken und nur,
solange dort noch die von der Migration gesetzte Luft beziehungsweise das
Quellwasser liegt und die vertikalen Umgebungsvoraussetzungen weiter erfüllt
sind. Andere Blöcke bleiben stehen; `remaining` nennt übersprungene Positionen.
Wiederholtes Undo kann noch offene Positionen bearbeiten. `release`
entfernt ausschließlich die zu Beginn selbst hinzugefügten Tickets. Verändere
währenddessen diese Tickets nicht gleichzeitig mit einer zweiten Verwaltung.

## Weg B: vorhandenes WorldEdit über Admin-Konsole oder RCON

Wenn die Installation keinen Datei-Upload erlaubt, bereitet
`worldedit_bundle(plan, "habitat_01", "ark", "eindeutiger_backupname")` aus
`world-design/fence_migration.py` gewöhnliche WorldEdit-7.4.6-Konsolenbefehle
vor. Jeder Befehl ist höchstens 500 Zeichen lang und passt damit in die
authentifizierte ARK-Verwaltungskonsole. Es werden weder neue Plugins noch
Commandblöcke benötigt.

Die zurückgegebenen Listen müssen in dieser Reihenfolge geprüft werden:

1. `select`: Welt, quaderförmige Auswahl, beide absolute Eckpunkte, Platzierung
   an Position 1 und eine leere globale Maske. Die Auswahl ist genau
   **33 × 1 × 33 = 1.089 Blöcke** groß.
2. `inspect`: Größe und fünf Zählungen. `inspection_counts` benennt zuerst
   `candidate_fences` und `candidate_gates`: die ursprünglichen Materialien
   an ihren jeweils passenden Positionen, ohne Umgebungsfilter. Es folgen
   `eligible_dry_fences`, `eligible_wet_fences` und `eligible_gates` mit
   Luft-/Grasbodenprüfung. Kandidatensumme minus entfernbarer Summe ergibt die
   geschützten Treffer. Insgesamt sind höchstens 126 Kandidaten erlaubt.
   Andere Blöcke der Auswahl werden gelesen, aber nicht gelöscht.
3. `backup`: Kopieren der tatsächlichen schmalen Ebene ohne Entitäten und
   Speichern als neues Sponge-Schematic unter
   `plugins/WorldEdit/schematics/<backupname>/habitat_01.schem`. Verwende niemals
   den Überschreibschalter `-f` für die ursprüngliche Sicherung.
4. `verify_backup`: Clipboard ausdrücklich leeren, das gespeicherte
   **`.schem`** neu laden und mit `//size -c` die wiederhergestellten Maße und
   den ursprünglichen Offset bestätigen. WorldEdit speichert und lädt
   asynchron; eine leere RCON-Antwort beweist keinen Erfolg. Erst das nach dem
   Leeren tatsächlich wieder verfügbare Clipboard belegt den Lesezugriff auf
   die Sicherung.
5. `apply`: Drei kleine Replace-Befehle mit absoluten Geometriemasken und
   passendem Material sowie freier Oberseite und ursprünglichem Grasboden.
   Der Schritt für nasse Zäune kann bei nachgewiesenen null Treffern entfallen.
   `verify` zählt anschließend `remaining_fences` und `remaining_gates` an den
   ursprünglichen Positionen **ohne** Umgebungsfilter. Geschützte Zäune bleiben
   so im Restzähler sichtbar und werden nicht als bereits entfernt ausgegeben.
6. `cleanup`: Globale Maske und Konsolenauswahl zurücksetzen; anschließend
   ausschließlich die selbst hinzugefügten Force-Load-Tickets entfernen.

WorldEdit verwendet in dieser Version **Leerzeichen innerhalb einer zitierten
Maske für Schnittmengen**. Die Koordinaten-Ausdrücke enthalten zusätzlich feste
Grenzen; eine versehentlich größere Auswahl erweitert daher die erzeugten
Löschmasken nicht. Block-Tags beginnen in WorldEdit mit `##`, beispielsweise
`//count ##minecraft:fences`. Das ist eine andere Syntax als bei Vanilla-
Blockprädikaten.

Undo hat drei getrennte Phasen: `undo_prepare` leert das Clipboard und lädt
das gespeicherte Schematic neu. `undo_verify` fragt das Clipboard ab. Warte
und wiederhole diese Prüfung, bis die ursprünglichen Maße **33 × 1 × 33**,
die 1.089 Blöcke und der passende ursprüngliche Offset bestätigt sind. Eine
leere Antwort oder ein noch leeres Clipboard erlaubt keine Wiederherstellung.
Erst danach führt `undo_restore` die drei gezielten Paste-Schritte aus.
`clipboard_expectation` enthält die dafür erwarteten Maße, Blockzahl und den
Ursprung an Auswahlposition 1.
Der Quellfilter begrenzt sie auf das gesicherte
Originalmaterial. Die Zielmaske erlaubt nur Luft beziehungsweise Quellwasser
an den vorgesehenen Positionen mit weiterhin freier Oberseite und
ursprünglichem Grasboden. Dadurch werden nachträgliche Steinblöcke,
Häuser oder andere Bauten nicht überschrieben. Nutze dafür weder ein
unmaskiertes `//paste` noch ein pauschales `//undo`.

Während Capture und Apply sollte niemand im betroffenen Bereich bauen.
Anders als eine einzelne Datapack-Funktion laufen mehrere RCON-Befehle nicht
im selben Server-Tick. Die WorldEdit-Konsolensession darf dabei nur von einer
Verwaltung verwendet werden. Protokolliere Befehle, Rückmeldungen, tatsächliche
Änderungszahlen, Sicherungsnamen und eigene Chunk-Tickets; Passwörter,
Session-Cookies und CSRF-Token gehören nicht in das Protokoll.

## Verifikation

Die automatischen Geometrieprüfungen laufen mit:

```sh
python3 -m unittest discover -s world-design -p 'test_*.py'
```

`world-design/fence_runtime_test.py` prüft beide Wege auf einer neu angelegten,
nur über Loopback erreichbaren Paper-26.3-Welt. Der Test setzt absichtlich
fremdes Material, höher gelegene und innere Zäune, stehende und hängende
Spieler-Laternen, vertauschte Zaun-/Tor-Slots, eine Wasserfüllung, ein geöffnetes
Tor und einen späteren Diamantblock. Er prüft Konflikt-Abbruch, wiederholte
Anwendung, Neustart-Persistenz, Wiederherstellung aus dem neu geladenen
Schematic und das Erhalten bereits vorhandener Chunk-Tickets. Er verwendet
keine bestehende Spielwelt.

Der finale lokale Lauf vom 10. November 2026 bestand **8 fokussierte
Unit-Tests und 15 native Checks** mit Paper 26.3 und WorldEdit 7.4.6. Vor dem
Support-Fix reproduzierte eine neue Assertion den Verlust einer stehenden
Laterne. Nach dem Fix erfasste und entfernte der Datapack-Test 119 zulässige
Ziele und meldete drei geschützte Ziele. Zwei nachträglich veränderte
Umgebungsvoraussetzungen bewirkten einen vollständigen Apply-Abbruch mit zwei
Konflikten. Der WorldEdit-Test ließ zusätzlich eine erst nach der Sicherung
angebrachte Laterne samt Zaun stehen; seine unabhängige Restzählung meldete
korrekt vier verbleibende Zaun-Treffer und null Tor-Treffer.

Die Testwelt deaktiviert zufällige Blockticks, damit sich Grasboden nicht
zufällig während der Wasser-/Undo-Prüfung ändert. Gezielte Bodenänderungen
werden ausdrücklich getestet. Diese Nachweise betreffen die beschriebenen
Fälle; sie sind keine allgemeine Unterdrückung sämtlicher Minecraft-
Nachbarphysik und keine Prüfung eines abrupten Serverabsturzes.
