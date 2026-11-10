package de.ark.dinos;

import com.destroystokyo.paper.entity.Pathfinder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

final class DinoControls implements Listener, AutoCloseable {
    private final ArkDinos plugin;
    private final NamespacedKey roamKey, formerOwnerKey;
    private final Map<UUID, Integer> nextWander = new HashMap<>();

    DinoControls(ArkDinos plugin) {
        this.plugin = plugin;
        roamKey = new NamespacedKey(plugin, "free-wander");
        formerOwnerKey = new NamespacedKey(plugin, "former-owner");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    boolean roaming(Entity entity) {
        return plugin.prototype(entity) && entity.getPersistentDataContainer().getOrDefault(roamKey, PersistentDataType.BYTE, (byte) 0) != 0;
    }

    boolean formerOwner(Entity entity, Entity target) {
        return !plugin.owned(entity) && target instanceof Player
                && target.getUniqueId().toString().equals(entity.getPersistentDataContainer().get(formerOwnerKey, PersistentDataType.STRING));
    }

    void clearMode(Entity entity) {
        entity.getPersistentDataContainer().remove(roamKey);
        nextWander.remove(entity.getUniqueId());
    }

    private static final class Menu implements InventoryHolder {
        final UUID player, dino;
        final boolean confirmation;
        Inventory inventory;
        boolean clicked;
        Menu(Player player, Mob dino, boolean confirmation) {
            this.player = player.getUniqueId(); this.dino = dino.getUniqueId(); this.confirmation = confirmation;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    private boolean valid(Player player, Mob mob) {
        return player.isOnline() && mob.isValid() && !mob.isDead() && plugin.owned(mob) && plugin.prototype(mob)
                && CareRules.SPECIES.contains(plugin.species(mob)) && plugin.owner(mob).equals(player.getUniqueId().toString())
                && plugin.survival(player) && player.getWorld().equals(mob.getWorld())
                && player.getLocation().distanceSquared(mob.getLocation()) <= 36
                && !player.isInsideVehicle();
    }

    private ItemStack button(Material type, String name, String description) {
        var item = new ItemStack(type); var meta = item.getItemMeta();
        meta.displayName(Component.text(name)); meta.lore(List.of(Component.text(description))); item.setItemMeta(meta);
        return item;
    }

    void open(Player player, Mob mob) { open(player, mob, false); }

    private void open(Player player, Mob mob, boolean confirmation) {
        if (!valid(player, mob)) {
            player.sendActionBar(Component.text("Nur der Besitzer kann sein freies Urtier in der Nähe im Survival steuern."));
            return;
        }
        Menu menu = new Menu(player, mob, confirmation);
        menu.inventory = plugin.getServer().createInventory(menu, 9, Component.text(confirmation ? "Urtier wirklich freilassen?" : "Urtier steuern"));
        if (confirmation) {
            menu.inventory.setItem(2, button(Material.RED_CONCRETE, "Ja, freilassen", "Wird wild; bleibt seinem bisherigen Besitzer friedlich."));
            menu.inventory.setItem(6, button(Material.GREEN_CONCRETE, "Abbrechen", "Dein Urtier bleibt gezähmt."));
        } else {
            String mode = plugin.staying(mob) ? "Bleiben" : roaming(mob) ? "Freilauf" : "Folgen";
            menu.inventory.setItem(0, button(Material.LEAD, "Folgen", "Folgt dir. Aktuell: " + mode));
            menu.inventory.setItem(2, button(Material.ANVIL, "Bleiben", "Stoppt Bewegung und Verteidigung sofort."));
            menu.inventory.setItem(4, button(Material.GRASS_BLOCK, "Freilauf", "Wandert selbstständig und verteidigt dich weiterhin."));
            menu.inventory.setItem(6, button(Material.SADDLE, plugin.publicRide(mob) ? "Reiten: öffentlich" : "Reiten: nur Besitzer",
                    "Umschalten: Andere dürfen reiten; Besitz und Pflege bleiben bei dir."));
            menu.inventory.setItem(8, button(Material.IRON_BARS, "Freilassen …", "Nur erwachsene Tiere; danach erneut bestätigen."));
        }
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        boolean allowed = !event.isCancelled(); event.setCancelled(true);
        if (!allowed || menu.clicked || !(event.getWhoClicked() instanceof Player player) || !menu.player.equals(player.getUniqueId())
                || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT
                || event.getRawSlot() < 0 || event.getRawSlot() >= 9) return;
        int slot = event.getRawSlot();
        if (menu.confirmation ? slot != 2 && slot != 6 : slot != 0 && slot != 2 && slot != 4 && slot != 6 && slot != 8) return;
        menu.clicked = true;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Entity entity = plugin.getServer().getEntity(menu.dino);
            if (!(entity instanceof Mob mob) || !valid(player, mob) || player.getOpenInventory().getTopInventory() != menu.inventory) return;
            if (!menu.confirmation && slot == 6) {
                plugin.publicRide(mob, !plugin.publicRide(mob));
                player.closeInventory();
                player.sendActionBar(Component.text(plugin.publicRide(mob) ? "Dein Urtier ist für alle zum Reiten freigegeben."
                        : "Nur du darfst dein Urtier reiten."));
                return;
            }
            if (!mob.getPassengers().isEmpty()) {
                player.closeInventory();
                player.sendActionBar(Component.text("Bitte zuerst absteigen. Die Reitfreigabe kannst du jederzeit ändern."));
                return;
            }
            if (menu.confirmation) {
                player.closeInventory();
                if (slot == 2) {
                    if (!plugin.adult(mob)) player.sendActionBar(Component.text("Jungtiere bleiben bei dir, bis sie erwachsen sind."));
                    else if (plugin.release(mob)) player.sendActionBar(Component.text("Dein Urtier ist jetzt wild und bleibt dir friedlich."));
                    else player.sendActionBar(Component.text("Freilassen gerade nicht möglich. Dein Urtier bleibt gezähmt."));
                }
            } else if (slot == 8) {
                if (!plugin.adult(mob)) { player.closeInventory(); player.sendActionBar(Component.text("Jungtiere bleiben bei dir, bis sie erwachsen sind.")); }
                else open(player, mob, true);
            } else {
                player.closeInventory();
                if (slot == 4) mob.getPersistentDataContainer().set(roamKey, PersistentDataType.BYTE, (byte) 1);
                else clearMode(mob);
                plugin.controlMode(mob, slot == 2);
                player.sendActionBar(Component.text(slot == 2 ? "Dein Urtier bleibt hier." : slot == 4 ? "Dein Urtier läuft frei umher." : "Dein Urtier folgt dir."));
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    void released(Mob mob, String formerOwner) {
        clearMode(mob);
        var data = mob.getPersistentDataContainer();
        data.set(formerOwnerKey, PersistentDataType.STRING, formerOwner);
        for (String key : List.of("tame-actor", "tame-feeds", "tame-next", "tame-expiry", "love-until")) data.remove(new NamespacedKey(plugin, key));
    }

    private boolean loaded(Pathfinder.PathResult path) {
        return path != null && path.getPoints().stream().allMatch(point -> point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4));
    }

    void wander(Mob mob) {
        if(plugin.roamPtera(mob))return;
        if (!mob.getPassengers().isEmpty()) { plugin.stop(mob); return; }
        var pathfinder = mob.getPathfinder();
        if (pathfinder.hasPath()) {
            if (!loaded(pathfinder.getCurrentPath())) plugin.stop(mob);
            return;
        }
        int tick = Bukkit.getCurrentTick();
        if (tick - nextWander.getOrDefault(mob.getUniqueId(), tick) < 0) return;
        nextWander.put(mob.getUniqueId(), tick + 80);
        var origin = mob.getLocation(); var world = mob.getWorld();
        for (int attempt = 0; attempt < 8; attempt++) {
            int x = origin.getBlockX() + ThreadLocalRandom.current().nextInt(-6, 7);
            int z = origin.getBlockZ() + ThreadLocalRandom.current().nextInt(-6, 7);
            if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
            for (int y = origin.getBlockY() + 2; y >= origin.getBlockY() - 2; y--) {
                if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight() || !world.getBlockAt(x, y - 1, z).getType().isSolid()
                        || !world.getBlockAt(x, y, z).isPassable() || !world.getBlockAt(x, y + 1, z).isPassable()
                        || world.getBlockAt(x, y, z).isLiquid() || world.getBlockAt(x, y + 1, z).isLiquid()) continue;
                var destination = new org.bukkit.Location(world, x + .5, y, z + .5);
                if (destination.distanceSquared(origin) < 4) continue;
                var path = pathfinder.findPath(destination, 0);
                // Native search uses loaded chunks; trimming/movement also needs every returned path chunk loaded.
                if (path != null && path.canReachFinalPoint() && loaded(path)) {
                    mob.setAware(true); pathfinder.moveTo(path, plugin.groundSpeed(mob,false)); return;
                }
            }
        }
        plugin.stop(mob);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void chunkUnload(ChunkUnloadEvent event) {
        for (UUID id : List.copyOf(nextWander.keySet())) if (plugin.getServer().getEntity(id) instanceof Mob mob && mob.getWorld().equals(event.getWorld())) {
            var path = mob.getPathfinder().getCurrentPath();
            if (path != null && path.getPoints().stream().anyMatch(point -> point.getWorld().equals(event.getWorld())
                    && point.getBlockX() >> 4 == event.getChunk().getX() && point.getBlockZ() >> 4 == event.getChunk().getZ())) plugin.stop(mob);
        }
    }

    @EventHandler public void death(EntityDeathEvent event) { nextWander.remove(event.getEntity().getUniqueId()); }

    @EventHandler public void unload(EntitiesUnloadEvent event) {
        event.getEntities().forEach(entity -> nextWander.remove(entity.getUniqueId()));
    }

    @Override public void close() {
        for (Player player : plugin.getServer().getOnlinePlayers()) if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        nextWander.clear(); HandlerList.unregisterAll(this);
    }
}
