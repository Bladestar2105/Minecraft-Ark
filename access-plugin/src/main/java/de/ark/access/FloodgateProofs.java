package de.ark.access;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

final class FloodgateProofs {
    record Proof(String name, String alternateName, UUID uuid, SocketAddress rawAddress, BooleanSupplier active) {}
    private final ConcurrentHashMap<SocketAddress, Proof> proofs = new ConcurrentHashMap<>();

    Proof add(String name, UUID uuid, SocketAddress rawAddress, BooleanSupplier active) {
        return add(name, name, uuid, rawAddress, active);
    }

    Proof add(String name, String alternateName, UUID uuid, SocketAddress rawAddress, BooleanSupplier active) {
        if (name == null || uuid == null || rawAddress == null) throw new IllegalArgumentException("Incomplete handshake proof");
        Proof proof = new Proof(name, alternateName, uuid, rawAddress, active);
        proofs.put(rawAddress, proof);
        return proof;
    }

    void remove(Proof proof) {
        proofs.remove(proof.rawAddress(), proof);
    }

    boolean matches(String name, UUID uuid, SocketAddress rawAddress) {
        if (rawAddress == null) return false;
        Proof proof = proofs.get(rawAddress);
        return proof != null && proof.active().getAsBoolean()
            && AccessRules.matchesFloodgate(name, uuid, rawAddress, proof.name(), proof.uuid(), proof.rawAddress());
    }

    boolean matchesName(String name, SocketAddress rawAddress) {
        if (rawAddress == null) return false;
        Proof proof = proofs.get(rawAddress);
        return proof != null && proof.active().getAsBoolean()
            && (proof.name().equals(name) || proof.alternateName().equals(name));
    }

    void clear() {
        proofs.clear();
    }
}
