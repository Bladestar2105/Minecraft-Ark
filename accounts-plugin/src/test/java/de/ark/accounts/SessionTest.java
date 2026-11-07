package de.ark.accounts;
import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static de.ark.accounts.TestSupport.*;
public final class SessionTest { public static void main(String[] args) throws Exception {
Path dir=Files.createTempDirectory("session-test");
        final Instant[] time = {Instant.parse("2026-11-07T00:00:00Z")};
        Sessions sessions = new Sessions(dir.resolve("sessions.json"),()->time[0]);
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        String code = sessions.pair(owner,"Alice");
        JsonObject login = sessions.redeem(code);
        String token = login.get("token").getAsString();
        check(sessions.require(token).uuid().equals(owner),"canonical owner");
        denied(401,()->sessions.redeem(code));
        check(!Files.readString(dir.resolve("sessions.json")).contains(token),"raw tokens never persisted");
        check(new Sessions(dir.resolve("sessions.json"),()->time[0]).require(token).uuid().equals(owner),"sessions survive restart");
        String delayed=sessions.pair(stranger,"Delayed");time[0]=time[0].plusSeconds(299);String delayedToken=sessions.redeem(delayed).get("token").getAsString();time[0]=time[0].plusSeconds(2);
        check(sessions.require(delayedToken).gameProofUntil()<time[0].getEpochSecond(),"late redemption does not renew original game proof");
        check(new Sessions(dir.resolve("sessions.json"),()->time[0]).require(delayedToken).gameProofUntil()<time[0].getEpochSecond(),"restart does not renew original game proof");
        String expired = sessions.pair(owner,"Alice"); time[0]=time[0].plusSeconds(301);
        denied(401,()->sessions.redeem(expired));
        sessions.revokeAll(owner); denied(401,()->sessions.require(token));
        String revoked = sessions.redeem(sessions.pair(owner,"Alice")).get("token").getAsString();
        sessions.logout(revoked); denied(401,()->sessions.require(revoked));
        String old = sessions.redeem(sessions.pair(owner,"Alice")).get("token").getAsString();
        time[0]=time[0].plus(Duration.ofDays(31)); denied(401,()->sessions.require(old));

System.out.println("SessionTest: "+checks+" checks passed"); }}
