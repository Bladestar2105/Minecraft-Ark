package de.ark.dinos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatFixture extends JavaPlugin implements Listener {
    private final Map<String, Integer> hits = new HashMap<>();
    private final Map<String, Integer> lastTick = new HashMap<>();
    private int minimumGap = Integer.MAX_VALUE;
    private String lastDamage = "none";
    private final Map<java.lang.reflect.Field,Object> brokenProtectionFields = new HashMap<>();
    private Object brokenProtection;
    private boolean cancelLaunch, cancelHit, cancelDamage, zeroDamage, holdShots;
    private String incomingDamage = "allow";
    private int launches, impacts;
    private String lastImpact = "none";
    private double launchHeight, launchForward, launchSide;
    private boolean allowGpPvp;
    private int gpPreventions;
    private final Map<java.util.UUID, org.bukkit.util.Vector> held = new HashMap<>();
    private final Map<java.util.UUID,Integer> swings = new HashMap<>();
    private final Map<java.util.UUID,Integer> attacks = new HashMap<>();
    private CombatModelProbe modelProbe;
    private final java.util.List<String> attackTrace = new java.util.ArrayList<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void swingObserved(PlayerArmSwingEvent event) {
        if(event.getHand()==EquipmentSlot.HAND)swings.merge(event.getPlayer().getUniqueId(),1,Integer::sum);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void attackObserved(io.papermc.paper.event.player.PrePlayerAttackEntityEvent event) {
        attacks.merge(event.getPlayer().getUniqueId(),1,Integer::sum);
        attackTrace.add(event.getAttacked().getType()+":"+event.getAttacked().getEntityId()+":will="+event.willAttack()+":cancel="+event.isCancelled()+":vehicle="+event.getPlayer().getVehicle());
    }

    @Override public void onDisable() {if(modelProbe!=null)modelProbe.close();}

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        var gp = getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp != null) try {
            var type = Class.forName("me.ryanhamshire.GriefPrevention.events.PreventPvPEvent", true, gp.getClass().getClassLoader()).asSubclass(org.bukkit.event.Event.class);
            getServer().getPluginManager().registerEvent(type, this, EventPriority.NORMAL, (listener, event) -> {
                gpPreventions++; if (allowGpPvp) ((org.bukkit.event.Cancellable) event).setCancelled(true);
            }, this);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        lastDamage = event.getDamager().getType() + ":" + event.getEntity().getType()
                + ":shooter=" + (event.getDamager() instanceof org.bukkit.entity.Projectile arrow ? arrow.getShooter() : "none");
        if (!(event.getDamager() instanceof Mob) || !event.getDamager().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:species"))) return;
        for (String tag : event.getDamager().getScoreboardTags()) {
            if (!tag.startsWith("combat_")) continue;
            hits.merge(tag, 1, Integer::sum);
            Integer previous = lastTick.put(event.getDamager().getUniqueId().toString(), org.bukkit.Bukkit.getCurrentTick());
            if (previous != null) minimumGap = Math.min(minimumGap, org.bukkit.Bukkit.getCurrentTick() - previous);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void cancelDamage(EntityDamageByEntityEvent event) {
        if (cancelDamage && event.getDamager() instanceof Mob && event.getDamager().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:species"))) event.setCancelled(true);
        if (zeroDamage && event.getDamager() instanceof Mob && event.getDamager().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:species"))) event.setDamage(0);
        if (event.getEntity() instanceof Mob && event.getEntity().getPersistentDataContainer().has(NamespacedKey.fromString("arkdinos:species"))) {
            if (incomingDamage.equals("cancel")) event.setCancelled(true);
            if (incomingDamage.equals("zero")) event.setDamage(0);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void launch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (!event.getEntity().getScoreboardTags().contains("ark_dino_spit")) return;
        launches++;
        if (event.getEntity().getShooter() instanceof Mob mob) {
            var delta = event.getEntity().getLocation().toVector().subtract(mob.getLocation().toVector());
            double yaw = Math.toRadians(mob.getBodyYaw());
            launchHeight = delta.getY(); launchForward = -delta.getX() * Math.sin(yaw) + delta.getZ() * Math.cos(yaw);
            launchSide = delta.getX() * Math.cos(yaw) + delta.getZ() * Math.sin(yaw);
        }
        if (cancelLaunch) event.setCancelled(true);
        if (holdShots) { held.put(event.getEntity().getUniqueId(), event.getEntity().getVelocity()); event.getEntity().setVelocity(new org.bukkit.util.Vector()); }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void impact(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!event.getEntity().getScoreboardTags().contains("ark_dino_spit")) return;
        impacts++;
        var contact = event.getHitEntity();
        lastImpact = contact == null ? "block" : contact.getType()+":"+contact.getClass().getName();
        if (cancelHit) event.setCancelled(true);
    }

    private Entity entity(String tag) {
        return getServer().getWorlds().stream().flatMap(world -> world.getEntities().stream())
                .filter(entity -> entity.getScoreboardTags().contains(tag)).findFirst().orElseThrow();
    }

    private Object call(Object receiver, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        var method = receiver.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(receiver, args);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof org.bukkit.command.ConsoleCommandSender) && !(sender instanceof org.bukkit.command.RemoteConsoleCommandSender)) return true;
        try {
            var plugin = getServer().getPluginManager().getPlugin("ArkDinos");
            if(args[0].equals("model-state")||args[0].equals("model-trace")||args[0].equals("model-proxy")) {
                if(modelProbe==null)modelProbe=new CombatModelProbe(this);
                Mob mob=(Mob)entity(args[1]);
                if(args[0].equals("model-proxy")){sender.sendMessage(modelProbe.proxy(mob,getServer().getPlayer(args[2])));return true;}
                if(args[0].equals("model-trace"))modelProbe.trace(mob,getServer().getPlayer(args[2]));
                var player=getServer().getPlayer(args[2]);
                sender.sendMessage(modelProbe.state(mob)+";swings="+swings.getOrDefault(player.getUniqueId(),0)+";attacks="+attacks.getOrDefault(player.getUniqueId(),0));return true;
            }
            if(args[0].equals("contact-state")) {
                var player=getServer().getPlayer(args[1]);var mob=(Mob)player.getVehicle();var eye=player.getEyeLocation();
                var result=player.getWorld().rayTrace(eye,eye.getDirection(),5,org.bukkit.FluidCollisionMode.NEVER,true,.2,e->{
                    try {var target=call(plugin,"combatTarget",new Class<?>[]{Entity.class},e);return target instanceof LivingEntity && !target.equals(player)&&!target.equals(mob);}
                    catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                });
                var contact=result==null?null:result.getHitEntity();Object resolved=contact==null?null:call(plugin,"combatTarget",new Class<?>[]{Entity.class},contact);
                var f=plugin.getClass().getDeclaredField("combat");f.setAccessible(true);var combat=f.get(plugin);
                sender.sendMessage("ray="+(contact==null?result:contact.getType()+":"+contact.getEntityId())+";resolved="+resolved+";los="+(contact!=null&&mob.hasLineOfSight(contact))+";reach="+(contact!=null&&(boolean)call(combat,"meleeReach",new Class<?>[]{Mob.class,Entity.class},mob,contact))+";valid="+(resolved instanceof LivingEntity living&&(boolean)call(combat,"validTarget",new Class<?>[]{Mob.class,LivingEntity.class},mob,living)));return true;
            }
            if(args[0].equals("menu")) {
                var holder=getServer().getPlayer(args[1]).getOpenInventory().getTopInventory().getHolder();
                sender.sendMessage("open="+(holder!=null&&holder.getClass().getName().endsWith("DinoControls$Menu")));return true;
            }
            if(args[0].equals("entity-id")){sender.sendMessage("id="+entity(args[1]).getEntityId());return true;}
            if(args[0].equals("mosa-spawn")) {
                var mob=getServer().getWorlds().getFirst().spawn(new Location(getServer().getWorlds().getFirst(),.5,8,.5),org.bukkit.entity.Dolphin.class,entity->{
                    entity.addScoreboardTag("combat_mosa");entity.addScoreboardTag("ark_dino");entity.addScoreboardTag("ark_dino_wild");
                    entity.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:species"),PersistentDataType.STRING,"mosasaurus");
                });sender.sendMessage("spawned="+mob.getUniqueId());return true;
            }
            if(args[0].equals("mosa-freeze")) {
                var field=plugin.getClass().getDeclaredField("aquatic");field.setAccessible(true);call(field.get(plugin),"close",new Class<?>[]{});
                var mob=(Mob)entity("combat_mosa");mob.setAI(false);mob.setGravity(false);mob.setVelocity(new org.bukkit.util.Vector());
                mob.teleport(new Location(mob.getWorld(), .5, 8, .5, 0, 0));mob.setBodyYaw(0);
                sender.sendMessage("frozen=true");return true;
            }
            if(args[0].equals("last-damage")){sender.sendMessage(lastDamage+";trace="+attackTrace);return true;}
            if(args[0].equals("restore-protection")) {
                for(var entry:brokenProtectionFields.entrySet())entry.getKey().set(brokenProtection,entry.getValue());
                brokenProtectionFields.clear();brokenProtection=null;sender.sendMessage("restored=true");return true;
            }
            if (args[0].equals("gp-config")) {
                var gp = getServer().getPluginManager().getPlugin("GriefPrevention");
                var configField=gp.getClass().getField("config_" + args[1]);
                if(configField.getType()==int.class)configField.setInt(gp,Integer.parseInt(args[2]));
                else configField.setBoolean(gp, Boolean.parseBoolean(args[2]));
                sender.sendMessage("configured=true"); return true;
            }
            if (args[0].equals("gp-allow-pvp")) { allowGpPvp = Boolean.parseBoolean(args[1]); sender.sendMessage("allow=" + allowGpPvp + ";preventions=" + gpPreventions); return true; }
            if (args[0].equals("launch-point")) { sender.sendMessage("height=" + launchHeight + ";forward=" + launchForward + ";side=" + launchSide); return true; }
            if (args[0].equals("gp-claim") || args[0].equals("gp-clear-claims") || args[0].equals("gp-trust")) {
                var gp = getServer().getPluginManager().getPlugin("GriefPrevention");
                Object store = gp.getClass().getField("dataStore").get(gp);
                var claimType = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, gp.getClass().getClassLoader());
                if (args[0].equals("gp-clear-claims")) {
                    var claims = (java.util.Collection<?>) store.getClass().getMethod("getClaims").invoke(store);
                    for (var claim : List.copyOf(claims)) store.getClass().getMethod("deleteClaim", claimType).invoke(store, claim);
                    sender.sendMessage("claimsCleared=true"); return true;
                }
                if (args[0].equals("gp-trust")) {
                    var player = getServer().getPlayer(args[1]);
                    Object claim = store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimType)
                            .invoke(store, entity(args[2]).getLocation(), false, null);
                    var permission = Class.forName("me.ryanhamshire.GriefPrevention.ClaimPermission", true, gp.getClass().getClassLoader());
                    claimType.getMethod("setPermission", String.class, permission).invoke(claim, player.getUniqueId().toString(), permission.getField("Container").get(null));
                    sender.sendMessage("trusted=true"); return true;
                }
                var world = getServer().getWorlds().getFirst();
                Object result = store.getClass().getMethod("createClaim", org.bukkit.World.class, int.class, int.class, int.class, int.class, int.class, int.class,
                                java.util.UUID.class, claimType, Long.class, Player.class)
                        .invoke(store, world, Integer.parseInt(args[2]), Integer.parseInt(args[3]), -64, 319, Integer.parseInt(args[4]), Integer.parseInt(args[5]),
                                args[1].equals("admin") ? null : getServer().getPlayer(args[1]).getUniqueId(), null, null, null);
                sender.sendMessage("claimCreated=" + result.getClass().getField("succeeded").getBoolean(result)); return true;
            }
            if (args[0].equals("gp-immune") || args[0].equals("gp-state")) {
                var gp = getServer().getPluginManager().getPlugin("GriefPrevention");
                Object store = gp.getClass().getField("dataStore").get(gp);
                var player = getServer().getPlayer(args[1]);
                Object data = store.getClass().getMethod("getPlayerData", java.util.UUID.class).invoke(store, player.getUniqueId());
                if (args[0].equals("gp-immune")) {
                    data.getClass().getField("pvpImmune").setBoolean(data, Boolean.parseBoolean(args[2]));
                    data.getClass().getField("lastPvpTimestamp").setLong(data, 0);
                }
                sender.sendMessage("gpEnabled=" + gp.isEnabled() + ";immune=" + data.getClass().getField("pvpImmune").getBoolean(data)
                        + ";pvpRules=" + gp.getClass().getMethod("pvpRulesApply", org.bukkit.World.class).invoke(gp, player.getWorld())
                        + ";timestamp=" + data.getClass().getField("lastPvpTimestamp").getLong(data));
                return true;
            }
            if (args[0].equals("model-calibrated")) {
                var mob = (Mob) entity(args[1]);
                mob.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:model-original-scale"), PersistentDataType.DOUBLE, 1.0);
                mob.getAttribute(Attribute.SCALE).setBaseValue(.6055556906077348);
                sender.sendMessage("calibrated=true"); return true;
            }
            if (args[0].equals("cancel-launch")) { cancelLaunch = Boolean.parseBoolean(args[1]); sender.sendMessage("cancel-launch=" + cancelLaunch); return true; }
            if (args[0].equals("cancel-hit")) { cancelHit = Boolean.parseBoolean(args[1]); sender.sendMessage("cancel-hit=" + cancelHit); return true; }
            if (args[0].equals("cancel-damage")) { cancelDamage = Boolean.parseBoolean(args[1]); sender.sendMessage("cancel-damage=" + cancelDamage); return true; }
            if (args[0].equals("zero-damage")) { zeroDamage = Boolean.parseBoolean(args[1]); sender.sendMessage("zero-damage=" + zeroDamage); return true; }
            if (args[0].equals("incoming-damage")) { incomingDamage = args[1]; sender.sendMessage("incoming-damage=" + incomingDamage); return true; }
            if (args[0].equals("hold-shots")) { holdShots = Boolean.parseBoolean(args[1]); sender.sendMessage("hold-shots=" + holdShots); return true; }
            if (args[0].equals("release-shots")) {
                holdShots = false;
                getServer().getWorlds().forEach(world -> world.getEntities().forEach(entity -> {
                    var velocity = held.remove(entity.getUniqueId()); if (velocity != null) entity.setVelocity(velocity);
                })); sender.sendMessage("released=true"); return true;
            }
            if (args[0].equals("shots")) {
                var shots = getServer().getWorlds().stream().flatMap(world -> world.getEntities().stream()).filter(e -> e.getScoreboardTags().contains("ark_dino_spit")).toList();
                sender.sendMessage("count=" + shots.size() + ";launches=" + launches + ";impacts=" + impacts
                        + ";lastImpact="+lastImpact+";black=" + shots.stream().allMatch(e -> e instanceof org.bukkit.entity.Snowball ball && ball.getItem().getType() == org.bukkit.Material.BLACK_DYE && !ball.hasGravity())); return true;
            }
            if (args[0].equals("disable")) { getServer().getPluginManager().disablePlugin(plugin); sender.sendMessage("disabled=true"); return true; }
            if (args[0].equals("enable")) { getServer().getPluginManager().enablePlugin(plugin); sender.sendMessage("enabled=true"); return true; }
            if (args[0].equals("create-world")) {
                var world = new org.bukkit.WorldCreator(args[1]).type(org.bukkit.WorldType.FLAT).generateStructures(false).createWorld();
                world.setSpawnLocation(0, 4, 0); sender.sendMessage("world=" + world.getKey()); return true;
            }
            if (args[0].equals("foreign-shot")) {
                var world = getServer().getWorlds().getFirst();
                var ball = world.spawn(new Location(world, 0, 10, 0), org.bukkit.entity.Snowball.class);
                ball.addScoreboardTag("combat_foreign"); ball.addScoreboardTag("ark_dino_spit"); ball.setGravity(false);
                var hit = new org.bukkit.event.entity.ProjectileHitEvent(ball, entity(args[1]), null, null);
                getServer().getPluginManager().callEvent(hit);
                sender.sendMessage("cancelled=" + hit.isCancelled() + ";valid=" + ball.isValid()); ball.remove(); return true;
            }
            if (args[0].equals("effects")) {
                var victim = args[1].startsWith("combat_") ? (LivingEntity) entity(args[1]) : getServer().getPlayer(args[1]);
                sender.sendMessage("blind=" + victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
                        + ";slow=" + victim.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)); return true;
            }
            if (args[0].equals("cleanup")) {
                held.clear();
                getServer().getWorlds().forEach(world -> List.copyOf(world.getEntities()).forEach(entity -> {
                    if (entity.getScoreboardTags().stream().anyMatch(tag -> tag.startsWith("combat_"))) entity.remove();
                })); sender.sendMessage("clean=true"); return true;
            }
            if (args[0].equals("status")) {
                try { Class.forName("de.ark.dinos.DinoCombat", true, plugin.getClass().getClassLoader()); sender.sendMessage("combatClass=true"); }
                catch (ClassNotFoundException missing) { sender.sendMessage("combatClass=false"); }
                return true;
            }
            if (args[0].equals("spawn")) {
                var world = args.length > 6 ? getServer().getWorld(args[6]) : getServer().getWorlds().getFirst();
                Class<? extends Mob> carrier = java.util.Set.of("trex", "velociraptor", "dilophosaurus").contains(args[2]) ? Ravager.class : org.bukkit.entity.Sniffer.class;
                var mob = world.spawn(new Location(world, Double.parseDouble(args[4]), 4, Double.parseDouble(args[5])), carrier, entity -> {
                    entity.addScoreboardTag(args[1]); entity.addScoreboardTag("ark_dino");
                    entity.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:species"), PersistentDataType.STRING, args[2]);
                    if (!args[3].equals("wild")) {
                        entity.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:owner"), PersistentDataType.STRING, getServer().getPlayer(args[3]).getUniqueId().toString());
                        entity.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:stay"), PersistentDataType.BYTE, (byte) 0);
                        entity.addScoreboardTag("ark_dino_tamed");
                    }
                    entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500);
                    entity.setHealth(500);
                });
                sender.sendMessage("spawned=" + mob.getUniqueId()); return true;
            }
            if (args[0].equals("victim")) {
                var world = args.length > 4 ? getServer().getWorld(args[4]) : getServer().getWorlds().getFirst();
                var victim = world.spawn(new Location(world, Double.parseDouble(args[2]), 4, Double.parseDouble(args[3])), org.bukkit.entity.Cow.class);
                victim.addScoreboardTag(args[1]); victim.setAI(false); victim.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500); victim.setHealth(500);
                sender.sendMessage("health=" + victim.getHealth()); return true;
            }
            if (args[0].equals("pet")) {
                var world = getServer().getWorlds().getFirst();
                Class<? extends Mob> type = switch(args[2]) {
                    case "wolf" -> org.bukkit.entity.Wolf.class;
                    case "cat" -> org.bukkit.entity.Cat.class;
                    case "horse" -> org.bukkit.entity.Horse.class;
                    default -> throw new IllegalArgumentException("Unknown fixture pet");
                };
                var pet = world.spawn(new Location(world, 0, 4, 2.25), type);
                pet.addScoreboardTag(args[1]); pet.setAI(false);
                pet.getAttribute(Attribute.MAX_HEALTH).setBaseValue(500); pet.setHealth(500);
                if (!args[3].equals("wild")) ((org.bukkit.entity.Tameable) pet).setOwner(getServer().getPlayer(args[3]));
                sender.sendMessage("health=" + pet.getHealth() + ";tamed=" + ((org.bukkit.entity.Tameable) pet).isTamed()); return true;
            }
            if (args[0].equals("strike")) {
                LivingEntity victim = args[1].startsWith("combat_") ? (LivingEntity) entity(args[1]) : getServer().getPlayer(args[1]);
                Entity attacker = args[2].startsWith("combat_") ? entity(args[2]) : getServer().getPlayer(args[2]);
                victim.setNoDamageTicks(0); victim.damage(1, attacker);
                if(args.length>3&&args[3].equals("clear-iframe"))victim.setNoDamageTicks(0);
                sender.sendMessage("health=" + victim.getHealth()); return true;
            }
            if (args[0].equals("arrow")) {
                var victim = (LivingEntity) entity(args[1]); var player = getServer().getPlayer(args[2]);
                var arrow = victim.getWorld().spawnArrow(victim.getEyeLocation().add(0, 0, -3), new org.bukkit.util.Vector(0, 0, 1), 1.5f, 0);
                arrow.setShooter(player); victim.setNoDamageTicks(0);
                sender.sendMessage("arrowLaunched=true"); return true;
            }
            if (args[0].equals("pvp")) { getServer().getWorlds().getFirst().setGameRule(org.bukkit.GameRules.PVP, Boolean.parseBoolean(args[1])); sender.sendMessage("pvp=" + args[1]); return true; }
            if (args[0].equals("health")) { sender.sendMessage("health=" + getServer().getPlayer(args[1]).getHealth()); return true; }
            if (args[0].equals("look")) {
                Player player = getServer().getPlayer(args[1]); var target = args[2].startsWith("combat_") ? (LivingEntity) entity(args[2]) : getServer().getPlayer(args[2]);
                var destination = player.getLocation(); destination.setDirection(target.getBoundingBox().getCenter().subtract(player.getEyeLocation().toVector()));
                sender.sendMessage("yaw=" + destination.getYaw() + ";pitch=" + destination.getPitch()); return true;
            }
            if (args[0].equals("wall")) {
                var player = getServer().getPlayer(args[1]); var target = (LivingEntity) entity(args[2]);
                var middle = player.getEyeLocation().toVector().add(target.getBoundingBox().getCenter()).multiply(.5);
                var block = player.getWorld().getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ());
                block.setType(org.bukkit.Material.STONE); sender.sendMessage("wall=" + block.getX() + "," + block.getY() + "," + block.getZ()); return true;
            }
            if (args[0].equals("spit-wall")) {
                var source = (Mob) entity(args[1]); var target = (LivingEntity) entity(args[2]);
                var middle = source.getEyeLocation().toVector().add(target.getBoundingBox().getCenter()).multiply(.5);
                var block = source.getWorld().getBlockAt(middle.getBlockX(), middle.getBlockY(), middle.getBlockZ());
                block.setType(org.bukkit.Material.STONE); sender.sendMessage("wall=" + block.getX() + "," + block.getY() + "," + block.getZ()); return true;
            }
            if (args[0].equals("muzzle-wall")) {
                var source = (Mob) entity(args[1]); var target = (LivingEntity) entity(args[2]);
                double yaw = Math.toRadians(source.getBodyYaw());
                var mouth = source.getLocation().add(-Math.sin(yaw) * 2.199375, 1.643194529315, Math.cos(yaw) * 2.199375);
                // At the enlarged muzzle the low target remains visible below the block.
                // A midpoint block would also obstruct native sight and miss the muzzle guard.
                var block = source.getWorld().getBlockAt(mouth.getBlockX(), mouth.getBlockY(), mouth.getBlockZ());
                block.setType(org.bukkit.Material.STONE); sender.sendMessage("wall=" + block.getX() + "," + block.getY() + "," + block.getZ() + ";los=" + source.hasLineOfSight(target)
                        + ";sourceEye=" + source.getEyeLocation() + ";targetEye=" + target.getEyeLocation()); return true;
            }
            if (args[0].equals("swing")) {
                var player = getServer().getPlayer(args[1]);
                var eye = player.getEyeLocation();
                double reach = player.getVehicle() instanceof Mob vehicle && "dilophosaurus".equals(vehicle.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:species"), PersistentDataType.STRING)) ? 8 : 5;
                var hit = player.getWorld().rayTrace(eye, eye.getDirection(), reach, org.bukkit.FluidCollisionMode.NEVER, true, .2,
                        entity -> entity instanceof LivingEntity && !entity.equals(player) && !entity.equals(player.getVehicle()));
                String detail = "vehicle=" + player.isInsideVehicle() + ";ray=" + (hit == null ? "none" : hit.getHitEntity() == null ? "block" : hit.getHitEntity().getType());
                getServer().getPluginManager().callEvent(new PlayerArmSwingEvent(player, EquipmentSlot.HAND));
                sender.sendMessage("swung=true;" + detail + ";lastDamage=" + lastDamage); return true;
            }
            Mob mob = (Mob) entity(args[1]);
            if (args[0].equals("unload")) {
                getServer().getPluginManager().callEvent(new org.bukkit.event.world.EntitiesUnloadEvent(mob.getChunk(), List.of(mob)));
                sender.sendMessage("unloaded=true"); return true;
            }
            if (args[0].equals("native-bite")) {
                var victim = (LivingEntity) entity(args[2]); victim.setNoDamageTicks(0); mob.attack(victim);
                sender.sendMessage("health=" + victim.getHealth()); return true;
            }
            if (args[0].equals("remove")) { mob.remove(); sender.sendMessage("removed=true"); return true; }
            if (args[0].equals("click")) {
                Player player = getServer().getPlayer(args[2]); player.setSneaking(args.length > 3);
                if(args.length>3)player.getInventory().setItemInMainHand(null);
                getServer().getPluginManager().callEvent(new PlayerInteractEntityEvent(player, mob, EquipmentSlot.HAND)); player.setSneaking(false);
                if(args.length>3)sender.sendMessage("controlMenu="+(player.getOpenInventory().getTopInventory().getHolder()==null?"none":player.getOpenInventory().getTopInventory().getHolder().getClass().getName())
                        +";distance="+player.getLocation().distance(mob.getLocation()));
                if(args.length>3&&player.getOpenInventory().getTopInventory().getHolder()!=null
                        &&player.getOpenInventory().getTopInventory().getHolder().getClass().getName().endsWith("DinoControls$Menu")) {
                    getServer().getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryClickEvent(player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                            args[3].equals("free")?4:args[3].equals("follow")?0:2,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
                }
            }
            if (args[0].equals("age")) {
                mob.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:growth-duration"),PersistentDataType.LONG,900000L);
                mob.getPersistentDataContainer().set(NamespacedKey.fromString("arkdinos:adult-at"), PersistentDataType.LONG, System.currentTimeMillis() + Long.parseLong(args[2]));
            }
            if (args[0].equals("scale")) mob.getAttribute(Attribute.SCALE).setBaseValue(Double.parseDouble(args[2]));
            var field = plugin.getClass().getDeclaredField("combat"); field.setAccessible(true); Object combat = field.get(plugin);
            if (args[0].equals("break-protection") || args[0].equals("break-gp-protection")) {
                var protectionField = combat.getClass().getDeclaredField("protection"); protectionField.setAccessible(true);
                Object protection = protectionField.get(combat);
                var methodField = protection.getClass().getDeclaredField(args[0].equals("break-protection") ? "test" : "gpDamage"); methodField.setAccessible(true); brokenProtection=protection;brokenProtectionFields.put(methodField,methodField.get(protection));methodField.set(protection, null);
                sender.sendMessage("reflection-broken=true"); return true;
            }
            boolean active = (boolean) call(combat, "active", new Class<?>[]{Mob.class}, mob);
            sender.sendMessage("health=" + mob.getHealth() + ";hits=" + hits.getOrDefault(args[1], 0) + ";active=" + active
                    + ";path=" + mob.getPathfinder().hasPath() + ";passengers=" + mob.getPassengers().size() + ";minimumGap=" + minimumGap
                    + ";stay=" + mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:stay"), PersistentDataType.BYTE)
                    + ";adult="+call(plugin,"adult",new Class<?>[]{Entity.class},mob)+";deadline="+mob.getPersistentDataContainer().get(NamespacedKey.fromString("arkdinos:adult-at"),PersistentDataType.LONG)
                    + ";free=" + mob.getPersistentDataContainer().getOrDefault(NamespacedKey.fromString("arkdinos:free-wander"), PersistentDataType.BYTE, (byte)0));
            return true;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
}
