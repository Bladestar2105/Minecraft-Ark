package de.ark.accounts;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class PortalNotices implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final PortalInfo info;
    private final NamespacedKey marker;
    private UUID display;
    private BukkitTask announcement;
    private boolean closed;
    PortalNotices(JavaPlugin plugin,PortalInfo info) {
        this.plugin=plugin;this.info=info;marker=new NamespacedKey(plugin,"portal-display");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        Bukkit.getScheduler().runTask(plugin,this::refresh);
        if(info.announcementSeconds()>0) {
            long ticks=info.announcementSeconds()*20L;
            announcement=Bukkit.getScheduler().runTaskTimer(plugin,()->{
                for(Player player:Bukkit.getOnlinePlayers())player.sendMessage(Component.text(info.announcement(player.locale())).clickEvent(ClickEvent.openUrl(info.url())));
            },ticks,ticks);
        }
    }
    private boolean owned(Entity entity){return entity instanceof TextDisplay&&Byte.valueOf((byte)1).equals(entity.getPersistentDataContainer().get(marker,PersistentDataType.BYTE));}
    private World mainWorld(){return Bukkit.getWorlds().isEmpty()?null:Bukkit.getWorlds().getFirst();}
    private void removeLoaded() {
        for(World world:Bukkit.getWorlds())for(TextDisplay entity:world.getEntitiesByClass(TextDisplay.class))if(owned(entity))entity.remove();
        display=null;
    }
    private void refresh(){if(closed)return;removeLoaded();ensureDisplay();}
    private void ensureDisplay() {
        if(closed||!info.spawnDisplay()||mainWorld()==null)return;
        if(display!=null&&Bukkit.getEntity(display) instanceof TextDisplay current&&current.isValid())return;
        Location location=mainWorld().getSpawnLocation().add(.5,2.5,.5);
        TextDisplay entity=mainWorld().spawn(location,TextDisplay.class,text->{
            text.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
            text.text(Component.text(info.spawnText()));text.setBillboard(Display.Billboard.CENTER);
            text.setShadowed(true);text.setLineWidth(400);text.setBrightness(new Display.Brightness(15,15));
            text.setGravity(false);text.setInvulnerable(true);text.setSilent(true);
            // Recreate from current configuration; never leave saved holograms after a crash or disabled setting.
            text.setPersistent(false);
        });
        display=entity.getUniqueId();
    }
    @EventHandler public void spawnChanged(SpawnChangeEvent event){if(event.getWorld().equals(mainWorld()))Bukkit.getScheduler().runTask(plugin,this::refresh);}
    @EventHandler public void worldLoaded(WorldLoadEvent event){if(event.getWorld().equals(mainWorld()))Bukkit.getScheduler().runTask(plugin,this::refresh);}
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent event) {
        for(Entity entity:event.getEntities())if(owned(entity)&&!entity.getUniqueId().equals(display))entity.remove();
        checkSpawnChunk(event.getChunk());
    }
    @EventHandler public void chunkLoaded(ChunkLoadEvent event){checkSpawnChunk(event.getChunk());}
    private void checkSpawnChunk(Chunk chunk) {
        World main=mainWorld();if(closed||!info.spawnDisplay()||!chunk.getWorld().equals(main))return;
        Location spawn=main.getSpawnLocation();
        if(chunk.getX()==spawn.getBlockX()>>4&&chunk.getZ()==spawn.getBlockZ()>>4)Bukkit.getScheduler().runTask(plugin,this::ensureDisplay);
    }
    @Override public void close(){closed=true;if(announcement!=null)announcement.cancel();HandlerList.unregisterAll(this);removeLoaded();}
}
