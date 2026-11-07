package de.ark.accounts;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class PrivateApiTest {
    static final String PLAYER="P".repeat(48),ADMIN="A".repeat(48);
    static HttpClient http=HttpClient.newHttpClient();static URI base;
    static HttpResponse<String> call(String method,String path,String key,String session,String body) throws Exception {
        var request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(3)).header("Authorization","Bearer "+key).header("Content-Type","application/json");
        if(session!=null)request.header("X-Player-Session",session);
        return http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("wardrobe-api");final Instant[] clock={Instant.now()};var sessions=new Sessions(dir.resolve("sessions.json"),()->clock[0]);var accounts=new Accounts(dir.resolve("accounts.json"),sessions,null);
        UUID uuid=UUID.randomUUID();accounts.observe(uuid,"Alice");String code=sessions.pair(uuid,"Alice");
        ModuleRegistry modules=new ModuleRegistry();Set<UUID> ops=new HashSet<>();ops.add(uuid);OperatorSessions operators=new OperatorSessions(dir.resolve("operators.json"),()->clock[0],accounts,ops::contains);
        Map<UUID,JsonObject> library=new HashMap<>();
        modules.register(new Object(),"wardrobe",request->{
            if(request.role()==AccountsApi.Role.SERVICE_ADMIN)return new AccountsApi.Response(200,new JsonObject());
            JsonObject out=new JsonObject();
            if(request.method().equals("POST")){JsonObject skin=request.body().deepCopy();skin.addProperty("owner",request.identity().uuid().toString());library.put(request.identity().uuid(),skin);out.add("skin",skin);return new AccountsApi.Response(201,out);}
            JsonArray skins=new JsonArray();if(library.containsKey(request.identity().uuid()))skins.add(library.get(request.identity().uuid()));out.add("skins",skins);return new AccountsApi.Response(200,out);
        },null);
        try(var api=new PrivateApi(new InetSocketAddress("127.0.0.1",0),PLAYER,ADMIN,sessions,accounts,operators,modules,s->{var p=new JsonObject();p.addProperty("uuid",s.uuid().toString());return p;})) {
            api.start();base=URI.create("http://127.0.0.1:"+api.port()+"/");
            TestSupport.check(call("GET","/v1/admin/signing",PLAYER,null,null).statusCode()==403,"player key cannot access admin");
            TestSupport.check(call("GET","/v1/skins",ADMIN,null,null).statusCode()==403,"admin key cannot act as player");
            TestSupport.check(call("GET","/v1/skins","wrong",null,null).statusCode()==401,"service authentication required");
            TestSupport.check(call("GET","/v1/skins",PLAYER,null,null).statusCode()==401,"session authentication required");
            var login=call("POST","/v1/pair/redeem",PLAYER,null,"{\"code\":\""+code+"\"}");
            TestSupport.check(login.statusCode()==200,"pair consumed");String token=JsonParser.parseString(login.body()).getAsJsonObject().get("token").getAsString();
            TestSupport.check(call("POST","/v1/pair/redeem",PLAYER,null,"{\"code\":\""+code+"\"}").statusCode()==401,"pair replay denied");
            TestSupport.check(call("GET","/v1/admin/signing",ADMIN,token,null).statusCode()==403,"player session never authorizes admin");
            TestSupport.check(JsonParser.parseString(call("GET","/v1/account",PLAYER,token,null).body()).getAsJsonObject().get("password_reset_allowed").getAsBoolean(),"fresh game proof is exposed");
            var skin=new JsonObject();skin.addProperty("name","Owned");skin.addProperty("uuid",UUID.randomUUID().toString());
            var created=call("POST","/v1/skins",PLAYER,token,skin.toString());TestSupport.check(created.statusCode()==201,"module create status retained");
            TestSupport.check(call("GET","/v1/skins",PLAYER,token,null).body().contains("Owned"),"UUID body does not change ownership");
            TestSupport.check(call("GET","/v1/profile",PLAYER,token,null).body().contains(uuid.toString()),"profile native UUID");
            TestSupport.check(call("POST","/v1/skins",PLAYER,token,"[1]").statusCode()==400,"object only");
            TestSupport.check(call("POST","/v1/skins",PLAYER,token,"{\"png\":\""+"a".repeat(200000)+"\"}").statusCode()==413,"body length bounded");
            TestSupport.check(call("GET","/v1/skins?uuid=other",PLAYER,token,null).statusCode()==400,"no query owner override");
            TestSupport.check(call("POST","/v1/logout",PLAYER,token,"{}").statusCode()==200,"logout");
            TestSupport.check(call("GET","/v1/session",PLAYER,token,null).statusCode()==401,"logout revokes token");
            accounts.password(uuid,"","a valid password123","a valid password123",true);
            TestSupport.check(call("GET","/v1/admin/modules",ADMIN,null,null).body().contains("\"accounts\":true"),"bootstrap admin module capabilities");
            TestSupport.check(call("POST","/v1/admin/auth/login",PLAYER,null,"{\"username\":\"Alice\",\"password\":\"a valid password123\"}").statusCode()==403,"player key cannot operator-login");
            TestSupport.check(call("POST","/v1/admin/auth/login",ADMIN,null,"{\"code\":\""+sessions.pair(uuid,"Alice")+"\"}").statusCode()==401,"ordinary pairing code cannot elevate");
            var operator=call("POST","/v1/admin/auth/login",ADMIN,null,"{\"username\":\"Alice\",\"password\":\"a valid password123\"}");
            TestSupport.check(operator.statusCode()==200,"explicit operator login");String opToken=JsonParser.parseString(operator.body()).getAsJsonObject().get("token").getAsString();
            String checkBody="{\"token\":\""+opToken+"\"}";
            boolean checksPassed=true;for(int i=0;i<125;i++)checksPassed&=call("POST","/v1/admin/auth/check",ADMIN,null,checkBody).statusCode()==200;TestSupport.check(checksPassed,"OP checks independent of shared admin budget");
            TestSupport.check(call("GET","/v1/admin/modules",ADMIN,null,null).statusCode()==200,"OP checks retain bootstrap admin capacity");
            ops.remove(uuid);TestSupport.check(call("POST","/v1/admin/auth/check",ADMIN,null,checkBody).statusCode()==403,"deop rejects current operator lease");
            TestSupport.check(call("GET","/v1/admin/modules",ADMIN,null,null).statusCode()==200,"bootstrap administrator does not require operator token");ops.add(uuid);

            for(int i=0;i<60;i++)call("POST","/v1/login",PLAYER,null,"{\"username\":\"Unknown\",\"password\":\"wrong\"}");
            TestSupport.check(call("POST","/v1/login",PLAYER,null,"{\"username\":\"Alice\",\"password\":\"a valid password123\"}").statusCode()==200,"throttled username cannot exhaust unrelated login capacity");
            String passwordToken=accounts.login("Alice","a valid password123").get("token").getAsString();
            TestSupport.check(!JsonParser.parseString(call("GET","/v1/account",PLAYER,passwordToken,null).body()).getAsJsonObject().get("password_reset_allowed").getAsBoolean(),"password login has no game-proof reset");
            String fresh=sessions.redeem(sessions.pair(uuid,"Alice")).get("token").getAsString();clock[0]=clock[0].plusSeconds(301);
            TestSupport.check(!JsonParser.parseString(call("GET","/v1/account",PLAYER,fresh,null).body()).getAsJsonObject().get("password_reset_allowed").getAsBoolean(),"five-minute proof expires independently of session");
            TestSupport.check(call("POST","/v1/password",PLAYER,fresh,"{\"current_password\":\"\",\"new_password\":\"expired reset password\",\"confirmation\":\"expired reset password\"}").statusCode()==401,"expired proof cannot reset configured password");
        }
        System.out.println("PrivateApiTest: "+TestSupport.checks+" checks passed");
    }
}
