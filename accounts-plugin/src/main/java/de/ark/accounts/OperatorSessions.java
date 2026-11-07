package de.ark.accounts;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.time.*;
import java.util.UUID;
import java.util.function.*;

final class OperatorSessions {
    private final Sessions sessions;
    private final Accounts accounts;
    private final Predicate<UUID> isOperator;
    OperatorSessions(Path file,Supplier<Instant> clock,Accounts accounts,Predicate<UUID> isOperator) {
        this.sessions=new Sessions(file,clock,Duration.ofHours(8));this.accounts=accounts;this.isOperator=isOperator;sessions.versions(accounts::version);
    }
    private void requireOperator(UUID uuid){if(!isOperator.test(uuid))throw new ApiError(403,"operator_required");}
    String pair(UUID owner,String name){requireOperator(owner);return sessions.pair(owner,name);}
    JsonObject loginCode(String code) {
        JsonObject result=sessions.redeem(code);String token=result.get("token").getAsString();
        try {check(token);return result;}catch(RuntimeException error){sessions.forget(token);throw error;}
    }
    JsonObject loginCredentials(String name,String password) {
        Accounts.Authenticated verified=accounts.authenticate(name,password);Accounts.Identity identity=verified.identity();requireOperator(identity.uuid());
        if(accounts.version(identity.uuid())!=verified.version())throw new ApiError(401,"unauthorized");
        JsonObject result=sessions.issue(identity.uuid(),identity.name(),false);String token=result.get("token").getAsString();
        try {if(accounts.version(identity.uuid())!=verified.version())throw new ApiError(401,"unauthorized");check(token);return result;}
        catch(RuntimeException error){sessions.forget(token);throw error;}
    }
    JsonObject check(String token){Sessions.Session session=sessions.require(token);try {requireOperator(session.uuid());}catch(ApiError error){if(error.status==403)sessions.forget(token);throw error;}if(accounts.version(session.uuid())!=session.version())throw new ApiError(401,"unauthorized");return Sessions.view(session);}
    void logout(String token){sessions.forget(token);}
}
