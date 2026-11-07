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
        unifiedAuthorizationAndBudgets();
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
        try(var api=new PrivateApi(new InetSocketAddress("127.0.0.1",0),PLAYER,ADMIN,sessions,accounts,operators,modules,ops::contains,s->{var p=new JsonObject();p.addProperty("uuid",s.uuid().toString());return p;})) {
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

    private static void unifiedAuthorizationAndBudgets() throws Exception {
        Path dir=Files.createTempDirectory("unified-api");final Instant[] clock={Instant.parse("2026-11-07T12:00:01Z")};
        var sessions=new Sessions(dir.resolve("sessions.json"),()->clock[0]);var accounts=new Accounts(dir.resolve("accounts.json"),sessions,null);
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();accounts.observe(alice,"Alice");accounts.observe(bob,"Bob");
        accounts.password(alice,"","original password123","original password123",true);
        Set<UUID> ops=new HashSet<>();ops.add(alice);
        var revokeDuringCheck=new java.util.concurrent.atomic.AtomicBoolean();
        var operators=new OperatorSessions(dir.resolve("operators.json"),()->clock[0],accounts,ops::contains);
        var modules=new ModuleRegistry();int[] activations={0},replies={0},writes={0};
        modules.register(new Object(),"wardrobe",request->{
            if(request.method().equals("POST")){writes[0]++;if(request.path().endsWith("/activate"))activations[0]++;}
            return new AccountsApi.Response(200,new JsonObject());
        },null);
        modules.register(new Object(),"support",request->{replies[0]++;return new AccountsApi.Response(200,new JsonObject());},null);
        String token=sessions.redeem(sessions.pair(alice,"Alice")).get("token").getAsString();String check="{\"token\":\""+token+"\"}";
        try(var api=new PrivateApi(new InetSocketAddress("127.0.0.1",0),PLAYER,ADMIN,sessions,accounts,operators,modules,uuid->{if(revokeDuringCheck.getAndSet(false))accounts.revoke(uuid);return ops.contains(uuid);},s->Sessions.view(s))) {
            api.start();base=URI.create("http://127.0.0.1:"+api.port()+"/");
            var checked=call("POST","/v1/admin/auth/player-check",ADMIN,null,check);
            TestSupport.check(checked.statusCode()==200,"ordinary player login authorizes current operator through admin-only check");
            JsonObject identity=JsonParser.parseString(checked.body()).getAsJsonObject();
            TestSupport.check(identity.get("uuid").getAsString().equals(alice.toString())&&!identity.has("token"),"operator identity canonical without issuing or exposing a token");
            TestSupport.check(call("POST","/v1/admin/auth/player-check",PLAYER,null,check).statusCode()==403,"player service cannot authorize administrator");
            TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,token,check).statusCode()==403,"admin check rejects mixed player-session header");
            TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,null,"{\"token\":\""+token+"\",\"uuid\":\""+bob+"\"}").statusCode()==400,"admin check accepts no browser-supplied identity override");
            JsonElement flag=identity.get("is_op");
            TestSupport.check(flag!=null&&flag.isJsonPrimitive()&&flag.getAsJsonPrimitive().isBoolean()&&flag.getAsBoolean(),"admin-only session check provides a strict boolean operator flag");
            ops.remove(alice);
            var deopped=call("POST","/v1/admin/auth/player-check",ADMIN,null,check);
            TestSupport.check(deopped.statusCode()==200&&!JsonParser.parseString(deopped.body()).getAsJsonObject().get("is_op").getAsBoolean(),"deop immediately reports loss of administration");
            var ordinary=call("GET","/v1/session",PLAYER,token,null);
            TestSupport.check(ordinary.statusCode()==200,"deop preserves ordinary player session");
            ops.add(alice);TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,null,check).statusCode()==200,"op immediately grants administration without another login");
            String bobToken=sessions.redeem(sessions.pair(bob,"Bob")).get("token").getAsString();
            var member=call("POST","/v1/admin/auth/player-check",ADMIN,null,"{\"token\":\""+bobToken+"\"}");
            TestSupport.check(member.statusCode()==200&&!JsonParser.parseString(member.body()).getAsJsonObject().get("is_op").getAsBoolean(),"ordinary member identity has no administration flag");
            accounts.adminPassword("Alice","changed password123");
            TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,null,check).statusCode()==401,"password reset revokes both player and admin authority");
            String raceToken=sessions.redeem(sessions.pair(alice,"Alice")).get("token").getAsString();revokeDuringCheck.set(true);
            TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,null,"{\"token\":\""+raceToken+"\"}").statusCode()==401,"revocation during main-thread lookup cannot authorize stale identity");
            String first=sessions.redeem(sessions.pair(alice,"Alice")).get("token").getAsString();
            String second=sessions.redeem(sessions.pair(alice,"Alice")).get("token").getAsString();
            String third=sessions.redeem(sessions.pair(alice,"Alice")).get("token").getAsString();
            clock[0]=clock[0].plusSeconds(61);
            String activate="/v1/skins/"+UUID.randomUUID()+"/activate";
            for(String lease:new String[]{first,first,second})TestSupport.check(call("POST",activate,PLAYER,lease,"{}").statusCode()==200,"normal skin activation admitted");
            var limited=call("POST",activate,PLAYER,third,"{}");
            TestSupport.check(limited.statusCode()==429&&activations[0]==3,"rotating session does not bypass activation quota or queue extra work");
            TestSupport.check(limited.headers().firstValue("Retry-After").orElse("").matches("[1-9][0-9]?"),"rate denial includes bounded Retry-After");
            TestSupport.check(call("POST",activate,PLAYER,bobToken,"{}").statusCode()==200,"another player retains activation capacity");
            String reply="/v1/support/"+UUID.randomUUID()+"/reply";
            for(int i=0;i<10;i++)TestSupport.check(call("POST",reply,PLAYER,i%2==0?first:second,"{}").statusCode()==200,"normal support reply admitted");
            TestSupport.check(call("POST",reply,PLAYER,third,"{}").statusCode()==429&&replies[0]==10,"support replies have an account-wide quota");
            clock[0]=clock[0].plusSeconds(61);
            for(int i=0;i<60;i++)TestSupport.check(call("POST","/v1/skins",PLAYER,i%2==0?first:second,"{}").statusCode()==200,"normal editor saves admitted");
            int before=writes[0];TestSupport.check(call("POST","/v1/skins",PLAYER,third,"{}").statusCode()==429&&writes[0]==before,"write quota cannot be bypassed with another session");
            TestSupport.check(call("GET","/v1/session",PLAYER,third,null).statusCode()==200,"write quota leaves profile/session reads available");
            for(int i=0;i<30;i++)TestSupport.check(call("POST","/v1/admin/signing/connect",ADMIN,null,"{}").statusCode()==200,"normal admin writes admitted");
            TestSupport.check(call("POST","/v1/admin/signing/connect",ADMIN,null,"{}").statusCode()==429,"admin writes have a separate quota");
            TestSupport.check(call("GET","/v1/admin/modules",ADMIN,null,null).statusCode()==200,"admin write quota leaves management reads available");
            clock[0]=clock[0].plusSeconds(61);
            for(int i=0;i<600;i++)TestSupport.check(call("GET","/v1/session",PLAYER,new String[]{first,second,third}[i%3],null).statusCode()==200,"normal account-wide read budget admitted");
            TestSupport.check(call("GET","/v1/session",PLAYER,first,null).statusCode()==429,"general quota follows UUID across sessions");
            TestSupport.check(call("GET","/v1/session",PLAYER,bobToken,null).statusCode()==200,"general quota preserves another account");
            clock[0]=clock[0].plusSeconds(61);
            TestSupport.check(call("POST",activate,PLAYER,third,"{}").statusCode()==200,"account budgets reopen after the retry window");
            for(int i=0;i<19;i++)sessions.redeem(sessions.pair(bob,"Bob"));
            var sessionLimit=call("POST","/v1/pair/redeem",PLAYER,null,"{\"code\":\""+sessions.pair(bob,"Bob")+"\"}");
            TestSupport.check(sessionLimit.statusCode()==429&&sessionLimit.headers().firstValue("Retry-After").isPresent(),"early session-limit denial includes Retry-After too");
            clock[0]=clock[0].plusSeconds(31L*86400);
            TestSupport.check(call("POST","/v1/admin/auth/player-check",ADMIN,null,"{\"token\":\""+first+"\"}").statusCode()==401,"expired player session cannot authorize administration");
        }
    }
}
