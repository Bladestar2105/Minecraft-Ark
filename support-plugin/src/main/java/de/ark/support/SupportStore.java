package de.ark.support;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

final class SupportStore {
    static final int MAX_TICKETS=1000,MAX_BYTES=16*1024*1024;
    record Notice(UUID owner,UUID ticket,String priority,boolean admins) { }
    private static final class Message {String author,message,created_at;}
    private static final class Ticket {
        String id,player_uuid,player_name,priority,status,created_at,updated_at;
        long revision;
        List<Message> messages=new ArrayList<>();
    }
    private final Path file;
    private final Supplier<Instant> clock;
    private final Consumer<Notice> notifier;
    private final BooleanSupplier enabled;
    private volatile boolean closed;
    private Map<String,Ticket> tickets=new LinkedHashMap<>();
    SupportStore(Path file,Supplier<Instant> clock,Consumer<Notice> notifier) {
        this(file,clock,notifier,()->true);
    }
    SupportStore(Path file,Supplier<Instant> clock,Consumer<Notice> notifier,BooleanSupplier enabled) {
        this.file=file;this.clock=clock;this.notifier=notifier;this.enabled=enabled;
        try {
            if(Files.exists(file)) {
                if(Files.size(file)>MAX_BYTES)throw new IllegalArgumentException();
                JsonArray rows=JsonParser.parseString(Files.readString(file)).getAsJsonArray();if(rows.size()>MAX_TICKETS)throw new IllegalArgumentException();
                for(JsonElement row:rows) {
                    Ticket ticket=Data.JSON.fromJson(row,Ticket.class);
                    if(ticket==null || !uuid(ticket.id) || !uuid(ticket.player_uuid) || ticket.player_name==null || ticket.player_name.isBlank() || ticket.player_name.length()>64 || ticket.player_name.codePoints().anyMatch(Character::isISOControl) || !Set.of("open","closed").contains(ticket.status) || ticket.revision<1 || ticket.revision>9_007_199_254_740_991L || ticket.messages==null || ticket.messages.isEmpty() || ticket.messages.size()>50 || ticket.revision<ticket.messages.size())throw new IllegalArgumentException();
                    priority(ticket.priority);Instant.parse(ticket.created_at);Instant.parse(ticket.updated_at);
                    for(Message message:ticket.messages){if(message==null || !Set.of("player","admin").contains(message.author))throw new IllegalArgumentException();text(message.message);Instant.parse(message.created_at);}
                    if(tickets.put(ticket.id,ticket)!=null)throw new IllegalArgumentException();
                }
            }
        }catch(Exception e){throw new ApiError(503,"storage_unavailable");}
    }
    private static boolean uuid(String value){try{return UUID.fromString(value).toString().equals(value);}catch(Exception e){return false;}}
    private static String priority(String value){if(value==null)return "normal";if(!Set.of("low","normal","high").contains(value))throw new ApiError(400,"invalid_priority");return value;}
    private static String text(String value) {
        if(value==null)throw new ApiError(400,"invalid_message");value=value.replace("\r\n","\n").replace('\r','\n');
        if(value.isBlank() || value.codePointCount(0,value.length())>2000 || value.codePoints().anyMatch(c->(Character.isISOControl(c)&&c!='\n') || c>=0xd800&&c<=0xdfff))throw new ApiError(400,"invalid_message");return value;
    }
    private Ticket require(UUID owner,UUID id,boolean admin) {
        requireOpen();
        Ticket ticket=tickets.get(id.toString());if(ticket==null || !admin && (owner==null || !ticket.player_uuid.equals(owner.toString())))throw new ApiError(404,"not_found");return ticket;
    }
    private static Ticket copy(Ticket ticket){return Data.JSON.fromJson(Data.JSON.toJson(ticket),Ticket.class);}
    private static void revision(Ticket ticket,long expected){if(ticket.revision!=expected)throw new ApiError(409,"stale_revision");}
    private static Message message(String author,String text,String at){Message message=new Message();message.author=author;message.message=text;message.created_at=at;return message;}
    private void commit(Ticket ticket) {
        requireOpen();
        Map<String,Ticket> next=new LinkedHashMap<>(tickets);next.put(ticket.id,ticket);
        String encoded=Data.JSON.toJson(next.values());
        if(next.size()>MAX_TICKETS || encoded.getBytes(StandardCharsets.UTF_8).length>MAX_BYTES)throw new ApiError(503,"support_capacity");
        requireOpen();try {AtomicFiles.write(file,encoded);tickets=next;}catch(Exception e){throw new ApiError(503,"storage_unavailable");}
    }
    private void notify(Ticket ticket,boolean admins){if(closed||!enabled.getAsBoolean())return;try {notifier.accept(new Notice(UUID.fromString(ticket.player_uuid),UUID.fromString(ticket.id),ticket.priority,admins));}catch(RuntimeException ignored){ }}
    private static JsonObject view(Ticket ticket,boolean summary) {JsonObject json=Data.JSON.toJsonTree(ticket).getAsJsonObject();if(summary){json.remove("messages");json.addProperty("message_count",ticket.messages.size());}return json;}
    synchronized JsonObject create(UUID owner,String name,String priority,String message) {
        requireOpen();
        String p=priority(priority),body=text(message);if(owner==null || name==null || name.isBlank() || name.length()>64 || name.codePoints().anyMatch(Character::isISOControl))throw new ApiError(400,"invalid_request");
        if(tickets.size()>=MAX_TICKETS)throw new ApiError(503,"support_capacity");
        List<Ticket> own=tickets.values().stream().filter(t->t.player_uuid.equals(owner.toString())).toList();
        if(own.stream().filter(t->t.status.equals("open")).count()>=5)throw new ApiError(429,"support_open_limit");
        Instant now=clock.get();if(own.stream().map(t->Instant.parse(t.created_at)).max(Comparator.naturalOrder()).filter(last->last.plusSeconds(60).isAfter(now)).isPresent())throw new ApiError(429,"support_cooldown");
        Ticket ticket=new Ticket();ticket.id=UUID.randomUUID().toString();ticket.player_uuid=owner.toString();ticket.player_name=name;ticket.priority=p;ticket.status="open";ticket.revision=1;ticket.created_at=now.toString();ticket.updated_at=ticket.created_at;ticket.messages.add(message("player",body,ticket.created_at));
        commit(ticket);notify(ticket,true);return view(ticket,false);
    }
    synchronized JsonObject list(UUID owner,boolean admin) {
        requireOpen();
        JsonArray rows=new JsonArray();tickets.values().stream().filter(t->admin || owner!=null&&t.player_uuid.equals(owner.toString())).sorted(Comparator.comparing((Ticket t)->Instant.parse(t.updated_at)).reversed().thenComparing(t->t.id)).limit(100).forEach(t->rows.add(view(t,true)));
        JsonObject out=new JsonObject();out.add("tickets",rows);return out;
    }
    synchronized JsonObject get(UUID owner,UUID id,boolean admin){return view(require(owner,id,admin),false);}
    synchronized JsonObject reply(UUID owner,UUID id,long expected,String message,boolean admin) {
        Ticket ticket=copy(require(owner,id,admin));revision(ticket,expected);String body=text(message);
        if(!ticket.status.equals("open"))throw new ApiError(409,"support_closed");if(ticket.messages.size()>=50)throw new ApiError(409,"support_message_limit");
        ticket.revision++;ticket.updated_at=clock.get().toString();ticket.messages.add(message(admin?"admin":"player",body,ticket.updated_at));commit(ticket);notify(ticket,!admin);return view(ticket,false);
    }
    synchronized JsonObject status(UUID id,long expected,String status) {
        Ticket ticket=copy(require(null,id,true));revision(ticket,expected);if(status==null || !Set.of("open","closed").contains(status))throw new ApiError(400,"invalid_status");
        if(ticket.status.equals(status))return view(ticket,false);
        if(status.equals("open")&&tickets.values().stream().filter(t->t.player_uuid.equals(ticket.player_uuid)&&t.status.equals("open")).count()>=5)throw new ApiError(429,"support_open_limit");
        ticket.status=status;ticket.revision++;ticket.updated_at=clock.get().toString();commit(ticket);notify(ticket,false);return view(ticket,false);
    }
    private void requireOpen(){if(closed||!enabled.getAsBoolean())throw new ApiError(503,"module_unavailable");}
    void close(){closed=true;}
}
