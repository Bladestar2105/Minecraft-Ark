package de.ark.dinos;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Sniffer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Removes only our saved presentation when the optional crossplay stack is incomplete. */
final class BetterModelCleanup implements AutoCloseable {
    private static final Set<String> NAMES = Set.of("ark_trex", "ark_triceratops", "ark_stegosaurus",
            "ark_brachiosaurus", "ark_velociraptor", "ark_pteranodon");
    private final JavaPlugin plugin;
    private final Map<UUID, Mob> carriers = new HashMap<>();
    private final ModelEventListener reload;

    BetterModelCleanup(JavaPlugin plugin) {
        this.plugin = plugin;
        reload = BetterModel.eventBus().subscribe(plugin::isEnabled, PluginEndReloadEvent.class, event ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Mob mob : carriers.values().toArray(Mob[]::new)) {
                        if (mob.isValid()) remove(mob, true);
                        else unload(mob);
                    }
                }));
    }

    void attach(Mob mob, String name) {
        if (!NAMES.contains(name) || !(mob instanceof Sniffer || mob instanceof Ravager)) return;
        String species = mob.getPersistentDataContainer().get(new NamespacedKey(plugin, "species"), PersistentDataType.STRING);
        if (!mob.getScoreboardTags().contains("ark_dino") && !name.equals("ark_" + species)) return;
        carriers.put(mob.getUniqueId(), mob);
        remove(mob, BetterModel.model(name).isPresent());
    }

    private void remove(Mob mob, boolean ready) {
        try {
            var registry = ready ? BetterModel.registry(BukkitAdapter.adapt(mob)) : BetterModel.registry(mob.getUniqueId());
            registry.ifPresent(value -> {
                // BetterModel can create an empty registry before its asynchronous model load completes.
                if (ready && value.trackers().isEmpty()) value.load();
                for (String name : NAMES) if (value.tracker(name) != null) value.remove(name);
            });
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Saved dinosaur model cleanup failed: " + error.getMessage());
        }
    }

    void unload(Mob mob) { carriers.remove(mob.getUniqueId()); }

    @Override public void close() {
        reload.unregister();
        carriers.clear();
    }
}
