package de.ark.dinos;

import java.util.TreeMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.CompassMeta;

/** Last-known pet positions use the world's normal persistent-data save. */
final class DinoLocator {
    private final ArkDinos plugin;
    private final NamespacedKey owner, species, name, x, y, z, trackedPet;

    DinoLocator(ArkDinos plugin) {
        this.plugin = plugin;
        owner = key("owner"); species = key("species"); name = key("name");
        x = key("x"); y = key("y"); z = key("z");
        trackedPet = key("tracked-pet");
    }

    private NamespacedKey key(String value) { return new NamespacedKey(plugin, value); }
    private NamespacedKey pet(Entity entity) { return key("pet_" + entity.getUniqueId()); }

    // shortcut: previously unloaded pets enter the index on next load; scan saved entities if immediate migration is needed.
    void remember(Entity entity) {
        if (!plugin.owned(entity) || !entity.isValid() || entity.isDead()) return;
        Mob mob = (Mob) entity;
        var world = mob.getWorld();
        var data = world.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        data.set(owner, PersistentDataType.STRING, plugin.owner(mob));
        String kind = plugin.species(mob);
        data.set(species, PersistentDataType.STRING, kind == null ? mob.getType().name() : kind);
        data.set(name, PersistentDataType.STRING, mob.getName());
        data.set(x, PersistentDataType.INTEGER, mob.getLocation().getBlockX());
        data.set(y, PersistentDataType.INTEGER, mob.getLocation().getBlockY());
        data.set(z, PersistentDataType.INTEGER, mob.getLocation().getBlockZ());
        var id = pet(mob);
        for (var other : plugin.getServer().getWorlds()) if (!other.equals(world)) other.getPersistentDataContainer().remove(id);
        world.getPersistentDataContainer().set(id, PersistentDataType.TAG_CONTAINER, data);
    }

    void forget(Entity entity) {
        var id = pet(entity);
        for (var world : plugin.getServer().getWorlds()) world.getPersistentDataContainer().remove(id);
    }

    boolean command(Player player) {
        return command(player, new String[0]);
    }

    private Location position(Player player, UUID uuid) {
        var id = key("pet_" + uuid);
        Entity live = plugin.getServer().getEntity(uuid);
        if (live != null) {
            if (!plugin.owned(live) || live.isDead() || !player.getUniqueId().toString().equals(plugin.owner(live))) return null;
            remember(live);
        }
        for (var world : plugin.getServer().getWorlds()) {
            var data = world.getPersistentDataContainer();
            if (!data.has(id, PersistentDataType.TAG_CONTAINER)) continue;
            var entry = data.get(id, PersistentDataType.TAG_CONTAINER);
            if (entry == null || !player.getUniqueId().toString().equals(entry.get(owner, PersistentDataType.STRING))
                    || !entry.has(x, PersistentDataType.INTEGER) || !entry.has(y, PersistentDataType.INTEGER)
                    || !entry.has(z, PersistentDataType.INTEGER)) continue;
            return new Location(world, entry.get(x, PersistentDataType.INTEGER),
                    entry.get(y, PersistentDataType.INTEGER), entry.get(z, PersistentDataType.INTEGER));
        }
        return null;
    }

    private ItemStack compass(Player player) {
        var hand = player.getInventory().getItemInMainHand();
        return hand.getType() == Material.COMPASS ? hand : player.getInventory().getItemInOffHand();
    }

