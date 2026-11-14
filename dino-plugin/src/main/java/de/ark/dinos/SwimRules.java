package de.ark.dinos;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

final class SwimRules {
    private static final int MAX_COLUMN_SAMPLES = 32;

    static boolean supports(String species) {
        return switch (species) {
            case "triceratops", "stegosaurus", "brachiosaurus", "trex", "velociraptor", "dilophosaurus", "pteranodon" -> true;
            case null, default -> false;
        };
    }

    static double verticalVelocity(double eyeY, double surfaceY, double currentY) {
        if (!Double.isFinite(eyeY) || !Double.isFinite(surfaceY) || !Double.isFinite(currentY)) return 0;
        double remaining = surfaceY + .12 - eyeY;
        // Recover a submerged head without accumulating upward momentum or driving a wading animal down.
        return remaining > 0 ? Math.min(.18, remaining * .35) : Math.clamp(currentY, -.08, 0);
    }

    static OptionalInt surface(int bottomY, int bodyTopY, int maxHeight, IntPredicate water) {
        if (bottomY > bodyTopY || bottomY >= maxHeight) return OptionalInt.empty();
        boolean submerged = false;
        int y = bottomY;
        for (int sampled = 0; sampled < MAX_COLUMN_SAMPLES && y < maxHeight; sampled++, y++) {
            if (!submerged && y > bodyTopY) return OptionalInt.empty();
            if (water.test(y)) submerged = true;
            else if (submerged) return OptionalInt.of(y);
        }
        // In very deep water this is an intermediate target; the next tick continues the bounded ascent.
        return submerged ? OptionalInt.of(y) : OptionalInt.empty();
    }
}
