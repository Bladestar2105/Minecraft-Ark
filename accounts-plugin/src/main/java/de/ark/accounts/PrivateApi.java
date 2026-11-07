package de.ark.accounts;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.sun.net.httpserver.*;
import de.ark.accounts.api.AccountsApi;
import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

final class PrivateApi implements AutoCloseable {
    static final int MAX_BODY=196608;
    private final HttpServer server;
    private final ExecutorService executor;
    private final String playerKey,adminKey;
    private final Sessions sessions;
    private final Accounts accounts;
    private final OperatorSessions operators;
    private final ModuleRegistry modules;
    private final Function<Sessions.Session,JsonObject> profile;
    private final Map<String,long[]> limits=new LinkedHashMap<>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,long[]> e){return size()>2048;}};
    PrivateApi(InetSocketAddress address,String playerKey,String adminKey,Sessions sessions,Accounts accounts,OperatorSessions operators,ModuleRegistry modules,Function<Sessions.Session,JsonObject> profile) throws IOException {
        if(playerKey==null || adminKey==null || playerKey.length()<32 || adminKey.length()<32 || same(playerKey,adminKey))throw new IllegalArgumentException("Distinct service secrets required");
        this.playerKey=playerKey;this.adminKey=adminKey;this.sessions=sessions;this.accounts=accounts;this.operators=operators;this.modules=modules;this.profile=profile;
        System.setProperty("sun.net.httpserver.maxReqTime",System.getProperty("sun.net.httpserver.maxReqTime","15"));
        System.setProperty("sun.net.httpserver.maxRspTime",System.getProperty("sun.net.httpserver.maxRspTime","45"));
        System.setProperty("jdk.httpserver.maxConnections",System.getProperty("jdk.httpserver.maxConnections","100"));
        server=HttpServer.create(address,32);
        executor=new ThreadPoolExecutor(4,4,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(32),r->{Thread t=new Thread(r,"ArkAccounts-http");t.setDaemon(true);return t;});
        server.setExecutor(executor);server.createContext("/",this::handle);
    }
    void start(){server.start();}int port(){return server.getAddress().getPort();}
    private static boolean same(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private synchronized void budget(String bucket,int max) {
        long now=System.currentTimeMillis()/60000;long[] value=limits.computeIfAbsent(bucket,k->new long[]{now,0});
        if(value[0]!=now){value[0]=now;value[1]=0;}if(++value[1]>max)throw new ApiError(429,"busy");
    }
    private void handle(HttpExchange exchange) throws IOException {
        int status=200;JsonObject response;
        try {
            String raw=exchange.getRequestURI().getRawPath();
            if(exchange.getRequestURI().getRawQuery()!=null || raw.contains("%") || raw.contains("..") || raw.length()>160)throw new ApiError(400,"invalid_request");
            String method=exchange.getRequestMethod(),path=exchange.getRequestURI().getPath();
            List<String> auth=exchange.getRequestHeaders().get("Authorization");
            if(auth==null || auth.size()!=1 || auth.getFirst().length()>1200 || !auth.getFirst().startsWith("Bearer "))throw new ApiError(401,"unauthorized");
            String bearer=auth.getFirst().substring(7);boolean player=same(bearer,playerKey),admin=same(bearer,adminKey);
            if(!player&&!admin)throw new ApiError(401,"unauthorized");
            boolean adminPath=path.startsWith("/v1/admin/");
            if(adminPath?(!admin || exchange.getRequestHeaders().containsKey("X-Player-Session")):!player)throw new ApiError(403,"forbidden");
            JsonObject body=method.equals("GET")?new JsonObject():body(exchange);
            if(adminPath) {
                if(path.startsWith("/v1/admin/auth/")) {
                    if(!method.equals("POST"))throw new ApiError(404,"not_found");
                    switch(path) {
                        case "/v1/admin/auth/login" -> {
                            if(body.has("code")) {
                                if(body.has("username")||body.has("password"))throw new ApiError(400,"invalid_request");
                                budget("operator-code",60);response=operators.loginCode(Protocol.string(body,"code"));
                            } else {
                                String name=Protocol.string(body,"username");budget("operator-login:"+Sessions.hash(name.toLowerCase(Locale.ROOT)),5);budget("operator-login",60);
                                response=operators.loginCredentials(name,Protocol.string(body,"password"));
                            }
                        }
                        case "/v1/admin/auth/check" -> {String token=Protocol.string(body,"token");budget("operator-check:"+Sessions.hash(token),600);response=operators.check(token);}
                        case "/v1/admin/auth/logout" -> {String token=Protocol.string(body,"token");budget("operator-check:"+Sessions.hash(token),600);operators.logout(token);response=ok();}
                        default -> throw new ApiError(404,"not_found");
                    }
                } else {
                    budget("admin",120);
                    switch(method+" "+path) {
                        case "GET /v1/admin/modules" -> response=modules.capabilities();
                        case "GET /v1/admin/accounts" -> response=accounts.list();
                        case "POST /v1/admin/accounts/password" -> {
                            Accounts.validate(Protocol.string(body,"new_password"),Protocol.string(body,"confirmation"));
                            accounts.adminPassword(Protocol.string(body,"username"),Protocol.string(body,"new_password"));response=ok();
                        }
                        default -> {var result=modules.dispatch(new AccountsApi.Request(method,path,AccountsApi.Role.SERVICE_ADMIN,null,body));response=result.body();status=result.status();}
                    }
                }
            } else if(path.equals("/v1/pair/redeem") && method.equals("POST")) {
                budget("pair",60);response=sessions.redeem(Protocol.string(body,"code"));
            } else if(path.equals("/v1/login") && method.equals("POST")) {
                String username=Protocol.string(body,"username");budget("login:"+Sessions.hash(username.toLowerCase(Locale.ROOT)),5);budget("login",60);response=accounts.login(username,Protocol.string(body,"password"));
            } else if(path.equals("/v1/recover") && method.equals("POST")) {
                String username=Protocol.string(body,"username");budget("recover:"+Sessions.hash(username.toLowerCase(Locale.ROOT)),5);budget("recover",30);
                accounts.recover(username,Protocol.string(body,"recovery_code"),Protocol.string(body,"new_password"),Protocol.string(body,"confirmation"));response=ok();
            } else {
                List<String> tokens=exchange.getRequestHeaders().get("X-Player-Session");if(tokens==null || tokens.size()!=1)throw new ApiError(401,"unauthorized");
                String token=tokens.getFirst();Sessions.Session session=sessions.require(token);UUID owner=session.uuid();budget("session:"+Sessions.hash(token),240);
                switch(method+" "+path) {
                    case "GET /v1/session" -> response=Sessions.view(session);
                    case "GET /v1/profile" -> response=profile.apply(session);
                    case "GET /v1/modules" -> response=modules.capabilities();
                    case "GET /v1/account" -> {response=accounts.status(owner);response.addProperty("password_reset_allowed",session.gameProofUntil()>sessions.now());}
                    case "POST /v1/logout" -> {sessions.logout(token);response=ok();}
                    case "POST /v1/password" -> {budget("password:"+owner,5);accounts.password(owner,Protocol.string(body,"current_password"),Protocol.string(body,"new_password"),Protocol.string(body,"confirmation"),session.gameProofUntil()>sessions.now());response=ok();}
                    case "POST /v1/recovery", "DELETE /v1/recovery" -> {budget("recovery:"+owner,5);response=accounts.recovery(owner,Protocol.string(body,"current_password"),body.has("recovery_code")?Protocol.string(body,"recovery_code"):null,method.equals("DELETE"));}
                    default -> {var result=modules.dispatch(new AccountsApi.Request(method,path,AccountsApi.Role.PLAYER,new AccountsApi.Identity(owner,session.name()),body));response=result.body();status=result.status();}
                }
            }
        }catch(AccountsApi.Failure error){status=error.status;response=error(error.code);}
        catch(Exception error){status=503;response=error("service_unavailable");}
        byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        try {exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}finally {exchange.close();}
    }
    private static JsonObject body(HttpExchange exchange) throws IOException {
        String type=exchange.getRequestHeaders().getFirst("Content-Type");if(type==null || !type.split(";",2)[0].equalsIgnoreCase("application/json"))throw new ApiError(415,"invalid_content_type");
        String size=exchange.getRequestHeaders().getFirst("Content-Length");if(size!=null)try {if(Long.parseLong(size)>MAX_BODY)throw new ApiError(413,"body_too_large");}catch(NumberFormatException e){throw new ApiError(400,"invalid_request");}
        byte[] bytes=exchange.getRequestBody().readNBytes(MAX_BODY+1);if(bytes.length>MAX_BODY)throw new ApiError(413,"body_too_large");
        try(var reader=new JsonReader(new StringReader(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()))) {
            reader.setStrictness(Strictness.STRICT);reader.setNestingLimit(16);
            JsonElement value=JsonParser.parseReader(reader);if(!value.isJsonObject() || reader.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException();return value.getAsJsonObject();
        }catch(Exception e){throw new ApiError(400,"invalid_request");}
    }
    static JsonObject ok(){JsonObject out=new JsonObject();out.addProperty("ok",true);return out;}
    private static JsonObject error(String code){JsonObject out=new JsonObject();out.addProperty("error",code.replace('_',' '));out.addProperty("code",code);return out;}
    public void close(){server.stop(0);executor.shutdownNow();}
}
