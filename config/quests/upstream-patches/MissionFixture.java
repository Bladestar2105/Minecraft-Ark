package de.ark.questtest;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import me.pikamug.quests.BukkitQuestsPlugin;
import me.pikamug.quests.player.BukkitQuester;
import me.pikamug.quests.quests.Quest;
import me.pikamug.quests.quests.components.BukkitRequirements;
import me.pikamug.quests.convo.misc.NpcOfferQuestPrompt;

/** Disposable loopback fixture only. Never install on a player server. */
public final class MissionFixture extends JavaPlugin {
    public void onEnable() {
        getServer().createWorld(new org.bukkit.WorldCreator("ark_exploration").type(org.bukkit.WorldType.FLAT));
    }
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof org.bukkit.entity.Player || args.length < 2 || !args[1].equals("QuestNameProbe")) return false;
        var player=getServer().getPlayerExact(args[1]);
        var plugin=(BukkitQuestsPlugin)getServer().getPluginManager().getPlugin("Quests");
        var quester=(BukkitQuester)plugin.getQuester(player.getUniqueId());
        switch(args[0]) {
            case "seed" -> {
                // Seed prerequisite history solely for this fresh synthetic actor; never grants rewards.
                var completed=new ArrayList<Quest>();
                for(int i=2;i<args.length;i++)completed.add(Objects.requireNonNull(plugin.getQuestById(args[i])));
                quester.setCompletedQuests(completed);
            }
            case "npc" -> {
                var prompt=new NpcOfferQuestPrompt(player.getUniqueId(),plugin,new LinkedList<>(plugin.getLoadedQuests()),"Fixture");
                prompt.start();
                sender.sendMessage("npcOrder="+prompt.getNpcQuests().stream().map(Quest::getId).toList());
            }
            case "cost" -> {
                var requirements=(BukkitRequirements)plugin.getQuestById("lagermaterial").getRequirements();
                requirements.setItems(List.of(new ItemStack(Material.STONE,1)));
                requirements.setRemoveItems(List.of(true));
                player.getInventory().addItem(new ItemStack(Material.STONE,1));
            }
            case "condition" -> {
                var quest=plugin.getQuestById("werkzeugmacher");var original=player.getLocation();
                if(!quester.meetsCondition(quest,false))throw new AssertionError("Survival world unexpectedly paused");
                var excluded=getServer().createWorld(new org.bukkit.WorldCreator("ark_creative").type(org.bukkit.WorldType.FLAT));
                try {
                    player.teleport(excluded.getSpawnLocation());
                    if(quester.meetsCondition(quest,false))throw new AssertionError("Excluded creative world grants progress");
                } finally {player.teleport(original);}
                if(!quester.meetsCondition(quest,false))throw new AssertionError("Survival world did not resume");
                sender.sendMessage("worldCondition=allowed-paused-resumed");
            }
            case "collection" -> {
                var seen=new HashSet<String>();
                for(var quest:plugin.getLoadedQuests()) {
                    if(!seen.containsAll(quest.getRequirements().getNeededQuestIds()))throw new AssertionError("Prerequisite after dependent: "+quest.getId());
                    seen.add(quest.getId());
                }
                var original=new ArrayList<>(plugin.getLoadedQuests());var doubled=new ArrayList<>(original);doubled.addAll(original);
                plugin.setLoadedQuests(doubled);
                if(!original.equals(new ArrayList<>(plugin.getLoadedQuests())))throw new AssertionError("Order/uniqueness changed");
                // Existing reload uses clear/add/set; those mutation operations must remain supported.
                plugin.getLoadedQuests().clear();plugin.getLoadedQuests().addAll(original);plugin.setLoadedQuests(plugin.getLoadedQuests());
                sender.sendMessage("collectionOrder="+plugin.getLoadedQuests().stream().map(Quest::getId).toList());
            }
            case "invalid" -> {
                var a=plugin.getQuestById("werkzeugmacher");var b=plugin.getQuestById("lagermaterial");
                var ar=a.getRequirements().getNeededQuestIds();var br=b.getRequirements().getNeededQuestIds();
                try {
                    a.getRequirements().setNeededQuestIds(List.of("does_not_exist"));
                    if(quester.canAcceptOffer(a,false))throw new AssertionError("Missing prerequisite accepted");
                    a.getRequirements().setNeededQuestIds(List.of(b.getId()));b.getRequirements().setNeededQuestIds(List.of(a.getId()));
                    if(quester.canAcceptOffer(a,false)||quester.canAcceptOffer(b,false))throw new AssertionError("Cyclic prerequisites accepted");
                    sender.sendMessage("invalidRequirements=closed");
                } finally {a.getRequirements().setNeededQuestIds(ar);b.getRequirements().setNeededQuestIds(br);}
            }
            case "inspect" -> {}
            default -> {return false;}
        }
        sender.sendMessage("loading="+plugin.isLoading()+";loaded="+plugin.getLoadedQuests().size()+";active="+quester.getCurrentQuests().keySet().stream().map(Quest::getId).sorted().toList()+";completed="+quester.getCompletedQuests().stream().map(Quest::getId).sorted().toList()+";pending="+quester.getQuestIdToTake()+";questpoints="+quester.getQuestPoints()+";experience="+player.getTotalExperience()+";items="+Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum()+";op="+player.isOp()+";permissions="+player.hasPermission("quests.quests")+","+player.hasPermission("quests.list")+","+player.hasPermission("quests.take"));
        return true;
    }
}
