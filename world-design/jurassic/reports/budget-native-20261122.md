# Modellbudget und offene Geräteabnahme, 22.11.2026

Die folgenden Zahlen wurden an den aktuellen Textquellen erneut gezählt. Die
Hashes aller neun Dinosaurier-BBModels sowie ihrer Geometrie- und Animationsexporte
stimmen mit dem nativen Lauf des endgültigen Gallimimus-Entwurfs überein.
[Der Messbericht](models-native-20261122.json) trennt die tatsächlich geprüften
JAR-Stände, Paket-Hashes und offenen Clientprüfungen.

Gezählt werden Würfelgeometrien und Knochen der Quelle, nicht GPU-Draw-Calls,
Dreiecke oder die vom Java-Compiler aufgeteilten Teile. Mosasaurus hat zusätzlich
fünf Locator-Elemente; sie zählen nicht als Würfel. PNG-Bytes stammen aus der
eingebetteten Originaltextur. Farbvarianten, Mipmaps und Engine-Atlanten sind in
dieser Quelltabelle nicht enthalten.

| Modell | Würfel | Knochen | Textur px | PNG Bytes | Clips | Keyframes | Animationen |
|---|---:|---:|---:|---:|---:|---:|---|
| ark_brachiosaurus | 127 | 10 | 64×64 | 4649 | 3 | 208 | idle, walk, attack |
| ark_dilophosaurus | 262 | 38 | 64×64 | 5024 | 7 | 251 | idle, walk, attack, growth_baby, growth_juvenile, growth_adult, spit |
| ark_gallimimus | 179 | 12 | 64×64 | 4868 | 5 | 229 | idle, walk, attack, run, graze |
| ark_mosasaurus | 311 | 16 | 64×64 | 4567 | 3 | 292 | idle, swim, attack |
| ark_pteranodon | 151 | 12 | 64×64 | 4793 | 8 | 165 | idle, walk, attack, growth_baby, growth_juvenile, growth_adult, flap, glide |
| ark_stegosaurus | 193 | 29 | 64×64 | 4710 | 6 | 342 | idle, walk, attack, growth_baby, growth_juvenile, growth_adult |
| ark_trex | 200 | 10 | 64×64 | 4939 | 3 | 121 | idle, walk, attack |
| ark_triceratops | 168 | 12 | 64×64 | 4916 | 6 | 226 | idle, walk, attack, growth_baby, growth_juvenile, growth_adult |
| ark_velociraptor | 171 | 10 | 64×64 | 5145 | 3 | 121 | idle, walk, attack |
| ark_egg_brachiosaurus | 66 | 1 | 256×256 | 35776 | 1 | 2 | idle |
| ark_egg_dilophosaurus | 66 | 1 | 256×256 | 37345 | 1 | 2 | idle |
| ark_egg_gallimimus | 66 | 1 | 256×256 | 37090 | 1 | 2 | idle |
| ark_egg_pteranodon | 66 | 1 | 256×256 | 35650 | 1 | 2 | idle |
| ark_egg_stegosaurus | 66 | 1 | 256×256 | 39650 | 1 | 2 | idle |
| ark_egg_trex | 66 | 1 | 256×256 | 39016 | 1 | 2 | idle |
| ark_egg_triceratops | 66 | 1 | 256×256 | 35106 | 1 | 2 | idle |
| ark_egg_velociraptor | 66 | 1 | 256×256 | 39901 | 1 | 2 | idle |

Neun unterschiedliche Dinosaurier ergeben **1762 Würfel / 149 Knochen** und
43611 komprimierte Texturbytes. Die acht land-/fluggebundenen Nestmodelle ergeben
**528 Würfel / 8 Knochen** und 299534 Texturbytes. Mosasaurus hat ein Ei-Item,
aber kein platziertes Landnest; der separate Item-Pack enthält neun Eisprites.

| Beispielbelegung | Tiere/Nester | Nominale Würfel | Nominale Knochen |
|---|---:|---:|---:|
| Filmszene: acht Gallimimus + ein T. rex | 9 | 1632 | 106 |
| Acht Nester, unabhängig von ihrer Art | 8 | 528 | 8 |
| Szene und acht Nester zusammen | 17 | 2160 | 114 |
| Je ein Exemplar aller neun Tiere und acht Nestmodelle | 17 | 2290 | 157 |

