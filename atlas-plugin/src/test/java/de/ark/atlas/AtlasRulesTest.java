package de.ark.atlas;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AtlasRulesTest {
    public static void main(String[] args) {
        AtlasRules.Window overview = AtlasRules.window(1, 7000, -8000);
        check(overview.equals(new AtlasRules.Window(0, 0, 20480)), "overview covers the whole world");
        AtlasRules.Window edge = AtlasRules.window(4, 10239, -10239);
        check(edge.equals(new AtlasRules.Window(7680, -7680, 5120)), "region stays inside the border");
        check(AtlasRules.window(8, 39, -39).equals(new AtlasRules.Window(40, -40, 2560)), "local center snaps to a map pixel");
        check(AtlasRules.nextZoom(1) == 4 && AtlasRules.nextZoom(4) == 8 && AtlasRules.nextZoom(8) == 1, "three zoom levels cycle");
        check(AtlasRules.sourceIndex(overview, 0, true, 1024) == 4, "northwest pixel samples its actual center");
        check(AtlasRules.sourceIndex(overview, 127, false, 1024) == 1020, "south is the bottom row");
        check(AtlasRules.cursor(overview, 0, true) == 0, "origin is the map center");
        check(AtlasRules.cursor(overview, -10240, false) == -128, "north border is the top edge");
        check(AtlasRules.cursor(overview, 10240, true) == 127, "positive border never overflows signed byte");
        check(!overview.contains(11000, 0), "hide sites outside this view");
        check(AtlasRules.direction(0) == 0 && AtlasRules.direction(90) == 4 && AtlasRules.direction(-90) == 12, "arrow follows Minecraft yaw");
        check(AtlasRules.autoGrant(true, false, false, true, true) == AtlasRules.Grant.GIVE, "first eligible join grants one atlas");
        check(AtlasRules.autoGrant(true, false, true, true, true) == AtlasRules.Grant.RECORD, "existing atlas records ownership without duplication");
        check(AtlasRules.autoGrant(true, false, false, false, true) == AtlasRules.Grant.GIVE_LOCAL, "missing terrain gives a real local map while atlas stays pending");
        check(AtlasRules.autoGrant(true, false, false, true, false) == AtlasRules.Grant.WAIT, "full inventory keeps starter pending without dropping");
        check(AtlasRules.autoGrant(true, true, false, true, true) == AtlasRules.Grant.NONE, "returning to ark never grants another starter");
        check(AtlasRules.autoGrant(false, false, false, true, true) == AtlasRules.Grant.NONE, "other worlds never grant starter maps");
        check(AtlasRules.canRequest(true, true), "Survival in ark can request an atlas");
        check(!AtlasRules.canRequest(true, false), "Creative in ark cannot request an atlas");
        check(!AtlasRules.canRequest(false, true), "Survival in another world cannot request an atlas");
        check(AtlasRules.autoGrant(true, false, true, false, true) == AtlasRules.Grant.RECORD_LOCAL, "an existing ordinary map prevents duplicate local starter");
        check(AtlasRules.autoGrant(true, true, false, false, true) == AtlasRules.Grant.NONE, "recorded local starter does not repeat on world switches");
        check(AtlasRules.autoGrant(true, false, false, false, false) == AtlasRules.Grant.WAIT, "full inventory prevents local map overwrite/drop");
        check(AtlasRules.autoGrant(false, false, false, false, true) == AtlasRules.Grant.NONE, "Creative never receives a local map");
        check(AtlasRules.autoGrant(true, false, false, true, true) == AtlasRules.Grant.GIVE, "local ownership never marks the separate full atlas grant completed");
        check(LeaderboardRules.rank(Map.of("Low", 3, "High", 12, "Middle", 7)).equals(List.of(
            new LeaderboardRules.Entry("High", 12), new LeaderboardRules.Entry("Middle", 7),
            new LeaderboardRules.Entry("Low", 3))), "leaderboard sorts points descending");
        check(LeaderboardRules.rank(Map.of("zed", 5, "Bob", 5, "alice", 5)).equals(List.of(
            new LeaderboardRules.Entry("alice", 5), new LeaderboardRules.Entry("Bob", 5),
            new LeaderboardRules.Entry("zed", 5))), "equal points sort by case-insensitive name");
        check(LeaderboardRules.rank(Map.of("Zero", 0, "Max", Integer.MAX_VALUE, "Min", Integer.MIN_VALUE)).equals(List.of(
            new LeaderboardRules.Entry("Max", Integer.MAX_VALUE), new LeaderboardRules.Entry("Zero", 0),
            new LeaderboardRules.Entry("Min", Integer.MIN_VALUE))), "zero and integer extremes retain their points without overflow");
        Map<String, Integer> aliases = new LinkedHashMap<>();
        aliases.put("alice", 9);
        aliases.put("Alice", 3);
        aliases.put("ALICE", 5);
        check(LeaderboardRules.rank(aliases).equals(List.of(new LeaderboardRules.Entry("ALICE", 9))),
            "name aliases collapse with maximum points and deterministic spelling");
        Map<String, Integer> reversedAliases = new LinkedHashMap<>();
        reversedAliases.put("ALICE", 5);
        reversedAliases.put("Alice", 3);
        reversedAliases.put("alice", 9);
        check(LeaderboardRules.rank(aliases).equals(LeaderboardRules.rank(reversedAliases)),
            "alias output does not depend on input iteration order");
        check(LeaderboardRules.rank(Map.of()).isEmpty(), "an empty roster has no leaderboard entries");
        check(aliases.equals(Map.of("alice", 9, "Alice", 3, "ALICE", 5)), "ranking never changes the input map");
        check(LeaderboardRules.rank(Map.of(".Bedrock", 2, "Player One", 1)).equals(List.of(
            new LeaderboardRules.Entry(".Bedrock", 2), new LeaderboardRules.Entry("Player One", 1))),
            "real player names with dots or spaces remain intact");
        boolean immutable = false;
        try {
            LeaderboardRules.rank(aliases).add(new LeaderboardRules.Entry("Other", 1));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        check(immutable, "ranked entries are immutable");
        System.out.println("AtlasRulesTest: 34 checks passed");
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
