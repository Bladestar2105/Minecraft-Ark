package de.ark.dinos;

import static org.mockito.Mockito.*;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** Tests target identity without starting a server; the optional run omits all model jars. */
public final class ModelTargetProbe {
    private static Method resolver(Class<?> type) {
        try { return type.getDeclaredMethod("resolveTarget", Entity.class); }
        catch (NoSuchMethodException error) { throw new AssertionError(type.getSimpleName() + " has no model-target resolver", error); }
    }

    private static void same(Object expected, Object actual, String message) {
        if (expected != actual) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        var plugin = mock(JavaPlugin.class);
        var server = mock(Server.class);
        var plugins = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ModelTargetProbe"));
        when(server.getPluginManager()).thenReturn(plugins);
        try (var models = new DinoModels(plugin)) {
            var entity = mock(Entity.class);
            same(entity, resolver(DinoModels.class).invoke(models, entity), "No-model fallback changed the target");
        }
        System.out.println("PASS model jars absent or disabled: original target retained");
        if (args.length == 0) EngineChecks.run();
    }

    private static final class EngineChecks {
        static void run() throws Exception {
            var adapter = mock(BetterModelAdapter.class, CALLS_REAL_METHODS);
            var resolve = resolver(BetterModelAdapter.class);
            var ordinary = mock(Entity.class);
            same(ordinary, resolve.invoke(adapter, ordinary), "An ordinary entity was changed");
            var source = mock(Entity.class);
            var server = mock(Server.class);
            var proxy = mock(Entity.class, withSettings().extraInterfaces(kr.toxicity.model.api.nms.HitBox.class));
            var platformSource = mock(kr.toxicity.model.api.platform.PlatformEntity.class);
            var uuid = UUID.randomUUID();
            when(((kr.toxicity.model.api.nms.HitBox) proxy).source()).thenReturn(platformSource);
            when(platformSource.uuid()).thenReturn(uuid);
            when(proxy.getServer()).thenReturn(server);
            when(server.getEntity(uuid)).thenReturn(source);
            same(source, resolve.invoke(adapter, proxy), "Hitbox did not resolve to its native source");
            when(server.getEntity(uuid)).thenReturn(null);
            same(proxy, resolve.invoke(adapter, proxy), "A missing source must retain the contacted target");
            System.out.println("PASS ordinary target, public hitbox source, removed-source fallback");
        }
    }
}
