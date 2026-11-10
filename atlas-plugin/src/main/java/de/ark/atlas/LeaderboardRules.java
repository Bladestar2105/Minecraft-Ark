package de.ark.atlas;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class LeaderboardRules {
    record Entry(String name, int points) {}

    static List<Entry> rank(Map<String, Integer> points) {
        Map<String, Entry> players = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        points.forEach((name, score) -> players.merge(name, new Entry(name, score), (first, second) ->
            new Entry(first.name().compareTo(second.name()) <= 0 ? first.name() : second.name(),
                Math.max(first.points(), second.points()))));
        return players.values().stream()
            .sorted(Comparator.comparingInt(Entry::points).reversed()
                .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::name))
            .toList();
    }
}
