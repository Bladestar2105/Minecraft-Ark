package de.ark.dinos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import java.util.*;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Reads the installed candidate; no production adapter classes are compiled into this fixture. */
public final class ModelGameplayFixture extends JavaPlugin {
    private final Map<UUID,EntityTracker> previous = new HashMap<>();
    private final Map<UUID,Integer> previousActors = new HashMap<>();
    private final Map<Player,Set<Player>> viewers = new HashMap<>();
    @Override public void onEnable() {
        getServer().getScheduler().runTaskTimer(this, () -> viewers.forEach((player,set)->{if(player.isOnline())set.add(player);}), 1, 1);
    }
    private Object plugin() { return getServer().getPluginManager().getPlugin("ArkDinos"); }
    private static Object field(Object target,String name) throws ReflectiveOperationException {
        var f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);
    }
    private static Object call(Object target,String name,Class<?>[] types,Object... args) throws ReflectiveOperationException {
        var m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);
    }
    private Mob mob(String tag) {
        return getServer().getWorlds().stream().flatMap(world->world.getEntities().stream())
                .filter(entity->entity.getScoreboardTags().contains(tag)).map(Mob.class::cast).findFirst().orElseThrow();
    }
    private EntityTracker tracker(Mob mob) {
        return BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:species"),PersistentDataType.STRING));
    }
    private Map<?,?> bridgeCache() throws ReflectiveOperationException {
        var bridge=getServer().getPluginManager().getPlugin("GeyserModelEngine");
        Object manager=bridge.getClass().getMethod("getModelManager").invoke(bridge);
        return (Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);
    }
    private Object bridgeData(Mob mob) throws ReflectiveOperationException {
        var entries=(Map<?,?>)bridgeCache().get(mob.getEntityId());
        if(entries!=null)for(Object data:entries.values())
            if(data.getClass().getMethod("getEntityTracker").invoke(data)==tracker(mob))return data;
        throw new IllegalStateException("No current Bridge actor for "+mob.getUniqueId());
    }
    private int actor(Mob mob) throws ReflectiveOperationException {
        var data=bridgeData(mob);Object entity=data.getClass().getMethod("getEntity").invoke(data);
        return (int)entity.getClass().getMethod("getEntityId").invoke(entity);
    }
    private String state(Mob mob) throws ReflectiveOperationException {
        var tracker=tracker(mob);var old=previous.get(mob.getUniqueId());
        int active=0,stale=0;var entries=(Map<?,?>)bridgeCache().get(mob.getEntityId());
        if(entries!=null)for(var data:entries.values()) {
            var tracked=(EntityTracker)data.getClass().getMethod("getEntityTracker").invoke(data);
            if(tracked==tracker&&!tracked.isClosed()&&!tracked.forRemoval())active++;else stale++;
        }
        var adapter=field(field(plugin(),"models"),"adapter");
        Object seat=((Map<?,?>)field(adapter,"extraSeats")).get(mob.getEntityId());
        return "id="+mob.getEntityId()+";uuid="+mob.getUniqueId()+";tracker="+System.identityHashCode(tracker)
                +";oldClosed="+(old!=null&&old.isClosed())+";replaced="+(old!=null&&old!=tracker)
                +";actor="+actor(mob)+";oldActor="+previousActors.getOrDefault(mob.getUniqueId(),-1)
                +";active="+active+";stale="+stale+";scale="+mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue()
                +";render="+tracker.scaler().scale(tracker)+";time="+System.currentTimeMillis()+";adultAt="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:adult-at"),PersistentDataType.LONG)+";original="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:model-original-scale"),PersistentDataType.DOUBLE)+";extra="+(seat==null?"none":seat)
                +";trackers="+BetterModel.registry(mob.getUniqueId()).orElseThrow().trackers().size()
                +";driver="+call(plugin(),"driver",new Class<?>[]{Mob.class},mob)
                +";x="+mob.getX()+";y="+mob.getY()+";z="+mob.getZ()+";yaw="+mob.getYaw();
    }
    @SuppressWarnings("unchecked")
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        try {
            switch(args[0]) {
                case "view-distance" -> {
                    int distance=Integer.parseInt(args[1]);if(distance<2||distance>32)throw new IllegalArgumentException("View distance");
                    getServer().getWorlds().forEach(world->world.setViewDistance(distance));
                    sender.sendMessage(getServer().getWorlds().stream().map(world->world.getName()+":view="+world.getViewDistance()+",simulation="+world.getSimulationDistance()).toList().toString());
                }
                case "registrations" -> {
                    var rows=new ArrayList<String>();
                    for(String name:new String[]{"trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus","mosasaurus"})
                        rows.add(name+":"+BetterModel.model("ark_"+name).isPresent()+":"+org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().containsKey(org.geysermc.geyser.api.util.Identifier.of("modelengine:ark_"+name)));
                    int eggs=0,bedrockEggs=0;
                    for(String name:new String[]{"trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus"}) {
                        if(BetterModel.model("ark_egg_"+name).isPresent())eggs++;
                        if(org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().containsKey(org.geysermc.geyser.api.util.Identifier.of("modelengine:ark_egg_"+name)))bedrockEggs++;
                    }
                    sender.sendMessage(String.join(";",rows)+";eggs="+eggs+";bedrockEggs="+bedrockEggs+";protocols="+org.geysermc.geyser.registry.Registries.ITEMS.get().size()+";packs="+org.geysermc.geyser.registry.Registries.RESOURCE_PACKS.get().size());
                }
                case "protections" -> {
                    var rows=new ArrayList<String>();
                    for(String name:new String[]{"WorldEdit","WorldGuard","GriefPrevention"}) {
                        var installed=getServer().getPluginManager().getPlugin(name);
                        rows.add(name+"="+(installed!=null&&installed.isEnabled()));
                    }
                    sender.sendMessage(String.join(";",rows));
                }
                case "ids" -> {
                    var version=PacketEvents.getAPI().getPlayerManager().getUser(getServer().getPlayerExact(args[1])).getClientVersion();
                    sender.sendMessage("passengers="+PacketType.Play.Server.SET_PASSENGERS.getId(version)+";sync="+PacketType.Play.Server.ENTITY_POSITION_SYNC.getId(version)+";pluginMessage="+PacketType.Play.Client.PLUGIN_MESSAGE.getId(version));
                }
                case "inspect" -> sender.sendMessage(state(mob(args[1])));
                case "snapshot" -> {
                    var mob=mob(args[1]);previous.put(mob.getUniqueId(),tracker(mob));previousActors.put(mob.getUniqueId(),actor(mob));sender.sendMessage(state(mob));
                }
                case "share" -> call(plugin(),"publicRide",new Class<?>[]{Mob.class,boolean.class},mob(args[1]),true);
                case "attack" -> call(plugin(),"attackAnimation",new Class<?>[]{Mob.class,boolean.class},mob(args[1]),false);
                case "phase" -> call(plugin(),"animation",new Class<?>[]{Mob.class,String.class},mob(args[1]),args[2]);
                case "cancel-attack" -> tracker(mob(args[1])).stopAnimation("attack");
                case "animation-state" -> {
                    var tracker=tracker(mob(args[1]));var states=new ArrayList<String>();
                    for(String name:new String[]{"neck","head","jaw"}){var bone=tracker.bone(name);states.add(name+"="+bone.runningAnimation()+",rotation="+bone.worldRotation());}
                    sender.sendMessage(String.join(";",states));
                }
                case "attach" -> {
                    var mob=mob(args[1]);call(field(plugin(),"models"),"attach",new Class<?>[]{Mob.class,String.class},mob,"ark_"+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:species"),PersistentDataType.STRING));
                }
                case "unload", "native" -> {
                    var mob=mob(args[1]);if(args[0].equals("unload"))call(field(plugin(),"models"),"unload",new Class<?>[]{Entity.class},mob);
                    sender.sendMessage("scale="+mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue()+";time="+System.currentTimeMillis()
                            +";adultAt="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:adult-at"),PersistentDataType.LONG));
                }
                case "age" -> {
                    var mob=mob(args[1]);mob.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:adult-at"),PersistentDataType.LONG,System.currentTimeMillis()+Long.parseLong(args[2]));
                    call(field(plugin(),"models"),"growth",new Class<?>[]{Mob.class},mob);
                }
                case "player" -> {
                    var p=getServer().getPlayerExact(args[1]);var vehicle=p.getVehicle();
                    sender.sendMessage("id="+p.getEntityId()+";vehicle="+(vehicle==null?-1:vehicle.getEntityId())+";type="+(vehicle==null?"none":vehicle.getType())
                            +";uuid="+p.getUniqueId()+";x="+p.getX()+";y="+p.getY()+";z="+p.getZ()+";yaw="+p.getYaw());
                }
                case "bridge" -> {
                    var mob=mob(args[1]);var data=bridgeData(mob);var player=getServer().getPlayerExact(args[2]);
                    var set=(Set<Player>)data.getClass().getMethod("getViewers").invoke(data);viewers.put(player,set);set.add(player);
                    var entity=data.getClass().getMethod("getEntity").invoke(data);entity.getClass().getMethod("sendSpawnPacket",Collection.class).invoke(entity,List.of(player));
                    sender.sendMessage("actor="+actor(mob));
                }
                case "bridge-off" -> {viewers.forEach((player,set)->set.remove(player));viewers.clear();}
                case "reloadmodel" -> {viewers.clear();getServer().dispatchCommand(getServer().getConsoleSender(),"bettermodel reload");}
                default -> throw new IllegalArgumentException(args[0]);
            }
        } catch(Throwable error) {error.printStackTrace();sender.sendMessage("ERROR "+error);}
        return true;
    }
}
