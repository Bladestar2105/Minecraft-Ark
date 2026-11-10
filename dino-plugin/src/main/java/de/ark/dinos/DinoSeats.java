package de.ark.dinos;

import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** A separate native vehicle gives a guest a real seat without changing the dinosaur's driver. */
final class DinoSeats implements Listener, AutoCloseable {
    private static final NamespacedKey SEAT_KEY = new NamespacedKey("arkdinos", "passenger-seat");
    private final ArkDinos plugin;
    private final BedrockSeats bedrock;
    private final Map<UUID, Seat> carriers = new HashMap<>();
    private final Map<UUID, Seat> markers = new HashMap<>();
    private final Map<UUID, Seat> guests = new HashMap<>();
    private NativeMover mover;
    private Seat moving;

    private static final class Seat {
        final Mob mob;
        final Player driver;
        final Player guest;
        final ArmorStand marker;
        boolean mounting;
        boolean mounted;
        float yaw;
        double rear;
        double relativeY;

        Seat(Mob mob, Player driver, Player guest, ArmorStand marker) {
            this.mob = mob;
            this.driver = driver;
            this.guest = guest;
            this.marker = marker;
            yaw = mob.getYaw();
            rear = SeatRules.effectiveRear(yaw, driver.getX() - mob.getX(), driver.getZ() - mob.getZ());
            relativeY = marker.getY() - mob.getY();
        }
    }

    DinoSeats(ArkDinos plugin) {
        this.plugin = plugin;
        bedrock = new BedrockSeats(plugin);
        try {
            if (!plugin.getServer().getMinecraftVersion().equals("26.3"))
                throw new IllegalStateException("Requires the verified Paper 26.3 native seat API");
            mover = new NativeMover();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            plugin.getLogger().warning("Second dinosaur seats unavailable: " + error.getMessage());
        }
    }

