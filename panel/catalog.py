"""Fixed admin configuration metadata and pure server-property validation."""
import re
from urllib.parse import urlsplit
from uuid import UUID


def _field(key, label, description, kind, default, group, **limits):
    return dict(key=key, label=label, description=description, type=kind, default=default,
                group=group, restart_required=True, **limits)


SERVER_FIELDS = {field['key']: field for field in [
    _field('motd', 'Serverbeschreibung', 'Text in der Minecraft-Serverliste.', 'string', 'A Minecraft Server', 'Allgemein', min_length=1, max_length=160),
    _field('gamemode', 'Standard-Spielmodus', 'Spielmodus für neue Spieler.', 'choice', 'survival', 'Spielbetrieb', options=['survival', 'creative', 'adventure', 'spectator']),
    _field('difficulty', 'Standard-Schwierigkeit', 'Standardwert beim Laden; einzelne Welten werden unter Welten verwaltet.', 'choice', 'easy', 'Spielbetrieb', options=['peaceful', 'easy', 'normal', 'hard']),
    _field('max-players', 'Spielerplätze', 'Maximale Zahl gleichzeitig verbundener Spieler.', 'integer', 20, 'Zugang', min=1, max=2147483647),
    _field('view-distance', 'Sichtweite', 'An Clients gesendete Entfernung in Chunks.', 'integer', 10, 'Leistung', min=2, max=32),
    _field('simulation-distance', 'Simulationsweite', 'Entfernung für aktive Spielabläufe in Chunks.', 'integer', 10, 'Leistung', min=2, max=32),
    _field('white-list', 'Whitelist verwenden', 'Begrenzt den Zugang auf freigegebene Spieler.', 'boolean', False, 'Zugang'),
    _field('allow-flight', 'Flugbewegungen erlauben', 'Verhindert Flug-Kicks, erteilt Spielern aber keine Flugfähigkeit.', 'boolean', False, 'Spielbetrieb'),
    _field('force-gamemode', 'Standardmodus beim Beitritt erzwingen', 'Setzt den vorherigen Spielmodus beim Verbinden zurück.', 'boolean', False, 'Spielbetrieb'),
    _field('enforce-whitelist', 'Whitelist sofort durchsetzen', 'Entfernt nicht freigegebene Spieler bei einer Whitelist-Aktualisierung.', 'boolean', False, 'Zugang'),
    _field('player-idle-timeout', 'Leerlauf bis zum Kick', 'Minuten ohne Aktivität; 0 deaktiviert den Leerlauf-Kick.', 'integer', 0, 'Zugang', min=0, max=2147483647),
    _field('spawn-protection', 'Vanilla-Spawnschutz', 'Schutzradius in Blöcken; 0 deaktiviert ihn. Zusätzliche Plugin-Schutzgebiete bleiben bestehen.', 'integer', 16, 'Spielbetrieb', min=0, max=29999984),
    _field('pause-when-empty-seconds', 'Pause bei leerem Server', 'Sekunden bis zur Pause ohne Spieler; -1 deaktiviert sie.', 'integer', -1, 'Leistung', min=-1, max=2147483647),
    _field('hide-online-players', 'Spielerliste verbergen', 'Verbirgt Spielernamen in der öffentlichen Statusantwort.', 'boolean', False, 'Datenschutz'),
    _field('log-ips', 'IP-Adressen protokollieren', 'Steuert die IP-Protokollierung des Servers; Plugins haben eigene Einstellungen.', 'boolean', True, 'Datenschutz'),
    _field('resource-pack', 'Ressourcenpaket-URL', 'Leere Angabe oder HTTP(S)-Downloadadresse des Ressourcenpakets.', 'string', '', 'Ressourcenpaket'),
    _field('require-resource-pack', 'Ressourcenpaket verlangen', 'Spieler müssen das angebotene Paket akzeptieren.', 'boolean', False, 'Ressourcenpaket'),
    _field('resource-pack-prompt', 'Ressourcenpaket-Hinweis', 'Hinweistext für die Paketabfrage des Clients.', 'string', '', 'Ressourcenpaket'),
    _field('resource-pack-id', 'Ressourcenpaket-ID', 'Leere Angabe oder UUID des Pakets.', 'string', '', 'Ressourcenpaket'),
    _field('resource-pack-sha1', 'Ressourcenpaket-Prüfsumme', 'Leere Angabe oder SHA-1-Prüfsumme mit 40 Hexadezimalzeichen.', 'string', '', 'Ressourcenpaket'),
    _field('max-tick-time', 'Watchdog-Zeitlimit', 'Millisekunden für einen einzelnen Tick; -1 deaktiviert den Watchdog.', 'integer', 60000, 'Erweitert', min=-1, max=9223372036854775807),
    _field('max-world-size', 'Maximaler Weltradius', 'Vanilla-Generierungsgrenze in Blöcken; ersetzt keine bestehende Weltgrenze.', 'integer', 29999984, 'Erweitert', min=1, max=29999984),
    _field('rate-limit', 'Paketlimit', 'Maximale eingehende Paketrate; 0 deaktiviert das Limit.', 'integer', 0, 'Erweitert', min=0, max=2147483647),
    _field('network-compression-threshold', 'Kompressionsschwelle', 'Pakete ab dieser Bytezahl werden komprimiert; -1 deaktiviert Kompression.', 'integer', 256, 'Erweitert', min=-1, max=2147483647),
    _field('broadcast-console-to-ops', 'Konsolenausgaben an Operatoren', 'Sendet Konsolen-Befehlsausgaben an angemeldete Operatoren.', 'boolean', True, 'Erweitert'),
    _field('enable-status', 'Statusabfragen erlauben', 'Zeigt den Serverstatus in der Minecraft-Serverliste an.', 'boolean', True, 'Zugang'),
    _field('enable-jmx-monitoring', 'JMX-Messwerte bereitstellen', 'Stellt Messwerte für eine separat konfigurierte JVM-Überwachung bereit.', 'boolean', False, 'Erweitert'),
]}


