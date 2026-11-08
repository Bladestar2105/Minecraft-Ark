package de.ark.dinos;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.scheduler.BukkitTask;

final class DinoCombat implements Listener {
    private final ArkDinos plugin;
    private final DinoProtection protection;
    private final DinoSpit spit;
    private final Map<UUID, Mob> wild = new HashMap<>();
    private final Map<UUID, LivingEntity> targets = new HashMap<>();
    private final Map<UUID, Integer> lastAttack = new HashMap<>();
    private final BukkitTask task;
    private Mob attacking;
    private LivingEntity attackTarget;
    private boolean landed;

    DinoCombat(ArkDinos plugin) {
        this.plugin = plugin;
        protection = new DinoProtection(plugin);
        spit = new DinoSpit(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    private boolean predator(Entity entity) {
        return plugin.prototype(entity) && SpeciesRules.ravager(plugin.species(entity));
    }

    private boolean wildLand(Mob mob) {
        String species=plugin.species(mob);
        return plugin.prototype(mob) && !plugin.owned(mob) && species!=null && CareRules.SPECIES.contains(species)
                && mob.getScoreboardTags().contains("ark_dino")
                && (mob instanceof org.bukkit.entity.Sniffer || mob instanceof org.bukkit.entity.Ravager);
    }

    void restore(Mob mob) {
        wild.remove(mob.getUniqueId());
        if (!wildLand(mob) || !plugin.prepare(mob)) return;
        wild.put(mob.getUniqueId(), mob);
    }

    private Player owner(Mob mob) {
        try { return plugin.getServer().getPlayer(UUID.fromString(plugin.owner(mob))); }
        catch (IllegalArgumentException invalid) { return null; }
    }

    private boolean available(Mob mob) {
        return mob.isValid() && !mob.isDead() && plugin.prototype(mob) && meleeDamage(mob) > 0 && plugin.adult(mob);
    }

    private double meleeDamage(Mob mob) {
        return switch (plugin.species(mob)) {
            case "trex" -> 12;
            case "velociraptor" -> 6;
            case "dilophosaurus" -> 5;
            case "triceratops", "stegosaurus" -> 8;
            case "brachiosaurus" -> 10;
            case "pteranodon" -> 4;
            case null, default -> 0;
        };
    }

    private boolean sameTeamBlocks(Player owner, Player target) {
        for (var board : List.of(plugin.getServer().getScoreboardManager().getMainScoreboard(), owner.getScoreboard(), target.getScoreboard())) {
            var team = board.getEntryTeam(owner.getName());
            if (team != null && team.equals(board.getEntryTeam(target.getName())) && !team.allowFriendlyFire()) return true;
        }
        return false;
    }

    boolean validTarget(Mob mob, LivingEntity target) {
        if (!available(mob) || !target.isValid() || target.isDead() || target.equals(mob)
                || !target.getWorld().equals(mob.getWorld()) || mob.getLocation().distanceSquared(target.getLocation()) > 24 * 24) return false;
        if (!plugin.owned(mob)) return predator(mob) && !plugin.formerOwner(mob, target)
                && target instanceof Player player && player.isOnline() && plugin.survival(player);
        Player owner = owner(mob);
        if (owner == null || (plugin.staying(mob) && !mob.getPassengers().contains(owner)) || !owner.isOnline() || owner.isDead() || !plugin.survival(owner)
                || !owner.getWorld().equals(mob.getWorld()) || owner.getLocation().distanceSquared(mob.getLocation()) > 48 * 48
                || target.equals(owner) || plugin.owned(target)) return false;
        if (target instanceof Player player && (!player.isOnline() || !plugin.survival(player)
                || !Boolean.TRUE.equals(mob.getWorld().getGameRuleValue(org.bukkit.GameRules.PVP)) || sameTeamBlocks(owner, player))) return false;
        return protection.allows(owner, mob, target);
    }

    boolean active(Mob mob) {
        LivingEntity target = targets.get(mob.getUniqueId());
        if (target == null) return false;
        if (validTarget(mob, target)) return true;
        cancel(mob);
        return false;
    }

    boolean permits(Mob mob, Entity target) {
        return attacking == mob && target instanceof LivingEntity living
                && living.equals(attackTarget) && validTarget(mob, living);
    }

    void cancel(Mob mob) {
        targets.remove(mob.getUniqueId());
        spit.cancel(mob);
        plugin.stop(mob);
    }

    private boolean meleeReach(Mob mob, Entity contact) {
        return mob.getBoundingBox().expand(1.5, 1, 1.5).overlaps(contact.getBoundingBox());
    }

    boolean damage(Mob mob, LivingEntity target, double amount, boolean nativeMelee) {
        if (!validTarget(mob, target)) return false;
        attacking = mob; attackTarget = target; landed = false;
        try {
            if (nativeMelee && mob instanceof org.bukkit.entity.Ravager) {
                var attribute = mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                double previous = attribute.getBaseValue();
                attribute.setBaseValue(amount);
                try { mob.attack(target); }
                finally { attribute.setBaseValue(previous); }
            } else target.damage(amount, mob);
            return landed;
        } finally { attacking = null; attackTarget = null; }
    }

    private boolean attack(Mob mob, LivingEntity target) {
        return attack(mob,target,target);
    }

    private boolean attack(Mob mob, LivingEntity target, Entity contact) {
        int tick = Bukkit.getCurrentTick();
        Integer previous = lastAttack.get(mob.getUniqueId());
        if ((previous != null && tick - previous < 20) || !target.equals(targets.get(mob.getUniqueId()))
                || !validTarget(mob, target) || !mob.hasLineOfSight(contact)) return false;
        if (!meleeReach(mob, contact)) {
            if ("dilophosaurus".equals(plugin.species(mob)) && spit.launch(mob, target)) {
                lastAttack.put(mob.getUniqueId(), tick);
                return true;
            }
            return false;
        }
        lastAttack.put(mob.getUniqueId(), tick);
        plugin.attackAnimation(mob, false);
        damage(mob, target, meleeDamage(mob), true);
        return true;
    }

    private void tick() {
        spit.tick();
        wild.values().removeIf(mob -> {
            if (mob.isValid() && !mob.isDead() && wildLand(mob)) return false;
            cancel(mob); lastAttack.remove(mob.getUniqueId()); return true;
        });
        for (Mob mob : wild.values()) {
            if (!active(mob) && available(mob) && predator(mob)) {
                Player nearest = null;
                double distance = 16 * 16;
                for (Player player : mob.getWorld().getPlayers()) {
                    double candidate = player.getLocation().distanceSquared(mob.getLocation());
                    if (candidate < distance && validTarget(mob, player) && mob.hasLineOfSight(player)) { nearest = player; distance = candidate; }
                }
                if (nearest != null) targets.put(mob.getUniqueId(), nearest);
            }
            if (!active(mob)) plugin.wanderWild(mob);
        }
        for (Mob mob : java.util.stream.Stream.concat(wild.values().stream(), plugin.companions().stream()).toList()) {
            if (!active(mob)) continue;
            if(plugin.aerial(mob)) {plugin.landPtera(mob);continue;}
            if (!mob.getPassengers().isEmpty()) { cancel(mob); continue; }
            LivingEntity target = targets.get(mob.getUniqueId());
            if ("dilophosaurus".equals(plugin.species(mob)) && !meleeReach(mob, target)
                    && mob.getLocation().distanceSquared(target.getLocation()) <= 8 * 8 && mob.hasLineOfSight(target)) {
                plugin.stop(mob);
                var direction = target.getLocation().subtract(mob.getLocation()).toVector();
                mob.setRotation((float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ())), 0);
            } else {
                mob.setAware(true);
                mob.getPathfinder().moveTo(target, plugin.groundSpeed(mob,true));
            }
            attack(mob, target);
        }
        lastAttack.keySet().removeIf(id -> plugin.getServer().getEntity(id) == null);
    }

    private LivingEntity attacker(Entity entity) {
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) return shooter;
        return entity instanceof LivingEntity living ? living : null;
    }

