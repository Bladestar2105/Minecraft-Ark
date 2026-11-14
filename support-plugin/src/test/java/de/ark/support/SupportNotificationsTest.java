package de.ark.support;

import com.google.gson.JsonParser;
import de.ark.accounts.api.AccountsApi;
import java.lang.reflect.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

public final class SupportNotificationsTest {
    private static final UUID OWNER=UUID.randomUUID(),OTHER=UUID.randomUUID();
    private static final List<Runnable> tasks=new ArrayList<>();
    private static final List<String> delivered=new ArrayList<>();
    private static Player online;
    private static boolean authenticated=true;
    private static boolean operator=true;
    private static UUID identity=OWNER;
    private static Locale locale=Locale.GERMAN;
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type,InvocationHandler handler){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},handler);}
    private static void notify(ArkSupport plugin,SupportStore.Notice notice,BooleanSupplier open) throws Exception {
        Method method=ArkSupport.class.getDeclaredMethod("notifyPlayers",SupportStore.Notice.class,BooleanSupplier.class);method.setAccessible(true);method.invoke(plugin,notice,open);
    }
    private static void drain(){List<Runnable> pending=new ArrayList<>(tasks);tasks.clear();pending.forEach(Runnable::run);}
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("support-notifications"),file=directory.resolve("support.json");
        List<SupportStore.Notice> notices=new ArrayList<>();
        SupportStore store=new SupportStore(file,Instant::now,notice->{
            if(!notice.admins())try{var messages=JsonParser.parseString(Files.readString(file)).getAsJsonArray().get(0).getAsJsonObject().getAsJsonArray("messages");TestSupport.check(messages.get(messages.size()-1).getAsJsonObject().get("message").getAsString().equals("Persist before delivery\n<gold>Answer</gold> 🙂"),"administrator reply is durable before notification");}catch(java.io.IOException error){throw new AssertionError(error);}
            notices.add(notice);
        });
        var created=store.create(OWNER,"Alice","normal","Persist before delivery");UUID ticket=SupportTest.id(created);
        store.reply(null,ticket,1,"Persist before delivery\n<gold>Answer</gold> 🙂",true);SupportStore.Notice answer=notices.getLast();
        byte[] before=Files.readAllBytes(file);Path saved=directory.resolve("saved.json");Files.move(file,saved);Files.createDirectory(file);int noticeCount=notices.size();
        TestSupport.denied(503,()->store.reply(null,ticket,2,"Must not be sent",true));
        TestSupport.check(notices.size()==noticeCount&&Arrays.equals(before,Files.readAllBytes(saved)),"failed persistence cannot deliver or lose a reply");
        Files.delete(file);Files.move(saved,file);store.status(ticket,2,"closed");SupportStore.Notice status=notices.getLast();

        BukkitScheduler scheduler=proxy(BukkitScheduler.class,(p,m,a)->{if(m.getName().equals("runTask")){tasks.add((Runnable)a[1]);return null;}throw new AssertionError(m);});
        Player owner=proxy(Player.class,(p,m,a)->switch(m.getName()){
            case "locale"->locale;case "getUniqueId"->OWNER;case "isOnline"->true;case "isOp"->operator;
            case "sendMessage"->{delivered.add(((TextComponent)a[0]).content());yield null;}
            default->throw new AssertionError(m);
        });
        Server server=proxy(Server.class,(p,m,a)->switch(m.getName()){
            case "getScheduler"->scheduler;case "getPlayer"->{TestSupport.check(a[0].equals(OWNER),"player notification looks up only ticket owner UUID");yield online;}
            case "getOnlinePlayers"->List.of(owner);default->throw new AssertionError(m);
        });
        Field serverField=Bukkit.class.getDeclaredField("server");serverField.setAccessible(true);Object previous=serverField.get(null);serverField.set(null,server);
        try {
            Class<?> unsafeClass=Class.forName("sun.misc.Unsafe");Field singleton=unsafeClass.getDeclaredField("theUnsafe");singleton.setAccessible(true);
            ArkSupport plugin=(ArkSupport)unsafeClass.getMethod("allocateInstance",Class.class).invoke(singleton.get(null),ArkSupport.class);
            AccountsApi accounts=proxy(AccountsApi.class,(p,m,a)->{if(!m.getName().equals("identity"))throw new AssertionError(m);if(!authenticated)throw new AccountsApi.Failure(403,"login_required");return new AccountsApi.Identity(identity,"Alice");});
            Field accountField=ArkSupport.class.getDeclaredField("accounts");accountField.setAccessible(true);accountField.set(plugin,accounts);
            AtomicBoolean open=new AtomicBoolean(true);online=owner;
            notify(plugin,answer,open::get);TestSupport.check(delivered.isEmpty()&&tasks.size()==1,"delivery waits for Paper main thread");drain();
            TestSupport.check(delivered.size()==1&&delivered.getLast().contains("Persist before delivery\n<gold>Answer</gold> 🙂"),"online owner receives full literal reply with line breaks and Unicode");
            TestSupport.check(delivered.getLast().contains("Antwort"),"German owner receives German reply label");
            delivered.clear();locale=Locale.ENGLISH;notify(plugin,answer,open::get);drain();
            TestSupport.check(delivered.size()==1&&delivered.getLast().contains("Reply"),"English owner receives English reply label");
            delivered.clear();notify(plugin,status,open::get);drain();TestSupport.check(delivered.size()==1&&delivered.getLast().contains("/web")&&!delivered.getLast().contains("<gold>Answer</gold>"),"status notification remains a portal hint without old reply text");
            delivered.clear();notify(plugin,notices.getFirst(),open::get);drain();TestSupport.check(delivered.size()==1&&delivered.getLast().contains("/web admin")&&!delivered.getLast().contains("Persist before delivery"),"operator notification excludes private player text");
            delivered.clear();operator=false;notify(plugin,notices.getFirst(),open::get);drain();TestSupport.check(delivered.isEmpty(),"non-operator receives no administrator notification");operator=true;
            delivered.clear();online=null;notify(plugin,answer,open::get);drain();TestSupport.check(delivered.isEmpty(),"offline player receives no chat delivery");
            online=owner;authenticated=false;notify(plugin,answer,open::get);drain();TestSupport.check(delivered.isEmpty(),"unauthenticated player receives no private reply");
            authenticated=true;identity=OTHER;notify(plugin,answer,open::get);drain();TestSupport.check(delivered.isEmpty(),"mismatched retained identity receives no private reply");
            identity=OWNER;notify(plugin,answer,open::get);open.set(false);drain();TestSupport.check(delivered.isEmpty(),"module disabled before scheduled delivery suppresses reply");
        } finally {serverField.set(null,previous);}
        System.out.println("SupportNotificationsTest: "+TestSupport.checks+" checks passed");
    }
}
