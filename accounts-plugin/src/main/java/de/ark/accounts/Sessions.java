package de.ark.accounts;

import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;

final class Sessions {
    record Session(UUID uuid,String name,long expiresAt,long version,long gameProofUntil) { }
    record Pair(Session owner,long expiresAt) { }
    static final SecureRandom RANDOM=new SecureRandom();
    static final Gson JSON=new Gson();
    private final Path file;
    private final Supplier<Instant> clock;
    private final long lifetime;
    private final Map<String,Pair> pairs=new HashMap<>();
    private final Map<String,Session> sessions=new HashMap<>();
    private java.util.function.Function<UUID,Long> version=uuid->0L;
    synchronized void versions(java.util.function.Function<UUID,Long> version) {this.version=version;}
    Sessions(Path file,Supplier<Instant> clock) {
        this(file,clock,Duration.ofDays(30));
    }
    Sessions(Path file,Supplier<Instant> clock,Duration lifetime) {
        this.file=file; this.clock=clock;this.lifetime=lifetime.toSeconds();
        try {
            if(Files.exists(file)) {
                if(Files.size(file)>4_000_000) throw new IllegalArgumentException();
                JsonObject saved=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for(var entry:saved.entrySet()) {
                    Session session=JSON.fromJson(entry.getValue(),Session.class);
                    if(!entry.getKey().matches("[0-9a-f]{64}") || session.uuid()==null || session.name()==null) throw new IllegalArgumentException();
                    if(session.expiresAt()>now()) sessions.put(entry.getKey(),session);
                }
            }
        } catch(Exception e) { throw new ApiError(503,"storage_unavailable"); }
    }
    long now() { return clock.get().getEpochSecond(); }
    synchronized String pair(UUID uuid,String name) {
        pairs.entrySet().removeIf(e->e.getValue().expiresAt()<now() || e.getValue().owner().uuid().equals(uuid));
        if(pairs.size()>=1000) throw new ApiError(429,"busy");
        String code=random(9).toUpperCase(Locale.ROOT);
        pairs.put(hash(code),new Pair(new Session(uuid,name,0,version.apply(uuid),now()+300),now()+300)); return code;
    }
    synchronized JsonObject redeem(String code) {
        if(code==null || code.length()>32) throw new ApiError(401,"invalid_code");
        String key=hash(code.trim().toUpperCase(Locale.ROOT)); Pair pair=pairs.get(key);
        if(pair==null || pair.expiresAt()<=now() || pair.owner().version()!=version.apply(pair.owner().uuid())) throw new ApiError(401,"invalid_code");
        sessions.entrySet().removeIf(e->e.getValue().expiresAt()<=now());
        if(sessions.size()>=10000) throw new ApiError(429,"busy");
        long count=sessions.values().stream().filter(s->s.uuid().equals(pair.owner().uuid())).count();
        if(count>=20) throw new ApiError(429,"session_limit");
        JsonObject issued=issue(pair.owner().uuid(),pair.owner().name(),pair.owner().gameProofUntil(),pair.owner().version());
        pairs.remove(key);return issued;
    }
    synchronized JsonObject issue(UUID uuid,String name,boolean gameProof) {
        return issue(uuid,name,gameProof?now()+300:0,version.apply(uuid));
    }
    private JsonObject issue(UUID uuid,String name,long proofUntil,long credentialVersion) {
        sessions.entrySet().removeIf(e->e.getValue().expiresAt()<=now());
        if(sessions.size()>=10000 || sessions.values().stream().filter(s->s.uuid().equals(uuid)).count()>=20)throw new ApiError(429,"session_limit");
        String token=random(32); Session session=new Session(uuid,name,now()+lifetime,credentialVersion,proofUntil);
        sessions.put(hash(token),session);
        try { persist(); } catch(RuntimeException e) { sessions.remove(hash(token)); throw e; }
        JsonObject out=view(session);out.addProperty("token",token); return out;
    }
    synchronized Session require(String token) {
        if(token==null || token.length()<32 || token.length()>128) throw new ApiError(401,"unauthorized");
        Session session=sessions.get(hash(token));
        if(session==null || session.expiresAt()<=now() || session.version()!=version.apply(session.uuid())) throw new ApiError(401,"unauthorized"); return session;
    }
    synchronized void logout(String token) { Session s=require(token); String key=hash(token);sessions.remove(key);try {persist();} catch(RuntimeException e) {sessions.put(key,s);throw e;} }
    synchronized void forget(String token) {if(token==null || token.length()<32 || token.length()>128)throw new ApiError(401,"unauthorized");String key=hash(token);Session previous=sessions.remove(key);if(previous!=null)try {persist();}catch(RuntimeException e){sessions.put(key,previous);throw e;}}
    synchronized void revokeAll(UUID owner) {
        var previous=new HashMap<>(sessions);sessions.entrySet().removeIf(e->e.getValue().uuid().equals(owner));
        try {persist();} catch(RuntimeException e) {sessions.clear();sessions.putAll(previous);throw e;}
        pairs.entrySet().removeIf(e->e.getValue().owner().uuid().equals(owner));
    }
    private void persist() { try {AtomicFiles.write(file,JSON.toJson(sessions));} catch(Exception e) {throw new ApiError(503,"storage_unavailable");} }
    static JsonObject view(Session s) {var out=new JsonObject();out.addProperty("uuid",s.uuid().toString());out.addProperty("name",s.name());out.addProperty("expires_at",s.expiresAt());return out;}
    static String random(int bytes) {byte[] value=new byte[bytes];RANDOM.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
    static String hash(String value) {return hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static String hash(byte[] value) {try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));} catch(NoSuchAlgorithmException e) {throw new IllegalStateException(e);} }
}
