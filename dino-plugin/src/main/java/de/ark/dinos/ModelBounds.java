package de.ark.dinos;

// Generated from final animated cube corners by dino-models/verify_animated_bounds.py.
final class ModelBounds {
    private ModelBounds() { }

    record Envelope(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double halfWidth() { return Math.max(Math.abs(minX), Math.abs(maxX)); }
        double halfLength() { return Math.max(Math.abs(minZ), Math.abs(maxZ)); }
    }

    static Envelope source(String modelName) {
        return switch (modelName) {
            case "ark_brachiosaurus" -> new Envelope(-0.715625, -0.002765, -2.755068, 0.715625, 5.816725, 4.198448);
            case "ark_dilophosaurus" -> new Envelope(-1.040078, -0.008088, -1.959145, 1.040078, 2.229002, 2.814959);
            case "ark_gallimimus" -> new Envelope(-0.635960, -0.011931, -2.059881, 0.635960, 3.293322, 3.268044);
            case "ark_mosasaurus" -> new Envelope(-5.553241, -2.273788, -10.072390, 5.553241, 3.634845, 14.512856);
            case "ark_pteranodon" -> new Envelope(-4.077198, -0.536372, -3.183207, 4.077198, 3.097387, 2.367851);
            case "ark_stegosaurus" -> new Envelope(-1.551744, 0.000000, -2.482358, 2.602230, 2.737500, 3.638057);
            case "ark_trex" -> new Envelope(-0.692443, -0.010178, -2.286795, 0.692443, 2.562667, 3.509221);
            case "ark_triceratops" -> new Envelope(-0.936769, -0.016122, -2.928637, 0.936990, 2.396703, 2.691637);
            case "ark_velociraptor" -> new Envelope(-0.842323, -0.009599, -2.059646, 0.842323, 2.117937, 3.562879);
            default -> throw new IllegalArgumentException("Unknown dinosaur model " + modelName);
        };
    }
}