def _config(name, path, group, format='yaml', reload_target=None, restart_required=True, requires_stopped=True, protected_note=None):
    result = dict(name=name, path=path, group=group, format=format, reload_target=reload_target,
                  restart_required=restart_required, requires_stopped=requires_stopped)
    if protected_note:
        result['protected_note'] = protected_note
    return result


EXTRA_CONFIGS = {
    'server': _config('Minecraft-Eigenschaften', 'server.properties', 'Minecraft', 'properties', requires_stopped=False, protected_note='Zugangsdaten, Netzwerk- und Weltidentitäten bleiben geschützt.'),
    'bukkit': _config('Bukkit', 'bukkit.yml', 'Minecraft'),
    'spigot': _config('Spigot', 'spigot.yml', 'Minecraft'),
    'paper-global': _config('Paper – global', 'config/paper-global.yml', 'Paper'),
    'paper-world-defaults': _config('Paper – Weltvorgaben', 'config/paper-world-defaults.yml', 'Paper'),
    'paper-world-ark': _config('Paper – Survival', 'ark/dimensions/minecraft/overworld/paper-world.yml', 'Paper'),
    'paper-world-nether': _config('Paper – Nether', 'ark/dimensions/minecraft/the_nether/paper-world.yml', 'Paper'),
    'paper-world-end': _config('Paper – Ende', 'ark/dimensions/minecraft/the_end/paper-world.yml', 'Paper'),
    'paper-world-creative': _config('Paper – Kreativ', 'ark/dimensions/minecraft/ark_creative/paper-world.yml', 'Paper'),
    'mv-core': _config('Multiverse – Grundeinstellungen', 'plugins/Multiverse-Core/config.yml', 'Multiverse'),
    'mv-worlds': _config('Multiverse – Welten', 'plugins/Multiverse-Core/worlds.yml', 'Multiverse'),
    'mv-inventories': _config('Multiverse – Inventare', 'plugins/Multiverse-Inventories/config.yml', 'Multiverse'),
    'mv-groups': _config('Multiverse – Inventargruppen', 'plugins/Multiverse-Inventories/groups.yml', 'Multiverse'),
    'mv-portals': _config('Multiverse – Portaleinstellungen', 'plugins/Multiverse-Portals/config.yml', 'Multiverse'),
    'mv-portal-definitions': _config('Multiverse – Portaldefinitionen', 'plugins/Multiverse-Portals/portals.yml', 'Multiverse'),
    'worldguard': _config('WorldGuard – global', 'plugins/WorldGuard/config.yml', 'Schutz'),
    'worldguard-ark': _config('WorldGuard – Survival', 'plugins/WorldGuard/worlds/ark/config.yml', 'Schutz'),
    'worldguard-creative': _config('WorldGuard – Kreativ', 'plugins/WorldGuard/worlds/ark_creative/config.yml', 'Schutz'),
    'worldguard-regions-ark': _config('Schutzgebiete – Survival', 'plugins/WorldGuard/worlds/ark/regions.yml', 'Schutz'),
    'worldguard-regions-creative': _config('Schutzgebiete – Kreativ', 'plugins/WorldGuard/worlds/ark_creative/regions.yml', 'Schutz'),
    'worldedit': _config('WorldEdit', 'plugins/WorldEdit/config.yml', 'Bauen'),
    'griefprevention': _config('GriefPrevention', 'plugins/GriefPreventionData/config.yml', 'Schutz'),
    'parties': _config('Parties', 'plugins/Parties/config.yml', 'Spielinhalte'),
    'quest-conditions': _config('Quests – Bedingungen', 'plugins/Quests/storage/conditions.yml', 'Spielinhalte', reload_target='quests', restart_required=False, requires_stopped=False),
    'quest-actions': _config('Quests – Aktionen', 'plugins/Quests/storage/actions.yml', 'Spielinhalte', reload_target='quests', restart_required=False, requires_stopped=False),
    'floodgate': _config('Floodgate', 'plugins/floodgate/config.yml', 'Bedrock', protected_note='Authentifizierung und Schlüssel bleiben geschützt.'),
    'ark-dinos': _config('ARK – Dinosaurier', 'plugins/ArkDinos/config.yml', 'Spielinhalte'),
    'ark-atlas': _config('ARK – Atlas', 'plugins/ArkAtlas/config.yml', 'Karten', protected_note='map-id gehört zur gespeicherten Spielkarte und bleibt geschützt.'),
    'ark-atlas-sites': _config('ARK – Atlasorte', 'plugins/ArkAtlas/sites.yml', 'Karten'),
    'chunky': _config('Chunky', 'plugins/Chunky/config.yml', 'Weltgenerierung'),
    'bluemap-core': _config('BlueMap – Kern', 'plugins/BlueMap/core.conf', 'BlueMap', 'json'),
    'bluemap-plugin': _config('BlueMap – Plugin', 'plugins/BlueMap/plugin.conf', 'BlueMap', 'json'),
    'bluemap-webapp': _config('BlueMap – Oberfläche', 'plugins/BlueMap/webapp.conf', 'BlueMap', 'json'),
    'bluemap-webserver': _config('BlueMap – interner Webserver', 'plugins/BlueMap/webserver.conf', 'BlueMap', 'json'),
    'bluemap-storage': _config('BlueMap – Dateispeicher', 'plugins/BlueMap/storages/file.conf', 'BlueMap', 'json'),
    'bluemap-map-ark': _config('BlueMap – Survival-Karte', 'plugins/BlueMap/maps/ark.conf', 'BlueMap', 'json'),
    'bluemap-map-creative': _config('BlueMap – Kreativkarte', 'plugins/BlueMap/maps/ark_creative.conf', 'BlueMap', 'json'),
}


