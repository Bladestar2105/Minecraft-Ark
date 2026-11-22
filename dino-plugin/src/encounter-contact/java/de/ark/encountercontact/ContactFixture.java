package de.ark.encountercontact;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable stationary contact test; never use as natural-chase evidence. */
public final class ContactFixture extends JavaPlugin implements Listener {
    private final Gson json = new Gson();
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final Map<EntityDamageByEntityEvent, Map<String, Object>> pending = new IdentityHashMap<>();
    private final Set<Chunk> tickets = new LinkedHashSet<>();
    private final List<Mob> stationary = new ArrayList<>();
    private JavaPlugin dinos;
    private Mob rex, prey, other;
    private boolean cancel = true;
    private String stage = "setup";

    @Override public void onEnable() {
        dinos = (JavaPlugin) Bukkit.getPluginManager().getPlugin("ArkDinos");
        Bukkit.getWorld("jurassic_archipelago").setGameRule(org.bukkit.GameRules.SPAWN_MOBS, false);
        Bukkit.getPluginManager().registerEvents(this, this);
        // Older control JARs re-enable AI in prepare(). Keep this contact-only
        // freeze stable after their controller ticks, without moving any actor.
        Bukkit.getScheduler().runTaskTimer(this, () -> stationary.stream().filter(Entity::isValid)
                .forEach(actor -> actor.setAI(false)), 1, 1);
    }
    private Object field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    private Object call(String name, Class<?>[] types, Object... values) throws ReflectiveOperationException {
        Method method = dinos.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(dinos, values);
    }
    private boolean permits(Mob attacker, Entity victim) throws ReflectiveOperationException {
        try {
            return (boolean) call("sceneDamage", new Class<?>[]{Mob.class, Entity.class}, attacker, victim);
        } catch (NoSuchMethodException baseline) {
            // A previous JAR without the in-flight guard is a useful RED control.
            return false;
        }
    }
    private boolean owned(Entity mob) throws ReflectiveOperationException {
        return (boolean) call("owned", new Class<?>[]{Entity.class}, mob);
    }
    private World world() { return Bukkit.getWorld("jurassic_archipelago"); }
    private void anchor(int x, int z) {
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            Chunk chunk = world().getChunkAt((x >> 4) + dx, (z >> 4) + dz);
            if (tickets.add(chunk)) chunk.addPluginChunkTicket(this);
        }
    }
    private boolean selected(EntityDamageByEntityEvent event) {
        return event.getDamager().equals(rex) && (event.getEntity().equals(prey) || event.getEntity().equals(other));
    }
    @EventHandler(priority = EventPriority.LOWEST) public void before(EntityDamageByEntityEvent event) {
        if (!selected(event)) return;
        try {
            var row = new LinkedHashMap<String, Object>();
            row.put("stage", stage);
            row.put("server_tick", Bukkit.getCurrentTick());
            row.put("scene_ticks", field(field(dinos, "encounter"), "ticks"));
            row.put("scene_phase", field(field(dinos, "encounter"), "phase").toString());
            row.put("attacker", event.getDamager().getUniqueId().toString());
            row.put("victim", event.getEntity().getUniqueId().toString());
            row.put("owned_victim", owned(event.getEntity()));
            row.put("in_flight_permission", permits(rex, event.getEntity()));
            row.put("other_target_permission", permits(rex, event.getEntity().equals(prey) ? other : prey));
            row.put("reverse_permission", permits(prey, rex));
            row.put("distance_squared", rex.getLocation().distanceSquared(event.getEntity().getLocation()));
            row.put("line_of_sight", rex.hasLineOfSight(event.getEntity()));
            row.put("scheduler_scene_stack", StackWalker.getInstance().walk(frames -> frames.anyMatch(frame ->
                    frame.getClassName().equals("de.ark.dinos.DinoEncounter") && frame.getMethodName().equals("tick"))));
            row.put("health_before", ((Mob) event.getEntity()).getHealth());
            row.put("cancelled_before_fixture", event.isCancelled());
            pending.put(event, row);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @EventHandler(priority = EventPriority.HIGH) public void externalCancellation(EntityDamageByEntityEvent event) {
        if (selected(event) && cancel) {
            Map<String, Object> row = pending.get(event);
            if (row != null) row.put("external_cancelled", true);
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void after(EntityDamageByEntityEvent event) {
        Map<String, Object> row = pending.remove(event);
        if (row == null) return;
        row.putIfAbsent("external_cancelled", false);
        row.put("cancelled_monitor", event.isCancelled());
        row.put("final_damage", event.getFinalDamage());
        events.add(row);
    }
    @SuppressWarnings("unchecked") private void setup() throws ReflectiveOperationException {
        Object encounter = field(dinos, "encounter");
        if (!field(encounter, "phase").toString().equals("GRAZING")) throw new IllegalStateException("Contact setup requires GRAZING");
        List<UUID> actors = (List<UUID>) field(encounter, "actors");
        if (actors.size() != 9) throw new IllegalStateException("Require exact native nine-actor scene");
        prey = (Mob) Bukkit.getEntity(actors.getFirst());
        other = (Mob) Bukkit.getEntity(actors.get(1));
        rex = (Mob) Bukkit.getEntity(actors.getLast());
        for (UUID id : actors) {
            Mob actor = (Mob) Bukkit.getEntity(id);
            stationary.add(actor);
            actor.getPathfinder().stopPathfinding();
            actor.setAI(false);
            actor.setCollidable(false);
        }
        // Only SETUP changes positions. The production scheduler owns phase/attack.
        rex.teleport(new Location(rex.getWorld(), 8192.5, 80, 3072.5, -90, 0));
        prey.teleport(new Location(prey.getWorld(), 8194.5, 80, 3072.5, 90, 0));
        stage = "external_cancel";
    }
    private Map<String, Object> status() throws ReflectiveOperationException {
        var row = new LinkedHashMap<String, Object>();
        Object encounter = field(dinos, "encounter");
        row.put("phase", field(encounter, "phase").toString());
        row.put("scene_ticks", field(encounter, "ticks"));
        row.put("stage", stage);
        row.put("events", events);
        row.put("actor_ids", field(encounter, "actors"));
        var ticketCounts = new LinkedHashMap<String, Integer>();
        world().getPluginChunkTickets().forEach((plugin, chunks) -> ticketCounts.put(plugin.getName(), chunks.size()));
        row.put("plugin_tickets", ticketCounts);
        row.put("forced_chunks", world().getForceLoadedChunks().size());
        row.put("spectators", world().getPlayers().stream().map(player -> Map.of("mode", player.getGameMode().name())).toList());
        row.put("natural_mob_spawning", world().getGameRuleValue(org.bukkit.GameRules.SPAWN_MOBS));
        var entities = new ArrayList<Map<String, Object>>();
        for (Entity entity : world().getEntities()) if (entity instanceof Mob && entity.getScoreboardTags().contains("ark_dino")) {
            var current = new LinkedHashMap<String, Object>();
            current.put("uuid", entity.getUniqueId().toString());
            current.put("persistent", entity.isPersistent());
            current.put("owner", call("owner", new Class<?>[]{Entity.class}, entity));
            entities.add(current);
        }
        row.put("entities", entities);
        if (prey != null) {
            row.put("prey_uuid", prey.getUniqueId().toString());
            row.put("rex_uuid", rex.getUniqueId().toString());
            row.put("health", prey.isDead() ? 0 : prey.getHealth());
            row.put("owned", owned(prey));
            try {
                row.put("scene_actor", call("sceneActor", new Class<?>[]{Entity.class}, prey));
            } catch (NoSuchMethodException baseline) {
                // Older RED-control JARs predate this observation method.
                row.put("scene_actor", null);
            }
            row.put("outside_permission", permits(rex, prey));
            row.put("distance_squared", rex.getLocation().distanceSquared(prey.getLocation()));
            row.put("prey_ai", prey.hasAI());
            row.put("rex_ai", rex.hasAI());
        }
        return row;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            switch (args[0]) {
                case "load" -> {
                    for (int i = 0; i < 8; i++) anchor(8210 + i % 4 * 24, 3020 + i / 4 * 26);
                    anchor(8140, 2960); anchor(8192, 3072);
                }
                case "join" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    if (player == null) throw new IllegalStateException("Protocol viewer has not joined");
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setInvulnerable(true);
                    player.teleport(world().getSpawnLocation().add(.5, 0, .5));
                }
                case "setup" -> setup();
                case "allow" -> { cancel = false; stage = "scheduled_allow"; }
                case "adopt" -> {
                    Player player = Bukkit.getPlayerExact(args[1]);
                    if (player == null || !(boolean) call("tame", new Class<?>[]{Mob.class, Player.class}, prey, player))
                        throw new IllegalStateException("Native tame() setup refused");
                    call("controlMode", new Class<?>[]{Mob.class, boolean.class}, prey, true);
                    stage = "owned";
                }
                case "probe" -> {
                    stage = args[1].equals("owned") ? "negative_owned_probe" : "negative_other_probe";
                    Mob victim = args[1].equals("owned") ? prey : other;
                    victim.setNoDamageTicks(0);
                    // Negative guard-leak control only; never counted as a successful scene strike.
                    victim.damage(1, rex);
                }
                case "release" -> { tickets.forEach(chunk -> chunk.removePluginChunkTicket(this)); tickets.clear(); }
                case "status" -> { }
                default -> throw new IllegalArgumentException("Unknown contact fixture operation");
            }
            sender.sendMessage(json.toJson(status()));
        } catch (Exception error) { sender.sendMessage("FIXTURE_ERROR: " + error); error.printStackTrace(); }
        return true;
    }
    @Override public void onDisable() { tickets.forEach(chunk -> chunk.removePluginChunkTicket(this)); tickets.clear(); }
}
