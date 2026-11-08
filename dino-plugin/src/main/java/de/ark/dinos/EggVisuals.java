package de.ark.dinos;

import java.util.Set;
import org.bukkit.Chunk;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

/** Optional shell only; incubation and native block interactions stay in DinoCare. */
final class EggVisuals implements AutoCloseable {
    private final JavaPlugin plugin;
    private EggVisualsAdapter adapter;
    private boolean warned;

    EggVisuals(JavaPlugin plugin) {
        this.plugin=plugin;
        if(available())try { adapter=new EggVisualsAdapter(plugin,this::warn); }
        catch(RuntimeException | LinkageError error) { warn(error.getMessage()); }
    }

    private boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled("BetterModel")
            && plugin.getServer().getPluginManager().isPluginEnabled("GeyserModelEngine")
            && plugin.getServer().getPluginManager().isPluginEnabled("packetevents");
    }

    private void warn(String reason) {
        if(!warned) { warned=true;plugin.getLogger().warning("Placed dinosaur egg models unavailable; native eggs remain: "+reason); }
    }

    void show(Block block,String species) {
        if(adapter==null) { warn("optional crossplay model stack is not loaded");return; }
        try {
            if(!available()) { close();warn("optional crossplay model stack was disabled");return; }
            adapter.show(block,species);
        } catch(RuntimeException | LinkageError error) { remove(block);warn(error.getMessage()); }
    }

    void remove(Block block) {
        if(adapter!=null)try { adapter.remove(block); }
        catch(RuntimeException | LinkageError error) { warn(error.getMessage()); }
    }

    void unload(Chunk chunk) {
        if(adapter!=null)adapter.unload(chunk);
    }

    void retain(Set<Block> blocks) {
        if(adapter!=null)adapter.retain(blocks);
    }

    @Override public void close() {
        if(adapter!=null) { adapter.close();adapter=null; }
    }
}
