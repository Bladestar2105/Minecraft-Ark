package de.ark.dinos;

import java.util.List;

public final class HerdRulesTest {
    public static void main(String[] args) {
        var origin = new HerdRules.Point(0, 0);
        var east = new HerdRules.Point(1, 0);
        var neighbor = new HerdRules.Point(2, 0);
        check(HerdRules.steer(origin, List.of(neighbor), null, origin).x() < 0,
                "Close animals separate before loose herd cohesion");
        check(HerdRules.steer(origin, List.of(new HerdRules.Point(20, 0)), null, origin).x() > 0,
                "A distant herd mate attracts ordinary loose movement");
        check(HerdRules.steer(origin, List.of(), new HerdRules.Point(-10, 0), east).x() > 0,
                "A herd animal runs away from the common threat");
        var threat = new HerdRules.Point(57, 0);
        check(HerdRules.threat(List.of(origin, neighbor), List.of(threat)) == threat,
                "Threat seen by a herd mate propagates to the local herd");
        check(HerdRules.threat(List.of(origin), List.of(new HerdRules.Point(80, 0))) == null,
                "Distant predators do not keep an entire island fleeing");
        check(!HerdRules.graze(0, 0, true, true), "Threat cancels grazing immediately");
        check(!HerdRules.graze(0, 0, false, false), "Grazing requires suitable ground");
        check(HerdRules.graze(0, 0, true, false), "Quiet grass allows a bounded grazing phase");
        check(!HerdRules.eligible(true, false, false, false, true), "Owned pets retain their existing controls");
        check(!HerdRules.eligible(false, true, false, false, true), "Passengers prevent herd override");
        check(!HerdRules.eligible(false, false, true, false, true), "Stay remains authoritative");
        check(!HerdRules.eligible(false, false, false, true, true), "Leashes remain authoritative");
        for (var point : List.of(new HerdRules.Point(Double.NaN, 0),
                new HerdRules.Point(Double.POSITIVE_INFINITY, 0), new HerdRules.Point(1e308, -1e308))) {
            var move = HerdRules.steer(origin, List.of(point), point, point);
            check(Double.isFinite(move.x()) && Double.isFinite(move.z()), "Unusable input never becomes NaN velocity");
            check(Math.hypot(move.x(), move.z()) <= 1.000001, "Movement direction stays bounded");
        }
        var overlap = HerdRules.steer(origin, List.of(origin), null, east);
        check(overlap.x() > 0 && Double.isFinite(overlap.x()), "Coincident animals get a finite separation direction");
        System.out.println("PASS: herd separation, shared flight, grazing and owner-safe eligibility");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
