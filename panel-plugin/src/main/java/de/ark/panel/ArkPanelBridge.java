package de.ark.panel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.Comparator;
import java.util.Locale;
import java.util.UUID;
import java.lang.reflect.Method;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ArkPanelBridge extends JavaPlugin {
    private CommandDispatcher<Object> dispatcher;
    private Method sourceHandle;
    private final PendingResults pending = new PendingResults(System::nanoTime);

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            pending.clear();
            try {
                // shortcut: Paper26.3's public mirror hides vanilla nodes; recheck this adapter on Paper upgrades.
                dispatcher = (CommandDispatcher<Object>) Class.forName("io.papermc.paper.command.brigadier.ApiMirrorRootNode")
                        .getMethod("getDispatcher").invoke(event.registrar().getDispatcher().getRoot());
                sourceHandle = Class.forName("io.papermc.paper.command.brigadier.PaperCommandSourceStack").getMethod("getHandle");
            } catch (ReflectiveOperationException exception) {
                dispatcher = null;
                getLogger().severe("Native completion adapter unavailable: " + exception.getClass().getSimpleName());
            }
            event.registrar().register(Commands.literal("arkpanel")
                    .requires(ArkPanelBridge::allowed)
                    .executes(context -> respond(context.getSource(), ""))
                    .then(Commands.argument("request", StringArgumentType.greedyString())
                            .executes(context -> respond(context.getSource(), StringArgumentType.getString(context, "request"))))
                    .build(), "Authenticated console panel metadata and completion");
        });
    }

    private static boolean allowed(CommandSourceStack source) {
        var sender = source.getSender();
        return source.getExecutor() == null && (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) && sender.hasPermission("arkpanel.admin");
    }

    @Override
    public void onDisable() { pending.clear(); }

    private int respond(CommandSourceStack source, String request) {
        String response;
        try {
            if (!allowed(source)) throw new IllegalArgumentException("Console administrator required");
            if (request.length() > 2200) throw new IllegalArgumentException("Request too long");
            String[] parts = request.split(" ", 3);
            response = switch (parts[0]) {
                case "settings" -> { if (parts.length != 1) throw new IllegalArgumentException("settings takes no arguments"); yield BridgeProtocol.JSON.toJson(RuntimeSettings.snapshot(getServer())); }
                case "worlds" -> { if (parts.length != 1) throw new IllegalArgumentException("worlds takes no arguments"); yield worlds(); }
                case "players" -> { if (parts.length != 1) throw new IllegalArgumentException("players takes no arguments"); yield players(); }
                case "complete" -> {
                    if (parts.length < 2) throw new IllegalArgumentException("Expected cursor and base64url input");
                    var input = BridgeProtocol.input(Integer.parseInt(parts[1]), parts.length == 3 ? parts[2] : "");
                    if (dispatcher == null) yield BridgeProtocol.error("completion_failed", "Native completion adapter unavailable");
                    if (pending.full()) yield BridgeProtocol.error("busy", "Too many pending requests");
                    var future = dispatcher.getCompletionSuggestions(dispatcher.parse(input.command(), sourceHandle.invoke(source)), input.cursor())
                            .handle((suggestions, error) -> error == null ? BridgeProtocol.suggestions(suggestions, input) : BridgeProtocol.error("completion_failed", "Completion provider failed"));
                    if (future.isDone()) yield future.getNow(BridgeProtocol.error("completion_failed", "Completion unavailable"));
                    UUID id = pending.add(future);
                    yield id == null ? BridgeProtocol.error("busy", "Too many pending requests") : pending.get(id);
                }
                case "result" -> {
                    if (parts.length != 2 || parts[1].length() != 36) throw new IllegalArgumentException("Expected completion UUID");
                    String result = pending.get(UUID.fromString(parts[1]));
                    yield result == null ? BridgeProtocol.error("not_found", "Completion expired or unknown") : result;
                }
                case "pvp" -> {
                    if (parts.length != 3 || !(parts[2].equals("true") || parts[2].equals("false"))) throw new IllegalArgumentException("Expected base64url world key and true/false");
                    NamespacedKey key = NamespacedKey.fromString(BridgeProtocol.decode(parts[1]));
                    if (key == null) throw new IllegalArgumentException("Invalid world key");
                    World world = getServer().getWorld(key);
                    if (world == null) throw new IllegalArgumentException("Unknown world");
                    world.setGameRule(GameRules.PVP, Boolean.parseBoolean(parts[2]));
                    var result = new JsonObject();
                    result.addProperty("world", world.getName());
                    result.addProperty("key", world.getKey().toString());
                    result.addProperty("pvp", world.getGameRuleValue(GameRules.PVP));
                    yield BridgeProtocol.JSON.toJson(result);
                }
                default -> throw new IllegalArgumentException("Expected settings, worlds, players, complete, result or pvp");
            };
        } catch (IllegalArgumentException exception) {
            response = BridgeProtocol.error("invalid_request", exception.getMessage());
        } catch (Exception exception) {
            getLogger().warning("Panel bridge request failed: " + exception.getClass().getSimpleName());
            response = BridgeProtocol.error("completion_failed", "Bridge request failed");
        }
        source.getSender().sendPlainMessage("ARKPANEL " + response);
        return 1;
    }

    private String worlds() {
        var worlds = new JsonArray();
        for (World world : getServer().getWorlds()) {
            var item = new JsonObject();
            item.addProperty("name", world.getName());
            item.addProperty("key", world.getKey().toString());
            item.addProperty("difficulty", world.getDifficulty().name().toLowerCase(Locale.ROOT));
            item.addProperty("pvp", world.getGameRuleValue(GameRules.PVP));
            item.addProperty("time", world.getTime());
            item.addProperty("weather", world.isThundering() ? "thunder" : world.hasStorm() ? "rain" : "clear");
            var spawn = world.getSpawnLocation();
            var position = new JsonObject();
            position.addProperty("x", spawn.getX()); position.addProperty("y", spawn.getY()); position.addProperty("z", spawn.getZ());
            position.addProperty("yaw", spawn.getYaw()); position.addProperty("pitch", spawn.getPitch());
            item.add("spawn", position);
            var border = new JsonObject();
            border.addProperty("size", world.getWorldBorder().getSize());
            border.addProperty("centerX", world.getWorldBorder().getCenter().getX()); border.addProperty("centerZ", world.getWorldBorder().getCenter().getZ());
            item.add("border", border);
            var rules = new JsonArray();
            Registry.GAME_RULE.stream().filter(rule -> world.getFeatureFlags().containsAll(rule.requiredFeatures()))
                    .sorted(Comparator.comparing(rule -> rule.getKey().toString())).forEach(rule -> {
                var value = new JsonObject();
                value.addProperty("name", rule.getKey().toString());
                value.addProperty("type", rule.getType() == Boolean.class ? "boolean" : "integer");
                value.add("value", BridgeProtocol.JSON.toJsonTree(world.getGameRuleValue(rule)));
                value.add("default", BridgeProtocol.JSON.toJsonTree(rule.getDefaultValue()));
                rules.add(value);
            });
            item.add("rules", rules);
            worlds.add(item);
        }
        var result = new JsonObject(); result.add("worlds", worlds);
        return BridgeProtocol.JSON.toJson(result);
    }

    private String players() {
        var players = new JsonArray();
        getServer().getOnlinePlayers().stream().sorted(Comparator.comparing(OfflinePlayer::getName)).forEach(player -> {
            var item = new JsonObject();
            item.addProperty("name", player.getName()); item.addProperty("uuid", player.getUniqueId().toString());
            item.addProperty("world", player.getWorld().getName()); item.addProperty("key", player.getWorld().getKey().toString());
            item.addProperty("gamemode", player.getGameMode().name().toLowerCase(Locale.ROOT));
            item.addProperty("op", player.isOp()); item.addProperty("whitelisted", player.isWhitelisted()); players.add(item);
        });
        var whitelist = new JsonArray(); var banned = new JsonArray();
        getServer().getWhitelistedPlayers().stream().map(OfflinePlayer::getName).filter(java.util.Objects::nonNull).sorted().forEach(whitelist::add);
        getServer().getBannedPlayers().stream().map(OfflinePlayer::getName).filter(java.util.Objects::nonNull).sorted().forEach(banned::add);
        var result = new JsonObject(); result.add("players", players); result.add("whitelist", whitelist); result.add("banned", banned);
        return BridgeProtocol.JSON.toJson(result);
    }
}
