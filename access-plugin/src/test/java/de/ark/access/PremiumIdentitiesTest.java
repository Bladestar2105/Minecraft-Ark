package de.ark.access;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;

public final class PremiumIdentitiesTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("ark-premium-ledger-test-");
        try {
            UUID legacy = UUID.fromString("11111111-1111-4111-8111-111111111111");
            UUID licensed = UUID.fromString("22222222-2222-4222-8222-222222222222");
            UUID other = UUID.fromString("33333333-3333-4333-8333-333333333333");
            UUID offline = UUID.fromString("44444444-4444-3444-8444-444444444444");
            Path file = directory.resolve("premium-identities.yml");
            Map<String, UUID> initial = Map.of("LegacyOne", legacy);
            var ledger = new PremiumIdentities(file, initial);
            check(ledger.known("legacyone").backendUuid().equals(legacy), "configured legacy UUID is authoritative");
            check(!Files.exists(file), "initialization must not invent authenticated bindings");
            ledger.confirm("NewLicensed", licensed, licensed);
            ledger = new PremiumIdentities(file, initial);
            check(ledger.known("NewLicensed").mojangUuid().equals(licensed), "confirmed bindings must survive reload");
            ledger.confirm("OfflineOwner", other, offline);
            check(ledger.backendFor(other).equals(offline), "password-confirmed offline progress must retain its UUID");
            final var stored = ledger;
            reject(() -> stored.confirm("NewLicensed", other, licensed), "a name binding must be immutable");
            reject(() -> stored.confirm("RenamedOwner", other, other), "a renamed Mojang account must not fork its backend UUID");
            stored.confirm("RenamedOwner", other, offline);
            check(stored.backendFor(other).equals(offline), "a verified rename can retain the same backend UUID");
            reject(() -> stored.confirm("newlicensed", licensed, licensed), "case variants must not replace canonical names");
            Files.writeString(file, "identities: [invalid\n");
            reject(() -> new PremiumIdentities(file, initial), "a damaged ledger must fail closed");
            System.out.println("PremiumIdentitiesTest: 9 persistence and ownership assertions passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private interface Checked { void run() throws Exception; }
    private static void reject(Checked action, String message) throws Exception {
        try { action.run(); }
        catch (Exception expected) { return; }
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
