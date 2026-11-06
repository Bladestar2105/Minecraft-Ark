package de.ark.dinos;

final class TamingRules {
    enum Action {
        IGNORE, DENY, TAME, STAY, FOLLOW;

        boolean consumesSeed() { return this == TAME; }
    }

    Action interact(String owner, String actor, boolean survival, boolean seed, boolean sneaking, boolean staying) {
        if (owner != null) {
            if (!owner.equals(actor) || !survival) return Action.DENY;
            return sneaking ? (staying ? Action.FOLLOW : Action.STAY) : Action.IGNORE;
        }
        if (!seed) return Action.IGNORE;
        return survival ? Action.TAME : Action.DENY;
    }
}
