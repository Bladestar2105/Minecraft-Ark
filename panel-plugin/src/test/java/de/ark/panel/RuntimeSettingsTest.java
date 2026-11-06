package de.ark.panel;

public final class RuntimeSettingsTest {
    public interface StableRuntimeApi { StableRuntimeApi config(); String title(); }
    public static final class LoadedConfig {
        public int max() { return 0; }
        public boolean enabled() { return false; }
        public String title() { return ""; }
        public String password() { throw new AssertionError("No secret getter may be invoked"); }
        public LoadedConfig config() { return this; }
    }
    public static final class NativeProperties {
        public final boolean enableJmxMonitoring = false;
    }
    public static final class NativeServer {
        public NativeServer getServer() { return this; }
        public long getMaxTickLength() { return 60000L; }
        public int getRateLimitPacketsPerSecond() { return 0; }
        public int getCompressionThreshold() { return -1; }
        public boolean shouldInformAdmins() { return false; }
        public boolean repliesToStatus() { return true; }
        public NativeProperties getProperties() { return new NativeProperties(); }
        public String getRconPassword() { throw new AssertionError("No secret field may be read"); }
    }
    public static void main(String[] args) {
        var optional = RuntimeSettings.readGetters(new OptionalRuntimeProvider(), new String[][] {{"title", "config.title"}});
        if (!optional.has("title") || !optional.get("title").getAsString().equals("Public loaded value"))
            throw new AssertionError("Public interface getter must work without an unrelated optional dependency");
        var values = RuntimeSettings.readGetters(new LoadedConfig(), new String[][] {
            {"limit", "config.max"}, {"flag", "config.enabled"}, {"title", "config.title"},
            {"unsupported", "config.missing"}
        });
        if (values.size() != 3 || values.get("limit").getAsInt() != 0 || values.get("flag").getAsBoolean()
                || !values.get("title").getAsString().isEmpty() || values.has("unsupported"))
            throw new AssertionError("Runtime snapshots must preserve zero/false/empty and omit unavailable getters");
        if (RuntimeSettings.readGetters(null, new String[][] {{"limit", "config.max"}}).size() != 0)
            throw new AssertionError("Unavailable plugin must not invent runtime defaults");
        var nativeValues = RuntimeSettings.nativeSettings(new NativeServer());
        if (nativeValues.size() != 6 || nativeValues.get("rate-limit").getAsInt() != 0
                || nativeValues.get("network-compression-threshold").getAsInt() != -1
                || nativeValues.get("enable-jmx-monitoring").getAsBoolean()
                || nativeValues.has("rcon.password"))
            throw new AssertionError("Pinned native adapter must expose only its six non-secret fields");
        System.out.println("PASS runtime getter boundaries, six-field native allowlist, false/zero/empty and missing plugin");
    }
}
