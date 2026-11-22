package de.ark.dinos;

import java.util.Set;

public final class GallimimusRulesTest {
    public static void main(String[] args) {
        check("gallimimus".equals(SpeciesRules.resolve("gallimimus", Set.of("ark_dino"), false, false).species()), "Gallimimus keeps its passive native carrier identity");
        check(!"gallimimus".equals(SpeciesRules.resolve("gallimimus", Set.of("ark_dino"), false, true).species()), "Gallimimus rejects a predator carrier");
        check(CareRules.SPECIES.contains("gallimimus") && CareRules.primary("gallimimus", "WHEAT") && CareRules.finisher("gallimimus", "TORCHFLOWER_SEEDS"), "Herbivore care and breeding registry includes Gallimimus");
        check(GroundSpeeds.pace("gallimimus").walk() == 2 && GroundSpeeds.pace("gallimimus").run() == 7, "Scene paces stay within the measured animation budget");
        check(SwimRules.supports("gallimimus"), "New land species retains shore recovery");
        check(Math.abs(DinoSizes.backHeight("ark_gallimimus") - 2.09375) < .000001, "Native saddle is calibrated to the new back");
        System.out.println("PASS Gallimimus identity, herbivore care, movement and calibrated saddle");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
