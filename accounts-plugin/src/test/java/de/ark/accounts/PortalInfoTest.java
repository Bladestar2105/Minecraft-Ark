package de.ark.accounts;

import java.util.Locale;

public final class PortalInfoTest {
    private static void invalid(String url,Object seconds,Object display) {
        try {PortalInfo.from(url,seconds,display);throw new AssertionError("accepted invalid portal settings");}
        catch(IllegalArgumentException expected){TestSupport.check(true,"invalid portal setting rejected");}
    }
    public static void main(String[] args) {
        var defaults=PortalInfo.from("https://mc.example.org/play/",null,null);
        TestSupport.check(defaults.url().equals("https://mc.example.org/"),"legacy URL links to the shared main page");
        TestSupport.check(defaults.announcementSeconds()==300&&defaults.spawnDisplay(),"missing notice settings retain safe defaults");
        for(String url:new String[]{"https://mc.example.org/","http://localhost:8090/","HTTPS://mc.example.org/path?a=1","http://[::1]:8090/","https://mc.example.org/ü"})
            TestSupport.check(PortalInfo.from(url,30,true).url().equals(url),"absolute public URL accepted");
        for(String url:new String[]{"","/play/","ftp://mc.example.org/","https://","https://user:secret@mc.example.org/","https://mc.example.org/#","https://mc.example.org/#secret","https://mc.example.org/\nattack","https://mc.example.org/ bad","https://mc.example.org/\u007f","https://mc.example.org/\\attack","https://bad_host/","https://-bad.example/","https://mc.example.org:","https://mc.example.org:0/","https://mc.example.org:65536/","https://mc.example.org/"+"x".repeat(512)})invalid(url,300,true);
        for(Object interval:new Object[]{-1,1,29,86401,"300",30.5,true})invalid("https://mc.example.org/",interval,true);
        for(int interval:new int[]{0,30,300,86400})TestSupport.check(PortalInfo.from("https://mc.example.org/",interval,false).announcementSeconds()==interval,"valid notice interval accepted including off");
        for(Object display:new Object[]{"true",1})invalid("https://mc.example.org/",300,display);
        String de=defaults.announcement(Locale.GERMANY),en=defaults.announcement(Locale.US);
        TestSupport.check(de.contains("Spielerportal")&&de.contains(defaults.url())&&de.contains("/web")&&de.contains("Skins"),"German notice includes portal URL and discovery command");
        TestSupport.check(en.contains("Player portal")&&en.contains(defaults.url())&&en.contains("/web")&&en.contains("skins"),"English notice includes portal URL and discovery command");
        TestSupport.check(defaults.announcement(Locale.FRANCE).equals(en),"other locales use English");
        TestSupport.check(defaults.spawnText().contains(defaults.url())&&defaults.spawnText().contains("Spielerportal")&&defaults.spawnText().contains("Player portal"),"shared spawn display includes both languages and same URL");
        System.out.println("PortalInfoTest: "+TestSupport.checks+" checks passed");
    }
}
