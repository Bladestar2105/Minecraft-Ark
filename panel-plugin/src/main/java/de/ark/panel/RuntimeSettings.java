package de.ark.panel;

import com.google.gson.JsonObject;
import java.time.Instant;
import java.lang.reflect.Modifier;
import java.util.Locale;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

/** Read-only, explicitly allowlisted live settings. Never serialize a complete config. */
final class RuntimeSettings {
    private static final String[][] GEYSER = {
        {"motd.primary-motd", "config.motd.primaryMotd"},
        {"motd.secondary-motd", "config.motd.secondaryMotd"},
        {"motd.passthrough-motd", "config.motd.passthroughMotd"},
        {"motd.max-players", "config.motd.maxPlayers"},
        {"motd.passthrough-player-counts", "config.motd.passthroughPlayerCounts"},
        {"gameplay.server-name", "config.gameplay.serverName"},
        {"gameplay.command-suggestions", "config.gameplay.commandSuggestions"},
        {"gameplay.show-coordinates", "config.gameplay.showCoordinates"},
        {"gameplay.force-resource-packs", "config.gameplay.forceResourcePacks"},
        {"gameplay.enable-integrated-pack", "config.gameplay.enableIntegratedPack"},
        {"debug-mode", "config.debugMode"}
    };
    private static final String[][] QUESTS = {
        {"max-quests", "getConfigSettings.getMaxQuests"},
        {"language", "getConfigSettings.getLanguage"},
        {"language-override-client", "getConfigSettings.canLanguageOverrideClient"},
        {"clickable-prompts", "getConfigSettings.canClickablePrompts"},
        {"confirm-accept", "getConfigSettings.canConfirmAccept"},
        {"confirm-abandon", "getConfigSettings.canConfirmAbandon"},
        {"accept-timeout", "getConfigSettings.getAcceptTimeout"},
        {"show-completed-objectives", "getConfigSettings.canShowCompletedObjs"},
        {"show-requirements", "getConfigSettings.canShowQuestReqs"},
        {"show-titles", "getConfigSettings.canShowQuestTitles"}
    };
    private static final String[][] NATIVE_SERVER = {
        {"max-tick-time", "getServer.getMaxTickLength"},
        {"rate-limit", "getServer.getRateLimitPacketsPerSecond"},
        {"network-compression-threshold", "getServer.getCompressionThreshold"},
        {"broadcast-console-to-ops", "getServer.shouldInformAdmins"},
        {"enable-status", "getServer.repliesToStatus"}
    };

    private RuntimeSettings() { }

    static JsonObject snapshot(Server server) {
        String observed = Instant.now().toString();
        var settings = new JsonObject();
        settings.addProperty("motd", server.getMotd());
        settings.addProperty("gamemode", server.getDefaultGameMode().name().toLowerCase(Locale.ROOT));
        settings.addProperty("max-players", server.getMaxPlayers());
        settings.addProperty("view-distance", server.getViewDistance());
        settings.addProperty("simulation-distance", server.getSimulationDistance());
        settings.addProperty("white-list", server.hasWhitelist());
        settings.addProperty("allow-flight", server.getAllowFlight());
        settings.addProperty("force-gamemode", server.forcesDefaultGameMode());
        settings.addProperty("enforce-whitelist", server.isWhitelistEnforced());
        settings.addProperty("player-idle-timeout", server.getIdleTimeout());
        settings.addProperty("spawn-protection", server.getSpawnRadius());
        settings.addProperty("pause-when-empty-seconds", server.getPauseWhenEmptyTime());
        settings.addProperty("hide-online-players", server.getHideOnlinePlayers());
        settings.addProperty("log-ips", server.isLoggingIPs());
        settings.addProperty("max-world-size", server.getMaxWorldSize());
        var pack = server.getServerResourcePack();
        settings.addProperty("resource-pack", pack == null ? "" : pack.getUrl());
        settings.addProperty("resource-pack-id", pack == null ? "" : pack.getId().toString());
        settings.addProperty("resource-pack-sha1", pack == null ? "" : pack.getHash());
        settings.addProperty("resource-pack-prompt", pack == null || pack.getPrompt() == null ? "" : pack.getPrompt());
        settings.addProperty("require-resource-pack", pack != null && pack.isRequired());
        nativeSettings(server).entrySet().forEach(entry -> settings.add(entry.getKey(), entry.getValue()));
        var scopes = new JsonObject();
        World primary = server.getWorlds().isEmpty() ? null : server.getWorlds().getFirst();
        if (primary != null) {
            settings.addProperty("difficulty", primary.getDifficulty().name().toLowerCase(Locale.ROOT));
            scopes.addProperty("difficulty", "primary_world");
        }
        var result = new JsonObject();
        result.add("minecraft", component(settings, scopes, observed));
        result.add("geyser", plugin(server.getPluginManager().getPlugin("Geyser-Spigot"), GEYSER, observed));
        result.add("quests", plugin(server.getPluginManager().getPlugin("Quests"), QUESTS, observed));
        return result;
    }

    private static JsonObject plugin(Plugin plugin, String[][] getters, String observed) {
        if (plugin == null || !plugin.isEnabled()) return component(new JsonObject(), new JsonObject(), observed);
        if (plugin.getName().equals("Quests")) {
            try {
                if (Boolean.TRUE.equals(call(plugin, "isLoading"))) return component(new JsonObject(), new JsonObject(), observed);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        }
        return component(readGetters(plugin, getters), new JsonObject(), observed);
    }

    private static JsonObject component(JsonObject settings, JsonObject scopes, String observed) {
        var result = new JsonObject();
        result.addProperty("available", !settings.isEmpty());
        result.addProperty("observed_at", observed);
        result.add("settings", settings);
        result.add("scopes", scopes);
        if (settings.isEmpty()) result.addProperty("message", "Live-Werte derzeit nicht verfügbar.");
        return result;
    }

    static JsonObject nativeSettings(Object server) {
        // shortcut: These six read-only accessors are pinned to Paper26.3; omit them if a later server changes its API.
        var values = readGetters(server, NATIVE_SERVER);
        try {
            Object dedicated = call(server, "getServer");
            Object properties = call(dedicated, "getProperties");
            scalar(values, "enable-jmx-monitoring", properties.getClass().getField("enableJmxMonitoring").get(properties));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) { }
        return values;
    }

    static JsonObject readGetters(Object root, String[][] getters) {
        var values = new JsonObject();
        if (root == null) return values;
        for (String[] getter : getters) {
            try {
                Object value = root;
                for (String method : getter[1].split("\\.")) value = call(value, method);
                scalar(values, getter[0], value);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // A plugin upgrade or reload must not turn file defaults into invented live values.
            }
        }
        return values;
    }

    private static Object call(Object value, String method) throws ReflectiveOperationException {
        // Concrete plugin classes may mention absent optional APIs in unrelated method signatures.
        for (Class<?> api : value.getClass().getInterfaces()) {
            if (!Modifier.isPublic(api.getModifiers())) continue;
            try {
                return api.getMethod(method).invoke(value);
            } catch (NoSuchMethodException ignored) { }
        }
        return value.getClass().getMethod(method).invoke(value);
    }

    private static void scalar(JsonObject values, String key, Object value) {
        if (value instanceof Boolean booleanValue) values.addProperty(key, booleanValue);
        else if (value instanceof Number number && Double.isFinite(number.doubleValue())) values.addProperty(key, number);
        else if (value instanceof String string && string.length() <= 4096) values.addProperty(key, string);
    }
}
