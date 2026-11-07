package de.ark.wardrobe;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

final class WardrobeStore implements AutoCloseable {
    record Property(String value,String signature) { }
    interface Signer { Property sign(byte[] png,String model) throws Exception; }
    interface Gateway {
        String currentKey(UUID owner);
        boolean select(UUID owner,String key,Property property) throws Exception;
        default boolean applied(UUID owner,Property property){return false;}
    }
    static final class Skin {
        UUID id;String name,model,png;long revision;Property property;String status="draft",error;
    }
    static final class Catalog {List<Skin> skins=new ArrayList<>();long request;UUID pending;long pendingRevision;}
    private final Path root;
    private final Signer signer;
    private final Gateway gateway;
    private final java.util.function.BooleanSupplier enabled;
    private volatile boolean closed;
    private final Map<UUID,Catalog> catalogs=new LinkedHashMap<>(16,.75f,true) {protected boolean removeEldestEntry(Map.Entry<UUID,Catalog> e){return size()>128;}};
    private final Map<String,Property> cache=new LinkedHashMap<>(16,.75f,true) {protected boolean removeEldestEntry(Map.Entry<String,Property> e){return size()>512;}};
    private final Map<UUID,List<String>> names=new ConcurrentHashMap<>();
    List<String> cachedNames(UUID owner){return names.getOrDefault(owner,List.of());}
    private final ThreadPoolExecutor queue=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{var t=new Thread(r,"ArkWardrobe-signing");t.setDaemon(true);return t;});
    WardrobeStore(Path root,Signer signer,Gateway gateway) {this(root,signer,gateway,()->true);}
    WardrobeStore(Path root,Signer signer,Gateway gateway,java.util.function.BooleanSupplier enabled) {this.root=root;this.signer=signer;this.gateway=gateway;this.enabled=enabled;}
    private void requireOpen(){if(closed||!enabled.getAsBoolean())throw new ApiError(503,"module_unavailable");}
    static String key(UUID id,long revision) {return "arkw_"+id.toString().replace("-","")+"_"+revision;}
    private static boolean selected(Skin skin,String key) {return key!=null && key.startsWith("arkw_"+skin.id.toString().replace("-","")+"_");}
    private Path file(UUID owner) {return root.resolve(owner.toString()).resolve("catalog.json");}
    private Catalog load(UUID owner) {
        requireOpen();
        Catalog known=catalogs.get(owner);if(known!=null)return known;
        Catalog catalog=new Catalog();
        try {
            if(Files.exists(file(owner))) {
                if(Files.size(file(owner))>5_000_000) throw new IllegalArgumentException();
                catalog=Data.JSON.fromJson(Files.readString(file(owner)),Catalog.class);
                if(catalog==null || catalog.skins==null || catalog.skins.size()>20) throw new IllegalArgumentException();
                Set<UUID> ids=new HashSet<>();Set<String> names=new HashSet<>();
                for(Skin skin:catalog.skins) {
                    if(skin==null || skin.id==null || skin.revision<1 || !ids.add(skin.id) || !names.add(name(skin.name).toLowerCase(Locale.ROOT))) throw new IllegalArgumentException();
                    model(skin.model);skin.png=Base64.getEncoder().encodeToString(Png.canonical(skin.png));
                    if(skin.status==null || skin.status.equals("preparing")) {skin.status=skin.property==null?"draft":"ready";skin.error=null;}
                    if(skin.property!=null) cache.put(digest(skin),skin.property);
                }
                catalog.pending=null;
            }
            catalogs.put(owner,catalog);names.put(owner,catalog.skins.stream().map(s->s.name).toList());return catalog;
        } catch(Exception e) {throw new ApiError(503,"storage_unavailable");}
    }
    private Catalog snapshot(Catalog c) {return Data.JSON.fromJson(Data.JSON.toJson(c),Catalog.class);}
    private void save(UUID owner,Catalog c) {
        requireOpen();
        try {AtomicFiles.write(file(owner),Data.JSON.toJson(c));catalogs.put(owner,c);names.put(owner,c.skins.stream().map(s->s.name).toList());}
        catch(Exception e) {throw new ApiError(503,"storage_unavailable");}
    }
    private Skin find(Catalog c,UUID id) {return c.skins.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow(()->new ApiError(404,"not_found"));}
    private void revision(Skin skin,long expected) {if(expected!=skin.revision)throw new ApiError(409,"stale_revision");}
    private static String name(String value) {
        if(value==null || !value.equals(value.strip()) || value.isBlank() || value.codePointCount(0,value.length())>32 || value.codePoints().anyMatch(Character::isISOControl)) throw new ApiError(400,"invalid_name");return value;
    }
    private static String model(String value) {if(!"classic".equals(value)&&!"slim".equals(value))throw new ApiError(400,"invalid_model");return value;}
    private void unique(Catalog c,String name,UUID except) {if(c.skins.stream().anyMatch(s->!s.id.equals(except)&&s.name.equalsIgnoreCase(name)))throw new ApiError(400,"duplicate_name");}
    private static String text(JsonObject body,String key) {return Signers.string(body,key);}
    synchronized JsonObject create(UUID owner,JsonObject body) {
        Catalog c=snapshot(load(owner));if(c.skins.size()>=20)throw new ApiError(400,"skin_limit");
        Skin s=new Skin();s.id=UUID.randomUUID();s.name=name(text(body,"name"));unique(c,s.name,null);s.model=model(text(body,"model"));s.png=Base64.getEncoder().encodeToString(Png.canonical(text(body,"png")));s.revision=1;
        c.skins.add(s);save(owner,c);return view(s,null);
    }
    synchronized JsonObject update(UUID owner,UUID id,JsonObject body,long expected) {
        Catalog c=snapshot(load(owner));Skin s=find(c,id);revision(s,expected);
        String n=name(text(body,"name"));unique(c,n,id);String m=model(text(body,"model"));String png=Base64.getEncoder().encodeToString(Png.canonical(text(body,"png")));
        boolean changed=!s.png.equals(png)||!s.model.equals(m);
        s.name=n;s.model=m;s.png=png;s.revision++;
        if(changed) {s.property=null;s.status="draft";s.error=null;}
        if(id.equals(c.pending)) {c.request++;c.pending=null;s.status=s.property==null?"draft":"ready";s.error=null;}
        save(owner,c);return view(s,gateway.currentKey(owner));
    }
    synchronized JsonObject copy(UUID owner,UUID id,String name,long expected) {
        Catalog c=load(owner);Skin s=find(c,id);revision(s,expected);return create(owner,copyBody(s,name));
    }
    private JsonObject copyBody(Skin s,String name) {var b=new JsonObject();b.addProperty("name",name);b.addProperty("model",s.model);b.addProperty("png",s.png);return b;}
    synchronized void delete(UUID owner,UUID id,long expected) {
        Catalog c=snapshot(load(owner));Skin s=find(c,id);revision(s,expected);
        if(selected(s,gateway.currentKey(owner)))throw new ApiError(409,"active_skin");
        c.skins.remove(s);if(id.equals(c.pending)){c.request++;c.pending=null;}save(owner,c);
    }
    synchronized JsonObject list(UUID owner) {
        Catalog c=load(owner);String selected=gateway.currentKey(owner);var a=new JsonArray();for(Skin s:c.skins){JsonObject item=view(s,selected);if(key(s.id,s.revision).equals(selected)&&s.property!=null&&!"preparing".equals(s.status))item.addProperty("status",gateway.applied(owner,s.property)?"applied":"saved");a.add(item);}var out=new JsonObject();out.add("skins",a);out.addProperty("limit",20);return out;
    }
    synchronized UUID active(UUID owner) {String key=gateway.currentKey(owner);return load(owner).skins.stream().filter(s->selected(s,key)).map(s->s.id).findFirst().orElse(null);}
    synchronized JsonObject activate(UUID owner,UUID id,long expected) {
        if(queue.getQueue().remainingCapacity()==0) throw new ApiError(429,"busy");
        Catalog c=snapshot(load(owner));Skin s=find(c,id);revision(s,expected);
        c.request++;c.pending=id;c.pendingRevision=s.revision;s.status="preparing";s.error=null;save(owner,c);
        long request=c.request;byte[] bytes=Base64.getDecoder().decode(s.png);String model=s.model;Property previous=s.property;String digest=digest(s);
        try {queue.execute(()->complete(owner,id,expected,request,bytes,model,digest,previous));}
        catch(RejectedExecutionException e) {c.pending=null;s.status="failed";s.error="busy";save(owner,c);throw new ApiError(429,"busy");}
        return view(s,gateway.currentKey(owner));
    }
    private void complete(UUID owner,UUID id,long revision,long request,byte[] png,String model,String digest,Property previous) {
        try {
            requireOpen();
            Property property;
            synchronized(this) {property=previous==null?cache.get(digest):previous;}
            if(property==null) property=signer.sign(png,model);
            if(property==null || property.value()==null || property.signature()==null)throw new ApiError(502,"signing_failed");
            synchronized(this) {
                requireOpen();
                cache.put(digest,property);Catalog c=snapshot(load(owner));Skin s=c.skins.stream().filter(x->x.id.equals(id)&&x.revision==revision).findFirst().orElse(null);
                if(s==null)return;
                s.property=property;s.status="ready";s.error=null;
                if(c.request==request && id.equals(c.pending) && c.pendingRevision==revision) {
                    requireOpen();
                    boolean applied=gateway.select(owner,key(id,revision),property);s.status=applied?"applied":"saved";c.pending=null;
                }
                save(owner,c);
            }
        } catch(Exception error) {
            synchronized(this) {
                try {
                    Catalog c=snapshot(load(owner));Skin s=c.skins.stream().filter(x->x.id.equals(id)&&x.revision==revision).findFirst().orElse(null);
                    if(s!=null && c.request==request && id.equals(c.pending)) {s.status="failed";s.error=error instanceof ApiError a?a.code:"signing_failed";c.pending=null;save(owner,c);}
                }catch(Exception ignored) { }
            }
        }
    }
    private static String digest(Skin skin) {return Data.hash(skin.model+":"+skin.png);}
    private JsonObject view(Skin s,String selected) {
        JsonObject out=new JsonObject();out.addProperty("id",s.id.toString());out.addProperty("name",s.name);out.addProperty("model",s.model);out.addProperty("revision",s.revision);out.addProperty("png",s.png);out.addProperty("ready",s.property!=null);boolean active=selected(s,selected);out.addProperty("selected",active);out.addProperty("status",!active && ("applied".equals(s.status)||"saved".equals(s.status))?"ready":s.status);if(s.error!=null)out.addProperty("error",s.error);return out;
    }
    void awaitIdle() throws Exception {queue.submit(()->{}).get(10,TimeUnit.SECONDS);}
    public void close() {closed=true;queue.shutdownNow();}
}
