package de.ark.panel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.suggestion.Suggestions;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class BridgeProtocol {
    static final Gson JSON = new Gson();
    record Input(String command, int cursor, int offset) { }

    static String decode(String encoded) {
        if (encoded.length() > 2004 || !encoded.matches("[A-Za-z0-9_-]*={0,2}")) throw new IllegalArgumentException("Invalid base64url");
        byte[] bytes = Base64.getUrlDecoder().decode(encoded);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded.replace("=", ""))) throw new IllegalArgumentException("Noncanonical base64url");
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.length() > 500 || text.indexOf('\0') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) throw new IllegalArgumentException("Input exceeds500 UTF16 units or contains a control character");
            return text;
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid UTF8");
        }
    }

    static Input input(int cursor, String encoded) {
        String text = decode(encoded);
        if (cursor < 0 || cursor > text.length() || (cursor > 0 && cursor < text.length() && Character.isHighSurrogate(text.charAt(cursor - 1)) && Character.isLowSurrogate(text.charAt(cursor)))) throw new IllegalArgumentException("Invalid UTF16 cursor");
        int offset = text.startsWith("/") && cursor > 0 ? 1 : 0;
        return new Input(offset == 1 ? text.substring(1) : text, cursor - offset, offset);
    }

    static String error(String code, String message) {
        var result = new JsonObject();
        result.addProperty("available", true);
        result.addProperty("ready", true);
        result.addProperty("error", code);
        result.addProperty("message", message);
        return JSON.toJson(result);
    }

    static String suggestions(Suggestions suggestions, Input input) {
        var result = new JsonObject();
        result.addProperty("available", true);
        result.addProperty("ready", true);
        result.addProperty("start", suggestions.getRange().getStart() + input.offset());
        result.addProperty("end", suggestions.getRange().getEnd() + input.offset());
        var list = new JsonArray();
        for (var suggestion : suggestions.getList()) {
            String text = suggestion.getText();
            if (list.size() == 50) break;
            if (text.length() > 500 || text.indexOf('\0') >= 0 || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) continue;
            var item = new JsonObject();
            item.addProperty("text", text);
            if (suggestion.getTooltip() != null) {
                String tooltip = suggestion.getTooltip().getString();
                item.addProperty("tooltip", tooltip.substring(0, Math.min(tooltip.length(), 300)));
            }
            list.add(item);
        }
        result.add("suggestions", list);
        return JSON.toJson(result);
    }
}
