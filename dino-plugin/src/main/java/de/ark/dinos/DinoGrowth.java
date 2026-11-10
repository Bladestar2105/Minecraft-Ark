package de.ark.dinos;

final class DinoGrowth {
    static final long DURATION_MS = 900_000;
    static double scale(Long adultAt, long now) {
        return scale(adultAt,now,DURATION_MS);
    }
    static double scale(Long adultAt, long now, long duration) {
        if (adultAt == null || adultAt <= now) return 1;
        return .45 + .55 * Math.max(0, 1 - (adultAt - (double) now) / duration);
    }
}
