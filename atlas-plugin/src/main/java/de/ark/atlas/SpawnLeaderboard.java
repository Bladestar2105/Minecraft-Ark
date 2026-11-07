package de.ark.atlas;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

final class SpawnLeaderboard implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final NamespacedKey marker;
    private final Map<UUID, String> players = new HashMap<>();
    private final BukkitTask refreshTask;
    private UUID display;
    private boolean closed;

    SpawnLeaderboard(JavaPlugin plugin) {
        this.plugin = plugin;
        marker = new NamespacedKey(plugin, "leaderboard-display");
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.hasPlayedBefore() || player.isOnline()) remember(player);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        removeLoaded();
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, 100);
    }

    private void remember(OfflinePlayer player) {
        String name = player.getName();
        if (name != null && !name.isBlank()) players.put(player.getUniqueId(), name);
    }

    private List<LeaderboardRules.Entry> ranking() {
        Bukkit.getOnlinePlayers().forEach(this::remember);
        Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective("ark_rp");
        Map<String, Integer> points = new HashMap<>();
        if (objective != null) for (String name : players.values()) {
            Score score = objective.getScore(name);
            if (score.isScoreSet()) points.put(name, score.getScore());
        }
        return LeaderboardRules.rank(points);
    }

    private Component table(List<LeaderboardRules.Entry> entries, boolean german, boolean hologram) {
        var text = Component.text().append(Component.text(hologram ? "ARK · TOP 10" : german ? "ARK-Rangliste" : "ARK leaderboard",
                NamedTextColor.GOLD, TextDecoration.BOLD));
        text.appendNewline().append(Component.text(hologram ? "Abenteuerpunkte / Adventure points" : german ? "Abenteuerpunkte" : "Adventure points", NamedTextColor.GRAY));
        if (entries.isEmpty()) text.appendNewline().append(Component.text(hologram ? "Noch keine Einträge / No entries yet" : german ? "Noch keine Einträge." : "No entries yet.", NamedTextColor.GRAY));
        for (int i = 0; i < Math.min(10, entries.size()); i++) {
            LeaderboardRules.Entry entry = entries.get(i);
            NamedTextColor color = i == 0 ? NamedTextColor.GOLD : i == 1 ? NamedTextColor.WHITE : i == 2 ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
            text.appendNewline().append(Component.text((i + 1) + ". ", color))
                    .append(Component.text(entry.name(), NamedTextColor.WHITE))
                    .append(Component.text("   " + entry.points() + " AP", NamedTextColor.GREEN));
        }
        if (hologram) text.appendNewline().appendNewline().append(Component.text("/rangliste · /leaderboard", NamedTextColor.AQUA));
        return text.build();
    }

    void show(CommandSender sender) {
        List<LeaderboardRules.Entry> entries = ranking();
        boolean german = !(sender instanceof Player player) || player.locale().getLanguage().equals(Locale.GERMAN.getLanguage());
        sender.sendMessage(table(entries, german, false));
        if (sender instanceof Player player) for (int i = 0; i < entries.size(); i++) {
            LeaderboardRules.Entry entry = entries.get(i);
            if (entry.name().equalsIgnoreCase(player.getName())) {
                sender.sendMessage(Component.text((german ? "Dein Platz: " : "Your position: ") + (i + 1) + " · " + entry.points() + " AP", NamedTextColor.AQUA));
                break;
            }
        }
    }

    private World world() { return Bukkit.getWorld("ark"); }
    private Location position() { World world = world(); return world == null ? null : world.getSpawnLocation().add(8.5, 4, .5); }
    private boolean owned(Entity entity) { return entity instanceof TextDisplay && Byte.valueOf((byte) 1).equals(entity.getPersistentDataContainer().get(marker, PersistentDataType.BYTE)); }

    private void removeLoaded() {
        World world = world();
        if (world != null) for (TextDisplay entity : world.getEntitiesByClass(TextDisplay.class)) if (owned(entity)) entity.remove();
        display = null;
    }

    private void refresh() {
        if (closed) return;
        Location location = position();
        if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) return;
        Component text = table(ranking(), true, true);
        if (display != null && Bukkit.getEntity(display) instanceof TextDisplay current && current.isValid()) {
            if (!current.getLocation().equals(location)) current.teleport(location);
            if (!current.text().equals(text)) current.text(text);
            return;
        }
        TextDisplay entity = location.getWorld().spawn(location, TextDisplay.class, hologram -> {
            hologram.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
            hologram.text(text);
            hologram.setBillboard(Display.Billboard.CENTER);
            hologram.setAlignment(TextDisplay.TextAlignment.CENTER);
            hologram.setLineWidth(420);
            hologram.setDefaultBackground(false);
            hologram.setBackgroundColor(Color.fromARGB(185, 12, 22, 32));
            hologram.setShadowed(true);
            hologram.setBrightness(new Display.Brightness(15, 15));
            hologram.setGravity(false);
            hologram.setInvulnerable(true);
            hologram.setSilent(true);
            // Recreate from current scores after chunk loads; never save stale holograms.
            hologram.setPersistent(false);
        });
        display = entity.getUniqueId();
    }

    private void checkChunk(org.bukkit.Chunk chunk) {
        Location location = position();
        if (location != null && chunk.getWorld().equals(location.getWorld()) && chunk.getX() == location.getBlockX() >> 4 && chunk.getZ() == location.getBlockZ() >> 4)
            Bukkit.getScheduler().runTask(plugin, this::refresh);
    }

    @EventHandler public void chunkLoaded(ChunkLoadEvent event) { checkChunk(event.getChunk()); }
    @EventHandler public void playerJoined(PlayerJoinEvent event) { remember(event.getPlayer()); }
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) if (owned(entity) && !entity.getUniqueId().equals(display)) entity.remove();
        checkChunk(event.getChunk());
    }
    @EventHandler public void worldLoaded(WorldLoadEvent event) {
        if (event.getWorld().equals(world())) Bukkit.getScheduler().runTask(plugin, this::refresh);
    }
    @EventHandler public void spawnChanged(SpawnChangeEvent event) {
        if (event.getWorld().equals(world())) { removeLoaded(); Bukkit.getScheduler().runTask(plugin, this::refresh); }
    }

    @Override public void close() {
        closed = true;
        refreshTask.cancel();
        HandlerList.unregisterAll(this);
        removeLoaded();
    }
}
