package de.ark.dinos;

import java.lang.reflect.Method;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CareFixture extends JavaPlugin implements Listener {
    private boolean denyPlacement,denySpawn;
    private String spawnIdentity="none";
    public void onEnable() { getServer().getPluginManager().registerEvents(this,this); }
    private NamespacedKey key(String name) { return NamespacedKey.fromString("arkdinos:"+name); }
    private Object care() throws ReflectiveOperationException {
        var plugin=getServer().getPluginManager().getPlugin("ArkDinos");var field=plugin.getClass().getDeclaredField("care");field.setAccessible(true);return field.get(plugin);
    }
    private Object call(String name,Class<?>[] types,Object... args) throws ReflectiveOperationException {
        Object care=care();Method method=care.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(care,args);
    }
    private Entity entity(String tag) { return getServer().getWorlds().stream().flatMap(w -> w.getEntities().stream()).filter(e -> e.getScoreboardTags().contains(tag)).findFirst().orElseThrow(); }
    private String state(Entity entity) {
        var data=entity.getPersistentDataContainer();
        return "id="+entity.getEntityId()+";uuid="+entity.getUniqueId()+";owner="+data.get(key("owner"),PersistentDataType.STRING)
                +";species="+data.get(key("species"),PersistentDataType.STRING)+";feeds="+data.getOrDefault(key("tame-feeds"),PersistentDataType.INTEGER,0)
                +";nextFeedAt="+data.getOrDefault(key("tame-next"),PersistentDataType.LONG,0L)
                +";actor="+data.get(key("tame-actor"),PersistentDataType.STRING)+";adultAt="+data.getOrDefault(key("adult-at"),PersistentDataType.LONG,0L)
                +";growthDuration="+data.getOrDefault(key("growth-duration"),PersistentDataType.LONG,0L)
                +";love="+data.getOrDefault(key("love-until"),PersistentDataType.LONG,0L)+";breedUntil="+data.getOrDefault(key("breed-until"),PersistentDataType.LONG,0L)
                +";type="+entity.getType()+";scale="+((org.bukkit.entity.LivingEntity)entity).getAttribute(org.bukkit.attribute.Attribute.SCALE).getBaseValue()
                +";passengers="+entity.getPassengers().size()+";x="+entity.getX()+";y="+entity.getY()+";z="+entity.getZ();
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void protection(BlockPlaceEvent event) { if(denyPlacement)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR)
    public void lateJoinSwap(org.bukkit.event.player.PlayerJoinEvent event) {
        var item=event.getPlayer().getInventory().getItem(0);if(item==null || !item.hasItemMeta())return;
        var meta=item.getItemMeta();if(!"keep".equals(meta.getPersistentDataContainer().get(key("fixture-preserve"),PersistentDataType.STRING)))return;
        meta.setItemModel(null);meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Auf erlaubtem Boden platzieren · 20 Minuten Brutzeit"),net.kyori.adventure.text.Component.text("Wächst in 15 Minuten heran · Futter beschleunigt das Wachstum")));item.setItemMeta(meta);event.getPlayer().getInventory().setItem(0,item);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void spawn(CreatureSpawnEvent event) {
        if(event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.EGG && event.getSpawnReason()!=CreatureSpawnEvent.SpawnReason.SPAWNER_EGG || !event.getEntity().getPersistentDataContainer().has(key("species")))return;
        spawnIdentity=state(event.getEntity());event.getEntity().addScoreboardTag(event.getEntity() instanceof org.bukkit.entity.Dolphin?"care_mosa":"care_hatch");
        if(denySpawn)event.setCancelled(true);
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.getName().equals("Rcon") && !sender.getName().equals("CONSOLE"))return true;
        try {
            switch(args[0]) {
                case "placed-visual" -> {
                    Block block=block(args).block();
                    var actors=block.getWorld().getNearbyEntities(block.getLocation().add(.5,.5,.5),.1,1,.1).stream()
                        .filter(e -> e instanceof org.bukkit.entity.ArmorStand && e.getScoreboardTags().contains("ark_egg_visual")).toList();
                    String model="none",uuid="none";int trackers=0,hitboxes=-1,bridge=0;boolean visible=false,persistent=true,marker=false;
                    if(!actors.isEmpty()) {
                        var actor=(org.bukkit.entity.ArmorStand)actors.getFirst();uuid=actor.getUniqueId().toString();persistent=actor.isPersistent();marker=actor.isMarker();
                        try {
                            var registry=((java.util.Optional<?>)Class.forName("kr.toxicity.model.api.BetterModel").getMethod("registry",java.util.UUID.class).invoke(null,actor.getUniqueId())).orElse(null);
                            if(registry!=null) {
                                var entries=(java.util.Collection<?>)registry.getClass().getMethod("trackers").invoke(registry);trackers=entries.size();
                                if(!entries.isEmpty()) {
                                    var tracker=entries.iterator().next();model=(String)tracker.getClass().getMethod("name").invoke(tracker);
                                    var bone=tracker.getClass().getMethod("bone",String.class).invoke(tracker,"shell");
                                    var display=bone.getClass().getMethod("getDisplay").invoke(bone);visible=display!=null && !(boolean)display.getClass().getMethod("invisible").invoke(display);
                                }
                                hitboxes=((java.util.Collection<?>)registry.getClass().getMethod("hitBoxes").invoke(registry)).size();
                            }
                            var plugin=getServer().getPluginManager().getPlugin("GeyserModelEngine");var manager=plugin.getClass().getMethod("getModelManager").invoke(plugin);
                            var cache=(Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);
                            var entries=(Map<?,?>)cache.get(actor.getEntityId());bridge=entries==null?0:entries.size();
                        }catch(ClassNotFoundException ignored) { }
                    }
                    var box=block.getBoundingBox();sender.sendMessage("actors="+actors.size()+";uuid="+uuid+";model="+model+";trackers="+trackers+";hitboxes="+hitboxes+";bridge="+bridge+";visible="+visible
                        +";persistent="+persistent+";marker="+marker+";block="+block.getType()+";width="+box.getWidthX()+";height="+box.getHeight()+";depth="+box.getWidthZ());
                }
                case "egg-renderers" -> {
                    int java=0,bedrock=0;
                    for(String species:new String[]{"trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus"}) {
                        if(kr.toxicity.model.api.BetterModel.model("ark_egg_"+species).isPresent())java++;
                        if(org.geysermc.geyser.registry.Registries.BEDROCK_ENTITY_DEFINITIONS.get().containsKey(org.geysermc.geyser.api.util.Identifier.of("modelengine:ark_egg_"+species)))bedrock++;
                    }
                    sender.sendMessage("java="+java+";bedrock="+bedrock);
                }
                case "egg-channel" -> sender.sendMessage(Integer.toString(com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Client.PLUGIN_MESSAGE.getId(
                    com.github.retrooper.packetevents.PacketEvents.getAPI().getPlayerManager().getUser(getServer().getPlayerExact(args[1])).getClientVersion())));
                case "egg-spawn" -> {
                    Block block=block(args).block();var actor=block.getWorld().getNearbyEntities(block.getLocation().add(.5,.5,.5),.1,1,.1).stream()
                        .filter(e -> e.getScoreboardTags().contains("ark_egg_visual")).findFirst().orElseThrow();
                    var plugin=getServer().getPluginManager().getPlugin("GeyserModelEngine");var manager=plugin.getClass().getMethod("getModelManager").invoke(plugin);
                    var cache=(Map<?,?>)manager.getClass().getMethod("getEntitiesCache").invoke(manager);var data=((Map<?,?>)cache.get(actor.getEntityId())).values().iterator().next();
                    var player=getServer().getPlayerExact(args[5]);((java.util.Set<Player>)data.getClass().getMethod("getViewers").invoke(data)).add(player);
                    var task=data.getClass().getMethod("getEntityTask").invoke(data);
                    task.getClass().getMethod("sendEntityData",Class.forName("re.imc.geysermodelengine.managers.model.entity.EntityData"),Player.class,int.class).invoke(task,data,player,1);
                    var packet=data.getClass().getMethod("getEntity").invoke(data);sender.sendMessage("actor="+packet.getClass().getMethod("getEntityId").invoke(packet));
                }
                case "visual-unload" -> {
                    var block=block(args).block();var field=care().getClass().getDeclaredField("visuals");field.setAccessible(true);var visuals=field.get(care());
                    var method=visuals.getClass().getDeclaredMethod("unload",org.bukkit.Chunk.class);method.setAccessible(true);method.invoke(visuals,block.getChunk());
                }
                case "care-tick" -> call("tick",new Class<?>[]{});
                case "hatch-space" -> sender.sendMessage("clear="+call("hatchSpace",new Class<?>[]{Block.class,String.class},block(args).block(),args[5]));
                case "create-world" -> new org.bukkit.WorldCreator(args[1]).type(org.bukkit.WorldType.FLAT).generateStructures(false).createWorld();
                case "inspect" -> sender.sendMessage(state(entity(args[1])));
                case "spawn-taming-race" -> {
                    Player p=getServer().getPlayerExact(args[1]);p.teleport(new org.bukkit.Location(p.getWorld(),-10,4,2));p.setSneaking(false);
                    p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.TORCHFLOWER_SEEDS,1));
                    int spawnTick=org.bukkit.Bukkit.getCurrentTick();
                    var mob=p.getWorld().spawn(new org.bukkit.Location(p.getWorld(),-10,4,0),org.bukkit.entity.Ravager.class,entity -> {
                        entity.addScoreboardTag(args[2]);entity.addScoreboardTag("ark_dino");entity.addScoreboardTag("ark_dino_wild");
                        entity.addScoreboardTag("ark_renewal_habitat_03_dino_0");entity.setAI(false);
                    });
                    String speciesBefore=mob.getPersistentDataContainer().get(key("species"),PersistentDataType.STRING);
                    // Spawn and interaction must stay in this one main-thread call, ahead of deferred restore.
                    var event=new org.bukkit.event.player.PlayerInteractEntityEvent(p,mob,EquipmentSlot.HAND);
                    getServer().getPluginManager().callEvent(event);
                    sender.sendMessage(state(mob)+";spawnTick="+spawnTick+";interactionTick="+org.bukkit.Bukkit.getCurrentTick()+";speciesBefore="+speciesBefore
                        +";cancelled="+event.isCancelled()+";hand="+p.getInventory().getItemInMainHand().getType()+";amount="+p.getInventory().getItemInMainHand().getAmount()
                        +";wild="+mob.getScoreboardTags().contains("ark_dino_wild")+";tamed="+mob.getScoreboardTags().contains("ark_dino_tamed"));
                }
                case "food" -> { Player p=getServer().getPlayerExact(args[1]);p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(new ItemStack(Material.valueOf(args[2]),Integer.parseInt(args[3]))); }
                case "fill-inventory" -> { Player p=getServer().getPlayerExact(args[1]);for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.STONE,64)); }
                case "inventory" -> {
                    Player p=getServer().getPlayerExact(args[1]);int eggs=0;String species="none",model="none",lore="none";
                    for(ItemStack item:p.getInventory().getContents())if(item!=null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key("egg-species"))) {
                        eggs+=item.getAmount();species=item.getItemMeta().getPersistentDataContainer().get(key("egg-species"),PersistentDataType.STRING);
                        model=String.valueOf(item.getItemMeta().getItemModel());lore=String.valueOf(item.getItemMeta().lore());
                    }
                    sender.sendMessage("hand="+p.getInventory().getItemInMainHand().getType()+";amount="+p.getInventory().getItemInMainHand().getAmount()+";eggs="+eggs+";eggSpecies="+species+";eggModel="+model+";lore="+lore+";top="+p.getOpenInventory().getTopInventory().getSize()+";cursor="+p.getItemOnCursor().getType());
                }
                case "time" -> entity(args[1]).getPersistentDataContainer().set(key(args[2]),PersistentDataType.LONG,Long.parseLong(args[3])==0?0:System.currentTimeMillis()+Long.parseLong(args[3]));
                case "legacy-growth" -> { var mob=entity(args[1]);var data=mob.getPersistentDataContainer();data.remove(key("growth-duration"));if(args.length>3)data.set(key("growth-duration"),PersistentDataType.LONG,Long.parseLong(args[3]));data.set(key("adult-at"),PersistentDataType.LONG,System.currentTimeMillis()+Long.parseLong(args[2])); }
                case "legacy-scale" -> {
                    var mob=(Mob)entity(args[1]);long remaining=Long.parseLong(args[2]);var data=mob.getPersistentDataContainer();
                    long duration=args.length>4?Long.parseLong(args[4]):900000L;
                    data.remove(key("growth-duration"));if(args.length>4)data.set(key("growth-duration"),PersistentDataType.LONG,duration);data.remove(key("model-original-scale"));data.set(key("adult-at"),PersistentDataType.LONG,System.currentTimeMillis()+remaining);
                    mob.getAttribute(org.bukkit.attribute.Attribute.SCALE).setBaseValue(Double.parseDouble(args[3])*Math.clamp(.45+.55*(1-remaining/(double)duration),.45,1));
                    var plugin=getServer().getPluginManager().getPlugin("ArkDinos");var method=plugin.getClass().getDeclaredMethod("restore",Entity.class);method.setAccessible(true);method.invoke(plugin,mob);sender.sendMessage(state(mob));
                }
                case "restore" -> { var plugin=getServer().getPluginManager().getPlugin("ArkDinos");var method=plugin.getClass().getDeclaredMethod("restore",Entity.class);method.setAccessible(true);method.invoke(plugin,entity(args[1])); }
                case "wild-egg" -> {
                    Player p=getServer().getPlayerExact(args[1]);var event=new org.bukkit.event.player.PlayerInteractEvent(p,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,p.getInventory().getItemInMainHand(),null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND);
                    event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                    if(args.length>2 && Boolean.parseBoolean(args[2]))event.setCancelled(true);
                    getServer().getPluginManager().callEvent(event);
                    sender.sendMessage("cancelled="+event.isCancelled()+";mosa="+p.getWorld().getEntities().stream().filter(e -> "mosasaurus".equals(e.getPersistentDataContainer().get(key("species"),PersistentDataType.STRING))).count());
                }
                case "mosa-count" -> sender.sendMessage("mosa="+getServer().getWorld(args[1]).getEntities().stream().filter(e -> "mosasaurus".equals(e.getPersistentDataContainer().get(key("species"),PersistentDataType.STRING))).count());
                case "legacy-eggs" -> {
                    Player p=getServer().getPlayerExact(args[1]);p.getInventory().clear();
                    ItemStack old=(ItemStack)call("egg",new Class<?>[]{String.class,java.util.UUID.class},"trex",java.util.UUID.fromString("8b26dbfd-7e37-4304-8362-ccc4816aee1f"));old.setAmount(16);
                    var meta=old.getItemMeta();meta.setItemModel(null);meta.displayName(net.kyori.adventure.text.Component.text("Family hatch egg"));
                    meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Auf erlaubtem Boden platzieren · 20 Minuten Brutzeit"),net.kyori.adventure.text.Component.text("Wächst in 15 Minuten heran · Futter beschleunigt das Wachstum")));
                    meta.getPersistentDataContainer().set(key("fixture-preserve"),PersistentDataType.STRING,"keep");old.setItemMeta(meta);p.getInventory().setItem(0,old);
                    ItemStack custom=old.clone();meta=custom.getItemMeta();meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("My own egg lore")));custom.setItemMeta(meta);custom.setAmount(7);p.getInventory().setItem(1,custom);
                    p.getInventory().setItem(2,new ItemStack(Material.SNIFFER_EGG,9));
                    ItemStack mosa=(ItemStack)call("egg",new Class<?>[]{String.class,java.util.UUID.class},"mosasaurus",p.getUniqueId());meta=mosa.getItemMeta();meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Nur Kreativmodus in der Testwelt · im tiefen Wasser rechtsklicken"),net.kyori.adventure.text.Component.text("Erzeugt einen wilden Mosasaurus · nicht zähmbar oder reitbar")));mosa.setItemMeta(meta);p.getInventory().setItem(3,mosa);
                    Block block=p.getWorld().getBlockAt(6,4,6);block.setType(Material.CHEST);var chest=(org.bukkit.block.Chest)block.getState();
                    ItemStack prototype=old.clone();meta=prototype.getItemMeta();meta.setItemModel(new org.bukkit.NamespacedKey("arkdinos","egg_trex"));meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("Testwelt: 5 Minuten Brutzeit · 5 Minuten Wachstum"),net.kyori.adventure.text.Component.text("Hauptwelt: 20 Minuten Brutzeit · 15 Minuten Wachstum"),net.kyori.adventure.text.Component.text("Auf erlaubtem Boden platzieren · Futter beschleunigt das Wachstum")));prototype.setItemMeta(meta);chest.getInventory().setItem(0,prototype);chest.getInventory().setItem(3,mosa.clone());
                    sender.sendMessage("seeded=true");
                }
                case "open-legacy-chest" -> {Player p=getServer().getPlayerExact(args[1]);p.openInventory(((org.bukkit.block.Chest)p.getWorld().getBlockAt(6,4,6).getState()).getInventory());}
                case "close" -> getServer().getPlayerExact(args[1]).closeInventory();
                case "legacy-egg-state" -> {
                    Player p=getServer().getPlayerExact(args[1]);var inv=args.length>2 && args[2].equals("chest")?((org.bukkit.block.Chest)p.getWorld().getBlockAt(6,4,6).getState()).getInventory():p.getInventory();
                    var item=inv.getItem(0);var meta=item.getItemMeta();
                    sender.sendMessage("amount="+item.getAmount()+";model="+meta.getItemModel()+";owner="+meta.getPersistentDataContainer().get(key("egg-owner"),PersistentDataType.STRING)+";species="+meta.getPersistentDataContainer().get(key("egg-species"),PersistentDataType.STRING)
                        +";name="+net.kyori.adventure.text.Component.text("Family hatch egg").equals(meta.displayName())+";extra="+meta.getPersistentDataContainer().get(key("fixture-preserve"),PersistentDataType.STRING)+";lore="+meta.lore()
                        +";customLore="+(inv.getItem(1)!=null && java.util.List.of(net.kyori.adventure.text.Component.text("My own egg lore")).equals(inv.getItem(1).getItemMeta().lore()))
                        +";vanilla="+(inv.getItem(2)!=null && inv.getItem(2).getAmount()==9 && !inv.getItem(2).hasItemMeta())+";mosaLore="+(inv.getItem(3)==null?"none":inv.getItem(3).getItemMeta().lore()));
                }
                case "egg" -> { Player p=getServer().getPlayerExact(args[1]);p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand((ItemStack)call("egg",new Class<?>[]{String.class,java.util.UUID.class},args[2],p.getUniqueId())); }
                case "menu" -> call("command",new Class<?>[]{Player.class},getServer().getPlayerExact(args[1]));
                case "click" -> {
                    Player p=getServer().getPlayerExact(args[1]);var event=new InventoryClickEvent(p.getOpenInventory(),InventoryType.SlotType.CONTAINER,Integer.parseInt(args[2]),ClickType.valueOf(args[3]),InventoryAction.PICKUP_ALL);
                    getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());
                }
                case "drag" -> {
                    Player p=getServer().getPlayerExact(args[1]);var event=new InventoryDragEvent(p.getOpenInventory(),new ItemStack(Material.AIR),new ItemStack(Material.SNIFFER_EGG),false,Map.of(0,new ItemStack(Material.SNIFFER_EGG)));
                    getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());
                }
                case "place" -> {
                    Player p=getServer().getPlayerExact(args[1]);Block block=p.getWorld().getBlockAt(Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4]));var old=block.getState();
                    block.setType(Material.SNIFFER_EGG,false);var event=new BlockPlaceEvent(block,old,block.getRelative(0,-1,0),p.getInventory().getItemInMainHand(),p,args.length<6 || Boolean.parseBoolean(args[5]),EquipmentSlot.HAND);
                    getServer().getPluginManager().callEvent(event);
                    if(event.isCancelled() || !event.canBuild())old.update(true,false);
                    else if(p.getGameMode()!=org.bukkit.GameMode.CREATIVE) { var item=p.getInventory().getItemInMainHand();item.setAmount(item.getAmount()-1);p.getInventory().setItemInMainHand(item); }
                    sender.sendMessage("cancelled="+event.isCancelled()+";block="+block.getType()+";nest="+block.getChunk().getPersistentDataContainer().has(key("nest_"+block.getX()+"_"+block.getY()+"_"+block.getZ())));
                }
                case "deny-place" -> denyPlacement=Boolean.parseBoolean(args[1]);
                case "deny-spawn" -> denySpawn=Boolean.parseBoolean(args[1]);
                case "spawn-identity" -> sender.sendMessage(spawnIdentity);
                case "drops" -> {
                    int count=0;for(Entity entity:getServer().getWorld(args[1]).getEntities())if(entity instanceof org.bukkit.entity.Item item && item.getItemStack().hasItemMeta()
                            && item.getItemStack().getItemMeta().getPersistentDataContainer().has(key("egg-species")))count+=item.getItemStack().getAmount();
                    sender.sendMessage("droppedEggs="+count);
                }
                case "nest","legacy-nest","ready","grow","fade","break","piston","explode" -> {
                    WorldBlock location=block(args);Block block=location.block();var data=block.getChunk().getPersistentDataContainer();var nestKey=key("nest_"+block.getX()+"_"+block.getY()+"_"+block.getZ());var nest=data.get(nestKey,PersistentDataType.TAG_CONTAINER);
                    if(args[0].equals("ready")) { nest.set(key("hatch-at"),PersistentDataType.LONG,0L);data.set(nestKey,PersistentDataType.TAG_CONTAINER,nest);call("tick",new Class<?>[]{}); }
                    if(args[0].equals("legacy-nest")) { nest.remove(key("incubation-duration"));if(args.length>6)nest.set(key("incubation-duration"),PersistentDataType.LONG,Long.parseLong(args[6]));nest.set(key("hatch-at"),PersistentDataType.LONG,System.currentTimeMillis()+Long.parseLong(args[5]));data.set(nestKey,PersistentDataType.TAG_CONTAINER,nest);call("tick",new Class<?>[]{}); }
                    if(args[0].equals("grow")) { var event=new BlockGrowEvent(block,block.getState());getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());return true; }
                    if(args[0].equals("fade")) { var state=block.getState();state.setType(Material.AIR);var event=new BlockFadeEvent(block,state);getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());return true; }
                    if(args[0].equals("break")) { var event=new BlockBreakEvent(block,getServer().getPlayerExact(args[5]));getServer().getPluginManager().callEvent(event);if(!event.isCancelled())block.setType(Material.AIR,false);sender.sendMessage("cancelled="+event.isCancelled()+";vanillaDrops="+event.isDropItems());return true; }
                    if(args[0].equals("piston")) { var event=new org.bukkit.event.block.BlockPistonExtendEvent(block.getRelative(-1,0,0),java.util.List.of(block),org.bukkit.block.BlockFace.EAST);getServer().getPluginManager().callEvent(event);sender.sendMessage("cancelled="+event.isCancelled());return true; }
                    if(args[0].equals("explode")) { var origin=block.getRelative(0,1,0);var event=new org.bukkit.event.block.BlockExplodeEvent(origin,origin.getState(),new java.util.ArrayList<>(java.util.List.of(block)),1,org.bukkit.ExplosionResult.DESTROY);if(args.length>5)event.setCancelled(Boolean.parseBoolean(args[5]));getServer().getPluginManager().callEvent(event); }
                    nest=data.get(nestKey,PersistentDataType.TAG_CONTAINER);
                    sender.sendMessage("block="+block.getType()+";nest="+(nest!=null)+";species="+(nest==null?"none":nest.get(key("egg-species"),PersistentDataType.STRING))+";owner="+(nest==null?"none":nest.get(key("egg-owner"),PersistentDataType.STRING))+";hatchAt="+(nest==null?0:nest.getOrDefault(key("hatch-at"),PersistentDataType.LONG,0L))+";duration="+(nest==null?0:nest.getOrDefault(key("incubation-duration"),PersistentDataType.LONG,0L)));
                }
                default -> throw new IllegalArgumentException("Unknown fixture command");
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        return true;
    }
    private record WorldBlock(Block block) { }
    private WorldBlock block(String[] args) { return new WorldBlock(getServer().getWorld(args[1]).getBlockAt(Integer.parseInt(args[2]),Integer.parseInt(args[3]),Integer.parseInt(args[4]))); }
}
