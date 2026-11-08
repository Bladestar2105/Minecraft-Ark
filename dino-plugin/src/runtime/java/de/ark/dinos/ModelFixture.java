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
    private final Map<UUID, org.bukkit.util.Vector> motion = new HashMap<>();
    private final List<String> startup = new ArrayList<>();
    private final Set<Integer> traceEntities = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final List<String> movementPackets = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<Integer,List<Integer>> animationMasks = new java.util.concurrent.ConcurrentHashMap<>();
    private Object bridgeCodec;
    private Set<Player> bridgeViewers;
    private Player bridgeViewer;
    private int syncPacketId;
    private int relativePacketId;
    private int relativeRotationPacketId;
    private final Map<Integer, String> packets = new java.util.concurrent.ConcurrentHashMap<>();
    private String clicked = "none";
    private final Map<Integer, Byte> flags = new java.util.concurrent.ConcurrentHashMap<>();
    private int passengerPacketId;
    private PacketListenerAbstract observer;
    @Override public void onEnable() {
        models = new DinoModels(this);
        try {
            var utils=Arrays.stream(getServer().getPluginManager().getPlugins()).filter(p->p.getClass().getName().equals("me.zimzaza4.geyserutils.spigot.GeyserUtils")).findFirst().orElseThrow();
            bridgeCodec=utils.getClass().getMethod("getPacketManager").invoke(null);
        } catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        Mob early=(Mob)getServer().getWorlds().getFirst().spawnEntity(getServer().getWorlds().getFirst().getSpawnLocation().add(0,2,0),EntityType.RAVAGER);
        early.setAI(false);early.setGravity(false);early.setRemoveWhenFarAway(false);mobs.put("early",early);recordStartup();models.attach(early,"ark_trex");recordStartup();
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (startup.size() < 60) recordStartup();
            if(bridgeViewers!=null)bridgeViewers.add(bridgeViewer);
            for (Mob mob : mobs.values()) {
                var velocity = motion.get(mob.getUniqueId());
                if (velocity != null) mob.setVelocity(velocity);
            }
        }, 1, 1);
        getServer().getPluginManager().registerEvents(this, this);
        observer = new PacketListenerAbstract(PacketListenerPriority.MONITOR) {
            @Override public void onPacketSend(PacketSendEvent event) {
                if(event.getPacketType()==PacketType.Play.Server.PLUGIN_MESSAGE){
                    var packet=new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage(event);
                    if(packet.getChannelName().equals("geyserutils:main"))try{
                        recordProperties(bridgeCodec.getClass().getMethod("decodePacket",byte[].class).invoke(bridgeCodec,(Object)packet.getData()));
                    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                }
                if(event.getPacketType()==PacketType.Play.Server.ENTITY_POSITION_SYNC){
                    syncPacketId=event.getPacketId();
                    var packet=new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync(event);
                    if(traceEntities.contains(packet.getId())){var pos=packet.getValues().getPosition();movementPackets.add(System.nanoTime()+",sync,"+packet.getId()+","+pos.getX()+","+pos.getY()+","+pos.getZ()+","+packet.getValues().getYaw());}
                }
                if(event.getPacketType()==PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION){
                    relativeRotationPacketId=event.getPacketId();
                    var packet=new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation(event);
                    if(traceEntities.contains(packet.getEntityId()))movementPackets.add(System.nanoTime()+",relative,"+packet.getEntityId()+","+packet.getDeltaX()+","+packet.getDeltaY()+","+packet.getDeltaZ()+","+packet.getYaw());
                }
                if(event.getPacketType()==PacketType.Play.Server.ENTITY_RELATIVE_MOVE){
                    relativePacketId=event.getPacketId();
                    var packet=new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove(event);
                    if(traceEntities.contains(packet.getEntityId()))movementPackets.add(System.nanoTime()+",relative,"+packet.getEntityId()+","+packet.getDeltaX()+","+packet.getDeltaY()+","+packet.getDeltaZ()+",none");
                }
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
    private void recordProperties(Object packet) throws ReflectiveOperationException {
        if(packet.getClass().getSimpleName().equals("BundlePacket")){
            for(Object child:(List<?>)packet.getClass().getMethod("getPackets").invoke(packet))recordProperties(child);
        }else if(packet.getClass().getSimpleName().equals("EntityPropertyPacket")
                &&packet.getClass().getMethod("getIdentifier").invoke(packet).equals("modelengine:anim0")){
            int id=(int)packet.getClass().getMethod("getEntityId").invoke(packet);
            animationMasks.computeIfAbsent(id,ignored->new java.util.concurrent.CopyOnWriteArrayList<>()).add((int)packet.getClass().getMethod("getValue").invoke(packet));
        }
    }
    private void recordStartup() {
        Mob mob = mobs.get("early");
        startup.add("tick="+getServer().getCurrentTick()+",valid="+mob.isValid()+",dead="+mob.isDead()
                +",health="+mob.getHealth()+",scale="+mob.getAttribute(Attribute.SCALE).getBaseValue()
                +",original="+mob.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this,"model-original-scale"),org.bukkit.persistence.PersistentDataType.DOUBLE));
    }
    private String seat(Mob mob) throws ReflectiveOperationException {
        var field=DinoModels.class.getDeclaredField("adapter");field.setAccessible(true);Object adapter=field.get(models);
        field=adapter.getClass().getDeclaredField("bedrockSeats");field.setAccessible(true);Object seats=field.get(adapter);
        field=seats.getClass().getDeclaredField("seats");field.setAccessible(true);Object bridge=field.get(seats);
        if(bridge==null)return "none";
        field=bridge.getClass().getDeclaredField("carriers");field.setAccessible(true);
        return Objects.toString(((Map<?,?>)field.get(bridge)).get(mob.getUniqueId()),"none");
    }
    @SuppressWarnings("unchecked")
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            String op = args[0], name = args.length > 1 ? args[1] : "";
            Mob mob = mobs.get(name);
            switch (op) {
                case "name-state" -> {
                    if(args.length>2)mob.customName(args[2].equals("null")?null:net.kyori.adventure.text.Component.text(args[2]));
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);
                    var field=DinoModels.class.getDeclaredField("adapter");field.setAccessible(true);Object adapter=field.get(models);
                    field=adapter.getClass().getDeclaredField("models");field.setAccessible(true);Object model=((Map<?,?>)field.get(adapter)).get(mob.getUniqueId());
                    field=model.getClass().getDeclaredField("label");field.setAccessible(true);TextDisplay display=(TextDisplay)field.get(model);
                    boolean live=display!=null&&display.isValid();
                    sender.sendMessage("name="+mob.customName()+";visible="+mob.isCustomNameVisible()+";labels="+(live?1:0)+";label="+(live?display.getEntityId():-1)
                            +";position="+(live?display.getX()+","+display.getY()+","+display.getZ():"none")+";text="+(live?display.text():"none")
                            +";persistent="+(live&&display.isPersistent())+";bmLabels="+tracker.bones().stream().filter(bone->bone.getNametag()!=null).count()+";native="+mob.getEntityId());return true;
                }
                case "bridge-race" -> {sender.sendMessage(BridgeRaceProbe.begin(mob,getServer().getPlayerExact(args[2])));return true;}
                case "bridge-race-replace" -> {sender.sendMessage(BridgeRaceProbe.replace());return true;}
                case "bridge-race-finish" -> {BridgeRaceProbe.finish();sender.sendMessage("finished=true");return true;}
                case "egg-mappings" -> {
                    try (var resource=getResource("egg-assets.properties");var zip=new java.util.zip.ZipFile(new java.io.File("plugins/BetterModel/build.zip"))) {
                        var expected=EggAssets.read(resource);
                        for(var asset:expected.entrySet()) {
                            var entry=zip.getEntry(asset.getKey());
                            if(entry==null)throw new AssertionError("Missing compiled Java egg asset "+asset.getKey());
                            try(var input=zip.getInputStream(entry)) {
                                if(!Arrays.equals(asset.getValue(),input.readAllBytes()))throw new AssertionError("Changed compiled egg asset "+asset.getKey());
                            }
                        }
                    }
                    var versions=org.geysermc.geyser.registry.Registries.ITEMS.get();
                    var items=new TreeSet<String>();
                    for(var version:versions.values()) {
                        var definitions=version.getMapping("minecraft:sniffer_egg").getCustomItemDefinitions();
                        int found=0;
                        for(var entry:definitions.entries()) {
                            var definition=entry.getValue().definition();
                            if(!definition.model().toString().startsWith("arkdinos:egg_"))continue;
                            if(!definition.model().equals(definition.bedrockIdentifier()))throw new AssertionError("Egg model/Bedrock identifier mismatch");
                            String icon=definition.icon();
                            if(!entry.getKey().asString().equals(definition.model().toString())||!icon.equals(definition.model().toString().replace(':','.')))throw new AssertionError("Egg selection/icon mismatch");
                            if(!entry.getValue().itemDefinition().getComponentData().toString().contains(icon))throw new AssertionError("Bedrock runtime item lacks egg icon");
                            items.add(definition.model().toString());found++;
                        }
                        if(found!=8)throw new AssertionError("Expected eight eggs per Bedrock protocol, found "+found);
                    }
                    boolean eggPack=org.geysermc.geyser.registry.Registries.RESOURCE_PACKS.get().containsKey(UUID.fromString("4b6d5dd8-9250-48cf-bc87-48251cfaf744"));
                    int entities=0;
                    for(String species:new String[]{"trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus","mosasaurus"}) {
                        if(org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().containsKey(org.geysermc.geyser.api.util.Identifier.of("modelengine:ark_"+species)))entities++;
                    }
                    sender.sendMessage("javaAssets=24;eggPack="+eggPack+";bedrockDefinitions="+items.size()+";protocols="+versions.size()+";modelEntities="+entities+";items="+String.join(",",items));return true;
                }
                case "client-ids" -> {
                    var version=PacketEvents.getAPI().getPlayerManager().getUser(getServer().getPlayerExact(name)).getClientVersion();
                    sender.sendMessage("pluginMessage="+PacketType.Play.Client.PLUGIN_MESSAGE.getId(version)+";pig="+com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.PIG.getId(version)+";ravager="+com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.RAVAGER.getId(version)+";sniffer="+com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.SNIFFER.getId(version)+";dolphin="+com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.DOLPHIN.getId(version)+";textDisplay="+com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.TEXT_DISPLAY.getId(version)+";destroy="+PacketType.Play.Server.DESTROY_ENTITIES.getId(version));return true;
                }
                case "pig" -> {mob=(Mob)getServer().getWorlds().getFirst().spawnEntity(new org.bukkit.Location(getServer().getWorlds().getFirst(),35,4,12),EntityType.PIG);mob.setAI(false);mob.setRemoveWhenFarAway(false);mobs.put(name,mob);}
                case "spawn" -> {
                    EntityType type = name.equals("trex") || name.equals("velociraptor") || name.equals("dilophosaurus") ? EntityType.RAVAGER : name.equals("mosasaurus") ? EntityType.DOLPHIN : EntityType.SNIFFER;
                    var location = getServer().getWorlds().getFirst().getSpawnLocation().clone();
                    location.set(args.length > 2 ? Double.parseDouble(args[2]) : 0, 4,
                            args.length > 3 ? Double.parseDouble(args[3]) : 0);
                    mob = (Mob) location.getWorld().spawnEntity(location, type);
                    mob.setAI(false); mob.setGravity(false); mob.setPersistent(true);mob.setRemoveWhenFarAway(false);
                    if (name.equals("mosasaurus")) {mob.getAttribute(Attribute.SCALE).setBaseValue(4);mob.setMaximumAir(20*60*15);mob.setRemainingAir(20*60*15);}
                    mob.addScoreboardTag("ark_dino");mob.addScoreboardTag("model_fixture_"+name);mob.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"species"),org.bukkit.persistence.PersistentDataType.STRING,name);
                    mobs.put(name, mob);
                    if (name.equals("baseline")) BetterModel.model("ark_trex").orElseThrow().create(kr.toxicity.model.api.bukkit.platform.BukkitAdapter.adapt(mob));
                    else if (!name.equals("missing")) models.attach(mob, "ark_" + name);
                    else models.attach(mob, "ark_missing");
                }
                case "attach" -> models.attach(mob, name.equals("early") ? "ark_trex" : "ark_" + name);
                case "scale" -> mob.getAttribute(Attribute.SCALE).setBaseValue(Double.parseDouble(args[2]));
                case "juvenile" -> {mob.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"adult-at"),org.bukkit.persistence.PersistentDataType.LONG,System.currentTimeMillis()+900_000);models.growth(mob);}
                case "age" -> {mob.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"adult-at"),org.bukkit.persistence.PersistentDataType.LONG,System.currentTimeMillis()+Long.parseLong(args[2]));models.growth(mob);}
                case "prepare-juvenile" -> {models.unload(mob);mob.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(this,"adult-at"),org.bukkit.persistence.PersistentDataType.LONG,System.currentTimeMillis()+900_000);mob.getAttribute(Attribute.SCALE).setBaseValue(.45);}
                case "adult" -> {mob.getPersistentDataContainer().remove(new org.bukkit.NamespacedKey(this,"adult-at"));models.growth(mob);}
                case "mount" -> { var player=getServer().getPlayerExact(args[2]);player.teleport(mob.getLocation().add(0,0,2));mob.addPassenger(player); }
                case "dismount" -> mob.eject();
                case "unload" -> models.unload(mob);
                case "phase" -> models.animation(mob, args[2]);
                case "attack" -> models.attack(mob,args.length>2&&args[2].equals("spit"));
                case "startup" -> { sender.sendMessage(startup.toString());return true; }
                case "bridge-state" -> {
                    var bridge=getServer().getPluginManager().getPlugin("GeyserModelEngine");
                    var manager=bridge.getClass().getMethod("getModelManager").invoke(bridge);
                    var cache=(Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);
                    var entries=(Map<?,?>)cache.get(mob.getEntityId());
                    var registry=BetterModel.registry(mob.getUniqueId()).orElse(null);
                    var current=registry==null?null:registry.tracker("ark_"+name);
                    int active=0,stale=0;
                    if(entries!=null)for(var data:entries.values()){
                        var tracked=(kr.toxicity.model.api.tracker.EntityTracker)data.getClass().getMethod("getEntityTracker").invoke(data);
                        if(!tracked.name().equals("ark_"+name))continue;
                        if(tracked==current&&!tracked.isClosed()&&!tracked.forRemoval())active++;else stale++;
                    }
                    sender.sendMessage("active="+active+";stale="+stale);return true;
                }
                case "bridge-viewer" -> {
                    var bridge=getServer().getPluginManager().getPlugin("GeyserModelEngine");
                    var manager=bridge.getClass().getMethod("getModelManager").invoke(bridge);
                    var cache=(Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);
                    String modelName=args.length>3&&!args[3].equals("first")?args[3]:"ark_"+name;
                    Object data=null;
                    for(Object candidate:((Map<?,?>)cache.get(mob.getEntityId())).values())if(((kr.toxicity.model.api.tracker.EntityTracker)candidate.getClass().getMethod("getEntityTracker").invoke(candidate)).name().equals(modelName)){data=candidate;break;}
                    if(data==null)throw new IllegalStateException("No GME actor for "+modelName);
                    bridgeViewers=(Set<Player>)data.getClass().getMethod("getViewers").invoke(data);bridgeViewer=getServer().getPlayerExact(args[2]);bridgeViewers.add(bridgeViewer);
                    var entity=data.getClass().getMethod("getEntity").invoke(data);int id=(int)entity.getClass().getMethod("getEntityId").invoke(entity);
                    if(args.length>3&&args[3].equals("first")){
                        var field=DinoModels.class.getDeclaredField("adapter");field.setAccessible(true);var adapter=field.get(models);
                        field=adapter.getClass().getDeclaredField("visualCarriers");field.setAccessible(true);((Map<?,?>)field.get(adapter)).remove(id);
                    }
                    traceEntities.clear();traceEntities.add(id);traceEntities.add(mob.getEntityId());movementPackets.clear();animationMasks.clear();
                    entity.getClass().getMethod("sendSpawnPacket",Collection.class).invoke(entity,List.of(bridgeViewer));
                    var tracked=(kr.toxicity.model.api.tracker.EntityTracker)data.getClass().getMethod("getEntityTracker").invoke(data);
                    sender.sendMessage("bridge="+id+";native="+mob.getEntityId()+";sameTracker="+(tracked==BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker(modelName))+";bridgeClosed="+tracked.isClosed());return true;
                }
                case "movement-packets" -> {sender.sendMessage(String.join("|",movementPackets));return true;}
                case "animation-masks" -> {sender.sendMessage(animationMasks.getOrDefault(Integer.parseInt(args[2]),List.of()).toString());return true;}
                case "bridge-off" -> {if(bridgeViewers!=null)bridgeViewers.remove(bridgeViewer);bridgeViewers=null;bridgeViewer=null;}
                case "movement-id" -> {sender.sendMessage("sync="+syncPacketId+";relative="+relativePacketId+";relativeRotation="+relativeRotationPacketId);return true;}
                case "pause-sampler", "visual-count" -> {
                    var field=DinoModels.class.getDeclaredField("adapter");field.setAccessible(true);var adapter=field.get(models);
                    if(op.equals("pause-sampler")){field=adapter.getClass().getDeclaredField("animationTask");field.setAccessible(true);((org.bukkit.scheduler.BukkitTask)field.get(adapter)).cancel();}
                    else{field=adapter.getClass().getDeclaredField("visualCarriers");field.setAccessible(true);int carrierId=mob.getEntityId();long count=((Map<?,?>)field.get(adapter)).values().stream().filter(value->value.equals(carrierId)).count();sender.sendMessage("visuals="+count);return true;}
                }
                case "rotation" -> mob.setRotation(Float.parseFloat(args[2]),0);
                case "motion", "swim-motion" -> {
                    mob.setAI(true); mob.setAware(false);
                    mob.setGravity(!op.equals("swim-motion"));
                    motion.put(mob.getUniqueId(), new org.bukkit.util.Vector(Double.parseDouble(args[2]),
                            Double.parseDouble(args[3]), Double.parseDouble(args[4])));
                }
                case "stopmotion" -> { motion.remove(mob.getUniqueId()); mob.setVelocity(new org.bukkit.util.Vector()); if(name.equals("mosasaurus"))mob.setAI(false); }
                case "position" -> {
                    var location = mob.getLocation();
                    location.set(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]));
                    mob.teleport(location);
                }
                case "detail-state" -> {
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);
                    var rows=new ArrayList<String>();
                    for(String boneName:args[2].split(",")) {
                        var bone=tracker.bone(boneName);var field=bone.getClass().getDeclaredField("globalState");field.setAccessible(true);var state=field.get(bone);
                        field=state.getClass().getDeclaredField("after");field.setAccessible(true);Object frame=field.get(state);Object scale=frame.getClass().getMethod("scale").invoke(frame);
                        rows.add(boneName+":"+scale.getClass().getMethod("x").invoke(scale)+":"+Objects.toString(bone.runningAnimation()==null?null:bone.runningAnimation().name(),"none"));
                    }
                    sender.sendMessage(String.join("|",rows));return true;
                }
                case "rotation-state" -> {
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);var rows=new ArrayList<String>();
                    for(String boneName:args[2].split(",")) {
                        var bone=tracker.bone(boneName);Object rotation=bone.getClass().getMethod("worldRotation").invoke(bone);
                        rows.add(boneName+":"+rotation.getClass().getMethod("x").invoke(rotation)+","+rotation.getClass().getMethod("y").invoke(rotation)+","+rotation.getClass().getMethod("z").invoke(rotation));
                    }
                    sender.sendMessage(String.join("|",rows));return true;
                }
                case "gaitpose" -> {
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);
                    var poses=new TreeMap<String,String>();
                    poses.put("timeSeconds",Double.toString(System.nanoTime()/1_000_000_000.));
                    for(var bone:tracker.bones())if(bone.name().name().startsWith("leg_")){
                        Object rotation=bone.getClass().getMethod("worldRotation").invoke(bone);
                        poses.put(bone.name().name(),String.valueOf(rotation.getClass().getMethod("x").invoke(rotation)));
                    }
                    sender.sendMessage(poses.entrySet().stream().map(entry->entry.getKey()+"="+entry.getValue()).collect(java.util.stream.Collectors.joining(";")));
                    return true;
                }
                case "repeat", "gait", "token" -> {
                    var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_"+name);
                    var bone=tracker.bones().stream().filter(value->op.equals("token")?value.name().name().equals("body"):value.runningAnimation()!=null&&value.runningAnimation().name().equals(args[2])).findFirst().orElseThrow();
                    var field=bone.getClass().getDeclaredField("globalState");field.setAccessible(true);var global=field.get(bone);
                    field=global.getClass().getDeclaredField("state");field.setAccessible(true);var state=field.get(global);
                    field=state.getClass().getDeclaredField("animators");field.setAccessible(true);var animators=field.get(state);
                    var getter=animators.getClass().getMethod("get",Comparable.class);
                    var before=getter.invoke(animators,args[2]);
                    if(op.equals("token")){sender.sendMessage("token="+System.identityHashCode(before));return true;}
                    if(op.equals("gait")){
                        field=before.getClass().getDeclaredField("modifier");field.setAccessible(true);
                        sender.sendMessage("speed="+((kr.toxicity.model.api.animation.AnimationModifier)field.get(before)).speedValue());return true;
                    }
                    models.animation(mob,args[2]);
                    sender.sendMessage("same="+(before==getter.invoke(animators,args[2])));return true;
                }
                case "restore-saved" -> {
                    for(var world:getServer().getWorlds())for(var entity:world.getEntities())if(entity instanceof Mob saved) {
                        String species=saved.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this,"species"),org.bukkit.persistence.PersistentDataType.STRING);
                        if(species!=null && BetterModel.model("ark_"+species).isPresent() && saved.getScoreboardTags().contains("model_fixture_"+species)) { mobs.put(species,saved);models.attach(saved,"ark_"+species); }
                    }
                    sender.sendMessage("restored="+mobs.keySet());return true;
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
                sender.sendMessage("id="+mob.getEntityId()+";uuid="+mob.getUniqueId()+";valid="+mob.isValid()+";dead="+mob.isDead()+";health="+mob.getHealth()+";eyeY="+mob.getEyeHeight()+";x="+mob.getX()+";y="+mob.getY()+";z="+mob.getZ()+";ground="+mob.isOnGround()+";water="+mob.isInWater()+";pitch="+mob.getPitch()+";modelPitch="+(tracker==null?"none":tracker.rotation().x())+";original="+Objects.toString(mob.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(this,"model-original-scale"),org.bukkit.persistence.PersistentDataType.DOUBLE),"none")+";seat="+seat(mob)+";scale="+mob.getAttribute(Attribute.SCALE).getBaseValue()+";width="+box.getWidthX()+";height="+box.getHeight()+";riderY="+rider+";riderBox="+(mob.getPassengers().isEmpty()?"none":mob.getPassengers().getFirst().getBoundingBox().toString())+";riderX="+(mob.getPassengers().isEmpty()?"none":mob.getPassengers().getFirst().getX()-mob.getX())+";riderZ="+(mob.getPassengers().isEmpty()?"none":mob.getPassengers().getFirst().getZ()-mob.getZ())+";trackers="+(registry==null?0:registry.trackers().size())+";render="+(tracker==null?"none":tracker.scaler().scale(tracker))+";flags="+flags.get(mob.getEntityId())+";running="+(tracker==null?"none":tracker.bones().stream().map(bone->String.valueOf(bone.runningAnimation())).distinct().sorted().toList())+";displays="+(tracker==null?0:tracker.displays().count())+";packetId="+passengerPacketId+";packet="+packets.get(mob.getEntityId())+";clicked="+clicked);
            }
        } catch (Throwable error) { error.printStackTrace(); sender.sendMessage("ERROR "+error); }
        return true;
    }
}
