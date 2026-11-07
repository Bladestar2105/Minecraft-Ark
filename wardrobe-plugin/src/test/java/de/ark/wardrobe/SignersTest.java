package de.ark.wardrobe;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class SignersTest {
    public static void main(String[] args) throws Exception {
        var keyPair=KeyPairGenerator.getInstance("RSA");keyPair.initialize(2048);var keys=keyPair.generateKeyPair();
        String texture="{\"textures\":{\"SKIN\":{\"metadata\":{\"model\":\"slim\"},\"url\":\"http://textures.minecraft.net/texture/"+"a".repeat(64)+"\"}}}";
        String value=Base64.getEncoder().encodeToString(texture.getBytes(StandardCharsets.UTF_8));
        var signing=Signature.getInstance("SHA1withRSA");signing.initSign(keys.getPrivate());signing.update(value.getBytes(StandardCharsets.UTF_8));String signature=Base64.getEncoder().encodeToString(signing.sign());
        String property="{\"value\":\""+value+"\",\"signature\":\""+signature+"\"}";
        AtomicInteger mode=new AtomicInteger();List<String> paths=new ArrayList<>();
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath();paths.add(path);String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);int status=200;String out="{}";
            if(mode.get()==5){exchange.sendResponseHeaders(200,100);exchange.getResponseBody().flush();try {Thread.sleep(10000);}catch(InterruptedException e){Thread.currentThread().interrupt();}exchange.close();return;}
            if(mode.get()==3 && path.equals("/token"))try {Thread.sleep(700);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            if(mode.get()==4 && path.equals("/skins")){status=401;out="{\"error\":\"DO-NOT-LEAK\"}";}
            else if(mode.get()==1) {status=429;out="{\"access_token\":\"DO-NOT-LEAK\"}";}
            else if(mode.get()==2 && path.equals("/token")) {status=400;out="{\"error\":\"invalid_grant\",\"error_description\":\"DO-NOT-LEAK\"}";}
            else switch(path) {
                case "/keys" -> out="{\"profilePropertyKeys\":[{\"publicKey\":\""+Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())+"\"}]}";
                case "/mineskin/queue" -> {WardrobeTest.check(body.contains("data:image/png;base64,") && body.contains("slim"),"PNG and model passed to generator");status=202;out="{\"success\":true,\"job\":{\"id\":\"test-job\",\"status\":\"waiting\"}}";}
                case "/mineskin/queue/test-job" -> out="{\"success\":true,\"skin\":{\"texture\":{\"data\":"+property+"}}}";
                case "/device" -> {WardrobeTest.check(body.contains("client_id=11111111-1111-1111-1111-111111111111"),"operator client ID used");out="{\"device_code\":\"PRIVATE-DEVICE\",\"user_code\":\"ABCD-EFGH\",\"verification_uri\":\"https://microsoft.com/link\",\"expires_in\":600,\"interval\":1}";}
                case "/token" -> out="{\"access_token\":\"PRIVATE-ACCESS\",\"refresh_token\":\"PRIVATE-REFRESH\",\"expires_in\":3600}";
                case "/xbl" -> out="{\"Token\":\"PRIVATE-XBL\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"123\"}]}}";
                case "/xsts" -> out="{\"Token\":\"PRIVATE-XSTS\",\"DisplayClaims\":{\"xui\":[{\"uhs\":\"123\"}]}}";
                case "/minecraft" -> out="{\"access_token\":\"PRIVATE-MC\",\"expires_in\":3600}";
                case "/profile" -> out="{\"id\":\"0123456789abcdef0123456789abcdef\",\"name\":\"Operator\"}";
                case "/skins" -> {WardrobeTest.check(body.contains("slim") && body.contains("image/png"),"Minecraft upload variant PNG");out="{\"id\":\"0123456789abcdef0123456789abcdef\",\"skins\":[{\"state\":\"ACTIVE\",\"url\":\"http://textures.minecraft.net/texture/"+"a".repeat(64)+"\"}]}";}
                case "/session/0123456789abcdef0123456789abcdef" -> out="{\"properties\":[{\"name\":\"textures\",\"value\":\""+value+"\",\"signature\":\""+signature+"\"}]}";
                default -> {status=404;out="{}";}
            }
            byte[] bytes=out.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
        });server.start();
        try {
            URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            var signers=new Signers(Files.createTempDirectory("signers").resolve("private.json"),Signers.Endpoints.fixture(base));
            WardrobeTest.denied(503,()->signers.signUnchecked(new byte[]{1},"slim"));
            var settings=new JsonObject();settings.addProperty("provider","mineskin");settings.addProperty("mineskin_api_key","PRIVATE-KEY");signers.update(settings);
            WardrobeTest.check(!signers.status().toString().contains("PRIVATE"),"settings redact all tokens");
            WardrobeTest.check(signers.signUnchecked(Png.canonical(WardrobeTest.image(64,0xff000000)),"slim").value().equals(value),"Mojang signature verified");
            mode.set(1);WardrobeTest.denied(429,()->signers.signUnchecked(new byte[]{1},"slim"));mode.set(0);
            settings=new JsonObject();settings.addProperty("provider","selfhosted");settings.addProperty("client_id","11111111-1111-1111-1111-111111111111");signers.update(settings);
            var connect=signers.connect();WardrobeTest.check(!connect.toString().contains("PRIVATE"),"connect only public device code");
            Thread.sleep(1100);mode.set(3);long started=System.nanoTime();signers.status();WardrobeTest.check(System.nanoTime()-started<200_000_000,"OAuth polling never blocks admin status response");
            for(int i=0;i<30&&!signers.status().getAsJsonObject("account").get("connected").getAsBoolean();i++)Thread.sleep(100);
            mode.set(0);WardrobeTest.check(signers.status().getAsJsonObject("account").get("connected").getAsBoolean(),"fixture device OAuth Xbox Minecraft complete");
            signers.signUnchecked(Png.canonical(WardrobeTest.image(64,0xff000000)),"slim");
            WardrobeTest.check(paths.contains("/xbl")&&paths.contains("/xsts")&&paths.contains("/minecraft")&&paths.contains("/skins"),"actual complete HTTP contract");
            mode.set(4);WardrobeTest.denied(401,()->signers.signUnchecked(new byte[]{1},"slim"));
            WardrobeTest.check(!signers.status().getAsJsonObject("account").get("connected").getAsBoolean(),"revoked Minecraft credentials clear connected state");mode.set(0);
            signers.disconnect();WardrobeTest.check(!signers.status().getAsJsonObject("account").get("connected").getAsBoolean(),"disconnect clears credentials");
            mode.set(2);signers.connect();Thread.sleep(1100);signers.status();Thread.sleep(200);var failed=signers.status();WardrobeTest.check(!failed.toString().contains("DO-NOT-LEAK") && !failed.getAsJsonObject("account").get("connected").getAsBoolean(),"revoked OAuth fails closed redacted");
            var bounded=new Signers(Files.createTempDirectory("signer-deadline").resolve("private.json"),Signers.Endpoints.fixture(base));
            settings=new JsonObject();settings.addProperty("provider","mineskin");settings.addProperty("mineskin_api_key","PRIVATE-KEY");bounded.update(settings);mode.set(5);
            var stalled=java.util.concurrent.CompletableFuture.runAsync(()->WardrobeTest.denied(503,()->bounded.signUnchecked(new byte[]{1},"slim")));
            try {stalled.get(9,java.util.concurrent.TimeUnit.SECONDS);}catch(java.util.concurrent.TimeoutException e){stalled.cancel(true);throw new AssertionError("entire provider response body must have a deadline");}
        } finally {server.stop(0);}
        System.out.println("SignersTest: "+WardrobeTest.checks+" checks passed (local fixture only)");
    }
}
