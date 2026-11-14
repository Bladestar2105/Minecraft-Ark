package de.ark.dinos;

import static org.mockito.Mockito.*;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.EventManager;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelEventBus;
import kr.toxicity.model.api.event.*;
import kr.toxicity.model.api.pack.PackZipper;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

/** Real listener/wrappers; only Bukkit/model discovery, scheduling and the socket boundary are mocked. */
public final class ModelSeatProbe {
    private static final int CARRIER = 40, SEAT = 41, ACTOR = 900, PLAYER = 7;
    private static final ServerVersion VERSION = ServerVersion.V_26_3;
    private static final ThreadLocal<Boolean> PACKETS = ThreadLocal.withInitial(() -> false);

    public static void main(String[] args) throws Exception {
        required("seat", Mob.class, Entity.class, double.class, double.class);
        required("clearSeat", Mob.class);
        try (var fixture = new Fixture()) {
            fixture.register(4);
            fixture.spawnNative();
            fixture.spawnActor(ACTOR);
            fixture.send(new WrapperPlayServerSetPassengers(CARRIER, new int[]{PLAYER}));
            fixture.bridge(ACTOR, 20, 80, 30, 0, 17);
            fixture.expect(CARRIER, 20, 80, 30, 0, 17);
            fixture.expect(SEAT, 20, 84, 28.8, 0, 0);
            fixture.bridge(ACTOR, 20, 80, 30, 90, -12);
            fixture.expect(SEAT, 21.2, 84, 30, 0, 0);
            System.out.println("PASS shared bridge clock: 0/90-degree rear offsets, native marker rotation and primary rider preserved");

            fixture.clearSent();
            check(fixture.send(new WrapperPlayServerEntityRelativeMove(SEAT, 2, .5, -1, true)).isCancelled(), "Fresh native marker delta leaked");
            check(fixture.sent.isEmpty(), "Fresh native marker delta emitted a second pose clock");
            fixture.expire(SEAT);
            check(fixture.send(new WrapperPlayServerEntityRelativeMove(SEAT, 1, .25, 2, false)).isCancelled(), "Stale relative packet must be replaced by its absolute baseline");
            fixture.expect(SEAT, 13, 70.75, 19.8, 0, 0);
            check(!fixture.send(new WrapperPlayServerEntityRelativeMove(SEAT, 1, 0, 0, true)).isCancelled(), "Native deltas never resumed after bridge timeout");
            fixture.bridge(ACTOR, 25, 85, 35, 90, 0);
            fixture.expire(SEAT);
            fixture.clearSent();
            fixture.tick.run();
            fixture.expect(SEAT, 14, 70.75, 19.8, 0, 0);
            System.out.println("PASS fresh suppression, continuous native delta baseline and 250 ms fallback without another movement packet");

            fixture.bridge(ACTOR, 25, 85, 35, 0, 0);
            fixture.send(new WrapperPlayServerEntityTeleport(SEAT, pose(1, 2, 3, 0, 0), RelativeFlag.X.or(RelativeFlag.Y).or(RelativeFlag.Z), true));
            fixture.clearSent();
            fixture.clearSeat();
            fixture.expect(SEAT, 15, 72.75, 22.8, 0, 0);
            fixture.bridge(ACTOR, 50, 90, 50, 0, 0);
            check(fixture.sent.stream().noneMatch(packet -> packet.getId() == SEAT), "Detached marker continued following the model");
            check(!fixture.send(new WrapperPlayServerEntityRelativeMove(SEAT, 1, 0, 0, true)).isCancelled(), "Detached marker native movement was cancelled");
            System.out.println("PASS relative teleport baseline, detach restoration and no detached-marker ghost sync");

            fixture.register(5);
            fixture.spawnSeat();
            fixture.bridge(ACTOR, 10, 80, 20, 0, 0);
            fixture.expect(SEAT, 10, 85, 18.8, 0, 0);
            fixture.clearSent();
            fixture.send(new WrapperPlayServerDestroyEntities(SEAT));
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            check(fixture.sent.stream().noneMatch(packet -> packet.getId() == SEAT), "Destroyed marker received ghost position sync");
            fixture.spawnSeat();
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            fixture.expect(SEAT, 20, 85, 28.8, 0, 0);
            fixture.clearSent();
            fixture.send(new WrapperPlayServerDestroyEntities(ACTOR));
            fixture.expect(SEAT, 10, 70, 18.8, 0, 0);
            fixture.bridge(ACTOR, 80, 80, 80, 0, 0);
            check(fixture.sent.isEmpty(), "Destroyed bridge actor resumed either rider");
            System.out.println("PASS per-viewer native/bridge destruction, restoration and respawn");

            fixture.spawnActor(ACTOR);
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            fixture.clearSent();
            fixture.reloadStart();
            fixture.expect(SEAT, 10, 70, 18.8, 0, 0);
            fixture.bridge(ACTOR, 80, 80, 80, 0, 0);
            check(fixture.sent.isEmpty(), "Old actor continued mirroring during reload");
            fixture.spawnActor(ACTOR + 1);
            fixture.reloadEnd();
            fixture.bridge(ACTOR + 1, 30, 90, 40, 90, 0);
            fixture.expect(SEAT, 31.2, 95, 40, 0, 0);
            fixture.unloadCarrier();
            fixture.clearSent();
            fixture.bridge(ACTOR + 1, 40, 90, 50, 0, 0);
            check(fixture.sent.isEmpty(), "Unloaded carrier kept a ghost seat mapping");
            System.out.println("PASS reload pauses and restores clocks, retains seat calibration, and unload stops both mirrors");
        }
        try (var fixture = new Fixture()) {
            // Paper may publish the marker synchronously inside World.spawn, before the hook returns.
            fixture.spawnNative();
            when(fixture.seat.getTrackedBy()).thenReturn(Set.of(fixture.viewer));
            when(fixture.seat.getLocation()).thenAnswer(call -> {
                serverThread();
                return new org.bukkit.Location(null, 10, 70, 18.8, 0, 0);
            });
            fixture.register(4);
            fixture.spawnActor(ACTOR);
            fixture.send(new WrapperPlayServerSetPassengers(CARRIER, new int[]{PLAYER}));
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            fixture.expect(SEAT, 20, 84, 28.8, 0, 0);
            fixture.clearSent();
            fixture.clearSeat();
            fixture.expect(SEAT, 10, 70, 18.8, 0, 0);
            System.out.println("PASS marker published before registration: tracked-viewer baseline and restoration");
        }
        try (var fixture = new Fixture()) {
            fixture.register(4);
            fixture.spawnNative();
            fixture.spawnActor(ACTOR);
            fixture.send(new WrapperPlayServerSetPassengers(CARRIER, new int[]{PLAYER}));
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            fixture.clearSent();
            fixture.register(1.2625, 5);
            check(fixture.sent.isEmpty(), "Changing occupied-seat calibration reset its native baseline");
            fixture.bridge(ACTOR, 20, 80, 30, 90, 0);
            fixture.expect(SEAT, 21.2625, 85, 30, 0, 0);
            fixture.checkOtherViewer();
            fixture.clearSent();
            fixture.send(new WrapperPlayServerDestroyEntities(ACTOR, CARRIER, SEAT));
            check(fixture.sent.isEmpty(), "Destroy batch emitted restore packets for entities destroyed in that same batch");
            fixture.bridge(ACTOR, 20, 80, 30, 0, 0);
            check(fixture.sent.isEmpty(), "Destroy batch left a rider clock alive");
            System.out.println("PASS occupied-seat recalibration, viewer isolation and actor-first destruction batch");
        }
        try (var fixture = new Fixture()) {
            fixture.register(4);
            fixture.spawnNative();
            fixture.spawnActor(ACTOR);
            fixture.send(new WrapperPlayServerSetPassengers(CARRIER, new int[]{PLAYER}));
            fixture.bridge(ACTOR, 30, 80, 40, 0, 0);
            fixture.clearSent();
            fixture.adapter.close();
            fixture.expect(SEAT, 10, 70, 18.8, 0, 0);
            fixture.bridge(ACTOR, 90, 90, 90, 0, 0);
            check(fixture.sent.isEmpty(), "Closed adapter continued mirroring a seat");
            System.out.println("PASS adapter close restores orphan registration and blocks in-flight callbacks");
        }
    }

