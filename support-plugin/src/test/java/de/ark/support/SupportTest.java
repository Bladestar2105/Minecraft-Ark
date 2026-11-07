package de.ark.support;

import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class SupportTest {
    static UUID id(JsonObject ticket){return UUID.fromString(ticket.get("id").getAsString());}
    static long revision(JsonObject ticket){return ticket.get("revision").getAsLong();}
    public static void main(String[] args) throws Exception {
        Path directory=Files.createTempDirectory("ark-support"),file=directory.resolve("support.json");
        final Instant[] clock={Instant.parse("2026-11-07T10:00:00Z")};
        List<SupportStore.Notice> notices=new ArrayList<>();UUID owner=UUID.randomUUID(),stranger=UUID.randomUUID();
        SupportStore store=new SupportStore(file,()->clock[0],notices::add);
        JsonObject created=store.create(owner,"Alice",null,"Private <support> message\nsecond line");UUID ticket=id(created);
        TestSupport.check(created.get("priority").getAsString().equals("normal"),"default normal priority");
        TestSupport.check(created.getAsJsonArray("messages").get(0).getAsJsonObject().get("message").getAsString().equals("Private <support> message\nsecond line"),"plain text retained");
        TestSupport.check(notices.size()==1&&notices.getFirst().admins()&&!notices.toString().contains("Private"),"private notification references exclude content");
        TestSupport.check(store.list(stranger,false).getAsJsonArray("tickets").isEmpty(),"foreign tickets absent from player list");
        TestSupport.denied(404,()->store.get(stranger,ticket,false));
        TestSupport.denied(404,()->store.reply(stranger,ticket,1,"steal",false));
        TestSupport.denied(409,()->store.reply(owner,ticket,99,"stale",false));
        TestSupport.denied(429,()->store.create(owner,"Alice","high","repeat"));
        TestSupport.denied(400,()->store.create(stranger,"Bob","urgent","invalid"));
        TestSupport.denied(400,()->store.create(stranger,"Bob","low","   "));
        TestSupport.denied(400,()->store.create(stranger,"Bob","low","control\u0000"));
        TestSupport.denied(400,()->store.create(stranger,"Bob","low","x".repeat(2001)));
        JsonObject replied=store.reply(owner,ticket,1,"Player reply",false);
        TestSupport.check(revision(replied)==2&&notices.getLast().admins(),"player reply increments revision and notifies admins");
        replied=store.reply(null,ticket,2,"Administrator reply",true);
        TestSupport.check(revision(replied)==3&&!notices.getLast().admins()&&notices.getLast().owner().equals(owner),"admin reply privately notifies owner");
        JsonObject closed=store.status(ticket,3,"closed");
        TestSupport.denied(409,()->store.reply(owner,ticket,revision(closed),"closed reply",false));
        TestSupport.denied(409,()->store.status(ticket,3,"open"));
        TestSupport.denied(400,()->store.status(ticket,revision(closed),"deleted"));
        JsonObject reopened=store.status(ticket,revision(closed),"open");
        TestSupport.check(reopened.get("status").getAsString().equals("open"),"admin can reopen");
        for(int i=0;i<4;i++){clock[0]=clock[0].plusSeconds(60);store.create(owner,"Alice","low","another "+i);}
        clock[0]=clock[0].plusSeconds(60);TestSupport.denied(429,()->store.create(owner,"Alice","normal","sixth open"));
        var summaries=store.list(null,true).getAsJsonArray("tickets");
        TestSupport.check(summaries.size()==5&&!summaries.get(0).getAsJsonObject().has("messages")&&summaries.get(0).getAsJsonObject().has("message_count"),"bounded summary list excludes conversation bodies");
        SupportStore restored=new SupportStore(file,()->clock[0],notices::add);
        TestSupport.check(restored.get(owner,ticket,false).getAsJsonArray("messages").size()==3,"durable conversation survives restart");
        TestSupport.denied(429,()->restored.create(owner,"Alice","normal","restart preserves open limit"));
        long current=revision(restored.get(owner,ticket,false));
        while(restored.get(owner,ticket,false).getAsJsonArray("messages").size()<50){current=revision(restored.reply(owner,ticket,current,"bounded reply",false));}
        long fullRevision=current;TestSupport.denied(409,()->restored.reply(owner,ticket,fullRevision,"51st",false));
        var limits=JsonParser.parseString(Files.readString(file)).getAsJsonArray();var fixture=new JsonArray();JsonObject template=limits.get(0).getAsJsonObject();
        for(int i=0;i<SupportStore.MAX_TICKETS;i++){JsonObject copy=template.deepCopy();copy.addProperty("id",UUID.randomUUID().toString());copy.addProperty("player_uuid",UUID.randomUUID().toString());fixture.add(copy);}
        Path full=directory.resolve("full.json");Files.writeString(full,fixture.toString());SupportStore capacity=new SupportStore(full,()->clock[0],notice->{});
        TestSupport.denied(503,()->capacity.create(stranger,"Bob","normal","full"));
        TestSupport.check(capacity.list(null,true).getAsJsonArray("tickets").size()==100,"list bounded to latest100");
        Files.writeString(file,"corrupt");TestSupport.denied(503,()->new SupportStore(file,()->clock[0],notice->{}));
        TestSupport.check(Files.readString(file).equals("corrupt"),"corrupt support data never silently replaced");
        System.out.println("SupportTest: "+TestSupport.checks+" checks passed");
    }
}
