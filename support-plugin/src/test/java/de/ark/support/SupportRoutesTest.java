package de.ark.support;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SupportRoutesTest {
    public static void main(String[] args) throws Exception {
        Path file=Files.createTempDirectory("support-routes").resolve("support.json");AtomicBoolean open=new AtomicBoolean(true);UUID owner=UUID.randomUUID();var identity=new AccountsApi.Identity(owner,"Alice");
        var store=new SupportStore(file,Instant::now,n->{},open::get);var routes=new SupportRoutes(store,open::get);JsonObject body=new JsonObject();body.addProperty("message","Private <message>");body.addProperty("player_uuid",UUID.randomUUID().toString());
        var created=routes.handle(new AccountsApi.Request("POST","/v1/support",AccountsApi.Role.PLAYER,identity,body));JsonObject ticket=created.body().getAsJsonObject("ticket");
        TestSupport.check(created.status()==201&&ticket.get("player_uuid").getAsString().equals(owner.toString()),"ticket owner from verified session");String id=ticket.get("id").getAsString();
        TestSupport.denied(404,()->routes.handle(new AccountsApi.Request("GET","/v1/support/"+id,AccountsApi.Role.PLAYER,new AccountsApi.Identity(UUID.randomUUID(),"Bob"),new JsonObject())));
        TestSupport.denied(403,()->routes.handle(new AccountsApi.Request("GET","/v1/admin/support",AccountsApi.Role.PLAYER,identity,new JsonObject())));
        JsonObject reply=new JsonObject();reply.addProperty("revision",1);reply.addProperty("message","Admin answer");
        TestSupport.check(routes.handle(new AccountsApi.Request("POST","/v1/admin/support/"+id+"/reply",AccountsApi.Role.SERVICE_ADMIN,null,reply)).body().getAsJsonObject("ticket").get("revision").getAsLong()==2,"admin reply increments revision");
        TestSupport.denied(409,()->routes.handle(new AccountsApi.Request("POST","/v1/support/"+id+"/reply",AccountsApi.Role.PLAYER,identity,reply)));
        String before=Files.readString(file);open.set(false);TestSupport.denied(503,()->routes.handle(new AccountsApi.Request("POST","/v1/support",AccountsApi.Role.PLAYER,identity,body)));
        TestSupport.check(Files.readString(file).equals(before),"closed module cannot mutate durable support state");
        System.out.println("SupportRoutesTest: "+TestSupport.checks+" checks passed");
    }
}
