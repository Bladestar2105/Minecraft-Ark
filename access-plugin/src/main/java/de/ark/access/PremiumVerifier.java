package de.ark.access;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientEncryptionResponse;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import com.github.retrooper.packetevents.wrapper.login.server.WrapperLoginServerEncryptionRequest;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/** Reuses AuthMe 6.0.1's verifier and codecs, without its name-keyed premium bypass cache. */
final class PremiumVerifier extends PacketListenerAbstract implements AutoCloseable {
    private enum Phase { PREPARING, CHALLENGED, VERIFYING, VERIFIED, CLOSED }
    static final class Session {
        final Channel channel;
        final User user;
        final String name;
        final UUID claimedUuid;
        final ClientVersion clientVersion;
        final PremiumIdentities.Identity known;
        final String nativeKey = UUID.randomUUID().toString();
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        volatile Phase phase = Phase.PREPARING;
        volatile boolean registeredAtStart;
        volatile UUID mojangUuid;
        volatile UUID expectedUuid;
        volatile UUID backendUuid;
        volatile Player joinedPlayer;
        volatile boolean registrationRequested;
        ScheduledFuture<?> timeout;

        Session(Channel channel, User user, String name, UUID claimedUuid, PremiumIdentities.Identity known) {
            this.channel = channel; this.user = user; this.name = name; this.claimedUuid = claimedUuid;
            this.known = known; this.clientVersion = user.getClientVersion();
        }
    }

    private final JavaPlugin plugin;
    private final PremiumIdentities identities;
    private final Predicate<String> registered;
    private final BiPredicate<String, SocketAddress> bedrock;
    private final Consumer<Throwable> fatal;
    private final Object verifier;
    private final Object mojang;
    private final Method lookup;
    private final Method publicKey, start, cleanup, decrypt, complete;
    private final Constructor<?> decoder, encoder;
    private final ConcurrentHashMap<Channel, Session> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SocketAddress, Session> addresses = new ConcurrentHashMap<>();

    PremiumVerifier(JavaPlugin plugin, Plugin auth, PremiumIdentities identities, Predicate<String> registered,
                    BiPredicate<String, SocketAddress> bedrock, Consumer<Throwable> fatal) throws Exception {
        super(PacketListenerPriority.LOWEST);
        this.plugin = plugin; this.identities = identities; this.registered = registered; this.bedrock = bedrock; this.fatal = fatal;
        ClassLoader loader = auth.getClass().getClassLoader();
        var field = auth.getClass().getDeclaredField("injector");
        field.setAccessible(true);
        Object injector = field.get(auth);
        Class<?> type = loader.loadClass("fr.xephi.authme.service.PremiumLoginVerifier");
        verifier = loader.loadClass("ch.jalu.injector.Injector").getMethod("getSingleton", Class.class).invoke(injector, type);
        Class<?> mojangType = loader.loadClass("fr.xephi.authme.service.MojangApiService");
        mojang = loader.loadClass("ch.jalu.injector.Injector").getMethod("getSingleton", Class.class).invoke(injector, mojangType);
        lookup = mojangType.getMethod("fetchUuidByName", String.class);
        publicKey = type.getMethod("getPublicKey");
        start = type.getMethod("startVerification", String.class, String.class, UUID.class);
        cleanup = type.getMethod("cleanupPending", String.class);
        decrypt = type.getMethod("decryptData", byte[].class);
        complete = type.getMethod("completeVerification", String.class, byte[].class, byte[].class);
        decoder = loader.loadClass("fr.xephi.authme.listener.packetevents.AesCfb8Decoder").getDeclaredConstructor(Cipher.class);
        encoder = loader.loadClass("fr.xephi.authme.listener.packetevents.AesCfb8Encoder").getDeclaredConstructor(Cipher.class);
        decoder.setAccessible(true); encoder.setAccessible(true);
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        try {
            if (event.getPacketType() == PacketType.Login.Client.LOGIN_START) loginStart(event);
            else if (event.getPacketType() == PacketType.Login.Client.ENCRYPTION_RESPONSE) encryptionResponse(event);
        } catch (Exception | LinkageError failure) {
            event.setCancelled(true);
            ((Channel) event.getUser().getChannel()).close();
            plugin.getLogger().fine("Rejected malformed premium login packet: " + failure.getClass().getSimpleName());
        }
    }

