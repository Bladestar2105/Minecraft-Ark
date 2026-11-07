package de.ark.access;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;

public final class AccessRulesTest {
    public static void main(String[] args) {
        UUID legacy = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID offline = UUID.fromString("22222222-2222-3222-8222-222222222222");
        AccessRules rules = new AccessRules(Map.of("LegacyOne", legacy), ".");
        check(rules.admit("LegacyOne", offline, false).uuid().equals(legacy), "legacy inventory identity must survive");
        check(!rules.admit("legacyone", offline, false).allowed(), "case variants must not create another identity");
        check(!rules.admit(".Bedrock", offline, false).allowed(), "a Java client cannot claim the Bedrock namespace");
        check(rules.admit(".Bedrock", offline, true).allowed(), "verified Floodgate admission must work");
        check(rules.admit("NewJava", offline, false).uuid().equals(offline), "new Java users retain offline UUIDs");
        check(rules.admit("LegacyOne", legacy, true).uuid().equals(legacy), "matching linked legacy identity preserves its UUID");
        check(!rules.admit("LegacyOne", offline, true).allowed(), "linked Floodgate must not replace a pinned Java identity");
        check(!rules.admit("legacyone", legacy, true).allowed(), "linked Floodgate must preserve the pinned canonical name");
        check(rules.isLegacy("lEgAcYoNe") && !rules.isLegacy("NewJava"), "reserved account checks must fold case");
        InetSocketAddress socket = new InetSocketAddress("127.0.0.1", 18001);
        check(AccessRules.matchesFloodgate("LegacyOne", legacy, socket, "LegacyOne", legacy, socket), "matching authenticated socket");
        check(!AccessRules.matchesFloodgate("LegacyOne", legacy, socket, "LegacyOne", legacy,
            new InetSocketAddress("127.0.0.1", 18002)), "same IP and stale UUID must not bypass password authentication");
        check(!AccessRules.matchesFloodgate("LegacyOne", legacy, socket, "SomeoneElse", legacy, socket), "Floodgate name must match");
        check(!AccessRules.matchesFloodgate("LegacyOne", legacy, socket, "LegacyOne", offline, socket), "Floodgate UUID must match");
        check(!AccessRules.matchesFloodgate("LegacyOne", legacy, socket, "LegacyOne", legacy, null), "missing socket proof fails closed");
        FloodgateProofs proofs = new FloodgateProofs();
        AtomicBoolean active = new AtomicBoolean(true);
        FloodgateProofs.Proof first = proofs.add("LegacyOne", legacy, socket, active::get);
        check(proofs.matches("LegacyOne", legacy, socket), "live handshake binds the correct raw connection");
        check(!proofs.matches("LegacyOne", legacy, new InetSocketAddress("127.0.0.1", 18002)), "a different raw socket is not verified");
        active.set(false);
        check(!proofs.matches("LegacyOne", legacy, socket), "a closed channel cannot prove authentication");
        FloodgateProofs.Proof replacement = proofs.add("LegacyOne", legacy, socket, () -> true);
        proofs.remove(first);
        check(proofs.matches("LegacyOne", legacy, socket), "closing an old channel must preserve a replacement proof");
        proofs.remove(replacement);
        check(!proofs.matches("LegacyOne", legacy, socket), "removing the current proof revokes authentication");
        FloodgateProofs.Proof linked = proofs.add("LegacyOne", ".Bedrock", legacy, true, socket, () -> true);
        check(linked.linked && proofs.proof("LegacyOne", socket) == linked, "linked status comes from the verified handshake");
        linked.admission = new FloodgateProofs.Admission(offline, legacy, true, true);
        check(linked.admission.action(true) == PremiumRules.Action.LOGIN, "confirmed linked identity may login");
        check(linked.admission.action(false) == PremiumRules.Action.REJECT, "removed account is not re-registered at join");
        var fresh = new FloodgateProofs.Admission(legacy, legacy, false, false);
        check(fresh.action(false) == PremiumRules.Action.REGISTER, "new linked identity may register");
        check(fresh.action(true) == PremiumRules.Action.REJECT, "linked registration race never becomes forced login");
        var unlinked = new FloodgateProofs.Admission(offline, null, true, true);
        check(unlinked.action(true) == PremiumRules.Action.LOGIN, "existing unlinked Bedrock still logs in");
        proofs.remove(linked);
        check(proofs.proof("LegacyOne", socket) == null, "removing handshake also removes access to its mapped admission");
        UUID bedrockId = new UUID(0, 1234567);
        AtomicBoolean localActive = new AtomicBoolean(true);
        var local = proofs.add("OfflineUser", ".Bedrock", offline, bedrockId, true, socket, localActive::get);
        check(local.matchesLocal("OfflineUser", offline, bedrockId), "local association binds both authenticated identities");
        check(!local.matchesLocal("OfflineUser", legacy, bedrockId), "local association must match the Java UUID");
        check(!local.matchesLocal("OfflineUser", offline, new UUID(0, 7654321)), "another XUID cannot use a local association");
        check(!local.matchesLocal("offlineuser", offline, bedrockId), "local association requires canonical name");
        localActive.set(false);
        check(!local.matchesLocal("OfflineUser", offline, bedrockId), "closed connection cannot attest a local association");
        var mapped = proofs.add("Converted", ".Bedrock", legacy, offline, bedrockId, true, socket, () -> true);
        check(mapped.uuid.equals(legacy) && mapped.floodgateUuid.equals(offline),
            "UUID remapping preserves immutable original identity proof alongside native backend identity");
        check(!mapped.matchesLocal("Converted", offline, bedrockId),
            "a remapped global UUID is not proof of a local association");
        var exact = new FloodgateProofs.LocalLink("Converted", legacy, bedrockId);
        check(Boolean.TRUE.equals(FloodgateProofs.localResult(mapped, CompletableFuture.completedFuture(exact), 50)),
            "a matching local provider row is attested");
        check(Boolean.FALSE.equals(FloodgateProofs.localResult(mapped, CompletableFuture.completedFuture(null), 50)),
            "a genuinely absent local row leaves global policy available");
        check(FloodgateProofs.localResult(mapped, CompletableFuture.failedFuture(new IllegalStateException()), 50) == null,
            "local provider failure is unavailable, never evidence for global registration");
        check(FloodgateProofs.localResult(mapped, new CompletableFuture<>(), 5) == null,
            "a bounded local lookup timeout is unavailable, never global provenance");
        check(FloodgateProofs.localResult(mapped, CompletableFuture.completedFuture(
            new FloodgateProofs.LocalLink("Converted", offline, bedrockId)), 50) == null,
            "a conflicting local row cannot fall through as an unbound global identity");
        System.out.println("AccessRulesTest: 38 security assertions passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
