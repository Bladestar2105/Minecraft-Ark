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
            check(stored.linked("OfflineOwner", other, true, offline).backendUuid().equals(offline),
                "global linked Mojang UUID must retain the confirmed offline backend");
            check(stored.linked("OfflineOwner", offline, true, offline) == null,
                "a retained v3 UUID claim alone does not attest a local link");
            check(stored.linked("OfflineOwner", offline, true, offline, true).mojangUuid().equals(other),
                "local linked retained v3 UUID uses the already confirmed Mojang binding");
            check(stored.linked("OfflineOwner", licensed, true, offline) == null,
                "a recycled name with another global Mojang identity is denied");
            check(stored.linked("offlineowner", other, true, offline) == null,
                "linked identity casing cannot replace the canonical account name");
            check(stored.linked("OfflineOwner", other, true, licensed) == null,
                "stored AuthMe UUID disagreement is denied");
            check(stored.linked("OfflineOwner", other, false, null) == null,
                "deleting a bound AuthMe account cannot reopen linked registration");
            check(stored.linked("Unbound", licensed, true, offline) == null,
                "existing unbound accounts require their original password through Java first");
            check(stored.linked("Unbound", offline, true, offline) == null,
                "an unbound v3 claim alone cannot bypass existing account confirmation");
            var local = stored.linked("Unbound", offline, true, offline, true);
            check(local != null && local.backendUuid().equals(offline) && local.mojangUuid() == null,
                "an attested mutual local link preserves the authenticated offline account without inventing Mojang proof");
            var localWithoutAuthMeUuid = stored.linked("Unbound", offline, true, null, true);
            check(localWithoutAuthMeUuid != null && localWithoutAuthMeUuid.backendUuid().equals(offline)
                && localWithoutAuthMeUuid.mojangUuid() == null,
                "when AuthMe UUID storage is off, the actual mutual local record supplies the stored backend UUID");
            check(stored.linked("Unbound", offline, true, licensed, true) == null,
                "a local association cannot replace the stored AuthMe UUID");
            check(stored.linked("Unbound", offline, false, null, true) == null,
                "a local association cannot recreate a deleted AuthMe account");
            check(stored.linked("LocalV4", licensed, false, null, true) == null,
                "a local v4 association is not global Mojang proof and cannot recreate a deleted account");
            check(stored.linked("NewName", other, false, null).backendUuid().equals(offline),
                "a new globally linked rename preserves its confirmed backend UUID");
            UUID fresh = UUID.fromString("55555555-5555-4555-8555-555555555555");
            check(stored.linked("FreshName", fresh, false, null).backendUuid().equals(fresh),
                "new global identities use their actual verified Mojang UUID");
            check(stored.linked("UnknownLocal", offline, false, null) == null,
                "an unknown local UUID cannot invent a Mojang identity");
            check(stored.linked("LegacyOne", licensed, true, legacy) == null,
                "a global link cannot replace a configured legacy identity");
            check(stored.linkedBackend("OfflineOwner", other).equals(offline),
                "Floodgate's native profile must receive the retained backend before prelogin");
            check(stored.linkedBackend("OfflineOwner", licensed).equals(licensed),
                "mapping must retain a mismatched original claim for admission rejection");
            check(stored.linkedBackend("offlineowner", other).equals(other),
                "mapping cannot normalize a conflicting canonical name");
            check(stored.linkedBackend("NewName", other).equals(offline),
                "verified global rename maps before Floodgate constructs its profile");
            reject(() -> stored.confirm("newlicensed", licensed, licensed), "case variants must not replace canonical names");
            Files.writeString(file, "identities: [invalid\n");
            reject(() -> new PremiumIdentities(file, initial), "a damaged ledger must fail closed");
            System.out.println("PremiumIdentitiesTest: 31 persistence and ownership assertions passed");
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
