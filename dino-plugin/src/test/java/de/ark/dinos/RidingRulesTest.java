package de.ark.dinos;

public final class RidingRulesTest {
    public static void main(String[] args) {
        check(RidingRules.canMount("alice", "alice", true, true, false, false, false), "Owner can mount on foot with empty hand");
        check(!RidingRules.canMount(null, "alice", true, true, false, false, false), "Wild dino cannot be ridden");
        check(!RidingRules.canMount("alice", "bob", true, true, false, false, false), "Another player cannot ride");
        check(!RidingRules.canMount("broken", "alice", true, true, false, false, false), "Malformed owner fails closed");
        check(!RidingRules.canMount("alice", "alice", false, true, false, false, false), "Creative or disallowed world cannot ride");
        check(!RidingRules.canMount("alice", "alice", true, false, false, false, false), "Held item must not mount");
        check(!RidingRules.canMount("alice", "alice", true, true, true, false, false), "Sneak interaction retains stay/follow");
        check(!RidingRules.canMount("alice", "alice", true, true, false, true, false), "Occupied pet refuses mounting");
        check(!RidingRules.canMount("alice", "alice", true, true, false, false, true), "Already mounted player cannot mount again");
        movement(0, true, false, false, false, 0, 0.22, "Forward follows yaw");
        movement(90, true, false, false, false, -0.22, 0, "Turning follows yaw");
        movement(0, false, true, false, false, 0, -0.22, "Backward reverses movement");
        movement(0, false, false, true, false, 0.22, 0, "Left strafes relative to facing");
        movement(0, false, false, false, true, -0.22, 0, "Right strafes relative to facing");
        movement(0, true, true, true, true, 0, 0, "Opposed keys cancel");
        movement(45, true, false, true, false, 0, 0.22, "Diagonal movement is normalized");
        movement(0, false, false, false, false, 0, 0, "Released controls stop horizontal movement");
        System.out.println("PASS: 17 riding ownership, input and movement checks");
    }

    private static void movement(float yaw, boolean forward, boolean backward, boolean left, boolean right,
                                 double x, double z, String message) {
        var motion = RidingRules.movement(yaw, forward, backward, left, right);
        check(Math.abs(motion.x() - x) < 0.000001 && Math.abs(motion.z() - z) < 0.000001, message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
