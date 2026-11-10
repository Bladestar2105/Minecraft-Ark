package de.ark.dinos;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Test-only native observations; this plugin is never included in ArkDinos. */
public final class SwimFixture extends JavaPlugin implements org.bukkit.event.Listener {
    private final java.util.Set<String> dismountSamples = new java.util.HashSet<>();
    @Override public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void dismount(org.bukkit.event.entity.EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof Mob mob) || !(event.getEntity() instanceof org.bukkit.entity.Player player)
                || mob.getScoreboardTags().stream().noneMatch(tag -> tag.startsWith("sw_"))) return;
        try {
            String sample = "Dismount probe: species=" + mob.getPersistentDataContainer().get(key("species"), PersistentDataType.STRING)
                    + ";cancellable=" + event.isCancellable() + ";water=" + mob.isInWater() + ";riderUnderwater=" + player.isUnderWater()
                    + ";currentEyeWater=" + call(module("swimming"), "eyeInWater", new Class<?>[]{org.bukkit.entity.LivingEntity.class}, player)
                    + ";sneaking=" + player.isSneaking() + ";inputSneak=" + player.getCurrentInput().isSneak()
                    + ";driver=" + (call(plugin(), "driver", new Class<?>[]{Mob.class}, mob) == player);
            if (dismountSamples.add(sample)) getLogger().info(sample);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    private Object plugin() { return getServer().getPluginManager().getPlugin("ArkDinos"); }
    private NamespacedKey key(String name) { return NamespacedKey.fromString("arkdinos:" + name); }

    private Object module(String name) throws ReflectiveOperationException {
        var field = plugin().getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(plugin());
    }

    private Object call(Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private Mob entity(String tag) {
        return getServer().getWorlds().stream().flatMap(world -> world.getEntities().stream())
                .filter(entity -> entity instanceof Mob && entity.getScoreboardTags().contains(tag))
                .map(Mob.class::cast).findFirst().orElseThrow();
    }

    private String state(Mob mob) {
        var data = mob.getPersistentDataContainer();
        return "id=" + mob.getEntityId() + ";uuid=" + mob.getUniqueId()
                + ";owner=" + data.get(key("owner"), PersistentDataType.STRING)
                + ";species=" + data.get(key("species"), PersistentDataType.STRING)
                + ";health=" + mob.getHealth() + ";scale=" + mob.getAttribute(Attribute.SCALE).getBaseValue()
                + ";passengers=" + mob.getPassengers().size() + ";aware=" + mob.isAware() + ";ai=" + mob.hasAI()
                + ";x=" + mob.getX() + ";y=" + mob.getY() + ";z=" + mob.getZ()
                + ";eye=" + mob.getEyeLocation().getY() + ";air=" + mob.getRemainingAir() + ";maxAir=" + mob.getMaximumAir()
                + ";water=" + mob.isInWater() + ";lava=" + mob.isInLava() + ";vy=" + mob.getVelocity().getY()
                + ";gravity=" + mob.hasGravity() + ";tick=" + org.bukkit.Bukkit.getCurrentTick()
                + ";riderUnderwater=" + mob.getPassengers().stream().anyMatch(rider -> rider instanceof org.bukkit.entity.Player player && player.isUnderWater())
                + ";riderTop=" + mob.getPassengers().stream().mapToDouble(rider -> rider.getBoundingBox().getMaxY()).max().orElse(Double.NaN);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.getName().equals("Rcon") && !sender.getName().equals("CONSOLE")) return true;
        try {
            switch (args[0]) {
                case "inspect" -> sender.sendMessage(state(entity(args[1])));
                case "scale" -> entity(args[1]).getAttribute(Attribute.SCALE).setBaseValue(Double.parseDouble(args[2]));
                case "restore" -> call(plugin(), "restore", new Class<?>[]{Entity.class}, entity(args[1]));
                case "occupy" -> {
                    Mob mob = entity(args[1]);
                    var field = plugin().getClass().getDeclaredField("riding"); field.setAccessible(true);
                    @SuppressWarnings("unchecked") var riding = (Map<java.util.UUID, Mob>) field.get(plugin());
                    riding.put(mob.getUniqueId(), mob);
                    sender.sendMessage("mounted=" + mob.addPassenger(getServer().getPlayerExact(args[2])));
                }
                case "count" -> {
                    var swim = module("swimming"); var field = swim.getClass().getDeclaredField("swimmers"); field.setAccessible(true);
                    sender.sendMessage("count=" + ((Map<?, ?>) field.get(swim)).size() + ";riding=" + ((Map<?, ?>) module("riding")).size());
                }
                case "water-material" -> {
                    var block = getServer().getWorld("ark_model_test").getBlockAt(0, 32, 0);
                    block.setType(org.bukkit.Material.valueOf(args[1]), false);
                    if (args.length > 2 && block.getBlockData() instanceof org.bukkit.block.data.Waterlogged data) {
                        data.setWaterlogged(Boolean.parseBoolean(args[2])); block.setBlockData(data, false);
                    } else if (args.length > 2 && block.getBlockData() instanceof org.bukkit.block.data.Levelled data) {
                        data.setLevel(Integer.parseInt(args[2])); block.setBlockData(data, false);
                    }
                    try {
                        String result = "water=" + call(module("swimming"), "water", new Class<?>[]{org.bukkit.block.Block.class}, block);
                        if (args.length > 3) {
                            var probe = block.getWorld().spawn(block.getLocation(), org.bukkit.entity.ArmorStand.class);
                            try {
                                probe.setGravity(false);
                                probe.teleport(block.getLocation().add(.5, Double.parseDouble(args[3]) - probe.getEyeHeight(), .5));
                                result += ";eyeWater=" + call(module("swimming"), "eyeInWater", new Class<?>[]{org.bukkit.entity.LivingEntity.class}, probe);
                            } finally { probe.remove(); }
                        }
                        sender.sendMessage(result);
                    }
                    finally { block.setType(org.bukkit.Material.AIR, false); }
                }
                case "boundary" -> {
                    World world = entity(args[1]).getWorld(); int before = world.getLoadedChunks().length;
                    Object result = call(module("swimming"), "column", new Class<?>[]{World.class, int.class, int.class, int.class, int.class},
                            world, 50000, 50000, 4, 8);
                    sender.sendMessage("result=" + result + ";before=" + before + ";after=" + world.getLoadedChunks().length
                            + ";loaded=" + world.isChunkLoaded(50000 >> 4, 50000 >> 4));
                }
                case "unload" -> {
                    Mob mob = entity(args[1]);
                    getServer().getPluginManager().callEvent(new org.bukkit.event.world.EntitiesUnloadEvent(mob.getChunk(), java.util.List.of(mob)));
                }
                case "disable" -> getServer().getPluginManager().disablePlugin((org.bukkit.plugin.Plugin) plugin());
                default -> throw new IllegalArgumentException("Unknown fixture command");
            }
        } catch (ReflectiveOperationException | RuntimeException error) {
            getLogger().severe(error.toString());
            sender.sendMessage("ERROR " + error);
        }
        return true;
    }
}
