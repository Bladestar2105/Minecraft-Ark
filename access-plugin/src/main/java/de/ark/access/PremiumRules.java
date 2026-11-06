package de.ark.access;

import java.util.UUID;

final class PremiumRules {
    enum Action { PASSWORD, REGISTER, LOGIN, REJECT }
    static boolean challenge(boolean knownIdentity, UUID claimedUuid) {
        return knownIdentity || claimedUuid != null && claimedUuid.version() == 4;
    }
    static Action action(boolean knownIdentity, boolean registeredAtStart, boolean registeredNow) {
        if (knownIdentity) return registeredNow ? Action.LOGIN : Action.REJECT;
        if (registeredAtStart) return registeredNow ? Action.PASSWORD : Action.REJECT;
        return registeredNow ? Action.REJECT : Action.REGISTER;
    }
}
