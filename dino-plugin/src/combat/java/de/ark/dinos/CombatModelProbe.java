package de.ark.dinos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Records actual Java transforms and GME property packets after ordinary mounted attack input. */
final class CombatModelProbe implements AutoCloseable {
    private final JavaPlugin plugin;
    private final Object codec;
    private final Set<Integer> displays=ConcurrentHashMap.newKeySet();
    private final List<String> javaTransforms=new CopyOnWriteArrayList<>();
    private final Map<Integer,List<Integer>> masks=new ConcurrentHashMap<>();
    private final PacketListenerAbstract observer;
    private Set<Player> viewers;
    private Player viewer;
    private int actor;
    private final org.bukkit.scheduler.BukkitTask task;

    CombatModelProbe(JavaPlugin plugin) throws ReflectiveOperationException {
        this.plugin=plugin;
        var utils=Arrays.stream(plugin.getServer().getPluginManager().getPlugins())
                .filter(p->p.getClass().getName().equals("me.zimzaza4.geyserutils.spigot.GeyserUtils")).findFirst().orElseThrow();
        codec=utils.getClass().getMethod("getPacketManager").invoke(null);
        observer=new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override public void onPacketSend(PacketSendEvent event) {
                if(event.getPacketType()==PacketType.Play.Server.ENTITY_METADATA) {
                    var packet=new WrapperPlayServerEntityMetadata(event);
                    if(displays.contains(packet.getEntityId()))for(var data:packet.getEntityMetadata())
                        if(data.getIndex()>=11&&data.getIndex()<=14)javaTransforms.add(packet.getEntityId()+":"+data.getIndex()+":"+data.getValue());
                } else if(event.getPacketType()==PacketType.Play.Server.PLUGIN_MESSAGE) {
                    var packet=new WrapperPlayServerPluginMessage(event);
                    if(packet.getChannelName().equals("geyserutils:main"))try {
                        properties(codec.getClass().getMethod("decodePacket",byte[].class).invoke(codec,(Object)packet.getData()));
                    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(observer);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,()->{if(viewers!=null)viewers.add(viewer);},1,1);
    }

    private void properties(Object packet) throws ReflectiveOperationException {
        if(packet.getClass().getSimpleName().equals("BundlePacket")) {
            for(Object child:(List<?>)packet.getClass().getMethod("getPackets").invoke(packet))properties(child);
        } else if(packet.getClass().getSimpleName().equals("EntityPropertyPacket")
                &&packet.getClass().getMethod("getIdentifier").invoke(packet).equals("modelengine:anim0")) {
            int id=(int)packet.getClass().getMethod("getEntityId").invoke(packet);
            masks.computeIfAbsent(id,ignored->new CopyOnWriteArrayList<>()).add((int)packet.getClass().getMethod("getValue").invoke(packet));
        }
    }

    private EntityTracker tracker(Mob mob) {
        var registry=BetterModel.registry(mob.getUniqueId()).orElse(null);
        return registry==null?null:registry.tracker("ark_"+mob.getPersistentDataContainer().get(org.bukkit.NamespacedKey.fromString("arkdinos:species"),org.bukkit.persistence.PersistentDataType.STRING));
    }

    @SuppressWarnings("unchecked")
    void trace(Mob mob,Player player) throws ReflectiveOperationException {
        if(viewers!=null)viewers.remove(viewer);
        var tracker=tracker(mob);
        var bridge=plugin.getServer().getPluginManager().getPlugin("GeyserModelEngine");
        Object manager=bridge.getClass().getMethod("getModelManager").invoke(bridge);
        var cache=(Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);
        Object data=((Map<?,?>)cache.get(mob.getEntityId())).values().stream().filter(candidate->{
            try{return candidate.getClass().getMethod("getEntityTracker").invoke(candidate)==tracker;}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }).findFirst().orElseThrow();
        viewers=(Set<Player>)data.getClass().getMethod("getViewers").invoke(data);viewer=player;viewers.add(player);
        Object entity=data.getClass().getMethod("getEntity").invoke(data);actor=(int)entity.getClass().getMethod("getEntityId").invoke(entity);
        entity.getClass().getMethod("sendSpawnPacket",Collection.class).invoke(entity,List.of(player));
        displays.clear();tracker.displays().forEach(display->displays.add(display.id()));javaTransforms.clear();masks.clear();
    }

    String state(Mob mob) {
        var tracker=tracker(mob);
        var mouth=tracker==null?null:tracker.bone("jaw");
        if(mouth==null&&tracker!=null)mouth=tracker.bone("head");
        var position=mouth==null?new org.joml.Vector3f():mouth.worldPosition();
        return "trackers="+(tracker==null?0:1)+";running="+(tracker==null?"none":tracker.bones().stream().map(b->String.valueOf(b.runningAnimation())).distinct().sorted().toList())
                +";rotations="+(tracker==null?"none":tracker.bones().stream().map(b->b.name()+":"+b.worldRotation()).toList())
                +";javaTransforms="+javaTransforms.size()+";masks="+masks.getOrDefault(actor,List.of())+";swingPacket="
                +PacketType.Play.Client.PUNCH.getId(com.github.retrooper.packetevents.protocol.player.ClientVersion.V_26_3)
                +";attackPacket="+PacketType.Play.Client.ATTACK.getId(com.github.retrooper.packetevents.protocol.player.ClientVersion.V_26_3)
                +";pluginMessagePacket="+PacketType.Play.Client.PLUGIN_MESSAGE.getId(com.github.retrooper.packetevents.protocol.player.ClientVersion.V_26_3)
                +";mouthX="+(mob.getX()+position.x)+";mouthY="+(mob.getY()+position.y)+";mouthZ="+(mob.getZ()+position.z);
    }

    String proxy(Mob mob,Player player) {
        var contact=mob.getWorld().getEntities().stream().filter(entity->entity instanceof kr.toxicity.model.api.nms.HitBox hit
                &&entity.getType()==org.bukkit.entity.EntityType.INTERACTION&&hit.source().uuid().equals(mob.getUniqueId()))
                .max(Comparator.comparingDouble(org.bukkit.entity.Entity::getZ)).orElseThrow();
        var look=player.getLocation();look.setDirection(contact.getBoundingBox().getCenter().subtract(player.getEyeLocation().toVector()));
        return "id="+contact.getEntityId()+";source="+mob.getUniqueId()+";x="+contact.getX()+";y="+contact.getY()+";z="+contact.getZ()
                +";yaw="+look.getYaw()+";pitch="+look.getPitch()+";bounds="+contact.getBoundingBox();
    }

    @Override public void close() {
        task.cancel();if(viewers!=null)viewers.remove(viewer);
        PacketEvents.getAPI().getEventManager().unregisterListener(observer);
    }
}
