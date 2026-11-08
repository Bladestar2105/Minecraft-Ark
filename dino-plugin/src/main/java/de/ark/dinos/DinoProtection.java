package de.ark.dinos;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

final class DinoProtection implements Listener {
    private final ArkDinos plugin;
    private Object query, pvp, animals;
    private Method adapt, test, wrap;
    private boolean warned;
    private Object gpHandler;
    private Method gpDamage, gpMonitor;
    private boolean gpWarned;

    DinoProtection(ArkDinos plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean allows(Player owner, Mob mob, LivingEntity target) {
        if (!griefPreventionAllows(owner, target)) return false;
        var guard = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        if (guard == null) return true;
        try {
            if (!guard.isEnabled()) throw new IllegalStateException("WorldGuard disabled");
            if (query == null) {
                var loader = guard.getClass().getClassLoader();
                var api = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
                Object instance = api.getMethod("getInstance").invoke(null);
                Object platform = api.getMethod("getPlatform").invoke(instance);
                Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                query = container.getClass().getMethod("createQuery").invoke(container);
                var flag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
                var flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
                pvp = Array.newInstance(flag, 1); Array.set(pvp, 0, flags.getField("PVP").get(null));
                animals = Array.newInstance(flag, 1); Array.set(animals, 0, flags.getField("DAMAGE_ANIMALS").get(null));
                var location = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
                var player = Class.forName("com.sk89q.worldguard.LocalPlayer", true, loader);
                adapt = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader).getMethod("adapt", Location.class);
                test = query.getClass().getMethod("testState", location, player, pvp.getClass());
                wrap = guard.getClass().getMethod("wrapPlayer", Player.class);
            }
            Object player = wrap.invoke(guard, owner);
            if (target instanceof Player) return test(owner.getLocation(), player, pvp) && test(mob.getLocation(), player, pvp) && test(target.getLocation(), player, pvp);
            return !(target instanceof Animals) || test(target.getLocation(), player, animals);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            if (!warned) { plugin.getLogger().warning("WorldGuard combat protection unavailable; companion attacks denied: " + error.getClass().getSimpleName()); warned = true; }
            return false;
        }
    }

    private boolean griefPreventionAllows(Player owner, LivingEntity target) {
        var grief = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (grief == null) return true;
        try {
            if (!grief.isEnabled()) throw new IllegalStateException("GriefPrevention disabled");
            if (gpHandler == null) {
                for (var registration : EntityDamageEvent.getHandlerList().getRegisteredListeners()) {
                    var listener = registration.getListener();
                    if (registration.getPlugin() == grief && listener.getClass().getName().equals("me.ryanhamshire.GriefPrevention.EntityDamageHandler")) {
                        gpDamage = listener.getClass().getMethod("onEntityDamage", EntityDamageEvent.class);
                        gpMonitor = listener.getClass().getMethod("onEntityDamageByEntityMonitor", EntityDamageByEntityEvent.class);
                        gpHandler = listener;
                        break;
                    }
                }
                if (gpHandler == null) throw new IllegalStateException("GriefPrevention damage handler missing");
            }
            if (gpMonitor == null) throw new IllegalStateException("GriefPrevention combat monitor missing");
            // Query only GP as the owner; global dispatch would trigger a second attack/defense cycle.
            var check = attributed(owner, target, 1);
            gpDamage.invoke(gpHandler, check);
            return !check.isCancelled();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            gpUnavailable(error);
            return false;
        }
    }

    @SuppressWarnings("deprecation") // Paper's full native damage-event constructor still requires DamageModifier.BASE.
    private EntityDamageByEntityEvent attributed(Player owner, LivingEntity target, double damage) {
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK)
                .withCausingEntity(owner).withDirectEntity(owner).build();
        var base = EntityDamageEvent.DamageModifier.BASE;
        var modifiers = new java.util.EnumMap<>(java.util.Map.of(base, damage));
        var functions = java.util.Map.<EntityDamageEvent.DamageModifier, com.google.common.base.Function<Double, Double>>of(base, ignored -> 0.0);
        return new EntityDamageByEntityEvent(owner, target, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, modifiers, functions, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0 || !(event.getDamager() instanceof Mob mob) || !(event.getEntity() instanceof Player target)
                || !plugin.prototype(mob) || !plugin.owned(mob)) return;
        var grief = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (grief == null) return;
        try {
            if (!grief.isEnabled()) throw new IllegalStateException("GriefPrevention disabled");
            var owner = plugin.getServer().getPlayer(java.util.UUID.fromString(plugin.owner(mob)));
            if (owner == null) throw new IllegalStateException("Companion owner unavailable");
            // Commit GP combat/logout state only after the real native damage event succeeds.
            gpMonitor.invoke(gpHandler, attributed(owner, target, event.getFinalDamage()));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { gpUnavailable(error); }
    }

    private void gpUnavailable(Throwable error) {
        if (!gpWarned) { plugin.getLogger().warning("GriefPrevention combat protection unavailable; companion attacks denied: " + error.getClass().getSimpleName()); gpWarned = true; }
    }

    void close() { HandlerList.unregisterAll(this); }

    private boolean test(Location location, Object player, Object flags) throws ReflectiveOperationException {
        return (boolean) test.invoke(query, adapt.invoke(null, location), player, flags);
    }
}
