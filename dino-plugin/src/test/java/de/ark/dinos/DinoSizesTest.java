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
        close(rex.height(), 5.28, "Rex visible height rounds outward");
        close(rex.length(), 11.96, "Rex visible length rounds outward");
        close(DinoSizes.adultBounds("ark_pteranodon").width(), 8.17, "Flight clearance includes unfolded wings");
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
        // Independently sampled envelope extrema, before the species multiplier.
        envelopeContains("ark_trex", -.69244277, -.010177325, -2.205604287);
        envelopeContains("ark_trex", .69244277, 2.658206253, 3.503061974);
        envelopeContains("ark_stegosaurus", 2.421701509, 2.764584078, 3.504479267);
        envelopeContains("ark_brachiosaurus", .6875, 5.822123601, 4.198447115);
        envelopeContains("ark_pteranodon", -4.080704534, -.546060649, -3.188734881);
        envelopeContains("ark_pteranodon", 4.080704534, 3.107420001, 2.371607158);
        envelopeContains("ark_dilophosaurus", .879351914, 2.263617670, 2.814958485);
        envelopeContains("ark_mosasaurus", 5.492842444, -2.317136732, 14.500933993);
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
