package de.ark.atlas;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
    private static final String WORLD = "ark";
    private static final String LOCAL_STARTER = "ark_local_map";
    private static final String PREPARING = "Der Weltatlas wird vorbereitet. Versuche /karte später erneut.";
    private static final String LEGEND = "Legende: Ziel = Spawn, Haus = Dorf, Rot = Dino, Blau = Händler, Rotes Kreuz = Abenteuer, Pfeil = du.";
    private final Map<UUID, ViewState> states = new HashMap<>();
    private NamespacedKey atlasKey;
    private NamespacedKey starterKey;
    private Terrain terrain;
    private MapView map;
    private AtlasRenderer renderer;

    @Override public void onEnable() {
        atlasKey = new NamespacedKey(this, "ark_atlas");
        starterKey = new NamespacedKey(this, "ark_atlas_starter");
        saveDefaultConfig();
        if (!new File(getDataFolder(), "sites.yml").exists()) saveResource("sites.yml", false);
        Bukkit.getPluginManager().registerEvents(this, this);
        try { loadTerrain(); }
        catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
            getLogger().warning("Atlas wird vorbereitet: " + e.getMessage());
        }
        attachMap();
        Bukkit.getOnlinePlayers().forEach(this::scheduleStarter);
    }

    @Override public void onDisable() {
        if (map != null && renderer != null) map.removeRenderer(renderer);
        states.clear();
    }

    private void loadTerrain() throws IOException, InvalidConfigurationException {
        File file = new File(getDataFolder(), "terrain.png");
        BufferedImage image;
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("terrain.png fehlt");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("terrain.png ist kein gültiges PNG");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                int size = reader.getWidth(0);
                if (!reader.getFormatName().equalsIgnoreCase("png") || size < 128 || size > 2048 || reader.getHeight(0) != size) {
                    throw new IOException("terrain.png muss quadratisch und 128–2048 Pixel groß sein");
                }
                image = reader.read(0);
            } finally { reader.dispose(); }
        }
        YamlConfiguration config = new YamlConfiguration();
        config.load(new File(getDataFolder(), "sites.yml"));
        if (!WORLD.equals(config.getString("world")) || config.getInt("extent") != AtlasRules.EXTENT) {
            throw new IOException("sites.yml benötigt world: ark und extent: 10240");
        }
        List<Site> sites = new ArrayList<>();
        if (!config.isList("sites") || config.getList("sites").size() != config.getMapList("sites").size()) {
            throw new IOException("sites.yml benötigt eine Liste von Orten unter sites");
        }
        for (Map<?, ?> entry : config.getMapList("sites")) {
            Object name = entry.get("name"), type = entry.get("type"), x = entry.get("x"), z = entry.get("z");
            if (!(name instanceof String label) || label.isBlank() || label.length() > 80 ||
                    !(type instanceof String kind) || !(x instanceof Number nx) || !(z instanceof Number nz) ||
                    !Double.isFinite(nx.doubleValue()) || !Double.isFinite(nz.doubleValue()) ||
                    Math.abs(nx.doubleValue()) > AtlasRules.EXTENT || Math.abs(nz.doubleValue()) > AtlasRules.EXTENT) {
                throw new IOException("Ungültiger Atlas-Ort: " + entry);
            }
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
        if (sites.size() > 256) throw new IOException("Höchstens 256 Atlas-Orte erlaubt");
        terrain = new Terrain(image, List.copyOf(sites));
        states.values().forEach(state -> state.window = null);
    }

    private void attachMap() {
        World world = Bukkit.getWorld(WORLD);
        if (world == null) {
            getLogger().warning("Welt ark fehlt; nach dem Laden /arkatlas reload verwenden.");
            return;
        }
        int id = getConfig().getInt("map-id", -1);
        map = id >= 0 ? Bukkit.getMap(id) : null;
        if (map == null && terrain != null) {
            map = Bukkit.createMap(world);
            getConfig().set("map-id", map.getId());
            saveConfig();
        }
        if (map == null) return;
        for (MapRenderer old : List.copyOf(map.getRenderers())) map.removeRenderer(old);
        map.setWorld(world);
        map.setCenterX(0);
        map.setCenterZ(0);
        map.setScale(MapView.Scale.FARTHEST);
        map.setTrackingPosition(false);
        map.setUnlimitedTracking(false);
        map.setLocked(true);
        renderer = new AtlasRenderer();
        map.addRenderer(renderer);
    }

    private boolean isAtlas(ItemStack item) {
        return item != null && item.getType() == Material.FILLED_MAP &&
                item.getItemMeta().getPersistentDataContainer().has(atlasKey, PersistentDataType.BYTE);
    }

    private boolean hasAtlas(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (isAtlas(item)) return true;
        return false;
    }

    private boolean hasAnyMap(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && (item.getType() == Material.MAP || item.getType() == Material.FILLED_MAP)) return true;
        }
        return false;
    }

    private ItemStack localItem() {
        ItemStack item = new ItemStack(Material.MAP);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("ARK-Umgebungskarte", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Benutzen zeichnet deine Umgebung.", NamedTextColor.GRAY),
                Component.text("Der Weltatlas folgt nach vollständiger Vorbereitung.", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private boolean isArk(Player player) { return player.getWorld().getName().equals(WORLD); }
    private boolean ready() { return terrain != null && map != null; }
    private boolean recorded(Player player) { return player.getPersistentDataContainer().has(starterKey, PersistentDataType.BYTE); }
    private void record(Player player) { player.getPersistentDataContainer().set(starterKey, PersistentDataType.BYTE, (byte) 1); }

    private ItemStack atlasItem() {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(map);
        meta.displayName(Component.text("ARK-Weltatlas", NamedTextColor.GOLD));
        meta.lore(List.of(Component.text("Rechtsklick: Welt → Region → Umgebung", NamedTextColor.GRAY),
                Component.text("20.480 / 5.120 / 2.560 Blöcke · Norden oben", NamedTextColor.GRAY),
                Component.text(LEGEND, NamedTextColor.GRAY),
                Component.text("Bedrock zeigt keine Ortsnamen an Symbolen.", NamedTextColor.GRAY),
                Component.text("Ersatz in Survival: /karte", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(atlasKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean give(Player player, boolean fullAtlas) {
        int slot = player.getInventory().firstEmpty();
        if (slot < 0) return false;
        player.getInventory().setItem(slot, fullAtlas ? atlasItem() : localItem());
        if (!fullAtlas) {
            player.addScoreboardTag(LOCAL_STARTER);
            player.sendMessage(Component.text("Du hast eine ARK-Umgebungskarte erhalten. Benutzen zeichnet deine Umgebung.", NamedTextColor.GOLD));
            player.sendMessage(Component.text(PREPARING, NamedTextColor.YELLOW));
            return true;
        }
        record(player);
        player.sendMessage(Component.text("Du hast den ARK-Weltatlas erhalten. Rechtsklick wechselt den Zoom.", NamedTextColor.GOLD));
        player.sendMessage(Component.text(LEGEND, NamedTextColor.GRAY));
        player.sendMessage(Component.text("Bedrock zeigt keine Ortsnamen an Symbolen. Nutze die Legende.", NamedTextColor.GRAY));
        return true;
    }

    private void scheduleStarter(Player player) {
        ViewState state = states.computeIfAbsent(player.getUniqueId(), key -> new ViewState());
        int revision = ++state.grantRevision;
        // Multiverse-Inventories restores the destination inventory after the world-change event.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || states.get(player.getUniqueId()) != state || state.grantRevision != revision) return;
            boolean atlasReady = ready();
            boolean eligible = AtlasRules.canRequest(isArk(player), player.getGameMode() == GameMode.SURVIVAL);
            if (eligible) {
                ItemStack[] contents = player.getInventory().getContents();
                for (int slot = 0; slot < contents.length; slot++) if (isAtlas(contents[slot])) {
                    var meta = contents[slot].getItemMeta();
                    List<Component> previous = meta.lore();
                    List<Component> updated = new ArrayList<>(previous == null ? List.of() : previous);
                    boolean replaced = false;
                    for (int line = 0; line < updated.size(); line++) {
                        if (updated.get(line) instanceof TextComponent text && text.content().startsWith("Legende:")) {
                            updated.set(line, Component.text(LEGEND, NamedTextColor.GRAY));
                            replaced = true;
                        }
                    }
                    if (!replaced) updated.add(Component.text(LEGEND, NamedTextColor.GRAY));
                    if (!updated.equals(previous)) {
                        meta.lore(updated);
                        contents[slot].setItemMeta(meta);
                        player.getInventory().setItem(slot, contents[slot]);
                    }
                }
            }
            applyGrant(player, AtlasRules.autoGrant(eligible,
                    atlasReady ? recorded(player) : player.getScoreboardTags().contains(LOCAL_STARTER),
                    atlasReady ? hasAtlas(player) : hasAnyMap(player), atlasReady, player.getInventory().firstEmpty() >= 0));
        }, 40L);
    }

    private void applyGrant(Player player, AtlasRules.Grant decision) {
        switch (decision) {
            case GIVE -> give(player, true);
            case GIVE_LOCAL -> give(player, false);
            case RECORD -> {
                record(player);
                player.sendMessage(Component.text("Du hast bereits einen ARK-Weltatlas im Inventar.", NamedTextColor.YELLOW));
            }
            case RECORD_LOCAL -> {
                player.addScoreboardTag(LOCAL_STARTER);
                player.sendMessage(Component.text("Umgebungskarte vorhanden; der Weltatlas wird vorbereitet.", NamedTextColor.YELLOW));
            }
            case WAIT -> player.sendMessage(Component.text("Bitte mache einen Inventarplatz frei und verwende /karte erneut.", NamedTextColor.YELLOW));
            case NONE -> { }
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onGameModeChange(PlayerGameModeChangeEvent event) { scheduleStarter(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { states.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) || !isAtlas(event.getItem())) return;
        Player player = event.getPlayer();
        if (!isArk(player)) return;
        if (event.getHand() == EquipmentSlot.OFF_HAND && isAtlas(player.getInventory().getItemInMainHand())) return;
        event.setCancelled(true);
        if (!ready()) { player.sendMessage(Component.text(PREPARING, NamedTextColor.YELLOW)); return; }
        ViewState state = states.computeIfAbsent(player.getUniqueId(), key -> new ViewState());
        int tick = Bukkit.getCurrentTick();
        if (tick - state.clickTick < 3) return;
        state.clickTick = tick;
        state.zoom = AtlasRules.nextZoom(state.zoom);
        state.window = null;
        player.sendMap(map);
        player.sendMessage(Component.text("Atlas: " + (state.zoom == 1 ? "Welt (20.480)" : state.zoom == 4 ? "Region (5.120)" : "Umgebung (2.560)") + " Blöcke", NamedTextColor.GOLD));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("arkatlas")) {
            if (!sender.hasPermission("arkatlas.admin")) { sender.sendMessage(Component.text("Dafür fehlt dir die Berechtigung.", NamedTextColor.RED)); return true; }
            if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) { sender.sendMessage(Component.text("/arkatlas reload")); return true; }
            try {
                loadTerrain();
                attachMap();
                Bukkit.getOnlinePlayers().forEach(this::scheduleStarter);
                sender.sendMessage(Component.text(ready() ? "Atlas neu geladen: " + terrain.sites().size() + " Orte." : PREPARING, NamedTextColor.GREEN));
            } catch (IOException | InvalidConfigurationException | IllegalArgumentException e) {
                sender.sendMessage(Component.text("Atlas konnte nicht geladen werden: " + e.getMessage() + (ready() ? " Bisheriger Atlas bleibt aktiv." : ""), NamedTextColor.RED));
            }
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("/karte ist für Spieler.")); return true; }
        if (!AtlasRules.canRequest(isArk(player), player.getGameMode() == GameMode.SURVIVAL)) { player.sendMessage(Component.text("Den Weltatlas erhältst du im Survival-Modus in ark.", NamedTextColor.YELLOW)); return true; }
        if (hasAtlas(player)) applyGrant(player, AtlasRules.Grant.RECORD);
        else applyGrant(player, AtlasRules.autoGrant(true, false, !ready() && hasAnyMap(player), ready(), player.getInventory().firstEmpty() >= 0));
        return true;
    }

    private record Site(String name, double x, double z, MapCursor.Type icon) { }
    private record Terrain(BufferedImage image, List<Site> sites) { }
    private static final class ViewState {
        int zoom = 1;
        int clickTick = -10;
        int grantRevision;
        AtlasRules.Window window;
    }

    private final class AtlasRenderer extends MapRenderer {
        AtlasRenderer() { super(true); }
        @Override public void render(MapView view, MapCanvas canvas, Player player) {
            if (terrain == null) return;
            ViewState state = states.computeIfAbsent(player.getUniqueId(), key -> new ViewState());
            Location location = player.getLocation();
            boolean inArk = isArk(player);
            AtlasRules.Window window = AtlasRules.window(inArk ? state.zoom : 1, location.getX(), location.getZ());
            if (!window.equals(state.window)) {
                BufferedImage output = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                BufferedImage source = terrain.image();
                for (int y = 0; y < 128; y++) {
                    int sy = AtlasRules.sourceIndex(window, y, false, source.getWidth());
                    for (int x = 0; x < 128; x++) output.setRGB(x, y, source.getRGB(AtlasRules.sourceIndex(window, x, true, source.getWidth()), sy));
                }
                canvas.drawImage(0, 0, output);
                state.window = window;
            }
            MapCursorCollection cursors = new MapCursorCollection();
            for (Site site : terrain.sites()) if (window.contains(site.x(), site.z())) {
                cursors.addCursor(new MapCursor(AtlasRules.cursor(window, site.x(), true), AtlasRules.cursor(window, site.z(), false),
                        (byte) 0, site.icon(), true, Component.text(site.name())));
            }
            if (inArk && window.contains(location.getX(), location.getZ())) cursors.addCursor(new MapCursor(
                    AtlasRules.cursor(window, location.getX(), true), AtlasRules.cursor(window, location.getZ(), false),
                    AtlasRules.direction(location.getYaw()), MapCursor.Type.PLAYER, true, Component.text(player.getName())));
            canvas.setCursors(cursors);
        }
    }
}
