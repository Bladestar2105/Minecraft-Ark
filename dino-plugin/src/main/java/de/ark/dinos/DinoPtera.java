package de.ark.dinos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Loaded, clear perch flights and owner-follow flight; mounted flight remains in ArkDinos. */
final class DinoPtera implements Listener,AutoCloseable {
    // Conservative all-animation envelope from animated-bounds.json, including every wing/yaw.
    private static final double RADIUS=5.2, BELOW=.56, ABOVE=3.12;
    private static final class Bird {
        final Mob mob;
        int nextFlight=Bukkit.getCurrentTick()+200+ThreadLocalRandom.current().nextInt(200), nextFollow, phase, ticks;
        Location launch,landing,waypoint;
        Bird(Mob mob){this.mob=mob;}
    }
    private final ArkDinos plugin;
    private final NamespacedKey airborne,groundUntil,tameExpiry;
    private final Map<UUID,Bird> birds=new HashMap<>();
    private final BukkitTask task;

    DinoPtera(ArkDinos plugin) {
        this.plugin=plugin;airborne=new NamespacedKey(plugin,"ptera-airborne");groundUntil=new NamespacedKey(plugin,"ptera-ground-until");tameExpiry=new NamespacedKey(plugin,"tame-expiry");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }

    private boolean eligible(Mob mob) {
        return mob.isValid()&&!mob.isDead()&&plugin.prototype(mob)&&"pteranodon".equals(plugin.species(mob))&&mob.getScoreboardTags().contains("ark_dino");
    }

    void restore(Entity entity) {
        if(!(entity instanceof Mob mob)||!eligible(mob))return;
        if(mob.getPersistentDataContainer().has(airborne)||!mob.hasGravity())recover(mob);
        birds.computeIfAbsent(mob.getUniqueId(),id->new Bird(mob));
    }

    boolean controls(Mob mob) {var bird=birds.get(mob.getUniqueId());return bird!=null&&bird.phase!=0;}

    private boolean grounded(Mob mob) {
        var data=mob.getPersistentDataContainer();long now=System.currentTimeMillis();
        return data.getOrDefault(groundUntil,PersistentDataType.LONG,0L)>now||data.getOrDefault(tameExpiry,PersistentDataType.LONG,0L)>now;
    }

    boolean roam(Mob mob) {
        var bird=birds.get(mob.getUniqueId());if(bird==null)return false;
        if(bird.phase!=0)return true;
        if(!plugin.adult(mob)||grounded(mob)||mob.isInWater()||!mob.isOnGround()||!mob.getPassengers().isEmpty()
                ||plugin.owned(mob)&&(!plugin.roaming(mob)||plugin.staying(mob))||Bukkit.getCurrentTick()<bird.nextFlight)return false;
        bird.nextFlight=Bukkit.getCurrentTick()+200;
        var start=mob.getLocation();var lift=start.clone().add(0,5,0);
        for(int attempt=0;attempt<8;attempt++) {
            int dx=ThreadLocalRandom.current().nextInt(-12,13),dz=ThreadLocalRandom.current().nextInt(-12,13);
            if(dx*dx+dz*dz<36)continue;
            var landing=landing(mob,start.clone().add(dx,2,dz),4);if(landing==null)continue;
            double altitude=Math.max(start.getY(),landing.getY())+5;lift.setY(altitude);var above=landing.clone();above.setY(altitude);
            if(!clear(mob,body(start,start.getY()).union(body(lift,start.getY())))
                    ||!clear(mob,body(lift,Double.NEGATIVE_INFINITY).union(body(above,Double.NEGATIVE_INFINITY)))
                    ||!clear(mob,body(landing,landing.getY()).union(body(above,landing.getY()))))continue;
            bird.launch=start;bird.landing=landing;bird.waypoint=lift.clone();bird.phase=1;bird.ticks=0;
            plugin.stop(mob);mob.setAI(true);mob.setGravity(false);mob.getPersistentDataContainer().set(airborne,PersistentDataType.BYTE,(byte)1);
            return true;
        }
        return false;
    }

    private Player lure(Mob mob) {
        if(plugin.owned(mob))return null;
        Player nearest=null;double distance=16*16;
        for(var player:mob.getWorld().getPlayers()) {
            double candidate=player.getLocation().distanceSquared(mob.getLocation());
            var inventory=player.getInventory();
            if(candidate<distance&&!player.isDead()&&plugin.survival(player)
                    &&(CareRules.primary("pteranodon",inventory.getItemInMainHand().getType().name())||CareRules.primary("pteranodon",inventory.getItemInOffHand().getType().name()))&&mob.hasLineOfSight(player)) {nearest=player;distance=candidate;}
        }
        return nearest;
    }

