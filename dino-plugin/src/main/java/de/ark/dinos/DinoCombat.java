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
    private Player attackActor;
    private boolean landed;

    DinoCombat(ArkDinos plugin) {
        this.plugin = plugin;
        protection = new DinoProtection(plugin, this);
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

    Player actor(Mob mob) {
        Player driver = plugin.driver(mob);
        return driver != null ? driver : plugin.passengers(mob).isEmpty() ? owner(mob) : null;
    }

    Player damageActor(Mob mob) { return attacking == mob ? attackActor : null; }

    private boolean available(Mob mob) {
        return mob.isValid() && !mob.isDead() && plugin.prototype(mob) && meleeDamage(mob) > 0 && plugin.adult(mob);
    }

    private double meleeDamage(Mob mob) {
        return switch (plugin.species(mob)) {
            case "trex" -> 20;
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
        return validTarget(mob, target, actor(mob));
    }

    boolean validTarget(Mob mob, LivingEntity target, Player actor) {
        if (!available(mob) || !target.isValid() || target.isDead() || target.equals(mob) || DinoSeats.isSeat(target)
                || plugin.passengers(mob).contains(target)
                || !target.getWorld().equals(mob.getWorld()) || mob.getLocation().distanceSquared(target.getLocation()) > 24 * 24) return false;
        if (!plugin.owned(mob)) return predator(mob) && !plugin.formerOwner(mob, target)
                && target instanceof Player player && player.isOnline() && plugin.survival(player);
        if (actor == null || !actor.equals(actor(mob)) || (plugin.staying(mob) && plugin.driver(mob) == null)
                || !actor.isOnline() || actor.isDead() || !plugin.survival(actor)
                || !actor.getWorld().equals(mob.getWorld()) || actor.getLocation().distanceSquared(mob.getLocation()) > 48 * 48
                || target.equals(actor) || target.getUniqueId().toString().equals(plugin.owner(mob))
                || plugin.owned(target)
                || target instanceof org.bukkit.entity.Tameable pet && pet.isTamed()) return false;
        if (target instanceof Player player && (!player.isOnline() || !plugin.survival(player)
                || !Boolean.TRUE.equals(mob.getWorld().getGameRuleValue(org.bukkit.GameRules.PVP)) || sameTeamBlocks(actor, player))) return false;
        return protection.allows(actor, mob, target);
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
                && living.equals(attackTarget) && validTarget(mob, living, attackActor);
    }

    void cancel(Mob mob) {
        targets.remove(mob.getUniqueId());
        spit.cancel(mob);
        plugin.stop(mob);
    }

    private boolean meleeReach(Mob mob, Entity contact) {
        return mob.getBoundingBox().expand(1.5, 1, 1.5).overlaps(contact.getBoundingBox());
    }

    private double aimRange(Mob mob, org.bukkit.Location eye) {
        // The rider's eyes may sit above the old five-block ray on large species.
        // Cover the existing melee volume; meleeReach still decides whether a hit is possible.
        var box = mob.getBoundingBox().expand(1.5, 1, 1.5);
        double x = Math.max(Math.abs(eye.getX() - box.getMinX()), Math.abs(eye.getX() - box.getMaxX()));
        double y = Math.max(Math.abs(eye.getY() - box.getMinY()), Math.abs(eye.getY() - box.getMaxY()));
        double z = Math.max(Math.abs(eye.getZ() - box.getMinZ()), Math.abs(eye.getZ() - box.getMaxZ()));
        double range = Math.max(5, Math.sqrt(x * x + y * y + z * z));
        return "dilophosaurus".equals(plugin.species(mob)) ? Math.max(range, 8 + eye.distance(mob.getLocation())) : range;
    }

    boolean damage(Mob mob, LivingEntity target, double amount, boolean nativeMelee) {
        return damage(mob, target, amount, nativeMelee, actor(mob));
    }

    boolean damage(Mob mob, LivingEntity target, double amount, boolean nativeMelee, Player actor) {
        if (!validTarget(mob, target, actor)) return false;
        attacking = mob; attackTarget = target; attackActor = actor; landed = false;
        try {
            if (nativeMelee && mob instanceof org.bukkit.entity.Ravager) {
                var attribute = mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                double previous = attribute.getBaseValue();
                attribute.setBaseValue(amount);
                try { mob.attack(target); }
                finally { attribute.setBaseValue(previous); }
            } else target.damage(amount, mob);
            return landed;
        } finally { attacking = null; attackTarget = null; attackActor = null; }
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
            if (!plugin.passengers(mob).isEmpty()) { cancel(mob); continue; }
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
            if (plugin.owner(mob).equals(owner.getUniqueId().toString())) defend(mob, target);
        }
    }

    private void defend(Mob mob, LivingEntity target) {
        if (target != null && plugin.owned(mob) && plugin.passengers(mob).isEmpty() && validTarget(mob, target)) {
            targets.put(mob.getUniqueId(), target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0) return;
        if (event.getDamager().equals(attacking) && event.getEntity().equals(attackTarget)) landed = true;
        LivingEntity source = attacker(event.getDamager());
        if (event.getEntity() instanceof Player victim) defend(victim, source);
        if (event.getEntity() instanceof Mob victim) defend(victim, source);
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
                || !plugin.owned(mob) || !player.equals(plugin.driver(mob)) || !available(mob)) return false;
        int tick=Bukkit.getCurrentTick();
        Integer previous=lastAttack.get(mob.getUniqueId());
        if(previous!=null&&tick-previous<20)return true;
        var eye = player.getEyeLocation();
        var hit = player.getWorld().rayTrace(eye, eye.getDirection(), aimRange(mob, eye), FluidCollisionMode.NEVER, true, .2,
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
            if (player.equals(actor(mob)) || player.equals(targets.get(mob.getUniqueId()))) cancel(mob);
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
