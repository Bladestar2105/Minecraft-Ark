package de.ark.support;

import com.google.gson.JsonObject;
import de.ark.accounts.api.AccountsApi;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;

public final class ArkSupport extends JavaPlugin implements CommandExecutor,TabCompleter {
    private AccountsApi accounts;
    private AccountsApi.Registration registration;
    private SupportStore store;
    private final ExecutorService io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"ArkSupport-commands");t.setDaemon(true);return t;});
    @Override public void onEnable(){
        try {
            accounts=Objects.requireNonNull(getServer().getServicesManager().load(AccountsApi.class),"ArkAccounts service unavailable");AtomicReference<AccountsApi.Registration> generation=new AtomicReference<>();BooleanSupplier open=()->generation.get()!=null&&generation.get().open();
            store=new SupportStore(getDataFolder().toPath().resolve("support.json"),Instant::now,notice->notifyPlayers(notice,open),open);
            registration=accounts.register(this,"support",new SupportRoutes(store,open),null);generation.set(registration);Objects.requireNonNull(getCommand("support")).setExecutor(this);getCommand("support").setTabCompleter(this);
            getLogger().info("Persistent support module registered with ArkAccounts.");
        }catch(Exception e){getLogger().severe("Support unavailable: "+e.getClass().getSimpleName());getServer().getPluginManager().disablePlugin(this);}
    }
    private void notifyPlayers(SupportStore.Notice notice,BooleanSupplier open){Bukkit.getScheduler().runTask(this,()->{
        if(!open.getAsBoolean())return;Collection<? extends Player> recipients=notice.admins()?Bukkit.getOnlinePlayers():Optional.ofNullable(Bukkit.getPlayer(notice.owner())).map(List::of).orElse(List.of());
        for(Player recipient:recipients){if(notice.admins()&&!recipient.isOp())continue;try{AccountsApi.Identity identity=accounts.identity(recipient);if(!notice.admins()&&!identity.uuid().equals(notice.owner()))continue;}catch(AccountsApi.Failure error){continue;}boolean de=recipient.locale().getLanguage().equals("de");recipient.sendMessage(Component.text((de?"Support-Ticket ":"Support ticket ")+notice.ticket().toString().substring(0,8)+" ("+notice.priority()+"): "+(notice.reply()!=null?(de?"Antwort vom OP:\n":"Reply from OP:\n")+notice.reply():(de?"Neue Aktivität im Portal. Nutze ":"New activity in the portal. Use ")+(notice.admins()?"/web admin":"/web"))));}
    });}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!(sender instanceof Player player)){sender.sendMessage("An authenticated player is required.");return true;}AccountsApi.Identity identity;AccountsApi.Registration generation=registration;
        try{generation.requireOpen();identity=accounts.identity(player);}catch(AccountsApi.Failure error){player.sendMessage(Component.text(error.code));return true;}
        if(args.length==0){player.sendMessage(Component.text("/support [low|normal|high] <message>"));return true;}String priority=Set.of("low","normal","high").contains(args[0].toLowerCase(Locale.ROOT))?args[0].toLowerCase(Locale.ROOT):"normal";int start=Set.of("low","normal","high").contains(args[0].toLowerCase(Locale.ROOT))?1:0;String message=String.join(" ",Arrays.copyOfRange(args,start,args.length));
        try{io.execute(()->{try{generation.requireOpen();JsonObject ticket=store.create(identity.uuid(),identity.name(),priority,message);Bukkit.getScheduler().runTask(this,()->{if(generation.open()&&player.isOnline())player.sendMessage(Component.text((player.locale().getLanguage().equals("de")?"Support-Ticket erstellt: ":"Support ticket created: ")+ticket.get("id").getAsString().substring(0,8)));});}catch(AccountsApi.Failure error){Bukkit.getScheduler().runTask(this,()->{if(generation.open()&&player.isOnline())player.sendMessage(Component.text(error.code));});}});}catch(RejectedExecutionException error){player.sendMessage(Component.text("busy"));}return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args){return args.length==1?List.of("low","normal","high"):List.of();}
    @Override public void onDisable(){if(registration!=null)registration.close();if(store!=null)store.close();io.shutdownNow();}
}
