package de.ark.dinos;

public final class DinoGrowthTest {
    public static void main(String[] args) {
        check(DinoGrowth.scale(null, 1_000) == 1, "existing adult remains full size");
        check(Math.abs(DinoGrowth.scale(901_000L, 1_000) - .45) < .00001, "hatchling starts small");
        check(Math.abs(DinoGrowth.scale(901_000L, 451_000) - .725) < .00001, "growth survives offline elapsed time");
        check(DinoGrowth.scale(901_000L, 901_000) == 1, "adult at deadline");
        check(DinoGrowth.scale(901_000L, 999_000) == 1, "past deadline stays adult");
        check(DinoGrowth.scale(Long.MAX_VALUE, 0) == .45, "future timestamps cannot shrink below hatch size");
        check(Math.abs(DinoGrowth.scale(301_000L,1_000,300_000)-.45)<.00001,"Five-minute hatchling starts small");
        check(Math.abs(DinoGrowth.scale(301_000L,151_000,300_000)-.725)<.00001,"Five-minute halfway growth");
        check(DinoGrowth.scale(301_000L,301_000,300_000)==1,"Five-minute adulthood");
        System.out.println("PASS: persisted juvenile growth and adult bounds");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
