package de.ark.dinos;

import static org.mockito.Mockito.*;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Runs with every Geyser class absent from the classpath. */
public final class BedrockSeatsOptionalProbe {
    public static void main(String[] args) {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("OptionalSeatProbe"));
        when(server.getPluginManager()).thenReturn(plugins);
        for (boolean enabled : new boolean[]{false, true}) {
            when(plugins.isPluginEnabled("Geyser-Spigot")).thenReturn(enabled);
            var seats = new BedrockSeats(plugin);
            UUID carrier = UUID.randomUUID();
            seats.put(carrier, 2.8f);
            seats.remove(carrier);
            seats.close();
            System.out.println("PASS no Geyser classes; plugin-enabled=" + enabled);
        }
    }
}
