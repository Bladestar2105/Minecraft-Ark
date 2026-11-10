package de.ark.dinos;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Sniffer;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Surface buoyancy for land carriers, applied after manual control and independent of their AI mode. */
final class DinoSwimming implements AutoCloseable {
    private final ArkDinos plugin;
    private final Map<UUID, Mob> swimmers = new HashMap<>();

    DinoSwimming(ArkDinos plugin) { this.plugin = plugin; }

    private boolean eligible(Entity entity) {
        return (entity instanceof Sniffer || entity instanceof Ravager) && plugin.prototype(entity)
                && SwimRules.supports(plugin.species(entity))
                && (plugin.owned(entity) || entity.getScoreboardTags().contains("ark_dino"));
    }

    void restore(Entity entity) {
        if (eligible(entity)) swimmers.put(entity.getUniqueId(), (Mob) entity);
        else unload(entity);
    }

    void unload(Entity entity) { swimmers.remove(entity.getUniqueId()); }

    void tick() {
        var iterator = swimmers.values().iterator();
        while (iterator.hasNext()) {
            Mob mob = iterator.next();
            if (!mob.isValid() || mob.isDead() || !eligible(mob)) { iterator.remove(); continue; }
            if (!mob.isInWater() || mob.isInLava()) continue; // Paper includes bubble columns in water.
            World world = mob.getWorld();
            BoundingBox box = mob.getBoundingBox();
            if (!loaded(world, box)) continue;
            OptionalInt surface = surface(world, box);
            if (surface.isEmpty()) continue;
            Vector velocity = mob.getVelocity();
            double upward = SwimRules.verticalVelocity(mob.getEyeLocation().getY(), surface.getAsInt(), velocity.getY());
            if (upward > 0 && !ridersClear(mob, upward)) upward = Math.min(0, velocity.getY());
            velocity.setY(upward);
            if (!loaded(world, box.clone().expandDirectional(velocity))) velocity.setX(0).setZ(0);
            mob.setVelocity(velocity);
        }
    }

    private OptionalInt surface(World world, BoundingBox box) {
        double centerX = (box.getMinX() + box.getMaxX()) / 2, centerZ = (box.getMinZ() + box.getMaxZ()) / 2;
        int bottom = Math.max(world.getMinHeight(), (int) Math.floor(box.getMinY() + .001));
        int top = Math.min(world.getMaxHeight() - 1, (int) Math.floor(Math.nextDown(box.getMaxY())));
        OptionalInt center = column(world, (int) Math.floor(centerX), (int) Math.floor(centerZ), bottom, top);
        if (center.isPresent()) return center;
        // At banks the native body can touch water while its centre is already on dry ground.
        OptionalInt highest = OptionalInt.empty();
        for (double x : new double[]{box.getMinX() + .01, box.getMaxX() - .01})
            for (double z : new double[]{box.getMinZ() + .01, box.getMaxZ() - .01}) {
                OptionalInt candidate = column(world, (int) Math.floor(x), (int) Math.floor(z), bottom, top);
                if (candidate.isPresent() && (highest.isEmpty() || candidate.getAsInt() > highest.getAsInt())) highest = candidate;
            }
        return highest;
    }

    private OptionalInt column(World world, int x, int z, int bottom, int top) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return OptionalInt.empty();
        return SwimRules.surface(bottom, top, world.getMaxHeight(), y -> water(world.getBlockAt(x, y, z)));
    }

    private static boolean water(Block block) {
        Material type = block.getType();
        return type == Material.WATER || type == Material.BUBBLE_COLUMN || type == Material.KELP || type == Material.KELP_PLANT
                || type == Material.SEAGRASS || type == Material.TALL_SEAGRASS
                || block.getBlockData() instanceof Waterlogged data && data.isWaterlogged();
    }

    /** Uses the current eye position; Entity.isUnderWater() can lag the first native dismount tick. */
    static boolean eyeInWater(LivingEntity entity) {
        var eye = entity.getEyeLocation(); World world = eye.getWorld();
        int x = eye.getBlockX(), y = eye.getBlockY(), z = eye.getBlockZ();
        if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return false;
        Block block = world.getBlockAt(x, y, z);
        if (!water(block)) return false;
        // A water cell below more water is full; exposed source/falling water is eight ninths high.
        double height = 8.0 / 9.0;
        if (y + 1 < world.getMaxHeight() && water(world.getBlockAt(x, y + 1, z))) height = 1;
        else if (block.getBlockData() instanceof Levelled level && level.getLevel() < 8) height = (8 - level.getLevel()) / 9.0;
        return eye.getY() < y + height;
    }

    private static boolean loaded(World world, BoundingBox box) {
        for (int x = (int) Math.floor(box.getMinX()) >> 4; x <= (int) Math.floor(Math.nextDown(box.getMaxX())) >> 4; x++)
            for (int z = (int) Math.floor(box.getMinZ()) >> 4; z <= (int) Math.floor(Math.nextDown(box.getMaxZ())) >> 4; z++)
                if (!world.isChunkLoaded(x, z)) return false;
        return true;
    }

    private boolean ridersClear(Mob mob, double upward) {
        World world = mob.getWorld();
        for (Entity rider : plugin.passengers(mob)) {
            BoundingBox sweep = rider.getBoundingBox().expandDirectional(0, upward, 0);
            if (sweep.getMinY() < world.getMinHeight() || sweep.getMaxY() >= world.getMaxHeight()) return false;
            for (int x = (int) Math.floor(sweep.getMinX()) - 1; x <= (int) Math.floor(sweep.getMaxX()) + 1; x++)
                for (int z = (int) Math.floor(sweep.getMinZ()) - 1; z <= (int) Math.floor(sweep.getMaxZ()) + 1; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
                    for (int y = Math.max(world.getMinHeight(), (int) Math.floor(sweep.getMinY()) - 1);
                         y <= Math.min(world.getMaxHeight() - 1, (int) Math.floor(sweep.getMaxY()) + 1); y++)
                        if (world.getBlockAt(x, y, z).getCollisionShape().overlaps(sweep.clone().shift(-x, -y, -z))) return false;
                }
        }
        return true;
    }

    @Override public void close() { swimmers.clear(); }
}