RULE_HELP = {'minecraft:' + name: {'label': label, 'description': description} for name, label, description in [
    ('advance_time', 'Tageszeit fortschreiten lassen', 'Lässt die Zeit in dieser Welt weiterlaufen, sodass Tag und Nacht einander ablösen.'),
    ('advance_weather', 'Wetter wechseln lassen', 'Erlaubt den natürlichen Wechsel zwischen klarem Wetter, Regen und Gewitter.'),
    ('allow_entering_nether_using_portals', 'Netherreisen durch Portale erlauben', 'Erlaubt Spielern, durch Netherportale in den Nether zu reisen.'),
    ('block_drops', 'Beute aus abgebauten Blöcken', 'Lässt abgebaute oder zerstörte Blöcke ihre vorgesehenen Gegenstände und Erfahrung fallen.'),
    ('block_explosion_drop_decay', 'Beuteverluste bei Block-Explosionen', 'Lässt bei Explosionen durch Blöcke, etwa Betten oder Respawnanker, einen Teil der Blockbeute verloren gehen.'),
    ('command_block_output', 'Befehlsblock-Meldungen an Operatoren', 'Erlaubt Meldungen ausgeführter Befehlsblöcke an Operatoren.'),
    ('command_blocks_work', 'Befehlsblöcke aktivieren', 'Erlaubt Befehlsblöcken, ihre gespeicherten Befehle auszuführen.'),
    ('drowning_damage', 'Schaden durch Ertrinken', 'Legt fest, ob Spieler bei Luftmangel unter Wasser Schaden nehmen.'),
    ('elytra_movement_check', 'Elytra-Bewegungen prüfen', 'Prüft zu schnelle Elytra-Flugbewegungen; andere Schutz-Plugins können zusätzliche Prüfungen vornehmen.'),
    ('ender_pearls_vanish_on_death', 'Geworfene Enderperlen beim Tod entfernen', 'Entfernt zuvor geworfene Enderperlen, wenn ihr Besitzer stirbt.'),
    ('entity_drops', 'Beute aus Fahrzeugen und Rahmen', 'Lässt zerstörte Fahrzeuge, Rahmen und ähnliche Objekte ihre vorgesehenen Gegenstände einschließlich des Inhalts fallen.'),
    ('fall_damage', 'Fallschaden', 'Legt fest, ob Spieler bei Stürzen Fallschaden erleiden.'),
    ('fire_damage', 'Feuer- und Lavaschaden', 'Legt fest, ob Spieler durch Feuer oder Lava Schaden erleiden.'),
    ('fire_spread_radius_around_player', 'Feuerausbreitung um Spieler', 'Feuerausbreitungsradius in Blöcken; 0 stoppt die Ausbreitung, -1 erlaubt sie auch ohne Spieler in der Nähe.'),
    ('forgive_dead_players', 'Zorn nach dem Tod beenden', 'Neutrale Kreaturen beenden ihren Zorn auf einen Spieler, wenn dieser in ihrer Nähe stirbt.'),
    ('freeze_damage', 'Erfrierungsschaden', 'Legt fest, ob Spieler in Pulverschnee Erfrierungsschaden erleiden.'),
    ('global_sound_events', 'Wichtige Geräusche weltweit hören', 'Lässt bestimmte Ereignisse, etwa das Erscheinen eines Bosses, in der ganzen Welt hörbar werden.'),
    ('immediate_respawn', 'Sofort wiedererscheinen', 'Lässt Spieler nach dem Tod sofort wiedererscheinen, ohne den Todesbildschirm anzuzeigen.'),
    ('keep_inventory', 'Inventar beim Tod behalten', 'An lässt Spieler Inventar und Erfahrung beim Tod behalten; aus lässt Gegenstände fallen und Erfahrung verloren gehen.'),
    ('lava_source_conversion', 'Neue Lavaquellen entstehen lassen', 'Erlaubt fließender Lava, zwischen passenden benachbarten Lavaquellen selbst zur Quelle zu werden.'),
    ('limited_crafting', 'Nur freigeschaltete Rezepte verwenden', 'Spieler können nur Gegenstände herstellen, deren Rezept sie bereits freigeschaltet haben.'),
    ('locator_bar', 'Spieler-Ortungsleiste anzeigen', 'Zeigt über die Ortungsleiste, in welcher Richtung andere Spieler sind.'),
    ('log_admin_commands', 'Admin-Befehle protokollieren', 'Schreibt die Ausgaben administrativer Befehle in das Serverprotokoll.'),
    ('max_block_modifications', 'Blocklimit pro Befehl', 'Begrenzt, wie viele Blöcke ein einzelner Befehl wie „fill“ oder „clone“ auf einmal verändern darf.'),
    ('max_command_forks', 'Maximale Befehlsverzweigungen', 'Begrenzt, wie viele Ausführungszweige Befehle wie „execute as“ erzeugen dürfen.'),
    ('max_command_sequence_length', 'Maximale Befehlsfolge', 'Begrenzt die Befehle, die Funktionen und Befehlsblock-Ketten in einer Ausführung abarbeiten dürfen.'),
    ('max_entity_cramming', 'Gedränge-Limit', 'Grenzwert für Gedrängeschaden bei dicht zusammenstehenden Lebewesen; 0 schaltet diesen Schaden aus.'),
    ('max_snow_accumulation_height', 'Maximale Schneeschichten', 'Begrenzt die Zahl aufeinanderliegender Schneeschichten bei Schneefall; 0 verhindert diese neue Schneeansammlung.'),
    ('mob_drops', 'Beute von Kreaturen', 'Lässt getötete Kreaturen ihre vorgesehenen Gegenstände und Erfahrungskugeln fallen.'),
    ('mob_explosion_drop_decay', 'Beuteverluste bei Kreaturen-Explosionen', 'Lässt einen Teil der Blockbeute verloren gehen, wenn Kreaturen wie Creeper Blöcke sprengen.'),
    ('mob_griefing', 'Blockänderungen durch Kreaturen', 'Erlaubt Kreaturen, Blöcke zu verändern, etwa durch Creeper-Explosionen, Endermen oder erntende Dorfbewohner.'),
    ('natural_health_regeneration', 'Natürliche Heilung', 'Erlaubt Spielern, bei ausreichend gefüllter Hungerleiste automatisch Gesundheit zurückzugewinnen.'),
    ('player_movement_check', 'Spielerbewegungen prüfen', 'Prüft zu schnelle Spielerbewegungen; Plugins können Bewegungen zusätzlich überwachen.'),
    ('players_nether_portal_creative_delay', 'Portalwartezeit im Kreativmodus', 'Wartezeit in Spielticks, die Kreativspieler vor einer Netherportal-Reise im Portal verbringen müssen.'),
    ('players_nether_portal_default_delay', 'Portalwartezeit außerhalb von Kreativ', 'Wartezeit in Spielticks, die Spieler außerhalb des Kreativmodus vor einer Netherportal-Reise im Portal verbringen müssen.'),
    ('players_sleeping_percentage', 'Anteil schlafender Spieler', 'Prozentanteil der Spieler, die schlafen müssen, damit die Nacht übersprungen werden kann.'),
    ('projectiles_can_break_blocks', 'Zerbrechliche Blöcke durch Projektile zerstören', 'Erlaubt auftreffenden Projektilen, dafür vorgesehene zerbrechliche Blöcke zu zerstören.'),
    ('pvp', 'Schaden zwischen Spielern (PvP)', 'Erlaubt Spielern, sich gegenseitig zu verletzen; Schutzgebiete können Schaden weiterhin verhindern.'),
    ('raids', 'Plünderer-Überfälle erlauben', 'Erlaubt Dorfüberfälle durch Plünderer; die Schutzregeln einzelner Dörfer gelten weiterhin.'),
    ('random_tick_speed', 'Tempo zufälliger Blockänderungen', 'Zufällige Blockaktualisierungen je Abschnitt und Tick; höhere Werte beschleunigen Wachstum und erhöhen die Last, 0 stoppt sie.'),
    ('reduced_debug_info', 'Debuganzeige einschränken', 'Reduziert Angaben in der Java-Debuganzeige, etwa genaue Koordinaten und Chunk-Daten.'),
    ('respawn_radius', 'Streuung am Weltstartpunkt', 'Zufälliger Abstand in Blöcken um den Weltstartpunkt beim Wiedererscheinen ohne eigenes gültiges Bett.'),
    ('send_command_feedback', 'Befehlsrückmeldungen im Chat', 'Zeigt Spielern Rückmeldungen ihrer ausgeführten Befehle im Chat.'),
    ('show_advancement_messages', 'Fortschritte im Chat ankündigen', 'Meldet neu erreichte Spielerfortschritte öffentlich im Chat.'),
    ('show_death_messages', 'Todesmeldungen anzeigen', 'Meldet im Chat, wenn ein Spieler stirbt.'),
    ('spawn_mobs', 'Kreaturen natürlich erscheinen lassen', 'Steuert das natürliche Erscheinen von Kreaturen; einige Arten haben zusätzliche eigene Schalter.'),
    ('spawn_monsters', 'Monster natürlich erscheinen lassen', 'Steuert das natürliche Erscheinen von Monstern wie Zombies und Skeletten.'),
    ('spawn_patrols', 'Plündererpatrouillen erscheinen lassen', 'Erlaubt, dass Plündererpatrouillen natürlich in der Umgebung von Spielern erscheinen.'),
    ('spawn_phantoms', 'Phantome erscheinen lassen', 'Erlaubt Phantome bei Spielern, die mehrere Nächte nicht geschlafen haben.'),
    ('spawn_wandering_traders', 'Fahrende Händler erscheinen lassen', 'Erlaubt das natürliche Erscheinen fahrender Händler.'),
    ('spawn_wardens', 'Wardens erscheinen lassen', 'Erlaubt das Erscheinen von Wardens durch dafür geeignete Sculk-Kreischer.'),
    ('spawner_blocks_work', 'Spawner-Blöcke aktivieren', 'Erlaubt Spawner-Blöcken, ihre vorgesehenen Kreaturen erscheinen zu lassen.'),
    ('spectators_generate_chunks', 'Gelände für Zuschauer erzeugen', 'Erlaubt Zuschauern, bislang unerzeugte Weltabschnitte beim Erkunden zu generieren.'),
    ('spread_vines', 'Ranken ausbreiten lassen', 'Gewöhnliche Ranken dürfen auf Nachbarblöcke wachsen; andere Rankenarten bleiben davon unberührt.'),
    ('tnt_explodes', 'TNT zünden und explodieren lassen', 'Erlaubt, dass TNT aktiviert wird und explodiert.'),
    ('tnt_explosion_drop_decay', 'Beuteverluste bei TNT-Explosionen', 'Bestimmt, ob TNT-Explosionen zusätzlich einen Teil der normalerweise entstehenden Blockbeute vernichten.'),
    ('universal_anger', 'Zorn gegen alle Spieler', 'Lässt verärgerte neutrale Kreaturen jeden nahen Spieler angreifen, nicht nur den ursprünglichen Auslöser.'),
    ('water_source_conversion', 'Neue Wasserquellen entstehen lassen', 'Erlaubt fließendem Wasser, zwischen passenden benachbarten Wasserquellen selbst zur Quelle zu werden.'),
]}


