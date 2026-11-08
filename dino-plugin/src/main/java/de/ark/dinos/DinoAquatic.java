package de.ark.dinos;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.VanillaGoal;
import io.papermc.paper.event.entity.EntityMoveEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

final class DinoAquatic implements Listener {
    private static final double[] MODEL_SIDES={-5.493,0,5.493},MODEL_UP={-2.318,0,3.5},MODEL_FORWARD={-14.501,0,10.069};
    private static final class Swimmer {
        final Dolphin mob;
        final Goal<Dolphin> wander;
        Player target;
        boolean chasing;
        int lastAttack = -20, nextPath, nextRecovery, nextBreach = Bukkit.getCurrentTick() + 900 + java.util.concurrent.ThreadLocalRandom.current().nextInt(300), breachTicks;
        Location launch;
        org.bukkit.util.BoundingBox corridor;
        Swimmer(Dolphin mob, Goal<Dolphin> wander) { this.mob = mob; this.wander = wander; }
    }
    private final ArkDinos plugin;
    private final NamespacedKey modelScale, breachOrigin;
    private final Map<UUID, Swimmer> swimmers = new HashMap<>();
    private final BukkitTask task;
    private Dolphin attacking;
    private Player victim;

    DinoAquatic(ArkDinos plugin) {
        this.plugin = plugin;
        modelScale = new NamespacedKey(plugin, "model-original-scale");
        breachOrigin = new NamespacedKey(plugin, "breach-origin-y");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private boolean mosa(Entity entity) {
        return entity instanceof Dolphin && SpeciesRules.aquatic(plugin.species(entity))
                && entity.getScoreboardTags().contains("ark_dino") && entity.getScoreboardTags().contains("ark_dino_wild");
    }

    private boolean eligible(Dolphin mob) {
        return mosa(mob) && mob.isValid() && !mob.isDead() && plugin.prototype(mob) && plugin.owner(mob).isEmpty();
    }

    void restore(Mob mob) {
        if (!(mob instanceof Dolphin dolphin) || !eligible(dolphin) || swimmers.containsKey(mob.getUniqueId())) return;
        var goals = plugin.getServer().getMobGoals();
        Goal<Dolphin> wander = null;
        for (var goal : java.util.List.copyOf(goals.getAllGoals(dolphin))) {
            if (goal.getKey().equals(VanillaGoal.RANDOM_SWIMMING)) wander = goal;
            else goals.removeGoal(dolphin, goal);
        }
        double oldMaximum = dolphin.getAttribute(Attribute.MAX_HEALTH).getValue(), health = dolphin.getHealth();
        dolphin.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);
        dolphin.setHealth(health >= oldMaximum ? 200 : Math.min(health, 200));
        dolphin.getAttribute(Attribute.SCALE).setBaseValue(4);
        dolphin.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(16);
        dolphin.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(16);
        dolphin.setPersistent(true); dolphin.setRemoveWhenFarAway(false); dolphin.setCanPickupItems(false);
        dolphin.setHasFish(false); dolphin.setTarget(null); dolphin.setAware(true);
        dolphin.setRemainingAir(dolphin.getMaximumAir()); dolphin.setMoistness(2400);
        var swimmer=new Swimmer(dolphin, wander);swimmers.put(mob.getUniqueId(), swimmer);
        var origin=dolphin.getPersistentDataContainer().get(breachOrigin,PersistentDataType.DOUBLE);
        if(origin!=null) {
            var landing=dolphin.getLocation();landing.setY(origin);
            beginBreach(swimmer,landing,true);
        }
    }

