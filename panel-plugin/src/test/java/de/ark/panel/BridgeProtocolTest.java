package de.ark.panel;

import com.google.gson.JsonParser;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

public final class BridgeProtocolTest {
    static String b64(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    public static void main(String[] args) {
        var input = BridgeProtocol.input(9, b64("/minecraf" + "t"));
        check(input.command().equals("minecraft") && input.cursor() == 8 && input.offset() == 1, "leading slash must preserve browser offset");
        var unicode = BridgeProtocol.input(6, b64("say 😀"));
        check(unicode.cursor() == 6, "cursor is a JavaScript UTF16 offset, not a codepoint count");
        check(BridgeProtocol.input(0, "").command().isEmpty(), "empty completion input accepted");
        check(BridgeProtocol.input(500, b64("x".repeat(500))).command().length() == 500, "500 units allowed");
        rejects(() -> BridgeProtocol.input(501, b64("x".repeat(501))));
        rejects(() -> BridgeProtocol.input(-1, b64("abc")));
        rejects(() -> BridgeProtocol.input(4, b64("abc")));
        rejects(() -> BridgeProtocol.input(5, b64("say 😀")));
        for (String value : List.of("a\n", "a\r", "a\u0000")) rejects(() -> BridgeProtocol.input(0, b64(value)));
        for (String encoded : List.of("%%%", "a", "_w", "YQ===", "YR")) rejects(() -> BridgeProtocol.input(0, encoded));
        var suggestions = new Suggestions(StringRange.between(0, 8), List.of(new Suggestion(StringRange.between(0, 8), "minecraft:help")));
        var json = JsonParser.parseString(BridgeProtocol.suggestions(suggestions, input)).getAsJsonObject();
        check(json.get("start").getAsInt() == 1 && json.get("end").getAsInt() == 9, "replacement range maps back across slash");
        check(json.getAsJsonArray("suggestions").get(0).getAsJsonObject().get("text").getAsString().equals("minecraft:help"), "suggestion JSON preserves text");
        var escaped = new Suggestions(StringRange.at(0), List.of(new Suggestion(StringRange.at(0), "\"\\☃")));
        check(JsonParser.parseString(BridgeProtocol.suggestions(escaped, BridgeProtocol.input(0, ""))).getAsJsonObject().getAsJsonArray("suggestions").get(0).getAsJsonObject().get("text").getAsString().equals("\"\\☃"), "JSON escapes untrusted suggestion text");
        var many = new ArrayList<Suggestion>();
        many.add(new Suggestion(StringRange.at(0), "x".repeat(501)));
        many.add(new Suggestion(StringRange.at(0), "injected\ncommand"));
        for (int i = 0; i < 60; i++) many.add(new Suggestion(StringRange.at(0), "choice" + i));
        var limited = JsonParser.parseString(BridgeProtocol.suggestions(new Suggestions(StringRange.at(0), many), BridgeProtocol.input(0, ""))).getAsJsonObject().getAsJsonArray("suggestions");
        check(limited.size() == 50 && limited.get(0).getAsJsonObject().get("text").getAsString().equals("choice0"), "oversized/control suggestions excluded and output capped at50");
        var clock = new AtomicLong();
        var jobs = new PendingResults(clock::get);
        var future = new CompletableFuture<String>();
        var id = jobs.add(future);
        check(jobs.get(id).contains("\"ready\":false"), "pending completion never blocks");
        future.complete("finished");
        check(jobs.get(id).equals("finished"), "completed future is retrieved");
        clock.set(10_000_000_001L);
        check(jobs.get(id) == null, "jobs expire after ten seconds");
        for (int i = 0; i < 100; i++) check(jobs.add(new CompletableFuture<>()) != null, "first100 jobs allowed");
        check(jobs.add(new CompletableFuture<>()) == null, "pending cache is bounded");
        check(jobs.full(), "capacity is checked before asking a new provider");
        System.out.println("PASS boundary, JSON, UTF16 and pending cache checks");
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    static void rejects(Runnable action) {
        try { action.run(); throw new AssertionError("Expected invalid request rejection"); }
        catch (IllegalArgumentException expected) { }
    }
}
