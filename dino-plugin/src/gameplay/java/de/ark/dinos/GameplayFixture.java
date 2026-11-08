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
 private java.util.UUID armedStay;
 private String lastStay="none";
 public void onEnable(){getServer().getPluginManager().registerEvents(this,this);}
 @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
 public void died(org.bukkit.event.entity.EntityDeathEvent event){if(event.getEntity().getScoreboardTags().contains("flight_death"))lastDeath="gravity="+event.getEntity().hasGravity()+";flight="+event.getEntity().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:flight"));}
 @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.LOWEST)
 public void movingBeforeStay(org.bukkit.event.player.PlayerInteractEntityEvent event){
  if(event.getRightClicked().getUniqueId().equals(armedStay)&&event.getRightClicked() instanceof Mob mob){
   mob.setVelocity(new org.bukkit.util.Vector(.3,-.08,.2));mob.setAware(true);mob.getPathfinder().moveTo(event.getPlayer());
  }
 }
 @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
 public void stoppedAfterStay(org.bukkit.event.player.PlayerInteractEntityEvent event){
  if(event.getRightClicked().getUniqueId().equals(armedStay)&&event.getRightClicked() instanceof Mob mob){
   lastStay="stay="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:stay"),PersistentDataType.BYTE)
    +";vx="+mob.getVelocity().getX()+";vy="+mob.getVelocity().getY()+";vz="+mob.getVelocity().getZ()+";aware="+mob.isAware()+";path="+mob.getPathfinder().hasPath();armedStay=null;
  }
 }
 public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
  if(!sender.getName().equals("Rcon")&&!sender.getName().equals("CONSOLE"))return true;
  if(args[0].equals("player")){var player=getServer().getPlayer(args[1]);if(player==null){sender.sendMessage("player=absent");return true;}sender.sendMessage("slowfall="+player.hasPotionEffect(PotionEffectType.SLOW_FALLING)+";main="+player.getInventory().getItemInMainHand().getAmount()+";off="+player.getInventory().getItemInOffHand().getAmount());return true;}
  if(args[0].equals("death-state")){sender.sendMessage(lastDeath);return true;}
  if(args[0].equals("create-world")){var world=new WorldCreator(args[1]).type(WorldType.FLAT).generateStructures(false).createWorld();world.setSpawnLocation(0,4,0);sender.sendMessage("world="+world.getKey());return true;}
  Mob mob=(Mob)getServer().getWorlds().stream().flatMap(world->world.getEntities().stream()).filter(e->e.getScoreboardTags().contains(args[1])).findFirst().orElseThrow();
  var plugin=getServer().getPluginManager().getPlugin("ArkDinos");
  switch(args[0]){
   case "growth-time" -> {
    var data=mob.getPersistentDataContainer();
    data.set(NamespacedKey.fromString("arkdinos:growth-duration"),PersistentDataType.LONG,300000L);
    data.set(NamespacedKey.fromString("arkdinos:adult-at"),PersistentDataType.LONG,System.currentTimeMillis()+Long.parseLong(args[2]));
    try {var field=plugin.getClass().getDeclaredField("models");field.setAccessible(true);var models=field.get(plugin);var grow=models.getClass().getDeclaredMethod("growth",Mob.class);grow.setAccessible(true);grow.invoke(models,mob);}
    catch(ReflectiveOperationException error){throw new IllegalStateException(error);}return true;
   }
   case "control-click" -> {
    var player=getServer().getPlayer(args[2]);
    var event=new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,Integer.parseInt(args[3]),org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
    getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());return true;
   }
   case "historical-growth" -> {
    try {
     var field=plugin.getClass().getDeclaredField("models");field.setAccessible(true);var models=field.get(plugin);
     var unload=models.getClass().getDeclaredMethod("unload",org.bukkit.entity.Entity.class);unload.setAccessible(true);
     var grow=models.getClass().getDeclaredMethod("growth",Mob.class);grow.setAccessible(true);
     var restore=plugin.getClass().getDeclaredMethod("restore",org.bukkit.entity.Entity.class);restore.setAccessible(true);
     unload.invoke(models,mob);var data=mob.getPersistentDataContainer();NamespacedKey adult=NamespacedKey.fromString("arkdinos:adult-at"),duration=NamespacedKey.fromString("arkdinos:growth-duration"),original=NamespacedKey.fromString("arkdinos:model-original-scale");
     long remaining=Long.parseLong(args[2]);double base=Double.parseDouble(args[3]);data.remove(duration);data.remove(original);
     data.set(adult,PersistentDataType.LONG,System.currentTimeMillis()+remaining);
     mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(base*(.45+.55*Math.max(0,1-remaining/900000d)));
     restore.invoke(plugin,mob);double saved=data.get(original,PersistentDataType.DOUBLE);long deadline=data.get(adult,PersistentDataType.LONG);
     var tracker=kr.toxicity.model.api.BetterModel.registry(mob.getUniqueId()).orElseThrow().trackers().iterator().next();double render=tracker.scaler().scale(tracker);
     unload.invoke(models,mob);double fallback=mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue();
     restore.invoke(plugin,mob);data.set(adult,PersistentDataType.LONG,0L);grow.invoke(models,mob);
     tracker=kr.toxicity.model.api.BetterModel.registry(mob.getUniqueId()).orElseThrow().trackers().iterator().next();double adultRender=tracker.scaler().scale(tracker);
     unload.invoke(models,mob);
     sender.sendMessage("uuid="+mob.getUniqueId()+";owner="+data.get(NamespacedKey.fromString("arkdinos:owner"),PersistentDataType.STRING)+";original="+saved+";render="+render+";deadline="+deadline+";duration="+data.get(duration,PersistentDataType.LONG)+";fallback="+fallback+";adultRender="+adultRender+";adultFallback="+mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue());return true;
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
   }
   case "clear-species" -> mob.getPersistentDataContainer().remove(NamespacedKey.fromString("arkdinos:species"));
   case "arm-stay" -> {armedStay=mob.getUniqueId();lastStay="none";}
   case "last-stay" -> {sender.sendMessage(lastStay);return true;}
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
     var details=new java.util.TreeSet<String>();
     for(var bone:(java.util.Collection<?>)tracker.getClass().getMethod("bones").invoke(tracker)){var running=bone.getClass().getMethod("runningAnimation").invoke(bone);if(running!=null)details.add(running.toString());}
     sender.sendMessage("trackers="+trackers.size()+";running="+pipeline.getClass().getMethod("runningAnimation").invoke(pipeline)+";details="+details);return true;
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
  sender.sendMessage("id="+mob.getEntityId()+";uuid="+mob.getUniqueId()+";health="+mob.getHealth()+";x="+loc.getX()+";y="+loc.getY()+";z="+loc.getZ()+";owner="+pdc.get(NamespacedKey.fromString("arkdinos:owner"),PersistentDataType.STRING)+";stay="+pdc.get(NamespacedKey.fromString("arkdinos:stay"),PersistentDataType.BYTE)+";species="+pdc.get(NamespacedKey.fromString("arkdinos:species"),PersistentDataType.STRING)+";passengers="+mob.getPassengers().size()+";aware="+mob.isAware()+";path="+mob.getPathfinder().hasPath()+";gravity="+mob.hasGravity()+";vx="+mob.getVelocity().getX()+";vy="+mob.getVelocity().getY()+";vz="+mob.getVelocity().getZ());return true;
 }
}
