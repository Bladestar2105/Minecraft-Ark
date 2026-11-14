package de.ark.dinos;

import com.destroystokyo.paper.entity.ai.GoalType;
import java.util.ArrayList;
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
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private final Map<UUID, Double> flying = new HashMap<>();
    private final Map<UUID, Mob> landing = new HashMap<>();
    private DinoModels models;
    private DinoCare care;
    private DinoControls controls;
    private DinoCombat combat;
    private DinoAquatic aquatic;
    private DinoPtera ptera;
    private DinoSwimming swimming;
    private DinoSeats seats;
    private DinoWildSpawns wildSpawns;
    private NamespacedKey speciesKey;
    private NamespacedKey flightKey;
    private boolean stopUnavailable;
    private final TamingRules rules = new TamingRules();
    private NamespacedKey ownerKey;
    private NamespacedKey stayKey;
    private NamespacedKey publicRideKey;
    private List<String> survivalWorlds;
    private List<String> prototypeWorlds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        survivalWorlds = getConfig().getStringList("survival-worlds");
        prototypeWorlds = getConfig().contains("prototype-worlds")
                ? getConfig().getStringList("prototype-worlds") : survivalWorlds;
        ownerKey = new NamespacedKey(this, "owner");
        stayKey = new NamespacedKey(this, "stay");
        publicRideKey = new NamespacedKey(this, "public-ride");
        speciesKey = new NamespacedKey(this, "species");
        flightKey = new NamespacedKey(this, "flight");
        models = new DinoModels(this);
        care = new DinoCare(this);
        combat = new DinoCombat(this);
        aquatic = new DinoAquatic(this);
        controls = new DinoControls(this);
        ptera = new DinoPtera(this);
        swimming = new DinoSwimming(this);
        seats = new DinoSeats(this);
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(care, this);
        getServer().getPluginManager().registerEvents(seats, this);
        getServer().getWorlds().forEach(world -> world.getEntities().forEach(this::restore));
        wildSpawns = new DinoWildSpawns(this);
        getServer().getScheduler().runTaskTimer(this, this::follow, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, this::ride, 1L, 1L);
    }

    @Override
    public void onDisable() {
        List.copyOf(loaded.values()).forEach(this::endRide);
        List.copyOf(landing.values()).forEach(this::restoreGravity);
        if (combat != null) combat.close();
        if (aquatic != null) aquatic.close();
        if (ptera != null) ptera.close();
        if (swimming != null) swimming.close();
        if (seats != null) seats.close();
        if (wildSpawns != null) wildSpawns.close();
        flying.clear();
        landing.clear();
        if (models != null) models.close();
        if (care != null) care.close();
        if (controls != null) controls.close();
        riding.clear();
        loaded.clear();
    }

    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                       String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dinoeier")) return false;
        if (sender instanceof Player player) return care.command(player);
        sender.sendMessage("Dino-Eier bitte im Kreativmodus mit /dinoeier auswählen.");
        return true;
    }

    private boolean supported(Entity entity) {
        return entity instanceof Sniffer || entity instanceof Ravager;
    }

    private boolean aquatic(Entity entity) {
        return entity instanceof Dolphin && "mosasaurus".equals(species(entity))
                && entity.getScoreboardTags().contains("ark_dino");
    }

    boolean owned(Entity entity) {
        return supported(entity) && entity.getPersistentDataContainer().has(ownerKey);
    }

    String owner(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.has(ownerKey, PersistentDataType.STRING) ? data.get(ownerKey, PersistentDataType.STRING) : "";
    }

    boolean publicRide(Entity entity) {
        return owned(entity) && entity.getPersistentDataContainer()
                .getOrDefault(publicRideKey, PersistentDataType.BYTE, (byte) 0) == 1;
    }

    void publicRide(Mob mob, boolean shared) {
        mob.getPersistentDataContainer().set(publicRideKey, PersistentDataType.BYTE, (byte) (shared ? 1 : 0));
        if (riding.containsKey(mob.getUniqueId()) && driver(mob) == null) endRide(mob);
        else if (seats != null) {
            Player guest = seats.passenger(mob);
            if (guest != null && !canRide(mob, guest)) seats.remove(mob);
        }
    }

    boolean canRide(Mob mob, Player player) {
        return owned(mob) && mob.isValid() && !mob.isDead() && adult(mob)
                && player.isOnline() && !player.isDead() && mob.getWorld().equals(player.getWorld())
                && RidingRules.canRide(owner(mob), player.getUniqueId().toString(), publicRide(mob), survival(player));
    }

    Player driver(Mob mob) {
        if (riding.get(mob.getUniqueId()) != mob) return null;
        var passengers = mob.getPassengers();
        return passengers.size() == 1 && passengers.getFirst() instanceof Player player
                && canRide(mob, player) && !player.getCurrentInput().isSneak() ? player : null;
    }

    /** All riders, including the independent passenger vehicle. The native passenger remains the driver. */
    List<Entity> passengers(Mob mob) {
        var result = new ArrayList<>(mob.getPassengers());
        if (seats != null) result.addAll(seats.riders(mob));
        return result;
    }

    void extraSeat(Mob mob, Entity seat, double rearDistance, double relativeSeatY) {
        models.seat(mob, seat, rearDistance, relativeSeatY);
    }

    void clearExtraSeat(Mob mob) { models.clearSeat(mob); }

    boolean staying(Entity entity) {
        var data = entity.getPersistentDataContainer();
        return data.has(stayKey, PersistentDataType.BYTE) && data.get(stayKey, PersistentDataType.BYTE) != 0;
    }

    boolean survival(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL && survivalWorlds.contains(player.getWorld().getName());
    }

    boolean prototype(Entity entity) {
        return prototype(entity.getWorld());
    }

    boolean prototype(org.bukkit.World world) {
        return prototypeWorlds.contains(world.getName());
    }

    java.util.Collection<Mob> companions() {
        return List.copyOf(loaded.values());
    }

    boolean adult(Entity entity) {
        return care.adult(entity);
    }

    Entity combatTarget(Entity target) { return models.resolveTarget(target); }

    void attackAnimation(Mob mob, boolean spit) {
        models.attack(mob, spit);
    }

    void animation(Mob mob, String phase) {
        models.animation(mob, phase);
    }

    void stop(Mob mob) {
        double vertical = mob.getVelocity().getY();
        mob.getPathfinder().stopPathfinding();
        mob.setTarget(null);
        try {
            // Paper awareness skips MoveControl; clear its retained movement input before disabling it.
            Object handle = mob.getClass().getMethod("getHandle").invoke(mob);
            handle.getClass().getMethod("stopInPlace").invoke(handle);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            mob.setAI(false);
            if (!stopUnavailable) { getLogger().severe("Dino movement controller unavailable; movement disabled: " + error); stopUnavailable = true; }
        }
        mob.setAware(false);
        mob.setVelocity(new Vector(0, vertical, 0));
    }

    boolean formerOwner(Entity entity, Entity target) { return controls.formerOwner(entity, target); }

    void wanderWild(Mob mob) { controls.wander(mob); }

    boolean roaming(Mob mob) { return controls.roaming(mob); }
    boolean aerial(Mob mob) { return ptera!=null&&ptera.controls(mob); }
    boolean roamPtera(Mob mob) { return ptera!=null&&ptera.roam(mob); }
    void landPtera(Mob mob) { if(ptera!=null)ptera.land(mob); }
    boolean combatActive(Mob mob) { return combat.active(mob); }
    double groundSpeed(Mob mob,boolean running) {
        var pace=prototype(mob)?GroundSpeeds.pace(species(mob)):null;
        return pace==null?(running?1.15:.85):GroundSpeeds.pathMultiplier(pace,running,mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getBaseValue());
    }
    double ridingSpeed(Mob mob,boolean sprint) {
        var pace=prototype(mob)?GroundSpeeds.pace(species(mob)):null;
        return pace==null?(sprint?.34:.22):pace.speed(sprint)/20;
    }
    void aerialVelocity(Mob mob,Vector motion) { flightVelocity(mob,motion); }
    void recoverAerial(Mob mob) { protectFall(mob);restoreGravity(mob); }
    boolean takeAerialRide(Mob mob) {
        if(!riding.containsKey(mob.getUniqueId()))return false;
        flying.put(mob.getUniqueId(),Math.min(mob.getY()+48,mob.getWorld().getMaxHeight()-mob.getHeight()-3));
        mob.getPersistentDataContainer().set(flightKey,PersistentDataType.BYTE,(byte)1);return true;
    }

    void controlMode(Mob mob, boolean stay) {
        mob.getPersistentDataContainer().set(stayKey, PersistentDataType.BYTE, (byte) (stay ? 1 : 0));
        stop(mob); combat.cancel(mob);
        landPtera(mob);
    }

    boolean release(Mob mob) {
        String previousOwner = owner(mob);
        if (!owned(mob) || !adult(mob) || !passengers(mob).isEmpty()) return false;
        endRide(mob); combat.cancel(mob);
        if (mob.getPersistentDataContainer().has(flightKey)) { protectFall(mob); restoreGravity(mob); }
        if (!prepare(mob)) return false;
        controls.released(mob, previousOwner);
        mob.getPersistentDataContainer().remove(ownerKey);
        mob.getPersistentDataContainer().remove(stayKey);
        mob.getPersistentDataContainer().remove(publicRideKey);
        mob.removeScoreboardTag("ark_dino_tamed"); mob.addScoreboardTag("ark_dino_wild");
        loaded.remove(mob.getUniqueId());
        combat.restore(mob);
        return true;
    }

    boolean prepare(Mob mob) {
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
            mob.setPersistent(!mob.getScoreboardTags().contains(DinoWildSpawns.NATURAL_TAG));
            mob.setRemoveWhenFarAway(false);
            mob.setCanPickupItems(false);
            mob.setDespawnInPeacefulOverride(TriState.FALSE);
            return true;
        } catch (ReflectiveOperationException exception) {
            getLogger().severe("Sniffer controller unavailable; dino stopped safely, seed retained: " + exception);
            return false;
        }
    }

    String species(Entity entity) {
        return entity.getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING);
    }

    Mob spawnWild(org.bukkit.Location point, String species) {
        if (!prototype(point.getWorld()) || !CareRules.SPECIES.contains(species) && !"mosasaurus".equals(species)) return null;
        Class<? extends Mob> carrier = switch (species) {
            case "trex", "velociraptor", "dilophosaurus" -> Ravager.class;
            case "mosasaurus" -> Dolphin.class;
            default -> Sniffer.class;
        };
        Mob mob = point.getWorld().spawn(point, carrier, CreatureSpawnEvent.SpawnReason.NATURAL, false, entity -> {
            entity.addScoreboardTag("ark_dino");
            entity.addScoreboardTag("ark_dino_wild");
            entity.addScoreboardTag(DinoWildSpawns.NATURAL_TAG);
            entity.getPersistentDataContainer().set(speciesKey, PersistentDataType.STRING, species);
            entity.setPersistent(false);
            entity.setRemoveWhenFarAway(false);
        });
        if (!mob.isValid()) return null; // A protection plugin may cancel the native spawn event.
        restore(mob);
        return mob;
    }

    private void identify(Entity entity) {
        if (!(supported(entity) || entity instanceof Dolphin) || !prototype(entity)) return;
        var result = SpeciesRules.resolve(species(entity), entity.getScoreboardTags(),
                entity.getPersistentDataContainer().has(ownerKey), entity instanceof Ravager, entity instanceof Dolphin);
        if (result.species() == null) return;
        if (result.conflict()) getLogger().warning("Conflicting habitat tags for " + entity.getUniqueId() + "; using " + result.species());
        entity.getPersistentDataContainer().set(speciesKey, PersistentDataType.STRING, result.species());
        models.attach((Mob) entity, "ark_" + result.species());
    }

    void restore(Entity entity) {
        if (seats != null) seats.restore(entity);
        care.restore(entity);
        if (entity instanceof Dolphin dolphin) aquatic.restore(dolphin);
        identify(entity);
        if(ptera!=null)ptera.restore(entity);
        if (owned(entity)) {
            Mob mob = (Mob) entity;
            endRide(mob);
            if (mob.getPersistentDataContainer().has(flightKey) || prototype(mob) && "pteranodon".equals(species(mob)) && !mob.hasGravity()) {
                protectFall(mob);
                restoreGravity(mob);
            }
            if (prepare(mob)) loaded.put(entity.getUniqueId(), mob);
        }
        if (entity instanceof Mob mob) combat.restore(mob);
        if (swimming != null) swimming.restore(entity);
    }

    boolean tame(Mob mob, Player player) {
        if (!supported(mob) || owned(mob) || !survival(player) || !prepare(mob)) return false;
        combat.cancel(mob);
        controls.clearMode(mob);
        DinoWildSpawns.preserve(mob);
        mob.getPersistentDataContainer().remove(publicRideKey);
        mob.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        mob.getPersistentDataContainer().set(stayKey, PersistentDataType.BYTE, (byte) 0);
        mob.removeScoreboardTag("ark_dino_wild");
        mob.addScoreboardTag("ark_dino_tamed");
        loaded.put(mob.getUniqueId(), mob);
        identify(mob);
        combat.restore(mob);
        mob.getWorld().spawnParticle(Particle.HEART, mob.getLocation().add(0, 2, 0), 8, .5, .5, .5);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void spawn(CreatureSpawnEvent event) {
        if (!(supported(event.getEntity()) || event.getEntity() instanceof Dolphin)) return;
        // Summon tags/PDC are available after the native spawn event completes.
        getServer().getScheduler().runTask(this, () -> { if (event.getEntity().isValid()) restore(event.getEntity()); });
    }

    @EventHandler
    public void load(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::restore);
    }

    @EventHandler
    public void unload(EntitiesUnloadEvent event) {
        event.getEntities().forEach(entity -> {
            Mob mob = loaded.remove(entity.getUniqueId());
            if (mob != null) { endRide(mob); if (flying.containsKey(mob.getUniqueId())) restoreGravity(mob); }
            models.unload(entity);
            if (swimming != null) swimming.unload(entity);
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        if ((aquatic(entity) || supported(entity) && (owned(entity) || entity.getScoreboardTags().contains("ark_dino")))
                && player.getInventory().getItem(event.getHand()).getType() == Material.NAME_TAG) {
            // Native naming must run before feeding, staying or mounting can consume this interaction.
            if (owned(entity) && !owner(entity).equals(player.getUniqueId().toString())) {
                event.setCancelled(true);
                player.sendActionBar(Component.text("Nur der Besitzer kann sein Urtier benennen."));
            }
            if (!event.isCancelled() && entity.getScoreboardTags().contains(DinoWildSpawns.NATURAL_TAG)) {
                getServer().getScheduler().runTask(this, () -> {
                    if (entity.isValid() && entity.customName() != null) DinoWildSpawns.preserve((Mob) entity);
                });
            }
            return;
        }
        if (aquatic(entity)) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                event.getPlayer().sendActionBar(Component.text("Der Mosasaurus bleibt wild und kann nicht gezähmt oder geritten werden."));
            }
            return;
        }
        if (!supported(entity) || (!owned(entity) && !entity.getScoreboardTags().contains("ark_dino"))) return;
        if (event.getHand() != EquipmentSlot.HAND) {
            if (owned(entity)) event.setCancelled(true);
            return;
        }
        // Summons are identified on the next tick. Never let that gap select legacy one-seed taming.
        if (prototype(entity) && species(entity) == null) identify(entity);
        if (prototype(entity) && (species(entity) == null || !CareRules.SPECIES.contains(species(entity)))) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Dieses Urtier ist noch nicht bereit. Bitte erneut versuchen."));
            return;
        }
        if (owned(entity) && player.isInsideVehicle()) {
            event.setCancelled(true);
            return;
        }
        if (owned(entity) && prototype(entity) && player.isSneaking()) {
            event.setCancelled(true);
            if (player.getInventory().getItemInMainHand().getType().isAir()) controls.open(player, (Mob) entity);
            else player.sendActionBar(Component.text("Schleichen + leere Hand: Urtier-Menü öffnen."));
            return;
        }
        if (!(owned(entity) && player.isSneaking()) && care.interact((Mob) entity, player)) {
            event.setCancelled(true);
            return;
        }
        var item = player.getInventory().getItemInMainHand();
        boolean seed = item.getType() == Material.TORCHFLOWER_SEEDS && item.getAmount() > 0;
        var action = rules.interact(owned(entity) ? owner(entity) : null, player.getUniqueId().toString(),
                survival(player), seed, player.isSneaking(), staying(entity));
        // Sharing grants mounting only. Feeding, naming and the control menu keep their owner checks.
        if (owned(entity) && publicRide(entity) && item.getType().isAir() && !player.isSneaking()
                && canRide((Mob) entity, player)) action = TamingRules.Action.IGNORE;
        if (owned(entity) || action != TamingRules.Action.IGNORE) event.setCancelled(true);
        if (action == TamingRules.Action.DENY) {
            player.sendActionBar(Component.text("Nur der Besitzer kann sein Urtier im Survival steuern."));
        } else if (action == TamingRules.Action.TAME) {
            Mob mob = (Mob) entity;
            if (!tame(mob, player)) {
                player.sendActionBar(Component.text("Zähmen gerade nicht möglich. Bitte Admin informieren."));
                return;
            }
            if (action.consumesSeed()) {
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItemInMainHand(item);
            }
            player.sendActionBar(Component.text("Gezähmt! Schleichen + Interagieren: Bleiben / Folgen."));
        } else if (action == TamingRules.Action.STAY || action == TamingRules.Action.FOLLOW) {
            boolean stay = action == TamingRules.Action.STAY;
            controls.clearMode(entity);
            controlMode((Mob) entity, stay);
            player.sendActionBar(Component.text(stay ? "Dein Urtier bleibt hier." : "Dein Urtier folgt dir."));
        } else if (loaded.containsKey(entity.getUniqueId()) && entity.getScoreboardTags().contains("ark_dino_tamed")
                && driver((Mob) entity) != null && item.getType().isAir() && !player.isSneaking()) {
            if (seats.mount((Mob) entity, player))
                player.sendActionBar(Component.text("Du sitzt mit auf. Der vordere Reiter steuert; Schleichen zum Absteigen."));
            else if (canRide((Mob) entity, player))
                player.sendActionBar(Component.text("Hier ist gerade kein freier, sicherer Mitfahrplatz verfügbar."));
        } else if (loaded.containsKey(entity.getUniqueId()) && entity.getScoreboardTags().contains("ark_dino_tamed")
                && RidingRules.canMount(owned(entity) ? owner(entity) : null, player.getUniqueId().toString(),
                publicRide(entity), survival(player), item.getType().isAir(), player.isSneaking(), !entity.getPassengers().isEmpty(), player.isInsideVehicle())) {
            Mob mob = (Mob) entity;
            if (!adult(mob)) {
                player.sendActionBar(Component.text("Dein Jungtier wächst noch und kann noch nicht geritten werden."));
                return;
            }
            combat.cancel(mob);
            stop(mob);
            riding.put(mob.getUniqueId(), mob);
            landing.remove(mob.getUniqueId());
            if (!mob.addPassenger(player)) endRide(mob);
        }
    }

    void protectFall(LivingEntity entity) {
        // Bounded flight exit protection also survives unload/disable; ordinary falling is unchanged.
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                (int) Math.max(20, Math.ceil((entity.getLocation().getY() - entity.getWorld().getMinHeight() + 16) / .1)),
                0, false, false));
    }

    private void restoreGravity(Mob mob) {
        flying.remove(mob.getUniqueId());
        landing.remove(mob.getUniqueId());
        mob.getPersistentDataContainer().remove(flightKey);
        mob.setGravity(true);
        mob.setVelocity(new Vector());
        models.animation(mob, "idle");
    }

    private void endRide(Mob mob) {
        riding.remove(mob.getUniqueId());
        if (flying.containsKey(mob.getUniqueId())) {
            for (Entity passenger : passengers(mob)) if (passenger instanceof LivingEntity living) protectFall(living);
            protectFall(mob);
            landing.put(mob.getUniqueId(), mob);
        }
        if (seats != null) seats.remove(mob);
        mob.eject();
        stopRidingMotion(mob);
    }

    private void stopRidingMotion(Mob mob) {
        stop(mob);
        boolean airborne = flying.containsKey(mob.getUniqueId());
        mob.setVelocity(new Vector(0, airborne ? 0 : mob.getVelocity().getY(), 0));
        if (!airborne) models.animation(mob, "idle");
    }

    private void endRide(Player player) {
        if (seats != null) seats.remove(player);
        for (Mob mob : List.copyOf(riding.values())) {
            if (mob.getPassengers().contains(player)) endRide(mob);
        }
    }

    private boolean destinationLoaded(Mob mob, Vector motion) {
        var box = mob.getBoundingBox().expandDirectional(motion).expand(.1);
        for (int x = (int) Math.floor(box.getMinX()) >> 4; x <= (int) Math.floor(box.getMaxX()) >> 4; x++) {
            for (int z = (int) Math.floor(box.getMinZ()) >> 4; z <= (int) Math.floor(box.getMaxZ()) >> 4; z++) {
                if (!mob.getWorld().isChunkLoaded(x, z)) return false;
            }
        }
        return true;
    }

    private void flightVelocity(Mob mob, Vector motion) {
        if (!destinationLoaded(mob, motion)) { mob.setVelocity(new Vector()); return; }
        // The carrier keeps native collision/floor resolution; protect the actual swept rider volume too.
        for (Entity passenger : passengers(mob)) {
            var sweep = passenger.getBoundingBox().expandDirectional(motion);
            // Neighbor blocks can have collision shapes extending outside their own block (e.g. fences).
            for (int x = (int) Math.floor(sweep.getMinX()) - 1; x <= (int) Math.floor(sweep.getMaxX()) + 1; x++) {
                for (int z = (int) Math.floor(sweep.getMinZ()) - 1; z <= (int) Math.floor(sweep.getMaxZ()) + 1; z++) {
                    if (!mob.getWorld().isChunkLoaded(x >> 4, z >> 4)) { mob.setVelocity(new Vector()); return; }
                    for (int y = (int) Math.floor(sweep.getMinY()) - 1; y <= (int) Math.floor(sweep.getMaxY()) + 1; y++) {
                        var shape = mob.getWorld().getBlockAt(x, y, z).getCollisionShape();
                        if (shape.overlaps(sweep.clone().shift(-x, -y, -z))) { mob.setVelocity(new Vector()); return; }
                    }
                }
            }
        }
        mob.setFallDistance(0);
        for (Entity passenger : passengers(mob)) passenger.setFallDistance(0);
        mob.setVelocity(motion);
    }

    private void ride() {
        for (Mob mob : List.copyOf(landing.values())) {
            if (!mob.isValid() || mob.isDead() || mob.isOnGround() || mob.isInWater()) restoreGravity(mob);
            else flightVelocity(mob, new Vector(0, -.16, 0));
        }
        for (Mob mob : List.copyOf(riding.values())) {
            Player player = driver(mob);
            if (player == null) {
                endRide(mob);
                continue;
            }
            stop(mob);
            var input = player.getCurrentInput();
            if (prototype(mob) && "pteranodon".equals(species(mob))) {
                if (input.isJump() && !mob.isInWater() && !flying.containsKey(mob.getUniqueId())) {
                    flying.put(mob.getUniqueId(), Math.min(mob.getLocation().getY() + 48, mob.getWorld().getMaxHeight() - mob.getHeight() - 3));
                    landing.remove(mob.getUniqueId());
                    mob.getPersistentDataContainer().set(flightKey, PersistentDataType.BYTE, (byte) 1);
                    mob.setGravity(false);
                }
                if (flying.containsKey(mob.getUniqueId())) {
                    var motion = FlightRules.movement(player.getYaw(), player.getPitch(), input.isForward(), input.isBackward(),
                            input.isLeft(), input.isRight(), input.isJump(), input.isSprint(), mob.getLocation().getY(), flying.get(mob.getUniqueId()));
                    if (mob.isInWater() || mob.isOnGround() && !input.isJump() && motion.y() <= 0) restoreGravity(mob);
                    else {
                        flightVelocity(mob, new Vector(motion.x(), motion.y(), motion.z()));
                        mob.setRotation(player.getYaw(), 0);
                        models.animation(mob, input.isJump() ? "flap" : "glide");
                        continue;
                    }
                }
            }
            var motion = RidingRules.movement(player.getYaw(), input.isForward(), input.isBackward(), input.isLeft(), input.isRight(), ridingSpeed(mob,input.isSprint()));
            Vector velocity = new Vector(motion.x(), mob.getVelocity().getY(), motion.z());
            if (destinationLoaded(mob, velocity) && seats.canMove(mob, velocity, player.getYaw())) {
                mob.setVelocity(velocity);
                mob.setRotation(player.getYaw(), 0);
            } else mob.setVelocity(new Vector());
        }
        // Buoyancy is the final motion controller, including Stay and an absent owner.
        if (swimming != null) swimming.tick();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (aquatic(event.getMount())) { event.setCancelled(true); return; }
        if (owned(event.getMount()) && (riding.get(event.getMount().getUniqueId()) != event.getMount()
                || !(event.getEntity() instanceof Player player) || !canRide((Mob) event.getMount(), player)
                || !event.getMount().getPassengers().isEmpty())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof Mob mob && riding.remove(mob.getUniqueId()) != null) {
            if (seats != null) seats.remove(mob);
            if (flying.containsKey(mob.getUniqueId())) {
                if (event.getEntity() instanceof LivingEntity living) protectFall(living);
                protectFall(mob);
                landing.put(mob.getUniqueId(), mob);
            }
            stopRidingMotion(mob);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swimmingDismount(EntityDismountEvent event) {
        // Vanilla's Ravager tag forces an exit every submerged tick. Keep an active,
        // authorized rider aboard while buoyancy raises it. Sneak still exits; all
        // lifecycle/revocation paths clear the riding reservation before ejecting.
        if (event.isCancellable() && event.getDismounted() instanceof Ravager mob && prototype(mob)
                && event.getEntity() instanceof Player player && driver(mob) == player
                && mob.isInWater() && DinoSwimming.eyeInWater(player) && !player.isSneaking()) event.setCancelled(true);
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
        if (mob != null) { endRide(mob); restoreGravity(mob); }
        models.unload(event.getEntity());
        if (swimming != null) swimming.unload(event.getEntity());
    }

    private void follow() {
        var iterator = loaded.values().iterator();
        while (iterator.hasNext()) {
            Mob mob = iterator.next();
            if (!mob.isValid() || mob.isDead()) {
                iterator.remove();
                continue;
            }
            models.growth(mob);
            if (combat.active(mob)) continue;
            mob.setTarget(null);
            if (riding.containsKey(mob.getUniqueId()) || flying.containsKey(mob.getUniqueId()) || aerial(mob)) continue;
            if (controls.roaming(mob) && !staying(mob)) { controls.wander(mob); continue; }
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
            mob.getPathfinder().moveTo(player, groundSpeed(mob,true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void target(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && (owned(event.getEntity()) || formerOwner(event.getEntity(), event.getTarget()))) event.setTarget(null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Mob mob && formerOwner(mob, event.getEntity())) { event.setCancelled(true); return; }
        if (event.getDamager() instanceof Mob mob && (owned(mob)
                || prototype(mob) && SpeciesRules.ravager(species(mob)))) {
            if (!combat.permits(mob, event.getEntity())) event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void portal(EntityPortalEvent event) {
        if (owned(event.getEntity()) || aquatic(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void teleport(EntityTeleportEvent event) {
        if ((owned(event.getEntity()) || aquatic(event.getEntity())) && event.getTo() != null && !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void leash(PlayerLeashEntityEvent event) {
        if (aquatic(event.getEntity())) { event.setCancelled(true); return; }
        if (owned(event.getEntity()) && !owner(event.getEntity()).equals(event.getPlayer().getUniqueId().toString())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void preserveLeashed(PlayerLeashEntityEvent event) {
        if (event.getEntity() instanceof Mob mob && mob.getScoreboardTags().contains(DinoWildSpawns.NATURAL_TAG)) {
            DinoWildSpawns.preserve(mob);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void unleash(PlayerUnleashEntityEvent event) {
        if (owned(event.getEntity()) && !owner(event.getEntity()).equals(event.getPlayer().getUniqueId().toString())) event.setCancelled(true);
    }
}