    private static boolean water(Location point) {
        var world = point.getWorld();
        return world.isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4) && point.getBlock().getType() == Material.WATER;
    }

    private static Location local(Location base, double side, double up, double forward) {
        double yaw = Math.toRadians(base.getYaw());
        return base.clone().add(side * Math.cos(yaw) - forward * Math.sin(yaw), up, side * Math.sin(yaw) + forward * Math.cos(yaw));
    }

    private boolean safe(Dolphin mob, Location point) {
        var box = mob.getBoundingBox().shift(point.toVector().subtract(mob.getLocation().toVector()));
        for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(Math.nextDown(box.getMaxX())); x++) {
            for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(Math.nextDown(box.getMaxY())); y++) {
                for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(Math.nextDown(box.getMaxZ())); z++) {
                    if (!water(new Location(point.getWorld(), x, y, z))) return false;
                }
            }
        }
        for (double side : MODEL_SIDES) for (double up : MODEL_UP) {
            for (double forward : MODEL_FORWARD) if (!water(local(point, side, up, forward))) return false;
        }
        return true;
    }

    static boolean spawnSpace(Location point) {
        var box=org.bukkit.util.BoundingBox.of(point,0,0,0);
        for(double side:MODEL_SIDES)for(double up:MODEL_UP)for(double forward:MODEL_FORWARD)box.union(local(point,side,up,forward));
        for(int x=(int)Math.floor(box.getMinX());x<=(int)Math.floor(box.getMaxX());x++)
            for(int y=(int)Math.floor(box.getMinY());y<=(int)Math.floor(box.getMaxY());y++)
                for(int z=(int)Math.floor(box.getMinZ());z<=(int)Math.floor(box.getMaxZ());z++)
                    if(!water(new Location(point.getWorld(),x,y,z)))return false;
        return true;
    }

    private boolean navigationLoaded(Dolphin mob) {
        // Native navigation caches a region around the path; keep that whole search inside already loaded chunks.
        int chunkX = mob.getLocation().getBlockX() >> 4, chunkZ = mob.getLocation().getBlockZ() >> 4;
        for (int x = chunkX - 2; x <= chunkX + 2; x++) for (int z = chunkZ - 2; z <= chunkZ + 2; z++) {
            if (!mob.getWorld().isChunkLoaded(x, z)) return false;
        }
        return true;
    }

    private org.bukkit.util.BoundingBox body(Dolphin mob,Location point) {
        var box=mob.getBoundingBox().shift(point.toVector().subtract(mob.getLocation().toVector()));
        for(double side:MODEL_SIDES)for(double up:MODEL_UP)for(double forward:MODEL_FORWARD)box.union(local(point,side,up,forward));
        return box;
    }

    private boolean clear(org.bukkit.World world,org.bukkit.util.BoundingBox box,boolean submerged) {
        if(box.getMinY()<world.getMinHeight()||box.getMaxY()>=world.getMaxHeight())return false;
        for(int x=(int)Math.floor(box.getMinX());x<=(int)Math.floor(box.getMaxX());x++)for(int z=(int)Math.floor(box.getMinZ());z<=(int)Math.floor(box.getMaxZ());z++) {
            if(!world.isChunkLoaded(x>>4,z>>4))return false;
            for(int y=(int)Math.floor(box.getMinY());y<=(int)Math.floor(box.getMaxY());y++) {
                var material=world.getBlockAt(x,y,z).getType();
                if(material!=Material.WATER&&(submerged||!material.isAir()))return false;
            }
        }
        return true;
    }

    private boolean beginBreach(Swimmer swimmer,Location launch,boolean recovering) {
        var mob=swimmer.mob;
        if(!navigationLoaded(mob)||!clear(mob.getWorld(),body(mob,launch),true))return false;
        if(!recovering) {
            boolean surface=false;
            for(int y=launch.getBlockY()+1;y<=launch.getY()+7;y++) {
                var material=mob.getWorld().getBlockAt(launch.getBlockX(),y,launch.getBlockZ()).getType();
                if(material!=Material.WATER) {surface=material.isAir();break;}
            }
            if(!surface)return false;
        }
        var top=launch.clone();top.setY(Math.max(launch.getY()+16,mob.getY()+1));
        var corridor=body(mob,launch).union(body(mob,top)).expand(.25,0,.25);
        if(!clear(mob.getWorld(),corridor,false))return false;
        swimmer.launch=launch.clone();swimmer.corridor=corridor;swimmer.breachTicks=1;
        mob.getPersistentDataContainer().set(breachOrigin,PersistentDataType.DOUBLE,launch.getY());
        stop(mob);mob.setAI(true);mob.setAware(false);mob.setVelocity(new Vector(0,recovering?-.2:1.5,0));
        plugin.animation(mob,"swim");return true;
    }

    private void breach(Swimmer swimmer) {
        var mob=swimmer.mob;var point=mob.getLocation();
        mob.setRotation(swimmer.launch.getYaw(),0);mob.setBodyYaw(swimmer.launch.getYaw());
        if(!navigationLoaded(mob)) {stop(mob);return;}
        if(swimmer.breachTicks++>5&&mob.getVelocity().getY()<=0&&clear(mob.getWorld(),body(mob,point),true)) {
            swimmer.breachTicks=0;mob.getPersistentDataContainer().remove(breachOrigin);stop(mob);mob.setAI(true);mob.setAware(true);return;
        }
        if(swimmer.breachTicks>120) {stop(mob);swimmer.breachTicks=0;return;}
        boolean returning=!clear(mob.getWorld(),swimmer.corridor,false)||!clear(mob.getWorld(),body(mob,swimmer.launch),true);
        mob.setAI(true);mob.setAware(false);
        mob.setVelocity(new Vector(0,returning?Math.min(-.2,mob.getVelocity().getY()):mob.getVelocity().getY(),0));
        plugin.animation(mob,"swim");
    }

    private void stop(Dolphin mob) {
        plugin.stop(mob); mob.setVelocity(new Vector()); mob.setAI(false);
    }

    private boolean valid(Dolphin mob, Player player) {
        return eligible(mob) && mob.isInWater() && player != null && player.isOnline() && !player.isDead()
                && plugin.survival(player) && player.isInWater() && player.getWorld().equals(mob.getWorld())
                && player.getLocation().distanceSquared(mob.getLocation()) <= 32 * 32;
    }

    private Location mouth(Dolphin mob) {
        return mob.getPersistentDataContainer().has(modelScale, PersistentDataType.DOUBLE)
                ? local(mob.getLocation(), 0, 1.0625, 9.4375) : mob.getEyeLocation();
    }

    private boolean biteReach(Dolphin mob, Player player) {
        var mouth = mouth(mob);
        if (!player.getBoundingBox().expand(2, 1, 2).contains(mouth.toVector()) || !mob.hasLineOfSight(player)) return false;
        var direction = player.getBoundingBox().getCenter().subtract(mouth.toVector());
        return direction.lengthSquared() == 0 || mouth.getWorld().rayTraceBlocks(mouth, direction, direction.length(), org.bukkit.FluidCollisionMode.NEVER, true) == null;
    }

    private void chase(Swimmer swimmer, Player target) {
        var mob = swimmer.mob;
        if (!swimmer.chasing) {
            if (swimmer.wander != null) plugin.getServer().getMobGoals().removeGoal(mob, swimmer.wander);
            swimmer.chasing = true;
        }
        var direction = target.getLocation().subtract(mob.getLocation()).toVector();
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float difference = (yaw - mob.getYaw() + 540) % 360 - 180;
        var facing = mob.getLocation(); facing.setYaw(mob.getYaw() + Math.clamp(difference, -3, 3)); facing.setPitch(0);
        if (safe(mob, facing)) { mob.setRotation(facing.getYaw(), 0); mob.setBodyYaw(facing.getYaw()); }
        if (biteReach(mob, target)) {
            mob.getPathfinder().stopPathfinding(); mob.setVelocity(mob.getVelocity().multiply(.5));
            int tick = Bukkit.getCurrentTick();
            if (tick - swimmer.lastAttack < 20) return;
            swimmer.lastAttack = tick;
            plugin.attackAnimation(mob, false);
            attacking = mob; victim = target;
            try { mob.attack(target); }
            finally { attacking = null; victim = null; }
        } else if (Bukkit.getCurrentTick() >= swimmer.nextPath) {
            swimmer.nextPath = Bukkit.getCurrentTick() + 10;
            mob.getPathfinder().moveTo(target, .8);
        }
    }

    private void tick() {
        var iterator = swimmers.values().iterator();
        while (iterator.hasNext()) {
            var swimmer = iterator.next(); var mob = swimmer.mob;
            if (!eligible(mob)) { if (mob.isValid()) stop(mob); iterator.remove(); continue; }
            mob.setRotation(mob.getYaw(), 0); mob.setBodyYaw(mob.getYaw());
            mob.setRemainingAir(mob.getMaximumAir());
            if (mob.isInWater()) mob.setMoistness(2400);
            var origin=mob.getPersistentDataContainer().get(breachOrigin,PersistentDataType.DOUBLE);
            if(swimmer.breachTicks==0&&origin!=null&&Bukkit.getCurrentTick()>=swimmer.nextRecovery) {
                swimmer.nextRecovery=Bukkit.getCurrentTick()+20;
                var landing=mob.getLocation();landing.setY(origin);beginBreach(swimmer,landing,true);
            }
            if(swimmer.breachTicks>0) {breach(swimmer);continue;}
            if (!safe(mob, mob.getLocation()) || !navigationLoaded(mob)) { stop(mob); swimmer.target = null; plugin.animation(mob, "idle"); continue; }
            mob.setAI(true); mob.setAware(true);
            if (!valid(mob, swimmer.target)) swimmer.target = null;
            if (swimmer.target == null) {
                double distance = 24 * 24;
                for (var player : mob.getWorld().getPlayers()) {
                    double candidate = player.getLocation().distanceSquared(mob.getLocation());
                    if (candidate < distance && valid(mob, player) && mob.hasLineOfSight(player)) { swimmer.target = player; distance = candidate; }
                }
            }
            if (swimmer.target != null) chase(swimmer, swimmer.target);
            else if (swimmer.chasing) {
                mob.getPathfinder().stopPathfinding(); swimmer.chasing = false;
                if (swimmer.wander != null) plugin.getServer().getMobGoals().addGoal(mob, 1, swimmer.wander);
            }
            if(swimmer.target==null&&mob.getPassengers().isEmpty()&&Bukkit.getCurrentTick()>=swimmer.nextBreach) {
                swimmer.nextBreach=Bukkit.getCurrentTick()+900+java.util.concurrent.ThreadLocalRandom.current().nextInt(300);
                if(beginBreach(swimmer,mob.getLocation(),false))continue;
            }
            plugin.animation(mob, mob.getVelocity().lengthSquared() > .0001 ? "swim" : "idle");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Dolphin mob) || !swimmers.containsKey(mob.getUniqueId())) return;
        var destination = event.getTo(); destination.setPitch(0); event.setTo(destination);
        var swimmer=swimmers.get(mob.getUniqueId());
        if(swimmer.breachTicks>0) {
            destination.setYaw(swimmer.launch.getYaw());event.setTo(destination);
            var swept=body(mob,event.getFrom()).union(body(mob,destination));
            if(!navigationLoaded(mob)||!swimmer.corridor.contains(swept)||!clear(mob.getWorld(),swept,false)) {
                event.setCancelled(true);stop(mob);mob.setVelocity(new Vector(0,-.2,0));
            } else mob.setBodyYaw(destination.getYaw());
            return;
        }
        if (!safe(mob, destination)) {
            event.setCancelled(true); stop(mob);
            if (!safe(mob, event.getFrom()) || !navigationLoaded(mob)) return;
            // Native swimming adds upward buoyancy even without a path; retreat from a rejected step before retrying.
            var retreat=event.getFrom().toVector().subtract(destination.toVector());
            if(retreat.lengthSquared()<.0001)retreat=event.getFrom().getDirection().multiply(-1);
            retreat.normalize().multiply(.15);
            for(var velocity:java.util.List.of(retreat,new Vector(0,-.15,0),new Vector(0,.15,0))) {
                if(safe(mob,event.getFrom().clone().add(velocity))) {mob.setVelocity(velocity);break;}
            }
        }
        else mob.setBodyYaw(destination.getYaw());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Dolphin mob) || !mosa(mob)) return;
        if (attacking != mob || !event.getEntity().equals(victim) || !valid(mob, victim) || !biteReach(mob, victim)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) { if (mosa(event.getEntity()) && event.getTarget() != null) event.setCancelled(true); }
    @EventHandler public void death(EntityDeathEvent event) { swimmers.remove(event.getEntity().getUniqueId()); }
    @EventHandler public void unload(EntitiesUnloadEvent event) {
        for (var entity : event.getEntities()) {
            var swimmer = swimmers.remove(entity.getUniqueId()); if (swimmer != null) stop(swimmer.mob);
        }
    }

    void close() {
        task.cancel(); swimmers.values().forEach(swimmer -> stop(swimmer.mob)); swimmers.clear(); HandlerList.unregisterAll(this);
    }
}
