package de.ark.access;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

final class AccessRules {
    record Admission(boolean allowed, UUID uuid, String reason) {}
    private record Identity(String name, UUID uuid) {}
    private final Map<String, Identity> identities = new HashMap<>();
    private final String prefix;

    AccessRules(Map<String, UUID> configured, String prefix) {
        if (prefix == null || prefix.isBlank()) throw new IllegalArgumentException("Bedrock prefix must not be empty");
        this.prefix = prefix;
        configured.forEach((name, uuid) -> {
            if (!name.matches("[A-Za-z0-9_]{3,16}") || uuid == null || uuid.version() != 4) {
                throw new IllegalArgumentException("Legacy identities need a canonical Java name and Mojang UUID v4");
            }
            if (identities.putIfAbsent(name.toLowerCase(Locale.ROOT), new Identity(name, uuid)) != null) {
                throw new IllegalArgumentException("Duplicate case-insensitive legacy name");
            }
        });
    }

    Admission admit(String name, UUID uuid, boolean verifiedFloodgate) {
        if (verifiedFloodgate) return new Admission(true, uuid, "");
        if (name.startsWith(prefix) || !name.matches("[A-Za-z0-9_]{3,16}")) {
            return new Admission(false, uuid, "Dieser Name ist fuer verifizierte Bedrock-Spieler reserviert.");
        }
        Identity legacy = identities.get(name.toLowerCase(Locale.ROOT));
        if (legacy != null) {
            if (!legacy.name().equals(name)) {
                return new Admission(false, uuid, "Bitte benutze die genaue Schreibweise: " + legacy.name());
            }
            return new Admission(true, legacy.uuid(), "");
        }
        return new Admission(true, uuid, "");
    }

    boolean isLegacy(String name) {
        return identities.containsKey(name.toLowerCase(Locale.ROOT));
    }
    static boolean matchesFloodgate(String name, UUID uuid, SocketAddress socket,
                                    String verifiedName, UUID verifiedUuid, Object verifiedSocket) {
        return uuid != null && socket != null && name != null && name.equals(verifiedName)
            && uuid.equals(verifiedUuid) && Objects.equals(socket, verifiedSocket);
    }
}