def rule_help(name):
    return RULE_HELP.get(name, {'label': name, 'description': 'Die genaue Wirkung dieser zusätzlichen Spielregel ist dem Panel nicht bekannt; bitte die Dokumentation der Serverversion beachten.'}).copy()


_CONFIG_DESCRIPTIONS = {
    'geyser': 'Verbindung und Darstellung für Bedrock-Spieler, etwa Serverliste, Koordinaten und Ressourcenpakete.',
    'quests': 'Aufbau der Missionen mit Aufgaben, Voraussetzungen, Fortschritt und Belohnungen.',
    'quest-settings': 'Allgemeines Verhalten der Missionen, etwa Sprache, Annahmebestätigung und gleichzeitige Aufträge.',
    'permissions': 'Berechtigungen für Befehle und Plugin-Funktionen sowie deren Weitergabe an untergeordnete Rechte.',
    'server': 'Gespeicherte Minecraft-Startvorgaben, etwa Spielerplätze, Sichtweite und Ressourcenpaket.',
    'bukkit': 'Bukkit-Grundfunktionen, Spawn-Vorgaben und serverweite Abläufe.',
    'spigot': 'Spigot-Verhaltens- und Leistungsoptionen einschließlich seiner Vorgaben für Welten.',
    'paper-global': 'Serverweite Paper-Optionen für Abläufe, Netzwerk und Leistungsgrenzen.',
    'paper-world-defaults': 'Gemeinsame Paper-Vorgaben für Welten; einzelne Welten können diese Werte überschreiben.',
    'paper-world-ark': 'Paper-Einstellungen der Survival-Welt; nicht gesetzte Werte stammen aus den Weltvorgaben.',
    'paper-world-nether': 'Paper-Einstellungen des Nethers; nicht gesetzte Werte stammen aus den Weltvorgaben.',
    'paper-world-end': 'Paper-Einstellungen des Endes; nicht gesetzte Werte stammen aus den Weltvorgaben.',
    'paper-world-creative': 'Paper-Einstellungen der Kreativwelt; nicht gesetzte Werte stammen aus den Weltvorgaben.',
    'mv-core': 'Grundverhalten der Weltverwaltung durch Multiverse, etwa Weltladen und Spielerwechsel.',
    'mv-worlds': 'Gespeicherte Einstellungen je Multiverse-Welt, etwa Schwierigkeit, Spielmodus und Ankunftspunkt.',
    'mv-inventories': 'Steuert, welche Inventar- und Spielerwerte Multiverse bei Weltwechseln austauscht.',
    'mv-groups': 'Legt fest, welche Welten Inventare und Erfahrung teilen; ARK hält Survival und Kreativ getrennt.',
    'mv-portals': 'Allgemeines Verhalten der Multiverse-Portale und ihrer Benutzung.',
    'mv-portal-definitions': 'Bereiche und Reiseziele der Multiverse-Portale zwischen den Welten.',
    'worldguard': 'Serverweite Vorgaben für WorldGuard-Schutz und die Behandlung geschützter Bereiche.',
    'worldguard-ark': 'WorldGuard-Vorgaben speziell für den Schutz in der Survival-Welt.',
    'worldguard-creative': 'WorldGuard-Vorgaben speziell für den Schutz in der Kreativwelt.',
    'worldguard-regions-ark': 'Umrisse, Mitglieder und Rechte der Schutzgebiete in Survival, etwa Spawn und Dörfer.',
    'worldguard-regions-creative': 'Umrisse, Mitglieder und Rechte der Schutzgebiete in der Kreativwelt.',
    'worldedit': 'Einstellungen und Arbeitsgrenzen der WorldEdit-Bauwerkzeuge.',
    'griefprevention': 'Grundstücksschutz, verfügbare Anspruchsblöcke und Regeln für private Spielergrundstücke.',
    'parties': 'Regeln und Bedienung der Spielergruppen, etwa Einladungen, Gruppenrechte und Nachrichten.',
    'quest-conditions': 'Definiert benannte Voraussetzungen und Einschränkungen, auf die Missionen verweisen.',
    'quest-actions': 'Definiert benannte Aktionen und Befehlsabläufe, die Missionen auslösen können.',
    'floodgate': 'Regelt die Anmeldung von Bedrock-Spielern über Geyser, etwa deren Namensdarstellung.',
    'ark-dinos': 'Verhalten der ARK-Dinosaurier, insbesondere Zähmen, Besitz und Folgen.',
    'ark-atlas': 'Verwaltung des Weltatlas im Spiel und seiner vorhandenen Karteninstanz.',
    'ark-atlas-sites': 'Namen, Symboltypen und Koordinaten der Orte, die auf dem Weltatlas markiert werden.',
    'chunky': 'Grundeinstellungen von Chunky für die Vorbereitung von Weltabschnitten und Fortschrittsmeldungen.',
    'bluemap-core': 'Steuert BlueMap-Rechenaufwand, unter anderem Render-Threads und Aktualisierungsintervalle.',
    'bluemap-plugin': 'Steuert die Zusammenarbeit des BlueMap-Plugins mit dem laufenden Minecraft-Server.',
    'bluemap-webapp': 'Darstellung und Bedienung der Browserkarte, etwa ihre Standardansicht.',
    'bluemap-webserver': 'Adresse und Port des internen BlueMap-Webservers, über den das Panel die Karte abruft.',
    'bluemap-storage': 'Legt fest, wo BlueMap berechnete Kartendaten im Dateisystem ablegt.',
    'bluemap-map-ark': 'Rendergrenzen, Detailstufen und verfügbare Ansichten der Survival-Browserkarte.',
    'bluemap-map-creative': 'Rendergrenzen, Detailstufen und verfügbare Ansichten der Kreativ-Browserkarte.',
}