    boolean mount(Mob mob, Player guest) {
        Player driver = plugin.driver(mob);
        if (mover == null || carriers.containsKey(mob.getUniqueId()) || guests.containsKey(guest.getUniqueId())
                || driver == null || driver == guest || !plugin.prototype(mob) || !plugin.adult(mob)
                || !SeatRules.supports(plugin.species(mob)) || !plugin.canRide(mob, guest)
                || guest.isInsideVehicle() || guest.isSneaking() || guest.getCurrentInput().isSneak()
                || !guest.getInventory().getItemInMainHand().getType().isAir()) return false;
        Location target = location(mob, driver, mob.getYaw(), new Vector());
        var box = guest.getBoundingBox().shift(target.getX() - guest.getX(),
                target.getY() - .6 - guest.getY(), target.getZ() - guest.getZ());
        if (!clear(mob.getWorld(), box)) return false;
        ArmorStand marker = mob.getWorld().spawn(target, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setPersistent(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCollidable(false);
            stand.setCanPickupItems(false);
            stand.getPersistentDataContainer().set(SEAT_KEY, PersistentDataType.STRING, mob.getUniqueId().toString());
        });
        Seat seat = new Seat(mob, driver, guest, marker);
        carriers.put(mob.getUniqueId(), seat);
        markers.put(marker.getUniqueId(), seat);
        guests.put(guest.getUniqueId(), seat);
        try {
            // Register the packet mapping and Bedrock offset before publishing the passenger link.
            plugin.extraSeat(mob, marker, seat.rear, seat.relativeY);
            bedrock.put(marker.getUniqueId(), SeatRules.BEDROCK_SEAT_Y);
            seat.mounting = true;
            seat.mounted = marker.addPassenger(guest);
            if (!seat.mounted || !attached(seat)) {
                remove(seat);
                return false;
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            remove(seat);
            plugin.getLogger().warning("Dinosaur guest could not mount: " + error.getMessage());
            return false;
        } finally {
            seat.mounting = false;
        }
    }

    Player passenger(Mob mob) {
        Seat seat = carriers.get(mob.getUniqueId());
        return seat != null && attached(seat) ? seat.guest : null;
    }

    boolean isPassenger(Mob mob, Entity entity) { return entity != null && passenger(mob) == entity; }

    /** Additional passengers only; ArkDinos adds the direct native driver. */
    List<Entity> riders(Mob mob) {
        Player guest = passenger(mob);
        return guest == null ? List.of() : List.of(guest);
    }

    void remove(Mob mob) {
        Seat seat = carriers.get(mob.getUniqueId());
        if (seat != null) remove(seat);
    }

    void remove(Player player) {
        Seat seat = guests.get(player.getUniqueId());
        if (seat != null) remove(seat);
        for (Seat ride : List.copyOf(carriers.values())) if (ride.driver == player) remove(ride);
    }

    private void remove(Seat seat) {
        if (!carriers.remove(seat.mob.getUniqueId(), seat)) return;
        markers.remove(seat.marker.getUniqueId(), seat);
        guests.remove(seat.guest.getUniqueId(), seat);
        plugin.clearExtraSeat(seat.mob);
        bedrock.remove(seat.marker.getUniqueId());
        if (seat.mounted && seat.guest.isValid() && !seat.guest.isDead()) {
            if (mover != null && seat.marker.isValid() && seat.guest.getVehicle() == seat.marker
                    && seat.marker.getWorld() == seat.guest.getWorld()
                    && clear(seat.guest.getWorld(), seat.guest.getBoundingBox())) {
                try {
                    // A marker's default exit is its own Y, .6 above the passenger attachment.
                    // Move only the soon-to-be-removed marker so native dismount retains safe feet.
                    mover.exitAt(seat.marker, seat.guest.getLocation());
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    plugin.getLogger().warning("Dinosaur passenger exit could not preserve its height: " + error.getMessage());
                }
            }
            protectExit(seat);
        }
        // Remove reservations before native dismount/removal events can re-enter this listener.
        seat.marker.eject();
        seat.marker.remove();
    }

    private void protectExit(Seat seat) {
        Player guest = seat.guest;
        Location origin = guest.getLocation();
        // Include the native exit height when an unavailable mover prevented alignment.
        origin.setY(Math.max(origin.getY(), seat.marker.getY()));
        World world = origin.getWorld();
        double drop = Math.max(0, origin.getY() - world.getMinHeight());
        if (drop > 0 && world.isChunkLoaded(origin.getBlockX() >> 4, origin.getBlockZ() >> 4)) {
            var floor = world.rayTraceBlocks(origin, new Vector(0, -1, 0), drop, org.bukkit.FluidCollisionMode.NEVER, true);
            if (floor != null) drop = Math.max(0, origin.getY() - floor.getHitPosition().getY());
        }
        if (drop <= 3) return;
        int ticks = (int) Math.min(Integer.MAX_VALUE, Math.ceil(drop / .1) + 10);
        var previous = guest.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING);
        if (previous == null || !previous.isInfinite() && previous.getAmplifier() == 0 && previous.getDuration() < ticks)
            guest.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING,
                    ticks, 0, false, false));
    }

    /** Markers are transient; refuse to restore a saved or orphaned vehicle as a ride. */
    void restore(Entity entity) {
        if (!marked(entity) || markers.containsKey(entity.getUniqueId())) return;
        for (Entity passenger : entity.getPassengers())
            if (passenger instanceof LivingEntity living && living.isValid() && !living.isDead()) plugin.protectFall(living);
        bedrock.remove(entity.getUniqueId());
        entity.eject();
        entity.remove();
    }

    static boolean isSeat(Entity entity) {
        return entity instanceof ArmorStand && entity.getPersistentDataContainer().has(SEAT_KEY, PersistentDataType.STRING);
    }

    private boolean marked(Entity entity) { return isSeat(entity); }

    private boolean eligible(Seat seat) {
        return plugin.driver(seat.mob) == seat.driver && plugin.prototype(seat.mob) && plugin.adult(seat.mob)
                && SeatRules.supports(plugin.species(seat.mob)) && plugin.canRide(seat.mob, seat.guest);
    }

    private static boolean attached(Seat seat) {
        return seat.marker.isValid() && !seat.marker.isDead() && seat.guest.getVehicle() == seat.marker
                && seat.marker.getPassengers().size() == 1 && seat.marker.getPassengers().getFirst() == seat.guest;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterTick(ServerTickEndEvent event) { tick(); }

    /** Run after native movement so falling and climbing use this tick's real driver feet. */
    void tick() {
        for (Seat seat : List.copyOf(carriers.values())) {
            if (!eligible(seat) || !attached(seat) || seat.guest.getCurrentInput().isSneak()
                    || !canMove(seat.mob, new Vector(), seat.mob.getYaw())) {
                remove(seat);
                continue;
            }
            Location target = location(seat.mob, seat.driver, seat.mob.getYaw(), new Vector());
            double relativeY = target.getY() - seat.mob.getY();
            double rear = SeatRules.effectiveRear(seat.mob.getYaw(), seat.driver.getX() - seat.mob.getX(),
                    seat.driver.getZ() - seat.mob.getZ());
            if (Math.abs(relativeY - seat.relativeY) > .000001 || Math.abs(rear - seat.rear) > .000001) {
                seat.relativeY = relativeY;
                seat.rear = rear;
                plugin.extraSeat(seat.mob, seat.marker, rear, relativeY);
            }
            try {
                moving = seat;
                if (seat.marker.getLocation().distanceSquared(target) > .00000001
                        || Math.abs(seat.guest.getY() - (target.getY() - .6)) > .000001)
                    mover.move(seat.marker, seat.guest, target);
                seat.yaw = seat.mob.getYaw();
                seat.marker.setFallDistance(0);
                seat.guest.setFallDistance(0);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                // Fail closed rather than silently replacing native movement with player teleports.
                mover = null;
                plugin.getLogger().warning("Second dinosaur seats disabled: " + error.getMessage());
                for (Seat active : List.copyOf(carriers.values())) remove(active);
                return;
            } finally {
                moving = null;
            }
        }
    }

    boolean canMove(Mob mob, Vector motion, float proposedYaw) {
        Seat seat = carriers.get(mob.getUniqueId());
        if (seat == null) return true;
        if (!attached(seat) || !eligible(seat) || !Double.isFinite(motion.getX()) || !Double.isFinite(motion.getY())
                || !Double.isFinite(motion.getZ()) || !Float.isFinite(proposedYaw) || motion.lengthSquared() > 256) return false;
        World world = mob.getWorld();
        if (seat.marker.getWorld() != world || seat.guest.getWorld() != world) return false;
        BoundingBox previous = seat.guest.getBoundingBox();
        Vector previousFeet = seat.guest.getLocation().toVector();
        double turn = SeatRules.yawDelta(seat.yaw, proposedYaw);
        int steps = SeatRules.rotationSteps(seat.yaw, proposedYaw);
        double radius = SeatRules.REAR_DISTANCE + Math.hypot(seat.driver.getX() - mob.getX(), seat.driver.getZ() - mob.getZ());
        double arcPadding = Math.max(.01, radius * (1 - Math.cos(Math.toRadians(Math.abs(turn) / steps) / 2)) + .001);
        for (int i = 1; i <= steps; i++) {
            double fraction = (double) i / steps;
            Location target = location(mob, seat.driver, (float) (seat.yaw + turn * fraction), motion.clone().multiply(fraction));
            Vector feet = target.toVector().subtract(new Vector(0, .6, 0));
            Vector delta = feet.clone().subtract(previousFeet);
            if (delta.lengthSquared() > 256) return false;
            // Include the arc outside each chord, also when native scale changes the seat radius.
            if (!clear(world, previous.clone().expandDirectional(delta).expand(arcPadding))) return false;
            previous.shift(delta);
            previousFeet = feet;
        }
        return true;
    }

    private static Location location(Mob mob, Player driver, float yaw, Vector motion) {
        var offset = SeatRules.rearOffset(mob.getYaw(), yaw, driver.getX() - mob.getX(), driver.getZ() - mob.getZ());
        return new Location(mob.getWorld(), mob.getX() + offset.x() + motion.getX(),
                SeatRules.markerY(driver.getY()) + motion.getY(), mob.getZ() + offset.z() + motion.getZ(), 0, 0);
    }

    private static boolean clear(World world, BoundingBox box) {
        if (!Double.isFinite(box.getMinX()) || !Double.isFinite(box.getMinY()) || !Double.isFinite(box.getMinZ())
                || !Double.isFinite(box.getMaxX()) || !Double.isFinite(box.getMaxY()) || !Double.isFinite(box.getMaxZ())
                || box.getMinY() < world.getMinHeight() || box.getMaxY() > world.getMaxHeight()
                || box.getWidthX() > 32 || box.getWidthZ() > 32 || box.getHeight() > 32) return false;
        var border = world.getWorldBorder();
        var center = border.getCenter();
        double radius = border.getSize() / 2;
        if (box.getMinX() < center.getX() - radius || box.getMaxX() > center.getX() + radius
                || box.getMinZ() < center.getZ() - radius || box.getMaxZ() > center.getZ() + radius) return false;
        int minX = (int) Math.floor(box.getMinX()) - 1, maxX = (int) Math.floor(box.getMaxX()) + 1;
        int minZ = (int) Math.floor(box.getMinZ()) - 1, maxZ = (int) Math.floor(box.getMaxZ()) + 1;
        for (int x = minX >> 4; x <= maxX >> 4; x++)
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) if (!world.isChunkLoaded(x, z)) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            for (int y = Math.max(world.getMinHeight(), (int) Math.floor(box.getMinY()) - 1);
                 y <= Math.min(world.getMaxHeight() - 1, (int) Math.floor(box.getMaxY()) + 1); y++)
                if (world.getBlockAt(x, y, z).getCollisionShape().overlaps(box.clone().shift(-x, -y, -z))) return false;
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (marked(event.getEntity())) { event.setCancelled(true); return; }
        if (!marked(event.getMount())) return;
        Seat seat = markers.get(event.getMount().getUniqueId());
        if (seat == null || !seat.mounting || event.getEntity() != seat.guest || !eligible(seat)
                || !seat.marker.getPassengers().isEmpty()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismount(EntityDismountEvent event) {
        Seat seat = markers.get(event.getDismounted().getUniqueId());
        if (seat != null) remove(seat);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (moving == null || moving.guest != event.getPlayer()) remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(EntityTeleportEvent event) { removeEntity(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) { remove(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void gameMode(PlayerGameModeChangeEvent event) { remove(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent event) { remove(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) { removeEntity(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void removed(EntityRemoveEvent event) { removeEntity(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void unload(EntitiesUnloadEvent event) { event.getEntities().forEach(this::removeEntity); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void load(EntitiesLoadEvent event) { event.getEntities().forEach(this::restore); }

    private void removeEntity(Entity entity) {
        Seat seat = markers.get(entity.getUniqueId());
        if (seat != null) remove(seat);
        if (entity instanceof Player player) remove(player);
        if (entity instanceof Mob mob) remove(mob);
    }

    @Override public void close() {
        for (Seat seat : List.copyOf(carriers.values())) remove(seat);
        bedrock.close();
        HandlerList.unregisterAll(this);
    }

    /** The pinned native path moves the vehicle normally; it never teleports its player. */
    private static final class NativeMover {
        private final Method handle;
        private final Method setPos;
        private final Method positionRider;

        NativeMover() throws ReflectiveOperationException {
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity");
            handle = craft.getMethod("getHandle");
            setPos = entity.getMethod("setPos", double.class, double.class, double.class);
            positionRider = entity.getMethod("positionRider", entity);
        }

        void move(ArmorStand marker, Player guest, Location target) throws ReflectiveOperationException {
            Object vehicle = handle.invoke(marker);
            setPos.invoke(vehicle, target.getX(), target.getY(), target.getZ());
            positionRider.invoke(vehicle, handle.invoke(guest));
        }

        void exitAt(ArmorStand marker, Location target) throws ReflectiveOperationException {
            setPos.invoke(handle.invoke(marker), target.getX(), target.getY(), target.getZ());
        }
    }
}
