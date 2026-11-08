package de.ark.dinos;

/** Conservative gameplay paces in blocks/second, not claims about extinct animals' maximum speeds. */
final class GroundSpeeds {
    record Pace(double walk,double run) {double speed(boolean running){return running?run:walk;}}
    static Pace pace(String species) {
        return switch(species) {
            case "brachiosaurus" -> new Pace(1,2);
            case "stegosaurus" -> new Pace(1,2.8);
            case "triceratops" -> new Pace(1.4,4.6);
            case "trex" -> new Pace(1.3,5);
            case "velociraptor" -> new Pace(2,7);
            case "dilophosaurus" -> new Pace(1.8,6);
            case "pteranodon" -> new Pace(1.4,3);
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