    private Player followOwner(Mob mob) {
        if(!plugin.owned(mob)||!plugin.adult(mob)||plugin.staying(mob)||plugin.roaming(mob))return null;
        Player player;
        try {player=Bukkit.getPlayer(UUID.fromString(plugin.owner(mob)));}catch(IllegalArgumentException error){return null;}
        if(player==null||!player.isOnline()||player.isDead()||!plugin.survival(player)||!mob.getWorld().equals(player.getWorld())
                ||!plugin.ownerFlyingOnPtera(player)||mob.getLocation().distanceSquared(player.getVehicle().getLocation())>48*48)return null;
        return player;
    }

    private void follow(Bird bird,Player owner) {
        var mob=bird.mob;var point=mob.getLocation();var mount=owner.getVehicle().getLocation();
        var behind=point.toVector().subtract(mount.toVector()).setY(0);
        if(behind.lengthSquared()<.01)behind=mount.getDirection().setY(0).multiply(-1);
        behind.normalize().multiply(12);
        bird.waypoint=mount.clone().add(behind);bird.waypoint.setY(Math.min(mount.getY(),Math.min(bird.launch.getY()+48,mob.getWorld().getMaxHeight()-ABOVE-1)));
    }

    private BoundingBox body(Location point,double floor) {
        return new BoundingBox(point.getX()-RADIUS,Math.max(point.getY()-BELOW,floor),point.getZ()-RADIUS,
                point.getX()+RADIUS,point.getY()+ABOVE,point.getZ()+RADIUS);
    }

    private boolean clear(Mob mob,BoundingBox box) {
        var world=mob.getWorld();if(box.getMinY()<world.getMinHeight()||box.getMaxY()>=world.getMaxHeight())return false;
        for(int x=(int)Math.floor(box.getMinX())-1;x<=(int)Math.floor(box.getMaxX())+1;x++)for(int z=(int)Math.floor(box.getMinZ())-1;z<=(int)Math.floor(box.getMaxZ())+1;z++) {
            if(!world.isChunkLoaded(x>>4,z>>4))return false;
            for(int y=(int)Math.floor(box.getMinY())-1;y<=(int)Math.floor(box.getMaxY())+1;y++) {
                var block=world.getBlockAt(x,y,z);
                if(block.isLiquid()&&box.overlaps(new BoundingBox(x,y,z,x+1,y+1,z+1))||block.getCollisionShape().overlaps(box.clone().shift(-x,-y,-z)))return false;
            }
        }
        return true;
    }

    private Location landing(Mob mob,Location above,int depth) {
        var world=mob.getWorld();double half=mob.getWidth()/2;
        for(int y=above.getBlockY();y>=above.getBlockY()-depth&&y>world.getMinHeight();y--) {
            var point=new Location(world,above.getBlockX()+.5,y,above.getBlockZ()+.5);boolean floor=true;
            for(int x=(int)Math.floor(point.getX()-half);x<=(int)Math.floor(Math.nextDown(point.getX()+half));x++)for(int z=(int)Math.floor(point.getZ()-half);z<=(int)Math.floor(Math.nextDown(point.getZ()+half));z++) {
                if(!world.isChunkLoaded(x>>4,z>>4)||!world.getBlockAt(x,y-1,z).getType().isSolid())floor=false;
            }
            if(floor&&clear(mob,body(point,y)))return point;
        }
        return null;
    }

    void land(Mob mob) {
        var bird=birds.get(mob.getUniqueId());if(bird==null||bird.phase==0||!mob.getPassengers().isEmpty())return;
        if(bird.phase==3)return;
        var point=landing(mob,mob.getLocation(),16);
        if(point==null) {recover(mob);bird.phase=0;return;}
        bird.landing=point;bird.waypoint=point;bird.phase=3;bird.ticks=0;mob.setVelocity(new Vector());
    }

    private void recover(Mob mob) {
        mob.getPersistentDataContainer().remove(airborne);plugin.recoverAerial(mob);
    }

    private void finish(Bird bird) {
        bird.phase=0;bird.nextFlight=Bukkit.getCurrentTick()+200+ThreadLocalRandom.current().nextInt(200);
        bird.mob.getPersistentDataContainer().remove(airborne);bird.mob.setGravity(true);plugin.stop(bird.mob);plugin.animation(bird.mob,"idle");
    }