Texturen werden pro Asset geteilt; die Texturdateigröße ist daher nicht mit der
Tierzahl zu multiplizieren. Die acht Nesttexturen haben zusammen 524288 Pixel,
rechnerisch 2 MiB bei unkomprimiertem RGBA8 ohne Mipmaps. Die neun
Dinosauriertexturen haben 36864 Pixel, entsprechend 144 KiB unter derselben
Annahme. Das ist keine Messung des realen GPU-Speichers.

Die vorhandenen Nester mit je **66 Würfeln und einer 256×256-Textur** wurden
ausdrücklich betrachtet: Ein Nest hat rund 37% der Gallimimus-Würfelzahl,
aber die sechzehnfache Pixelzahl einer 64×64-Dinosauriertextur. Acht Nester können
somit bereits 528 zusätzliche Quellwürfel beitragen. Eine vereinfachte
Nestgeometrie, ein reduziertes UV-/Texturlayout oder dessen Gerätevergleich wurde
hier nicht umgesetzt oder als bestanden bewertet. Bestehende Quellen-/Export-
und Paketprüfungen ersetzen keine sichtbare UV-Abnahme und keinen Belastungstest
mit vielen Nestern auf Switch, Bedrock oder Java.

Der reale Java-Pack enthält 3705 Modell-JSONs und 7048 kompilierte Cuboids; diese
Paketbestände sind kein gleichzeitiger Szenenbestand. Der Bedrock-Pack enthält
17 Entitäten und 34 Geometrien einschließlich Farbvarianten. Weder diese Werte
noch die kleinen PNG-Dateien belegen stabile FPS, akzeptable Draw-Calls oder
eine sichere Tierzahl auf einem bestimmten Gerät. Solche Last- und Gerätetests
sowie die sichtbare Abnahme bleiben offen.

## Gallimimus: Fossilvorlage und gestalterische Entscheidungen

Die ausdrücklich angeforderte unabhängige Überarbeitung durch GPT-6.2-Sol mit
Reasoning High wurde als eigener 179-Würfel-/12-Knochen-Entwurf geliefert.
Er besitzt einen kleinen zahnlosen Kopf, einen gebogenen schmalen Hals, hohe
Hintergliedmaßen, drei Bodenspitzen pro Fuß, kleine dreifingrige Vorderarme und
einen langen auslaufenden Schwanz. Die Quelltests prüfen diese Geometrie,
Grasreichweite, Lauf-/Gehzyklen und das Budget. Eine beschriftete Quellvorschau
ist keine Ingame-Aufnahme.

Die anatomischen Proportionsziele orientieren sich an der Originalbeschreibung
von Gallimimus und ihren Skeletttafeln; das Blockmodell ist keine metrische
Fossilrekonstruktion. Referenz: [Osmólska, Roniewicz & Barsbold (1972),
Palaeontologia Polonica 27, 103–143, Tafeln XXIX–LIII](https://www.palaeontologia.pan.pl/Archive/1972-27_103-143_29-53.pdf).
Die zahnlose Schnabelgestaltung wird zusätzlich durch erhaltene
Schnabel-Weichgewebe bei Ornithomimiden gestützt; daraus wird keine bestimmte
Ernährungsweise für das Spielmodell bewiesen. Referenz: [Norell, Makovicky &
Currie (2001), The beaks of ostrich dinosaurs, Nature 412, 873–874](https://doi.org/10.1038/35091139).

Gefieder ist eine Rekonstruktionsannahme aus der verwandten Gattung
Ornithomimus, kein durch diesen Fund belegtes Gallimimus-Federkleid. Referenz:
[van der Reest, Wolfe & Currie (2016), Cretaceous Research 58,
108–117](https://doi.org/10.1016/j.cretres.2015.10.004). Die konkrete Farbpalette,
Musterung, vereinfachte Weichteile und die filmisch lesbare Herdensilhouette
sind gestalterische Entscheidungen. Die zitierten Fossilien belegen diese
Farben nicht. Geometrie, Textur und Bewegungen sind eigenständige Projektassets;
die Filmszene behauptet kein fossil belegtes Herden- oder Jagdverhalten.

Die gemessenen Quellschrittlängen bleiben 1.630854 Blöcke beim Gehen und
3.135356 beim Rennen; die Sattelfläche bleibt bei 2.09375 Quellblöcken. Die
gemeinsamen Bewegungsgrenzen wurden über 26004552 transformierte Würfelecken
aller neun Tiere dicht abgetastet. Das belegt die deterministische
Quell-/Runtime-Parität, keine vollständig geprüfte Darstellung beliebiger
Animationsmischungen, Geländelagen oder Clientkameras.
