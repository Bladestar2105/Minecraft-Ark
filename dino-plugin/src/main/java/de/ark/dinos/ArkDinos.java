package de.ark.dinos;

import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public final class ArkDinos extends JavaPlugin implements Listener {
    private final Map<UUID, Mob> loaded = new HashMap<>();
    private final Map<UUID, Mob> riding = new HashMap<>();
    private final TamingRules rules = new TamingRules();
    private NamespacedKey ownerKey;
    private NamespacedKey stayKey;
    private List<String> survivalWorlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        survivalWorlds = getConfig().getStringList("survival-worlds");
        ownerKey = new NamespacedKey(this, "owner");
        stayKey = new NamespacedKey(this, "stay");
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getWorlds().forEach(world -> world.getEntities().forEach(this::restore));
        getServer().getScheduler().runTaskTimer(this, this::follow, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::ride, 1L, 1L);
    }

    @Override
    public void onDisable() {
        loaded.values().forEach(this::endRide);
        riding.clear();
        loaded.clear();
    }

    private boolean supported(Entity entity) {
        return entity instanceof Sniffer || entity instanceof Ravager;
    }

    private boolean owned(Entity entity) {
        return supported(entity) && entity.getPersistentDataContainer().has(ownerKey);
    }

    private String owner(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.has(ownerKey, PersistentDataType.STRING) ? data.get(ownerKey, PersistentDataType.STRING) : "";
    }

    private boolean staying(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.has(stayKey, PersistentDataType.BYTE) && data.get(stayKey, PersistentDataType.BYTE) != 0;
    }

    private boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL && survivalWorlds.contains(player.getWorld().getName());
    }

    private void stop(Mob mob) {
        mob.getPathfinder().stopPathfinding();
        mob.setAware(false);
    }

    private boolean prepare(Mob mob) {
        stop(mob);
        try {
            if (mob instanceof Sniffer sniffer) {
                // shortcut: Sniffer's Brain has no Paper controller; recheck these 26.3 methods on Minecraft upgrades.
                Object handle = sniffer.getClass().getMethod("getHandle").invoke(sniffer);
                Object brain = handle.getClass().getMethod("getBrain").invoke(handle);
                var remove = brain.getClass().getMethod("removeAllBehaviors");
                var clear = brain.getClass().getMethod("clearMemories");
                remove.invoke(brain);
                clear.invoke(brain);
                sniffer.setState(Sniffer.State.IDLING);
            }
            var goals = getServer().getMobGoals();
            goals.removeAllGoals(mob, GoalType.MOVE);
            goals.removeAllGoals(mob, GoalType.LOOK);
            goals.removeAllGoals(mob, GoalType.TARGET);
            mob.setTarget(null);
            if (mob instanceof Ravager ravager) {
                ravager.setAttackTicks(-1);
                ravager.setStunnedTicks(-1);
                ravager.setRoarTicks(-1);
                ravager.setCanJoinRaid(false);
            }
            mob.setAI(true);
            mob.setPersistent(true);
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.setDespawnInPeacefulOverride(TriState.FALSE);
            return true;
        } catch (ReflectiveOperationException exception) {
            getLogger().severe("Sniffer controller unavailable; dino stopped safely, seed retained: " + exception);
            return false;
        }
    }

    private void restore(Entity entity) {
        if (owned(entity)) {
            endRide((Mob) entity);
            if (prepare((Mob) entity)) loaded.put(entity.getUniqueId(), (Mob) entity);
        }
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::restore);
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        event.getEntities().forEach(entity -> {
            Mob mob = loaded.remove(entity.getUniqueId());
            if (mob != null) endRide(mob);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (!supported(entity) || (!owned(entity) && !entity.getScoreboardTags().contains("ark_dino"))) return;
        if (event.getHand() != EquipmentSlot.HAND) {
            if (owned(entity)) event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();
        if (owned(entity) && player.isInsideVehicle()) {
            event.setCancelled(true);
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        boolean seed = item.getType() == Material.TORCHFLOWER_SEEDS && item.getAmount() > 0;
        var action = rules.interact(owned(entity) ? owner(entity) : null, player.getUniqueId().toString(),
                survival(player), seed, player.isSneaking(), staying(entity));
        if (owned(entity) || action != TamingRules.Action.IGNORE) event.setCancelled(true);
        if (action == TamingRules.Action.DENY) {
            player.sendActionBar(Component.text("Nur der Besitzer kann sein Urtier im Survival steuern."));
        } else if (action == TamingRules.Action.TAME) {
            Mob mob = (Mob) entity;
            if (!prepare(mob)) {
                player.sendActionBar(Component.text("Zähmen gerade nicht möglich. Bitte Admin informieren."));
                return;
            }
            entity.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            entity.getPersistentDataContainer().set(stayKey, PersistentDataType.BYTE, (byte) 0);
            entity.removeScoreboardTag("ark_dino_wild");
            entity.addScoreboardTag("ark_dino_tamed");
            loaded.put(entity.getUniqueId(), mob);
            if (action.consumesSeed()) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(item);
            }
            entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 2, 0), 8, 0.5, 0.5, 0.5);
            player.sendActionBar(Component.text("Gezähmt! Schleichen + Interagieren: Bleiben / Folgen."));
        } else if (action == TamingRules.Action.STAY || action == TamingRules.Action.FOLLOW) {
            boolean stay = action == TamingRules.Action.STAY;
            entity.getPersistentDataContainer().set(stayKey, PersistentDataType.BYTE, (byte) (stay ? 1 : 0));
            stop((Mob) entity);
            player.sendActionBar(Component.text(stay ? "Dein Urtier bleibt hier." : "Dein Urtier folgt dir."));
        } else if (loaded.containsKey(entity.getUniqueId()) && entity.getScoreboardTags().contains("ark_dino_tamed")
                && RidingRules.canMount(owned(entity) ? owner(entity) : null, player.getUniqueId().toString(),
                survival(player), item.getType().isAir(), player.isSneaking(), !entity.getPassengers().isEmpty(), player.isInsideVehicle())) {
            Mob mob = (Mob) entity;
            stop(mob);
            riding.put(mob.getUniqueId(), mob);
            if (!mob.addPassenger(player)) endRide(mob);
        }
    }

    private void endRide(Mob mob) {
        riding.remove(mob.getUniqueId());
        mob.eject();
        stop(mob);
        var velocity = mob.getVelocity();
        mob.setVelocity(new Vector(0, velocity.getY(), 0));
    }

    private void endRide(Player player) {
        if (player.getVehicle() instanceof Mob mob && owned(mob)) endRide(mob);
    }

    private void ride() {
        for (Mob mob : List.copyOf(riding.values())) {
            var passengers = mob.getPassengers();
            if (!mob.isValid() || mob.isDead() || passengers.size() != 1 || !(passengers.getFirst() instanceof Player player)
                    || !player.isOnline() || player.isDead() || !owner(mob).equals(player.getUniqueId().toString())
                    || !survival(player) || !player.getWorld().equals(mob.getWorld()) || player.getCurrentInput().isSneak()) {
                endRide(mob);
                continue;
            }
            stop(mob);
            var input = player.getCurrentInput();
            var motion = RidingRules.movement(player.getYaw(), input.isForward(), input.isBackward(), input.isLeft(), input.isRight());
            var location = mob.getLocation();
            // Never step into an unloaded chunk; native collisions and gravity still apply.
            int chunkX = (int) Math.floor(location.getX() + motion.x() + Math.signum(motion.x()) * mob.getWidth()) >> 4;
            int chunkZ = (int) Math.floor(location.getZ() + motion.z() + Math.signum(motion.z()) * mob.getWidth()) >> 4;
            var velocity = mob.getVelocity();
            mob.setVelocity(mob.getWorld().isChunkLoaded(chunkX, chunkZ)
                    ? new Vector(motion.x(), velocity.getY(), motion.z()) : new Vector(0, velocity.getY(), 0));
            mob.setRotation(player.getYaw(), 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (owned(event.getMount()) && (riding.get(event.getMount().getUniqueId()) != event.getMount()
                || !(event.getEntity() instanceof Player player) || !owner(event.getMount()).equals(player.getUniqueId().toString())
                || !survival(player) || !event.getMount().getPassengers().isEmpty())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Mob mob && riding.remove(mob.getUniqueId()) != null) {
            stop(mob);
            mob.setVelocity(new Vector(0, mob.getVelocity().getY(), 0));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) { endRide(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) { endRide(event.getPlayer()); }

    @EventHandler
    public void world(PlayerChangedWorldEvent event) { endRide(event.getPlayer()); }

    @EventHandler
    public void death(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player player) endRide(player);
        Mob mob = loaded.remove(event.getEntity().getUniqueId());
        if (mob != null) endRide(mob);
    }

    private void follow() {
        var iterator = loaded.values().iterator();
        while (iterator.hasNext()) {
            Mob mob = iterator.next();
            if (!mob.isValid() || mob.isDead()) {
                iterator.remove();
                continue;
            }
            mob.setTarget(null);
            if (riding.containsKey(mob.getUniqueId())) continue;
            Player player;
            try {
                player = getServer().getPlayer(UUID.fromString(owner(mob)));
            } catch (IllegalArgumentException exception) {
                stop(mob);
                continue;
            }
            if (staying(mob) || player == null || !survival(player) || !player.getWorld().equals(mob.getWorld())) {
                stop(mob);
                continue;
            }
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance <= 9 || distance > 48 * 48) {
                stop(mob);
                continue;
            }
            mob.setAware(true);
            mob.getPathfinder().moveTo(player, 1.15);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent event) {
        if (owned(event.getEntity()) && event.getTarget() != null) event.setTarget(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        if (owned(event.getDamager())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void portal(EntityPortalEvent event) {
        if (owned(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleport(EntityTeleportEvent event) {
        if (owned(event.getEntity()) && event.getTo() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void leash(PlayerLeashEntityEvent event) {
        if (owned(event.getEntity()) && !owner(event.getEntity()).equals(event.getPlayer().getUniqueId().toString())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void unleash(PlayerUnleashEntityEvent event) {
        if (owned(event.getEntity()) && !owner(event.getEntity()).equals(event.getPlayer().getUniqueId().toString())) event.setCancelled(true);
    }
}
