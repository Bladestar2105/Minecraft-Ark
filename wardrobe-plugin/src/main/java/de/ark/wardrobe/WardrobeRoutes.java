package de.ark.wardrobe;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.util.*;
import java.util.function.BooleanSupplier;

final class WardrobeRoutes implements AccountsApi.Handler {
    private final WardrobeStore store;private final Signers signers;private final BooleanSupplier open;
    WardrobeRoutes(WardrobeStore store,Signers signers,BooleanSupplier open){this.store=store;this.signers=signers;this.open=open;}
    public AccountsApi.Response handle(AccountsApi.Request request) {
        if(!open.getAsBoolean())throw new ApiError(503,"module_unavailable");String path=request.path(),method=request.method();JsonObject body=request.body();
        if(path.startsWith("/v1/admin/")) {
            if(request.role()!=AccountsApi.Role.SERVICE_ADMIN)throw new ApiError(403,"forbidden");
            return new AccountsApi.Response(200,switch(method+" "+path){case "GET /v1/admin/signing"->signers.status();case "PUT /v1/admin/signing"->signers.update(body);case "POST /v1/admin/signing/connect"->signers.connect();case "POST /v1/admin/signing/disconnect"->signers.disconnect();default->throw new ApiError(404,"not_found");});
        }
        if(request.role()!=AccountsApi.Role.PLAYER)throw new ApiError(403,"forbidden");UUID owner=request.identity().uuid();
        if(path.equals("/v1/skins")){if(method.equals("GET"))return new AccountsApi.Response(200,store.list(owner));if(method.equals("POST"))return response(201,store.create(owner,body));throw new ApiError(404,"not_found");}
        String[] parts=path.split("/",-1);if(parts.length<4||parts.length>5||!parts[1].equals("v1")||!parts[2].equals("skins"))throw new ApiError(404,"not_found");
        UUID id;try{id=UUID.fromString(parts[3]);if(!id.toString().equals(parts[3]))throw new IllegalArgumentException();}catch(Exception e){throw new ApiError(404,"not_found");}long revision=revision(body);
        if(parts.length==4&&method.equals("PUT"))return response(200,store.update(owner,id,body,revision));
        if(parts.length==4&&method.equals("DELETE")){store.delete(owner,id,revision);JsonObject out=new JsonObject();out.addProperty("ok",true);return new AccountsApi.Response(200,out);}
        if(parts.length==5&&method.equals("POST")&&parts[4].equals("copy"))return response(201,store.copy(owner,id,Signers.string(body,"name"),revision));
        if(parts.length==5&&method.equals("POST")&&parts[4].equals("activate"))return response(202,store.activate(owner,id,revision));throw new ApiError(404,"not_found");
    }
    private static AccountsApi.Response response(int status,JsonObject skin){JsonObject out=new JsonObject();out.add("skin",skin);return new AccountsApi.Response(status,out);}
    private static long revision(JsonObject body){try{JsonElement value=body.get("revision");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException();return value.getAsLong();}catch(Exception e){throw new ApiError(400,"invalid_revision");}}
}
