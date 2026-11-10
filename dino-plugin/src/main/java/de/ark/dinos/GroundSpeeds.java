package de.ark.dinos;

/** Gameplay paces in blocks/second, not claims about extinct animals' maximum speeds. */
final class GroundSpeeds {
    record Pace(double walk,double run) {double speed(boolean running){return running?run:walk;}}
    static Pace pace(String species) {
        return switch(species) {
            case "brachiosaurus" -> new Pace(1.4,3.5);
            case "stegosaurus" -> new Pace(1.5,4.5);
            case "triceratops" -> new Pace(2,7);
            case "trex" -> new Pace(2.5,9);
            case "velociraptor" -> new Pace(3,12);
            case "dilophosaurus" -> new Pace(2.8,10);
            case "pteranodon" -> new Pace(2,4.5);
            case null,default -> null;
        };
    }

    static double pathMultiplier(Pace pace,boolean running,double carrierBaseSpeed) {
        // Paper244 MoveControl feeds speed into both movement input and acceleration.
        // Native flat-stone probes measured ~44*(baseAttribute*pathMultiplier)^2 blocks/second.
        // Normalize the base only: Slowness and other live attribute modifiers still affect movement.
        return !Double.isFinite(carrierBaseSpeed)||carrierBaseSpeed<=0?0:Math.sqrt(pace.speed(running)/44)/carrierBaseSpeed;
    }
}
