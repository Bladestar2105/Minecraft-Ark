package de.ark.wardrobe;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.skinsrestorer.api.*;
import net.skinsrestorer.api.property.*;

public final class ArkWardrobe extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    private AccountsApi accounts;
    private AccountsApi.Registration registration;
    private WardrobeStore store;
    private Signers signers;
    private final ExecutorService io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"ArkWardrobe-commands");t.setDaemon(true);return t;});
    @Override public void onEnable() {
        try {
            accounts=Objects.requireNonNull(getServer().getServicesManager().load(AccountsApi.class),"ArkAccounts service unavailable");
            SkinsRestorer skins=SkinsRestorerProvider.get();AtomicReference<AccountsApi.Registration> generation=new AtomicReference<>();BooleanSupplier open=()->generation.get()!=null&&generation.get().open();
            signers=new Signers(getDataFolder().toPath().resolve("signing-secrets.json"),Signers.Endpoints.production(),open);
            store=new WardrobeStore(getDataFolder().toPath(),signers,new WardrobeStore.Gateway() {
                public String currentKey(UUID uuid){return skins.getPlayerStorage().getSkinIdOfPlayer(uuid).filter(id->id.getSkinType()==SkinType.CUSTOM).map(SkinIdentifier::getIdentifier).orElse(null);}
                public boolean select(UUID uuid,String key,WardrobeStore.Property raw) throws Exception {
                    require(open);SkinProperty property=SkinProperty.of(raw.value(),raw.signature());skins.getSkinStorage().setCustomSkinData(key,property);require(open);skins.getPlayerStorage().setSkinIdOfPlayer(uuid,SkinIdentifier.ofCustom(key));
                    CompletableFuture<Boolean> applied=new CompletableFuture<>();
                    Bukkit.getScheduler().runTask(ArkWardrobe.this,()->{
                        if(!open.getAsBoolean()){applied.complete(false);return;}Player player=Bukkit.getPlayer(uuid);if(player==null||!eligible(player)){applied.complete(false);return;}
                        skins.getSkinApplier(Player.class).applySkin(player,property);
                        Bukkit.getScheduler().runTaskLater(ArkWardrobe.this,()->applied.complete(open.getAsBoolean()&&matches(player,raw)),5);
                    });
                    try{return applied.get(5,TimeUnit.SECONDS);}catch(TimeoutException e){return false;}
                }
                public boolean applied(UUID uuid,WardrobeStore.Property raw){return onMain(open,()->{Player p=Bukkit.getPlayer(uuid);return p!=null&&matches(p,raw);});}
            },open);
            WardrobeStore library=store;WardrobeRoutes routes=new WardrobeRoutes(store,signers,open);
            registration=accounts.register(this,"wardrobe",routes,identity->library.active(identity.uuid()));generation.set(registration);
            getServer().getPluginManager().registerEvents(this,this);Objects.requireNonNull(getCommand("wardrobe")).setExecutor(this);getCommand("wardrobe").setTabCompleter(this);
            getLogger().info("Personal wardrobe module registered with ArkAccounts.");
        }catch(Exception e){getLogger().severe("Wardrobe unavailable: "+e.getClass().getSimpleName());getServer().getPluginManager().disablePlugin(this);}
    }
    private static void require(BooleanSupplier open){if(!open.getAsBoolean())throw new ApiError(503,"module_unavailable");}
    private boolean eligible(Player player){try{accounts.identity(player);return true;}catch(AccountsApi.Failure error){return false;}}
    private <T>T onMain(BooleanSupplier open,Callable<T> task){require(open);try{Callable<T> guarded=()->{require(open);return task.call();};return Bukkit.isPrimaryThread()?guarded.call():Bukkit.getScheduler().callSyncMethod(this,guarded).get(5,TimeUnit.SECONDS);}catch(Exception e){throw new ApiError(503,"server_unavailable");}}
    private static boolean matches(Player player,WardrobeStore.Property property){return player.isOnline()&&player.getPlayerProfile().getProperties().stream().anyMatch(p->p.getName().equals("textures")&&p.getValue().equals(property.value())&&Objects.equals(p.getSignature(),property.signature()));}
    private void message(Player player,AccountsApi.Registration generation,String english,String german){Bukkit.getScheduler().runTask(this,()->{if(generation.open()&&player.isOnline())player.sendMessage(Component.text(player.locale().getLanguage().equals("de")?german:english));});}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)){sender.sendMessage("An authenticated player is required.");return true;}AccountsApi.Identity identity;AccountsApi.Registration generation=registration;
        try{generation.requireOpen();identity=accounts.identity(player);}catch(AccountsApi.Failure error){player.sendMessage(Component.text(error.code));return true;}
        try {io.execute(()->{
            try {
                generation.requireOpen();
                if(args.length==0||args.length==1&&args[0].equalsIgnoreCase("list")) {
                    JsonArray skins=store.list(identity.uuid()).getAsJsonArray("skins");boolean clickable=args.length==0;
                    Bukkit.getScheduler().runTask(this,()->{if(!generation.open()||!player.isOnline())return;player.sendMessage(Component.text(player.locale().getLanguage().equals("de")?"Deine Garderobe:":"Your wardrobe:"));for(JsonElement entry:skins){JsonObject skin=entry.getAsJsonObject();String name=skin.get("name").getAsString();Component line=Component.text((skin.get("selected").getAsBoolean()?"* ":"- ")+name);if(clickable)line=line.clickEvent(ClickEvent.runCommand("/wardrobe wear "+name));player.sendMessage(line);}});
                } else if(args.length>=2&&args[0].equalsIgnoreCase("wear")) {
                    String target=String.join(" ",Arrays.copyOfRange(args,1,args.length));JsonObject skin=store.list(identity.uuid()).getAsJsonArray("skins").asList().stream().map(JsonElement::getAsJsonObject).filter(s->s.get("name").getAsString().equalsIgnoreCase(target)).findFirst().orElseThrow(()->new ApiError(404,"not_found"));
                    generation.requireOpen();store.activate(identity.uuid(),UUID.fromString(skin.get("id").getAsString()),skin.get("revision").getAsLong());message(player,generation,"Skin preparation queued; view progress in the portal.","Skin-Vorbereitung eingereiht; Fortschritt im Portal anzeigen.");
                } else message(player,generation,"Use /wardrobe list or /wardrobe wear <name>.","Nutze /wardrobe list oder /wardrobe wear <name>.");
            }catch(AccountsApi.Failure error){message(player,generation,error.code,error.code);}catch(Exception e){message(player,generation,"service_unavailable","service_unavailable");}
        });}catch(RejectedExecutionException error){player.sendMessage(Component.text("busy"));}return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){if(!(sender instanceof Player player)||!eligible(player))return List.of();return args.length==1?List.of("list","wear"):args.length>=2&&args[0].equalsIgnoreCase("wear")?store.cachedNames(player.getUniqueId()):List.of();}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void guard(PlayerCommandPreprocessEvent event){String label=CommandPolicy.label(event.getMessage());org.bukkit.command.Command command=getServer().getCommandMap().getCommand(label);boolean owned=command instanceof PluginIdentifiableCommand c&&c.getPlugin().getName().equals("SkinsRestorer");if(!event.getPlayer().hasPermission("arkwardrobe.admin")&&CommandPolicy.privateSkinCommand(event.getMessage(),owned)){event.setCancelled(true);event.getPlayer().sendMessage(Component.text("Use /wardrobe for your personal skins."));}}
    @EventHandler public void commands(PlayerCommandSendEvent event){if(!event.getPlayer().hasPermission("arkwardrobe.admin"))event.getCommands().removeIf(label->CommandPolicy.privateSkinCommand(label,false));}
    @Override public void onDisable(){if(registration!=null)registration.close();if(store!=null)store.close();if(signers!=null)signers.close();io.shutdownNow();}
}
