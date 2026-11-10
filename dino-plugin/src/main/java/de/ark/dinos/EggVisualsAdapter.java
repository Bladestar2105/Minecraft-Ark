package de.ark.dinos;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.event.ModelEventListener;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;

final class EggVisualsAdapter implements AutoCloseable {
    private record Visual(ArmorStand actor,EntityTracker tracker) { }
    private final JavaPlugin plugin;
    private final Consumer<String> warning;
    private final Map<Block,Visual> visuals=new HashMap<>();
    private final ModelEventListener reload;

    EggVisualsAdapter(JavaPlugin plugin,Consumer<String> warning) {
        this.plugin=plugin;this.warning=warning;
        reload=BetterModel.eventBus().subscribe(plugin::isEnabled,PluginEndReloadEvent.class,event ->
            plugin.getServer().getScheduler().runTask(plugin,this::clear));
    }

    void show(Block block,String species) {
        String name="ark_egg_"+species;Visual previous=visuals.get(block);
        if(previous!=null && previous.actor().isValid() && !previous.tracker().isClosed()
            && !previous.tracker().forRemoval() && previous.tracker().name().equals(name))return;
        remove(block);
        var renderer=BetterModel.model(name).orElse(null);
        if(renderer==null) { warning.accept("missing model "+name);return; }
        ArmorStand actor=block.getWorld().spawn(block.getLocation().add(.5,0,.5),ArmorStand.class,entity -> {
            entity.setMarker(true);entity.setGravity(false);entity.setPersistent(false);entity.setInvulnerable(true);
            entity.setSilent(true);entity.setBasePlate(false);entity.setCollidable(false);entity.addScoreboardTag("ark_egg_visual");
        });
        if(!actor.isValid())return;
        try {
            // Native invisibility also hides BetterModel displays; the tracker hides only the carrier on clients.
            var tracker=renderer.getOrCreate(BukkitAdapter.adapt(actor),new TrackerModifier(false,false,false),
                created -> created.scaler(ModelScaler.value(1)));
            visuals.put(block,new Visual(actor,tracker));
        } catch(RuntimeException | LinkageError error) { actor.remove();throw error; }
    }

    void remove(Block block) {
        Visual visual=visuals.remove(block);if(visual==null)return;
        try {
            visual.tracker().forRemoval(true);
            BetterModel.registry(visual.actor().getUniqueId()).ifPresent(registry -> registry.remove(visual.tracker().name()));
        } catch(RuntimeException | LinkageError error) { warning.accept(error.getMessage()); }
        finally { visual.actor().remove(); }
    }

    void unload(Chunk chunk) {
        for(Block block:Set.copyOf(visuals.keySet()))if(block.getWorld().equals(chunk.getWorld())
            && block.getX()>>4==chunk.getX() && block.getZ()>>4==chunk.getZ())remove(block);
    }

    void retain(Set<Block> blocks) {
        for(Block block:Set.copyOf(visuals.keySet()))if(!blocks.contains(block))remove(block);
    }

    private void clear() {
        for(Block block:Set.copyOf(visuals.keySet()))remove(block);
    }

    @Override public void close() { reload.unregister();clear(); }
}
