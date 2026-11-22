package de.ark.encounterfixture;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable fixture only. Never load this plugin on an existing server. */
public final class EncounterFixture extends JavaPlugin implements Listener {
    private final Gson json = new Gson();
    private final Set<Chunk> tickets = new LinkedHashSet<>();
    private final Map<String, UUID> fixtures = new LinkedHashMap<>();
    private final Map<String, Object> adoption = new LinkedHashMap<>();
    private final List<Map<String, Object>> lazyChecks = new ArrayList<>();
    private JavaPlugin dinos;
    private World world;

    @Override public void onEnable() {
        dinos = (JavaPlugin) Bukkit.getPluginManager().getPlugin("ArkDinos");
        Bukkit.getPluginManager().registerEvents(this, this);
        world = new WorldCreator(NamespacedKey.minecraft("jurassic_archipelago")).generator(new ChunkGenerator() {
            @Override public void generateNoise(WorldInfo info, Random random, int x, int z, ChunkData data) {
                data.setRegion(0, data.getMinHeight(), 0, 16, 79, 16, Material.STONE);
                data.setRegion(0, 79, 0, 16, 80, 16, Material.GRASS_BLOCK);
            }
            @Override public boolean shouldGenerateNoise() { return false; }
            @Override public boolean shouldGenerateSurface() { return false; }
            @Override public boolean shouldGenerateCaves() { return false; }
            @Override public boolean shouldGenerateDecorations() { return false; }
            @Override public boolean shouldGenerateMobs() { return false; }
            @Override public boolean shouldGenerateStructures() { return false; }
        }).createWorld();
        world.setSpawnLocation(8192, 80, 3072);
        world.getWorldBorder().setSize(32768);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private Object call(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = dinos.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(dinos, arguments);
    }
    private NamespacedKey key(String name) { return new NamespacedKey("arkdinos", name); }
    private Map<String, Object> activation(Mob mob) {
        var values = new LinkedHashMap<String, Object>();
        values.put("server_tick", Bukkit.getCurrentTick());
        values.put("age_ticks", mob.getTicksLived());
        try {
            Object handle = mob.getClass().getMethod("getHandle").invoke(mob);
            for (String name : List.of("activatedTick", "defaultActivationState", "isTemporarilyActive"))
                values.put(name, handle.getClass().getField(name).get(handle));
        } catch (ReflectiveOperationException error) { values.put("unavailable", error.toString()); }
        return values;
    }
    private Player player(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) throw new IllegalArgumentException("Protocol player has not joined");
        return player;
    }
    @EventHandler(priority = EventPriority.MONITOR) public void adopted(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getUniqueId().equals(fixtures.get("adopted"))
                && entity.getPersistentDataContainer().has(key("owner"), PersistentDataType.STRING)) {
            adoption.put("uuid", entity.getUniqueId().toString());
            adoption.put("portal_cooldown", entity.getPortalCooldown());
            adoption.put("tags", List.copyOf(entity.getScoreboardTags()));
            adoption.put("tick", Bukkit.getCurrentTick());
        }
    }
    private void anchor(int x, int z) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            Chunk chunk = world.getChunkAt((x >> 4) + dx, (z >> 4) + dz);
            if (tickets.add(chunk)) chunk.addPluginChunkTicket(this);
        }
    }
    private Mob spawn(String label, int x, int z, String species) throws Exception {
        Mob mob = (Mob) call("spawnWild", new Class<?>[]{Location.class, String.class},
                new Location(world, x + .5, 80, z + .5), species);
        if (mob == null || !mob.isValid()) throw new IllegalStateException("Native fixture spawn failed");
        fixtures.put(label, mob.getUniqueId());
        mob.setAI(false);
        return mob;
    }
    @SuppressWarnings("unchecked") private Map<String, Object> status() throws Exception {
        Object encounter = field(dinos, "encounter");
        List<UUID> actors = (List<UUID>) field(encounter, "actors");
        var value = new LinkedHashMap<String, Object>();
        value.put("phase", field(encounter, "phase").toString());
        value.put("actor_ids", actors.stream().map(UUID::toString).toList());
        value.put("token", field(encounter, "token"));
        value.put("ticks", field(encounter, "ticks"));
        value.put("cooldown_ms", Math.max(0, (long) field(encounter, "readyAt") - System.currentTimeMillis()));
        value.put("fixtures", fixtures);
        value.put("scene_portal_originals", field(encounter, "portalCooldowns"));
        value.put("adoption_event", adoption);
        value.put("lazy_checks", lazyChecks);
        var entities = new ArrayList<Map<String, Object>>();
        for (Entity entity : world.getEntities()) if (entity instanceof Mob mob && entity.getScoreboardTags().contains("ark_dino")) {
            var row = new LinkedHashMap<String, Object>();
            row.put("uuid", entity.getUniqueId().toString());
            row.put("native_id", entity.getEntityId());
            row.put("species", entity.getPersistentDataContainer().get(key("species"), PersistentDataType.STRING));
            row.put("owner", entity.getPersistentDataContainer().get(key("owner"), PersistentDataType.STRING));
            row.put("persistent", entity.isPersistent());
            row.put("portal_cooldown", entity.getPortalCooldown());
            row.put("is_ticking", entity.isTicking());
            row.put("tags", entity.getScoreboardTags());
            row.put("x", entity.getX()); row.put("y", entity.getY()); row.put("z", entity.getZ());
            row.put("target", mob.getTarget() == null ? null : mob.getTarget().getUniqueId().toString());
            row.put("velocity", List.of(mob.getVelocity().getX(), mob.getVelocity().getY(), mob.getVelocity().getZ()));
            row.put("path", mob.getPathfinder().hasPath());
            row.put("aware", mob.isAware());
            row.put("ai", mob.hasAI());
            row.put("native_activation", activation(mob));
            row.put("grazing", entity.getPersistentDataContainer().getOrDefault(key("grazing"), PersistentDataType.BYTE, (byte) 0));
            entities.add(row);
        }
        value.put("entities", entities);
        var sceneChunks = new ArrayList<Map<String, Object>>();
        Chunk[] loadedChunks = world.getLoadedChunks();
        for (int i = 0; i < 9; i++) {
            int x = (i == 8 ? 8140 : 8210 + i % 4 * 24) >> 4;
            int z = (i == 8 ? 2960 : 3020 + i / 4 * 26) >> 4;
            String level = "UNLOADED";
            for (Chunk chunk : loadedChunks) if (chunk.getX() == x && chunk.getZ() == z) {
                level = chunk.getLoadLevel().name(); break;
            }
            sceneChunks.add(Map.of("x", x, "z", z, "loaded", world.isChunkLoaded(x, z), "load_level", level));
        }
        value.put("scene_chunks", sceneChunks);
        Entity probe = Bukkit.getEntity(fixtures.getOrDefault("lazy_probe", new UUID(0, 0)));
        if (probe != null) value.put("lazy_probe", Map.of("is_ticking", probe.isTicking(), "valid", probe.isValid(),
                "chunk_loaded", world.isChunkLoaded(probe.getLocation().getBlockX() >> 4, probe.getLocation().getBlockZ() >> 4)));
        value.put("view_distance", world.getViewDistance());
        value.put("simulation_distance", world.getSimulationDistance());
        var counts = new LinkedHashMap<String, Integer>();
        world.getPluginChunkTickets().forEach((plugin, chunks) -> counts.put(plugin.getName(), chunks.size()));
        value.put("plugin_tickets", counts);
        value.put("forced_chunks", world.getForceLoadedChunks().size());
        value.put("herd_trackers", ((Map<?, ?>) field(field(dinos, "herd"), "controlled")).size());
        value.put("players", world.getPlayers().size());
        value.put("spectators", world.getPlayers().stream().map(player -> Map.of(
                "x", player.getX(), "y", player.getY(), "z", player.getZ(), "mode", player.getGameMode().name())).toList());
        value.put("mspt", Bukkit.getAverageTickTime());
        value.put("tps", Bukkit.getTPS());
        value.put("loaded_chunks", world.getLoadedChunks().length);
        return value;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            switch (args[0]) {
                case "load" -> {
                    for (int i = 0; i < 8; i++) anchor(8210 + i % 4 * 24, 3020 + i / 4 * 26);
                    anchor(7900, 3030); anchor(8140, 2960); anchor(8192, 3072); anchor(8192, 3272); anchor(8180, 3150);
                    for (int i = 0; i < 4; i++) { anchor(8600 + i * 24, 3160); anchor(8180 + i * 18, 3160); }
                }
                case "corridor" -> {
                    for (int x = 7880; x <= 8312; x += 16) { anchor(x, 3000); anchor(x, 3032); anchor(x, 3064); anchor(x, 3096); }
                    // Native prey reached z3088; reserve the remaining flight travel south.
                    for (int x = 8192; x <= 8312; x += 16) anchor(x, 3128);
                    for (int x = 8120; x <= 8250; x += 16) for (int z = 2940; z <= 3020; z += 32) anchor(x, z);
                }
                case "join" -> {
                    Player player = player(args[1]); player.setGameMode(GameMode.SURVIVAL);
                    player.setInvulnerable(true);
                    player.teleport(world.getSpawnLocation().add(.5, 0, .5 + (args.length > 2 ? Integer.parseInt(args[2]) : 0)));
                }
                case "lazy" -> {
                    Player player = player(args[1]);
                    world.setViewDistance(20); world.setSimulationDistance(3);
                    player.setViewDistance(20); player.setSendViewDistance(20); player.setSimulationDistance(3);
                    player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(true);
                    player.teleport(world.getSpawnLocation().add(.5, 0, 160.5));
                }
                case "lazyprobe" -> {
                    if (!world.isChunkLoaded(8210 >> 4, 3020 >> 4)) throw new IllegalStateException("Probe chunk is not loaded");
                    Cow probe = world.spawn(new Location(world, 8218.5, 80, 3012.5), Cow.class);
                    probe.setAI(false); probe.setPersistent(false);
                    fixtures.put("lazy_probe", probe.getUniqueId());
                }
                case "lazycheck" -> {
                    lazyChecks.clear();
                    for (int i = 0; i < 9; i++) {
                        int x = i == 8 ? 8140 : 8210 + i % 4 * 24, z = i == 8 ? 2960 : 3020 + i / 4 * 26;
                        boolean safe = false;
                        int y = 0;
                        if (world.isChunkLoaded(x >> 4, z >> 4)) {
                            y = world.getHighestBlockYAt(x, z, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                            safe = (boolean) call("safeWildSpawn", new Class<?>[]{Location.class, String.class},
                                    new Location(world, x + .5, y, z + .5, -90, 0), i == 8 ? "trex" : "gallimimus");
                        }
                        lazyChecks.add(Map.of("x", x, "y", y, "z", z, "clear", safe));
                    }
                }
                case "budget" -> {
                    int global = Integer.parseInt(args[1]), nearby = Integer.parseInt(args[2]);
                    dinos.getConfig().set("wild-spawning.per-player", global);
                    dinos.getConfig().set("wild-spawning.world-limit", global);
                    dinos.getConfig().set("wild-spawning.nearby-limit", nearby);
                }
                case "population" -> {
                    for (int i = 0; i < 4; i++) spawn("budget_" + i, args[1].equals("far") ? 8600 + i * 24 : 8180 + i * 18, 3160, "triceratops");
                }
                case "clearbudget" -> {
                    for (var entry : fixtures.entrySet()) if (entry.getKey().startsWith("budget_")) {
                        Entity entity = Bukkit.getEntity(entry.getValue()); if (entity != null) entity.remove();
                    }
                    fixtures.keySet().removeIf(name -> name.startsWith("budget_"));
                }
                case "sentinels" -> {
                    Player player = player(args[1]);
                    Mob pet = spawn("pet", 8160, 3150, "gallimimus");
                    if (!(boolean) call("tame", new Class<?>[]{Mob.class, Player.class}, pet, player)) throw new IllegalStateException("Pet setup taming failed");
                    call("controlMode", new Class<?>[]{Mob.class, boolean.class}, pet, true);
                    Mob foreign = spawn("foreign", 8180, 3150, "triceratops");
                    // Existing named animal, no longer a disposable natural spawn.
                    foreign.removeScoreboardTag("ark_dino_natural");
                    foreign.addScoreboardTag("ark_jurassic_scene"); foreign.addScoreboardTag("foreign_scene_token");
                    foreign.customName(net.kyori.adventure.text.Component.text("Unrelated named animal"));
                    foreign.setPersistent(true);
                    call("restore", new Class<?>[]{Entity.class}, foreign);
                }
                case "prime" -> {
                    @SuppressWarnings("unchecked") List<UUID> actors = (List<UUID>) field(field(dinos, "encounter"), "actors");
                    Mob mob = (Mob) Bukkit.getEntity(actors.getFirst());
                    Player player = player(args[1]);
                    mob.getPersistentDataContainer().set(key("tame-actor"), PersistentDataType.STRING, player.getUniqueId().toString());
                    mob.getPersistentDataContainer().set(key("tame-feeds"), PersistentDataType.INTEGER, 5);
                    mob.getPersistentDataContainer().set(key("tame-next"), PersistentDataType.LONG, 0L);
                    mob.getPersistentDataContainer().set(key("tame-expiry"), PersistentDataType.LONG, System.currentTimeMillis() + 300000);
                    player.getInventory().setItemInMainHand(new ItemStack(Material.TORCHFLOWER_SEEDS));
                    player.teleport(mob.getLocation().add(0, 0, 3));
                    fixtures.put("adopted", mob.getUniqueId());
                }
                case "release" -> { tickets.forEach(chunk -> chunk.removePluginChunkTicket(this)); tickets.clear(); }
                case "status" -> { }
                default -> throw new IllegalArgumentException("Unknown fixture operation");
            }
            sender.sendMessage(json.toJson(status()));
        } catch (Exception error) { sender.sendMessage("FIXTURE_ERROR: " + error); error.printStackTrace(); }
        return true;
    }
    @Override public void onDisable() { tickets.forEach(chunk -> chunk.removePluginChunkTicket(this)); tickets.clear(); }
}