    private static Method required(String name, Class<?>... parameters) {
        try { return BetterModelAdapter.class.getDeclaredMethod(name, parameters); }
        catch (NoSuchMethodException error) { throw new AssertionError("Missing model second-seat hook: " + name, error); }
    }

    private static EntityPositionData pose(double x, double y, double z, float yaw, float pitch) {
        return new EntityPositionData(new Vector3d(x, y, z), Vector3d.zero(), yaw, pitch);
    }

    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void serverThread() { check(!PACKETS.get(), "Bukkit Entity API was accessed on the packet thread"); }
    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    public interface BridgePlugin { Object getModelManager(); }
    public record BridgeManager(Map<Integer, Map<String, BridgeData>> entities) {
        public Map<Integer, Map<String, BridgeData>> getEntitiesCache() { return entities; }
    }
    public record BridgeData(EntityTracker tracker, Actor entity, Set<Player> viewers) {
        public EntityTracker getEntityTracker() { return tracker; }
        public Actor getEntity() { return entity; }
        public Set<Player> getViewers() { return viewers; }
    }
    public record Actor(int id) { public int getEntityId() { return id; } }

    private static final class Fixture implements AutoCloseable {
        final org.mockito.MockedStatic<BetterModel> models = mockStatic(BetterModel.class);
        final UUID viewerId = UUID.randomUUID();
        final Player viewer = mock(Player.class);
        final User user = mock(User.class);
        final Mob carrier = mock(Mob.class);
        final Entity seat = mock(Entity.class);
        final Map<Integer, Map<String, BridgeData>> bridgeCache = new ConcurrentHashMap<>();
        final Map<Class<?>, Consumer<Object>> reloads = new HashMap<>();
        final List<WrapperPlayServerEntityPositionSync> sent = new ArrayList<>();
        final BetterModelAdapter adapter;
        PacketListenerAbstract listener;
        Runnable tick;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Fixture() throws Exception {
            var plugin = mock(JavaPlugin.class);
            var server = mock(Server.class);
            var plugins = mock(PluginManager.class);
            var scheduler = mock(BukkitScheduler.class);
            when(plugin.getName()).thenReturn("ArkDinos");
            when(plugin.namespace()).thenReturn("arkdinos");
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(Logger.getLogger("ModelSeatProbe"));
            when(plugin.getResource("egg-assets.properties")).thenAnswer(call -> Files.newInputStream(Path.of("dino-plugin/src/main/resources/egg-assets.properties")));
            when(server.getPluginManager()).thenReturn(plugins);
            when(server.getScheduler()).thenReturn(scheduler);
            doReturn(List.of(viewer)).when(server).getOnlinePlayers();
            when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> { tick = call.getArgument(1); return mock(BukkitTask.class); });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { call.getArgument(1, Runnable.class).run(); return mock(BukkitTask.class); });
            when(viewer.getUniqueId()).thenAnswer(call -> { serverThread(); return viewerId; });
            when(viewer.getEntityId()).thenAnswer(call -> { serverThread(); return PLAYER; });
            when(carrier.getEntityId()).thenAnswer(call -> { serverThread(); return CARRIER; });
            when(carrier.getUniqueId()).thenAnswer(call -> { serverThread(); return new UUID(0, CARRIER); });
            when(carrier.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
            when(seat.getEntityId()).thenAnswer(call -> { serverThread(); return SEAT; });
            when(seat.getUniqueId()).thenAnswer(call -> { serverThread(); return new UUID(0, SEAT); });
            var bridge = mock(Plugin.class, withSettings().extraInterfaces(BridgePlugin.class));
            when(((BridgePlugin) bridge).getModelManager()).thenReturn(new BridgeManager(bridgeCache));
            when(plugins.getPlugin("GeyserModelEngine")).thenReturn(bridge);
            var bus = mock(BetterModelEventBus.class);
            models.when(BetterModel::eventBus).thenReturn(bus);
            var platform = mock(kr.toxicity.model.api.BetterModelPlatform.class, RETURNS_DEEP_STUBS);
            models.when(BetterModel::platform).thenReturn(platform);
            when(bus.subscribe(any(), any(), any())).thenAnswer(call -> { reloads.put(call.getArgument(1), call.getArgument(2)); return mock(ModelEventListener.class); });
            var api = mock(PacketEventsAPI.class);
            var events = mock(EventManager.class);
            var playerManager = mock(PlayerManager.class);
            var serverManager = mock(ServerManager.class);
            when(api.getEventManager()).thenReturn(events);
            when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
            when(api.getPlayerManager()).thenReturn(playerManager);
            when(api.getServerManager()).thenReturn(serverManager);
            when(serverManager.getVersion()).thenReturn(VERSION);
            when(playerManager.getUser(viewer)).thenReturn(user);
            when(events.registerListener(any(PacketListenerCommon.class))).thenAnswer(call -> { listener = call.getArgument(0); return listener; });
            PacketEvents.setAPI(api);
            when(user.getUUID()).thenReturn(viewerId);
            when(user.getClientVersion()).thenReturn(VERSION.toClientVersion());
            doAnswer(call -> { sent.add(call.getArgument(0)); return null; }).when(user).sendPacketSilently(any(PacketWrapper.class));
            adapter = new BetterModelAdapter(plugin);
            ((Map<Integer, String>) field(BetterModelAdapter.class, "modeledCarriers").get(adapter)).put(CARRIER, "ark_trex");
        }

