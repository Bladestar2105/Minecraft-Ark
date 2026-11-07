package de.ark.wardrobe;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WardrobeRoutesTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("wardrobe-routes");AtomicBoolean open=new AtomicBoolean(true);UUID owner=UUID.randomUUID();var identity=new AccountsApi.Identity(owner,"Alice");
        try(var signers=new Signers(dir.resolve("signing.json"),Signers.Endpoints.fixture(URI.create("http://127.0.0.1:1/")));var store=new WardrobeStore(dir,signers,new WardrobeTest.MemoryGateway(),open::get)) {
            var routes=new WardrobeRoutes(store,signers,open::get);JsonObject body=WardrobeTest.body("Owned","classic",WardrobeTest.image(64,0xff123456));body.addProperty("uuid",UUID.randomUUID().toString());
            var create=routes.handle(new AccountsApi.Request("POST","/v1/skins",AccountsApi.Role.PLAYER,identity,body));WardrobeTest.check(create.status()==201,"module create preserves201");
            JsonObject skin=create.body().getAsJsonObject("skin");String path="/v1/skins/"+skin.get("id").getAsString();
            WardrobeTest.check(store.list(owner).getAsJsonArray("skins").size()==1,"verified identity owns saved skin");
            WardrobeTest.denied(403,()->routes.handle(new AccountsApi.Request("GET","/v1/admin/signing",AccountsApi.Role.PLAYER,identity,new JsonObject())));
            JsonObject revision=new JsonObject();revision.addProperty("revision",1);
            WardrobeTest.denied(404,()->routes.handle(new AccountsApi.Request("DELETE",path,AccountsApi.Role.PLAYER,new AccountsApi.Identity(UUID.randomUUID(),"Bob"),revision)));
            WardrobeTest.check(routes.handle(new AccountsApi.Request("POST",path+"/activate",AccountsApi.Role.PLAYER,identity,revision)).status()==202,"activation retains queued202");store.awaitIdle();
            WardrobeTest.check(store.list(owner).toString().contains("signing_unconfigured"),"unconfigured provider never reports success");
            open.set(false);WardrobeTest.denied(503,()->routes.handle(new AccountsApi.Request("POST","/v1/skins",AccountsApi.Role.PLAYER,identity,body)));
            WardrobeTest.denied(503,()->store.create(owner,body));
        }
        System.out.println("WardrobeRoutesTest: "+WardrobeTest.checks+" checks passed");
    }
}
