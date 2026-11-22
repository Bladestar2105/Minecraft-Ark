package de.ark.dinos;

/** Shared adult proportions for rendering, native collision, riding and hatch clearance. */
final class DinoSizes {
    private DinoSizes() { }

    record Size(double width, double height, double length) { }

    static double adultScale(String modelName) {
        return switch (modelName) {
            case "ark_trex" -> 2.1;
            case "ark_triceratops" -> 1.45;
            case "ark_stegosaurus" -> 1.55;
            case "ark_brachiosaurus" -> 2.15;
            case "ark_dilophosaurus" -> 1.15;
            case "ark_velociraptor", "ark_pteranodon", "ark_mosasaurus", "ark_gallimimus" -> 1;
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
            case "ark_gallimimus" -> 2.09375;
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

    /** Adult dimensions across the final animation clips, rounded outward. */
    static Size adultBounds(String modelName) {
        var source = ModelBounds.source(modelName);
        double scale = adultScale(modelName);
        return new Size(up((source.maxX() - source.minX()) * scale),
                up((source.maxY() - source.minY()) * scale), up((source.maxZ() - source.minZ()) * scale));
    }

    /**
     * Animation envelope centered horizontally on the native carrier, rounded outward.
     * Its bottom is clearanceMinY(); height spans that bottom to the highest posed part.
     * Ground placement can clamp the bottom to zero to allow normal sole/floor contact.
     */
    static Size centeredClearanceBounds(String modelName) {
        var source = ModelBounds.source(modelName);
        double scale = adultScale(modelName);
        return new Size(up(2 * source.halfWidth() * scale),
                up(source.maxY() * scale - clearanceMinY(modelName)), up(2 * source.halfLength() * scale));
    }

    static double clearanceMinY(String modelName) {
        return Math.floor(ModelBounds.source(modelName).minY() * adultScale(modelName) * 100) / 100;
    }

    private static double up(double value) {
        return Math.ceil(value * 100) / 100;
    }
}
