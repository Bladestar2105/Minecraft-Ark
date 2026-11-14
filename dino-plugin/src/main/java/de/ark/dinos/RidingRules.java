package de.ark.dinos;

final class RidingRules {
    static boolean canRide(String owner, String actor, boolean shared, boolean survival) {
        return owner != null && !owner.isBlank() && actor != null && !actor.isBlank()
                && survival && (owner.equals(actor) || shared);
    }

    static boolean canMount(String owner, String actor, boolean survival, boolean emptyHand,
                            boolean sneaking, boolean occupied, boolean alreadyRiding) {
        return canMount(owner, actor, false, survival, emptyHand, sneaking, occupied, alreadyRiding);
    }

    static boolean canMount(String owner, String actor, boolean shared, boolean survival, boolean emptyHand,
                            boolean sneaking, boolean occupied, boolean alreadyRiding) {
        return canRide(owner, actor, shared, survival) && emptyHand && !sneaking && !occupied && !alreadyRiding;
    }

    record Motion(double x, double z) { }

    static Motion movement(float yaw, boolean forward, boolean backward, boolean left, boolean right, boolean sprint) {
        return movement(yaw,forward,backward,left,right,sprint ? .34 : .22);
    }

    static Motion movement(float yaw, boolean forward, boolean backward, boolean left, boolean right, double speed) {
        double ahead = (forward ? 1 : 0) - (backward ? 1 : 0);
        double sideways = (left ? 1 : 0) - (right ? 1 : 0);
        double length = Math.hypot(ahead, sideways);
        if (length == 0) return new Motion(0, 0);
        double angle = Math.toRadians(yaw);
        return new Motion((-Math.sin(angle) * ahead + Math.cos(angle) * sideways) * speed / length,
                (Math.cos(angle) * ahead + Math.sin(angle) * sideways) * speed / length);
    }
}
