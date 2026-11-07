package de.ark.support;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.util.*;
import java.util.function.BooleanSupplier;

final class SupportRoutes implements AccountsApi.Handler {
    private final SupportStore store;private final BooleanSupplier open;
    SupportRoutes(SupportStore store,BooleanSupplier open){this.store=store;this.open=open;}
    public AccountsApi.Response handle(AccountsApi.Request request) {
        if(!open.getAsBoolean())throw new ApiError(503,"module_unavailable");String path=request.path(),method=request.method();boolean admin=path.startsWith("/v1/admin/");
        if(admin!=(request.role()==AccountsApi.Role.SERVICE_ADMIN))throw new ApiError(403,"forbidden");UUID owner=admin?null:request.identity().uuid();JsonObject body=request.body();String base=admin?"/v1/admin/support":"/v1/support";
        if(path.equals(base)) {
            if(method.equals("GET")){JsonObject out=store.list(owner,admin);if(admin)out.addProperty("delete_closed",true);return new AccountsApi.Response(200,out);}
            if(!admin&&method.equals("POST"))return response(201,store.create(owner,request.identity().name(),body.has("priority")?string(body,"priority"):null,string(body,"message")));
            throw new ApiError(404,"not_found");
        }
        if(!path.startsWith(base+"/"))throw new ApiError(404,"not_found");String[] parts=path.substring(base.length()+1).split("/",-1);if(parts.length<1||parts.length>2)throw new ApiError(404,"not_found");
        UUID id;try{id=UUID.fromString(parts[0]);if(!id.toString().equals(parts[0]))throw new IllegalArgumentException();}catch(Exception e){throw new ApiError(404,"not_found");}
        if(parts.length==1&&method.equals("GET"))return response(200,store.get(owner,id,admin));
        if(admin&&parts.length==1&&method.equals("DELETE")){
            if(!body.keySet().equals(Set.of("revision")))throw new ApiError(400,"invalid_request");
            store.delete(id,revision(body));JsonObject out=new JsonObject();out.addProperty("ok",true);return new AccountsApi.Response(200,out);
        }
        if(parts.length==2&&parts[1].equals("reply")&&method.equals("POST"))return response(200,store.reply(owner,id,revision(body),string(body,"message"),admin));
        if(admin&&parts.length==2&&parts[1].equals("status")&&method.equals("PUT"))return response(200,store.status(id,revision(body),string(body,"status")));throw new ApiError(404,"not_found");
    }
    private static AccountsApi.Response response(int status,JsonObject ticket){JsonObject out=new JsonObject();out.add("ticket",ticket);return new AccountsApi.Response(status,out);}
    private static String string(JsonObject body,String key){try{JsonElement value=body.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException();return value.getAsString();}catch(Exception e){throw new ApiError(400,"invalid_request");}}
    private static long revision(JsonObject body){try{JsonElement value=body.get("revision");if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isNumber()||!value.getAsString().matches("[1-9][0-9]{0,15}"))throw new IllegalArgumentException();return value.getAsLong();}catch(Exception e){throw new ApiError(400,"invalid_revision");}}
}
