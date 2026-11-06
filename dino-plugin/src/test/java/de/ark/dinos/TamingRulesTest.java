package de.ark.dinos;

public final class TamingRulesTest {
    public static void main(String[] args) {
        var rules = new TamingRules();
        check(rules.interact(null, "alice", true, true, false, false) == TamingRules.Action.TAME,
                "Survival seed interaction must tame an unowned dino");
        check(TamingRules.Action.TAME.consumesSeed(), "Taming must consume exactly one seed");
        check(!TamingRules.Action.STAY.consumesSeed(), "Stay must not consume seeds");
        check(!TamingRules.Action.FOLLOW.consumesSeed(), "Follow must not consume seeds");
        check(rules.interact(null, "alice", false, true, false, false) == TamingRules.Action.DENY,
                "Creative must not acquire a dino");
        check(rules.interact(null, "alice", true, false, false, false) == TamingRules.Action.IGNORE,
                "Wrong food must not tame or be consumed");
        check(rules.interact("alice", "bob", true, true, true, false) == TamingRules.Action.DENY,
                "Another player must not steal or command an owned dino");
        check(rules.interact("invalid owner data", "alice", true, true, true, false) == TamingRules.Action.DENY,
                "Malformed existing ownership must fail closed");
        check(rules.interact("alice", "alice", true, false, true, false) == TamingRules.Action.STAY,
                "Sneaking owner must toggle follow to stay without food");
        check(rules.interact("alice", "alice", true, true, true, true) == TamingRules.Action.FOLLOW,
                "Sneaking owner must toggle stay to follow without consuming food");
        check(rules.interact("alice", "alice", false, true, true, false) == TamingRules.Action.DENY,
                "Creative must not command Survival pets");
        System.out.println("PASS: 11 ownership, consumption and stay/follow checks");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
