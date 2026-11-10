package de.ark.dinos;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ControlsFixture extends JavaPlugin implements Listener {
    private boolean denyInteraction,denyClick,denyPteraDamage,zeroPteraDamage,denySeatMount;
    private final Map<java.util.UUID,Integer> playerTeleports=new java.util.HashMap<>();
    private final Map<java.util.UUID,double[]> pteraSamples=new java.util.HashMap<>();
    public void onEnable() { getServer().getPluginManager().registerEvents(this,this);getServer().getScheduler().runTaskTimer(this,()->{
        for(var world:getServer().getWorlds())for(var entity:world.getEntities())if(entity.getScoreboardTags().stream().anyMatch(t->t.startsWith("ptera_"))) {
            var sample=pteraSamples.computeIfAbsent(entity.getUniqueId(),id->new double[]{entity.getX(),entity.getZ(),0,entity.getY(),0});
            double step=Math.hypot(entity.getX()-sample[0],entity.getZ()-sample[1]);if(step<5)sample[2]+=step;sample[0]=entity.getX();sample[1]=entity.getZ();sample[3]=Math.max(sample[3],entity.getY());if(!entity.isOnGround()&&entity.getY()>4.75)sample[4]++;
        }
    },1,1); }
    private NamespacedKey key(String name) { return NamespacedKey.fromString("arkdinos:"+name); }
    private Object plugin() { return getServer().getPluginManager().getPlugin("ArkDinos"); }
    private Object module(String name) throws ReflectiveOperationException { var field=plugin().getClass().getDeclaredField(name);field.setAccessible(true);return field.get(plugin()); }
    private Object call(Object target,String name,Class<?>[] types,Object... args) throws ReflectiveOperationException { Method method=target.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(target,args); }
    private Entity entity(String tag) { return getServer().getWorlds().stream().flatMap(w->w.getEntities().stream()).filter(e->e.getScoreboardTags().contains(tag)).findFirst().orElseThrow(); }
    private int behaviors(Mob mob) {
        if (!(mob instanceof org.bukkit.entity.Sniffer)) return -1;
        try {
            Object handle=mob.getClass().getMethod("getHandle").invoke(mob);Object brain=handle.getClass().getMethod("getBrain").invoke(handle);
            var field=brain.getClass().getDeclaredField("availableBehaviorsByPriority");field.setAccessible(true);return ((Map<?,?>)field.get(brain)).size();
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private boolean blocked(Player player) {
        var box=player.getBoundingBox();var world=player.getWorld();
        for(int x=(int)Math.floor(box.getMinX())-1;x<=(int)Math.floor(box.getMaxX())+1;x++)
            for(int z=(int)Math.floor(box.getMinZ())-1;z<=(int)Math.floor(box.getMaxZ())+1;z++)
                for(int y=(int)Math.floor(box.getMinY())-1;y<=(int)Math.floor(box.getMaxY())+1;y++)
                    if(world.getBlockAt(x,y,z).getCollisionShape().overlaps(box.clone().shift(-x,-y,-z)))return true;
        return false;
    }
    private String state(Entity entity) {
        var data=entity.getPersistentDataContainer();Mob mob=(Mob)entity;
        return "id="+entity.getEntityId()+";uuid="+entity.getUniqueId()+";owner="+data.get(key("owner"),PersistentDataType.STRING)
                +";species="+data.get(key("species"),PersistentDataType.STRING)+";stay="+data.getOrDefault(key("stay"),PersistentDataType.BYTE,(byte)0)
                +";roam="+data.getOrDefault(key("free-wander"),PersistentDataType.BYTE,(byte)0)+";former="+data.get(key("former-owner"),PersistentDataType.STRING)
                +";shared="+data.getOrDefault(key("public-ride"),PersistentDataType.BYTE,(byte)0)
                +";persistent="+entity.isPersistent()+";natural="+entity.getScoreboardTags().contains("ark_dino_natural")
                +";feeds="+data.getOrDefault(key("tame-feeds"),PersistentDataType.INTEGER,0)+";tags="+entity.getScoreboardTags()
                +";health="+mob.getHealth()+";scale="+mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue()
                +";passengers="+entity.getPassengers().size()+";aware="+mob.isAware()+";ai="+mob.hasAI()+";path="+mob.getPathfinder().hasPath()
                +";goals="+getServer().getMobGoals().getAllGoals(mob).size()+";behaviors="+behaviors(mob)+";x="+entity.getX()+";y="+entity.getY()+";z="+entity.getZ()+";yaw="+entity.getYaw()
                +";gravity="+entity.hasGravity()+";ground="+entity.isOnGround()+";horizontal="+pteraSamples.getOrDefault(entity.getUniqueId(),new double[4])[2]+";maxY="+pteraSamples.getOrDefault(entity.getUniqueId(),new double[]{0,0,0,entity.getY()})[3]
                +";pteraFlight="+data.has(key("ptera-airborne"))+";groundUntil="+data.getOrDefault(key("ptera-ground-until"),PersistentDataType.LONG,0L)
                +";tick="+org.bukkit.Bukkit.getCurrentTick()+";movementSpeed="+mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).getValue()
                +";airTicks="+pteraSamples.getOrDefault(entity.getUniqueId(),new double[5])[4]+";tameExpiry="+data.getOrDefault(key("tame-expiry"),PersistentDataType.LONG,0L);
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEntityEvent event) { if(denyInteraction)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGH) public void inventoryClick(InventoryClickEvent event) { if(denyClick)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void playerTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        playerTeleports.merge(event.getPlayer().getUniqueId(),1,Integer::sum);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void seatMount(org.bukkit.event.entity.EntityMountEvent event) {
        if(denySeatMount && event.getMount() instanceof org.bukkit.entity.ArmorStand marker && marker.isMarker())event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pteraDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if(event.getEntity().getScoreboardTags().stream().noneMatch(t->t.startsWith("ptera_")))return;
        if(denyPteraDamage)event.setCancelled(true);if(zeroPteraDamage)event.setDamage(0);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.getName().equals("Rcon") && !sender.getName().equals("CONSOLE"))return true;
        try {
            switch(args[0]) {
                case "player" -> {
                    var p=getServer().getPlayerExact(args[1]);var v=p.getVehicle();
                    sender.sendMessage("uuid="+p.getUniqueId()+";x="+p.getX()+";y="+p.getY()+";z="+p.getZ()+";yaw="+p.getYaw()+";pitch="+p.getPitch()
                            +";vehicle="+(v==null?"none":v.getType())+";vehicleUuid="+(v==null?"none":v.getUniqueId())+";vehicleId="+(v==null?-1:v.getEntityId())
                            +";health="+p.getHealth()+";air="+p.getRemainingAir()+";teleports="+playerTeleports.getOrDefault(p.getUniqueId(),0)
                            +";slowFall="+(p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING)==null?0:p.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING).getDuration())
                            +";markers="+p.getWorld().getEntitiesByClass(org.bukkit.entity.ArmorStand.class).stream().filter(org.bukkit.entity.ArmorStand::isMarker).count());
                }
                case "share" -> call(plugin(),"publicRide",new Class<?>[]{Mob.class,boolean.class},entity(args[1]),Boolean.parseBoolean(args[2]));
                case "revoke-inspect" -> {
                    var guest=getServer().getPlayerExact(args[2]);
                    call(plugin(),"publicRide",new Class<?>[]{Mob.class,boolean.class},entity(args[1]),false);
                    sender.sendMessage("blocked="+blocked(guest)+";y="+guest.getY()+";vehicle="+(guest.getVehicle()==null?"none":guest.getVehicle().getType()));
                }
                case "manual-attack" -> sender.sendMessage("accepted="+call(module("combat"),"manualAttack",new Class<?>[]{Player.class},getServer().getPlayerExact(args[1])));
                case "swim-clear" -> sender.sendMessage("clear="+call(module("swimming"),"ridersClear",new Class<?>[]{Mob.class,double.class},entity(args[1]),Double.parseDouble(args[2])));
                case "seat-motion" -> {
                    Mob mob=(Mob)entity(args[1]);int before=mob.getWorld().getLoadedChunks().length;
                    boolean clear=(boolean)call(module("seats"),"canMove",new Class<?>[]{Mob.class,org.bukkit.util.Vector.class,float.class},mob,
                            new org.bukkit.util.Vector(Double.parseDouble(args[2]),Double.parseDouble(args[3]),Double.parseDouble(args[4])),Float.parseFloat(args[5]));
                    sender.sendMessage("clear="+clear+";before="+before+";after="+mob.getWorld().getLoadedChunks().length);
                }
                case "seat-target" -> {
                    var guest=getServer().getPlayerExact(args[2]);
                    sender.sendMessage("valid="+call(module("combat"),"validTarget",new Class<?>[]{Mob.class,LivingEntity.class},entity(args[1]),guest.getVehicle()));
                }
                case "disable-dinos" -> getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin("ArkDinos"));
                case "present" -> sender.sendMessage("present="+(getServer().getEntity(java.util.UUID.fromString(args[1]))!=null));
                case "wild-tick" -> {
                    var world=getServer().getWorlds().getFirst();int before=world.getLoadedChunks().length;
                    long start=System.nanoTime();for(int i=0;i<Math.clamp(Integer.parseInt(args[1]),1,50);i++)call(module("wildSpawns"),"tick",new Class<?>[]{});
                    sender.sendMessage("before="+before+";after="+world.getLoadedChunks().length+";elapsedMs="+(System.nanoTime()-start)/1_000_000.0);
                }
                case "wild-counts" -> {
                    var mobs=getServer().getWorlds().getFirst().getEntities().stream().filter(e->e.getScoreboardTags().contains("ark_dino_natural")).toList();
                    sender.sendMessage("natural="+mobs.size()+";persistent="+mobs.stream().filter(Entity::isPersistent).count());
                }
                case "wild-allowed" -> sender.sendMessage("allowed="+call(module("wildSpawns"),"protectionAllows",new Class<?>[]{org.bukkit.Location.class},
                        new org.bukkit.Location(getServer().getWorlds().getFirst(),Double.parseDouble(args[1]),4,Double.parseDouble(args[2]))));
                case "wild-edge" -> {
                    var world=getServer().getWorlds().getFirst();world.getChunkAt(64,64);world.getBlockAt(1024,3,1024).setType(Material.GRASS_BLOCK,false);int before=world.getLoadedChunks().length;
                    boolean clear=(boolean)call(module("wildSpawns"),"space",new Class<?>[]{org.bukkit.Location.class,String.class},new org.bukkit.Location(world,1024.5,4,1024.5),"brachiosaurus");
                    sender.sendMessage("clear="+clear+";before="+before+";after="+world.getLoadedChunks().length);
                }
                case "wild-space" -> sender.sendMessage("clear="+call(module("wildSpawns"),"space",new Class<?>[]{org.bukkit.Location.class,String.class},
                        new org.bukkit.Location(getServer().getWorlds().getFirst(),Double.parseDouble(args[2]),Double.parseDouble(args[3]),Double.parseDouble(args[4])),args[1]));
                case "wild-cleanup" -> call(module("wildSpawns"),"close",new Class<?>[]{});
                case "wild-spawn" -> {
                    Mob mob=(Mob)call(plugin(),"spawnWild",new Class<?>[]{org.bukkit.Location.class,String.class},
                            new org.bukkit.Location(getServer().getWorlds().getFirst(),20.5,4,20.5),"triceratops");
                    mob.addScoreboardTag("wildlife_leashed");sender.sendMessage(state(mob));
                }
                case "wild-leash" -> {
                    Mob mob=(Mob)entity("wildlife_leashed");Player player=getServer().getPlayerExact(args[1]);
                    var event=new org.bukkit.event.entity.PlayerLeashEntityEvent(mob,player,player,EquipmentSlot.HAND);
                    event.setCancelled(Boolean.parseBoolean(args[2]));getServer().getPluginManager().callEvent(event);
                    if(!event.isCancelled())mob.setLeashHolder(player);
                    sender.sendMessage("cancelled="+event.isCancelled()+";leashed="+mob.isLeashed()+";"+state(mob));
                }
                case "wild-disable-guard" -> getServer().getPluginManager().disablePlugin(getServer().getPluginManager().getPlugin(args[1]));
                case "wild-name" -> entity(args[1]).customName(net.kyori.adventure.text.Component.text("Kept wild dinosaur"));
                case "wild-region" -> {
                    var guard=getServer().getPluginManager().getPlugin("WorldGuard");var loader=guard.getClass().getClassLoader();
                    var adapter=Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter",true,loader);
                    var worldType=Class.forName("com.sk89q.worldedit.world.World",true,loader);
                    Object adapted=adapter.getMethod("adapt",org.bukkit.World.class).invoke(null,getServer().getWorlds().getFirst());
                    var wg=Class.forName("com.sk89q.worldguard.WorldGuard",true,loader);Object platform=wg.getMethod("getPlatform").invoke(wg.getMethod("getInstance").invoke(null));
                    Object container=platform.getClass().getMethod("getRegionContainer").invoke(platform),manager=container.getClass().getMethod("get",worldType).invoke(container,adapted);
                    var vector=Class.forName("com.sk89q.worldedit.math.BlockVector3",true,loader);var at=vector.getMethod("at",int.class,int.class,int.class);
                    Object region=Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion",true,loader)
                            .getConstructor(String.class,vector,vector).newInstance("wildlife_protected",at.invoke(null,-10,-64,-10),at.invoke(null,10,319,10));
                    manager.getClass().getMethod("addRegion",Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion",true,loader)).invoke(manager,region);
                    sender.sendMessage("created=true");
                }
                case "wild-claim" -> {
                    var grief=getServer().getPluginManager().getPlugin("GriefPrevention");Object store=grief.getClass().getField("dataStore").get(grief);
                    var claim=Class.forName("me.ryanhamshire.GriefPrevention.Claim",true,grief.getClass().getClassLoader());
                    Object result=store.getClass().getMethod("createClaim",org.bukkit.World.class,int.class,int.class,int.class,int.class,int.class,int.class,java.util.UUID.class,claim,Long.class,Player.class)
                            .invoke(store,getServer().getWorlds().getFirst(),45,65,-64,319,45,65,getServer().getPlayerExact(args[1]).getUniqueId(),null,null,null);
                    sender.sendMessage("created="+result.getClass().getField("succeeded").getBoolean(result));
                }
                case "inspect" -> sender.sendMessage(state(entity(args[1])));
                case "food" -> { var p=getServer().getPlayerExact(args[1]);p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.valueOf(args[2]),Integer.parseInt(args[3]))); }
                case "inventory" -> { var p=getServer().getPlayerExact(args[1]);sender.sendMessage("top="+p.getOpenInventory().getTopInventory().getSize()+";holder="+p.getOpenInventory().getTopInventory().getHolder().getClass().getSimpleName()+";hand="+p.getInventory().getItemInMainHand().getType()+";cursor="+p.getItemOnCursor().getType()); }
                case "click" -> {
                    var p=getServer().getPlayerExact(args[1]);var event=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,Integer.parseInt(args[2]),ClickType.valueOf(args[3]),InventoryAction.PICKUP_ALL);
                    getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());
                }
                case "drag" -> { var p=getServer().getPlayerExact(args[1]);var event=new InventoryDragEvent(p.getOpenInventory(),new ItemStack(Material.AIR),new ItemStack(Material.STONE),false,Map.of(0,new ItemStack(Material.STONE)));getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled()); }
                case "interact" -> { var p=getServer().getPlayerExact(args[1]);p.setSneaking(Boolean.parseBoolean(args[3]));var event=new PlayerInteractEntityEvent(p,entity(args[2]),EquipmentSlot.HAND);getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled()); }
                case "deny-interact" -> denyInteraction=Boolean.parseBoolean(args[1]);
                case "deny-seat-mount" -> denySeatMount=Boolean.parseBoolean(args[1]);
                case "deny-click" -> denyClick=Boolean.parseBoolean(args[1]);
                case "close" -> getServer().getPlayerExact(args[1]).closeInventory();
                case "restore" -> call(plugin(),"restore",new Class<?>[]{Entity.class},entity(args[1]));
                case "ptera-force" -> {
                    try {var state=module("ptera");var field=state.getClass().getDeclaredField("birds");field.setAccessible(true);Object bird=((Map<?,?>)field.get(state)).get(entity(args[1]).getUniqueId());field=bird.getClass().getDeclaredField("nextFlight");field.setAccessible(true);field.setInt(bird,0);sender.sendMessage("forced=true");}
                    catch(NoSuchFieldException missing){sender.sendMessage("forced=false");}
                }
                case "ptera-attempt" -> {
                    Mob mob=(Mob)entity(args[1]);int before=mob.getWorld().getLoadedChunks().length;
                    var state=module("ptera");var field=state.getClass().getDeclaredField("birds");field.setAccessible(true);Object bird=((Map<?,?>)field.get(state)).get(mob.getUniqueId());field=bird.getClass().getDeclaredField("nextFlight");field.setAccessible(true);field.setInt(bird,0);
                    sender.sendMessage("flight="+call(state,"roam",new Class<?>[]{Mob.class},mob)+";before="+before+";after="+mob.getWorld().getLoadedChunks().length);
                }
                case "ptera-edge-probe" -> {
                    Mob mob=(Mob)entity(args[1]);var world=mob.getWorld();world.getChunkAt(64,64);int before=world.getLoadedChunks().length;
                    boolean clear=(boolean)call(module("ptera"),"clear",new Class<?>[]{Mob.class,org.bukkit.util.BoundingBox.class},mob,new org.bukkit.util.BoundingBox(1019,4,1019,1030,12,1030));
                    sender.sendMessage("clear="+clear+";before="+before+";after="+world.getLoadedChunks().length);
                }
                case "ptera-hit" -> {
                    Mob mob=(Mob)entity(args[1]);var player=getServer().getPlayerExact(args[2]);denyPteraDamage=args[3].equals("cancelled");zeroPteraDamage=args[3].equals("zero");mob.setNoDamageTicks(0);double before=mob.getHealth();
                    if(args[3].equals("projectile")) {
                        var arrow=mob.getWorld().spawn(mob.getLocation().add(0,.8,3),org.bukkit.entity.Arrow.class);arrow.setShooter(player);arrow.setGravity(false);arrow.setDamage(.1);arrow.setVelocity(new org.bukkit.util.Vector(0,0,-2));getServer().getScheduler().runTaskLater(this,arrow::remove,40);sender.sendMessage("projectile=true");
                    } else {try{mob.damage(.1,player);}finally{denyPteraDamage=false;zeroPteraDamage=false;}sender.sendMessage("loss="+(before-mob.getHealth()));}
                }
                case "ptera-unload" -> {var mob=entity(args[1]);getServer().getPluginManager().callEvent(new org.bukkit.event.world.EntitiesUnloadEvent(mob.getChunk(),java.util.List.of(mob)));sender.sendMessage("gravity="+mob.hasGravity());}
                case "ground-path" -> {
                    Mob mob=(Mob)entity(args[1]);double speed=args.length>4?Double.parseDouble(args[4]):.85;
                    try{speed=(double)call(plugin(),"groundSpeed",new Class<?>[]{Mob.class,boolean.class},mob,speed>1);}catch(NoSuchMethodException old){try{speed=(double)call(plugin(),"groundSpeed",new Class<?>[]{Mob.class,double.class},mob,speed);}catch(NoSuchMethodException baseline){}}
                    call(plugin(),"stop",new Class<?>[]{Mob.class},mob);
                    mob.setAI(true);mob.setAware(true);boolean moved=mob.getPathfinder().moveTo(new org.bukkit.Location(mob.getWorld(),Double.parseDouble(args[2]),mob.getY(),Double.parseDouble(args[3])),speed);sender.sendMessage("speed="+speed+";path="+moved);
                }
                case "time" -> entity(args[1]).getPersistentDataContainer().set(key(args[2]),PersistentDataType.LONG,Long.parseLong(args[3])==0?0:System.currentTimeMillis()+Long.parseLong(args[3]));
                case "wander-probe" -> {
                    Mob mob=(Mob)entity(args[1]);var origin=mob.getLocation();var world=mob.getWorld();
                    world.getChunkAt(64,64);for(int x=1024;x<1040;x++)for(int z=1024;z<1040;z++)world.getBlockAt(x,3,z).setType(Material.STONE,false);
                    mob.teleport(new org.bukkit.Location(world,1024.5,4,1024.5));Object controls=module("controls");var field=controls.getClass().getDeclaredField("nextWander");field.setAccessible(true);
                    @SuppressWarnings("unchecked") var deadlines=(Map<java.util.UUID,Integer>)field.get(controls);
                    int before=mob.getWorld().getLoadedChunks().length;boolean allLoaded=true;int reached=0;
                    for(int i=0;i<16;i++) {
                        mob.getPathfinder().stopPathfinding();deadlines.remove(mob.getUniqueId());call(controls,"wander",new Class<?>[]{Mob.class},mob);
                        var path=mob.getPathfinder().getCurrentPath();if(path!=null){reached++;allLoaded&=path.canReachFinalPoint() && path.getPoints().stream().allMatch(p->p.getWorld().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4));}
                    }
                    call(plugin(),"controlMode",new Class<?>[]{Mob.class,boolean.class},mob,true);
                    int after=world.getLoadedChunks().length;mob.teleport(origin);
                    sender.sendMessage("before="+before+";after="+after+";pathLoaded="+allLoaded+";reachable="+reached);
                }
                case "tame" -> sender.sendMessage("tamed="+call(plugin(),"tame",new Class<?>[]{Mob.class,Player.class},entity(args[1]),getServer().getPlayerExact(args[2])));
                case "occupy" -> {
                    Mob mob=(Mob)entity(args[1]);var field=plugin().getClass().getDeclaredField("riding");field.setAccessible(true);
                    @SuppressWarnings("unchecked") var riding=(Map<java.util.UUID,Mob>)field.get(plugin());riding.put(mob.getUniqueId(),mob);
                    sender.sendMessage("mounted="+mob.addPassenger(getServer().getPlayerExact(args[2])));
                }
                case "active" -> sender.sendMessage("active="+call(module("combat"),"active",new Class<?>[]{Mob.class},entity(args[1])));
                case "valid" -> sender.sendMessage("valid="+call(module("combat"),"validTarget",new Class<?>[]{Mob.class,LivingEntity.class},entity(args[1]),getServer().getPlayerExact(args[2])));
                case "damage" -> { var p=getServer().getPlayerExact(args[2]);p.setNoDamageTicks(0);double before=p.getHealth();boolean landed=(boolean)call(module("combat"),"damage",new Class<?>[]{Mob.class,LivingEntity.class,double.class,boolean.class},entity(args[1]),p,2.0,true);sender.sendMessage("landed="+landed+";loss="+(before-p.getHealth())); }
                case "defend" -> {
                    var p=getServer().getPlayerExact(args[1]);var target=(Mob)entity(args[2]);p.setNoDamageTicks(0);
                    var resistance=p.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
                    p.removePotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
                    try {p.damage(.1,target);} finally {if(resistance!=null)p.addPotionEffect(resistance);}
                    sender.sendMessage("active="+call(module("combat"),"active",new Class<?>[]{Mob.class},entity(args[3])));
                }
                case "create-world" -> new org.bukkit.WorldCreator(args[1]).type(org.bukkit.WorldType.FLAT).generateStructures(false).createWorld();
                case "counts" -> sender.sendMessage("chunks="+getServer().getWorld(args[1]).getLoadedChunks().length+";dinos="+getServer().getWorld(args[1]).getEntities().stream().filter(e->e.getScoreboardTags().contains("ark_dino")).count());
                default -> throw new IllegalArgumentException("Unknown fixture command");
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        return true;
    }
}
