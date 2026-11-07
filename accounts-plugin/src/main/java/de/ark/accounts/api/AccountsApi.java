package de.ark.accounts.api;

import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Function;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Shared account authority and the two fixed optional module registrations. */
public interface AccountsApi {
    record Identity(UUID uuid,String name) {public Identity {Objects.requireNonNull(uuid);Objects.requireNonNull(name);}}
    enum Role {PLAYER,SERVICE_ADMIN}
    record Request(String method,String path,Role role,Identity identity,JsonObject body) {
        public Request {
            Objects.requireNonNull(method);Objects.requireNonNull(path);Objects.requireNonNull(role);Objects.requireNonNull(body);
            if((role==Role.PLAYER)!=(identity!=null))throw new IllegalArgumentException("Role identity mismatch");body=body.deepCopy();
        }
    }
    record Response(int status,JsonObject body) {public Response {if(status<200||status>599)throw new IllegalArgumentException("Invalid status");body=Objects.requireNonNull(body).deepCopy();}}
    class Failure extends RuntimeException {
        public final int status;public final String code;
        public Failure(int status,String code){super(code);if(status<400||status>599||code==null||!code.matches("[a-z][a-z0-9_]{0,63}"))throw new IllegalArgumentException("Invalid safe error");this.status=status;this.code=code;}
    }
    @FunctionalInterface interface Handler {Response handle(Request request);}
    interface Registration extends AutoCloseable {
        boolean open();
        default void requireOpen(){if(!open())throw new Failure(503,"module_unavailable");}
        @Override void close();
    }
    /** Verify a currently connected game player; no name-derived identity or permission elevation. */
    Identity identity(Player player);
    /** Module IDs are exactly wardrobe or support; core assigns their player/admin path prefixes. */
    Registration register(Plugin owner,String module,Handler handler,Function<Identity,UUID> selectedSkin);
}
