package de.ark.accounts;

import com.google.gson.JsonObject;
import de.ark.accounts.api.AccountsApi;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

final class ModuleRegistry {
    private final Map<String,Entry> entries=new ConcurrentHashMap<>();
    private final class Entry implements AccountsApi.Registration {
        final Object owner;final String module;final AccountsApi.Handler handler;final Function<AccountsApi.Identity,UUID> selected;
        final AtomicBoolean active=new AtomicBoolean(true);
        Entry(Object owner,String module,AccountsApi.Handler handler,Function<AccountsApi.Identity,UUID> selected){this.owner=owner;this.module=module;this.handler=handler;this.selected=selected;}
        public boolean open(){return active.get()&&entries.get(module)==this;}
        public void close(){active.set(false);entries.remove(module,this);}
    }
    AccountsApi.Registration register(Object owner,String module,AccountsApi.Handler handler,Function<AccountsApi.Identity,UUID> selected) {
        if(!Set.of("wardrobe","support").contains(module)||module.equals("support")&&selected!=null)throw new ApiError(400,"invalid_module");
        Entry next=new Entry(Objects.requireNonNull(owner),module,Objects.requireNonNull(handler),selected);
        if(entries.putIfAbsent(module,next)!=null)throw new ApiError(409,"module_conflict");return next;
    }
    private static boolean prefix(String path,String prefix){return path.equals(prefix)||path.startsWith(prefix+"/");}
    AccountsApi.Response dispatch(AccountsApi.Request request) {
        String module;AccountsApi.Role role;
        if(prefix(request.path(),"/v1/skins")){module="wardrobe";role=AccountsApi.Role.PLAYER;}
        else if(prefix(request.path(),"/v1/admin/signing")){module="wardrobe";role=AccountsApi.Role.SERVICE_ADMIN;}
        else if(prefix(request.path(),"/v1/support")){module="support";role=AccountsApi.Role.PLAYER;}
        else if(prefix(request.path(),"/v1/admin/support")){module="support";role=AccountsApi.Role.SERVICE_ADMIN;}
        else throw new ApiError(404,"not_found");
        if(role!=request.role())throw new ApiError(403,"forbidden");Entry entry=entries.get(module);if(entry==null)throw new ApiError(503,"module_unavailable");
        entry.requireOpen();AccountsApi.Response response=entry.handler.handle(request);entry.requireOpen();return response;
    }
    JsonObject capabilities(){JsonObject out=new JsonObject();out.addProperty("accounts",true);for(String id:List.of("wardrobe","support")){Entry e=entries.get(id);out.addProperty(id,e!=null&&e.open());}return out;}
    UUID selected(AccountsApi.Identity identity){Entry entry=entries.get("wardrobe");if(entry==null||!entry.open()||entry.selected==null)return null;UUID selected=entry.selected.apply(identity);return entry.open()?selected:null;}
    void closeOwner(Object owner){for(Entry entry:entries.values())if(entry.owner==owner)entry.close();}
    void closeAll(){for(Entry entry:entries.values())entry.close();}
}
