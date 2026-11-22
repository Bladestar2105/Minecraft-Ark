package de.ark.dinos;

public final class DinoSizesTest {
    public static void main(String[] args) {
        String[] models = {"ark_trex", "ark_triceratops", "ark_stegosaurus", "ark_brachiosaurus",
                "ark_velociraptor", "ark_pteranodon", "ark_dilophosaurus"};
        double[] adultBacks = {3.9077724, 2.809375, 2.90625, 5.375, 1.489667, 1.619054, 1.37006975};
        double[] babyBacks = {1.75849758, 1.26421875, 1.3078125, 2.41875, .67035015, .7285743, .6165313875};
        for (int i = 0; i < models.length; i++) {
            String model = models[i];
            close(DinoSizes.backHeight(model), adultBacks[i], model + " adult rider rests on the enlarged back");
            close(DinoSizes.backHeight(model) * DinoGrowth.scale(901_000L, 1_000), babyBacks[i],
                    model + " hatchling rider height applies species size and growth once");
            close(DinoSizes.backHeight(model) * DinoGrowth.scale(901_000L, 901_000), adultBacks[i],
                    model + " growth deadline returns to the species adult size");
        }
        close(5.6942 * DinoSizes.adultScale("ark_trex"), 11.95782, "Rex grows to roughly twelve blocks in length");
        close(5.8125 * DinoSizes.adultScale("ark_brachiosaurus"), 12.496875, "Brachiosaurus keeps the tallest silhouette");
        close(8.1614 * DinoSizes.adultScale("ark_pteranodon"), 8.1614, "Pteranodon retains its already broad wings");
        close(24 * DinoSizes.adultScale("ark_mosasaurus"), 24, "Wild Mosasaurus is not enlarged twice");
        close(DinoSizes.adultScale("ark_trex") * DinoGrowth.scale(901_000L, 1_000), .945,
                "Rex hatchling render scale combines species size and persisted growth");
        var rex = DinoSizes.adultBounds("ark_trex");
        close(rex.width(), 2.91, "Rex visible width rounds outward");
        close(rex.height(), 5.41, "Rex visible height rounds outward");
        close(rex.length(), 12.18, "Rex visible length rounds outward");
        close(DinoSizes.adultBounds("ark_pteranodon").width(), 8.16, "Flight clearance includes unfolded wings");
        for (String model : models) {
            double attachment = model.equals("ark_trex") || model.equals("ark_velociraptor") || model.equals("ark_dilophosaurus") ? 2.2625 : 2.09375;
            close(DinoSizes.nativeScale(model) * attachment, DinoSizes.backHeight(model), model + " native mount matches the visible saddle");
            var visible = DinoSizes.adultBounds(model);
            var clearance = DinoSizes.centeredClearanceBounds(model);
            if (clearance.width() < visible.width() || clearance.height() < visible.height() || clearance.length() < visible.length()) {
                throw new AssertionError(model + " origin-centered animation clearance cannot shrink its visible dimensions");
            }
        }
        close(DinoSizes.nativeScale("ark_mosasaurus"), 4, "Aquatic collision keeps its native controller scale");
        // Independently transformed final-model extrema, before the species multiplier.
        envelopeContains("ark_brachiosaurus", -0.715625000000000, -0.002764965246550, -2.755067246155886);
        envelopeContains("ark_brachiosaurus", 0.715625000000000, 5.816724605325704, 4.198447114381509);
        envelopeContains("ark_dilophosaurus", -1.040077957397711, -0.008087253469159, -1.959144051359056);
        envelopeContains("ark_dilophosaurus", 1.040077957397711, 2.229001931552599, 2.814958484131847);
        envelopeContains("ark_mosasaurus", -5.553240170888869, -2.273787826741154, -10.072389615728401);
        envelopeContains("ark_mosasaurus", 5.553240170888869, 3.634844674793386, 14.512855271476568);
        envelopeContains("ark_pteranodon", -4.077197167314868, -0.536371904676064, -3.183206616449002);
        envelopeContains("ark_pteranodon", 4.077197167314868, 3.097386116372260, 2.367850701733349);
        envelopeContains("ark_stegosaurus", -1.551743556538070, 0.000000000000000, -2.482357806302712);
        envelopeContains("ark_stegosaurus", 2.602229773018967, 2.737500000000000, 3.638056312933649);
        envelopeContains("ark_trex", -0.692442769595454, -0.010177324923287, -2.286794624286744);
        envelopeContains("ark_trex", 0.692442769595454, 2.562666736630999, 3.509220876231562);
        envelopeContains("ark_triceratops", -0.936768608916124, -0.016121210559007, -2.928636908382682);
        envelopeContains("ark_triceratops", 0.936989433821639, 2.396702080721023, 2.691636740603795);
        envelopeContains("ark_velociraptor", -0.842322637129099, -0.009598669639068, -2.059645687526335);
        envelopeContains("ark_velociraptor", 0.842322637129099, 2.117936802491760, 3.562878447285873);
        envelopeContains("ark_gallimimus", -.635959149566, -.011930838259, -2.059880633595);
        envelopeContains("ark_gallimimus", .635959149566, 3.293321466985, 3.268043567587);
        reject(() -> DinoSizes.adultScale("ark_unknown"), "Unknown species cannot silently receive a mismatched scale");
        reject(() -> DinoSizes.backHeight("ark_mosasaurus"), "Unrideable Mosasaurus has no saddle height");
        System.out.println("PASS: species proportions, adult and hatchling back heights, unchanged aquatic size");
    }

    private static void envelopeContains(String model, double x, double y, double z) {
        double scale = DinoSizes.adultScale(model);
        var bounds = DinoSizes.centeredClearanceBounds(model);
        double bottom = DinoSizes.clearanceMinY(model);
        if (Math.abs(x * scale) > bounds.width() / 2 || Math.abs(z * scale) > bounds.length() / 2
                || y * scale < bottom || y * scale > bottom + bounds.height()) {
            throw new AssertionError(model + " animation escapes its rounded clearance envelope");
        }
    }

    private static void close(double actual, double expected, String message) {
        if (!Double.isFinite(actual) || Math.abs(actual - expected) > 0.0000001) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void reject(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
