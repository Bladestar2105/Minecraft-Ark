package de.ark.access;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class FloodgateProofs {
    record LocalLink(String name, UUID javaUuid, UUID bedrockUuid) {}

    // Only absence is false; unavailable/conflicting local evidence must never become global provenance.
    static Boolean localResult(Proof proof, CompletableFuture<LocalLink> lookup, long timeoutMillis) {
        try {
            LocalLink link = lookup.get(timeoutMillis, TimeUnit.MILLISECONDS);
            if (link == null) return false;
            return proof.matchesLocal(link.name(), link.javaUuid(), link.bedrockUuid()) ? Boolean.TRUE : null;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception unavailable) {
            return null;
        }
    }
    record Admission(UUID backendUuid, UUID mojangUuid, boolean bound, boolean registeredAtStart) {
        PremiumRules.Action action(boolean registeredNow) {
            return PremiumRules.action(bound, registeredAtStart, registeredNow);
        }
    }
    static final class Proof {
        final String name, alternateName;
        final UUID uuid, floodgateUuid, bedrockUuid;
        final boolean linked;
        final SocketAddress rawAddress;
        final BooleanSupplier active;
        volatile Admission admission;
        volatile boolean registrationRequested;
        volatile boolean localConfirmed;

        Proof(String name, String alternateName, UUID uuid, UUID floodgateUuid, UUID bedrockUuid, boolean linked, SocketAddress rawAddress, BooleanSupplier active) {
            this.name = name; this.alternateName = alternateName; this.uuid = uuid; this.linked = linked;
            this.bedrockUuid = bedrockUuid; this.floodgateUuid = floodgateUuid;
            this.rawAddress = rawAddress; this.active = active;
        }

        boolean matchesLocal(String localName, UUID localJavaUuid, UUID localBedrockUuid) {
            return linked && active.getAsBoolean() && name.equals(localName) && uuid.equals(localJavaUuid)
                && bedrockUuid != null && bedrockUuid.equals(localBedrockUuid);
        }
    }
    private final ConcurrentHashMap<SocketAddress, Proof> proofs = new ConcurrentHashMap<>();

    Proof add(String name, UUID uuid, SocketAddress rawAddress, BooleanSupplier active) {
        return add(name, name, uuid, false, rawAddress, active);
    }

    Proof add(String name, String alternateName, UUID uuid, boolean linked, SocketAddress rawAddress, BooleanSupplier active) {
        return add(name, alternateName, uuid, null, linked, rawAddress, active);
    }

    Proof add(String name, String alternateName, UUID uuid, UUID bedrockUuid, boolean linked, SocketAddress rawAddress, BooleanSupplier active) {
        return add(name, alternateName, uuid, uuid, bedrockUuid, linked, rawAddress, active);
    }

    Proof add(String name, String alternateName, UUID uuid, UUID floodgateUuid, UUID bedrockUuid, boolean linked, SocketAddress rawAddress, BooleanSupplier active) {
        if (name == null || uuid == null || rawAddress == null) throw new IllegalArgumentException("Incomplete handshake proof");
        Proof proof = new Proof(name, alternateName, uuid, floodgateUuid, bedrockUuid, linked, rawAddress, active);
        proofs.put(rawAddress, proof);
        return proof;
    }

    void remove(Proof proof) {
        proofs.remove(proof.rawAddress, proof);
    }

    boolean matches(String name, UUID uuid, SocketAddress rawAddress) {
        Proof proof = proof(name, rawAddress);
        return proof != null && proof.uuid.equals(uuid);
    }

    Proof proof(String name, SocketAddress rawAddress) {
        if (rawAddress == null) return null;
        Proof proof = proofs.get(rawAddress);
        return proof != null && proof.active.getAsBoolean() && proof.name.equals(name) ? proof : null;
    }

    boolean matchesName(String name, SocketAddress rawAddress) {
        if (rawAddress == null) return false;
        Proof proof = proofs.get(rawAddress);
        return proof != null && proof.active.getAsBoolean()
            && (proof.name.equals(name) || proof.alternateName.equals(name));
    }

    void clear() {
        proofs.clear();
    }
}
