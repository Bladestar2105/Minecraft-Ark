package de.ark.dinos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AquaticFixture extends JavaPlugin implements Listener {
    private static final class Sample {
        Location previous;
        double distance, horizontal, minY = Double.POSITIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY, maxX = Double.NEGATIVE_INFINITY, minZ = Double.POSITIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        int wet, dry, airJumps, hits, blocked, fullAir;
        String lastBlocked = "none";
    }
    private final Map<UUID, Sample> samples = new HashMap<>();
    private boolean cancelDamage, zeroDamage, cancelMove;
    private long lastHitTick = -100;
    private long minimumGap = Long.MAX_VALUE;

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (var world : getServer().getWorlds()) for (var entity : world.getEntities()) {
                if (!(entity instanceof Dolphin dolphin) || !entity.getScoreboardTags().contains("aquatic_mosa")) continue;
                var sample = samples.computeIfAbsent(entity.getUniqueId(), key -> new Sample());
                var point = dolphin.getLocation();
                if (sample.previous != null && sample.previous.getWorld().equals(point.getWorld()) && sample.previous.distanceSquared(point) < 25) {
                    sample.distance += sample.previous.distance(point);sample.horizontal+=Math.hypot(point.getX()-sample.previous.getX(),point.getZ()-sample.previous.getZ());
                }
                sample.previous = point;
                sample.minY = Math.min(sample.minY, point.getY()); sample.maxY = Math.max(sample.maxY, point.getY());
                sample.minX = Math.min(sample.minX, point.getX()); sample.maxX = Math.max(sample.maxX, point.getX());
                sample.minZ = Math.min(sample.minZ, point.getZ()); sample.maxZ = Math.max(sample.maxZ, point.getZ());
                if(point.getY()-2.318>=16)sample.fullAir++;
                if (dolphin.isInWater()) sample.wet++; else if (sample.wet > 0) { sample.dry++; if (dolphin.getVelocity().getY() > .02) sample.airJumps++; }
            }
        }, 1, 1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void gate(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Dolphin dolphin) || !dolphin.getScoreboardTags().contains("aquatic_mosa")) return;
        if (cancelDamage) event.setCancelled(true);
        if (zeroDamage) event.setDamage(0);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void gateMove(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if(cancelMove&&event.getEntity().getScoreboardTags().contains("aquatic_mosa")) {event.setCancelled(true);cancelMove=false;}
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Dolphin dolphin) || !dolphin.getScoreboardTags().contains("aquatic_mosa") || event.getFinalDamage() <= 0) return;
        samples.computeIfAbsent(dolphin.getUniqueId(), key -> new Sample()).hits++;
        long tick = org.bukkit.Bukkit.getCurrentTick();
        minimumGap = Math.min(minimumGap, tick - lastHitTick); lastHitTick = tick;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void moved(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Dolphin mob) || !mob.getScoreboardTags().contains("aquatic_mosa") || !event.isCancelled()) return;
        var sample=samples.computeIfAbsent(mob.getUniqueId(), key -> new Sample());sample.blocked++;
        var from=event.getFrom();var to=event.getTo();
        sample.lastBlocked="from("+from.getX()+","+from.getY()+","+from.getZ()+","+from.getYaw()+")to("+to.getX()+","+to.getY()+","+to.getZ()+","+to.getYaw()+")";
    }

    private Dolphin dolphin(String tag) {
        return (Dolphin) getServer().getWorlds().stream().flatMap(world -> world.getEntities().stream())
                .filter(entity -> entity.getScoreboardTags().contains(tag)).findFirst().orElseThrow();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !(sender instanceof org.bukkit.command.RemoteConsoleCommandSender)) return true;
        try {
            var plugin = getServer().getPluginManager().getPlugin("ArkDinos");
            if (args[0].equals("spawn") || args[0].equals("spawn-paused")) {
                var world = args.length > 6 ? getServer().getWorld(args[6]) : getServer().getWorlds().getFirst();
                var dolphin = world.spawn(new Location(world, Double.parseDouble(args[3]), Double.parseDouble(args[4]), Double.parseDouble(args[5])), Dolphin.class, mob -> {
                    mob.addScoreboardTag(args[1]); mob.setPersistent(true); mob.setRemoveWhenFarAway(false);
                    if (args[0].equals("spawn-paused")) mob.setAI(false);
                    if (args[2].equals("mosasaurus")) {
                        mob.addScoreboardTag("ark_dino"); mob.addScoreboardTag("ark_dino_wild");
                        mob.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:species"), PersistentDataType.STRING, "mosasaurus");
                    }
                }); sender.sendMessage("uuid=" + dolphin.getUniqueId()); return true;
            }
            if (args[0].equals("cleanup")) {
                getServer().getWorlds().forEach(world -> world.getEntities().stream().filter(e -> e.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith("aquatic_"))).forEach(Entity::remove));
                samples.clear(); lastHitTick = -100; minimumGap = Long.MAX_VALUE; sender.sendMessage("clean=true"); return true;
            }
            if (args[0].equals("cancel-damage")) { cancelDamage = Boolean.parseBoolean(args[1]); sender.sendMessage("cancel=" + cancelDamage); return true; }
            if (args[0].equals("zero-damage")) { zeroDamage = Boolean.parseBoolean(args[1]); sender.sendMessage("zero=" + zeroDamage); return true; }
            if (args[0].equals("cancel-move")) { cancelMove=Boolean.parseBoolean(args[1]);sender.sendMessage("cancelMove="+cancelMove);return true; }
            if (args[0].equals("health")) { sender.sendMessage("health=" + getServer().getPlayer(args[1]).getHealth()); return true; }
            if (args[0].equals("disable")) { getServer().getPluginManager().disablePlugin(plugin); sender.sendMessage("disabled=true"); return true; }
            if (args[0].equals("enable")) { getServer().getPluginManager().enablePlugin(plugin); sender.sendMessage("enabled=true"); return true; }
            if (args[0].equals("create-world")) {
                var world = new org.bukkit.WorldCreator(args[1]).type(org.bukkit.WorldType.FLAT).generateStructures(false).createWorld();
                world.setSpawnLocation(0, 4, 0); sender.sendMessage("world=" + world.getKey()); return true;
            }
            if (args[0].equals("load-pocket")) {
                var world = getServer().getWorlds().getFirst();
                for (int x = 5; x <= 7; x++) for (int z = -1; z <= 0; z++) world.loadChunk(x, z);
                sender.sendMessage("pocket=true"); return true;
            }
            if (args[0].equals("chunk")) {
                var world = getServer().getWorlds().getFirst(); int x = Integer.parseInt(args[1]), z = Integer.parseInt(args[2]);
                boolean unloaded = args.length > 3 && world.unloadChunk(x, z, true);
                Object handle = world.getClass().getMethod("getHandle").invoke(world);
                Object source = handle.getClass().getMethod("getChunkSource").invoke(handle);
                var storageField = source.getClass().getDeclaredField("ticketStorage"); storageField.setAccessible(true); Object storage = storageField.get(source);
                Object tickets = storage.getClass().getMethod("getTickets", long.class).invoke(storage, ((long) z << 32) | (x & 0xffffffffL));
                sender.sendMessage("loaded=" + world.isChunkLoaded(x, z) + ";forced=" + world.getForceLoadedChunks().size() + ";unloaded=" + unloaded
                        + ";players=" + world.getPlayersSeeingChunk(x, z).size() + ";pluginTickets=" + world.getPluginChunkTickets(x, z).size() + ";tickets=" + tickets); return true;
            }
            if (args[0].equals("activation")) {
                var world=getServer().getWorlds().getFirst();Object handle=world.getClass().getMethod("getHandle").invoke(world);
                Object config=handle.getClass().getField("spigotConfig").get(handle);var range=config.getClass().getField("waterActivationRange");
                if(args.length>1)range.setInt(config,Integer.parseInt(args[1]));
                if(args.length>2) {world.setViewDistance(Integer.parseInt(args[2]));world.setSimulationDistance(Integer.parseInt(args[3]));}
                sender.sendMessage("waterActivation="+range.getInt(config)+";view="+world.getViewDistance()+";simulation="+world.getSimulationDistance());return true;
            }
            var mob = dolphin(args[1]);
            if(args[0].equals("cycle-controller")) {
                var field=plugin.getClass().getDeclaredField("aquatic");field.setAccessible(true);Object aquatic=field.get(plugin);
                var close=aquatic.getClass().getDeclaredMethod("close");close.setAccessible(true);close.invoke(aquatic);
                var constructor=aquatic.getClass().getDeclaredConstructor(plugin.getClass());constructor.setAccessible(true);Object replacement=constructor.newInstance(plugin);field.set(plugin,replacement);
                var restore=replacement.getClass().getDeclaredMethod("restore",org.bukkit.entity.Mob.class);restore.setAccessible(true);restore.invoke(replacement,mob);sender.sendMessage("restored=true");return true;
            }
            if(args[0].equals("breach")) {
                var field=plugin.getClass().getDeclaredField("aquatic");field.setAccessible(true);Object aquatic=field.get(plugin);
                var swimmers= aquatic.getClass().getDeclaredField("swimmers");swimmers.setAccessible(true);Object swimmer=((Map<?,?>)swimmers.get(aquatic)).get(mob.getUniqueId());
                try {var next=swimmer.getClass().getDeclaredField("nextBreach");next.setAccessible(true);next.setInt(swimmer,args.length>2?org.bukkit.Bukkit.getCurrentTick()+Integer.parseInt(args[2]):0);sender.sendMessage("forced=true");}
                catch(NoSuchFieldException missing){sender.sendMessage("forced=false");}return true;
            }
            if(args[0].equals("path")) {
                mob.setAI(true);mob.setAware(true);boolean result=mob.getPathfinder().moveTo(new Location(mob.getWorld(),Double.parseDouble(args[2]),Double.parseDouble(args[3]),Double.parseDouble(args[4])),1);
                sender.sendMessage("path="+result);return true;
            }
            if (args[0].equals("safety")) {
                var field=plugin.getClass().getDeclaredField("aquatic");field.setAccessible(true);Object controller=field.get(plugin);
                var safe=controller.getClass().getDeclaredMethod("safe",Dolphin.class,Location.class);safe.setAccessible(true);
                var navigation=controller.getClass().getDeclaredMethod("navigationLoaded",Dolphin.class);navigation.setAccessible(true);
                Object handle=mob.getClass().getMethod("getHandle").invoke(mob);
                sender.sendMessage("safe="+safe.invoke(controller,mob,mob.getLocation())+";navigation="+navigation.invoke(controller,mob)
                        +";activation="+handle.getClass().getField("activationType").get(handle)+";activatedTick="+handle.getClass().getField("activatedTick").get(handle)
                        +";tick="+org.bukkit.Bukkit.getCurrentTick()+";age="+mob.getTicksLived()+";forced="+mob.getWorld().getForceLoadedChunks().size());return true;
            }
            if (args[0].equals("teleport")) {
                var point = new Location(mob.getWorld(), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]), Float.parseFloat(args[5]), 0);
                mob.teleport(point); mob.setBodyYaw(point.getYaw()); mob.setVelocity(new org.bukkit.util.Vector());
                var sample=samples.get(mob.getUniqueId());if(sample!=null)sample.previous=mob.getLocation();
                sender.sendMessage("teleported=true"); return true;
            }
            if (args[0].equals("calibrated")) {
                var key = NamespacedKey.fromString("arkdinos:model-original-scale");
                if (Boolean.parseBoolean(args[2])) mob.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, 4.0);
                else mob.getPersistentDataContainer().remove(key);
                sender.sendMessage("calibrated=" + args[2]); return true;
            }
            if (args[0].equals("cross-world")) { sender.sendMessage("teleported=" + mob.teleport(new Location(getServer().getWorlds().getFirst().getEnvironment() == org.bukkit.World.Environment.NORMAL ? getServer().getWorlds().get(1) : getServer().getWorlds().getFirst(), 0, 80, 0))); return true; }
            if (args[0].equals("air")) { mob.setRemainingAir(1); mob.setMoistness(1); sender.sendMessage("air=1"); return true; }
            if (args[0].equals("health-set")) { mob.setHealth(Double.parseDouble(args[2])); sender.sendMessage("health=" + mob.getHealth()); return true; }
            if (args[0].equals("velocity")) { mob.setAI(true); mob.setVelocity(new org.bukkit.util.Vector(Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]))); sender.sendMessage("velocity=true"); return true; }
            if (args[0].equals("boundary-move")) {
                var from = mob.getLocation(); var to = from.clone().add(0, 0, 40);
                var move = new io.papermc.paper.event.entity.EntityMoveEvent(mob, from, to); getServer().getPluginManager().callEvent(move);
                sender.sendMessage("cancelled=" + move.isCancelled()); return true;
            }
            if (args[0].equals("native-bite")) { var target = getServer().getPlayer(args[2]); target.setNoDamageTicks(0); mob.attack(target); sender.sendMessage("health=" + target.getHealth()); return true; }
            if (args[0].equals("click")) {
                var player = getServer().getPlayer(args[2]);
                player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.TORCHFLOWER_SEEDS, 2));
                var click = new org.bukkit.event.player.PlayerInteractEntityEvent(player, mob, EquipmentSlot.HAND);
                getServer().getPluginManager().callEvent(click);
                sender.sendMessage("cancelled=" + click.isCancelled() + ";seeds=" + player.getInventory().getItemInMainHand().getAmount()
                        + ";owned=" + mob.getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:owner"))); return true;
            }
            if (args[0].equals("mount")) { sender.sendMessage("mounted=" + mob.addPassenger(getServer().getPlayer(args[2])) + ";passengers=" + mob.getPassengers().size()); return true; }
            if (args[0].equals("leash")) {
                var player = getServer().getPlayer(args[2]);
                var leash = new org.bukkit.event.entity.PlayerLeashEntityEvent(mob, player, player, EquipmentSlot.HAND);
                getServer().getPluginManager().callEvent(leash); sender.sendMessage("cancelled=" + leash.isCancelled()); return true;
            }
            if (args[0].equals("portal")) {
                var portal = new org.bukkit.event.entity.EntityPortalEvent(mob, mob.getLocation(), new Location(getServer().getWorlds().get(1), 0, 80, 0), 128, false, 0, org.bukkit.PortalType.NETHER);
                getServer().getPluginManager().callEvent(portal); sender.sendMessage("cancelled=" + portal.isCancelled()); return true;
            }
            var sample = samples.computeIfAbsent(mob.getUniqueId(), key -> new Sample());
            var aquaticField = plugin.getClass().getDeclaredField("aquatic"); aquaticField.setAccessible(true); var aquatic = aquaticField.get(plugin);
            var swimmersField = aquatic.getClass().getDeclaredField("swimmers"); swimmersField.setAccessible(true);
            boolean managed = ((Map<?, ?>) swimmersField.get(aquatic)).containsKey(mob.getUniqueId());
            var point = mob.getLocation();
            String goals = getServer().getMobGoals().getAllGoals(mob).stream().map(goal -> goal.getKey().getNamespacedKey().toString()).sorted().toList().toString();
            sender.sendMessage("uuid=" + mob.getUniqueId() + ";x=" + point.getX() + ";y=" + point.getY() + ";z=" + point.getZ()
                    + ";scale=" + mob.getAttribute(Attribute.SCALE).getValue() + ";maxHealth=" + mob.getAttribute(Attribute.MAX_HEALTH).getValue()
                    + ";health=" + mob.getHealth() + ";air=" + mob.getRemainingAir() + ";moistness=" + mob.getMoistness()
                    + ";water=" + mob.isInWater() + ";ai=" + mob.hasAI() + ";managed=" + managed + ";path=" + mob.getPathfinder().hasPath()+";breachOrigin="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:breach-origin-y"),PersistentDataType.DOUBLE)
                    + ";blockWater=" + (point.getBlock().getType() == org.bukkit.Material.WATER) + ";velocity=" + mob.getVelocity().lengthSquared()
                    + ";distance=" + sample.distance + ";horizontal="+sample.horizontal+";minY=" + sample.minY + ";maxY=" + sample.maxY + ";wet=" + sample.wet + ";dry=" + sample.dry
                    + ";spanX=" + (sample.maxX - sample.minX) + ";spanZ=" + (sample.maxZ - sample.minZ) + ";pitch=" + mob.getPitch()
                    + ";airJumps=" + sample.airJumps + ";fullAir="+sample.fullAir+";hits=" + sample.hits + ";minimumGap=" + minimumGap + ";blocked="+sample.blocked+";lastBlocked="+sample.lastBlocked+";goals=" + goals);
            return true;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
