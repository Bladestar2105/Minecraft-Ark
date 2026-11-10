package de.ark.dinos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.event.PluginStartReloadEvent;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationProgress;
import kr.toxicity.model.api.animation.AnimationKeyframe;
import kr.toxicity.model.api.animation.TimedStorage;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimator;
import kr.toxicity.model.api.bone.BoneMovement;
import kr.toxicity.model.api.bone.BoneName;
import org.joml.Vector3f;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.animation.AnimationOverrideState;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.nms.HitBox;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BetterModelAdapter implements Listener, AutoCloseable {
    private static final AnimationModifier PHASE = AnimationModifier.builder()
            .type(AnimationIterator.Type.LOOP).priority(100).build();
    private static final AnimationModifier GROWTH = AnimationModifier.builder()
            .type(AnimationIterator.Type.LOOP).priority(300).override(true).build();
    private static final AnimationModifier ATTACK = AnimationModifier.builder()
            .type(AnimationIterator.Type.PLAY_ONCE).priority(200).override(true).end(2).build();
    private final JavaPlugin plugin;
    private final NamespacedKey originalScale;
    private final NamespacedKey adultAt;
    private final Map<UUID, Model> models = new HashMap<>();
    private final Map<UUID, Model> pending = new HashMap<>();
    private final ModelEventListener reload;
    private final ModelEventListener eggAssets;
    private final Map<Integer, String> modeledCarriers = new ConcurrentHashMap<>();
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();
    private final Set<Integer> riddenCarriers = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> visualCarriers = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Integer, CarrierPose>> synchronizedViewers = new ConcurrentHashMap<>();
    private final Map<?, ?> bridgeModels;
    private final Object bridgeManager;
    private final Set<String> warned = new HashSet<>();
    private final PacketListenerAbstract packets;
    private final BukkitTask animationTask;
    private final BedrockSeats bedrockSeats;

    private static final class CarrierPose {
        EntityPositionData nativePose;
        boolean onGround;
        boolean mirrored;
        long lastBridgePose;
    }

    private static final class Model {
        final Mob mob;
        final String name;
        String phase;
        boolean phaseScheduled;
        volatile float walkSpeed = 1;
        Location previousLocation;
        EntityTracker tracker;
        EntityTracker visualTracker;
        int visualId = -1;
        double growth = Double.NaN;
        String detailStage;
        volatile Map<BoneName, Vector3f> detailScales = Map.of();
        Component customName;
        TextDisplay label;
        Model(Mob mob, String name) {
            this.mob = mob;
            this.name = name;
            previousLocation = mob.getLocation();
        }
    }

    Entity resolveTarget(Entity entity) {
        if (!(entity instanceof HitBox hitBox)) return entity;
        Entity source = entity.getServer().getEntity(hitBox.source().uuid());
        return source == null ? entity : source;
    }

    BetterModelAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        originalScale = new NamespacedKey(plugin, "model-original-scale");
        adultAt = new NamespacedKey(plugin, "adult-at");
        bedrockSeats = new BedrockSeats(plugin);
        try {
            var bridge = plugin.getServer().getPluginManager().getPlugin("GeyserModelEngine");
            bridgeManager = bridge.getClass().getMethod("getModelManager").invoke(bridge);
            bridgeModels = (Map<?, ?>) bridgeManager.getClass().getMethod("getEntitiesCache").invoke(bridgeManager);
        } catch (ReflectiveOperationException error) {
            bedrockSeats.close();
            throw new IllegalStateException("Dinosaur movement bridge unavailable", error);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) players.add(player.getEntityId());
        packets = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketSend(PacketSendEvent event) {
                UUID viewer = event.getUser().getUUID();
                if (viewer == null) return;
                if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
                    var packet = new WrapperPlayServerSpawnEntity(event);
                    if (packet.getEntityType() == EntityTypes.PIG && bridgeCarrier(packet.getEntityId(), viewer) != null) {
                        // Geyser's Pig factory skips interpolation for client-predicted vehicles.
                        // Keep the custom definition/ID, with the same interpolation as our carriers.
                        packet.setEntityType(EntityTypes.RAVAGER);
                    }
                    CarrierPose pose = carrierPose(viewer, packet.getEntityId(), true);
                    if (pose != null) synchronized (pose) {
                        pose.nativePose = new EntityPositionData(packet.getPosition(), Vector3d.zero(), packet.getYaw(), packet.getPitch());
                        pose.mirrored = false;
                    }
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.DESTROY_ENTITIES) {
                    Map<Integer, CarrierPose> poses = synchronizedViewers.get(viewer);
                    if (poses != null) for (int id : new WrapperPlayServerDestroyEntities(event).getEntityIds()) poses.remove(id);
                    return;
                }
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
                    var packet = new WrapperPlayServerEntityPositionSync(event);
                    Integer carrier = visualCarriers.get(packet.getId());
                    if (carrier != null && riddenCarriers.contains(carrier)) {
                        CarrierPose pose = carrierPose(viewer, carrier, false);
                        if (pose != null) synchronized (pose) {
                            if (pose.nativePose != null) {
                                pose.lastBridgePose = System.nanoTime();
                                pose.mirrored = true;
                                // The visible bridge actor and its rider must use the same pose clock.
                                event.getUser().sendPacketSilently(new WrapperPlayServerEntityPositionSync(carrier, packet.getValues(), packet.isOnGround()));
                            }
                        }
                    } else {
                        CarrierPose pose = carrierPose(viewer, packet.getId(), true);
                        if (pose != null) synchronized (pose) {
                            pose.nativePose = packet.getValues();
                            pose.onGround = packet.isOnGround();
                            synchronizeCarrier(event, packet.getId(), pose);
                        }
                    }
                    return;
                }
                int moving = -1;
                Vector3d delta = Vector3d.zero();
                Float yaw = null, pitch = null;
                boolean ground = false;
                EntityPositionData teleport = null;
                RelativeFlag flags = RelativeFlag.NONE;
                if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
                    var packet = new WrapperPlayServerEntityRelativeMove(event);
                    moving = packet.getEntityId();delta = new Vector3d(packet.getDeltaX(),packet.getDeltaY(),packet.getDeltaZ());ground = packet.isOnGround();
                } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
                    var packet = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
                    moving = packet.getEntityId();delta = new Vector3d(packet.getDeltaX(),packet.getDeltaY(),packet.getDeltaZ());ground = packet.isOnGround();yaw = packet.getYaw();pitch = packet.getPitch();
                } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_ROTATION) {
                    var packet = new WrapperPlayServerEntityRotation(event);
                    moving = packet.getEntityId();ground = packet.isOnGround();yaw = packet.getYaw();pitch = packet.getPitch();
                } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_TELEPORT) {
                    var packet = new WrapperPlayServerEntityTeleport(event);
                    moving = packet.getEntityId();teleport = packet.getValues();flags = packet.getRelativeFlags();ground = packet.isOnGround();
                }
                if (moving >= 0) {
                    CarrierPose pose = carrierPose(viewer, moving, teleport != null);
                    if (pose != null) synchronized (pose) {
                        EntityPositionData previous = pose.nativePose;
                        if (teleport != null && (flags.getFullMask() == 0 || previous != null)) {
                            var position = teleport.getPosition();
                            if (previous != null) position = position.add(flags.has(RelativeFlag.X) ? previous.getPosition().x : 0,
                                    flags.has(RelativeFlag.Y) ? previous.getPosition().y : 0, flags.has(RelativeFlag.Z) ? previous.getPosition().z : 0);
                            pose.nativePose = new EntityPositionData(position, Vector3d.zero(),
                                    teleport.getYaw() + (flags.has(RelativeFlag.YAW) ? previous.getYaw() : 0),
                                    teleport.getPitch() + (flags.has(RelativeFlag.PITCH) ? previous.getPitch() : 0));
                        } else if (previous != null) {
                            pose.nativePose = new EntityPositionData(previous.getPosition().add(delta), Vector3d.zero(),
                                    yaw == null ? previous.getYaw() : yaw, pitch == null ? previous.getPitch() : pitch);
                        }
                        pose.onGround = ground;
                        synchronizeCarrier(event, moving, pose);
                    }
                    return;
                }
                if (event.getPacketType() != PacketType.Play.Server.SET_PASSENGERS) return;
                var packet = new WrapperPlayServerSetPassengers(event);
                if (!modeledCarriers.containsKey(packet.getEntityId())) return;
                int[] incoming = packet.getPassengers();
                boolean ridden = java.util.Arrays.stream(incoming).anyMatch(players::contains);
                if (ridden) riddenCarriers.add(packet.getEntityId());
                else riddenCarriers.remove(packet.getEntityId());
                int[] ordered = DinoPassengerOrder.riderFirst(incoming, players::contains);
                if (ordered != incoming) packet.setPassengers(ordered);
            }
        };
        try (var resource = plugin.getResource("egg-assets.properties")) {
            var assets = EggAssets.read(resource);
            eggAssets = BetterModel.eventBus().subscribe(plugin::isEnabled, PluginStartReloadEvent.class, event ->
                    assets.forEach((path, bytes) -> event.zipper().assets().add(path, bytes.length, () -> bytes)));
        } catch (java.io.IOException | IllegalArgumentException error) {
            bedrockSeats.close();
            throw new IllegalStateException("Dinosaur egg graphics unavailable", error);
        }
        reload = BetterModel.eventBus().subscribe(plugin::isEnabled, PluginEndReloadEvent.class, event ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    var reloadModels = new HashMap<>(models);
                    reloadModels.putAll(pending);
                    for (Model model : reloadModels.values()) {
                        try {
                            if (model.mob.isValid()) attach(model.mob, model.name);
                            else unload(model.mob);
                        } catch (RuntimeException | LinkageError error) {
                            unload(model.mob);
                            plugin.getLogger().warning("Dinosaur model reload failed: " + error.getMessage());
                        }
                    }
                }));
        PacketEvents.getAPI().getEventManager().registerListener(packets);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        animationTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAnimations, 1, 1);
    }

    @EventHandler public void join(PlayerJoinEvent event) { players.add(event.getPlayer().getEntityId()); }
    @EventHandler public void quit(PlayerQuitEvent event) { players.remove(event.getPlayer().getEntityId()); synchronizedViewers.remove(event.getPlayer().getUniqueId()); }

    private CarrierPose carrierPose(UUID viewer, int carrier, boolean create) {
        Map<Integer, CarrierPose> poses = synchronizedViewers.get(viewer);
        if (create && modeledCarriers.containsKey(carrier)) {
            poses = synchronizedViewers.computeIfAbsent(viewer, ignored -> new ConcurrentHashMap<>());
            return poses.computeIfAbsent(carrier, ignored -> new CarrierPose());
        }
        return poses == null ? null : poses.get(carrier);
    }

    private void synchronizeCarrier(PacketSendEvent event, int carrier, CarrierPose pose) {
        if (!pose.mirrored) return;
        if (!riddenCarriers.contains(carrier) || System.nanoTime() - pose.lastBridgePose >= 250_000_000) {
            // Keep consuming vanilla deltas while mirrored, then resume its original baseline.
            event.getUser().sendPacketSilently(new WrapperPlayServerEntityPositionSync(carrier, pose.nativePose, pose.onGround));
            pose.mirrored = false;
        }
        event.setCancelled(true);
    }

    private void restoreCarrier(int carrier) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<Integer, CarrierPose> poses = synchronizedViewers.get(player.getUniqueId());
            CarrierPose pose = poses == null ? null : poses.remove(carrier);
            if (pose != null) synchronized (pose) {
                if (pose.mirrored) PacketEvents.getAPI().getPlayerManager().getUser(player).sendPacketSilently(
                        new WrapperPlayServerEntityPositionSync(carrier, pose.nativePose, pose.onGround));
                pose.mirrored = false;
            }
        }
    }

    private Integer bridgeCarrier(int actor, UUID viewer) {
        // Spawn can precede our next Bukkit sampling tick; use GME's concurrent source cache.
        for (var owned : modeledCarriers.entrySet()) {
            Object cached = bridgeModels.get(owned.getKey());
            if (!(cached instanceof Map<?, ?> entries)) continue;
            try {
                for (Object data : entries.values()) {
                    var tracker = (EntityTracker) data.getClass().getMethod("getEntityTracker").invoke(data);
                    if (!tracker.name().equals(owned.getValue()) || tracker.isClosed() || tracker.forRemoval()) continue;
                    var entity = data.getClass().getMethod("getEntity").invoke(data);
                    if ((int) entity.getClass().getMethod("getEntityId").invoke(entity) != actor) continue;
                    var viewers = (Set<?>) data.getClass().getMethod("getViewers").invoke(data);
                    if (viewers.stream().noneMatch(value -> value instanceof Player player && player.getUniqueId().equals(viewer))) return null;
                    visualCarriers.put(actor, owned.getKey());
                    return owned.getKey();
                }
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Dinosaur movement bridge unavailable", error);
            }
        }
        return null;
    }

    private void trackVisualCarrier(Model model) {
        if (model.visualTracker == model.tracker) return;
        Object cached = bridgeModels.get(model.mob.getEntityId());
        if (!(cached instanceof Map<?, ?> entries)) return;
        try {
            EntityTracker stale = null;
            for (Object data : entries.values()) {
                var tracked = (EntityTracker) data.getClass().getMethod("getEntityTracker").invoke(data);
                if (tracked != model.tracker) {
                    if (tracked.name().equals(model.name) && tracked.isClosed()) stale = tracked;
                    continue;
                }
                var entity = data.getClass().getMethod("getEntity").invoke(data);
                int id = (int) entity.getClass().getMethod("getEntityId").invoke(entity);
                visualCarriers.remove(model.visualId);
                visualCarriers.put(id, model.mob.getEntityId());
                model.visualId = id;
                model.visualTracker = model.tracker;
                return;
            }
            if (stale != null) {
                stale.forRemoval(true);
                var handler = bridgeManager.getClass().getMethod("getModelHandler").invoke(bridgeManager);
                handler.getClass().getMethod("createModel", Object[].class).invoke(handler, (Object) new Object[]{model.tracker});
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Dinosaur movement bridge unavailable", error);
        }
    }

    void attach(Mob mob, String modelName) {
        var renderer = BetterModel.model(modelName).orElse(null);
        if (renderer == null) {
            unload(mob);
            pending.put(mob.getUniqueId(), new Model(mob, modelName));
            if (warned.add(modelName)) plugin.getLogger().warning("Missing dinosaur model: " + modelName);
            return;
        }
        pending.remove(mob.getUniqueId());
        Model previous = models.get(mob.getUniqueId());
        if (previous != null && !previous.name.equals(modelName)) unload(mob);
        var scale = mob.getAttribute(Attribute.SCALE);
        if (scale == null) return;
        var data = mob.getPersistentDataContainer();
        if (!data.has(originalScale, PersistentDataType.DOUBLE)) {
            data.set(originalScale, PersistentDataType.DOUBLE, modelName.equals("ark_mosasaurus") ? scale.getBaseValue() : scale.getBaseValue() / growthScale(mob));
        }
        modeledCarriers.put(mob.getEntityId(), modelName);
        models.putIfAbsent(mob.getUniqueId(), new Model(mob, modelName));
        var tracker = renderer.getOrCreate(BukkitAdapter.adapt(mob), TrackerModifier.DEFAULT,
                created -> created.scaler(ModelScaler.value((float) (DinoSizes.adultScale(modelName)
                        * (modelName.equals("ark_mosasaurus") ? 1 : growthScale(mob))))));
        String phase = previous == null || previous.phase == null ? "idle" : previous.phase;
        Model current = models.get(mob.getUniqueId());
        if (current.tracker != tracker) { current.phase = null; current.detailStage = null; current.customName = null; }
        current.tracker = tracker;
        nametag(current);
        current.growth = Double.NaN;
        growth(mob);
        animation(mob, phase);
    }

    private void nametag(Model model) {
        Component name = model.mob.customName();
        if (name == null || name.equals(Component.empty())) {
            if (model.label != null) model.label.remove();
            model.label = null;
            model.customName = name;
            return;
        }
        var anchor = model.tracker.bone("name_anchor");
        if (anchor == null) return;
        var offset = anchor.worldPosition();
        var location = model.mob.getLocation().add(offset.x, offset.y, offset.z);
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            if (model.label != null) model.label.remove();
            model.label = null;
            return;
        }
        if (model.label == null || !model.label.isValid()) {
            model.label = location.getWorld().spawn(location, TextDisplay.class, label -> {
                label.setPersistent(false);
                label.setGravity(false);
                label.setInvulnerable(true);
                label.setBillboard(Display.Billboard.CENTER);
                label.setTeleportDuration(2);
                label.setDefaultBackground(false);
                label.setShadowed(true);
                label.text(name);
            });
        } else {
            if (!java.util.Objects.equals(name, model.customName)) model.label.text(name);
            model.label.teleport(location);
        }
        model.customName = name;
    }

    private double growthScale(Mob mob) {
        Long duration = mob.getPersistentDataContainer().get(new NamespacedKey(plugin, "growth-duration"), PersistentDataType.LONG);
        return DinoGrowth.scale(mob.getPersistentDataContainer().get(adultAt, PersistentDataType.LONG),
                System.currentTimeMillis(), duration == null ? DinoGrowth.DURATION_MS : duration);
    }

    void growth(Mob mob) {
        Model model = models.get(mob.getUniqueId());
        if (model == null || model.tracker == null) return;
        if (model.name.equals("ark_mosasaurus")) return; // Wild aquatic controller owns native collision; no mount calibration.
        double value = growthScale(mob);
        String stage = switch (model.name) {
            case "ark_triceratops", "ark_stegosaurus", "ark_pteranodon", "ark_dilophosaurus" ->
                    value < .725 ? "growth_baby" : value < 1 ? "growth_juvenile" : "growth_adult";
            default -> null;
        };
        if (stage != null && !stage.equals(model.detailStage)) {
            if (model.detailStage != null) model.tracker.stopAnimation(model.detailStage);
            var pose = model.tracker.renderer().animations().get(stage);
            if (pose != null) {
                var scales = new HashMap<BoneName, Vector3f>();
                for (var entry : pose.animator().entrySet()) {
                    var movement = entry.getValue().keyframe().get(0).animate(new BoneMovement(), new BoneMovement());
                    scales.put(entry.getKey(), new Vector3f(movement.scale()));
                }
                model.detailScales = Map.copyOf(scales);
                // Keep the stage flag for Bedrock on the locally static body.
                model.tracker.bone("body").addAnimation(AnimationOverrideState.MATCHED, pose, GROWTH, () -> { });
                model.detailStage = stage;
            } else if (warned.add("growth:" + model.name)) plugin.getLogger().warning("Missing dinosaur growth pose: " + model.name + "/" + stage);
        }
        if (Double.isFinite(model.growth) && Math.abs(value - model.growth) < .001 && value != 1) return;
        if (value == model.growth) return;
        model.tracker.scaler(ModelScaler.value((float) (DinoSizes.adultScale(model.name) * value)));
        mob.getAttribute(Attribute.SCALE).setBaseValue(nativeScale(model.name) * value);
        bedrockSeats.put(mob.getUniqueId(), (float) (backHeight(model.name) * value + 1.62001 - .6));
        model.growth = value;
    }

    // Native attachment height times SCALE is the seat; player feet sit 0.6 blocks below it.
    static double nativeScale(String name) {
        return DinoSizes.nativeScale(name);
    }

    static float bedrockSeatY(String name) {
        // Geyser's player origin is 1.62001 above the feet; seated hips are 0.6 above them.
        return (float) (backHeight(name) + 1.62001 - 0.6);
    }

    private static double backHeight(String name) {
        return DinoSizes.backHeight(name);
    }

    void animation(Mob mob, String phase) {
        Model model = models.get(mob.getUniqueId());
        if (model == null || !Set.of("idle", "walk", "flap", "glide", "swim").contains(phase)) return;
        var registry = BetterModel.registry(mob.getUniqueId()).orElse(null);
        var tracker = registry == null ? null : registry.tracker(model.name);
        if (tracker == null) {
            attach(mob, model.name);
            return;
        }
        if (model.tracker != tracker) {
            model.tracker = tracker;
            model.customName = null;
            nametag(model);
            model.phase = null;
            model.growth = Double.NaN;
            model.detailStage = null;
            growth(mob);
        }
        // BetterModel installs its built-in idle when this tracker first starts.
        boolean scheduled = tracker.isScheduled();
        if (phase.equals(model.phase) && scheduled == model.phaseScheduled) return;
        if (model.phase != null) tracker.stopAnimation(model.phase);
        tracker.animate(growthClip(model, tracker.renderer().animations().get(phase), false), phase.equals("walk") || phase.equals("swim") ? AnimationModifier.builder()
                .type(AnimationIterator.Type.LOOP).priority(100).speed(() -> model.walkSpeed).build() : PHASE);
        model.phase = phase;
        model.phaseScheduled = scheduled;
    }

    void attack(Mob mob, boolean spit) {
        Model model = models.get(mob.getUniqueId());
        if (model == null) return;
        var registry = BetterModel.registry(mob.getUniqueId()).orElse(null);
        var tracker = registry == null ? null : registry.tracker(model.name);
        if (tracker == null) return;
        tracker.stopAnimation("attack");
        tracker.stopAnimation("spit");
        tracker.animate(growthClip(model, tracker.renderer().animations().get(spit && model.name.equals("ark_dilophosaurus") ? "spit" : "attack"), true), ATTACK);
    }

    private record GrowthProgress(AnimationProgress source, Model model, BoneName bone) implements AnimationProgress {
        @Override public float time() { return source.time(); }
        @Override public boolean skipInterpolation() { return source.skipInterpolation(); }
        @Override public boolean globalRotation() { return source.globalRotation(); }
        @Override public BoneMovement animate(BoneMovement movement, BoneMovement dest) {
            source.animate(movement, dest);
            var scale = model.detailScales.get(bone);
            if (scale != null) dest.scale().mul(scale);
            return dest;
        }
    }

    // Detail bones must sample their parent on the same phase clock.
    private BlueprintAnimation growthClip(Model model, BlueprintAnimation base, boolean attack) {
        if (model.detailScales.isEmpty()) return base;
        var animators = new HashMap<>(base.animator());
        for (var bone : model.tracker.bones()) {
            if (!model.detailScales.containsKey(bone.name())) continue;
            var own = base.animator().get(bone.name());
            var ancestor = bone.getParent();
            BlueprintAnimator inherited = null;
            while (ancestor != null && inherited == null) {
                inherited = base.animator().get(ancestor.name());
                ancestor = ancestor.getParent();
            }
            if (attack && own == null && inherited == null) continue;
            TimedStorage<AnimationProgress> timing = own != null ? own.keyframe()
                    : inherited != null ? inherited.keyframe().toEmpty() : base.emptyAnimator();
            var frames = new AnimationProgress[timing.size()];
            for (int i = 0; i < frames.length; i++) frames[i] = new GrowthProgress(timing.get(i), model, bone.name());
            animators.put(bone.name(), new BlueprintAnimator(bone.name(), new AnimationKeyframe(frames)));
        }
        return new BlueprintAnimation(base.name(), base.loop(), base.length(), base.override(), animators, base.script(), base.emptyAnimator());
    }

    private static double walkStride(String name) {
        return switch (name) {
            case "ark_trex" -> 1.208210;
            case "ark_triceratops", "ark_stegosaurus" -> .603235;
            case "ark_brachiosaurus" -> 1.000753;
            case "ark_velociraptor" -> .969712;
            case "ark_pteranodon" -> .899698;
            case "ark_dilophosaurus" -> .862348;
            case "ark_mosasaurus" -> 6;
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + name);
        };
    }

    private void updateAnimations() {
        for (Model model : models.values().toArray(Model[]::new)) {
            try {
                if (model.mob.isDead()) {
                    unload(model.mob);
                    continue;
                }
                // Paper can briefly invalidate a living carrier during startup.
                if (!model.mob.isValid()) continue;
                growth(model.mob);
                nametag(model);
                trackVisualCarrier(model);
                Location location = model.mob.getLocation();
                Location previous = model.previousLocation;
                model.previousLocation = location;
                if (!model.mob.isOnGround() && ("flap".equals(model.phase) || "glide".equals(model.phase))) continue;
                double dx = location.getX() - previous.getX(), dz = location.getZ() - previous.getZ();
                if (model.name.equals("ark_mosasaurus")) {
                    double dy = location.getY() - previous.getY();
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    boolean swimming = model.mob.isInWater() && location.getWorld().equals(previous.getWorld()) && distance > .01;
                    if (swimming) model.walkSpeed = (float) (.15 + Math.hypot(dx, dz) * 20 / walkStride(model.name));
                    animation(model.mob, swimming ? "swim" : "idle");
                    continue;
                }
                boolean walking = model.mob.isOnGround() && location.getWorld().equals(previous.getWorld())
                        && dx * dx + dz * dz > .0001;
                // Match the Bedrock incremental clock without rapid short-stride/juvenile cycling.
                if (walking) model.walkSpeed = (float) Math.clamp(Math.hypot(dx, dz) * 20 / 4, .35, 1.8);
                animation(model.mob, walking ? "walk" : "idle");
            } catch (RuntimeException | LinkageError error) {
                unload(model.mob);
                plugin.getLogger().warning("Dinosaur animation unavailable: " + error.getMessage());
            }
        }
    }

    void unload(Entity entity) {
        pending.remove(entity.getUniqueId());
        Model model = models.remove(entity.getUniqueId());
        if (model != null && model.label != null) model.label.remove();
        modeledCarriers.remove(entity.getEntityId());
        riddenCarriers.remove(entity.getEntityId());
        visualCarriers.entrySet().removeIf(entry -> entry.getValue() == entity.getEntityId());
        restoreCarrier(entity.getEntityId());
        bedrockSeats.remove(entity.getUniqueId());
        try {
            if (model != null) {
                BetterModel.registry(entity.getUniqueId()).ifPresent(registry -> {
                    var tracker = registry.tracker(model.name);
                    if (tracker != null) tracker.forRemoval(true);
                    registry.remove(model.name);
                });
                Object cached = bridgeModels.get(entity.getEntityId());
                if (cached instanceof Map<?, ?> entries) {
                    for (Object data : entries.values()) {
                        var tracker = (EntityTracker) data.getClass().getMethod("getEntityTracker").invoke(data);
                        if (tracker.name().equals(model.name)) tracker.forRemoval(true);
                    }
                }
            }
        } catch (ReflectiveOperationException error) {
            plugin.getLogger().warning("Dinosaur bridge cleanup failed: " + error.getMessage());
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Dinosaur model cleanup failed: " + error.getMessage());
        } finally {
            if (entity instanceof Mob mob) DinoModels.restoreScale(plugin, mob);
        }
    }

    @Override public void close() {
        animationTask.cancel();
        reload.unregister();
        eggAssets.unregister();
        pending.clear();
        for (Model model : models.values().toArray(Model[]::new)) unload(model.mob);
        bedrockSeats.close();
        PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        HandlerList.unregisterAll(this);
        players.clear();
        visualCarriers.clear();
        riddenCarriers.clear();
        synchronizedViewers.clear();
    }
}