    boolean command(Player player, String[] args) {
        if (args.length > 0) {
            UUID uuid;
            try {
                if (args.length != 1) throw new IllegalArgumentException();
                uuid = UUID.fromString(args[0]);
            } catch (IllegalArgumentException error) {
                player.sendMessage(Component.text("Wähle mit /dinos ein eigenes Tier aus."));
                return true;
            }
            Location point = position(player, uuid);
            if (point == null) {
                player.sendMessage(Component.text("Dieses eigene gezähmte Tier ist nicht erfasst."));
                return true;
            }
            if (!point.getWorld().equals(player.getWorld())) {
                player.sendMessage(Component.text("Das Tier ist in " + point.getWorld().getName() + ": X "
                        + point.getBlockX() + ", Y " + point.getBlockY() + ", Z " + point.getBlockZ()
                        + ". Wähle es dort erneut aus."));
                return true;
            }
            ItemStack item = compass(player);
            if (item.getType() != Material.COMPASS) {
                player.sendMessage(Component.text("Nimm einen normalen Kompass in die Haupt- oder Nebenhand und wähle das Tier erneut."));
                return true;
            }
            var meta = (CompassMeta) item.getItemMeta();
            meta.setLodestone(point);
            meta.setLodestoneTracked(false);
            meta.getPersistentDataContainer().set(trackedPet, PersistentDataType.STRING, uuid.toString());
            item.setItemMeta(meta);
            if (player.getInventory().getItemInMainHand().getType() == Material.COMPASS) player.getInventory().setItemInMainHand(item);
            else player.getInventory().setItemInOffHand(item);
            player.sendMessage(Component.text("Dein Kompass zeigt zum gewählten Dino. Bei entladenen Tieren gilt die letzte bekannte Position."));
            return true;
        }
        var lines = new TreeMap<String, UUID>();
        String playerId = player.getUniqueId().toString();
        for (var world : plugin.getServer().getWorlds()) {
            var data = world.getPersistentDataContainer();
            for (var id : data.getKeys()) {
                if (!id.getNamespace().equals("arkdinos") || !id.getKey().startsWith("pet_")) continue;
                if (!data.has(id, PersistentDataType.TAG_CONTAINER)) continue;
                PersistentDataContainer entry = data.get(id, PersistentDataType.TAG_CONTAINER);
                if (entry == null || !playerId.equals(entry.get(owner, PersistentDataType.STRING))) continue;
                UUID uuid;
                try { uuid = UUID.fromString(id.getKey().substring(4)); }
                catch (IllegalArgumentException error) { continue; }
                Entity live = plugin.getServer().getEntity(uuid);
                if (live != null) {
                    if (!plugin.owned(live) || live.isDead() || !playerId.equals(plugin.owner(live))) {
                        data.remove(id);
                        continue;
                    }
                    remember(live);
                    entry = live.getWorld().getPersistentDataContainer().get(id, PersistentDataType.TAG_CONTAINER);
                }
                if (entry == null || !entry.has(x, PersistentDataType.INTEGER)
                        || !entry.has(y, PersistentDataType.INTEGER) || !entry.has(z, PersistentDataType.INTEGER)) continue;
                String kind = entry.getOrDefault(species, PersistentDataType.STRING, "Dino");
                String label = entry.getOrDefault(name, PersistentDataType.STRING, kind);
                lines.put(label + " [" + kind + ", " + uuid.toString().substring(0, 8) + "] – "
                        + (live == null ? world.getName() : live.getWorld().getName())
                        + " X " + entry.get(x, PersistentDataType.INTEGER) + ", Y " + entry.get(y, PersistentDataType.INTEGER)
                        + ", Z " + entry.get(z, PersistentDataType.INTEGER)
                        + (live == null ? " (zuletzt gesehen)" : " (aktuell)"), uuid);
            }
        }
        player.sendMessage(Component.text("Deine gezähmten Dinos: " + lines.size()));
        for (var line : lines.entrySet().stream().limit(50).toList()) player.sendMessage(Component.text(line.getKey())
                .append(Component.text(" [Kompass]").clickEvent(ClickEvent.runCommand("/dinofind " + line.getValue()))));
        if (lines.isEmpty()) player.sendMessage(Component.text("Noch keine eigenen Dinos erfasst. Sie werden beim Laden ihrer Umgebung aufgenommen."));
        if (lines.size() > 50) player.sendMessage(Component.text("Die ersten 50 Tiere werden angezeigt."));
        return true;
    }

    void updateCompasses() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (var slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
                var item = slot == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
                if (item.getType() != Material.COMPASS || !(item.getItemMeta() instanceof CompassMeta meta)) continue;
                String selected = meta.getPersistentDataContainer().get(trackedPet, PersistentDataType.STRING);
                if (selected == null) continue;
                Location point;
                try { point = position(player, UUID.fromString(selected)); }
                catch (IllegalArgumentException error) { point = null; }
                if (point == null) {
                    meta.setLodestone(null);
                    meta.getPersistentDataContainer().remove(trackedPet);
                } else if (!point.getWorld().equals(player.getWorld()) || point.equals(meta.getLodestone())) continue;
                else { meta.setLodestone(point); meta.setLodestoneTracked(false); }
                item.setItemMeta(meta);
                player.getInventory().setItem(slot, item);
            }
        }
    }
}
