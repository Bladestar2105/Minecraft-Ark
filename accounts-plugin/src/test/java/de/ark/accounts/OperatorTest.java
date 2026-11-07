package de.ark.accounts;

import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class OperatorTest {
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("ark-operator");final Instant[] clock={Instant.parse("2026-11-07T10:00:00Z")};
        Sessions players=new Sessions(directory.resolve("players.json"),()->clock[0]);Accounts accounts=new Accounts(directory.resolve("accounts.json"),players,null);
        UUID alice=UUID.randomUUID(),bob=UUID.randomUUID();accounts.observe(alice,"Alice");accounts.observe(bob,"Bob");
        accounts.password(alice,"","alice password123","alice password123",true);accounts.password(bob,"","bob password123","bob password123",true);
        Set<UUID> ops=new HashSet<>();ops.add(alice);Path file=directory.resolve("operators.json");
        OperatorSessions operators=new OperatorSessions(file,()->clock[0],accounts,ops::contains);
        String playerCode=players.pair(alice,"Alice");TestSupport.denied(401,()->operators.loginCode(playerCode));
        String playerToken=accounts.login("Alice","alice password123").get("token").getAsString();String before=Files.readString(directory.resolve("players.json"));
        var login=operators.loginCredentials("Alice","alice password123");String token=login.get("token").getAsString();
        TestSupport.check(Files.readString(directory.resolve("players.json")).equals(before),"operator login does not issue player lease");
        TestSupport.check(login.get("uuid").getAsString().equals(alice.toString()),"operator identity canonical");
        TestSupport.check(login.get("expires_at").getAsLong()==clock[0].plusSeconds(8*3600).getEpochSecond(),"operator lifetime eight absolute hours");
        TestSupport.denied(401,()->players.require(token));TestSupport.denied(401,()->operators.check(playerToken));
        TestSupport.denied(401,()->operators.loginCredentials("Alice","wrong"));TestSupport.denied(403,()->operators.loginCredentials("Bob","bob password123"));
        TestSupport.check(!Files.readString(file).contains(token),"operator tokens hashed on disk");
        TestSupport.check(new OperatorSessions(file,()->clock[0],accounts,ops::contains).check(token).get("name").getAsString().equals("Alice"),"operator lease survives restart");
        String code=operators.pair(alice,"Alice");ops.remove(alice);
        TestSupport.denied(403,()->operators.loginCode(code));TestSupport.denied(403,()->operators.check(token));TestSupport.denied(403,()->operators.pair(alice,"Alice"));
        ops.add(alice);TestSupport.denied(401,()->operators.loginCode(code));TestSupport.denied(401,()->operators.check(token));
        String resetCode=operators.pair(alice,"Alice");accounts.password(alice,"alice password123","changed password123","changed password123",false);
        TestSupport.denied(401,()->operators.check(token));TestSupport.denied(401,()->operators.loginCode(resetCode));
        String renewed=operators.loginCredentials("Alice","changed password123").get("token").getAsString();
        clock[0]=clock[0].plusSeconds(8*3600+1);TestSupport.denied(401,()->operators.check(renewed));
        String logout=operators.loginCredentials("Alice","changed password123").get("token").getAsString();operators.logout(logout);TestSupport.denied(401,()->operators.check(logout));
        String logoutAll=operators.loginCredentials("Alice","changed password123").get("token").getAsString();accounts.revoke(alice);TestSupport.denied(401,()->operators.check(logoutAll));
        System.out.println("OperatorTest: "+TestSupport.checks+" checks passed");
    }
}
