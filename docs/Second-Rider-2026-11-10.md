# Zweiter Dino-Reiter – Umsetzung und Prüfstand

> Historischer Prüfstand vor der abschließenden Laufzeitprüfung. Aktuelle Korrekturen, Kandidatenhashes, echte Unloads und Clientnachweise: [Abschlussbericht](Dinosaur-Final-Verification-2026-11-10.md).

## Umfang

ArkDinos 1.2.0 erhält einen eigenen zweiten Sitz für erwachsene **T-Rex,
Triceratops, Stegosaurus und Brachiosaurus** in den eingerichteten Dino-Welten.
Der Besitzer kann über die bestehende öffentliche Reitfreigabe einen Mitfahrer
zulassen. Kleine Arten, Pteranodon, Jungtiere und der wilde Mosasaurus erhalten
keinen zusätzlichen Sitz. Diese Änderung bleibt auf dem Entwurfs-PR; sie ist
noch nicht auf dem Live-Server installiert.

Die erste aufgestiegene Person bleibt Fahrer. Nur sie lenkt und befiehlt die
Dino-Angriffe. Der Mitfahrer sitzt 1,2 Blöcke hinter der tatsächlichen nativen
Fahrerposition, auf derselben Fußhöhe, und kann unabhängig umherschauen.
Schleichen entfernt nur den Mitfahrer. Steigt der Fahrer ab oder verliert seine
Berechtigung, endet der Ritt für beide. Ein Mitfahrer wird nicht automatisch
Fahrer. Eigentum und Pflege-/Menürechte ändern sich nicht.

