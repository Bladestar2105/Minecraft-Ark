package de.ark.atlas;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArkAtlas extends JavaPlugin implements Listener {
    private static final String LEGACY_WORLD = "ark";
    private static final String LOCAL_STARTER = "ark_local_map";
    private static final String PREPARING = "Der Weltatlas wird vorbereitet. Versuche /atlas später erneut.";
    private static final String LEGEND = "Legende: Ziel = Spawn, Haus = Dorf, Rot = Dino, Blau = Händler, Rotes Kreuz = Abenteuer, Pfeil = du.";
    private final Map<String, WorldAtlas> atlases = new LinkedHashMap<>();
    private final Map<UUID, Map<String, ViewState>> states = new HashMap<>();
    private NamespacedKey atlasKey;
    private NamespacedKey worldKey;
    private SpawnLeaderboard leaderboard;

    @Override public void onEnable() {
        atlasKey = new NamespacedKey(this, "ark_atlas");
        worldKey = new NamespacedKey(this, "atlas_world");
        saveDefaultConfig();
        if (!new File(getDataFolder(), "sites.yml").exists()) saveResource("sites.yml", false);
        atlases.put(LEGACY_WORLD, new WorldAtlas(LEGACY_WORLD, "map-id", AtlasRules.LEGACY, null, List.of()));
        Bukkit.getPluginManager().registerEvents(this, this);
        leaderboard = new SpawnLeaderboard(this);
        try { loadAtlases(); }
        catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            getLogger().warning("Atlas wird vorbereitet: " + e.getMessage());
        }
        attachMaps();
        Bukkit.getOnlinePlayers().forEach(this::scheduleStarter);
    }

    @Override public void onDisable() {
        if (leaderboard != null) leaderboard.close();
        atlases.values().forEach(WorldAtlas::detach);
        states.clear();
    }

    private File localFile(String name) throws IOException {
        if (name == null || name.isBlank()) throw new IOException("Atlas-Dateipfad fehlt");
        Path root = getDataFolder().toPath().toAbsolutePath().normalize();
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || path.equals(root)) throw new IOException("Atlas-Datei außerhalb des Pluginordners");
        for (Path part = path; part != null && part.startsWith(root); part = part.getParent()) {
            if (Files.isSymbolicLink(part)) throw new IOException("Atlas-Datei darf kein Symlink sein");
        }
        return path.toFile();
    }

    private BufferedImage loadImage(File file) throws IOException {
        if (!file.exists()) return null;
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Geländekarte fehlt: " + file.getName());
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Geländekarte ist kein gültiges PNG");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int size = reader.getWidth(0);
                if (!reader.getFormatName().equalsIgnoreCase("png") || size < 128 || size > 2048 || reader.getHeight(0) != size)
                    throw new IOException("Geländekarte muss quadratisch und 128–2048 Pixel groß sein");
                return reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private static double number(ConfigurationSection config, String key, double fallback) throws IOException {
        Object value = config.get(key, fallback);
        if (!(value instanceof Number n) || !Double.isFinite(n.doubleValue())) throw new IOException("Ungültiger Atlas-Wert: " + key);
        return n.doubleValue();
    }

    private WorldAtlas loadAtlas(String world, String mapPath, String sitesFile, String terrainFile) throws IOException, InvalidConfigurationException {
        if (!world.matches("[A-Za-z0-9_][A-Za-z0-9_-]{0,63}")) throw new IOException("Ungültiger Atlas-Weltname");
        YamlConfiguration config = new YamlConfiguration();
        config.load(localFile(sitesFile));
        if (!world.equals(config.getString("world"))) throw new IOException("Atlas-Welt stimmt nicht mit sites.yml überein: " + world);
        double extent = number(config, "extent", AtlasRules.EXTENT);
        if (extent != Math.rint(extent) || extent < 64 || extent > 15000000) throw new IOException("extent muss eine ganze Zahl von 64 bis 15000000 sein");
        List<Integer> zooms = List.of(1, 4, 8);
        if (config.contains("zoom-divisors")) {
            if (!(config.get("zoom-divisors") instanceof List<?> values) || values.stream().anyMatch(value -> !(value instanceof Integer)))
                throw new IOException("zoom-divisors benötigt ganze Zahlen");
            zooms = config.getIntegerList("zoom-divisors");
        }
        AtlasRules.Layout layout = new AtlasRules.Layout(number(config, "origin-x", 0), number(config, "origin-z", 0), (int) extent, zooms);
        List<Site> sites = new ArrayList<>();
        if (!config.isList("sites") || config.getList("sites").size() != config.getMapList("sites").size())
            throw new IOException("sites.yml benötigt eine Liste von Orten unter sites");
        for (Map<?, ?> entry : config.getMapList("sites")) {
            Object name = entry.get("name"), type = entry.get("type"), x = entry.get("x"), z = entry.get("z");
            if (!(name instanceof String label) || label.isBlank() || label.length() > 80 ||
                    !(type instanceof String kind) || !(x instanceof Number nx) || !(z instanceof Number nz) ||
                    !Double.isFinite(nx.doubleValue()) || !Double.isFinite(nz.doubleValue()) ||
                    Math.abs(nx.doubleValue() - layout.originX()) > layout.extent() || Math.abs(nz.doubleValue() - layout.originZ()) > layout.extent())
                throw new IOException("Ungültiger Atlas-Ort: " + entry);
            MapCursor.Type icon = switch (kind) {
                case "spawn" -> MapCursor.Type.TARGET_POINT;
                case "village" -> MapCursor.Type.MANSION;
                case "dino" -> MapCursor.Type.RED_MARKER;
                case "merchant" -> MapCursor.Type.BLUE_MARKER;
                case "adventure" -> MapCursor.Type.RED_X;
                default -> throw new IOException("Unbekannter Atlas-Ortstyp: " + kind);
            };
            sites.add(new Site(label, nx.doubleValue(), nz.doubleValue(), icon));
        }
        if (sites.size() > 256) throw new IOException("Höchstens 256 Atlas-Orte pro Welt erlaubt");
        return new WorldAtlas(world, mapPath, layout, loadImage(localFile(terrainFile)), List.copyOf(sites));
    }

    private void loadAtlases() throws IOException, InvalidConfigurationException {
        Map<String, WorldAtlas> loaded = new LinkedHashMap<>();
        loaded.put(LEGACY_WORLD, loadAtlas(LEGACY_WORLD, "map-id", "sites.yml", "terrain.png"));
        ConfigurationSection worlds = getConfig().getConfigurationSection("worlds");
        if (worlds != null) for (String world : worlds.getKeys(false)) {
            if (loaded.containsKey(world) || loaded.size() >= 8) throw new IOException("Doppelte Atlas-Welt oder mehr als acht Welten");
            ConfigurationSection settings = worlds.getConfigurationSection(world);
            if (settings == null) throw new IOException("Atlas-Welt benötigt eine Zuordnung");
            if (!settings.getBoolean("enabled", true)) continue;
            loaded.put(world, loadAtlas(world, "worlds." + world + ".map-id",
                    settings.getString("sites-file", world + "/sites.yml"), settings.getString("terrain-file", world + "/terrain.png")));
        }
        Map<Integer, String> identities = new HashMap<>();
        for (WorldAtlas atlas : loaded.values()) {
            WorldAtlas previous=atlases.get(atlas.world);
            if(previous!=null && previous.image!=null && atlas.image==null)
                throw new IOException("Fehlendes Kartenbild für aktiven Atlas: "+atlas.world);
            int id = getConfig().getInt(atlas.mapPath, -1);
            if (id >= 0 && identities.putIfAbsent(id, atlas.world) != null) throw new IOException("MapView-ID wird von mehreren Atlas-Welten verwendet");
            MapView existing = id < 0 ? null : Bukkit.getMap(id);
            if (existing != null && existing.getWorld() != null && !existing.getWorld().getName().equals(atlas.world))
                throw new IOException("MapView-ID gehört zu einer anderen Welt: " + id);
        }
        atlases.values().forEach(WorldAtlas::detach);
        atlases.clear();
        atlases.putAll(loaded);
        states.clear();
    }

    private void attachMaps() {
        for (WorldAtlas atlas : atlases.values()) {
            World world = Bukkit.getWorld(atlas.world);
            if (world == null) continue;
            int id = getConfig().getInt(atlas.mapPath, -1);
            atlas.map = id >= 0 ? Bukkit.getMap(id) : null;
            if (atlas.map == null && atlas.image != null) {
                atlas.map = Bukkit.createMap(world);
                getConfig().set(atlas.mapPath, atlas.map.getId());
                saveConfig();
            }
            if (atlas.map == null || atlas.renderer != null) continue;
            if (atlas.map.getWorld() != null && !atlas.map.getWorld().equals(world)) {
                getLogger().warning("Atlas-MapView gehört zu einer anderen Welt; ID prüfen: " + id);
                atlas.map = null;
                continue;
            }
            for (MapRenderer old : List.copyOf(atlas.map.getRenderers())) atlas.map.removeRenderer(old);
            atlas.map.setWorld(world);
            atlas.map.setCenterX((int) atlas.layout.originX());
            atlas.map.setCenterZ((int) atlas.layout.originZ());
            atlas.map.setScale(MapView.Scale.FARTHEST);
            atlas.map.setTrackingPosition(false);
            atlas.map.setUnlimitedTracking(false);
            atlas.map.setLocked(true);
            atlas.renderer = new AtlasRenderer(atlas);
            atlas.map.addRenderer(atlas.renderer);
        }
    }

    private boolean isAtlas(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP &&
                item.getItemMeta().getPersistentDataContainer().has(atlasKey, PersistentDataType.BYTE);
    }
    private boolean isAtlas(ItemStack item, String world) {
        return isAtlas(item) && AtlasRules.atlasMatches(item.getItemMeta().getPersistentDataContainer().get(worldKey, PersistentDataType.STRING), world);
    }
    private boolean hasAtlas(Player player, String world) {
        for (ItemStack item : player.getInventory().getContents()) if (isAtlas(item, world)) return true;
        return false;
    }
    private boolean hasAnyMap(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && (item.getType() == Material.MAP || item.getType() == Material.FILLED_MAP) && !isAtlas(item)) return true;
        }
        return false;
    }
    private static String worldSuffix(String world) { return UUID.nameUUIDFromBytes(world.getBytes(StandardCharsets.UTF_8)).toString(); }
    private NamespacedKey starterKey(String world) { return new NamespacedKey(this, LEGACY_WORLD.equals(world) ? "ark_atlas_starter" : "starter_" + worldSuffix(world)); }
    private static String localTag(String world) { return LEGACY_WORLD.equals(world) ? LOCAL_STARTER : LOCAL_STARTER + "_" + worldSuffix(world); }
    private boolean recorded(Player player, String world) { return player.getPersistentDataContainer().has(starterKey(world), PersistentDataType.BYTE); }
    private void record(Player player, String world) { player.getPersistentDataContainer().set(starterKey(world), PersistentDataType.BYTE, (byte) 1); }
    private WorldAtlas atlas(Player player) { return atlases.get(player.getWorld().getName()); }
    private ViewState state(Player player, WorldAtlas atlas) { return states.computeIfAbsent(player.getUniqueId(), key -> new HashMap<>()).computeIfAbsent(atlas.world, key -> new ViewState()); }

    private ItemStack atlasItem(WorldAtlas atlas, boolean full) {
        ItemStack item = new ItemStack(full ? Material.FILLED_MAP : Material.MAP);
        var meta = item.getItemMeta();
        if (full) ((MapMeta) meta).setMapView(atlas.map);
        meta.displayName(Component.text(full ? "ARK-Weltatlas · " + atlas.world : "ARK-Umgebungskarte", NamedTextColor.GOLD));
        meta.lore(full ? List.of(Component.text("Rechtsklick: Zoom wechseln · Norden oben", NamedTextColor.GRAY),
                Component.text(atlas.layout.zooms().stream().map(z -> Integer.toString(2 * atlas.layout.extent() / z)).reduce((a, b) -> a + " / " + b).orElse("") + " Blöcke", NamedTextColor.GRAY),
                Component.text(LEGEND, NamedTextColor.GRAY), Component.text("Bedrock: Ortsnamen über die Legende · /atlas", NamedTextColor.GRAY)) :
                List.of(Component.text("Benutzen zeichnet deine Umgebung.", NamedTextColor.GRAY), Component.text(PREPARING, NamedTextColor.GRAY)));
        if (full) {
            meta.getPersistentDataContainer().set(atlasKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(worldKey, PersistentDataType.STRING, atlas.world);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void applyGrant(Player player, WorldAtlas atlas, AtlasRules.Grant decision) {
        switch (decision) {
            case GIVE, GIVE_LOCAL -> {
                int slot = player.getInventory().firstEmpty();
                if (slot < 0) return;
                boolean full = decision == AtlasRules.Grant.GIVE;
                player.getInventory().setItem(slot, atlasItem(atlas, full));
                if (full) record(player, atlas.world); else player.addScoreboardTag(localTag(atlas.world));
                player.sendMessage(Component.text(full ? "Du hast den Weltatlas für " + atlas.world + " erhalten. Rechtsklick wechselt den Zoom." : PREPARING, NamedTextColor.GOLD));
                if (full) player.sendMessage(Component.text(LEGEND, NamedTextColor.GRAY));
            }
            case RECORD -> record(player, atlas.world);
            case RECORD_LOCAL -> player.addScoreboardTag(localTag(atlas.world));
            case WAIT -> player.sendMessage(Component.text("Bitte mache einen Inventarplatz frei und verwende /atlas erneut.", NamedTextColor.YELLOW));
            case NONE -> { }
        }
    }

    private void scheduleStarter(Player player) {
        WorldAtlas atlas = atlas(player);
        if (atlas == null) return;
        ViewState state = state(player, atlas);
        int revision = ++state.grantRevision;
        // Multiverse-Inventories restores the destination inventory after the world-change event.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || atlas(player) != atlas || state(player, atlas) != state || state.grantRevision != revision) return;
            boolean ready = atlas.ready();
            applyGrant(player, atlas, AtlasRules.autoGrant(player.getGameMode() == GameMode.SURVIVAL,
                    ready ? recorded(player, atlas.world) : player.getScoreboardTags().contains(localTag(atlas.world)),
                    ready ? hasAtlas(player, atlas.world) : hasAnyMap(player), ready, player.getInventory().firstEmpty() >= 0));
        }, 40L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onGameModeChange(PlayerGameModeChangeEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onWorldLoad(WorldLoadEvent event) { attachMaps(); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { states.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) || !isAtlas(event.getItem())) return;
        Player player = event.getPlayer();
        WorldAtlas atlas = atlas(player);
        if (atlas == null || !isAtlas(event.getItem(), atlas.world)) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND && isAtlas(player.getInventory().getItemInMainHand(), atlas.world)) return;
        event.setCancelled(true);
        if (!atlas.ready()) { player.sendMessage(Component.text(PREPARING, NamedTextColor.YELLOW)); return; }
        ViewState state = state(player, atlas);
        int tick = Bukkit.getCurrentTick();
        if (tick - state.clickTick < 3) return;
        state.clickTick = tick;
        state.zoom = AtlasRules.nextZoom(atlas.layout, state.zoom);
        state.window = null;
        player.sendMap(atlas.map);
        player.sendMessage(Component.text("Atlas: " + 2 * atlas.layout.extent() / state.zoom + " Blöcke", NamedTextColor.GOLD));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("rangliste")) {
            if (args.length != 0) { sender.sendMessage(Component.text("/rangliste")); return true; }
            leaderboard.show(sender);
            return true;
        }
        if (command.getName().equals("arkatlas")) {
            if (!sender.hasPermission("arkatlas.admin")) { sender.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", NamedTextColor.RED)); return true; }
            if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) { sender.sendMessage(Component.text("/arkatlas reload")); return true; }
            String previous = getConfig().saveToString();
            try {
                reloadConfig();
                loadAtlases();
                attachMaps();
                Bukkit.getOnlinePlayers().forEach(this::scheduleStarter);
                sender.sendMessage(Component.text("Atlas neu geladen: " + atlases.size() + " Welten.", NamedTextColor.GREEN));
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
                // Later WorldLoad events must still use the last accepted MapView IDs.
                try { getConfig().loadFromString(previous); }
                catch (InvalidConfigurationException impossible) { throw new IllegalStateException("Cannot restore accepted atlas configuration", impossible); }
                sender.sendMessage(Component.text("Atlas konnte nicht geladen werden: " + e.getMessage() + ". Bisherige Atlanten bleiben aktiv.", NamedTextColor.RED));
            }
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("/atlas ist für Spieler.")); return true; }
        WorldAtlas atlas = atlas(player);
        if (atlas == null || player.getGameMode() != GameMode.SURVIVAL) {
            player.sendMessage(Component.text("Den Weltatlas erhältst du im Survival-Modus in einer Atlas-Welt.", NamedTextColor.YELLOW));
            return true;
        }
        if (hasAtlas(player, atlas.world)) applyGrant(player, atlas, AtlasRules.Grant.RECORD);
        else applyGrant(player, atlas, AtlasRules.autoGrant(true, false, !atlas.ready() && hasAnyMap(player), atlas.ready(), player.getInventory().firstEmpty() >= 0));
        return true;
    }

    private record Site(String name, double x, double z, MapCursor.Type icon) { }
    private final class WorldAtlas {
        final String world;
        final String mapPath;
        final AtlasRules.Layout layout;
        final BufferedImage image;
        final List<Site> sites;
        MapView map;
        AtlasRenderer renderer;
        WorldAtlas(String world, String mapPath, AtlasRules.Layout layout, BufferedImage image, List<Site> sites) {
            this.world = world; this.mapPath = mapPath; this.layout = layout; this.image = image; this.sites = sites;
        }
        boolean ready() { return image != null && map != null; }
        void detach() { if (map != null && renderer != null) map.removeRenderer(renderer); }
    }
    private static final class ViewState {
        int zoom = 1;
        int clickTick = -10;
        int grantRevision;
        AtlasRules.Window window;
    }

    private final class AtlasRenderer extends MapRenderer {
        private final WorldAtlas atlas;
        AtlasRenderer(WorldAtlas atlas) { super(true); this.atlas = atlas; }
        @Override public void render(MapView view, MapCanvas canvas, Player player) {
            if (atlas.image == null) return;
            ViewState state = state(player, atlas);
            Location location = player.getLocation();
            boolean inWorld = player.getWorld().getName().equals(atlas.world);
            AtlasRules.Window window = AtlasRules.window(atlas.layout, inWorld ? state.zoom : 1, location.getX(), location.getZ());
            if (!window.equals(state.window)) {
                BufferedImage output = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < 128; y++) {
                    int sy = AtlasRules.sourceIndex(atlas.layout, window, y, false, atlas.image.getWidth());
                    for (int x = 0; x < 128; x++) output.setRGB(x, y, atlas.image.getRGB(AtlasRules.sourceIndex(atlas.layout, window, x, true, atlas.image.getWidth()), sy));
                }
                canvas.drawImage(0, 0, output);
                state.window = window;
            }
            MapCursorCollection cursors = new MapCursorCollection();
            for (Site site : atlas.sites) if (window.contains(site.x(), site.z())) {
                cursors.addCursor(new MapCursor(AtlasRules.cursor(window, site.x(), true), AtlasRules.cursor(window, site.z(), false),
                        (byte) 0, site.icon(), true, Component.text(site.name())));
            }
            if (inWorld && window.contains(location.getX(), location.getZ())) cursors.addCursor(new MapCursor(
                    AtlasRules.cursor(window, location.getX(), true), AtlasRules.cursor(window, location.getZ(), false),
                    AtlasRules.direction(location.getYaw()), MapCursor.Type.PLAYER, true, Component.text(player.getName())));
            canvas.setCursors(cursors);
        }
    }
}
