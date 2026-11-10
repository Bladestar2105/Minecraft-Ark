package de.ark.dinos;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

/** Natural encounters in already loaded terrain. No home anchor, chunk tickets or world rewriting. */
final class DinoWildSpawns implements AutoCloseable {
    static final String NATURAL_TAG = "ark_dino_natural";
    private static final Set<Material> GROUND = Set.of(Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
            Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM, Material.MOSS_BLOCK, Material.SAND,
            Material.RED_SAND, Material.STONE, Material.GRAVEL, Material.SNOW_BLOCK);
    private final ArkDinos plugin;
    private final Map<UUID, Long> lastNearPlayer = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private final BukkitTask task;
    private final int perPlayer, worldLimit, nearbyLimit;

    DinoWildSpawns(ArkDinos plugin) {
        this.plugin = plugin;
        perPlayer = Math.clamp(plugin.getConfig().getInt("wild-spawning.per-player", 8), 0, 32);
        worldLimit = Math.clamp(plugin.getConfig().getInt("wild-spawning.world-limit", 64), 0, 256);
        nearbyLimit = Math.clamp(plugin.getConfig().getInt("wild-spawning.nearby-limit", 6), 0, 32);
        task = plugin.getConfig().getBoolean("wild-spawning.enabled", true)
                ? plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200, 200) : null;
    }

    private boolean wild(Mob mob) {
        return !plugin.owned(mob) && mob.isValid() && !mob.isDead()
                && mob.getScoreboardTags().contains("ark_dino") && plugin.species(mob) != null;
    }

    static void preserve(Mob mob) {
        mob.removeScoreboardTag(NATURAL_TAG);
        mob.setPersistent(true);
        mob.setRemoveWhenFarAway(false);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Set<UUID> seen = new HashSet<>();
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.prototype(world) || world.getEnvironment() != World.Environment.NORMAL) continue;
            var players = new ArrayList<>(world.getPlayers().stream().filter(plugin::survival).filter(p -> !p.isDead()).toList());
            java.util.Collections.shuffle(players);
            var population = new ArrayList<Mob>();
            for (var entity : world.getEntities()) {
                if (!(entity instanceof Mob mob) || !wild(mob)) continue;
                if (mob.getScoreboardTags().contains(NATURAL_TAG) && (mob.customName() != null || mob.isLeashed())) preserve(mob);
                if (mob.getScoreboardTags().contains(NATURAL_TAG)) {
                    seen.add(mob.getUniqueId());
                    boolean near = players.stream().anyMatch(p -> p.getLocation().distanceSquared(mob.getLocation()) < 128 * 128);
                    if (near) lastNearPlayer.put(mob.getUniqueId(), now);
                    long last = lastNearPlayer.computeIfAbsent(mob.getUniqueId(), ignored -> now);
                    if (!near && now - last > 60_000 && mob.getPassengers().isEmpty() && !mob.isLeashed()
                            && mob.customName() == null) { mob.remove(); continue; }
                }
                population.add(mob);
            }
            int limit = WildSpawnRules.limit(players.size(), perPlayer, worldLimit);
            int spawned = 0, attempts = 0;
            for (Player player : players) {
                if (population.size() >= limit || spawned >= 2 || attempts >= 16) break;
                for (int attempt = 0; attempt < 4 && attempts < 16; attempt++) {
                    attempts++;
                    Location point = candidate(player);
                    if (point == null) continue;
                    String species = WildSpawnRules.species(world.getBiome(point).getKey().toString(), ThreadLocalRandom.current().nextInt(100));
                    if (species == null) continue;
                    if ("mosasaurus".equals(species)) point.subtract(0, 5, 0);
                    final Location at = point;
                    int nearby = (int) population.stream().filter(mob -> mob.getLocation().distanceSquared(at) < 96 * 96).count();
                    if (!WildSpawnRules.permits(population.size(), limit, nearby, nearbyLimit)
                            || players.stream().anyMatch(p -> p.getLocation().distanceSquared(at) < 32 * 32)
                            || !protectionAllows(point) || !space(point, species)) continue;
                    Mob mob = plugin.spawnWild(point, species);
                    if (mob != null && mob.isValid()) {
                        population.add(mob); seen.add(mob.getUniqueId());
                        lastNearPlayer.put(mob.getUniqueId(), now); spawned++;
                    }
                    break;
                }
            }
        }
        lastNearPlayer.keySet().removeIf(id -> !seen.contains(id));
    }

    private Location candidate(Player player) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double distance = ThreadLocalRandom.current().nextDouble(32, 81);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        World world = player.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Location point = new Location(world, x + .5, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + .5);
        return world.getWorldBorder().isInside(point) ? point : null;
    }

    private boolean space(Location point, String species) {
        World world = point.getWorld();
        boolean aquatic = "mosasaurus".equals(species);
        if (!aquatic && !GROUND.contains(world.getBlockAt(point.getBlockX(), point.getBlockY() - 1, point.getBlockZ()).getType())) return false;
        var size = DinoSizes.centeredClearanceBounds("ark_" + species);
        // Reserve the measured animation envelope for any initial yaw and later turns.
        double radius = Math.hypot(size.width(), size.length()) / 2;
        double yaw = Math.toRadians(point.getYaw());
        double halfX = aquatic ? (Math.abs(Math.cos(yaw)) * size.width() + Math.abs(Math.sin(yaw)) * size.length()) / 2 : radius;
        double halfZ = aquatic ? (Math.abs(Math.sin(yaw)) * size.width() + Math.abs(Math.cos(yaw)) * size.length()) / 2 : radius;
        double bottom = aquatic ? DinoSizes.clearanceMinY("ark_mosasaurus") : .05;
        BoundingBox box = new BoundingBox(point.getX() - halfX, point.getY() + bottom, point.getZ() - halfZ,
                point.getX() + halfX, point.getY() + (aquatic ? bottom : .2) + size.height(), point.getZ() + halfZ);
        if (!world.getWorldBorder().isInside(new Location(world, box.getMinX(), point.getY(), box.getMinZ()))
                || !world.getWorldBorder().isInside(new Location(world, box.getMaxX(), point.getY(), box.getMaxZ()))) return false;
        if (aquatic) {
            for (int x = (int) Math.floor(box.getMinX()) >> 4; x <= (int) Math.floor(box.getMaxX()) >> 4; x++)
                for (int z = (int) Math.floor(box.getMinZ()) >> 4; z <= (int) Math.floor(box.getMaxZ()) >> 4; z++)
                    if (!world.isChunkLoaded(x, z)) return false;
            return DinoAquatic.spawnSpace(point)
                    && world.getNearbyEntities(box).stream().noneMatch(entity -> entity instanceof org.bukkit.entity.LivingEntity);
        }
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
            for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
                if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
                    if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
                    var block = world.getBlockAt(x, y, z);
                    if (block.isEmpty()) continue;
                    if (block.isLiquid() || block.getCollisionShape().overlaps(box.clone().shift(-x, -y, -z))) return false;
                }
            }
        }
        return world.getNearbyEntities(box).stream().noneMatch(entity -> entity instanceof org.bukkit.entity.LivingEntity);
    }

    private boolean protectionAllows(Location point) {
        var manager = plugin.getServer().getPluginManager();
        var guard = manager.getPlugin("WorldGuard");
        if (guard != null && !guard.isEnabled()) {
            warn("WorldGuard", new IllegalStateException("Installed region plugin is disabled")); return false;
        }
        if (guard != null && guard.isEnabled()) {
            try {
                var loader = guard.getClass().getClassLoader();
                var adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
                Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, point);
                var location = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
                var wg = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
                Object platform = wg.getMethod("getPlatform").invoke(wg.getMethod("getInstance").invoke(null));
                Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                Object query = container.getClass().getMethod("createQuery").invoke(container);
                Object regions = query.getClass().getMethod("getApplicableRegions", location).invoke(query, adapted);
                for (Object region : (Iterable<?>) regions) {
                    if (!"__global__".equals(region.getClass().getMethod("getId").invoke(region))) return false;
                }
                var flagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
                Object flags = Array.newInstance(flagClass, 1);
                Array.set(flags, 0, Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader).getField("MOB_SPAWNING").get(null));
                var actor = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
                if (!(boolean) query.getClass().getMethod("testState", location, actor, flags.getClass()).invoke(query, adapted, null, flags)) return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn("WorldGuard", error); return false;
            }
        }
        var grief = manager.getPlugin("GriefPrevention");
        if (grief != null && !grief.isEnabled()) {
            warn("GriefPrevention", new IllegalStateException("Installed claim plugin is disabled")); return false;
        }
        if (grief != null && grief.isEnabled()) {
            try {
                Object store = grief.getClass().getField("dataStore").get(grief);
                var claim = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, grief.getClass().getClassLoader());
                if (store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claim).invoke(store, point, true, null) != null) return false;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                warn("GriefPrevention", error); return false;
            }
        }
        return true;
    }

    private void warn(String name, Throwable error) {
        if (warned.add(name)) plugin.getLogger().warning("Natural dinosaur spawning paused where " + name + " cannot be checked: " + error);
    }

    @Override public void close() {
        if (task != null) task.cancel();
        for (World world : plugin.getServer().getWorlds()) for (var entity : world.getEntities()) {
            if (entity instanceof Mob mob && wild(mob) && mob.getScoreboardTags().contains(NATURAL_TAG)) {
                if (mob.customName() != null || mob.isLeashed()) preserve(mob);
                else mob.remove();
            }
        }
        lastNearPlayer.clear();
    }
}
