package de.ark.dinos;

import static org.mockito.Mockito.*;

import java.util.*;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.entity.data.GeyserEntityDataTypes;
import org.geysermc.geyser.entity.VanillaEntities;
import org.geysermc.geyser.entity.spawn.EntitySpawnContext;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.geyser.translator.protocol.java.entity.JavaSetPassengersTranslator;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetPassengersPacket;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;

/** Real pinned translator and metadata path; only the network/session boundary is mocked. */
public final class BedrockSeatProbe {
    public static void main(String[] args) throws Exception {
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        var geyser = mock(GeyserImpl.class);
        var config = mock(org.geysermc.geyser.configuration.GeyserConfig.class, RETURNS_DEEP_STUBS);
        when(geyser.config()).thenReturn(config);
        when(geyser.getLogger()).thenReturn(mock(GeyserLogger.class));
        var bus = new GeyserEventBus();
        when(geyser.eventBus()).thenReturn(bus);
        when(geyser.provider(eq(org.geysermc.geyser.api.util.Identifier.class), anyString(), anyString()))
                .thenAnswer(call -> org.geysermc.geyser.impl.IdentifierImpl.of(call.getArgument(1), call.getArgument(2)));
        try (var singleton = mockStatic(GeyserImpl.class); var api = mockStatic(GeyserApi.class)) {
            singleton.when(GeyserImpl::getInstance).thenReturn(geyser);
            api.when(GeyserApi::api).thenReturn(geyser);
            var plugin = mock(JavaPlugin.class);
            var server = mock(Server.class);
            var plugins = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            when(plugins.isPluginEnabled("Geyser-Spigot")).thenReturn(true);
            Object seats = baseline ? null : Class.forName("de.ark.dinos.BedrockSeats").getDeclaredConstructor(JavaPlugin.class).newInstance(plugin);
            String[] species = {"trex", "triceratops", "stegosaurus", "brachiosaurus", "velociraptor", "pteranodon", "dilophosaurus"};
            float[] expected = {2.880854f, 2.95751f, 2.89501f, 3.52001f, 2.509677f, 2.639064f, 2.211375f};
            Entity mountedAtClose = null;
            for (int i = 0; i < species.length; i++) {
                var session = mock(GeyserSession.class);
                var cache = mock(EntityCache.class);
                var world = mock(org.geysermc.geyser.session.cache.WorldCache.class);
                var scoreboard = mock(org.geysermc.geyser.scoreboard.Scoreboard.class);
                when(session.getWorldCache()).thenReturn(world);
                when(world.getScoreboard()).thenReturn(scoreboard);
                var lookup = mock(org.geysermc.geyser.api.entity.EntityData.class);
                when(session.getEntityCache()).thenReturn(cache);
                when(session.getGeyser()).thenReturn(geyser);
                when(session.entities()).thenReturn(lookup);
                when(geyser.onlineConnections()).thenReturn(List.of(session));
                doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(session).ensureInEventLoop(any(Runnable.class));
                var uuid = UUID.randomUUID();
                var definition = i == 0 || i == 4 || i == 6 ? VanillaEntities.RAVAGER : VanillaEntities.SNIFFER;
                var carrier = new LivingEntity(new EntitySpawnContext(session, definition, 40, uuid));
                carrier.setValid(true);
                var rider = new SessionPlayerEntity(session);
                rider.setEntityId(29);
                var other = new PlayerEntity(new EntitySpawnContext(session, VanillaEntities.PLAYER, 30, UUID.randomUUID()), "RemoteJavaRider", null);
                other.setValid(true);
                when(session.getPlayerEntity()).thenReturn(rider);
                when(cache.getEntityByJavaId(40)).thenReturn(carrier);
                when(cache.getEntityByJavaId(29)).thenReturn(rider);
                when(cache.getEntityByJavaId(30)).thenReturn(other);
                when(lookup.byUuid(uuid)).thenReturn(carrier);
                List<BedrockPacket> packets = new ArrayList<>();
                doAnswer(call -> { packets.add(call.getArgument(0)); return null; }).when(session).sendUpstreamPacket(any(BedrockPacket.class));
                double nativeScale = (double) Class.forName("de.ark.dinos.BetterModelAdapter")
                        .getDeclaredMethod("nativeScale", String.class).invoke(null, "ark_" + species[i]);
                carrier.updateBedrockAttributes(session, List.of(new Attribute(AttributeType.Builtin.SCALE, nativeScale)));
                if (Math.abs(carrier.value(GeyserEntityDataTypes.SCALE) - (float) nativeScale) > .000001f)
                    throw new AssertionError("Geyser dropped native carrier scale");
                float seatY = baseline ? expected[i] : (float) Class.forName("de.ark.dinos.BetterModelAdapter")
                        .getDeclaredMethod("bedrockSeatY", String.class).invoke(null, "ark_" + species[i]);
                if (Math.abs(seatY - expected[i]) > .000001f) throw new AssertionError("Model back calibration changed: " + species[i]);
                if (seats != null) invoke(seats, "put", new Class<?>[]{UUID.class, float.class}, uuid, seatY);
                var translator = new JavaSetPassengersTranslator();
                translator.translate(session, new ClientboundSetPassengersPacket(40, new int[]{29, 9001, 9002}));
                rider.updateBedrockMetadata();
                checkSeat(species[i], rider, packets, expected[i]);
                if (!rider.getFlag(EntityFlag.RIDING)) throw new AssertionError("Rider lost RIDING flag");
                if (packets.stream().filter(SetEntityLinkPacket.class::isInstance).map(SetEntityLinkPacket.class::cast)
                        .noneMatch(packet -> packet.getEntityLink().getType() == EntityLinkData.Type.RIDER)) throw new AssertionError("Missing RIDER link");
                // The same viewer must see the correct seat for another player too.
                translator.translate(session, new ClientboundSetPassengersPacket(40, new int[]{30}));
                other.updateBedrockMetadata();
                checkSeat(species[i] + " remote", other, packets, expected[i]);
                if (rider.override(GeyserEntityDataTypes.SEAT_OFFSET) != null) throw new AssertionError("Dismount kept our seat override");
                // Reattaching a changed model updates an already mounted rider.
                invoke(seats, "put", new Class<?>[]{UUID.class, float.class}, uuid, expected[i] + .1f);
                other.updateBedrockMetadata();
                checkSeat(species[i] + " reload", other, packets, expected[i] + .1f);
                // Entity destruction may reach a viewer before Bukkit's unload callback.
                when(lookup.byUuid(uuid)).thenReturn(null);
                invoke(seats, "remove", new Class<?>[]{UUID.class}, uuid);
                other.updateBedrockMetadata();
                if (other.override(GeyserEntityDataTypes.SEAT_OFFSET) != null) throw new AssertionError("Unloading kept our seat override");
                checkSeat(species[i] + " native restored", other, packets, i == 0 || i == 4 || i == 6 ? 3.37001f : 3.07001f);
                when(lookup.byUuid(uuid)).thenReturn(carrier);
                translator.translate(session, new ClientboundSetPassengersPacket(40, new int[]{}));
                invoke(seats, "put", new Class<?>[]{UUID.class, float.class}, uuid, expected[i]);
                translator.translate(session, new ClientboundSetPassengersPacket(40, new int[]{29}));
                rider.updateBedrockMetadata();
                checkSeat(species[i] + " remount", rider, packets, expected[i]);
                if (i + 1 < species.length) invoke(seats, "remove", new Class<?>[]{UUID.class}, uuid);
                else mountedAtClose = rider;
                System.out.println("PASS " + species[i] + " local/remote RIDER metadata, dismount, reload, unload and remount");
            }
            invoke(seats, "close", new Class<?>[]{});
            if (mountedAtClose.override(GeyserEntityDataTypes.SEAT_OFFSET) != null) throw new AssertionError("Close kept our seat override");
            System.out.println("PASS seat integration close");
        }
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... values) throws Exception {
        target.getClass().getDeclaredMethod(name, types).invoke(target, values);
    }

    private static void checkSeat(String label, Entity rider, List<BedrockPacket> packets, float expected) {
        Vector3f actual = packets.stream().filter(SetEntityDataPacket.class::isInstance).map(SetEntityDataPacket.class::cast)
                .filter(packet -> packet.getRuntimeEntityId() == rider.geyserId() && packet.getMetadata().containsKey(EntityDataTypes.SEAT_OFFSET))
                .reduce((first, last) -> last).orElseThrow().getMetadata().get(EntityDataTypes.SEAT_OFFSET);
        if (Math.abs(actual.getY() - expected) > .00001f) throw new AssertionError(label + ": wanted SEAT_OFFSET.y=" + expected + " but emitted " + actual);
    }
}
