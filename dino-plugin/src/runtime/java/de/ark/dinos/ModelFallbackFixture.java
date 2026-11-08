package de.ark.dinos;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Mob;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ModelFallbackFixture extends JavaPlugin {
    private DinoModels models;
    private Mob mob;
    private boolean loaded;
    @Override public void onEnable() {
        models = new DinoModels(this);
        for(int x=-1;x<5;x++)getServer().getWorlds().getFirst().getChunkAt(x,0).load();
        mob = getServer().getWorlds().getFirst().getEntities().stream().filter(entity->entity.getScoreboardTags().contains("model_fixture_trex")).map(Mob.class::cast).findFirst().orElse(null);
        loaded=mob!=null;
        if(mob!=null){
            for(var entity:getServer().getWorlds().getFirst().getEntities())if(entity instanceof Mob candidate){
                var species=candidate.getPersistentDataContainer().get(new NamespacedKey(this,"species"),PersistentDataType.STRING);
                if(species!=null)models.attach(candidate,"ark_"+species);
            }
            return;
        }
        mob = (Mob)getServer().getWorlds().getFirst().spawnEntity(getServer().getWorlds().getFirst().getSpawnLocation().add(0,2,0),EntityType.RAVAGER);
        mob.setAI(false);mob.setGravity(false);mob.addScoreboardTag("ark_dino");
        mob.getPersistentDataContainer().set(new NamespacedKey(this,"species"),PersistentDataType.STRING,"trex");
        mob.getAttribute(Attribute.SCALE).setBaseValue(.8);
        mob.getPersistentDataContainer().set(new NamespacedKey(this,"model-original-scale"),PersistentDataType.DOUBLE,1.2);
        models.attach(mob,"ark_trex");models.animation(mob,"walk");
    }
    private String registryNames(Mob mob) {
        try {
            var type=Class.forName("kr.toxicity.model.api.BetterModel");
            var platformType=Class.forName("kr.toxicity.model.api.platform.PlatformEntity");
            var wrap=Class.forName("kr.toxicity.model.api.bukkit.platform.BukkitAdapter").getMethod("adapt",org.bukkit.entity.Entity.class).invoke(null,mob);
            var registry=(java.util.Optional<?>)type.getMethod("registry",platformType).invoke(null,wrap);
            if(registry.isEmpty())return "[]";
            var trackers=(java.util.Collection<?>)registry.get().getClass().getMethod("trackers").invoke(registry.get());
            if(trackers.isEmpty()) {
                registry.get().getClass().getMethod("load").invoke(registry.get());
                trackers=(java.util.Collection<?>)registry.get().getClass().getMethod("trackers").invoke(registry.get());
            }
            var names=new java.util.ArrayList<String>();
            for(var tracker:trackers)names.add((String)tracker.getClass().getMethod("name").invoke(tracker));
            return names.toString();
        } catch(ClassNotFoundException ignored) {return "unavailable";} catch(Exception error){throw new RuntimeException(error);}
    }
    @Override public void onDisable() { models.close(); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(args.length>0&&args[0].equals("release")){mob.eject();sender.sendMessage("released");return true;}
        String trackers=registryNames(mob);
        models.attach(mob,"ark_trex");
        trackers=registryNames(mob);
        String shared="absent";
        for(var entity:getServer().getWorlds().getFirst().getEntities())if(entity instanceof Mob candidate&&entity.getScoreboardTags().contains("model_fixture_stegosaurus")){
            registryNames(candidate);
            models.attach(candidate,"ark_stegosaurus");
            shared=registryNames(candidate);
        }
        var nativeStates=new java.util.ArrayList<String>();
        for(var entity:getServer().getWorlds().getFirst().getEntities())if(entity instanceof Mob candidate){
            String species=candidate.getPersistentDataContainer().get(new NamespacedKey(this,"species"),PersistentDataType.STRING);
            if(species==null||!java.util.Set.of("trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus","mosasaurus").contains(species))continue;
            models.attach(candidate,"ark_"+species);
            nativeStates.add(species+":"+candidate.getAttribute(Attribute.SCALE).getBaseValue()+":"+candidate.isInvisible()+":"+registryNames(candidate));
        }
        var player=getServer().getPlayerExact("ModelRider");
        player.teleport(mob.getLocation().add(0,0,2));
        mob.addPassenger(player);
        sender.sendMessage("loaded="+loaded+";scale="+mob.getAttribute(Attribute.SCALE).getBaseValue()+";invisible="+mob.isInvisible()+";passengers="+mob.getPassengers().size()+";trackers="+trackers+";shared="+shared+";id="+mob.getEntityId()+";nativeAll="+String.join("|",nativeStates));
        return true;
    }
}
