package de.ark.dinos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Dolphin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Sniffer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

final class DinoCare implements Listener, AutoCloseable {
    private static final List<String> EGG_SPECIES=java.util.stream.Stream.concat(CareRules.SPECIES.stream(),java.util.stream.Stream.of("mosasaurus")).toList();
    private final ArkDinos plugin;
    private final EggVisuals visuals;
    private final Set<Chunk> nestingChunks=new HashSet<>();
    private final BukkitTask timer;
    private final NamespacedKey eggSpecies,eggOwner,adultAt,growNext,tameActor,tameFeeds,tameNext,tameExpiry,loveUntil,breedUntil;

    DinoCare(ArkDinos plugin) {
        this.plugin=plugin;
        visuals=new EggVisuals(plugin);
        eggSpecies=key("egg-species");eggOwner=key("egg-owner");adultAt=key("adult-at");growNext=key("grow-next");
        tameActor=key("tame-actor");tameFeeds=key("tame-feeds");tameNext=key("tame-next");tameExpiry=key("tame-expiry");
        loveUntil=key("love-until");breedUntil=key("breed-until");
        for(World world:plugin.getServer().getWorlds())if(plugin.prototype(world))for(Chunk chunk:world.getLoadedChunks())index(chunk);
        timer=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,20,20);
    }

    private NamespacedKey key(String name) { return new NamespacedKey(plugin,name); }
    private static long number(PersistentDataContainer data,NamespacedKey key) { return data.getOrDefault(key,PersistentDataType.LONG,0L); }
    private long adultAt(Entity entity) {
        var data=entity.getPersistentDataContainer();long deadline=number(data,adultAt);
        if(!data.has(adultAt,PersistentDataType.LONG))return 0;
        long duration=CareRules.GROWTH_MS;
        long previous=data.getOrDefault(key("growth-duration"),PersistentDataType.LONG,CareRules.GROWTH_MS);
        if(previous!=duration) {
            long now=System.currentTimeMillis(),oldDeadline=deadline;
            if(deadline>now)deadline=CareRules.deadline(deadline,previous,duration);
            // Model cleanup leaves native size on the old growth clock after removing its base-scale marker.
            if(entity instanceof Mob mob && !SpeciesRules.aquatic(plugin.species(entity)) && !data.has(key("model-original-scale"))) {
                var scale=mob.getAttribute(Attribute.SCALE);
                if(scale!=null)scale.setBaseValue(scale.getBaseValue()*DinoGrowth.scale(deadline,now,duration)/DinoGrowth.scale(oldDeadline,now,previous));
            }
            data.set(adultAt,PersistentDataType.LONG,deadline);data.set(key("growth-duration"),PersistentDataType.LONG,duration);
        }
        return deadline;
    }
    boolean adult(Entity entity) { return adultAt(entity)<=System.currentTimeMillis(); }
    void restore(Entity entity) { adultAt(entity); }
    private void message(Player player,String message) { player.sendActionBar(Component.text(message)); }
    private void hearts(Entity entity) { entity.getWorld().spawnParticle(Particle.HEART,entity.getLocation().add(0,entity.getHeight(),0),8,.5,.5,.5); }
    private void consume(Player player) {
        var item=player.getInventory().getItemInMainHand();
        item.setAmount(item.getAmount()-1);player.getInventory().setItemInMainHand(item);
    }
    private String instructions(String species) {
        String timing=CareRules.requiredFeeds(species)+"× ";
        String interval=" ("+(CareRules.feedCooldown(species)/1000)+" s Abstand), dann ";
        return switch(species) {
            case "trex","velociraptor","dilophosaurus" -> timing+"rohes Fleisch"+interval+"1 Knochen.";
            case "pteranodon" -> timing+"roher Fisch"+interval+"1 Seegras.";
            default -> timing+"Weizen, Farn oder Löwenzahn"+interval+"1 Fackelblumensamen.";
        };
    }
    private String finisherLabel(String species) { return switch(CareRules.finisher(species)) {
        case "BONE" -> "1 Knochen";case "SEAGRASS" -> "1 Seegras";default -> "1 Fackelliliensamen";
    }; }
    private CareRules.Progress progress(String species,PersistentDataContainer data) {
        String actor=data.get(tameActor,PersistentDataType.STRING);
        int feeds=data.getOrDefault(tameFeeds,PersistentDataType.INTEGER,0);
        return actor==null || feeds<1 || feeds>CareRules.requiredFeeds(species) ? null : new CareRules.Progress(actor,feeds,number(data,tameNext),number(data,tameExpiry));
    }
    private void progress(PersistentDataContainer data,CareRules.Progress progress) {
        if(progress==null) { for(var key:List.of(tameActor,tameFeeds,tameNext,tameExpiry))data.remove(key);return; }
        data.set(tameActor,PersistentDataType.STRING,progress.actor());data.set(tameFeeds,PersistentDataType.INTEGER,progress.feeds());
        data.set(tameNext,PersistentDataType.LONG,progress.nextFeedAt());data.set(tameExpiry,PersistentDataType.LONG,progress.expiresAt());
    }

    boolean interact(Mob mob,Player player) {
        String species=plugin.species(mob);
        if(!plugin.prototype(mob) || species==null || !CareRules.SPECIES.contains(species))return false;
        var item=player.getInventory().getItemInMainHand();String food=item.getType().name();
        if(plugin.owned(mob)) {
            if(!CareRules.primary(species,food) && !CareRules.finisher(species,food))return false;
            if(!plugin.owner(mob).equals(player.getUniqueId().toString()) || !plugin.survival(player)) {
                message(player,"Nur der Besitzer kann sein Urtier im Survival füttern.");return true;
            }
            long now=System.currentTimeMillis();var data=mob.getPersistentDataContainer();
            if(!adult(mob)) {
                if(!CareRules.primary(species,food)) { message(player,"Jungtiere wachsen mit ihrem Lieblingsfutter schneller.");return true; }
                if(number(data,growNext)>now) { message(player,"Nächstes Wachstumsfutter in "+((number(data,growNext)-now+999)/1000)+" s.");return true; }
                long deadline=Math.max(now,number(data,adultAt)-CareRules.GROWTH_FEED_MS);
                data.set(adultAt,PersistentDataType.LONG,deadline);
                data.set(growNext,PersistentDataType.LONG,now+CareRules.GROWTH_FEED_COOLDOWN_MS);
                consume(player);hearts(mob);
                message(player,deadline<=now?"Dein Urtier ist jetzt erwachsen.":"Wachstum beschleunigt. Noch "+((deadline-now+59_999)/60_000)+" Minuten.");
                return true;
            }
            if(!CareRules.primary(species,food)) { message(player,"Zum Paaren einmal das Lieblingsfutter geben.");return true; }
            if(number(data,breedUntil)>now) { message(player,"Paarungspause: noch "+((number(data,breedUntil)-now+999)/1000)+" s.");return true; }
            if(number(data,loveUntil)>now) { message(player,"Bereit zur Paarung: gleichartige Tiere desselben Besitzers zusammenführen.");return true; }
            consume(player);data.set(loveUntil,PersistentDataType.LONG,now+CareRules.LOVE_MS);hearts(mob);
            for(Entity entity:mob.getNearbyEntities(6,6,6))if(entity instanceof Mob partner && plugin.prototype(partner) && plugin.owned(partner)
                    && CareRules.pair(parent(mob),parent(partner),mob.getLocation().distanceSquared(partner.getLocation()),now)) {
                data.set(breedUntil,PersistentDataType.LONG,now+CareRules.BREED_COOLDOWN_MS);data.remove(loveUntil);
                var other=partner.getPersistentDataContainer();other.set(breedUntil,PersistentDataType.LONG,now+CareRules.BREED_COOLDOWN_MS);other.remove(loveUntil);
                give(player,egg(species,player.getUniqueId()));hearts(partner);message(player,"Ein Dino-Ei liegt in deinem Inventar. Brutzeit: "+(CareRules.INCUBATION_MS/60_000)+" Minuten.");return true;
            }
            message(player,"Paarungsbereit für 60 s. Zweites erwachsenes Tier derselben Art füttern.");return true;
        }
        if(!plugin.survival(player)) { message(player,"Wildtiere werden nur im Survival gezähmt. "+instructions(species));return true; }
        long now=System.currentTimeMillis();var data=mob.getPersistentDataContainer();
        int required=CareRules.requiredFeeds(species);
        var feeding=CareRules.feed(species,food,player.getUniqueId().toString(),progress(species,data),now);
        switch(feeding.action()) {
            case FEED -> { consume(player);progress(data,feeding.progress());hearts(mob);message(player,"Zähmen: "+feeding.progress().feeds()+"/"+required+". "+(feeding.progress().feeds()==required?"Jetzt: "+finisherLabel(species):"Nächstes Futter in "+(CareRules.feedCooldown(species)/1000)+" s.")); }
            case FINISH -> {
                if(plugin.tame(mob,player)) { consume(player);progress(data,null);message(player,"Gezähmt! Schleichen + Interagieren: Bleiben / Folgen."); }
                else message(player,"Zähmen gerade nicht möglich. Futter bleibt erhalten.");
            }
            case LOCKED -> message(player,"Dieses Tier wird bereits von jemand anderem gezähmt (5 Minuten Schutz).");
            case WAIT -> message(player,"Zähmen: "+feeding.progress().feeds()+"/"+required+". Noch "+((feeding.progress().nextFeedAt()-now+999)/1000)+" s warten.");
            case READY -> message(player,"Zähmen: "+required+"/"+required+". Jetzt: "+finisherLabel(species)+".");
            default -> message(player,instructions(species));
        }
        return true;
    }
    private CareRules.Parent parent(Mob mob) {
        var data=mob.getPersistentDataContainer();
        return new CareRules.Parent(plugin.species(mob),plugin.owner(mob),adult(mob),number(data,loveUntil),number(data,breedUntil));
    }

    ItemStack egg(String species,UUID owner) {
        if(!EGG_SPECIES.contains(species))throw new IllegalArgumentException("Unknown dinosaur species");
        ItemStack item=new ItemStack(Material.SNIFFER_EGG);var meta=item.getItemMeta();
        meta.displayName(Component.text("Dino-Ei · "+label(species)));
        meta.setItemModel(new NamespacedKey("arkdinos","egg_"+species));
        meta.lore(eggLore(species));
        meta.getPersistentDataContainer().set(eggSpecies,PersistentDataType.STRING,species);
        meta.getPersistentDataContainer().set(eggOwner,PersistentDataType.STRING,owner.toString());item.setItemMeta(meta);return item;
    }
    private List<Component> eggLore(String species) {
        return species.equals("mosasaurus")
                ? List.of(Component.text("Nur Kreativmodus · im tiefen Wasser rechtsklicken"),Component.text("Erzeugt einen wilden Mosasaurus · nicht zähmbar oder reitbar"))
                : List.of(Component.text("20 Minuten Brutzeit · 15 Minuten Wachstum"),Component.text("Auf erlaubtem Boden platzieren · Futter beschleunigt das Wachstum"));
    }
    private void refreshEggs(Inventory inventory) {
        for(int slot=0;slot<inventory.getSize();slot++) {
            ItemStack item=inventory.getItem(slot);if(item==null || item.getType()!=Material.SNIFFER_EGG || !item.hasItemMeta())continue;
            var meta=item.getItemMeta();String species=meta.getPersistentDataContainer().get(eggSpecies,PersistentDataType.STRING);
            if(species==null || !EGG_SPECIES.contains(species))continue;
            var model=new NamespacedKey("arkdinos","egg_"+species);boolean changed=!model.equals(meta.getItemModel());
            if(List.of(Component.text("Auf erlaubtem Boden platzieren · 20 Minuten Brutzeit"),Component.text("Wächst in 15 Minuten heran · Futter beschleunigt das Wachstum")).equals(meta.lore())
                    || List.of(Component.text("Testwelt: 5 Minuten Brutzeit · 5 Minuten Wachstum"),Component.text("Hauptwelt: 20 Minuten Brutzeit · 15 Minuten Wachstum"),Component.text("Auf erlaubtem Boden platzieren · Futter beschleunigt das Wachstum")).equals(meta.lore())
                    || List.of(Component.text("Nur Kreativmodus in der Testwelt · im tiefen Wasser rechtsklicken"),Component.text("Erzeugt einen wilden Mosasaurus · nicht zähmbar oder reitbar")).equals(meta.lore())) { meta.lore(eggLore(species));changed=true; }
            if(changed) { meta.setItemModel(model);item.setItemMeta(meta);inventory.setItem(slot,item); }
        }
    }
    private void refreshAfterSwap(Player player) {
        plugin.getServer().getScheduler().runTask(plugin,() -> { if(player.isOnline())refreshEggs(player.getInventory()); });
    }
    @EventHandler public void joined(PlayerJoinEvent event) { refreshAfterSwap(event.getPlayer()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { refreshAfterSwap(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void changedMode(PlayerGameModeChangeEvent event) { refreshAfterSwap(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void opened(InventoryOpenEvent event) { refreshEggs(event.getPlayer().getInventory());refreshEggs(event.getInventory()); }
    private String label(String species) { return switch(species) {
        case "trex" -> "T-Rex";case "triceratops" -> "Triceratops";case "stegosaurus" -> "Stegosaurus";
        case "brachiosaurus" -> "Brachiosaurus";case "velociraptor" -> "Velociraptor";case "dilophosaurus" -> "Dilophosaurus";case "mosasaurus" -> "Mosasaurus";default -> "Pteranodon";
    }; }
    private void give(Player player,ItemStack item) {
        player.getInventory().addItem(item).values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(),extra));
    }
    private static final class EggMenu implements InventoryHolder {
        final UUID owner;Inventory inventory;boolean claimed;
        EggMenu(UUID owner) { this.owner=owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
    boolean command(Player player) {
        if(player.getGameMode()!=GameMode.CREATIVE || !plugin.prototype(player)) {
            player.sendMessage(Component.text("Dino-Eier gibt es nur im Kreativmodus einer Dino-Welt."));return true;
        }
        EggMenu menu=new EggMenu(player.getUniqueId());menu.inventory=plugin.getServer().createInventory(menu,9,Component.text("Dino-Eier"));
        for(int i=0;i<EGG_SPECIES.size();i++)menu.inventory.setItem(i,egg(EGG_SPECIES.get(i),player.getUniqueId()));
        player.openInventory(menu.inventory);return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void menuClick(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof EggMenu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId()) || menu.claimed
                || player.getGameMode()!=GameMode.CREATIVE || !plugin.prototype(player)
                || event.getRawSlot()<0 || event.getRawSlot()>=EGG_SPECIES.size()
                || event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT)return;
        menu.claimed=true;String species=EGG_SPECIES.get(event.getRawSlot());
        plugin.getServer().getScheduler().runTask(plugin,() -> {
            if(player.isOnline() && player.getGameMode()==GameMode.CREATIVE && plugin.prototype(player)
                    && player.getOpenInventory().getTopInventory()==menu.inventory) {
                player.closeInventory();give(player,egg(species,player.getUniqueId()));
            }
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void menuDrag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof EggMenu)event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void wildEgg(PlayerInteractEvent event) {
        var item=event.getItem();var meta=item==null?null:item.getItemMeta();
        if(meta==null || !"mosasaurus".equals(meta.getPersistentDataContainer().get(eggSpecies,PersistentDataType.STRING))
                || event.getAction()!=Action.RIGHT_CLICK_BLOCK && event.getAction()!=Action.RIGHT_CLICK_AIR)return;
        boolean denied=event.useItemInHand()==Event.Result.DENY || event.getAction()==Action.RIGHT_CLICK_BLOCK && event.useInteractedBlock()==Event.Result.DENY;
        event.setCancelled(true);
        if(denied || event.getHand()!=EquipmentSlot.HAND)return;
        Player player=event.getPlayer();
        if(player.getGameMode()!=GameMode.CREATIVE || !plugin.prototype(player)) { message(player,"Mosasaurus-Eier sind nur im Kreativmodus einer Dino-Welt nutzbar.");return; }
        var target=player.rayTraceBlocks(6,FluidCollisionMode.ALWAYS);
        Block block=target==null?null:target.getHitBlock();
        if(block==null || block.getType()!=Material.WATER) { message(player,"Im tiefen, freien Wasser rechtsklicken. Das Ei bleibt erhalten.");return; }
        var point=block.getLocation().add(.5,.5,.5);point.setYaw(player.getLocation().getYaw());
        if(!DinoAquatic.spawnSpace(point)) { message(player,"Der Mosasaurus braucht tiefes, weiträumig freies Wasser. Das Ei bleibt erhalten.");return; }
        var placement=new BlockPlaceEvent(block,block.getState(),block,item,player,true,EquipmentSlot.HAND);
        plugin.getServer().getPluginManager().callEvent(placement);
        if(placement.isCancelled() || !placement.canBuild())return;
        Dolphin mob=block.getWorld().spawn(point,Dolphin.class,CreatureSpawnEvent.SpawnReason.SPAWNER_EGG,false,entity -> {
            entity.getPersistentDataContainer().set(key("species"),PersistentDataType.STRING,"mosasaurus");
            entity.addScoreboardTag("ark_dino");entity.addScoreboardTag("ark_dino_wild");
            entity.setPersistent(true);entity.setRemoveWhenFarAway(false);
        });
        if(!mob.isValid() || mob.isDead())return;
        plugin.restore(mob);message(player,"Wilder Mosasaurus erzeugt. Nicht zähmbar oder reitbar.");
    }

    private record Nest(String species,UUID owner,long hatchAt) { }
    private NamespacedKey nestKey(Block block) { return key("nest_"+block.getX()+"_"+block.getY()+"_"+block.getZ()); }
    private boolean nestKey(NamespacedKey key) { return key.getNamespace().equals(eggSpecies.getNamespace()) && key.getKey().startsWith("nest_"); }
    private Nest read(PersistentDataContainer data) {
        if(data==null)return null;
        String species=data.get(eggSpecies,PersistentDataType.STRING),owner=data.get(eggOwner,PersistentDataType.STRING);
        if(species==null || !CareRules.SPECIES.contains(species) || owner==null)return null;
        try { return new Nest(species,UUID.fromString(owner),number(data,key("hatch-at"))); }
        catch(IllegalArgumentException error) { return null; }
    }
    private Nest nest(Block block) {
        if(!plugin.prototype(block.getWorld()) || !block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4))return null;
        var data=block.getChunk().getPersistentDataContainer().get(nestKey(block),PersistentDataType.TAG_CONTAINER);
        Nest nest=read(data);if(nest==null)return null;
        return normalize(block.getChunk(),nestKey(block),data,nest);
    }
    private Nest normalize(Chunk chunk,NamespacedKey nestKey,PersistentDataContainer data,Nest nest) {
        long duration=CareRules.INCUBATION_MS;
        long previous=data.getOrDefault(key("incubation-duration"),PersistentDataType.LONG,CareRules.INCUBATION_MS);
        if(previous==duration)return nest;
        long deadline=CareRules.deadline(nest.hatchAt(),previous,duration);
        data.set(key("hatch-at"),PersistentDataType.LONG,deadline);data.set(key("incubation-duration"),PersistentDataType.LONG,duration);
        chunk.getPersistentDataContainer().set(nestKey,PersistentDataType.TAG_CONTAINER,data);
        return new Nest(nest.species(),nest.owner(),deadline);
    }
    private void remove(Block block) { visuals.remove(block);block.getChunk().getPersistentDataContainer().remove(nestKey(block)); }
    private void index(Chunk chunk) {
        if(plugin.prototype(chunk.getWorld()) && chunk.getPersistentDataContainer().getKeys().stream().anyMatch(this::nestKey))nestingChunks.add(chunk);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void validatePlacement(BlockPlaceEvent event) {
        var meta=event.getItemInHand().getItemMeta();
        if(meta==null || !meta.getPersistentDataContainer().has(eggSpecies))return;
        if("mosasaurus".equals(meta.getPersistentDataContainer().get(eggSpecies,PersistentDataType.STRING)) && event.getBlockPlaced().getType()==Material.WATER)return;
        if(event.getBlockPlaced().getType()!=Material.SNIFFER_EGG || !plugin.prototype(event.getBlock().getWorld())
                || event.getPlayer().getGameMode()!=GameMode.CREATIVE && !plugin.survival(event.getPlayer()) || read(meta.getPersistentDataContainer())==null) {
            event.setCancelled(true);message(event.getPlayer(),"Dino-Eier können nur in einer Dino-Welt ausgebrütet werden.");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(BlockPlaceEvent event) {
        if(!event.canBuild() || event.getBlockPlaced().getType()!=Material.SNIFFER_EGG || !plugin.prototype(event.getBlock().getWorld()))return;
        var meta=event.getItemInHand().getItemMeta();Nest nest=meta==null?null:read(meta.getPersistentDataContainer());if(nest==null)return;
        var chunk=event.getBlock().getChunk();var data=chunk.getPersistentDataContainer().getAdapterContext().newPersistentDataContainer();
        data.set(eggSpecies,PersistentDataType.STRING,nest.species());data.set(eggOwner,PersistentDataType.STRING,nest.owner().toString());
        long duration=CareRules.INCUBATION_MS;
        data.set(key("hatch-at"),PersistentDataType.LONG,System.currentTimeMillis()+duration);data.set(key("incubation-duration"),PersistentDataType.LONG,duration);
        chunk.getPersistentDataContainer().set(nestKey(event.getBlock()),PersistentDataType.TAG_CONTAINER,data);nestingChunks.add(chunk);
        message(event.getPlayer(),"Dino-Ei platziert. Es schlüpft in "+(duration/60_000)+" Minuten; die Welt darf entladen werden.");
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void chunkLoaded(ChunkLoadEvent event) { index(event.getChunk()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void chunkUnloaded(ChunkUnloadEvent event) { visuals.unload(event.getChunk());nestingChunks.remove(event.getChunk()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(BlockGrowEvent event) { if(nest(event.getBlock())!=null)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fade(BlockFadeEvent event) { if(nest(event.getBlock())!=null)event.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breaking(BlockBreakEvent event) { if(nest(event.getBlock())!=null) { event.setDropItems(false);event.setExpToDrop(0); } }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent event) {
        Nest nest=nest(event.getBlock());if(nest==null)return;
        remove(event.getBlock());
        if(event.getPlayer().getGameMode()!=GameMode.CREATIVE)event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(.5,.5,.5),egg(nest.species(),nest.owner()));
    }
    private void exploded(List<Block> blocks) {
        for(Block block:blocks)if(nest(block)!=null) { remove(block);block.setType(Material.AIR,false); }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void explosion(EntityExplodeEvent event) { exploded(event.blockList()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void explosion(BlockExplodeEvent event) { exploded(event.blockList()); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent event) {
        if(nest(event.getBlock().getRelative(event.getDirection()))!=null || event.getBlocks().stream().anyMatch(b -> nest(b)!=null || nest(b.getRelative(event.getDirection()))!=null))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonRetractEvent event) {
        if(event.getBlocks().stream().anyMatch(b -> nest(b)!=null || nest(b.getRelative(event.getDirection()))!=null))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void eggInfo(PlayerInteractEvent event) {
        if(event.getClickedBlock()==null)return;Nest nest=nest(event.getClickedBlock());if(nest==null)return;
        long remaining=(nest.hatchAt()-System.currentTimeMillis()+999)/1000;
        message(event.getPlayer(),label(nest.species())+"-Ei · "+(remaining>0?"noch "+remaining+" s Brutzeit.":"wartet auf freien Platz und erlaubten Schlupf."));
    }
    private void tick() {
        long now=System.currentTimeMillis();
        Set<Block> activeNests=new HashSet<>();
        for(Chunk chunk:Set.copyOf(nestingChunks)) {
            if(!chunk.isLoaded() || !plugin.prototype(chunk.getWorld())) { nestingChunks.remove(chunk);continue; }
            var data=chunk.getPersistentDataContainer();boolean found=false;
            for(NamespacedKey key:Set.copyOf(data.getKeys()))if(nestKey(key)) {
                var nestData=data.get(key,PersistentDataType.TAG_CONTAINER);Nest nest=read(nestData);String[] position=key.getKey().split("_");
                if(nest==null || position.length!=4) { data.remove(key);continue; }
                nest=normalize(chunk,key,nestData,nest);
                Block block;
                try {
                    int x=Integer.parseInt(position[1]),y=Integer.parseInt(position[2]),z=Integer.parseInt(position[3]);
                    if(x>>4!=chunk.getX() || z>>4!=chunk.getZ() || y<chunk.getWorld().getMinHeight() || y>=chunk.getWorld().getMaxHeight()) { data.remove(key);continue; }
                    block=chunk.getBlock(x&15,y,z&15);
                } catch(NumberFormatException error) { data.remove(key);continue; }
                if(block.getType()!=Material.SNIFFER_EGG) { data.remove(key);continue; }
                activeNests.add(block);visuals.show(block,nest.species());
                found=true;if(nest.hatchAt()<=now)hatch(block,nest,now);
            }
            if(!found)nestingChunks.remove(chunk);
        }
        visuals.retain(activeNests);
    }
    private boolean hatchSpace(Block egg,String species) {
        String model="ark_"+species;
        var visible=DinoSizes.centeredClearanceBounds(model);
        long now=System.currentTimeMillis();
        double growth=DinoGrowth.scale(now+CareRules.GROWTH_MS,now,CareRules.GROWTH_MS);
        // Reserve the visible rig and both the modeled carrier and optional-plugin fallback.
        double nativeScale=Math.max(1,DinoSizes.nativeScale(model));
        double nativeWidth=SpeciesRules.ravager(species)?1.95:1.9;
        double nativeHeight=SpeciesRules.ravager(species)?2.2:1.75;
        double halfWidth=Math.max(visible.width(),nativeWidth*nativeScale)*growth/2;
        double halfLength=Math.max(visible.length(),nativeWidth*nativeScale)*growth/2;
        double height=Math.max(visible.height()+DinoSizes.clearanceMinY(model),nativeHeight*nativeScale)*growth;
        var box=new BoundingBox(egg.getX()+.5-halfWidth,egg.getY(),egg.getZ()+.5-halfLength,
                egg.getX()+.5+halfWidth,egg.getY()+height,egg.getZ()+.5+halfLength);
        World world=egg.getWorld();
        for(int x=(int)Math.floor(box.getMinX())-1;x<=(int)Math.floor(box.getMaxX())+1;x++)for(int z=(int)Math.floor(box.getMinZ())-1;z<=(int)Math.floor(box.getMaxZ())+1;z++) {
            if(!world.isChunkLoaded(x>>4,z>>4))return false;
            for(int y=egg.getY()-1;y<=(int)Math.floor(box.getMaxY());y++) {
                if(y<world.getMinHeight() || y>=world.getMaxHeight())return false;
                if(x==egg.getX() && y==egg.getY() && z==egg.getZ())continue;
                if(world.getBlockAt(x,y,z).getCollisionShape().overlaps(box.clone().shift(-x,-y,-z)))return false;
            }
        }
        return true;
    }
    private void hatch(Block block,Nest nest,long now) {
        if(!hatchSpace(block,nest.species()))return;
        Class<? extends Mob> carrier=SpeciesRules.ravager(nest.species()) ? Ravager.class : Sniffer.class;
        Mob mob=block.getWorld().spawn(block.getLocation().add(.5,0,.5),carrier,CreatureSpawnEvent.SpawnReason.EGG,true,entity -> {
            var data=entity.getPersistentDataContainer();data.set(key("species"),PersistentDataType.STRING,nest.species());
            data.set(key("owner"),PersistentDataType.STRING,nest.owner().toString());data.set(key("stay"),PersistentDataType.BYTE,(byte)1);
            long duration=CareRules.GROWTH_MS;
            data.set(adultAt,PersistentDataType.LONG,now+duration);data.set(key("growth-duration"),PersistentDataType.LONG,duration);
            var scale=entity.getAttribute(Attribute.SCALE);if(scale!=null)scale.setBaseValue(DinoGrowth.scale(now+duration,now,duration));
            entity.addScoreboardTag("ark_dino");entity.addScoreboardTag("ark_dino_tamed");entity.setPersistent(true);entity.setRemoveWhenFarAway(false);
            if(entity instanceof Sniffer sniffer) { sniffer.setAdult();sniffer.setBreed(false); }
        });
        if(!mob.isValid() || mob.isDead())return;
        remove(block);block.setType(Material.AIR,false);plugin.restore(mob);hearts(mob);
    }
    @Override public void close() {
        timer.cancel();visuals.close();nestingChunks.clear();
        for(Player player:plugin.getServer().getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof EggMenu)player.closeInventory();
    }
}
