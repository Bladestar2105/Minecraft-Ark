package de.ark.dinos;

import java.util.function.IntPredicate;

final class DinoPassengerOrder {
    private DinoPassengerOrder() { }

    static int[] riderFirst(int[] passengers, IntPredicate isPlayer) {
        boolean seenDisplay = false;
        boolean needsOrder = false;
        for (int passenger : passengers) {
            if (isPlayer.test(passenger)) needsOrder |= seenDisplay;
            else seenDisplay = true;
        }
        if (!needsOrder) return passengers;
        int[] ordered = new int[passengers.length];
        int index = 0;
        for (int passenger : passengers) if (isPlayer.test(passenger)) ordered[index++] = passenger;
        for (int passenger : passengers) if (!isPlayer.test(passenger)) ordered[index++] = passenger;
        return ordered;
    }
}
