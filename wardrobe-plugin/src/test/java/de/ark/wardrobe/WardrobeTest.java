package de.ark.wardrobe;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

public final class WardrobeTest {
    static int checks;
    static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    static void denied(int status, Runnable action) {
        try { action.run(); throw new AssertionError("expected " + status); }
        catch (ApiError e) { check(e.status == status, "wrong status " + e.status); }
    }
    static JsonObject body(String name, String model, String png) {
        JsonObject o = new JsonObject(); o.addProperty("name", name); o.addProperty("model", model); o.addProperty("png", png); return o;
    }
    static String image(int size, int color) throws Exception {
        BufferedImage image = new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,color); var bytes = new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes);
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    static long rev(JsonObject o) { return o.get("revision").getAsLong(); }
    static UUID id(JsonObject o) { return UUID.fromString(o.get("id").getAsString()); }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("ark-wardrobe-test");
        String png = image(64,0xff123456), other = image(64,0xffabcdef);
        check(Arrays.equals(Png.canonical(png),Png.canonical(png)),"canonical PNG");
        denied(400,()->Png.canonical("a"));
        String wrong = image(128,0); denied(400,()->Png.canonical(wrong));
        denied(400,()->Png.canonical(Base64.getEncoder().encodeToString(new byte[131073])));
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        var gateway = new MemoryGateway(); var blocked = new CountDownLatch(1); var entered = new CountDownLatch(1);
        var signerCalls = new java.util.concurrent.atomic.AtomicInteger();
        WardrobeStore.Signer signer = (bytes,model)-> { signerCalls.incrementAndGet(); entered.countDown(); blocked.await(); return new WardrobeStore.Property("signed-value","signed-signature"); };
        try (WardrobeStore store = new WardrobeStore(dir.resolve("library"),signer,gateway)) {
            JsonObject a = store.create(owner,body("First","classic",png));
            denied(400,()->store.create(owner,body("first","classic",png)));
            denied(400,()->store.create(owner,body(" ","classic",png)));
            denied(400,()->store.create(owner,body("x","invalid",png)));
            JsonObject typed=body("Typed","classic",png);typed.addProperty("name",12);denied(400,()->store.create(owner,typed));
            denied(404,()->store.update(stranger,id(a),body("Stolen","classic",png),rev(a)));
            denied(409,()->store.update(owner,id(a),body("First","classic",png),999));
            JsonObject b = store.copy(owner,id(a),"Second",rev(a));
            store.activate(owner,id(a),rev(a)); check(entered.await(2,TimeUnit.SECONDS),"signing queued");
            store.update(owner,id(a),body("Edited","slim",other),rev(a));
            store.activate(owner,id(b),rev(b)); blocked.countDown(); store.awaitIdle();
            check(store.list(owner).toString().contains("Second"),"library readable");
            check(gateway.currentKey(owner).equals(WardrobeStore.key(id(b),rev(b))),"late signing cannot overwrite new selection");
            check(signerCalls.get()==1,"same PNG and model use signed cache");
            denied(409,()->store.delete(owner,id(b),rev(b)));
            gateway.keys.remove(owner);
            check(store.list(owner).getAsJsonArray("skins").asList().stream().noneMatch(e->e.getAsJsonObject().get("selected").getAsBoolean()),"external SR selection reconciled");
            store.delete(owner,id(b),rev(b));
            for(int i=0;i<19;i++) store.create(owner,body("Skin "+i,"classic",png));
            denied(400,()->store.create(owner,body("Overflow","classic",png)));
        }
        try (WardrobeStore store = new WardrobeStore(dir.resolve("library"),signer,gateway)) {
            check(store.list(owner).getAsJsonArray("skins").size()==20,"atomic manifest survives restart");
            Path manifest=dir.resolve("library").resolve(owner.toString()).resolve("catalog.json");
            Files.writeString(manifest,"broken");
        }
        try (WardrobeStore store = new WardrobeStore(dir.resolve("library"),signer,gateway)) {
            denied(503,()->store.list(owner));
            check(Files.readString(dir.resolve("library").resolve(owner.toString()).resolve("catalog.json")).equals("broken"),"corrupt manifest never replaced with empty data");
        }
        check(!new ApiError(502,"signing_failed").toString().contains("signed-value"),"safe error messages");
        CountDownLatch renameEntered=new CountDownLatch(1),renameRelease=new CountDownLatch(1);
        try(WardrobeStore store=new WardrobeStore(dir.resolve("rename"),(bytes,model)->{renameEntered.countDown();renameRelease.await();return new WardrobeStore.Property("signed-value","signed-signature");},new MemoryGateway())) {
            JsonObject original=store.create(owner,body("Original","classic",png));store.activate(owner,id(original),rev(original));check(renameEntered.await(2,TimeUnit.SECONDS),"rename signing started");
            store.update(owner,id(original),body("Renamed","classic",png),rev(original));renameRelease.countDown();store.awaitIdle();
            check(!store.list(owner).getAsJsonArray("skins").get(0).getAsJsonObject().get("status").getAsString().equals("preparing"),"rename cancels old revision without permanent preparing state");
        }
        CountDownLatch lateEntered=new CountDownLatch(1),lateRelease=new CountDownLatch(1);var worker=new java.util.concurrent.atomic.AtomicReference<Thread>();var lateGateway=new MemoryGateway();
        WardrobeStore stopped=new WardrobeStore(dir.resolve("stopped"),(bytes,model)->{worker.set(Thread.currentThread());lateEntered.countDown();try {lateRelease.await();}catch(InterruptedException ignored){}return new WardrobeStore.Property("late-value","late-signature");},lateGateway);
        JsonObject pending=stopped.create(owner,body("Pending","classic",png));stopped.activate(owner,id(pending),rev(pending));check(lateEntered.await(2,TimeUnit.SECONDS),"late signer entered");
        Path stoppedFile=dir.resolve("stopped").resolve(owner.toString()).resolve("catalog.json");String before=Files.readString(stoppedFile);stopped.close();lateRelease.countDown();worker.get().join(2000);
        check(lateGateway.currentKey(owner)==null,"disabled module ignores late signer selection");check(Files.readString(stoppedFile).equals(before),"disabled module ignores late signer state writes");
        System.out.println("WardrobeTest: " + checks + " checks passed");
    }
    static final class MemoryGateway implements WardrobeStore.Gateway {
        final Map<UUID,String> keys = new ConcurrentHashMap<>();
        public String currentKey(UUID owner) { return keys.get(owner); }
        public boolean select(UUID owner,String key,WardrobeStore.Property property) { keys.put(owner,key); return false; }
    }
}
