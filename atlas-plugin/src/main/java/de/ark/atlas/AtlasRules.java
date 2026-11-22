package de.ark.atlas;

import java.util.List;

final class AtlasRules {
    static final int EXTENT = 10240;
    static final Layout LEGACY = new Layout(0, 0, EXTENT, List.of(1, 4, 8));
    record Layout(double originX, double originZ, int extent, List<Integer> zooms) {
        Layout {
            if (!Double.isFinite(originX) || !Double.isFinite(originZ) || extent < 64 || extent > 15000000
                    || Math.abs(originX) + extent > 30000000 || Math.abs(originZ) + extent > 30000000
                    || zooms == null || zooms.isEmpty() || zooms.size() > 8 || zooms.getFirst() == null || zooms.getFirst() != 1
                    || zooms.stream().anyMatch(z -> z == null || z < 1 || z > extent / 64 || (2 * extent) % z != 0)
                    || zooms.stream().distinct().count() != zooms.size()) throw new IllegalArgumentException("Invalid atlas origin, extent or zoom-divisors");
            zooms = List.copyOf(zooms);
        }
    }
    record Window(double centerX, double centerZ, int span) {
        boolean contains(double x, double z) {
            return Math.abs(x - centerX) <= span / 2.0 && Math.abs(z - centerZ) <= span / 2.0;
        }
    }
    enum Grant { NONE, WAIT, RECORD, GIVE, RECORD_LOCAL, GIVE_LOCAL }
    static Window window(int zoom, double x, double z) {
        return window(LEGACY, zoom, x, z);
    }
    static Window window(Layout layout, int zoom, double x, double z) {
        if (!layout.zooms().contains(zoom)) zoom = 1;
        if (zoom == 1) return new Window(layout.originX(), layout.originZ(), 2 * layout.extent());
        int span = 2 * layout.extent() / zoom;
        double step = span / 128.0;
        double limit = layout.extent() - span / 2.0;
        return new Window(layout.originX() + clamp(Math.rint((x - layout.originX()) / step) * step, -limit, limit),
                layout.originZ() + clamp(Math.rint((z - layout.originZ()) / step) * step, -limit, limit), span);
    }
    static int nextZoom(int zoom) { return nextZoom(LEGACY, zoom); }
    static int nextZoom(Layout layout, int zoom) { return layout.zooms().get((layout.zooms().indexOf(zoom) + 1) % layout.zooms().size()); }
    static int sourceIndex(Window window, int pixel, boolean xAxis, int imageSize) {
        return sourceIndex(LEGACY, window, pixel, xAxis, imageSize);
    }
    static int sourceIndex(Layout layout, Window window, int pixel, boolean xAxis, int imageSize) {
        double center = xAxis ? window.centerX() : window.centerZ();
        double coordinate = center - window.span() / 2.0 + (pixel + 0.5) * window.span() / 128.0;
        double origin = xAxis ? layout.originX() : layout.originZ();
        return (int) clamp(Math.floor((coordinate - origin + layout.extent()) * imageSize / (2.0 * layout.extent())), 0, imageSize - 1);
    }
    static byte cursor(Window window, double coordinate, boolean xAxis) {
        double center = xAxis ? window.centerX() : window.centerZ();
        return (byte) clamp(Math.floor((coordinate - center) * 256.0 / window.span()), -128, 127);
    }
    static byte direction(float yaw) { return (byte) Math.floorMod(Math.round(yaw * 16 / 360), 16); }
    static boolean canRequest(boolean inArk, boolean survival) { return inArk && survival; }
    static boolean atlasMatches(String itemWorld, String currentWorld) { return (itemWorld == null ? "ark" : itemWorld).equals(currentWorld); }
    static Grant autoGrant(boolean eligible, boolean recorded, boolean hasAtlas, boolean ready, boolean space) {
        if (!eligible || recorded) return Grant.NONE;
        if (hasAtlas) return ready ? Grant.RECORD : Grant.RECORD_LOCAL;
        if (!space) return Grant.WAIT;
        return ready ? Grant.GIVE : Grant.GIVE_LOCAL;
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
