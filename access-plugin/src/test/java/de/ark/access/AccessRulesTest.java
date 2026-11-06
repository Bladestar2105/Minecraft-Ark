package de.ark.access;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

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
        check(rules.admit("LegacyOne", offline, true).uuid().equals(offline), "linked Floodgate identity must never be remapped");
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
        System.out.println("AccessRulesTest: 17 security assertions passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
