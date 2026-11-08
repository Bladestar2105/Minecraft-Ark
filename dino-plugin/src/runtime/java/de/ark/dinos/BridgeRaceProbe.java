package de.ark.dinos;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import kr.toxicity.model.api.BetterModel;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import re.imc.geysermodelengine.GeyserModelEngine;
import re.imc.geysermodelengine.managers.model.EntityTaskManager;
import re.imc.geysermodelengine.managers.model.entity.BetterModelEntityData;

/** Forces the legal zero-delay execution order before the entityTask assignment. */
final class BridgeRaceProbe {
    private static GeyserModelEngine plugin;
    private static Object originalManager;
    private static ScheduledExecutorService originalPool;
    private static ProbeManager manager;
    private static BetterModelEntityData data;
    private static int immediateRuns;

    private static Field field(String name) throws ReflectiveOperationException {
        var field=GeyserModelEngine.class.getDeclaredField(name);field.setAccessible(true);return field;
    }

    private static void worker(Runnable action) throws InterruptedException {
        var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
        var thread=new Thread(()->{try{action.run();}catch(Throwable error){failure.set(error);}},"bridge-publication-probe");
        thread.start();thread.join(5000);
        if(thread.isAlive())throw new AssertionError("Forced first tick did not finish");
        if(failure.get()!=null)throw new AssertionError(failure.get());
    }

    private static ScheduledExecutorService inlineFirstPool() {
        return (ScheduledExecutorService)Proxy.newProxyInstance(BridgeRaceProbe.class.getClassLoader(),new Class<?>[]{ScheduledExecutorService.class},(proxy,method,args)->{
            if(method.getName().equals("scheduleAtFixedRate")) {
                Runnable runnable=(Runnable)args[0];worker(runnable);immediateRuns++;
                return originalPool.scheduleAtFixedRate(runnable,150,20,TimeUnit.MILLISECONDS);
            }
            try{return method.invoke(originalPool,args);}catch(InvocationTargetException error){throw error.getCause();}
        });
    }

    static String begin(Mob mob,Player viewer) throws ReflectiveOperationException {
        plugin=(GeyserModelEngine)Bukkit.getPluginManager().getPlugin("GeyserModelEngine");
        originalManager=field("entityTaskManager").get(plugin);originalPool=(ScheduledExecutorService)field("schedulerPool").get(plugin);
        manager=new ProbeManager(plugin,viewer);field("entityTaskManager").set(plugin,manager);
        plugin.getModelManager().getPlayerJoinedCache().add(viewer.getUniqueId());
        field("schedulerPool").set(plugin,inlineFirstPool());
        try {
            var tracker=BetterModel.registry(mob.getUniqueId()).orElseThrow().tracker("ark_trex");
            data=new BetterModelEntityData(plugin,tracker.sourceEntity(),tracker);
        } finally {field("schedulerPool").set(plugin,originalPool);}
        boolean publishedVolatile=Modifier.isVolatile(BetterModelEntityData.class.getDeclaredField("entityTask").getModifiers());
        return "actor="+data.getEntity().getEntityId()+";earlyViewer="+data.getViewers().contains(viewer)+";volatile="+publishedVolatile+";immediateRuns="+immediateRuns;
    }

    static String replace() throws ReflectiveOperationException,InterruptedException {
        var old=data.getEntityTask();old.cancel();
        field("schedulerPool").set(plugin,inlineFirstPool());
        try{data.runEntityTask();}finally{field("schedulerPool").set(plugin,originalPool);}
        var replacement=data.getEntityTask();replacement.cancel();
        int before=manager.checks.get();worker(old::runAsync);int oldChecks=manager.checks.get()-before;
        before=manager.checks.get();worker(replacement::runAsync);int newChecks=manager.checks.get()-before;
        return "replaced="+(old!=replacement)+";oldChecks="+oldChecks+";newChecks="+newChecks+";viewers="+data.getViewers().size();
    }

    static void finish() throws ReflectiveOperationException {
        if(data!=null) {data.getEntityTask().cancel();data.getEntity().remove();data=null;}
        if(plugin!=null) {field("entityTaskManager").set(plugin,originalManager);field("schedulerPool").set(plugin,originalPool);plugin=null;}
    }

    private static final class ProbeManager extends EntityTaskManager {
        final AtomicInteger checks=new AtomicInteger();
        private final Player viewer;
        ProbeManager(GeyserModelEngine plugin,Player viewer) {super(plugin);this.viewer=viewer;refreshBedrockPlayers();}
        @Override public void refreshBedrockPlayers() {
            try{var field=EntityTaskManager.class.getDeclaredField("bedrockPlayers");field.setAccessible(true);field.set(this,List.of(viewer));}
            catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
        @Override public void checkViewers(re.imc.geysermodelengine.managers.model.entity.EntityData model,Set<Player> viewers) {checks.incrementAndGet();super.checkViewers(model,viewers);}
    }
}
