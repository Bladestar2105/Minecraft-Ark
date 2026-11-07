package de.ark.accounts;

import java.util.Locale;
import java.net.URI;
import java.util.Set;
import java.util.Arrays;

record PortalInfo(String url,int announcementSeconds,boolean spawnDisplay) {
    static PortalInfo from(String url,Object seconds,Object display) {
        if(url==null||url.codePointCount(0,url.length())>512||url.codePoints().anyMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)||Character.isISOControl(c)||c=='\\'))throw new IllegalArgumentException("portal-url");
        URI uri=URI.create(url);
        if(uri.getScheme()==null||!Set.of("http","https").contains(uri.getScheme().toLowerCase(Locale.ROOT))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getRawFragment()!=null||uri.getPort()==0||uri.getPort()>65535||uri.getRawAuthority().endsWith(":"))throw new IllegalArgumentException("portal-url");
        String host=uri.getHost();if(host.endsWith("."))host=host.substring(0,host.length()-1);
        if(host.length()>253||(!host.startsWith("[")&&Arrays.stream(host.split("\\.")).anyMatch(label->label.length()>63)))throw new IllegalArgumentException("portal-url");
        int interval=300;
        if(seconds!=null) {
            if(!(seconds instanceof Integer)||((Integer)seconds!=0&&((Integer)seconds<30||(Integer)seconds>86400)))throw new IllegalArgumentException("portal-announcement-seconds");
            interval=(Integer)seconds;
        }
        if(display!=null&&!(display instanceof Boolean))throw new IllegalArgumentException("portal-spawn-display");
        return new PortalInfo(url.replaceFirst("/play/?$","/"),interval,display==null||(Boolean)display);
    }
    String announcement(Locale locale){return locale.getLanguage().equals("de")?"Spielerportal: "+url+" — Skins, Statistiken und Support. Mit /web erhältst du einen Einmalcode.":"Player portal: "+url+" — skins, statistics and support. Use /web for a one-time login code.";}
    String spawnText(){return "Spielerportal / Player portal\n"+url+"\nSkins · Statistiken / Statistics · Support\n/web → Einmalcode / one-time login code";}
}
