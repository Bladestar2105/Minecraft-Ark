package de.ark.atlas;

final class AtlasRules {
    static final int EXTENT = 10240;
    record Window(double centerX, double centerZ, int span) {
        boolean contains(double x, double z) {
            return Math.abs(x - centerX) <= span / 2.0 && Math.abs(z - centerZ) <= span / 2.0;
        }
    }
    enum Grant { NONE, WAIT, RECORD, GIVE, RECORD_LOCAL, GIVE_LOCAL }
    static Window window(int zoom, double x, double z) {
        if (zoom == 1) return new Window(0, 0, 20480);
        int span = 20480 / zoom;
        double step = span / 128.0;
        double limit = EXTENT - span / 2.0;
        return new Window(clamp(Math.rint(x / step) * step, -limit, limit),
                clamp(Math.rint(z / step) * step, -limit, limit), span);
    }
    static int nextZoom(int zoom) { return zoom == 1 ? 4 : zoom == 4 ? 8 : 1; }
    static int sourceIndex(Window window, int pixel, boolean xAxis, int imageSize) {
        double center = xAxis ? window.centerX() : window.centerZ();
        double coordinate = center - window.span() / 2.0 + (pixel + 0.5) * window.span() / 128.0;
        return (int) clamp(Math.floor((coordinate + EXTENT) * imageSize / 20480.0), 0, imageSize - 1);
    }
    static byte cursor(Window window, double coordinate, boolean xAxis) {
        double center = xAxis ? window.centerX() : window.centerZ();
        return (byte) clamp(Math.floor((coordinate - center) * 256.0 / window.span()), -128, 127);
    }
    static byte direction(float yaw) { return (byte) Math.floorMod(Math.round(yaw * 16 / 360), 16); }
    static boolean canRequest(boolean inArk, boolean survival) { return inArk && survival; }
    static Grant autoGrant(boolean eligible, boolean recorded, boolean hasAtlas, boolean ready, boolean space) {
        if (!eligible || recorded) return Grant.NONE;
        if (hasAtlas) return ready ? Grant.RECORD : Grant.RECORD_LOCAL;
        if (!space) return Grant.WAIT;
        return ready ? Grant.GIVE : Grant.GIVE_LOCAL;
    }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
