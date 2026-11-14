package de.ark.dinos;

/** Shared adult proportions for rendering, native collision, riding and hatch clearance. */
final class DinoSizes {
    private DinoSizes() { }

    record Size(double width, double height, double length) { }
    private record Envelope(double halfWidth, double minY, double maxY, double halfLength) { }

    static double adultScale(String modelName) {
        return switch (modelName) {
            case "ark_trex" -> 2.1;
            case "ark_triceratops" -> 1.45;
            case "ark_stegosaurus" -> 1.55;
            case "ark_brachiosaurus" -> 2.15;
            case "ark_dilophosaurus" -> 1.15;
            case "ark_velociraptor", "ark_pteranodon", "ark_mosasaurus" -> 1;
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + modelName);
        };
    }

    /** Height of the adult saddle surface above native feet, in blocks. */
    static double backHeight(String modelName) {
        double sourceBack = switch (modelName) {
            case "ark_trex" -> 1.860844;
            case "ark_triceratops" -> 1.9375;
            case "ark_stegosaurus" -> 1.875;
            case "ark_brachiosaurus" -> 2.5;
            case "ark_velociraptor" -> 1.489667;
            case "ark_pteranodon" -> 1.619054;
            case "ark_dilophosaurus" -> 1.191365;
            default -> throw new IllegalArgumentException("Dinosaur model has no saddle " + modelName);
        };
        return sourceBack * adultScale(modelName);
    }

    static double nativeScale(String modelName) {
        if (modelName.equals("ark_mosasaurus")) return 4;
        double attachment = switch (modelName) {
            case "ark_trex", "ark_velociraptor", "ark_dilophosaurus" -> 2.2625;
            default -> 2.09375;
        };
        return backHeight(modelName) / attachment;
    }

    /** Resting adult dimensions; Pteranodon uses its full gliding wingspan. */
    static Size adultBounds(String modelName) {
        Size source = switch (modelName) {
            case "ark_trex" -> new Size(1.384886, 2.510010, 5.694213);
            case "ark_triceratops" -> new Size(1.837795, 2.334855, 5.416726);
            case "ark_stegosaurus" -> new Size(1.303847, 2.764585, 5.944516);
            case "ark_brachiosaurus" -> new Size(1.375, 5.8125, 6.963123);
            case "ark_velociraptor" -> new Size(1.466602, 2.052167, 5.565657);
            case "ark_pteranodon" -> new Size(8.161410, 2.656066, 5.436285);
            case "ark_dilophosaurus" -> new Size(1.0125, 2.202115, 4.754062);
            case "ark_mosasaurus" -> new Size(10.539497, 5, 24);
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + modelName);
        };
        double scale = adultScale(modelName);
        return new Size(up(source.width * scale), up(source.height * scale), up(source.length * scale));
    }

    /**
     * Animation envelope centered horizontally on the native carrier, rounded outward.
     * Its bottom is clearanceMinY(); height spans that bottom to the highest posed part.
     * Ground placement can clamp the bottom to zero to allow normal sole/floor contact.
     */
    static Size centeredClearanceBounds(String modelName) {
        Envelope source = sourceEnvelope(modelName);
        double scale = adultScale(modelName);
        return new Size(up(2 * source.halfWidth * scale),
                up(source.maxY * scale) - clearanceMinY(modelName), up(2 * source.halfLength * scale));
    }

    static double clearanceMinY(String modelName) {
        return Math.floor(sourceEnvelope(modelName).minY * adultScale(modelName) * 100) / 100;
    }

    // From independent transformed corners of every exported clip, sampled at <=0.25 degrees.
    private static Envelope sourceEnvelope(String modelName) {
        return switch (modelName) {
            case "ark_trex" -> new Envelope(.692443, -.010178, 2.658207, 3.503062);
            case "ark_triceratops" -> new Envelope(.921625, 0, 2.379552, 2.790895);
            case "ark_stegosaurus" -> new Envelope(2.421702, 0, 2.764585, 3.504480);
            case "ark_brachiosaurus" -> new Envelope(.6875, -.002765, 5.822124, 4.198448);
            case "ark_velociraptor" -> new Envelope(.845169, -.009599, 2.163654, 3.564836);
            case "ark_pteranodon" -> new Envelope(4.080705, -.546061, 3.107421, 3.188735);
            case "ark_dilophosaurus" -> new Envelope(.879352, -.008088, 2.263618, 2.814959);
            case "ark_mosasaurus" -> new Envelope(5.492843, -2.317137, 3.5, 14.500934);
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + modelName);
        };
    }

    private static double up(double value) {
        return Math.ceil(value * 100) / 100;
    }
}
