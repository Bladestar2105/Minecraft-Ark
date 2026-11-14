package de.ark.dinos;

public final class WildSpawnRulesTest {
    public static void main(String[] args) {
        check(WildSpawnRules.limit(0, 8, 64) == 0, "No wildlife population is created without players");
        check(WildSpawnRules.limit(2, 8, 64) == 16, "Player population sets the budget");
        check(WildSpawnRules.limit(20, 8, 64) == 64, "World hard cap bounds large servers");
        check(WildSpawnRules.limit(Integer.MAX_VALUE, 8, 64) == 64, "Counts must not overflow into negative budgets");
        check(WildSpawnRules.permits(7, 8, 5, 6), "Last available population slot is usable");
        check(!WildSpawnRules.permits(8, 8, 5, 6), "World budget is checked before each spawn");
        check(!WildSpawnRules.permits(0, 8, 6, 6), "Crowded player areas cannot accumulate dinos");
        check(WildSpawnRules.species("minecraft:deep_ocean", 0).equals("mosasaurus"), "Deep sea uses the aquatic species");
        check(WildSpawnRules.species("minecraft:river", 50) == null, "Rivers do not receive large marine animals");
        check(WildSpawnRules.species("minecraft:nether_wastes", 50) == null, "No overworld species in Nether terrain");
        check(WildSpawnRules.species("minecraft:the_end", 50) == null, "No dinosaurs in End terrain");
        check(WildSpawnRules.species("minecraft:plains", 0).equals("trex"), "Rare large predator can occur outside a habitat");
        check(!WildSpawnRules.species("minecraft:desert", 0).equals("brachiosaurus"), "Desert profile does not choose the giant grazer");
        check(WildSpawnRules.species("minecraft:plains", -1).equals(WildSpawnRules.species("minecraft:plains", 99)), "Random draw normalization stays bounded");
        System.out.println("PASS: bounded wildlife budgets and biome selection");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