def config_description(config_id):
    return _CONFIG_DESCRIPTIONS.get(config_id, 'Konfiguration dieser Komponente; die Bedeutung zusätzlicher Felder steht in deren Dokumentation.')

_PROTECTED = {'online-mode', 'enforce-secure-profile', 'prevent-proxy-connections', 'level-name', 'level-seed',
              'server-ip', 'server-port', 'enable-rcon', 'enable-query', 'query.port', 'text-filtering-config',
              'pvp', 'allow-nether', 'spawn-monsters', 'enable-command-block'}


def protected_property(key):
    key = key.strip().lower()
    return key in _PROTECTED or key.startswith(('rcon.', 'management-server')) or any(word in key for word in ['secret', 'password', 'token'])



# These properties stay optional: help must not insert new startup or world-generation values.
EXTRA_SERVER_HELP = {key: {'label': label, 'description': description} for key, label, description in [
    ('accepts-transfers', 'Weitergeleitete Spieler annehmen', 'Erlaubt Verbindungen, die ein anderer Minecraft-Server per Transferfunktion hierher vermittelt.'),
    ('broadcast-rcon-to-ops', 'Fernkonsolen-Meldungen an Operatoren', 'Sendet Rückmeldungen von Fernkonsolen-Befehlen an angemeldete Operatoren.'),
    ('bug-report-link', 'Adresse für Fehlerberichte', 'Link im Spiel für Fehlerberichte an die Serverbetreiber; eine leere Angabe blendet diesen zusätzlichen Link aus.'),
    ('chat-spam-threshold-seconds', 'Chat-Spam-Grenze in Sekunden', 'Grenze des mit der Zeit sinkenden Chat-Spam-Zählers in Sekunden; jede Nachricht addiert eine Sekunde, 0 deaktiviert diese Kicks.'),
    ('command-spam-threshold-seconds', 'Befehls-Spam-Grenze in Sekunden', 'Grenze des mit der Zeit sinkenden Befehls-Spam-Zählers in Sekunden; jeder Befehl addiert eine Sekunde, 0 deaktiviert diese Kicks.'),
    ('debug', 'Diagnosemodus des Servers', 'Aktiviert interne Diagnosefunktionen des Minecraft-Servers für die Fehlersuche.'),
    ('enable-code-of-conduct', 'Verhaltensregeln bestätigen lassen', 'Zeigt beim Beitritt die hinterlegten Verhaltensregeln an und verlangt deren Zustimmung.'),
    ('entity-broadcast-range-percentage', 'Sichtweite für Kreaturen und Objekte (%)', 'Skaliert die Übertragungsreichweite von Spielern, Kreaturen und anderen Objekten; 100 ist die normale Reichweite.'),
    ('function-permission-level', 'Befehlsrechte für Funktionen', 'Berechtigungsstufe von 0 bis 4, mit der Datapack-Funktionen ihre Befehle ausführen.'),
    ('generate-structures', 'Natürliche Bauwerke in neuen Welten', 'Vorgabe bei neu angelegten Welten, ob natürliche Bauwerke erzeugt werden; bestehende Welt-Einstellungen bleiben erhalten.'),
    ('generator-settings', 'Generatoroptionen für neue Welten', 'Zusätzliche Generator-Einstellungen im JSON-Format, etwa für eine neu angelegte Flachwelt.'),
    ('hardcore', 'Hardcore für neue Welten', 'Hardcore-Vorgabe für neu angelegte Welten: Nach dem Tod ist dort kein normales Weiterspielen als Überlebender möglich.'),
    ('initial-disabled-packs', 'Anfangs deaktivierte Datenpakete', 'Kommagetrennte Datenpakete, die beim ersten Anlegen einer neuen Welt deaktiviert sein sollen.'),
    ('initial-enabled-packs', 'Anfangs aktive Datenpakete', 'Kommagetrennte Datenpakete, die für eine neu angelegte Welt zunächst aktiviert werden sollen.'),
    ('level-type', 'Welttyp für neue Welten', 'Wählt den Generator beim Anlegen einer neuen Welt, etwa normal oder flat; bestehende Welten werden nicht umgestellt.'),
    ('max-chained-neighbor-updates', 'Limit verketteter Blockaktualisierungen', 'Begrenzt zusammenhängende Aktualisierungen benachbarter Blöcke, etwa bei großen Redstone-Schaltungen.'),
    ('op-permission-level', 'Standardrechte für neue Operatoren', 'Vorgegebene Berechtigungsstufe von 0 bis 4 für neu vergebene Operatorenrechte; höhere Stufen erlauben mehr Verwaltungsbefehle.'),
    ('region-file-compression', 'Kompression der Weltdaten', 'Bestimmt die Kompression neu geschriebener Regionsdaten, zum Beispiel deflate oder lz4.'),
    ('status-heartbeat-interval', 'Intervall für Management-Statusmeldungen', 'Abstand in Sekunden für Statusmeldungen der aktiven Management-Schnittstelle an verbundene Programme; 0 deaktiviert diese Meldungen.'),
    ('sync-chunk-writes', 'Weltabschnitte synchron speichern', 'Fordert beim Speichern von Weltabschnitten synchrone Dateischreibvorgänge an; dies kann langsamer sein.'),
    ('text-filtering-version', 'Version des externen Textfilters', 'Protokollversion eines eingerichteten externen Textfilters; nur mit dessen Konfiguration und nach Vorgabe des Filterdiensts wirksam.'),
    ('use-native-transport', 'Native Netzwerkverarbeitung verwenden', 'Erlaubt dem Java-Spielserver, verfügbare plattformspezifische Netzwerkfunktionen zu nutzen.'),
]}


