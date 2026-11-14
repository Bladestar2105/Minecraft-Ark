package de.ark.dinos;

import java.util.Locale;

/** Bounded population policy; world/claim/block access stays in the Bukkit controller. */
final class WildSpawnRules {
    static int limit(int players, int perPlayer, int hardLimit) {
        return (int) Math.min(Math.max(0, hardLimit), Math.max(0L, players) * Math.max(0L, perPlayer));
    }

    static boolean permits(int population, int limit, int nearby, int nearbyLimit) {
        return population >= 0 && population < limit && nearby >= 0 && nearby < nearbyLimit;
    }

    static String species(String biome, int draw) {
        if (biome == null) return null;
        String name = biome.toLowerCase(Locale.ROOT);
        if (name.contains("ocean")) return "mosasaurus";
        if (name.contains("river") || name.contains("nether") || name.contains("the_end") || name.contains("end_")
                || name.contains("basalt") || name.contains("soul_sand") || name.contains("crimson") || name.contains("warped")
                || name.contains("void")) return null;
        int roll = Math.floorMod(draw, 100);
        if (name.contains("desert") || name.contains("badlands")) {
            return roll < 35 ? "dilophosaurus" : roll < 60 ? "velociraptor" : roll < 85 ? "stegosaurus" : "pteranodon";
        }
        if (name.contains("peak") || name.contains("mountain") || name.contains("beach") || name.contains("slope")) {
            return roll < 70 ? "pteranodon" : "velociraptor";
        }
        return roll < 8 ? "trex" : roll < 24 ? "velociraptor" : roll < 38 ? "dilophosaurus"
                : roll < 58 ? "triceratops" : roll < 76 ? "stegosaurus" : roll < 90 ? "brachiosaurus" : "pteranodon";
    }
}
