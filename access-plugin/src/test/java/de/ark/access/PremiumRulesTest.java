package de.ark.access;

import java.util.UUID;

public final class PremiumRulesTest {
    public static void main(String[] args) {
        UUID v4 = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID v3 = UUID.fromString("22222222-2222-3222-8222-222222222222");
        check(PremiumRules.challenge(true, v3), "bound names always require a fresh challenge");
        check(PremiumRules.challenge(false, v4), "untrusted v4 can select a challenge");
        check(!PremiumRules.challenge(false, v3), "unknown offline UUID stays on password authentication");
        check(!PremiumRules.challenge(false, null), "missing client UUID never implies ownership");
        check(PremiumRules.action(true, true, true) == PremiumRules.Action.LOGIN, "only a bound existing account may auto-login");
        check(PremiumRules.action(false, false, false) == PremiumRules.Action.REGISTER, "new verified identities may register");
        check(PremiumRules.action(false, true, true) == PremiumRules.Action.PASSWORD, "an existing password account still needs its owner's password");
        check(PremiumRules.action(false, false, true) == PremiumRules.Action.REJECT, "registration races must not become forced login");
        check(PremiumRules.action(true, true, false) == PremiumRules.Action.REJECT, "deleted bound accounts must not reopen registration");
        check(PremiumRules.action(false, true, false) == PremiumRules.Action.REJECT, "deleted protected accounts must not become new accounts");
        System.out.println("PremiumRulesTest: 10 security assertions passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
