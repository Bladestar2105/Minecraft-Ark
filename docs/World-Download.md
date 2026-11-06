# Welt herunterladen

Die fertige Welt liegt unter
[GitHub Releases](https://github.com/Bladestar2105/Minecraft-Ark/releases/latest),
getrennt vom Quellcode. Für den Stand **2026-11-06** alle Dateien
`minecraft-ark-world-2026-11-06.tar.gz.part-000`, `...part-001` usw. sowie
`SHA256SUMS`, `world-manifest.json`, `WORLD-LICENSE.md` und `THIRD_PARTY.md`
herunterladen. Ein Teil ist höchstens 1.800 MiB groß; nur der letzte darf kleiner
sein. Anzahl und Gesamtgröße stehen in der Release-Beschreibung. Alle Teile müssen
zur selben Version gehören. Die Dateien direkt im frisch geklonten Repository ablegen.

```sh
# Im Verzeichnis mit den heruntergeladenen Release-Assets:
# Linux:
sha256sum -c SHA256SUMS
# macOS:
shasum -a 256 -c SHA256SUMS
```

Nach erfolgreicher Prüfung auf Linux oder macOS im Repository entpacken:

```sh
cat minecraft-ark-world-2026-11-06.tar.gz.part-* | tar -xz
```

Die dreistellige Nummerierung sorgt für die korrekte Reihenfolge. Erst wenn die
Prüfsummen aller Teile stimmen, entpacken; fehlende Teile nicht überspringen.
Auf Windows kann PowerShell die Teile binär zusammenfügen:

```powershell
$parts = Get-ChildItem minecraft-ark-world-2026-11-06.tar.gz.part-* | Sort-Object Name
$out = [IO.File]::Create((Join-Path $PWD 'minecraft-ark-world-2026-11-06.tar.gz'))
try {
  foreach ($part in $parts) {
    $inputFile = [IO.File]::OpenRead($part.FullName)
    try { $inputFile.CopyTo($out) } finally { $inputFile.Dispose() }
  }
} finally { $out.Dispose() }
```

Vorher mit `Get-FileHash -Algorithm SHA256 DATEINAME` jeden Teil gegen `SHA256SUMS`
prüfen. Danach mit 7-Zip die `.tar.gz` und anschließend die enthaltene `.tar` ins
Repository entpacken. Alternativ unter WSL die Linux-Befehle verwenden.

Das Archiv enthält den relativen Wurzelordner `data/`; das Ergebnis muss so aussehen:

```text
data/
  ark/
    level.dat
    datapacks/                    # eigene Inhalte; fremde Packs lädt der Installer
    dimensions/minecraft/
      overworld/
      the_nether/
      the_end/
      ark_creative/
  plugins/                       # geprüfte Spielkonfigurationen und Atlasdaten
```

Der Inhalt stammt aus einer konsistenten, bereinigten Serverkopie. Spielerprofile,
Inventare, Fortschritt, private Grundstücke, Konten, Schlüssel, Logs und RCON-
Einstellungen gehören nicht zum öffentlichen Download. Fremde JARs und die
ARR-Datapacks Dungeons and Taverns/Geophilic werden nicht mitgeliefert; der Installer
lädt sie vor dem ersten Start separat. Eigenes Terrain bleibt Teil der Weltkopie.
Die BlueMap-Webkarte wird bei Bedarf aus gespeicherten Chunks neu gerendert;
ein fertiger privater Webcache ist nicht Teil des Downloads.

Auch `data/minecraft/world_gen_settings.dat` innerhalb jeder Dimension gehört
zur benötigten Weltmetadatenstruktur. Diese Dateien nicht beim Kopieren auslassen.

Die Hauptwelt vollständig übertragen, einschließlich Dimensionen, Kartendaten,
Entitäten und POI. Einzelnes Kopieren von `region/` verliert Portale, Kreativwelt
und weitere Weltzustände. Das Format entspricht Paper 26.3; die Datei ist kein
Bedrock-`.mcworld`. Für Singleplayer ist die komplette Pluginmechanik nicht verfügbar.

Niemals über eine laufende oder bereits bespielte Installation entpacken. Für eine
bestehende Runde einen neuen Ordner verwenden und private Sicherungen behalten.
Lizenzen: eigener Code MIT, eigene Weltgestaltung CC BY 4.0; siehe
[WORLD-LICENSE.md](https://github.com/Bladestar2105/Minecraft-Ark/blob/main/WORLD-LICENSE.md).
