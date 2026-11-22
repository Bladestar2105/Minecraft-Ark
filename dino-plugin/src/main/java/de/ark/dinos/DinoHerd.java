package de.ark.dinos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Loaded, player-near ecology; scene actors use the same controller and budget. */
final class DinoHerd implements AutoCloseable {
    private final ArkDinos plugin;
    private final NamespacedKey grazing;
    private final BukkitTask task;
    private final Map<UUID, Mob> controlled = new HashMap<>();
    private long ticks;

    DinoHerd(ArkDinos plugin) {
        this.plugin = plugin;
        grazing = new NamespacedKey(plugin, "grazing");
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    private boolean eligible(Mob mob) {
        return "gallimimus".equals(plugin.species(mob)) && mob.getScoreboardTags().contains("ark_dino")
                && HerdRules.eligible(plugin.owned(mob), !plugin.passengers(mob).isEmpty() || mob.isInsideVehicle(),
                        plugin.staying(mob), mob.isLeashed(), mob.isValid() && !mob.isDead())
                && !plugin.combatActive(mob);
    }

    private static HerdRules.Point point(Mob mob) { return new HerdRules.Point(mob.getX(), mob.getZ()); }

    private void tick() {
        ticks += 10;
        Map<UUID, Mob> next = new HashMap<>();
        int limit = Math.clamp(plugin.getConfig().getInt("wild-spawning.world-limit", 64), 0, 64);
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.prototype(world)) continue;
            Map<UUID, Mob> candidates = new LinkedHashMap<>();
            Map<UUID, Mob> predators = new LinkedHashMap<>();
            // A scene admits viewers up to 320 blocks away. Its nine exact loaded
            // actors keep that relevance range without expanding free ecology.
            for(Mob mob:plugin.sceneActors(world)) {
                if(eligible(mob) && candidates.size()<limit) candidates.put(mob.getUniqueId(),mob);
                if("trex".equals(plugin.species(mob))) predators.put(mob.getUniqueId(),mob);
            }
            for (var player : world.getPlayers()) {
                if (!plugin.survival(player) || player.isDead()) continue;
                for (var entity : player.getNearbyEntities(96, 48, 96)) {
                    if (!(entity instanceof Mob mob) || !mob.isValid() || mob.isDead()) continue;
                    boolean herdAnimal = eligible(mob);
                    if (herdAnimal && candidates.size() < limit) candidates.put(mob.getUniqueId(), mob);
                    else if ("gallimimus".equals(plugin.species(mob))) mob.getPersistentDataContainer().remove(grazing);
                    if ("trex".equals(plugin.species(mob)) && predators.size() < 64)
                        predators.put(mob.getUniqueId(), mob);
                }
            }
            var population = new ArrayList<>(candidates.values());
            var threats = predators.values().stream().map(DinoHerd::point).toList();
            for (Mob mob : population) {
                var neighbors = population.stream().filter(other -> other != mob)
                        .filter(other -> other.getLocation().distanceSquared(mob.getLocation()) < 32 * 32)
                        .sorted(Comparator.comparingDouble(other -> other.getLocation().distanceSquared(mob.getLocation())))
                        .limit(7).map(DinoHerd::point).toList();
                var herd = new ArrayList<>(neighbors);
                herd.add(point(mob));
                var threat = HerdRules.threat(herd, threats);
                next.put(mob.getUniqueId(), mob);
                boolean grass = world.getBlockAt(mob.getLocation().subtract(0, .1, 0)).getType() == Material.GRASS_BLOCK;
                boolean grazingNow = HerdRules.graze(ticks, mob.getUniqueId().hashCode(), grass, threat != null);
                mob.setAware(true);
                if (grazingNow) {
                    mob.getPathfinder().stopPathfinding();
                    mob.getPersistentDataContainer().set(grazing, PersistentDataType.BYTE, (byte) 1);
                    continue;
                }
                mob.getPersistentDataContainer().remove(grazing);
                double heading = Math.floorMod(mob.getUniqueId().hashCode() + ticks / 160, 6283) / 1000.0;
                var direction = HerdRules.steer(point(mob), neighbors, threat,
                        new HerdRules.Point(Math.cos(heading), Math.sin(heading)));
                move(mob, direction, threat != null);
            }
        }
        for (var entry : controlled.entrySet()) if (!next.containsKey(entry.getKey())) clear(entry.getValue());
        controlled.clear();
        controlled.putAll(next);
    }

    private void move(Mob mob, HerdRules.Point direction, boolean running) {
        if (Math.hypot(direction.x(), direction.z()) < .05) {
            mob.getPathfinder().stopPathfinding();
            return;
        }
        Location from = mob.getLocation();
        for (double turn : new double[]{0, Math.PI / 3, -Math.PI / 3}) {
            double dx = (direction.x() * Math.cos(turn) - direction.z() * Math.sin(turn)) * 6;
            double dz = (direction.x() * Math.sin(turn) + direction.z() * Math.cos(turn)) * 6;
            Location target = from.clone().add(dx, 0, dz);
            if (!loadedCorridor(from, target) || !mob.getWorld().getWorldBorder().isInside(target)) continue;
            for (int rise : new int[]{0, 1, -1, 2, -2}) {
                int y = from.getBlockY() + rise;
                if (y <= mob.getWorld().getMinHeight() || y + 3 >= mob.getWorld().getMaxHeight()) continue;
                int x = target.getBlockX(), z = target.getBlockZ();
                if (!mob.getWorld().getBlockAt(x, y - 1, z).getType().isSolid()) continue;
                boolean clear = true;
                for (int up = 0; up < 3; up++) {
                    var block = mob.getWorld().getBlockAt(x, y + up, z);
                    if (!block.isPassable() || block.isLiquid()) { clear = false; break; }
                }
                if (!clear) continue;
                target.setY(y);
                if (mob.getPathfinder().moveTo(target, plugin.groundSpeed(mob, running))) return;
            }
        }
        mob.getPathfinder().stopPathfinding();
    }

    private boolean loadedCorridor(Location from, Location to) {
        for (int x = ((int) Math.floor(Math.min(from.getX(), to.getX())) - 16) >> 4;
             x <= ((int) Math.floor(Math.max(from.getX(), to.getX())) + 16) >> 4; x++) {
            for (int z = ((int) Math.floor(Math.min(from.getZ(), to.getZ())) - 16) >> 4;
                 z <= ((int) Math.floor(Math.max(from.getZ(), to.getZ())) + 16) >> 4; z++) {
                if (!from.getWorld().isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private void clear(Mob mob) {
        if (!mob.isValid()) return;
        mob.getPersistentDataContainer().remove(grazing);
        if (eligible(mob)) mob.getPathfinder().stopPathfinding();
    }

    @Override public void close() {
        task.cancel();
        controlled.values().forEach(this::clear);
        controlled.clear();
        for (World world : plugin.getServer().getWorlds()) for (var entity : world.getEntities())
            entity.getPersistentDataContainer().remove(grazing);
    }
}
