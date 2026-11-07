package de.ark.dinos;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class BetterModelAdapter implements Listener, AutoCloseable {
    private static final AnimationModifier PHASE = AnimationModifier.builder()
            .type(AnimationIterator.Type.LOOP).priority(100).build();
    private final JavaPlugin plugin;
    private final NamespacedKey originalScale;
    private final Map<UUID, Model> models = new HashMap<>();
    private final Map<UUID, Model> pending = new HashMap<>();
    private final ModelEventListener reload;
    private final Set<Integer> modeledCarriers = ConcurrentHashMap.newKeySet();
    private final Set<Integer> players = ConcurrentHashMap.newKeySet();
    private final Set<String> warned = new HashSet<>();
    private final PacketListenerAbstract packets;

    private static final class Model {
        final Mob mob;
        final String name;
        String phase;
        EntityTracker tracker;
        Model(Mob mob, String name) { this.mob = mob; this.name = name; }
    }

    BetterModelAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
        originalScale = new NamespacedKey(plugin, "model-original-scale");
        for (Player player : plugin.getServer().getOnlinePlayers()) players.add(player.getEntityId());
        packets = new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.SET_PASSENGERS) return;
                var packet = new WrapperPlayServerSetPassengers(event);
                if (!modeledCarriers.contains(packet.getEntityId())) return;
                int[] incoming = packet.getPassengers();
                int[] ordered = DinoPassengerOrder.riderFirst(incoming, players::contains);
                if (ordered != incoming) packet.setPassengers(ordered);
            }
        };
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
    }

    @EventHandler public void join(PlayerJoinEvent event) { players.add(event.getPlayer().getEntityId()); }
    @EventHandler public void quit(PlayerQuitEvent event) { players.remove(event.getPlayer().getEntityId()); }

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
            data.set(originalScale, PersistentDataType.DOUBLE, scale.getBaseValue());
        }
        modeledCarriers.add(mob.getEntityId());
        models.putIfAbsent(mob.getUniqueId(), new Model(mob, modelName));
        var tracker = renderer.getOrCreate(BukkitAdapter.adapt(mob), TrackerModifier.DEFAULT,
                created -> created.scaler(ModelScaler.value(1f)));
        tracker.scaler(ModelScaler.value(1f));
        scale.setBaseValue(nativeScale(modelName));
        String phase = previous == null || previous.phase == null ? "idle" : previous.phase;
        Model current = models.get(mob.getUniqueId());
        if (current.tracker != tracker) current.phase = null;
        current.tracker = tracker;
        animation(mob, phase);
    }

    // Native attachment height times SCALE is the seat; player feet sit 0.6 blocks below it.
    static double nativeScale(String name) {
        return switch (name) {
            case "ark_trex" -> 1.860844 / 2.2625;
            case "ark_triceratops" -> 1.9375 / 2.09375;
            case "ark_stegosaurus" -> 1.875 / 2.09375;
            case "ark_brachiosaurus" -> 2.5 / 2.09375;
            case "ark_velociraptor" -> 1.489667 / 2.2625;
            case "ark_pteranodon" -> 1.619054 / 2.09375;
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + name);
        };
    }

    void animation(Mob mob, String phase) {
        Model model = models.get(mob.getUniqueId());
        if (model == null || !Set.of("idle", "walk", "flap", "glide").contains(phase)) return;
        var registry = BetterModel.registry(mob.getUniqueId()).orElse(null);
        var tracker = registry == null ? null : registry.tracker(model.name);
        if (tracker == null) {
            attach(mob, model.name);
            return;
        }
        if (model.tracker != tracker) {
            model.tracker = tracker;
            model.phase = null;
            tracker.scaler(ModelScaler.value(1f));
        }
        if (phase.equals(model.phase)) return;
        if (model.phase != null) tracker.stopAnimation(model.phase);
        tracker.animate(phase, PHASE);
        model.phase = phase;
    }

    void unload(Entity entity) {
        pending.remove(entity.getUniqueId());
        Model model = models.remove(entity.getUniqueId());
        modeledCarriers.remove(entity.getEntityId());
        try {
            if (model != null) BetterModel.registry(entity.getUniqueId()).ifPresent(registry -> registry.remove(model.name));
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Dinosaur model cleanup failed: " + error.getMessage());
        } finally {
            if (entity instanceof Mob mob) DinoModels.restoreScale(plugin, mob);
        }
    }

    @Override public void close() {
        reload.unregister();
        pending.clear();
        for (Model model : models.values().toArray(Model[]::new)) unload(model.mob);
        PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        HandlerList.unregisterAll(this);
        players.clear();
    }
}
