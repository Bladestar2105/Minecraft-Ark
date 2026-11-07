package de.ark.dinos;

import java.util.Arrays;

public final class DinoPassengerOrderTest {
    public static void main(String[] args) {
        int[] incoming = { 90, 91, 7, 92, 8 };
        int[] sorted = DinoPassengerOrder.riderFirst(incoming, id -> id == 7 || id == 8);
        if (!Arrays.equals(sorted, new int[] { 7, 8, 90, 91, 92 })) throw new AssertionError("Real player must be the first Bedrock rider");
        if (!Arrays.equals(incoming, new int[] { 90, 91, 7, 92, 8 })) throw new AssertionError("Input modified");
        if (DinoPassengerOrder.riderFirst(incoming, id -> false) != incoming) throw new AssertionError("Unrelated passengers changed");
        int[] already = { 7, 8, 90 };
        if (DinoPassengerOrder.riderFirst(already, id -> id < 10) != already) throw new AssertionError("Already ordered packet changed");
        if (DinoPassengerOrder.riderFirst(new int[0], id -> true).length != 0) throw new AssertionError("Empty passengers");
        System.out.println("DinoPassengerOrderTest passed");
    }
}
