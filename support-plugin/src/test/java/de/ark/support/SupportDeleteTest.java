package de.ark.support;

import com.google.gson.*;
import de.ark.accounts.api.AccountsApi;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public final class SupportDeleteTest {
    private static final Instant NOW=Instant.parse("2026-11-07T10:00:00Z");
    private static void denied(int status,String code,Runnable action){try{action.run();throw new AssertionError("expected "+code);}catch(AccountsApi.Failure e){TestSupport.check(e.status==status&&e.code.equals(code),"expected "+code+", got "+e.code);}}
    private static JsonObject body(long revision){JsonObject body=new JsonObject();body.addProperty("revision",revision);return body;}
    private static AccountsApi.Request request(String method,String path,AccountsApi.Role role,AccountsApi.Identity identity,JsonObject body){return new AccountsApi.Request(method,path,role,identity,body);}
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("support-delete"),file=directory.resolve("support.json");
        List<SupportStore.Notice> notices=new ArrayList<>();SupportStore store=new SupportStore(file,()->NOW,notices::add);
        UUID owner=UUID.randomUUID();JsonObject created=store.create(owner,"Alice","normal","Remove this closed conversation");UUID id=SupportTest.id(created);
        JsonObject keep=store.create(UUID.randomUUID(),"Bob","low","Retain this conversation");UUID keptId=SupportTest.id(keep);
        denied(409,"support_open",()->store.delete(id,1));
        JsonObject closed=store.status(id,1,"closed");
        denied(409,"stale_revision",()->store.delete(id,1));
        TestSupport.check(store.get(owner,id,false).equals(closed),"stale deletion leaves the closed conversation intact");

        byte[] durable=Files.readAllBytes(file);Path backup=directory.resolve("before.json");Files.move(file,backup);Files.createDirectory(file);
        denied(503,"storage_unavailable",()->store.delete(id,2));
        TestSupport.check(store.get(owner,id,false).equals(closed),"failed persistence retains ticket in memory");
        TestSupport.check(Arrays.equals(Files.readAllBytes(backup),durable),"failed deletion preserves prior durable state");
        Files.delete(file);Files.move(backup,file);int noticesBefore=notices.size();store.delete(id,2);
        denied(404,"not_found",()->store.get(owner,id,false));
        TestSupport.check(notices.size()==noticesBefore,"deletion does not send activity notices");
        SupportStore restored=new SupportStore(file,()->NOW,notice->{});
        denied(404,"not_found",()->restored.get(null,id,true));
        TestSupport.check(restored.list(null,true).getAsJsonArray("tickets").size()==1&&restored.get(null,keptId,true).equals(keep),"deletion survives restart and retains other tickets");
        denied(404,"not_found",()->restored.delete(id,2));

        Path routesFile=directory.resolve("routes.json");SupportStore routeStore=new SupportStore(routesFile,()->NOW,notice->{});SupportRoutes routes=new SupportRoutes(routeStore,()->true);
        var identity=new AccountsApi.Identity(owner,"Alice");JsonObject ticket=routeStore.create(owner,"Alice","normal","Route deletion");String ticketId=ticket.get("id").getAsString();String adminPath="/v1/admin/support/"+ticketId;
        TestSupport.check(routes.handle(request("GET","/v1/admin/support",AccountsApi.Role.SERVICE_ADMIN,null,new JsonObject())).body().get("delete_closed").getAsBoolean(),"admin list advertises closed-ticket deletion");
        TestSupport.check(!routes.handle(request("GET","/v1/support",AccountsApi.Role.PLAYER,identity,new JsonObject())).body().has("delete_closed"),"player list does not advertise admin capability");
        JsonObject forged=body(1);forged.addProperty("role","SERVICE_ADMIN");forged.addProperty("admin",true);
        denied(403,"forbidden",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.PLAYER,identity,forged)));
        denied(404,"not_found",()->routes.handle(request("DELETE","/v1/support/"+ticketId,AccountsApi.Role.PLAYER,identity,forged)));
        denied(403,"forbidden",()->routes.handle(request("DELETE","/v1/support/"+ticketId,AccountsApi.Role.SERVICE_ADMIN,null,forged)));
        denied(400,"invalid_request",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,forged)));
        denied(400,"invalid_revision",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,body(0))));
        JsonObject stringRevision=new JsonObject();stringRevision.addProperty("revision","1");
        denied(400,"invalid_revision",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,stringRevision)));
        denied(404,"not_found",()->routes.handle(request("DELETE","/v1/admin/support/"+ticketId.toUpperCase(Locale.ROOT),AccountsApi.Role.SERVICE_ADMIN,null,body(1))));
        denied(409,"support_open",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,body(1))));
        JsonObject status=body(1);status.addProperty("status","closed");routes.handle(request("PUT",adminPath+"/status",AccountsApi.Role.SERVICE_ADMIN,null,status));
        denied(409,"stale_revision",()->routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,body(1))));
        var response=routes.handle(request("DELETE",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,body(2)));
        TestSupport.check(response.status()==200&&response.body().size()==1&&response.body().get("ok").getAsBoolean(),"admin DELETE returns only successful acknowledgement");
        denied(404,"not_found",()->routes.handle(request("GET",adminPath,AccountsApi.Role.SERVICE_ADMIN,null,new JsonObject())));
        denied(404,"not_found",()->new SupportStore(routesFile,()->NOW,notice->{}).get(owner,UUID.fromString(ticketId),false));
        UUID racingId=SupportTest.id(routeStore.create(UUID.randomUUID(),"Carol","normal","Concurrent reopen and delete"));routeStore.status(racingId,1,"closed");
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()){
            CountDownLatch start=new CountDownLatch(1);
            Future<String> deleting=workers.submit(()->{start.await();try{routeStore.delete(racingId,2);return "deleted";}catch(ApiError error){return error.code;}});
            Future<String> reopening=workers.submit(()->{start.await();try{routeStore.status(racingId,2,"open");return "reopened";}catch(ApiError error){return error.code;}});
            start.countDown();String deletion=deleting.get(),reopen=reopening.get();
            if(deletion.equals("deleted")){
                TestSupport.check(reopen.equals("not_found"),"deletion before reopen returns not_found without recreating the ticket");
                denied(404,"not_found",()->routeStore.get(null,racingId,true));
            }else{
                TestSupport.check(deletion.equals("stale_revision")&&reopen.equals("reopened"),"reopening before deletion invalidates the deletion revision");
                TestSupport.check(routeStore.get(null,racingId,true).get("status").getAsString().equals("open"),"concurrent reopening preserves the open ticket");
            }
        }
        routeStore.close();denied(503,"module_unavailable",()->routeStore.delete(keptId,1));
        System.out.println("SupportDeleteTest: "+TestSupport.checks+" checks passed");
    }
}