def field_for(key, value):
    if protected_property(key):
        return None
    if key in SERVER_FIELDS:
        field = SERVER_FIELDS[key].copy()
        if 'options' in field:
            field['options'] = list(field['options'])
        return field
    if type(value) is bool or str(value).lower() in ['true', 'false']:
        kind, default = 'boolean', str(value).lower() == 'true'
    elif type(value) is int or re.fullmatch(r'-?[0-9]+', str(value)):
        kind, default = 'integer', int(value)
    else:
        kind, default = 'string', str(value)
    field = _field(key, key, 'Zusätzliche Eigenschaft der tatsächlich vorhandenen Serverkonfiguration.', kind, default,
                   'Weitere Einstellungen', **({'max_length': 4096} if kind == 'string' else {}))
    field.update(EXTRA_SERVER_HELP.get(key, {}))
    return field


def validate_field(field, value):
    label = field.get('label', field['key'])
    kind = field['type']
    if kind == 'boolean':
        if type(value) is not bool:
            raise ValueError(f'{label}: Bitte einen Wahrheitswert wählen.')
        return
    if kind == 'integer':
        if type(value) is not int:
            raise ValueError(f'{label}: Bitte eine ganze Zahl angeben.')
        if ('min' in field and value < field['min']) or ('max' in field and value > field['max']):
            raise ValueError(f'{label}: Die Zahl liegt außerhalb des erlaubten Bereichs.')
        return
    if not isinstance(value, str):
        raise ValueError(f'{label}: Bitte Text angeben.')
    try:
        value.encode('utf-8')
    except UnicodeEncodeError as error:
        raise ValueError(f'{label}: Der Text enthält ungültige Unicode-Zeichen.') from error
    if any(character in value for character in '\r\n\0') or not field.get('min_length', 0) <= len(value) <= field.get('max_length', 4096):
        raise ValueError(f'{label}: Textlänge oder enthaltene Steuerzeichen sind nicht erlaubt.')
    if kind == 'choice' and value not in field['options']:
        raise ValueError(f'{label}: Bitte einen der angebotenen Werte wählen.')
    if field['key'] == 'resource-pack' and value:
        try:
            url = urlsplit(value)
            url.port  # Validate optional port syntax and range.
            valid = url.scheme in ['http', 'https'] and bool(url.netloc and url.hostname) and not any(c.isspace() for c in value)
        except ValueError:
            valid = False
        if not valid:
            raise ValueError(f'{label}: Bitte eine HTTP(S)-Adresse oder eine leere Angabe verwenden.')
    if field['key'] == 'resource-pack-sha1' and value and not re.fullmatch(r'[0-9a-fA-F]{40}', value):
        raise ValueError(f'{label}: Die Prüfsumme muss aus 40 Hexadezimalzeichen bestehen.')
    if field['key'] == 'resource-pack-id' and value:
        try:
            if str(UUID(value)) != value.lower():
                raise ValueError
        except ValueError as error:
            raise ValueError(f'{label}: Bitte eine gültige UUID oder eine leere Angabe verwenden.') from error
