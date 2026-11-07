package de.ark.accounts;

import com.google.gson.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class Accounts {
    record Identity(UUID uuid,String name) { }
    record Authenticated(Identity identity,long version) { }
    interface Authority {boolean configured(String name);boolean check(String name,String password);void change(String name,String password);default Identity identity(String name){return null;}}
    static final class Account {UUID uuid;String name,password;List<String> recovery=new ArrayList<>();long version;}
    private final Path file;
    private final Sessions sessions;
    private final Authority authority;
    private final java.util.function.Consumer<Identity> audit;
    private volatile Map<UUID,Account> accounts=new HashMap<>();
    Accounts(Path file,Sessions sessions,Authority authority) {
        this(file,sessions,authority,identity->{});
    }
    Accounts(Path file,Sessions sessions,Authority authority,java.util.function.Consumer<Identity> audit) {
        this.file=file;this.sessions=sessions;this.authority=authority;this.audit=audit;
        try {
            if(Files.exists(file)) {
                if(Files.size(file)>8_000_000)throw new IllegalArgumentException();
                for(var element:JsonParser.parseString(Files.readString(file)).getAsJsonArray()) {
                    Account a=Sessions.JSON.fromJson(element,Account.class);
                    if(a==null || a.uuid==null || a.name==null || a.recovery==null || a.recovery.size()>10 || a.recovery.stream().anyMatch(c->!c.matches("[0-9a-f]{64}")))throw new IllegalArgumentException();
                    accounts.put(a.uuid,a);
                }
            }
        } catch(Exception e) {throw new ApiError(503,"storage_unavailable");}
        sessions.versions(this::version);
    }
    long version(UUID uuid) {Account a=accounts.get(uuid);return a==null?0:a.version;}
    synchronized void observe(UUID uuid,String name) {
        Account a=accounts.get(uuid);
        if(a!=null && a.name.equals(name))return;
        Account next=a==null?new Account():copy(a);next.uuid=uuid;next.name=name;save(next);
    }
    private Account owner(UUID uuid) {Account a=accounts.get(uuid);if(a==null)throw new ApiError(401,"unauthorized");return a;}
    private Account named(String name) {
        if(name==null || name.length()>64)throw new ApiError(401,"invalid_credentials");
        List<Account> matches=accounts.values().stream().filter(a->a.name.equalsIgnoreCase(name)).toList();
        if(matches.size()!=1)throw new ApiError(401,"invalid_credentials");return matches.getFirst();
    }
    private boolean configured(Account a) {return authority==null?a.password!=null:authority.configured(a.name);}
    private boolean check(Account a,String password) {
        if(password==null || password.length()>1024)return false;
        return authority==null?verify(password,a.password):authority.check(a.name,password);
    }
    synchronized JsonObject login(String name,String password) {
        Identity identity=authenticate(name,password).identity();return sessions.issue(identity.uuid(),identity.name(),false);
    }
    synchronized Authenticated authenticate(String name,String password) {
        if(name==null || name.isBlank() || name.length()>64 || name.codePoints().anyMatch(Character::isISOControl) || password==null || password.length()>1024)throw new ApiError(401,"invalid_credentials");
        if(authority!=null) {
            if(!authority.check(name,password))throw new ApiError(401,"invalid_credentials");
            Identity identity=authority.identity(name);
            if(identity==null || identity.uuid()==null)throw new ApiError(403,"game_join_required");
            observe(identity.uuid(),identity.name());Account a=owner(identity.uuid());return new Authenticated(new Identity(a.uuid,a.name),a.version);
        }
        Account a=named(name);if(!check(a,password))throw new ApiError(401,"invalid_credentials");return new Authenticated(new Identity(a.uuid,a.name),a.version);
    }
    synchronized JsonObject status(UUID uuid) {
        Account a=owner(uuid);JsonObject out=new JsonObject();out.addProperty("password_configured",configured(a));out.addProperty("recovery_enabled",!a.recovery.isEmpty());out.addProperty("recovery_codes_remaining",a.recovery.size());return out;
    }
    static void validate(String password,String confirmation) {
        if(password==null || !password.equals(confirmation) || password.codePointCount(0,password.length())<12 || password.codePointCount(0,password.length())>64 || password.codePoints().anyMatch(Character::isISOControl))throw new ApiError(400,"invalid_password");
    }
    synchronized void password(UUID uuid,String current,String password,String confirmation,boolean freshGameProof) {
        Account a=owner(uuid);validate(password,confirmation);
        if(configured(a)) {if(!(freshGameProof && "".equals(current)) && !check(a,current))throw new ApiError(401,"invalid_credentials");}
        else if(!freshGameProof)throw new ApiError(403,"game_proof_required");
        change(a,password,copy(a));
    }
    synchronized void passwordFromGame(UUID uuid,String current,String password,String confirmation) {
        password(uuid,current,password,confirmation,true);
    }
    synchronized Identity adminPassword(String name,String password) {
        validate(password,password);Account account;
        if(name==null || name.isBlank() || name.length()>64 || name.codePoints().anyMatch(Character::isISOControl))throw new ApiError(404,"account_not_found");
        if(authority!=null) {
            if(!authority.configured(name))throw new ApiError(404,"account_not_found");
            Identity identity=authority.identity(name);if(identity==null || identity.uuid()==null)throw new ApiError(403,"game_join_required");
            observe(identity.uuid(),identity.name());account=owner(identity.uuid());
        } else {
            try {account=named(name);}catch(ApiError e){if(e.status==401)throw new ApiError(404,"account_not_found");throw e;}
        }
        change(account,password,copy(account));Identity identity=new Identity(account.uuid,account.name);audit.accept(identity);return identity;
    }
    synchronized JsonObject list(){JsonArray rows=new JsonArray();accounts.values().stream().sorted(Comparator.comparing(a->a.name.toLowerCase(Locale.ROOT))).limit(1000).forEach(a->{JsonObject row=new JsonObject();row.addProperty("uuid",a.uuid.toString());row.addProperty("name",a.name);rows.add(row);});JsonObject out=new JsonObject();out.add("accounts",rows);return out;}
    private void change(Account old,String password,Account next) {
        next.version++;
        if(authority==null)next.password=hashPassword(password);
        else {
            // Persist invalidation before the external asynchronous authority changes the password.
            save(next);sessions.revokeAll(old.uuid);authority.change(old.name,password);return;
        }
        save(next);sessions.revokeAll(old.uuid);
    }
    synchronized JsonObject recovery(UUID uuid,String current,String recoveryCode,boolean disable) {
        Account a=owner(uuid);if(!check(a,current))throw new ApiError(401,"invalid_credentials");
        if(!a.recovery.isEmpty() && (recoveryCode==null || !a.recovery.contains(Sessions.hash(recoveryCode.strip()))))throw new ApiError(401,"invalid_recovery_code");
        Account next=copy(a);next.recovery.clear();JsonArray codes=new JsonArray();
        if(!disable)for(int i=0;i<10;i++){String code=Sessions.random(18);codes.add(code);next.recovery.add(Sessions.hash(code));}
        save(next);JsonObject out=new JsonObject();if(disable)out.addProperty("ok",true);else out.add("codes",codes);return out;
    }
    synchronized void recover(String name,String code,String password,String confirmation) {
        validate(password,confirmation);Account a;
        try {a=named(name);}catch(ApiError e){if(e.status==401)throw new ApiError(401,"invalid_recovery_code");throw e;}
        if(code==null || code.length()>128 || !a.recovery.contains(Sessions.hash(code.strip())))throw new ApiError(401,"invalid_recovery_code");
        Account next=copy(a);next.recovery.remove(Sessions.hash(code.strip()));change(a,password,next);
    }
    synchronized void revokeName(String name) {for(Account a:new ArrayList<>(accounts.values()))if(a.name.equalsIgnoreCase(name)){Account next=copy(a);next.version++;save(next);sessions.revokeAll(a.uuid);}}
    synchronized void revoke(UUID owner) {Account a=accounts.get(owner);if(a!=null){Account next=copy(a);next.version++;save(next);}sessions.revokeAll(owner);}
    private Account copy(Account a) {return Sessions.JSON.fromJson(Sessions.JSON.toJson(a),Account.class);}
    private void save(Account account) {
        var next=new HashMap<>(accounts);next.put(account.uuid,account);
        try {AtomicFiles.write(file,Sessions.JSON.toJson(next.values()));accounts=next;}
        catch(Exception e) {throw new ApiError(503,"storage_unavailable");}
    }
    private static String hashPassword(String password) {String salt=Sessions.random(24);return salt+":"+Base64.getEncoder().encodeToString(derive(password,salt));}
    private static byte[] derive(String password,String salt) {
        PBEKeySpec spec=new PBEKeySpec(password.toCharArray(),salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),600000,256);
        try {return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();}
        catch(Exception e) {throw new IllegalStateException(e);}finally {spec.clearPassword();}
    }
    private static boolean verify(String password,String stored) {
        if(stored==null)return false;
        try {String[] parts=stored.split(":",2);return MessageDigest.isEqual(derive(password,parts[0]),Base64.getDecoder().decode(parts[1]));}
        catch(IllegalArgumentException e) {throw new ApiError(503,"storage_unavailable");}
    }
}
