package de.ark.accounts;

import com.google.gson.*;

final class Protocol {
    static String string(JsonObject body,String key){try {JsonElement value=body.get(key);if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString()||value.getAsString().length()>65536)throw new IllegalArgumentException();return value.getAsString();}catch(Exception e){throw new ApiError(400,"invalid_request");}}
}
