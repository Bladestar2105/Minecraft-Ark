package de.ark.accounts;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.util.*;
import java.util.concurrent.*;

public final class ModuleRegistryTest {
    static AccountsApi.Request request(String path,AccountsApi.Role role,AccountsApi.Identity identity){return new AccountsApi.Request("GET",path,role,identity,new JsonObject());}
    public static void main(String[] args) throws Exception {
        ModuleRegistry registry=new ModuleRegistry();Object owner=new Object();UUID uuid=UUID.randomUUID();var identity=new AccountsApi.Identity(uuid,"Alice");
        TestSupport.check(!registry.capabilities().get("wardrobe").getAsBoolean()&&!registry.capabilities().get("support").getAsBoolean(),"optional modules initially absent");
        for(String invalid:List.of("accounts","/v1/login","wardrobe/sub"))TestSupport.denied(400,()->registry.register(owner,invalid,r->new AccountsApi.Response(200,new JsonObject()),null));
        var registration=registry.register(owner,"wardrobe",r->{JsonObject body=new JsonObject();body.addProperty("owner",r.identity()==null?"admin":r.identity().uuid().toString());return new AccountsApi.Response(202,body);},i->uuid);
        TestSupport.check(registry.dispatch(request("/v1/skins",AccountsApi.Role.PLAYER,identity)).status()==202,"module response status retained");
        TestSupport.check(registry.dispatch(request("/v1/skins/child",AccountsApi.Role.PLAYER,identity)).body().get("owner").getAsString().equals(uuid.toString()),"verified player identity passed to module");
        TestSupport.denied(403,()->registry.dispatch(request("/v1/admin/signing",AccountsApi.Role.PLAYER,identity)));
        TestSupport.denied(404,()->registry.dispatch(request("/v1/skinsx",AccountsApi.Role.PLAYER,identity)));
        TestSupport.denied(409,()->registry.register(new Object(),"wardrobe",r->null,null));
        TestSupport.check(registry.selected(identity).equals(uuid),"registered selected-skin callback");
        registration.close();TestSupport.check(registry.selected(identity)==null&&!registry.capabilities().get("wardrobe").getAsBoolean(),"disable removes routes and profile callback");
        TestSupport.denied(503,registration::requireOpen);TestSupport.denied(503,()->registry.dispatch(request("/v1/skins",AccountsApi.Role.PLAYER,identity)));
        var replacement=registry.register(new Object(),"wardrobe",r->new AccountsApi.Response(200,new JsonObject()),null);registration.close();TestSupport.check(replacement.open(),"stale close cannot unregister replacement");
        registry.closeOwner(owner);TestSupport.check(replacement.open(),"old plugin owner cannot unregister replacement");replacement.close();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        var pending=registry.register(owner,"support",r->{entered.countDown();try {release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return new AccountsApi.Response(200,new JsonObject());},null);
        var call=CompletableFuture.runAsync(()->TestSupport.denied(503,()->registry.dispatch(request("/v1/support",AccountsApi.Role.PLAYER,identity))));
        TestSupport.check(entered.await(2,TimeUnit.SECONDS),"handler entered");pending.close();release.countDown();call.get(2,TimeUnit.SECONDS);
        System.out.println("ModuleRegistryTest: "+TestSupport.checks+" checks passed");
    }
}