    private void loginStart(PacketReceiveEvent event) {
        User user = event.getUser();
        Channel channel = (Channel) user.getChannel();
        var packet = new WrapperLoginClientLoginStart(event);
        String name = packet.getUsername();
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return;
        if (bedrock.test(name, channel.remoteAddress())) return;
        var known = identities.known(name);
        UUID claimed = packet.getPlayerUUID().orElse(null);
        if (!PremiumRules.challenge(known != null, claimed)) return;
        event.setCancelled(true);
        if (known != null && !known.name().equals(name)) { channel.close(); return; }
        Session session = new Session(channel, user, name, claimed, known);
        if (sessions.putIfAbsent(channel, session) != null) { channel.close(); return; }
        addresses.put(channel.remoteAddress(), session);
        channel.closeFuture().addListener(ignored -> remove(session));
        session.timeout = channel.eventLoop().schedule(() -> reject(session), 30, TimeUnit.SECONDS);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                session.registeredAtStart = registered.test(name);
                if (known != null && !session.registeredAtStart) {
                    throw new IllegalStateException("A bound premium account is no longer registered");
                }
                session.expectedUuid = known == null ? lookup(name).orElse(null) : known.mojangUuid();
                if (known == null && (session.expectedUuid == null || !session.expectedUuid.equals(claimed))) {
                    channel.eventLoop().execute(() -> {
                        if (!pending(session, Phase.PREPARING)) return;
                        remove(session);
                        resume(session);
                    });
                    return;
                }
                channel.eventLoop().execute(() -> {
                    if (!pending(session, Phase.PREPARING)) return;
                    try {
                        byte[] token = (byte[]) start.invoke(verifier, session.nativeKey, name, claimed);
                        session.phase = Phase.CHALLENGED;
                        user.sendPacket(new WrapperLoginServerEncryptionRequest("", (PublicKey) publicKey.invoke(verifier), token, true));
                    } catch (Exception failure) { reject(session); fatal.accept(failure); }
                });
            } catch (Exception failure) { reject(session); fatal.accept(failure); }
        });
    }

    @SuppressWarnings("unchecked")
    private void encryptionResponse(PacketReceiveEvent event) throws Exception {
        Session session = sessions.get((Channel) event.getUser().getChannel());
        if (session == null) return;
        event.setCancelled(true);
        if (!pending(session, Phase.CHALLENGED)) { reject(session); return; }
        var response = new WrapperLoginClientEncryptionResponse(event);
        Optional<byte[]> token = response.getEncryptedVerifyToken();
        if (token.isEmpty()) { reject(session); return; }
        byte[] sharedSecret;
        try { sharedSecret = (byte[]) decrypt.invoke(verifier, (Object) response.getEncryptedSharedSecret().clone()); }
        catch (Exception invalid) { reject(session); return; }
        if (sharedSecret.length != 16) { reject(session); return; }
        installEncryption(session.channel, sharedSecret);
        session.phase = Phase.VERIFYING;
        var future = (CompletableFuture<Optional<UUID>>) complete.invoke(verifier, session.nativeKey, sharedSecret, token.get().clone());
        future.whenComplete((result, error) -> session.channel.eventLoop().execute(() -> {
            if (!pending(session, Phase.VERIFYING)) return;
            if (error != null || result == null || result.isEmpty() || result.get().version() != 4
                || !session.expectedUuid.equals(result.get())) {
                reject(session); return;
            }
            session.mojangUuid = result.get();
            session.phase = Phase.VERIFIED;
            session.timeout.cancel(false);
            resume(session);
        }));
    }

    @SuppressWarnings("unchecked")
    private Optional<UUID> lookup(String name) {
        try { return (Optional<UUID>) lookup.invoke(mojang, name); }
        catch (ReflectiveOperationException unavailable) { return Optional.empty(); }
    }

    private void resume(Session session) {
        if (session.channel.isActive()) session.user.receivePacketSilently(
            new WrapperLoginClientLoginStart(session.clientVersion, session.name, null, session.claimedUuid));
    }

    private void installEncryption(Channel channel, byte[] secret) throws Exception {
        var pipeline = channel.pipeline();
        if (pipeline.get("decrypt") != null || pipeline.get("encrypt") != null) throw new IllegalStateException("Duplicate login encryption");
        var key = new SecretKeySpec(secret, "AES");
        var iv = new IvParameterSpec(secret);
        Cipher inbound = Cipher.getInstance("AES/CFB8/NoPadding"), outbound = Cipher.getInstance("AES/CFB8/NoPadding");
        inbound.init(Cipher.DECRYPT_MODE, key, iv); outbound.init(Cipher.ENCRYPT_MODE, key, iv);
        pipeline.addBefore("splitter", "decrypt", (ChannelHandler) decoder.newInstance(inbound));
        pipeline.addBefore("prepender", "encrypt", (ChannelHandler) encoder.newInstance(outbound));
    }

    private boolean pending(Session session, Phase phase) {
        if (sessions.get(session.channel) != session || !session.channel.isActive() || session.phase != phase) return false;
        if (System.nanoTime() > session.deadline) { reject(session); return false; }
        return true;
    }

    Session proof(String name, SocketAddress rawAddress) {
        if (rawAddress == null) return null;
        Session session = addresses.get(rawAddress);
        return session != null && session.name.equals(name) && session.phase == Phase.VERIFIED
            && session.channel.isActive() && sessions.get(session.channel) == session ? session : null;
    }

    Session joined(Player player) {
        Session session = proof(player.getName(), player.getConnection().getAddress());
        if (session == null || !player.getUniqueId().equals(session.backendUuid)) return null;
        session.joinedPlayer = player;
        return session;
    }

    private void reject(Session session) {
        remove(session);
        session.channel.close();
    }

    private void remove(Session session) {
        sessions.remove(session.channel, session);
        addresses.remove(session.channel.remoteAddress(), session);
        session.phase = Phase.CLOSED;
        if (session.timeout != null) session.timeout.cancel(false);
        try { cleanup.invoke(verifier, session.nativeKey); }
        catch (Exception failure) { fatal.accept(failure); }
    }

    @Override
    public void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
        sessions.values().forEach(this::reject);
    }
}
