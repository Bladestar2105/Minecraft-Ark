package de.ark.access;

import java.net.SocketAddress;
import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.InstanceHolder;
import org.geysermc.floodgate.api.handshake.HandshakeHandlers;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.api.player.PropertyKey;
import org.geysermc.floodgate.util.BedrockData;

// This public hook is pinned to Floodgate 2.2.5; a future API change must fail closed.
@SuppressWarnings("deprecation")
final class FloodgateVerifier implements AutoCloseable {
    private final FloodgateProofs proofs = new FloodgateProofs();
    private final FloodgateApi api = FloodgateApi.getInstance();
    private final HandshakeHandlers handlers = InstanceHolder.getHandshakeHandlers();
    private final int handlerId;

    FloodgateVerifier() {
        handlerId = handlers.addHandshakeHandler(data -> {
            if (!data.isFloodgatePlayer() || data.getBedrockData() == null
                || data.getBedrockData().getDataLength() != BedrockData.EXPECTED_LENGTH || data.shouldDisconnect()) return;
            var channel = data.getChannel();
            var proof = proofs.add(data.getCorrectUsername(), data.getJavaUsername(), data.getCorrectUniqueId(), channel.remoteAddress(), channel::isActive);
            channel.closeFuture().addListener(ignored -> proofs.remove(proof));
        });
        if (handlerId == -1) throw new IllegalStateException("Floodgate handshake hook could not be installed");
    }

    boolean verified(String name, UUID uuid, SocketAddress rawAddress, SocketAddress clientAddress) {
        if (!proofs.matches(name, uuid, rawAddress)) return false;
        FloodgatePlayer player = api.getPlayer(uuid);
        return player != null && AccessRules.matchesFloodgate(name, uuid, clientAddress,
            player.getCorrectUsername(), player.getCorrectUniqueId(), player.getProperty(PropertyKey.SOCKET_ADDRESS));
    }

    boolean verifiedHandshake(String name, SocketAddress rawAddress) {
        return proofs.matchesName(name, rawAddress);
    }

    @Override
    public void close() {
        handlers.removeHandshakeHandler(handlerId);
        proofs.clear();
    }
}
