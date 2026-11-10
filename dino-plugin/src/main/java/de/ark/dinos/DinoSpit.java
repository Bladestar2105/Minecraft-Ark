package de.ark.dinos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class DinoSpit implements Listener {
    private record Shot(Snowball ball, Mob source, LivingEntity target, String owner, int born) { }
    private final ArkDinos plugin;
    private final DinoCombat combat;
    private final NamespacedKey modelScaleKey;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final Map<UUID, Integer> lastShot = new HashMap<>();

    DinoSpit(ArkDinos plugin, DinoCombat combat) {
        this.plugin = plugin; this.combat = combat;
        modelScaleKey = new NamespacedKey(plugin, "model-original-scale");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean launch(Mob mob, LivingEntity target) {
        int tick = Bukkit.getCurrentTick();
        Integer previous = lastShot.get(mob.getUniqueId());
        if ((previous != null && tick - previous < 60) || !combat.validTarget(mob, target)
                || mob.getLocation().distanceSquared(target.getLocation()) > 8 * 8 || !mob.hasLineOfSight(target)) return false;
        var eye = mob.getEyeLocation();
        var mouth = eye.clone();
        if (mob.getPersistentDataContainer().has(modelScaleKey, PersistentDataType.DOUBLE)) {
            // Adult visual dimensions are independent of the native carrier's seat/collision scale.
            double yaw = Math.toRadians(mob.getBodyYaw());
            mouth = mob.getLocation().add(-Math.sin(yaw) * 1.9125, 1.4288648081, Math.cos(yaw) * 1.9125);
        }
        for (int x = Math.min(eye.getBlockX(), mouth.getBlockX()) >> 4; x <= Math.max(eye.getBlockX(), mouth.getBlockX()) >> 4; x++) {
            for (int z = Math.min(eye.getBlockZ(), mouth.getBlockZ()) >> 4; z <= Math.max(eye.getBlockZ(), mouth.getBlockZ()) >> 4; z++) {
                if (!mouth.getWorld().isChunkLoaded(x, z)) return false;
            }
        }
        var offset = mouth.toVector().subtract(eye.toVector());
        if (offset.lengthSquared() > 0 && mouth.getWorld().rayTraceBlocks(eye, offset, offset.length(), org.bukkit.FluidCollisionMode.NEVER, true) != null) return false;
        lastShot.put(mob.getUniqueId(), tick);
        var velocity = target.getBoundingBox().getCenter().subtract(mouth.toVector()).normalize().multiply(.7);
        Snowball ball = mob.getWorld().spawn(mouth, Snowball.class, projectile -> {
            projectile.setShooter(mob);
            projectile.setVelocity(velocity);
            projectile.addScoreboardTag("ark_dino_spit");
            projectile.setItem(new ItemStack(Material.BLACK_DYE));
            projectile.setGravity(false);
            projectile.setPersistent(false);
        });
        if (ball.isValid()) {
            shots.put(ball.getUniqueId(), new Shot(ball, mob, target, plugin.owner(mob), tick));
            plugin.attackAnimation(mob, true);
        } else ball.remove();
        return true;
    }

    private boolean valid(Shot shot) {
        return plugin.owner(shot.source()).equals(shot.owner()) && combat.validTarget(shot.source(), shot.target())
                && shot.source().getLocation().distanceSquared(shot.target().getLocation()) <= 8 * 8;
    }

    void tick() {
        var iterator = shots.values().iterator();
        while (iterator.hasNext()) {
            Shot shot = iterator.next();
            var point = shot.ball().getLocation();
            var next = point.clone().add(shot.ball().getVelocity().multiply(2));
            if (!shot.ball().isValid() || Bukkit.getCurrentTick() - shot.born() >= 40 || !valid(shot)
                    || !point.getWorld().equals(shot.source().getWorld()) || point.distanceSquared(shot.source().getLocation()) > 8 * 8
                    || !point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)
                    || !point.getWorld().isChunkLoaded(next.getBlockX() >> 4, next.getBlockZ() >> 4)) {
                shot.ball().remove(); iterator.remove();
            } else point.getWorld().spawnParticle(Particle.SQUID_INK, point, 1, .03, .03, .03, 0);
        }
        lastShot.keySet().removeIf(id -> plugin.getServer().getEntity(id) == null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void hit(ProjectileHitEvent event) {
        Shot shot = shots.remove(event.getEntity().getUniqueId());
        if (shot == null) return;
        boolean allowed = !event.isCancelled();
        event.setCancelled(true);
        shot.ball().remove();
        if (allowed && shot.target().equals(plugin.combatTarget(event.getHitEntity())) && valid(shot) && shot.source().hasLineOfSight(shot.target())
                && combat.damage(shot.source(), shot.target(), 3, false)) {
            shot.target().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
            shot.target().addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0));
        }
    }

    void cancel(Mob mob) {
        shots.values().removeIf(shot -> {
            if (!shot.source().equals(mob)) return false;
            shot.ball().remove(); return true;
        });
    }

    void close() {
        shots.values().forEach(shot -> shot.ball().remove());
        shots.clear(); lastShot.clear(); HandlerList.unregisterAll(this);
    }
}
