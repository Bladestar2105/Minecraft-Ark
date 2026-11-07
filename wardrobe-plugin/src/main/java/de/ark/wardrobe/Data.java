package de.ark.wardrobe;

import com.google.gson.Gson;
import java.security.*;
import java.util.*;

final class Data {
    static final Gson JSON=new Gson();
    static String random(int count){byte[] bytes=new byte[count];new SecureRandom().nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String hash(String value){return hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
