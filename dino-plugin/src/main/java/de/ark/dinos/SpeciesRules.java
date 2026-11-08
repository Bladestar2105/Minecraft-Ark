package de.ark.dinos;

import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;

final class SpeciesRules {
    private static final String[] SPECIES = {"triceratops", "stegosaurus", "trex", "brachiosaurus", "pteranodon", "velociraptor"};
    private static final Pattern HABITAT = Pattern.compile("ark_renewal_habitat_(0[1-9]|1[0-2])_dino_[01]");
    record Resolution(String species, boolean conflict) { }

    static Resolution resolve(String stored, Set<String> tags, boolean owned, boolean ravager, boolean dolphin) {
        if (!dolphin) return resolve(stored, tags, owned, ravager);
        return new Resolution(!owned && aquatic(stored) && tags.contains("ark_dino") && tags.contains("ark_dino_wild") ? stored : null, false);
    }

    static boolean aquatic(String species) { return "mosasaurus".equals(species); }

    static Resolution resolve(String stored, Set<String> tags, boolean owned, boolean ravager) {
        Set<String> candidates = new HashSet<>();
        for (String tag : tags) {
            var match = HABITAT.matcher(tag);
            if (match.matches()) candidates.add(SPECIES[(Integer.parseInt(match.group(1)) - 1) % 6]);
        }
        if (!owned && !tags.contains("ark_dino") && candidates.isEmpty()) return new Resolution(null, false);
        if (compatible(stored, ravager)) return new Resolution(stored, false);
        boolean conflict = candidates.size() > 1 || candidates.stream().anyMatch(name -> !compatible(name, ravager));
        String species = !conflict && candidates.size() == 1 ? candidates.iterator().next() : ravager ? "trex" : "triceratops";
        return new Resolution(species, conflict);
    }

    private static boolean compatible(String name, boolean ravager) {
        if (name == null) return false;
        return ravager ? ravager(name)
                : name.equals("triceratops") || name.equals("stegosaurus") || name.equals("brachiosaurus") || name.equals("pteranodon");
    }

    static boolean ravager(String species) {
        return "trex".equals(species) || "velociraptor".equals(species) || "dilophosaurus".equals(species);
    }
}
