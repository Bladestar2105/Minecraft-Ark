package de.ark.dinos;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import kr.toxicity.model.api.BetterModel;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import java.util.*;

public final class ModelFixture extends JavaPlugin implements Listener {
    private DinoModels models;
    private final Map<String, Mob> mobs = new HashMap<>();
    private final Map<Integer, String> packets = new java.util.concurrent.ConcurrentHashMap<>();
    private String clicked = "none";
    private final Map<Integer, Byte> flags = new java.util.concurrent.ConcurrentHashMap<>();
    private int passengerPacketId;
    private PacketListenerAbstract observer;
    @Override public void onEnable() {
        models = new DinoModels(this);
        Mob early=(Mob)getServer().getWorlds().getFirst().spawnEntity(getServer().getWorlds().getFirst().getSpawnLocation().add(0,2,0),EntityType.RAVAGER);
        early.setAI(false);early.setGravity(false);mobs.put("early",early);models.attach(early,"ark_trex");
        getServer().getPluginManager().registerEvents(this, this);
        observer = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
                    var packet = new WrapperPlayServerEntityMetadata(event);
                    packet.getEntityMetadata().stream().filter(data->data.getIndex()==0&&data.getValue() instanceof Byte)
                        .forEach(data->flags.put(packet.getEntityId(),(Byte)data.getValue()));
                }
                if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
                    var packet = new WrapperPlayServerSetPassengers(event);
                    passengerPacketId = event.getPacketId();
                    packets.put(packet.getEntityId(), Arrays.toString(packet.getPassengers()));
                }
            }
        };
        PacketEvents.getAPI().getEventManager().registerListener(observer);
    }
    @Override public void onDisable() {
        models.close();
        PacketEvents.getAPI().getEventManager().unregisterListener(observer);
    }
    @EventHandler public void click(PlayerInteractEntityEvent event) { clicked = Integer.toString(event.getRightClicked().getEntityId()); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String op = args[0], name = args.length > 1 ? args[1] : "";
            Mob mob = mobs.get(name);
            switch (op) {
                case "spawn" -> {
                    EntityType type = name.equals("trex") || name.equals("velociraptor") ? EntityType.RAVAGER : EntityType.SNIFFER;
                    var location = getServer().getWorlds().getFirst().getSpawnLocation().clone();
                    location.set(args.length > 2 ? Double.parseDouble(args[2]) : 0, 4, 0);
                    mob = (Mob) location.getWorld().spawnEntity(location, type);
                    mob.setAI(false); mob.setGravity(false); mob.setPersistent(true);
                    mob.addScoreboardTag("ark_dino");mob.addScoreboardTag("model_fixture_"+name);mob.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"species"),org.bukkit.persistence.PersistentDataType.STRING,name);
                    mobs.put(name, mob);
                    if (name.equals("baseline")) BetterModel.model("ark_trex").orElseThrow().create(kr.toxicity.model.api.bukkit.platform.BukkitAdapter.adapt(mob));
                    else if (!name.equals("missing")) models.attach(mob, "ark_" + name);
                    else models.attach(mob, "ark_missing");
                }
                case "attach" -> models.attach(mob, "ark_" + name);
                case "scale" -> mob.getAttribute(Attribute.SCALE).setBaseValue(Double.parseDouble(args[2]));
                case "mount" -> { var player=getServer().getPlayerExact(args[2]);player.teleport(mob.getLocation().add(0,0,2));mob.addPassenger(player); }
                case "dismount" -> mob.eject();
                case "unload" -> models.unload(mob);
                case "phase" -> models.animation(mob, args[2]);
                case "repeat" -> {
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);
                    var bone=tracker.bones().stream().filter(value->value.runningAnimation()!=null&&value.runningAnimation().name().equals(args[2])).findFirst().orElseThrow();
                    var field=bone.getClass().getDeclaredField("globalState");field.setAccessible(true);var global=field.get(bone);
                    field=global.getClass().getDeclaredField("state");field.setAccessible(true);var state=field.get(global);
                    field=state.getClass().getDeclaredField("animators");field.setAccessible(true);var animators=field.get(state);
                    var getter=animators.getClass().getMethod("get",Comparable.class);
                    var before=getter.invoke(animators,args[2]);
                    models.animation(mob,args[2]);
                    sender.sendMessage("same="+(before==getter.invoke(animators,args[2])));return true;
                }
                case "reset" -> { models.close(); models = new DinoModels(this); }
                case "pipeline" -> {
                    Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(getServer().getPlayerExact(name));
                    var names = channel.getClass().getMethod("pipeline").invoke(channel);
                    sender.sendMessage(names.toString()); return true;
                }
                case "shared" -> BetterModel.model("shared_fixture").orElseThrow().create(kr.toxicity.model.api.bukkit.platform.BukkitAdapter.adapt(mob));
                case "reloadmodel" -> getServer().dispatchCommand(getServer().getConsoleSender(), "bettermodel reload");
                case "ray" -> {
                    var loc=mob.getLocation().add(0,Double.parseDouble(args[2]),3);
                    var hit=loc.getWorld().rayTraceEntities(loc,new org.bukkit.util.Vector(0,0,-1),4,entity->entity.equals(mobs.get(name)));
                    sender.sendMessage("ray="+(hit==null?"none":hit.getHitEntity().getEntityId()));return true;
                }
                case "baselineclose" -> BetterModel.registry(mob.getUniqueId()).ifPresent(registry->registry.close());
                case "disablemodel" -> getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("BetterModel"));
                case "inspect" -> { }
                default -> throw new IllegalArgumentException(op);
            }
            if (mob != null) {
                var registry=BetterModel.registry(mob.getUniqueId()).orElse(null);
                var tracker=registry == null ? null : registry.tracker(name.equals("early")?"ark_trex":"ark_"+name);
                String rider = mob.getPassengers().isEmpty() ? "none" : String.format(java.util.Locale.ROOT,"%.6f",mob.getPassengers().getFirst().getLocation().getY()-mob.getLocation().getY());
                var box=mob.getBoundingBox();
                sender.sendMessage("id="+mob.getEntityId()+";scale="+mob.getAttribute(Attribute.SCALE).getBaseValue()+";width="+box.getWidthX()+";height="+box.getHeight()+";riderY="+rider+";trackers="+(registry==null?0:registry.trackers().size())+";render="+(tracker==null?"none":tracker.scaler().scale(tracker))+";flags="+flags.get(mob.getEntityId())+";running="+(tracker==null?"none":tracker.bones().stream().map(bone->String.valueOf(bone.runningAnimation())).distinct().sorted().toList())+";displays="+(tracker==null?0:tracker.displays().count())+";packetId="+passengerPacketId+";packet="+packets.get(mob.getEntityId())+";clicked="+clicked);
            }
        } catch (Throwable error) { error.printStackTrace(); sender.sendMessage("ERROR "+error); }
        return true;
    }
}