    private void tick() {
        var iterator=birds.values().iterator();
        while(iterator.hasNext()) {
            var bird=iterator.next();var mob=bird.mob;
            if(!eligible(mob)) {if(mob.isValid()&&controls(mob))recover(mob);iterator.remove();continue;}
            var lure=lure(mob);
            if(lure!=null) {
                var data=mob.getPersistentDataContainer();data.set(groundUntil,PersistentDataType.LONG,Math.max(data.getOrDefault(groundUntil,PersistentDataType.LONG,0L),System.currentTimeMillis()+2000));
                land(mob);
                if(bird.phase==0&&!plugin.combatActive(mob)&&Bukkit.getCurrentTick()%10==0) {
                    if(mob.getLocation().distanceSquared(lure.getLocation())<=9)plugin.stop(mob);
                    else {mob.setAware(true);mob.getPathfinder().moveTo(lure,plugin.groundSpeed(mob,false));}
                }
            }
            if(bird.phase==0) {
                var owner=followOwner(mob);
                if(owner==null||Bukkit.getCurrentTick()<bird.nextFollow||!mob.isOnGround()||mob.isInWater()||grounded(mob)||plugin.combatActive(mob)||!mob.getPassengers().isEmpty())continue;
                bird.launch=mob.getLocation();bird.phase=4;bird.ticks=0;
                mob.getPersistentDataContainer().set(airborne,PersistentDataType.BYTE,(byte)1);
            }
            if(!mob.getPassengers().isEmpty()) {
                bird.phase=0;mob.getPersistentDataContainer().remove(airborne);
                if(!plugin.takeAerialRide(mob))recover(mob);continue;
            }
            if(bird.phase==4) {
                var owner=followOwner(mob);
                if(owner==null) {bird.nextFollow=Bukkit.getCurrentTick()+100;land(mob);}
                else follow(bird,owner);
            }
            if(grounded(mob)||bird.phase!=4&&plugin.owned(mob)&&(!plugin.roaming(mob)||plugin.staying(mob))||plugin.combatActive(mob))land(mob);
            if(bird.phase==0)continue;
            if(bird.phase!=4&&++bird.ticks>240||mob.isInWater()) {recover(mob);bird.phase=0;continue;}
            var point=mob.getLocation();
            if(bird.phase==3&&(mob.isOnGround()&&Math.abs(point.getY()-bird.landing.getY())<.5||point.distanceSquared(bird.landing)<.04)) {finish(bird);continue;}
            if(point.distanceSquared(bird.waypoint)<.09) {
                if(bird.phase==1) {bird.phase=2;bird.waypoint=bird.landing.clone();bird.waypoint.setY(point.getY());}
                else if(bird.phase==2) {bird.phase=3;bird.waypoint=bird.landing;}
            }
            var direction=bird.waypoint.toVector().subtract(point.toVector());double speed=FlightRules.autonomousSpeed(bird.phase,point.distance(bird.waypoint));
            var motion=direction.lengthSquared()==0?new Vector():direction.clone().normalize().multiply(speed);
            if(bird.phase==4) {
                double horizontal=Math.hypot(direction.getX(),direction.getZ());
                motion=horizontal<.05?new Vector():new Vector(direction.getX(),0,direction.getZ()).multiply(FlightRules.autonomousSpeed(4,horizontal)/horizontal);
                motion.setY(Math.clamp(direction.getY(),-.4,.4));
            }
            double floor=bird.phase==1||bird.phase==4&&point.getY()<bird.launch.getY()+BELOW?bird.launch.getY():bird.phase==3?bird.landing.getY():Double.NEGATIVE_INFINITY;
            if(!clear(mob,body(point,floor).union(body(point.clone().add(motion),floor)))) {recover(mob);bird.phase=0;bird.nextFollow=Bukkit.getCurrentTick()+100;continue;}
            plugin.stop(mob);mob.setAI(true);mob.setGravity(false);
            if(motion.getX()*motion.getX()+motion.getZ()*motion.getZ()>.0001)mob.setRotation((float)Math.toDegrees(Math.atan2(-motion.getX(),motion.getZ())),0);
            plugin.aerialVelocity(mob,motion);
            boolean folded=point.getY()<floor+.75;plugin.animation(mob,folded?"idle":bird.phase==1||motion.getY()>.05?"flap":"glide");
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent event) {
        if(event.getFinalDamage()<=0||!(event.getEntity() instanceof Mob mob)||!eligible(mob)||!mob.getPassengers().isEmpty())return;
        Entity source=event.getDamager();if(source instanceof Projectile projectile&&projectile.getShooter() instanceof Entity shooter)source=shooter;
        if(!(source instanceof Player))return;
        mob.getPersistentDataContainer().set(groundUntil,PersistentDataType.LONG,System.currentTimeMillis()+60_000);land(mob);
    }

    @EventHandler public void death(EntityDeathEvent event) {var bird=birds.remove(event.getEntity().getUniqueId());if(bird!=null&&bird.phase!=0)recover(bird.mob);}
    @EventHandler public void unload(EntitiesUnloadEvent event) {for(var entity:event.getEntities()){var bird=birds.remove(entity.getUniqueId());if(bird!=null&&bird.phase!=0)recover(bird.mob);}}
    @Override public void close() {task.cancel();for(var bird:birds.values())if(bird.phase!=0||bird.mob.getPersistentDataContainer().has(airborne))recover(bird.mob);birds.clear();HandlerList.unregisterAll(this);}
}
