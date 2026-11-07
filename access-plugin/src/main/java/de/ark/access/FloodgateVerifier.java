package de.ark.access;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.function.BiFunction;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.InstanceHolder;
import org.geysermc.floodgate.api.handshake.HandshakeHandlers;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.floodgate.api.player.PropertyKey;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.floodgate.util.LinkedPlayer;
import org.geysermc.floodgate.link.GlobalPlayerLinking;

// This public hook is pinned to Floodgate 2.2.5; a future API change must fail closed.
@SuppressWarnings("deprecation")
final class FloodgateVerifier implements AutoCloseable {
    private final FloodgateProofs proofs = new FloodgateProofs();
    private final FloodgateApi api = FloodgateApi.getInstance();
    private final HandshakeHandlers handlers = InstanceHolder.getHandshakeHandlers();
    private final int handlerId;

    FloodgateVerifier(BiFunction<String, UUID, UUID> retainedBackend) {
        handlerId = handlers.addHandshakeHandler(data -> {
            if (!data.isFloodgatePlayer() || data.getBedrockData() == null
                || data.getBedrockData().getDataLength() != BedrockData.EXPECTED_LENGTH || data.shouldDisconnect()) return;
            var channel = data.getChannel();
            String name = data.getCorrectUsername();
            UUID externalUuid = data.getCorrectUniqueId();
            boolean linked = data.getLinkedPlayer() != null;
            UUID backendUuid = linked ? retainedBackend.apply(name, externalUuid) : externalUuid;
            // Keep external ownership proof immutable while Floodgate and Paper share the retained backend identity.
            if (!backendUuid.equals(externalUuid)) data.setLinkedPlayer(LinkedPlayer.of(name, backendUuid, data.getJavaUniqueId()));
            var proof = proofs.add(name, data.getJavaUsername(), externalUuid, backendUuid,
                data.getJavaUniqueId(), linked, channel.remoteAddress(), channel::isActive);
            channel.closeFuture().addListener(ignored -> proofs.remove(proof));
        });
        if (handlerId == -1) throw new IllegalStateException("Floodgate handshake hook could not be installed");
    }

    FloodgateProofs.Proof verified(String name, UUID uuid, SocketAddress rawAddress, SocketAddress clientAddress) {
        var proof = proofs.proof(name, rawAddress);
        if (proof == null || !uuid.equals(proof.admission == null ? proof.floodgateUuid : proof.admission.backendUuid())) return null;
        FloodgatePlayer player = api.getPlayer(proof.floodgateUuid);
        return player != null && player.isLinked() == proof.linked && AccessRules.matchesFloodgate(name, proof.floodgateUuid, clientAddress,
            player.getCorrectUsername(), player.getCorrectUniqueId(), player.getProperty(PropertyKey.SOCKET_ADDRESS)) ? proof : null;
    }

    boolean verifiedHandshake(String name, SocketAddress rawAddress) {
        return proofs.matchesName(name, rawAddress);
    }

    // Called only by AsyncPlayerPreLoginEvent. Never use the global fallback to attest a local association.
    Boolean confirmedLocalLink(FloodgateProofs.Proof proof) {
        if (!proof.linked || proof.bedrockUuid == null) return false;
        var local = api.getPlayerLink();
        if (local instanceof GlobalPlayerLinking global) local = global.getDatabaseImpl();
        if (local == null || !local.isEnabled()) return false;
        try {
            return FloodgateProofs.localResult(proof, local.getLinkedPlayer(proof.bedrockUuid).thenApply(link -> link == null ? null
                : new FloodgateProofs.LocalLink(link.getJavaUsername(), link.getJavaUniqueId(), link.getBedrockId())), 3000);
        } catch (Exception unavailable) {
            return null;
        }
    }

    @Override
    public void close() {
        handlers.removeHandshakeHandler(handlerId);
        proofs.clear();
    }
}
