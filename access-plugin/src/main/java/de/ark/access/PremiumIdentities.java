package de.ark.access;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

final class PremiumIdentities {
    record Identity(String name, UUID mojangUuid, UUID backendUuid) {}
    private final Path file;
    private final Map<String, Identity> legacy;
    private volatile Map<String, Identity> confirmed;

    PremiumIdentities(Path file, Map<String, UUID> configured) throws Exception {
        this.file = file;
        Map<String, Identity> initial = new LinkedHashMap<>();
        configured.forEach((name, uuid) -> initial.put(key(name), new Identity(name, uuid, uuid)));
        legacy = Map.copyOf(initial);
        Map<String, Identity> loaded = new LinkedHashMap<>();
        if (Files.exists(file)) {
            var yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            var section = yaml.getConfigurationSection("identities");
            if (section == null) throw new IllegalStateException("Malformed premium identity ledger");
            for (String name : section.getKeys(false)) {
                Identity identity = new Identity(name, UUID.fromString(section.getString(name + ".mojang-uuid", "")),
                    UUID.fromString(section.getString(name + ".backend-uuid", "")));
                validate(identity);
                if (loaded.putIfAbsent(key(name), identity) != null) throw new IllegalStateException("Duplicate premium ledger name");
                Identity original = legacy.get(key(name));
                if (original != null && !original.equals(identity)) throw new IllegalStateException("Premium ledger contradicts a legacy identity");
            }
        }
        confirmed = Map.copyOf(loaded);
        for (Identity identity : all().values()) backendFor(identity.mojangUuid());
    }

    Identity known(String name) {
        Identity identity = confirmed.get(key(name));
        return identity == null ? legacy.get(key(name)) : identity;
    }

    Map<String, Identity> all() {
        var combined = new LinkedHashMap<>(legacy);
        combined.putAll(confirmed);
        return combined;
    }

    UUID backendFor(UUID mojangUuid) {
        UUID backend = null;
        for (Identity identity : all().values()) {
            if (!identity.mojangUuid().equals(mojangUuid)) continue;
            if (backend != null && !backend.equals(identity.backendUuid())) {
                throw new IllegalStateException("A premium identity has conflicting backend UUIDs");
            }
            backend = identity.backendUuid();
        }
        return backend;
    }

    Identity linked(String name, UUID linkedUuid, boolean registered, UUID storedUuid) {
        return linked(name, linkedUuid, registered, storedUuid, false);
    }

    // Floodgate b141 discards Paper's replacement prelogin profile. Supply the retained UUID before it constructs one.
    UUID linkedBackend(String name, UUID externalUuid) {
        Identity known = known(name);
        if (known != null) return name.equals(known.name()) && externalUuid.equals(known.mojangUuid())
            ? known.backendUuid() : externalUuid;
        UUID retained = externalUuid.version() == 4 ? backendFor(externalUuid) : null;
        return retained == null ? externalUuid : retained;
    }

    Identity linked(String name, UUID linkedUuid, boolean registered, UUID storedUuid, boolean localConfirmed) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}") || linkedUuid == null) return null;
        Identity known = known(name);
        if (known != null) {
            boolean sameIdentity = linkedUuid.equals(known.mojangUuid())
                || localConfirmed && linkedUuid.version() == 3 && linkedUuid.equals(known.backendUuid());
            return sameIdentity && name.equals(known.name()) && registered
                && (storedUuid == null || storedUuid.equals(known.backendUuid())) ? known : null;
        }
        // A local association was created by authenticated players on this server. It is not Mojang proof.
        // AuthMe UUID storage is optional; the independently attested local record already stores the actual backend UUID.
        if (localConfirmed) return registered && (storedUuid == null || linkedUuid.equals(storedUuid))
            ? new Identity(name, null, linkedUuid) : null;
        // An external Java link cannot prove ownership of a pre-existing server-password account.
        if (registered || linkedUuid.version() != 4) return null;
        UUID backend = backendFor(linkedUuid);
        return new Identity(name, linkedUuid, backend == null ? linkedUuid : backend);
    }

    synchronized void confirm(String name, UUID mojangUuid, UUID backendUuid) throws IOException {
        Identity identity = new Identity(name, mojangUuid, backendUuid);
        validate(identity);
        Identity prior = known(name);
        if (prior != null && !prior.equals(identity)) throw new IllegalStateException("Refusing to reassign a bound premium identity");
        UUID priorBackend = backendFor(mojangUuid);
        if (priorBackend != null && !priorBackend.equals(backendUuid)) throw new IllegalStateException("Refusing a premium UUID fork");
        if (identity.equals(confirmed.get(key(name)))) return;
        Map<String, Identity> next = new LinkedHashMap<>(confirmed);
        next.put(key(name), identity);
        var yaml = new YamlConfiguration();
        next.values().forEach(value -> {
            yaml.set("identities." + value.name() + ".mojang-uuid", value.mojangUuid().toString());
            yaml.set("identities." + value.name() + ".backend-uuid", value.backendUuid().toString());
        });
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".premium-identities-", ".yml");
        try {
            yaml.save(temporary.toFile());
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            confirmed = Map.copyOf(next);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static void validate(Identity identity) {
        if (!identity.name().matches("[A-Za-z0-9_]{3,16}") || identity.mojangUuid().version() != 4
            || identity.backendUuid().version() != 3 && identity.backendUuid().version() != 4) {
            throw new IllegalStateException("Invalid premium identity ledger entry");
        }
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }
}