        void register(double height) throws Exception { register(1.2, height); }
        void register(double rear, double height) throws Exception { required("seat", Mob.class, Entity.class, double.class, double.class).invoke(adapter, carrier, seat, rear, height); }
        void clearSeat() throws Exception { required("clearSeat", Mob.class).invoke(adapter, carrier); }
        void unloadCarrier() {
            // ID-based unload bookkeeping is independent of Bukkit's native attribute registry.
            var entity = mock(Entity.class);
            when(entity.getEntityId()).thenReturn(CARRIER);
            when(entity.getUniqueId()).thenReturn(new UUID(0, CARRIER));
            adapter.unload(entity);
        }
        void spawnNative() { send(spawn(CARRIER, EntityTypes.RAVAGER, 10, 66, 20)); spawnSeat(); }
        void spawnSeat() { send(spawn(SEAT, EntityTypes.ARMOR_STAND, 10, 70, 18.8)); }
        void spawnActor(int actor) {
            var tracker = mock(EntityTracker.class);
            when(tracker.name()).thenReturn("ark_trex");
            bridgeCache.put(CARRIER, Map.of("ark_trex", new BridgeData(tracker, new Actor(actor), Set.of(viewer))));
            send(spawn(actor, EntityTypes.PIG, 10, 66, 20));
        }
        WrapperPlayServerSpawnEntity spawn(int id, com.github.retrooper.packetevents.protocol.entity.type.EntityType type, double x, double y, double z) {
            return new WrapperPlayServerSpawnEntity(id, Optional.of(new UUID(0, id)), type, new Vector3d(x, y, z), 0, 0, 0, 0, Optional.of(Vector3d.zero()));
        }
        void bridge(int actor, double x, double y, double z, float yaw, float pitch) { clearSent(); send(new WrapperPlayServerEntityPositionSync(actor, pose(x, y, z, yaw, pitch), true)); }
        void clearSent() { sent.clear(); }
        PacketSendEvent send(PacketWrapper<?> packet) {
            return send(user, packet);
        }
        PacketSendEvent send(User recipient, PacketWrapper<?> packet) {
            var event = mock(PacketSendEvent.class, CALLS_REAL_METHODS);
            doReturn(recipient).when(event).getUser();
            doReturn(VERSION).when(event).getServerVersion();
            doReturn(packet.getPacketTypeData().getPacketType()).when(event).getPacketType();
            event.setLastUsedWrapper(packet);
            PACKETS.set(true);
            try { listener.onPacketSend(event); }
            finally { PACKETS.set(false); }
            return event;
        }
        void checkOtherViewer() {
            var other = mock(Player.class);
            UUID otherId = UUID.randomUUID();
            when(other.getUniqueId()).thenAnswer(call -> { serverThread(); return otherId; });
            when(other.getEntityId()).thenAnswer(call -> { serverThread(); return PLAYER + 1; });
            adapter.join(new org.bukkit.event.player.PlayerJoinEvent(other, net.kyori.adventure.text.Component.empty()));
            var otherUser = mock(User.class);
            when(otherUser.getUUID()).thenReturn(otherId);
            when(otherUser.getClientVersion()).thenReturn(VERSION.toClientVersion());
            var otherSent = new ArrayList<PacketWrapper<?>>();
            doAnswer(call -> { otherSent.add(call.getArgument(0)); return null; }).when(otherUser).sendPacketSilently(any(PacketWrapper.class));
            send(otherUser, spawn(CARRIER, EntityTypes.RAVAGER, 10, 66, 20));
            send(otherUser, spawn(SEAT, EntityTypes.ARMOR_STAND, 10, 70, 18.8));
            send(otherUser, spawn(ACTOR, EntityTypes.PIG, 10, 66, 20));
            send(otherUser, new WrapperPlayServerEntityPositionSync(ACTOR, pose(70, 80, 90, 0, 0), true));
            check(otherSent.isEmpty(), "A viewer outside the model's viewer set received mirrored poses");
            check(!send(otherUser, new WrapperPlayServerEntityRelativeMove(SEAT, 1, 0, 0, true)).isCancelled(), "A native-only viewer's marker movement was suppressed");
            adapter.quit(new org.bukkit.event.player.PlayerQuitEvent(other, net.kyori.adventure.text.Component.empty()));
        }
        void expect(int id, double x, double y, double z, float yaw, float pitch) {
            var matches = sent.stream().filter(packet -> packet.getId() == id).toList();
            check(matches.size() == 1, "Expected one position sync for " + id + ", received " + matches.size());
            var actual = matches.getFirst().getValues();
            var position = actual.getPosition();
            check(Math.abs(position.x - x) < .000001 && Math.abs(position.y - y) < .000001 && Math.abs(position.z - z) < .000001
                    && actual.getYaw() == yaw && actual.getPitch() == pitch,
                    "Wrong position/rotation for " + id + ": " + actual + "; expected " + x + "," + y + "," + z + "/" + yaw + "," + pitch);
        }
        @SuppressWarnings("unchecked") void expire(int id) throws Exception {
            var views = (Map<UUID, Map<Integer, Object>>) field(BetterModelAdapter.class, "synchronizedViewers").get(adapter);
            var state = views.get(viewerId).get(id);
            field(state.getClass(), "lastBridgePose").setLong(state, System.nanoTime() - 300_000_000L);
        }
        void reloadStart() { reloads.get(PluginStartReloadEvent.class).accept(new PluginStartReloadEvent(mock(PackZipper.class, RETURNS_DEEP_STUBS))); }
        void reloadEnd() { reloads.get(PluginEndReloadEvent.class).accept(new PluginEndReloadEvent(null)); }
        @Override public void close() { adapter.close(); models.close(); }
    }
}
