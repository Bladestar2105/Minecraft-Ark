package de.ark.wardrobe;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.time.*;
import java.util.*;

final class Signers implements WardrobeStore.Signer,AutoCloseable {
    record Endpoints(URI mineSkin,URI device,URI token,URI xbl,URI xsts,URI minecraft,URI profile,URI skins,URI session,URI keys) {
        static Endpoints production() {return new Endpoints(
            URI.create("https://api.mineskin.org/v2/queue"),URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"),
            URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"),URI.create("https://user.auth.xboxlive.com/user/authenticate"),
            URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"),URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"),
            URI.create("https://api.minecraftservices.com/minecraft/profile"),URI.create("https://api.minecraftservices.com/minecraft/profile/skins"),
            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"),URI.create("https://api.minecraftservices.com/publickeys"));}
        static Endpoints fixture(URI base) {return new Endpoints(base.resolve("mineskin/queue"),base.resolve("device"),base.resolve("token"),base.resolve("xbl"),base.resolve("xsts"),base.resolve("minecraft"),base.resolve("profile"),base.resolve("skins"),base.resolve("session/"),base.resolve("keys"));}
    }
    static final class State {
        String provider="mineskin",clientId="",mineSkinKey="",refreshToken,accessToken,name,uuid;
        long accessExpires;
    }
    record Device(String code,long expires,int interval,long nextPoll) { }
    record Response(int status,JsonObject body) { }
    private final Path file;
    private final Endpoints endpoints;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private State state=new State();
    private Device pending;
    private String status="unconfigured";
    private long generation;
    private long nextMineSkin;
    private List<PublicKey> keys=List.of();
    private long keysExpire;
    private final java.util.function.BooleanSupplier enabled;
    private volatile boolean closed;
    private final java.util.concurrent.ExecutorService oauth=java.util.concurrent.Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ArkWardrobe-OAuth");t.setDaemon(true);return t;});
    private boolean polling;
    Signers(Path file,Endpoints endpoints) {
        this(file,endpoints,()->true);
    }
    Signers(Path file,Endpoints endpoints,java.util.function.BooleanSupplier enabled) {
        this.file=file;this.endpoints=endpoints;this.enabled=enabled;
        try {
            if(Files.exists(file)) {
                if(Files.size(file)>262144)throw new IOException();
                state=Data.JSON.fromJson(Files.readString(file),State.class);
                if(state==null || !(state.provider.equals("mineskin")||state.provider.equals("selfhosted")) || state.clientId==null || state.mineSkinKey==null)throw new IOException();
            }
        }catch(Exception e) {throw new ApiError(503,"storage_unavailable");}
    }
    private void requireOpen(){if(closed||!enabled.getAsBoolean())throw new ApiError(503,"module_unavailable");}
    private void save(State next) {requireOpen();try {AtomicFiles.write(file,Data.JSON.toJson(next));state=next;}catch(Exception e){throw new ApiError(503,"storage_unavailable");}}
    private State copy() {return Data.JSON.fromJson(Data.JSON.toJson(state),State.class);}
    private void saveCredentials(State credentials) {
        State current=copy();current.refreshToken=credentials.refreshToken;current.accessToken=credentials.accessToken;current.accessExpires=credentials.accessExpires;current.name=credentials.name;current.uuid=credentials.uuid;save(current);
    }
    synchronized JsonObject update(JsonObject body) {
        requireOpen();
        State next=copy();
        if(body.has("provider")) {next.provider=string(body,"provider");if(!next.provider.equals("mineskin")&&!next.provider.equals("selfhosted"))throw new ApiError(400,"invalid_provider");}
        if(body.has("client_id")) {
            String id=string(body,"client_id").strip();if(!id.isEmpty())try {UUID.fromString(id);}catch(Exception e){throw new ApiError(400,"invalid_client_id");}
            if(!id.equals(next.clientId)) {next.clientId=id;clear(next);pending=null;generation++;}
        }
        if(body.has("mineskin_api_key")) {String key=string(body,"mineskin_api_key").strip();if(key.length()>1024 || key.codePoints().anyMatch(Character::isISOControl))throw new ApiError(400,"invalid_api_key");if(!key.isEmpty())next.mineSkinKey=key;}
        if(body.has("clear_mineskin_api_key")&&body.get("clear_mineskin_api_key").getAsBoolean())next.mineSkinKey="";
        save(next);return view();
    }
    synchronized JsonObject status() {
        requireOpen();
        if(pending!=null && pending.nextPoll<=now() && !polling) {
            polling=true;oauth.execute(()->{try {poll();}finally {synchronized(this){polling=false;}}});
        }
        return view();
    }
    private JsonObject view() {
        JsonObject out=new JsonObject();out.addProperty("provider",state.provider);out.addProperty("client_id",state.clientId);out.addProperty("key_configured",!state.mineSkinKey.isEmpty());
        JsonObject account=new JsonObject();boolean connected=state.refreshToken!=null && state.uuid!=null;account.addProperty("connected",connected);if(connected){account.addProperty("name",state.name);account.addProperty("uuid",state.uuid);}out.add("account",account);
        String current=pending!=null?"connecting":state.provider.equals("mineskin")?(!state.mineSkinKey.isEmpty()?"configured":"unconfigured"):(connected?"connected":status);
        out.addProperty("status",current);if(state.provider.equals("selfhosted"))out.addProperty("message","selfhosted_operator_verification_required");return out;
    }
    JsonObject connect() {
        requireOpen();
        String clientId;long epoch;
        synchronized(this){if(state.clientId.isEmpty() || !state.provider.equals("selfhosted"))throw new ApiError(400,"client_id_required");clientId=state.clientId;epoch=++generation;pending=null;}
        Response response=form(endpoints.device,Map.of("client_id",clientId,"scope","XboxLive.signin offline_access"));requireSuccess(response);
        JsonObject b=response.body;int interval=(int)Math.max(1,Math.min(60,number(b,"interval",5)));long expires=now()+Math.max(1,Math.min(900,number(b,"expires_in",600)));
        String code=string(b,"device_code"),userCode=string(b,"user_code"),uri=string(b,"verification_uri");
        if(!uri.startsWith("https://") || uri.length()>2048 || userCode.length()>64)throw new ApiError(502,"provider_invalid_response");
        synchronized(this){requireOpen();if(epoch!=generation)throw new ApiError(503,"cancelled");pending=new Device(code,expires,interval,now()+interval);status="connecting";}
        JsonObject out=new JsonObject();out.addProperty("user_code",userCode);out.addProperty("verification_uri",uri);out.addProperty("expires_at",expires);out.addProperty("interval",interval);return out;
    }
    synchronized JsonObject disconnect() {State next=copy();clear(next);save(next);pending=null;generation++;status="disconnected";return view();}
    private static void clear(State state) {state.refreshToken=null;state.accessToken=null;state.accessExpires=0;state.uuid=null;state.name=null;}
    private void poll() {
        Device device;State next;long epoch;
        synchronized(this) {
            if(pending==null)return;
            if(pending.expires<=now()){pending=null;status="authorization_expired";return;}
            if(pending.nextPoll>now())return;
            device=pending;pending=new Device(device.code,device.expires,device.interval,now()+device.interval);next=copy();epoch=generation;
        }
        try {
            Response r=form(endpoints.token,Map.of("client_id",next.clientId,"grant_type","urn:ietf:params:oauth:grant-type:device_code","device_code",device.code));
            if(r.status==400 && r.body.has("error")) {
                String error=string(r.body,"error");
                synchronized(this){
                    if(epoch!=generation)return;
                    if(error.equals("authorization_pending"))return;
                    if(error.equals("slow_down")){pending=new Device(device.code,device.expires,Math.min(60,device.interval+5),now()+device.interval+5);return;}
                    pending=null;status="authorization_failed";return;
                }
            }
            requireSuccess(r);exchange(next,r.body);synchronized(this){if(epoch!=generation)return;saveCredentials(next);pending=null;status="connected";}
        } catch(ApiError e) {synchronized(this){if(epoch==generation){status=e.code;if(e.status!=429 && e.status!=503)pending=null;}}}
    }
    private void exchange(State next,JsonObject token) {
        String access=string(token,"access_token");if(token.has("refresh_token"))next.refreshToken=string(token,"refresh_token");
        JsonObject props=new JsonObject();props.addProperty("AuthMethod","RPS");props.addProperty("SiteName","user.auth.xboxlive.com");props.addProperty("RpsTicket","d="+access);
        JsonObject body=new JsonObject();body.add("Properties",props);body.addProperty("RelyingParty","http://auth.xboxlive.com");body.addProperty("TokenType","JWT");
        JsonObject xbl=json(endpoints.xbl,body,null);String userToken=string(xbl,"Token");
        JsonObject xstsProps=new JsonObject();xstsProps.addProperty("SandboxId","RETAIL");JsonArray tokens=new JsonArray();tokens.add(userToken);xstsProps.add("UserTokens",tokens);
        body=new JsonObject();body.add("Properties",xstsProps);body.addProperty("RelyingParty","rp://api.minecraftservices.com/");body.addProperty("TokenType","JWT");
        JsonObject xsts=json(endpoints.xsts,body,null);String userHash;
        try {userHash=string(xsts.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject(),"uhs");}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
        body=new JsonObject();body.addProperty("identityToken","XBL3.0 x="+userHash+";"+string(xsts,"Token"));
        JsonObject mc=json(endpoints.minecraft,body,null);next.accessToken=string(mc,"access_token");next.accessExpires=now()+Math.min(86400,number(mc,"expires_in",3600));
        JsonObject profile=get(endpoints.profile,next.accessToken);String uuid=string(profile,"id");if(!uuid.matches("[0-9a-fA-F]{32}"))throw new ApiError(502,"provider_invalid_response");next.uuid=uuid.toLowerCase(Locale.ROOT);next.name=string(profile,"name");
        if(next.refreshToken==null)throw new ApiError(502,"provider_invalid_response");
    }
    public WardrobeStore.Property sign(byte[] png,String model) {
        requireOpen();
        State snapshot;long epoch;
        synchronized(this){snapshot=copy();epoch=generation;}
        if(snapshot.provider.equals("mineskin"))return mineSkin(snapshot,png,model);
        if(snapshot.refreshToken==null)throw new ApiError(503,"signing_unconfigured");
        if(snapshot.accessToken==null || snapshot.accessExpires<now()+60) {
            try {
                Response response=form(endpoints.token,Map.of("client_id",snapshot.clientId,"grant_type","refresh_token","refresh_token",snapshot.refreshToken,"scope","XboxLive.signin offline_access"));requireSuccess(response);exchange(snapshot,response.body);
                synchronized(this){if(epoch!=generation)throw new ApiError(503,"cancelled");saveCredentials(snapshot);}
            } catch(ApiError error) {synchronized(this){if(epoch==generation&&(error.status==401 || error.status==403)){State next=copy();clear(next);save(next);status="reauthentication_required";}}throw error;}
        }
        if(Thread.currentThread().isInterrupted())throw new ApiError(503,"cancelled");
        String boundary="ArkWardrobe"+Data.random(12);
        ByteArrayOutputStream upload=new ByteArrayOutputStream();
        try {
            upload.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"+model+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));upload.write(png);upload.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        }catch(IOException impossible){throw new IllegalStateException(impossible);}
        synchronized(this){if(epoch!=generation)throw new ApiError(503,"cancelled");}
        Response uploaded=request("POST",endpoints.skins,"multipart/form-data; boundary="+boundary,upload.toByteArray(),snapshot.accessToken);
        if(uploaded.status==401 || uploaded.status==403)synchronized(this){if(epoch==generation){State next=copy();clear(next);save(next);status="reauthentication_required";generation++;}}
        requireSuccess(uploaded);
        String expectedUrl=null;
        try {for(JsonElement e:uploaded.body.getAsJsonArray("skins"))if("ACTIVE".equals(string(e.getAsJsonObject(),"state")))expectedUrl=string(e.getAsJsonObject(),"url");}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
        if(expectedUrl==null)throw new ApiError(502,"provider_invalid_response");
        for(int attempt=0;attempt<10;attempt++) {
            synchronized(this){if(epoch!=generation)throw new ApiError(503,"cancelled");}
            JsonObject profile=get(URI.create(endpoints.session.toString()+snapshot.uuid+"?unsigned=false"),null);
            try {
                for(JsonElement e:profile.getAsJsonArray("properties")) {
                    JsonObject p=e.getAsJsonObject();if(!"textures".equals(string(p,"name")))continue;
                    WardrobeStore.Property property=new WardrobeStore.Property(string(p,"value"),string(p,"signature"));
                    String url=texture(property).getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                    URI actual=URI.create(url),expected=URI.create(expectedUrl);
                    if(Objects.equals(actual.getHost(),expected.getHost())&&Objects.equals(actual.getPath(),expected.getPath()))return verified(p,model);
                }
            }catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
            pause(1000);
        }
        throw new ApiError(503,"provider_not_ready");
    }
    WardrobeStore.Property signUnchecked(byte[] png,String model) {return sign(png,model);}
    private WardrobeStore.Property mineSkin(State snapshot,byte[] png,String model) {
        if(snapshot.mineSkinKey.isEmpty())throw new ApiError(503,"signing_unconfigured");
        synchronized(this){if(now()<nextMineSkin)throw new ApiError(429,"provider_rate_limited");}
        JsonObject body=new JsonObject();body.addProperty("variant",model);body.addProperty("visibility","unlisted");body.addProperty("url","data:image/png;base64,"+Base64.getEncoder().encodeToString(png));
        Response response=request("POST",endpoints.mineSkin,"application/json",body.toString().getBytes(StandardCharsets.UTF_8),snapshot.mineSkinKey);updateRateLimit(response);requireSuccess(response);
        for(int attempt=0;attempt<90;attempt++) {
            if(response.body.has("skin")) {
                try {return verified(response.body.getAsJsonObject("skin").getAsJsonObject("texture").getAsJsonObject("data"),model);}catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
            }
            String job;
            try {JsonObject info=response.body.getAsJsonObject("job");job=string(info,"id");if(!job.matches("[a-zA-Z0-9_-]{1,100}") || "failed".equals(string(info,"status")))throw new ApiError(502,"signing_failed");}catch(ApiError e){throw e;}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
            pause(1000);response=request("GET",URI.create(endpoints.mineSkin+"/"+job),null,null,snapshot.mineSkinKey);updateRateLimit(response);requireSuccess(response);
        }
        throw new ApiError(503,"provider_not_ready");
    }
    private synchronized void updateRateLimit(Response response) {
        try {long millis=response.body.getAsJsonObject("rateLimit").getAsJsonObject("next").get("relative").getAsLong();nextMineSkin=Math.max(nextMineSkin,now()+(Math.max(0,Math.min(millis,3600000))+999)/1000);}catch(Exception ignored) { }
        if(response.status==429)nextMineSkin=Math.max(nextMineSkin,now()+60);
    }
    private WardrobeStore.Property verified(JsonObject data,String model) {
        String value=string(data,"value"),signature=string(data,"signature");if(value.length()>32768 || signature.length()>4096)throw new ApiError(502,"invalid_signature");
        WardrobeStore.Property property=new WardrobeStore.Property(value,signature);JsonObject skin;
        try {
            skin=texture(property).getAsJsonObject("textures").getAsJsonObject("SKIN");URI url=URI.create(string(skin,"url"));
            if(!("http".equals(url.getScheme())||"https".equals(url.getScheme())) || !"textures.minecraft.net".equals(url.getHost()) || !url.getPath().matches("/texture/[0-9a-fA-F]{32,64}") || url.getRawQuery()!=null || url.getUserInfo()!=null)throw new IllegalArgumentException();
            String variant=skin.has("metadata")?string(skin.getAsJsonObject("metadata"),"model"):"classic";
            if(!variant.equals(model))throw new IllegalArgumentException();
        }catch(Exception e){throw new ApiError(502,"invalid_texture");}
        List<PublicKey> publicKeys=publicKeys();
        for(PublicKey key:publicKeys)try {Signature verifier=Signature.getInstance("SHA1withRSA");verifier.initVerify(key);verifier.update(value.getBytes(StandardCharsets.UTF_8));if(verifier.verify(Base64.getDecoder().decode(signature)))return property;}catch(Exception ignored) { }
        throw new ApiError(502,"invalid_signature");
    }
    private JsonObject texture(WardrobeStore.Property property) {try{return JsonParser.parseString(new String(Base64.getDecoder().decode(property.value()),StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new ApiError(502,"invalid_texture");}}
    private synchronized List<PublicKey> publicKeys() {
        if(keysExpire>now()&&!keys.isEmpty())return keys;
        JsonObject result=get(endpoints.keys,null);List<PublicKey> loaded=new ArrayList<>();
        try {for(JsonElement e:result.getAsJsonArray("profilePropertyKeys")){String key=string(e.getAsJsonObject(),"publicKey");loaded.add(KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));}}catch(Exception e){throw new ApiError(502,"invalid_signature_keys");}
        if(loaded.isEmpty())throw new ApiError(502,"invalid_signature_keys");keys=List.copyOf(loaded);keysExpire=now()+86400;return keys;
    }
    private JsonObject get(URI uri,String bearer) {Response r=request("GET",uri,null,null,bearer);requireSuccess(r);return r.body;}
    private JsonObject json(URI uri,JsonObject body,String bearer) {Response r=request("POST",uri,"application/json",body.toString().getBytes(StandardCharsets.UTF_8),bearer);requireSuccess(r);return r.body;}
    private Response form(URI uri,Map<String,String> fields) {StringJoiner body=new StringJoiner("&");fields.forEach((key,value)->body.add(URLEncoder.encode(key,StandardCharsets.UTF_8)+"="+URLEncoder.encode(value,StandardCharsets.UTF_8)));return request("POST",uri,"application/x-www-form-urlencoded",body.toString().getBytes(StandardCharsets.UTF_8),null);}
    private Response request(String method,URI uri,String type,byte[] body,String bearer) {
        requireOpen();
        java.util.concurrent.CompletableFuture<HttpResponse<byte[]>> pendingResponse=null;
        try {
            var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8)).header("Accept","application/json").header("User-Agent","ArkWardrobe/1.0 (+https://github.com/Bladestar2105/Minecraft-Ark)");
            if(type!=null)builder.header("Content-Type",type);if(bearer!=null)builder.header("Authorization","Bearer "+bearer);
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
            pendingResponse=http.sendAsync(builder.build(),HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(),262144));
            HttpResponse<byte[]> response=pendingResponse.get(8,java.util.concurrent.TimeUnit.SECONDS);requireOpen();JsonObject parsed;
            try {parsed=JsonParser.parseString(new String(response.body(),StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new ApiError(502,"provider_invalid_response");}
            return new Response(response.statusCode(),parsed);
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiError(503,"cancelled");}
        catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException|IllegalArgumentException e){throw new ApiError(503,"provider_unavailable");}
        finally {if(pendingResponse!=null&&!pendingResponse.isDone())pendingResponse.cancel(true);}
    }
    private static void requireSuccess(Response r) {
        if(r.status>=200&&r.status<300)return;
        if(r.status==429)throw new ApiError(429,"provider_rate_limited");
        if(r.status==401||r.status==403 || (r.status==400&&r.body.has("error")&&"invalid_grant".equals(string(r.body,"error"))))throw new ApiError(401,"provider_unauthorized");
        throw new ApiError(502,"signing_failed");
    }
    static String string(JsonObject body,String key) {try {JsonElement e=body.get(key);if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())throw new IllegalArgumentException();String value=e.getAsString();if(value.length()>65536)throw new IllegalArgumentException();return value;}catch(Exception e){throw new ApiError(400,"invalid_request");}}
    private static long number(JsonObject body,String key,long fallback) {try{return body.get(key).getAsLong();}catch(Exception e){return fallback;}}
    private static long now() {return Instant.now().getEpochSecond();}
    private static void pause(long millis) {try {Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new ApiError(503,"cancelled");}}
    public void close(){closed=true;oauth.shutdownNow();}
}
