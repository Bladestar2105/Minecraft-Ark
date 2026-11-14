package de.ark.dinos;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional presentation only; the native mob remains the mount and interaction target. */
final class DinoModels implements AutoCloseable {
    private final JavaPlugin plugin;
    private BetterModelAdapter adapter;
    private BetterModelCleanup cleanup;

    DinoModels(JavaPlugin plugin) {
        this.plugin = plugin;
        if (plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")
                && plugin.getServer().getPluginManager().isPluginEnabled("packetevents")
                && plugin.getServer().getPluginManager().isPluginEnabled("GeyserModelEngine")) {
            try {
                adapter = new BetterModelAdapter(plugin);
            } catch (RuntimeException | LinkageError error) {
                plugin.getLogger().warning("Dinosaur models unavailable: " + error.getMessage());
            }
        }
    }

    Entity resolveTarget(Entity entity) {
        if (adapter == null) return entity;
        try {
            return adapter.resolveTarget(entity);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Dinosaur target unavailable: " + error.getMessage());
            return entity;
        }
    }

    void attach(Mob mob, String modelName) {
        try {
            if (adapter == null || !plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")
                    || !plugin.getServer().getPluginManager().isPluginEnabled("GeyserModelEngine")) {
                if (plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")) {
                    if (cleanup == null) cleanup = new BetterModelCleanup(plugin);
                    cleanup.attach(mob, modelName);
                }
                restoreScale(plugin, mob);
                return;
            }
            adapter.attach(mob, modelName);
        } catch (RuntimeException | LinkageError error) {
            unload(mob);
            plugin.getLogger().warning("Dinosaur model " + modelName + " unavailable: " + error.getMessage());
        }
    }

    void animation(Mob mob, String animation) {
        if (adapter == null) return;
        try {
            adapter.animation(mob, animation);
        } catch (RuntimeException | LinkageError error) {
            unload(mob);
            plugin.getLogger().warning("Dinosaur animation unavailable: " + error.getMessage());
        }
    }

    void attack(Mob mob, boolean spit) {
        if (adapter == null) return;
        try {
            adapter.attack(mob, spit);
        } catch (RuntimeException | LinkageError error) {
            unload(mob);
            plugin.getLogger().warning("Dinosaur attack animation unavailable: " + error.getMessage());
        }
    }

    void seat(Mob carrier, Entity seat, double rearDistance, double relativeSeatY) {
        if (adapter == null) return;
        try {
            adapter.seat(carrier, seat, rearDistance, relativeSeatY);
        } catch (RuntimeException | LinkageError error) {
            clearSeat(carrier);
            plugin.getLogger().warning("Dinosaur passenger model unavailable: " + error.getMessage());
        }
    }

    void clearSeat(Mob carrier) {
        if (adapter == null) return;
        try {
            adapter.clearSeat(carrier);
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Dinosaur passenger model cleanup failed: " + error.getMessage());
        }
    }

    void growth(Mob mob) {
        if (adapter != null) {
            try {
                adapter.growth(mob);
            } catch (RuntimeException | LinkageError error) {
                unload(mob);
                plugin.getLogger().warning("Dinosaur growth model unavailable: " + error.getMessage());
            }
        }
        if (adapter == null || !mob.getPersistentDataContainer().has(new NamespacedKey(plugin, "model-original-scale"))) {
            Long adultAt = mob.getPersistentDataContainer().get(new NamespacedKey(plugin, "adult-at"), PersistentDataType.LONG);
            Long duration = mob.getPersistentDataContainer().get(new NamespacedKey(plugin, "growth-duration"), PersistentDataType.LONG);
            var scale = mob.getAttribute(Attribute.SCALE);
            if (adultAt != null && scale != null) {
                var baseline = new NamespacedKey(plugin, "growth-original-scale");
                double value = mob.getPersistentDataContainer().getOrDefault(baseline, PersistentDataType.DOUBLE, 1.0);
                if (!Double.isFinite(value) || value <= 0) value = 1;
                long now = System.currentTimeMillis();
                scale.setBaseValue(value * DinoGrowth.scale(adultAt, now, duration == null ? DinoGrowth.DURATION_MS : duration));
                if (adultAt <= now) mob.getPersistentDataContainer().remove(baseline);
            }
        }
    }

    void unload(Entity entity) {
        if (adapter != null) adapter.unload(entity);
        if (entity instanceof Mob mob) {
            if (cleanup != null) cleanup.unload(mob);
            restoreScale(plugin, mob);
        }
    }

    static void restoreScale(JavaPlugin plugin, Mob mob) {
        var key = new NamespacedKey(plugin, "model-original-scale");
        var data = mob.getPersistentDataContainer();
        Double value = data.get(key, PersistentDataType.DOUBLE);
        var scale = mob.getAttribute(Attribute.SCALE);
        if (value != null && scale != null) {
            if (Double.isFinite(value) && value > 0) {
                Long adultAt = data.get(new NamespacedKey(plugin, "adult-at"), PersistentDataType.LONG);
                Long duration = data.get(new NamespacedKey(plugin, "growth-duration"), PersistentDataType.LONG);
                long now = System.currentTimeMillis();
                var baseline = new NamespacedKey(plugin, "growth-original-scale");
                // Saved native size ages while unloaded; retain its adult baseline, not its old growth ratio.
                if (adultAt != null && adultAt > now) data.set(baseline, PersistentDataType.DOUBLE, value);
                else data.remove(baseline);
                scale.setBaseValue(value * DinoGrowth.scale(adultAt, now, duration == null ? DinoGrowth.DURATION_MS : duration));
            }
            data.remove(key);
        }
    }

    @Override public void close() {
        if (cleanup != null) {
            cleanup.close();
            cleanup = null;
        }
        if (adapter != null) {
            adapter.close();
            adapter = null;
        }
    }
}
