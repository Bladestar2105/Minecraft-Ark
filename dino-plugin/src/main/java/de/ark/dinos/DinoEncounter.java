package de.ark.dinos;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.scheduler.BukkitTask;

/** Explicit film encounter; ecology never calls start and never respawns actors. */
final class DinoEncounter implements AutoCloseable,Listener {
    private static final String WORLD="jurassic_archipelago";
    static final String TAG="ark_jurassic_scene";
    private final ArkDinos plugin;
    private final List<UUID> actors=new ArrayList<>();
    private final Map<UUID,Integer> portalCooldowns=new HashMap<>();
    private final BukkitTask task;
    private EncounterRules.Phase phase=EncounterRules.Phase.IDLE;
    private String token;
    private int ticks;
    private long readyAt;
    private Mob attacking,attackTarget;
    DinoEncounter(ArkDinos plugin) {
        this.plugin=plugin;
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    boolean owns(org.bukkit.entity.Entity entity) {
        return token!=null && entity!=null && entity.isValid() && !entity.isDead()
                && actors.contains(entity.getUniqueId()) && entity.getScoreboardTags().contains(TAG)
                && entity.getScoreboardTags().contains(token);
    }
    List<Mob> activeActors(World world) {
        return actors.stream().map(plugin.getServer()::getEntity).filter(e->e instanceof Mob && owns(e))
                .map(Mob.class::cast).filter(m->m.getWorld().equals(world)).toList();
    }
    private void restorePortalCooldown(org.bukkit.entity.Entity entity) {
        Integer original=portalCooldowns.remove(entity.getUniqueId());
        if(original!=null) entity.setPortalCooldown(original);
    }
    void adopted(Mob mob) {
        if(!owns(mob) || !plugin.owned(mob)) return;
        restorePortalCooldown(mob);
        mob.removeScoreboardTag(TAG);mob.removeScoreboardTag(token);
    }
    boolean permitsDamage(Mob mob,org.bukkit.entity.Entity target) {
        return phase==EncounterRules.Phase.RUNNING && attacking==mob && target==attackTarget
                && owns(mob) && owns(target) && !plugin.owned(mob) && !plugin.owned(target)
                && "trex".equals(plugin.species(mob)) && "gallimimus".equals(plugin.species(target))
                && mob.getWorld().equals(target.getWorld()) && mob.getLocation().distanceSquared(target.getLocation())<9
                && mob.hasLineOfSight(target);
    }
    boolean command(CommandSender sender,String[] args) {
        if(!sender.hasPermission("ark.jurassic.scene")){sender.sendMessage("Scene control requires ark.jurassic.scene.");return true;}
        try {
            if(args.length!=2) {sender.sendMessage("/dinos scene start|stop|status");return true;}
            switch(args[1]) {
                case "start" -> start();
                case "stop" -> cleanup();
                case "status" -> { }
                default -> throw new IllegalArgumentException("Use start, stop or status");
            }
            sender.sendMessage("Jurassic scene: "+phase+", temporary actors="+activeActors(plugin.getServer().getWorld(WORLD)).size()+", cooldown_ms="+Math.max(0,readyAt-System.currentTimeMillis()));
        } catch(RuntimeException error){sender.sendMessage("Scene refused: "+error.getMessage());}
        return true;
    }
    private boolean viewers(World world) {
        return world!=null && world.getPlayers().stream().anyMatch(p->plugin.survival(p) && !p.isDead()
                && p.getLocation().distanceSquared(new Location(world,8192,80,3072))<320*320);
    }
    private void start() {
        if(!actors.isEmpty() || System.currentTimeMillis()<readyAt) throw new IllegalStateException("Already active or cooling down");
        World world=plugin.getServer().getWorld(WORLD);
        if(!viewers(world)) throw new IllegalStateException("A survival spectator must be near the Nublar meadow");
        int population=0,nearby=0;
        Location center=new Location(world,8192,80,3072);
        for(var entity:world.getEntities()) if(entity.getScoreboardTags().contains("ark_dino")) {
            population++;
            if(entity.getLocation().distanceSquared(center)<192*192) nearby++;
        }
        int players=(int)world.getPlayers().stream().filter(plugin::survival).count();
        int limit=WildSpawnRules.limit(players,plugin.getConfig().getInt("wild-spawning.per-player",8),
                plugin.getConfig().getInt("wild-spawning.world-limit",64));
        if(!EncounterRules.budget(population,limit,nearby,plugin.getConfig().getInt("wild-spawning.nearby-limit",6)))
            throw new IllegalStateException("Shared world/nearby budget has no room for eight Gallimimus and one Rex");
        List<Location> positions=new ArrayList<>();
        for(int i=0;i<9;i++) {
            int x=i==8?8140:8210+(i%4)*24,z=i==8?2960:3020+(i/4)*26;
            if(!world.isChunkLoaded(x>>4,z>>4)) throw new IllegalStateException("Scene chunks must already be loaded by spectators");
            Location point=new Location(world,x+.5,world.getHighestBlockYAt(x,z,org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES)+1,z+.5,-90,0);
            if(!plugin.safeWildSpawn(point,i==8?"trex":"gallimimus")) throw new IllegalStateException("A scene position is obstructed or protected");
            positions.add(point);
        }
        token=UUID.randomUUID().toString();ticks=0;
        try {
            for(int i=0;i<positions.size();i++) {
                Mob actor=plugin.spawnWild(positions.get(i),i==8?"trex":"gallimimus");
                if(actor==null || !actor.isValid()) throw new IllegalStateException("A native spawn was denied");
                actors.add(actor.getUniqueId());actor.addScoreboardTag(TAG);actor.addScoreboardTag(token);
                portalCooldowns.put(actor.getUniqueId(),actor.getPortalCooldown());
                actor.setPortalCooldown(40);
                actor.removeScoreboardTag(DinoWildSpawns.NATURAL_TAG);
                actor.setPersistent(false);
                if(!actor.isTicking()) throw new IllegalStateException("Scene actors require entity-ticking chunks; move closer or check simulation distance");
            }
            phase=EncounterRules.Phase.GRAZING;
        } catch(RuntimeException error){cleanup();throw error;}
    }
    private void tick() {
        if(actors.isEmpty()) {if(phase==EncounterRules.Phase.COOLDOWN && System.currentTimeMillis()>=readyAt) phase=EncounterRules.Phase.IDLE;return;}
        World world=plugin.getServer().getWorld(WORLD);
        ticks+=10;
        phase=EncounterRules.phase(ticks,viewers(world));
        if(phase==EncounterRules.Phase.COOLDOWN){cleanup();return;}
        // shortcut: Paper26.3 ticks portal cooldown entities; recheck on upgrades.
        // Keep only these nine temporary actors active through grazing pauses;
        // restore their original value immediately on adoption or cleanup.
        for(UUID id:List.copyOf(portalCooldowns.keySet())) {
            var entity=plugin.getServer().getEntity(id);
            if(!(entity instanceof Mob actor) || !owns(actor) || !actor.isTicking()) {cleanup();return;}
            if(plugin.owned(actor)) adopted(actor);
            else actor.setPortalCooldown(40);
        }
        if(phase==EncounterRules.Phase.RUNNING) {
            var rex=plugin.getServer().getEntity(actors.getLast());
            var prey=actors.subList(0,actors.size()-1).stream().map(plugin.getServer()::getEntity)
                    .filter(e->e instanceof Mob m && m.isValid() && !m.isDead() && !plugin.owned(m)
                            && m.getScoreboardTags().contains(TAG) && m.getScoreboardTags().contains(token))
                    .findFirst().orElse(null);
            if(rex instanceof Mob predator && prey instanceof Mob target && !plugin.owned(predator)) {
                Location a=predator.getLocation(),b=target.getLocation();
                for(int x=Math.min(a.getBlockX(),b.getBlockX())>>4;x<=(Math.max(a.getBlockX(),b.getBlockX())>>4);x++)
                    for(int z=Math.min(a.getBlockZ(),b.getBlockZ())>>4;z<=(Math.max(a.getBlockZ(),b.getBlockZ())>>4);z++)
                        if(!world.isChunkLoaded(x,z)){cleanup();return;}
                predator.setAware(true);
                predator.getPathfinder().moveTo(b,plugin.groundSpeed(predator,true));
                if(ticks%20==0 && a.distanceSquared(b)<9 && predator.hasLineOfSight(target)) {
                    plugin.attackAnimation(predator,false);
                    attacking=predator;attackTarget=target;
                    try{target.damage(10,predator);}finally{attacking=null;attackTarget=null;}
                }
            }
        }
    }
    @EventHandler public void death(EntityDeathEvent event) {
        if(token!=null && actors.contains(event.getEntity().getUniqueId()) && event.getEntity().getScoreboardTags().contains(TAG)
                && event.getEntity().getScoreboardTags().contains(token)) {
            event.getDrops().clear();event.setDroppedExp(0);
            portalCooldowns.remove(event.getEntity().getUniqueId());
        }
    }
    private void cleanup() {
        for(UUID id:actors) {
            var entity=plugin.getServer().getEntity(id);
            if(entity==null) continue;
            restorePortalCooldown(entity);
            if(!entity.getScoreboardTags().contains(TAG) || !entity.getScoreboardTags().contains(token)) continue;
            if(plugin.owned(entity)) {
                entity.removeScoreboardTag(TAG);entity.removeScoreboardTag(token);entity.setPersistent(true);
            } else entity.remove();
        }
        actors.clear();portalCooldowns.clear();attacking=null;attackTarget=null;
        token=null;readyAt=System.currentTimeMillis()+300_000;phase=EncounterRules.Phase.COOLDOWN;
    }
    @Override public void close(){task.cancel();cleanup();}
}
