package de.ark.dinos;

import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Loaded with BetterModel, PacketEvents and Geyser entirely absent. */
public final class ModelSeatOptionalProbe {
    public static void main(String[] args) throws Exception {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ModelSeatOptionalProbe"));
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        Method seat = required("seat", Mob.class, Entity.class, double.class, double.class);
        Method clear = required("clearSeat", Mob.class);
        try (var models = new DinoModels(plugin)) {
            var carrier = mock(Mob.class);
            seat.invoke(models, carrier, mock(Entity.class), 1.2, 4.0);
            clear.invoke(models, carrier);
        }
        System.out.println("PASS optional model jars absent: second-seat hooks load and safely no-op");
    }

    private static Method required(String name, Class<?>... parameters) {
        try { return DinoModels.class.getDeclaredMethod(name, parameters); }
        catch (NoSuchMethodException error) { throw new AssertionError("Missing optional second-seat hook: " + name, error); }
    }
}
