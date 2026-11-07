package de.ark.dinos;
import java.util.List;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
public final class GameplayFixture extends JavaPlugin implements org.bukkit.event.Listener {
 private String lastDeath="none";
 public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
 @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
 public void died(org.bukkit.event.entity.EntityDeathEvent event){if(event.getEntity().getScoreboardTags().contains("flight_death"))lastDeath="gravity="+event.getEntity().hasGravity()+";flight="+event.getEntity().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:flight"));}
 public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!sender.getName().equals("Rcon")&&!sender.getName().equals("CONSOLE"))return true;
  if(args[0].equals("player")){var player=getServer().getPlayer(args[1]);sender.sendMessage("slowfall="+player.hasPotionEffect(PotionEffectType.SLOW_FALLING));return true;}
  if(args[0].equals("death-state")){sender.sendMessage(lastDeath);return true;}
  Mob mob=(Mob)getServer().getWorld("ark").getEntities().stream().filter(e->e.getScoreboardTags().contains(args[1])).findFirst().orElseThrow();
  var plugin=getServer().getPluginManager().getPlugin("ArkDinos");
  switch(args[0]){
   case "collision-state" -> {
    var rider=(org.bukkit.entity.Player)mob.getPassengers().getFirst();var block=mob.getWorld().getBlockAt(0,8,0);
    sender.sendMessage("riderOverlap="+block.getBoundingBox().overlaps(rider.getBoundingBox())+";riderBox="+rider.getBoundingBox()+";eyeY="+rider.getEyeLocation().getY()+";mountY="+mob.getLocation().getY());return true;
   }
   case "model-state" -> {
    try {
     var optional=(java.util.Optional<?>)Class.forName("kr.toxicity.model.api.BetterModel").getMethod("registry",java.util.UUID.class).invoke(null,mob.getUniqueId());
     if(optional.isEmpty()){sender.sendMessage("trackers=0;running=none");return true;}
     var registry=optional.get();var trackers=(java.util.Collection<?>)registry.getClass().getMethod("trackers").invoke(registry);
     var tracker=trackers.iterator().next();var pipeline=tracker.getClass().getMethod("getPipeline").invoke(tracker);
     sender.sendMessage("trackers="+trackers.size()+";running="+pipeline.getClass().getMethod("runningAnimation").invoke(pipeline));return true;
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
   }
   case "boundary" -> {
    mob.setVelocity(new org.bukkit.util.Vector(1,1,1));
    try {var method=plugin.getClass().getDeclaredMethod("flightVelocity",Mob.class,org.bukkit.util.Vector.class);method.setAccessible(true);method.invoke(plugin,mob,new org.bukkit.util.Vector(0,1,10000));}
    catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
   }
   case "disable" ->getServer().getPluginManager().disablePlugin(plugin);
   case "enable" ->getServer().getPluginManager().enablePlugin(plugin);
   case "load-event" ->getServer().getPluginManager().callEvent(new EntitiesLoadEvent(mob.getChunk(),List.of(mob)));
   case "unload-event" ->getServer().getPluginManager().callEvent(new EntitiesUnloadEvent(mob.getChunk(),List.of(mob)));
   case "world" ->{var world=new WorldCreator("ark_creative").type(WorldType.FLAT).generateStructures(false).createWorld();getServer().getPlayer(args[2]).teleport(world.getSpawnLocation());}
   default ->{}
  }
  var pdc=mob.getPersistentDataContainer();var loc=mob.getLocation();
  sender.sendMessage("uuid="+mob.getUniqueId()+";health="+mob.getHealth()+";x="+loc.getX()+";y="+loc.getY()+";z="+loc.getZ()+";owner="+pdc.get(NamespacedKey.fromString("arkdinos:owner"),PersistentDataType.STRING)+";stay="+pdc.get(NamespacedKey.fromString("arkdinos:stay"),PersistentDataType.BYTE)+";species="+pdc.get(NamespacedKey.fromString("arkdinos:species"),PersistentDataType.STRING)+";passengers="+mob.getPassengers().size()+";aware="+mob.isAware()+";path="+mob.getPathfinder().hasPath()+";gravity="+mob.hasGravity()+";vx="+mob.getVelocity().getX()+";vy="+mob.getVelocity().getY()+";vz="+mob.getVelocity().getZ());return true;
 }
}