    private void defend(Player owner, LivingEntity target) {
        if (!owner.isOnline() || !plugin.survival(owner) || target == null) return;
        for (Mob mob : plugin.companions()) {
            if (plugin.owner(mob).equals(owner.getUniqueId().toString()) && mob.getPassengers().isEmpty() && validTarget(mob, target)) {
                targets.put(mob.getUniqueId(), target);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager().equals(attacking) && event.getEntity().equals(attackTarget) && event.getFinalDamage() > 0) landed = true;
        LivingEntity source = attacker(event.getDamager());
        if (event.getEntity() instanceof Player victim) defend(victim, source);
        if (source instanceof Player owner && event.getEntity() instanceof LivingEntity target) defend(owner, target);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        if (predator(event.getEntity()) && event.getTarget() != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swing(PlayerArmSwingEvent event) {
        if (event.getHand() == EquipmentSlot.HAND) manualAttack(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void playerAttack(PrePlayerAttackEntityEvent event) {
        // Paper26.3 target attacks no longer emit the arm-swing event used for air punches.
        if (plugin.combatTarget(event.getAttacked()) instanceof LivingEntity && manualAttack(event.getPlayer())) event.setCancelled(true);
    }

    private boolean manualAttack(Player player) {
        if (!(player.getVehicle() instanceof Mob mob) || !plugin.survival(player)
                || !plugin.owned(mob) || !plugin.owner(mob).equals(player.getUniqueId().toString()) || !available(mob)) return false;
        int tick=Bukkit.getCurrentTick();
        Integer previous=lastAttack.get(mob.getUniqueId());
        if(previous!=null&&tick-previous<20)return true;
        var eye = player.getEyeLocation();
        var hit = player.getWorld().rayTrace(eye, eye.getDirection(), "dilophosaurus".equals(plugin.species(mob)) ? 8 : 5, FluidCollisionMode.NEVER, true, .2,
                entity -> plugin.combatTarget(entity) instanceof LivingEntity target && !target.equals(player) && !target.equals(mob));
        Entity contact=hit==null?null:hit.getHitEntity();
        if (contact != null && plugin.combatTarget(contact) instanceof LivingEntity target && validTarget(mob, target)) {
            targets.put(mob.getUniqueId(), target);
            try { if(attack(mob, target, contact))return true; }
            finally { targets.remove(mob.getUniqueId()); }
        }
        lastAttack.put(mob.getUniqueId(),tick);
        plugin.attackAnimation(mob,false);
        return true;
    }

    private void playerUnavailable(Player player) {
        for (Mob mob : java.util.stream.Stream.concat(wild.values().stream(), plugin.companions().stream()).toList()) {
            if (plugin.owner(mob).equals(player.getUniqueId().toString()) || player.equals(targets.get(mob.getUniqueId()))) cancel(mob);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent event) { playerUnavailable(event.getPlayer()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { playerUnavailable(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mode(PlayerGameModeChangeEvent event) { if (event.getNewGameMode() != org.bukkit.GameMode.SURVIVAL) playerUnavailable(event.getPlayer()); }
    @EventHandler public void death(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) playerUnavailable(player);
        if (event.getEntity() instanceof Mob mob) { cancel(mob); wild.remove(mob.getUniqueId()); lastAttack.remove(mob.getUniqueId()); }
    }
    @EventHandler public void unload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) if (entity instanceof Mob mob) {
            spit.cancel(mob);
            targets.remove(mob.getUniqueId()); wild.remove(mob.getUniqueId()); lastAttack.remove(mob.getUniqueId());
        }
    }

    void close() {
        task.cancel();
        spit.close();
        protection.close();
        for (Mob mob : java.util.stream.Stream.concat(wild.values().stream(), plugin.companions().stream()).toList()) cancel(mob);
        wild.clear(); targets.clear(); lastAttack.clear(); HandlerList.unregisterAll(this);
    }
}
