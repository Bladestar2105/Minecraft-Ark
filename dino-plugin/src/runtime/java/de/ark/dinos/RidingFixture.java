package de.ark.dinos;

import java.util.List;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated smoke-test commands; never included in the production JAR. */
public final class RidingFixture extends JavaPlugin {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.getName().equals("Rcon") && !sender.getName().equals("CONSOLE")) return true;
        Mob mob = (Mob) getServer().getWorld("ark").getEntities().stream()
                .filter(entity -> entity.getScoreboardTags().contains(args[1])).findFirst().orElseThrow();
        switch (args[0]) {
            case "disable" -> getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("ArkDinos"));
            case "unload-event" -> getServer().getPluginManager().callEvent(new EntitiesUnloadEvent(mob.getChunk(), List.of(mob)));
            case "world" -> {
                var world = new WorldCreator("ark_creative").type(WorldType.FLAT).generateStructures(false).createWorld();
                getServer().getPlayer(args[2]).teleport(world.getSpawnLocation());
            }
            default -> { }
        }
        sender.sendMessage("passengers=" + mob.getPassengers().size() + ";aware=" + mob.isAware()
                + ";path=" + mob.getPathfinder().hasPath() + ";vx=" + mob.getVelocity().getX() + ";vz=" + mob.getVelocity().getZ());
        return true;
    }
}