Die Spieleranleitung beschreibt die Bedienung unter
[Zu zweit reiten](Dino-Riding-DE.md#zu-zweit-reiten).

## Technische Entscheidungen

Zwei direkt am Dino befestigte Spieler liegen auf derselben nativen
Sitzposition. Ein als weiterer Dino-Passagier angehängter Marker löst das
ebenfalls nicht. Deshalb verwendet der Mitfahrer ein separates, unsichtbares,
nicht dauerhaft gespeichertes Markerfahrzeug. Es folgt nach der nativen Physik
über `ServerTickEndEvent`.

Die gepinnte Paper-26.3-Anbindung bewegt den Marker mit `setPos` und aktualisiert
seinen Passagier mit `positionRider`. Gewöhnliches Reisen löst keine
Spieler-Teleportereignisse und keine Self-Teleportkorrekturen aus. Ein früherer
Versuch mit Bukkit-Teleports erzeugte solche Ereignisse laufend und wurde
verworfen. Bei einer nicht unterstützten Version oder ausgefallenen nativen
API werden zweite Sitze abgelehnt; der erste Sitz bleibt verfügbar.

Der native Fahrer hat je nach Trägertier und Skalierung einen kleinen eigenen
horizontalen Versatz. Dieser wird gemessen und mitgedreht. Eine feste Position
relativ zur Dinomitte wäre deshalb nicht derselbe Abstand zwischen den beiden
Spielern. Bewegung und Drehung prüfen den Mitfahrerraum einschließlich des
Zwischenbogens einer Kurve, Nachbarblock-Kollisionsformen, geladener Chunks,
Weltgrenze und Bauhöhe. Beim Aufschwimmen zählt der Kopfraum beider Spieler.

Der optionale Modelladapter synchronisiert beide Fahrzeuge pro Betrachter mit
dem sichtbaren Bridge-Modell. Er behält dabei die nativen Bewegungswerte bei
und kehrt nach 250 Millisekunden ohne neue Bridge-Pose zu ihnen zurück, auch im
Stillstand. Registrierung nach einem bereits gesendeten Marker-Spawn,
unterschiedliche Betrachter, gebündelte Entfernungen, Detach und Reload sind
separat geprüft. Der Marker bleibt ungedreht; die Position dreht sich mit dem
Dino. Geysers eigener Sitzwert wird für den Marker auf **1.02001** korrigiert;
sein unveränderter Wert **1.27001** setzt den Spieler zu hoch.

Quellen: [Sitzcontroller](../dino-plugin/src/main/java/de/ark/dinos/DinoSeats.java),
[Sitzregeln](../dino-plugin/src/main/java/de/ark/dinos/SeatRules.java),
[Modelladapter](../dino-plugin/src/main/java/de/ark/dinos/BetterModelAdapter.java).

## Beim Review gefundene und behobene Fehler

Der native Abstieg von einem Marker verwendet dessen eigene Höhe. Diese liegt
0,6 Blöcke über den sitzenden Spielerfüßen. Ein erzwungener Ausstieg konnte
damit unter einer niedrigen Decke enden, obwohl der Sitz selbst frei war. Die
gezielte Gegenprobe zeigte vor der Korrektur eine Blocküberschneidung direkt
nach dem Widerruf. Jetzt wird vor dem nativen Ausstieg nur der ohnehin zu
entfernende Marker auf die geprüfte freie Fußposition verschoben. Der Spieler
erhält weiterhin nur die normale native Absteigekorrektur.

Außerdem gewährte die bestehende Flug-Fallschutzfunktion bei einem normalen
Mitfahrerabstieg Schutz bis zur Weltunterkante. Die Gegenprobe maß **855 Ticks**
bei einem niedrigen Bodenabstieg. Der Zusatzsitz misst nun die tatsächliche
Bodenentfernung in der geladenen Spalte. Bis drei Blöcke gibt es keinen neuen
Effekt, bei größeren Abständen nur einen begrenzten Schutz für diesen Fallweg.
Längere, stärkere oder unendliche vorhandene Effekte bleiben bestehen.

## Nachweise

Die neue native Suite besteht mit **13 von 13 Prüfgruppen**. Sie umfasst
private/öffentliche Rechte, getrennte Positionen, alle vier großen Arten,
Ablehnung eines dritten Reiters und ungeeigneter Tiere, Bewegung und freie
Sicht ohne Teleport-Spam, Kampfbefehle, Schutz des Mitfahrers und Sitzhelfers,
beide Absteigewege, Widerruf bei vertauschten Besitzer-/Fahrerrollen,
Kurvenhindernisse, niedrige Ausstiegsdecken, Wasser/Kopfraum, begrenzten
Fallschutz, abgebrochene Mounts, externe Teleports, Moduswechsel, Tod,
Unload-Ereignis, Plugin-Abschaltung und einen echten Serverneustart.

Die Modellpaketprobe besteht in **neun Gruppen**, einschließlich vollständig
fehlender optionaler Modell-Jars. Die separate Geyser-Probe verwendet den
tatsächlichen gepinnten Passenger-Übersetzer und prüft lokale und entfernte
Marker-Mitfahrer, ausgegebene Sitzmetadaten, Rider-/Remove-Verbindungen,
Unsichtbarkeit sowie Dismount-/Removal-/Remount-/Close-Rücksetzungen. Ihr
unkorrigierter Markerfall ist reproduzierbar rot.

Auf demselben Plugin-Build bestehen zusätzlich die **17 bisherigen nativen
Steuerungsprüfungen** und die **35 nativen Kampfprüfungen**. Der vollständige
Build aller sieben Projektplugins einschließlich **13 Dino-Regeltestprogrammen**
ist erfolgreich und erzeugt denselben Pluginhash wie die nativen Läufe.

Der getestete Plugin-Build hat folgenden SHA-256:

```text
1a7a30aa010ca6a01460a9816f7c625f2819e16a5bfac99b167a980d07b4ca27
```

Reproduktionsquellen:

- [Native Tests](../dino-plugin/second-rider-runtime-test.mjs) und
  [Prüffälle](../dino-plugin/second-rider-runtime-checks.mjs)
- [Modellpaketprobe](../dino-plugin/model-seat-test.py)
- [Geyser-Sitzprobe](../dino-plugin/bedrock-seat-test.py)
- [Maschinenlesbare Zusammenfassung](verification/2026-11-10-second-rider.json)

Die nativen Tests benötigen JDK 25, `DINO_PAPER_JAR`, `DINO_PAPER_CACHE` und
einen gebauten Pluginstand. `DINO_PLUGIN_JAR` kann einen expliziten Snapshot
auswählen; `DINO_CONTROLS_OUTPUT` bestimmt das isolierte Testverzeichnis.
Die Paketproben verwenden die gepinnten Projektabhängigkeiten sowie gecachte
Mockito-/Byte-Buddy-/Objenesis-Testbibliotheken über `DINO_MODEL_TEST_LIBS`.
Der Parameter `marker-baseline` der Geyser-Probe reproduziert den unkorrigierten
Höhenfehler und soll fehlschlagen.

## Grenzen vor der Freigabe

Die nativen Tests laufen mit Java-Protokollclients in wegwerfbaren lokalen
Paper-Welten. Sie beweisen kein sichtbares Rendern auf einem offiziellen
Java-, Bedrock- oder Konsolenclient. Die Modellprobe kontrolliert
Engine-Ermittlung, Scheduler und Netzwerkgrenzen; ihr Reload-Test prüft die
Paketuhren und Sitzregistrierung, keinen vollständigen Modellneuaufbau.
Der Unload-Fall löst ausdrücklich ein `EntitiesUnloadEvent` aus. Er ist kein
Nachweis für das tatsächliche Entladen eines entfernt gerittenen Chunks. Der
Drehungstest kontrolliert die Chunkzahl in der geladenen Testumgebung; die
Abweisung ungeladener Zielchunks ist zusätzlich im Code geprüft.

Die vollständige gemeinsame BetterModel-/Geyser-Runtime und die sichtbare
Abnahme auf echten Geräten bleiben die bereits dokumentierten Freigabepunkte.
Der [Gesamtbericht](Dinosaur-Review-2026-11-10.md) beschreibt den bisherigen
Geyser-Discovery-Blocker und die übrigen Dino-Korrekturen. Die autorisierte
[Live-Zaunprüfung](Fence-Migration-DE.md) ist davon getrennt.
