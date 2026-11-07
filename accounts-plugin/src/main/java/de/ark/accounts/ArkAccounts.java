package de.ark.accounts;

import com.google.gson.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.server.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.ClickCallback;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.*;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import de.ark.accounts.api.AccountsApi;
import org.bukkit.plugin.*;
import org.bukkit.event.server.PluginDisableEvent;

public final class ArkAccounts extends JavaPlugin implements Listener,CommandExecutor,TabCompleter,AccountsApi {
    private Sessions sessions;
    private Accounts accounts;
    private PrivateApi api;
    private OperatorSessions operators;
    private final ModuleRegistry modules=new ModuleRegistry();
    private volatile boolean stopping;
    private Object authMe;
    private Method isAuthenticated,checkPassword,changePassword,isRegistered,forceLogout;
    private String portalUrl;
    private final SecretCommands secretCommands=new SecretCommands();
    private final ExecutorService io=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"ArkAccounts-commands");t.setDaemon(true);return t;});
    private final Map<UUID,Long> commandCooldown=new HashMap<>();
    @Override public void onEnable() {
        try {
            secretCommands.install();
            saveDefaultConfig();Path root=getDataFolder().toPath();
            String player=secret("ARK_ACCOUNTS_PLAYER_TOKEN_FILE","player-token-file");String admin=secret("ARK_ACCOUNTS_ADMIN_TOKEN_FILE","admin-token-file");
            portalUrl=System.getenv().getOrDefault("ARK_ACCOUNTS_PLAYER_URL",getConfig().getString("portal-url","http://localhost:8090/play/"));
            URI portal=URI.create(portalUrl);if(!Set.of("http","https").contains(portal.getScheme()) || portal.getHost()==null || portal.getUserInfo()!=null)throw new IllegalArgumentException();
            if(getServer().getPluginManager().getPlugin("AuthMe")!=null) {
                Class<?> type=Class.forName("fr.xephi.authme.api.v3.AuthMeApi");authMe=type.getMethod("getInstance").invoke(null);
                isAuthenticated=type.getMethod("isAuthenticated",Player.class);checkPassword=type.getMethod("checkPassword",String.class,String.class);
                changePassword=type.getMethod("changePassword",String.class,String.class);isRegistered=type.getMethod("isRegistered",String.class);forceLogout=type.getMethod("forceLogout",Player.class);
            }
            sessions=new Sessions(root.resolve("sessions.json"),Instant::now);
            accounts=new Accounts(root.resolve("accounts.json"),sessions,authMe==null?null:new Accounts.Authority() {
                public boolean configured(String name){return authBoolean(isRegistered,name);}
                public boolean check(String name,String password){return authBoolean(checkPassword,name,password);}
                public Accounts.Identity identity(String name) {
                    Accounts.Identity cached=onMain(()->{OfflinePlayer player=Bukkit.getOfflinePlayerIfCached(name);return player!=null&&(player.hasPlayedBefore()||player.isOnline())?new Accounts.Identity(player.getUniqueId(),player.getName()==null?name:player.getName()):null;});
                    if(cached!=null)return cached;
                    try {
                        Object info=((Optional<?>)authMe.getClass().getMethod("getPlayerInfo",String.class).invoke(authMe,name)).orElse(null);
                        if(info==null)return null;
                        Class<?> playerInfo=Class.forName("fr.xephi.authme.api.v3.AuthMePlayer");
                        UUID uuid=(UUID)((Optional<?>)playerInfo.getMethod("getUuid").invoke(info)).orElse(null);
                        return uuid==null?null:new Accounts.Identity(uuid,(String)playerInfo.getMethod("getName").invoke(info));
                    }catch(Exception e){throw new ApiError(503,"authentication_unavailable");}
                }
                public void change(String name,String password) {
                    try {
                        changePassword.invoke(authMe,name,password);
                        boolean confirmed=false;
                        for(int i=0;i<40;i++) {if(authBoolean(checkPassword,name,password)){confirmed=true;break;}Thread.sleep(100);}
                        if(!confirmed)throw new ApiError(503,"password_change_failed");
                        // AuthMe's public admin-change API updates storage but not the online PlayerCache.
                        onMain(()->{Player p=Bukkit.getPlayerExact(name);if(p!=null && eligible(p))forceLogout.invoke(authMe,p);return null;});
                    }catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(503,"password_change_failed");}
                }
            },identity->getLogger().info("audit action=account_password_reset target="+identity.uuid()+" time="+Instant.now()));
            operators=new OperatorSessions(root.resolve("operator-sessions.json"),Instant::now,accounts,uuid->onMain(()->{
                if(authMe==null&&!getServer().getOnlineMode())return false;
                if(authMe!=null&&!getServer().getPluginManager().isPluginEnabled("AuthMe"))throw new ApiError(503,"authentication_unavailable");
                return Bukkit.getOfflinePlayer(uuid).isOp();
            }));
            api=new PrivateApi(new InetSocketAddress(getConfig().getString("bind","0.0.0.0"),getConfig().getInt("port",8101)),player,admin,sessions,accounts,operators,modules,this::profile);
            getServer().getServicesManager().register(AccountsApi.class,this,this,ServicePriority.Normal);
            api.start();getServer().getPluginManager().registerEvents(this,this);
            if(authMe!=null)registerUnregisterEvents();
            for(String name:List.of("web","account")){Objects.requireNonNull(getCommand(name)).setExecutor(this);getCommand(name).setTabCompleter(this);}
            getLogger().info("Private accounts API listening on port "+api.port()+". Optional modules register separately.");
        }catch(Exception e){getLogger().severe("Accounts unavailable: "+(e instanceof ApiError a?a.code:e.getClass().getSimpleName()));getServer().getPluginManager().disablePlugin(this);}
    }
    private String secret(String variable,String option) throws Exception {
        Path file=Path.of(System.getenv().getOrDefault(variable,getConfig().getString(option,"")));
        if(Files.size(file)>4096)throw new IllegalArgumentException();String token=Files.readString(file).strip();
        if(token.length()<32 || token.length()>1024 || token.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isISOControl(c)))throw new IllegalArgumentException();return token;
    }
    private boolean authBoolean(Method method,Object... args){try{return Boolean.TRUE.equals(method.invoke(authMe,args));}catch(Exception e){throw new ApiError(503,"authentication_unavailable");}}
    private boolean eligible(Player player){return !stopping && player.isOnline() && (authMe==null?getServer().getOnlineMode():getServer().getPluginManager().isPluginEnabled("AuthMe")&&authBoolean(isAuthenticated,player));}
    private <T>T onMain(Callable<T> task) {
        if(stopping)throw new ApiError(503,"server_unavailable");
        try {Callable<T> guarded=()->{if(stopping)throw new ApiError(503,"server_unavailable");return task.call();};return Bukkit.isPrimaryThread()?guarded.call():Bukkit.getScheduler().callSyncMethod(this,guarded).get(5,TimeUnit.SECONDS);}
        catch(Exception e){throw new ApiError(503,"server_unavailable");}
    }
    private JsonObject profile(Sessions.Session session) {
        UUID active=modules.selected(new AccountsApi.Identity(session.uuid(),session.name()));JsonObject result=onMain(()->{
            OfflinePlayer player=Bukkit.getOfflinePlayer(session.uuid());JsonObject out=new JsonObject();out.addProperty("uuid",session.uuid().toString());out.addProperty("name",player.getName()==null?session.name():player.getName());out.addProperty("online",player.isOnline());
            JsonObject stats=new JsonObject();stats.addProperty("play_time_ticks",player.getStatistic(Statistic.PLAY_ONE_MINUTE));stats.addProperty("deaths",player.getStatistic(Statistic.DEATHS));stats.addProperty("mob_kills",player.getStatistic(Statistic.MOB_KILLS));stats.addProperty("player_kills",player.getStatistic(Statistic.PLAYER_KILLS));out.add("stats",stats);out.addProperty("observed_at",Instant.now().toString());return out;
        });if(active==null)result.add("active_skin_id",JsonNull.INSTANCE);else result.addProperty("active_skin_id",active.toString());return result;
    }
    private void async(Player player,Runnable task) {
        UUID uuid=player.getUniqueId();String name=player.getName();
        try {io.execute(()->{try {if(!onMain(()->player.isOnline()&&player.getUniqueId().equals(uuid)&&eligible(player)))throw new ApiError(403,"game_login_required");accounts.observe(uuid,name);task.run();}
            catch(Exception e){message(player,e instanceof ApiError a?a.code:"service_unavailable");}});}
        catch(RejectedExecutionException e){message(player,"busy");}
    }
    private void message(Player p,String english,String german){Bukkit.getScheduler().runTask(this,()->{if(p.isOnline())p.sendMessage(Component.text(p.locale().getLanguage().equals("de")?german:english));});}
    private void message(Player p,String code){message(p,"Account: "+code,"Konto: "+code);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player player)){sender.sendMessage("An authenticated player is required.");return true;}
        if(!eligible(player)){message(player,"Sign in to the game first.","Melde dich zuerst im Spiel an.");return true;}
        long now=System.currentTimeMillis();if(commandCooldown.getOrDefault(player.getUniqueId(),0L)>now){message(player,"busy");return true;}commandCooldown.put(player.getUniqueId(),now+1000);
        UUID uuid=player.getUniqueId();String name=player.getName();
        if(command.getName().equals("web")) {
            if(args.length==1 && args[0].equalsIgnoreCase("logout")){async(player,()->{accounts.revoke(uuid);message(player,"All portal sessions signed out.","Alle Portalsitzungen abgemeldet.");});return true;}
            boolean operator=args.length==1&&args[0].equalsIgnoreCase("admin");
            if(args.length!=0&&!operator){message(player,"Use /web, /web admin or /web logout.","Nutze /web, /web admin oder /web logout.");return true;}
            if(operator&&!player.isOp()){message(player,"operator_required");return true;}
            async(player,()->{String code=operator?operators.pair(uuid,name):sessions.pair(uuid,name);Bukkit.getScheduler().runTask(this,()->{if(player.isOnline()) {
                player.sendMessage(Component.text((player.locale().getLanguage().equals("de")?"Portal öffnen: ":"Open portal: ")+(operator?portalUrl.replaceFirst("/play/?$","/"):portalUrl)).clickEvent(ClickEvent.openUrl(operator?portalUrl.replaceFirst("/play/?$","/"):portalUrl)));
                player.sendMessage(Component.text((player.locale().getLanguage().equals("de")?"Einmalcode (5 Minuten): ":"One-time code (5 minutes): ")+code).clickEvent(ClickEvent.copyToClipboard(code)));
            }});});return true;
        }
        if(command.getName().equals("account")) {
            if(args.length!=0){message(player,"Use /account without passwords in chat.","Nutze /account ohne Passwörter im Chat.");return true;}
            async(player,()->Bukkit.getScheduler().runTask(this,()->showPasswordDialog(player)));return true;
        }
        return true;
    }
    private void showPasswordDialog(Player player) {
        boolean de=player.locale().getLanguage().equals("de");UUID owner=player.getUniqueId();
        var base=DialogBase.builder(Component.text(de?"Server- und Portalpasswort":"Server and portal password"))
            .body(List.of(DialogBody.plainMessage(Component.text(de?"12–64 Zeichen. Eingaben werden direkt übertragen, nicht als Chatbefehl. Bei automatisch erzeugtem AuthMe-Passwort aktuelles Feld leer lassen.":"12–64 characters. Inputs are sent directly, outside chat commands. Leave current password empty for an automatically generated AuthMe password."))))
            .inputs(List.of(DialogInput.text("current",Component.text(de?"Aktuelles Passwort":"Current password")).maxLength(1024).build(),DialogInput.text("new",Component.text(de?"Neues Passwort":"New password")).maxLength(128).build(),DialogInput.text("confirm",Component.text(de?"Bestätigung":"Confirmation")).maxLength(128).build())).build();
        var action=DialogAction.customClick((response,audience)->{
            if(!(audience instanceof Player actual) || !actual.getUniqueId().equals(owner))return;
            String current=response.getText("current"),password=response.getText("new"),confirm=response.getText("confirm");
            async(actual,()->{accounts.passwordFromGame(owner,current,password,confirm);message(actual,"Password changed for server and portal; portal sessions revoked.","Passwort für Server und Portal geändert; Portalsitzungen beendet.");});
        },ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(2)).build());
        player.showDialog(Dialog.create(factory->factory.empty().base(base).type(DialogType.notice(ActionButton.builder(Component.text(de?"Passwort ändern":"Change password")).action(action).build()))));
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p) || !eligible(p))return List.of();
        if(command.getName().equals("web"))return args.length==1?List.of("admin","logout"):List.of();
        return List.of();
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void guard(PlayerCommandPreprocessEvent event) {
        Player player=event.getPlayer();String command=event.getMessage(),label=CommandPolicy.label(command);
        String bare=label.substring(label.lastIndexOf(':')+1);
        boolean authNamespace=!label.contains(":")||label.startsWith("authme:");
        CommandPolicy.PasswordChange change=authMe==null||!authNamespace?null:CommandPolicy.passwordChange(command);
        if(change!=null) {
            event.setCancelled(true);
            if(!eligible(player)){message(player,"game_login_required");return;}
            boolean admin=change.target()!=null;
            if(!player.hasPermission(admin?"authme.admin.changepassword":"authme.player.changepassword")){message(player,"forbidden");return;}
            UUID owner=player.getUniqueId();async(player,()->{
                if(admin)accounts.adminPassword(change.target(),change.password());else accounts.password(owner,change.current(),change.password(),change.password(),false);
                message(player,"Password changed; portal sessions revoked.","Passwort geändert; Portalsitzungen beendet.");
            });return;
        }
        if(bare.equals("account") && command.strip().contains(" ")){event.setCancelled(true);event.setMessage("/account");message(player,"Use /account without passwords in chat.","Nutze /account ohne Passwörter im Chat.");}
    }
    private void registerUnregisterEvents() throws ReflectiveOperationException {
        for(String name:List.of("UnregisterByPlayerEvent","UnregisterByAdminEvent")) {
            Class<? extends Event> type=Class.forName("fr.xephi.authme.events."+name).asSubclass(Event.class);
            Method player=type.getMethod("getPlayer");Method playerName=name.equals("UnregisterByAdminEvent")?type.getMethod("getPlayerName"):null;
            getServer().getPluginManager().registerEvent(type,this,EventPriority.MONITOR,(listener,event)->{
                try {
                    Player actual=(Player)player.invoke(event);UUID uuid=actual==null?null:actual.getUniqueId();String target=playerName==null?null:(String)playerName.invoke(event);
                    Runnable revoke=()->{if(uuid!=null)accounts.revoke(uuid);else accounts.revokeName(target);};
                    if(event.isAsynchronous())revoke.run();else io.execute(revoke);
                }catch(Exception e){getLogger().severe("Account unregister session revocation failed: "+e.getClass().getSimpleName());}
            },this);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){commandCooldown.remove(event.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void adminCommand(ServerCommandEvent event) {adminPassword(event);}
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void remoteAdminCommand(RemoteServerCommandEvent event) {adminPassword(event);}
    private void adminPassword(ServerCommandEvent event) {
        if(authMe==null)return;CommandPolicy.PasswordChange change=CommandPolicy.passwordChange(event.getCommand());if(change==null || change.target()==null)return;
        event.setCancelled(true);CommandSender sender=event.getSender();if(!sender.hasPermission("authme.admin.changepassword")){sender.sendMessage("forbidden");return;}
        try {io.execute(()->{try {accounts.adminPassword(change.target(),change.password());Bukkit.getScheduler().runTask(this,()->sender.sendMessage("Password changed; portal sessions revoked."));}catch(Exception e){Bukkit.getScheduler().runTask(this,()->sender.sendMessage(e instanceof ApiError a?a.code:"service_unavailable"));}});}
        catch(RejectedExecutionException e){sender.sendMessage("busy");}
    }
    @Override public Identity identity(Player player){
        Identity identity=onMain(()->{if(!eligible(player))throw new ApiError(403,"game_login_required");return new Identity(player.getUniqueId(),player.getName());});
        try {io.execute(()->accounts.observe(identity.uuid(),identity.name()));}catch(RejectedExecutionException e){throw new ApiError(429,"busy");}return identity;
    }
    @Override public Registration register(Plugin owner,String module,Handler handler,java.util.function.Function<Identity,UUID> selectedSkin){if(stopping||!owner.isEnabled())throw new ApiError(503,"module_unavailable");return modules.register(owner,module,handler,selectedSkin);}
    @EventHandler public void pluginDisabled(PluginDisableEvent event){modules.closeOwner(event.getPlugin());}
    @Override public void onDisable(){stopping=true;modules.closeAll();getServer().getServicesManager().unregisterAll(this);if(api!=null)api.close();io.shutdownNow();secretCommands.close();}
}
