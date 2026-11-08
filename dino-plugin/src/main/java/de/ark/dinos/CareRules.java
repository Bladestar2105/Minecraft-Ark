package de.ark.dinos;

import java.util.List;

final class CareRules {
    static final List<String> SPECIES=List.of("trex","triceratops","stegosaurus","brachiosaurus","velociraptor","pteranodon","dilophosaurus");
    static final long FEED_COOLDOWN_MS=10_000, PROGRESS_EXPIRY_MS=300_000, LOVE_MS=60_000,
            BREED_COOLDOWN_MS=300_000, INCUBATION_MS=1_200_000, GROWTH_MS=DinoGrowth.DURATION_MS,
            GROWTH_FEED_MS=60_000, GROWTH_FEED_COOLDOWN_MS=30_000;
    enum Action { IGNORE, FEED, WAIT, LOCKED, NEED_FEEDS, READY, FINISH }
    record Progress(String actor,int feeds,long nextFeedAt,long expiresAt) { }
    record Feeding(Action action,Progress progress) { }
    record Parent(String species,String owner,boolean adult,long loveUntil,long cooldownUntil) { }
    static long incubationMs(boolean prototype) { return prototype ? 300_000 : INCUBATION_MS; }
    static long growthMs(boolean prototype) { return prototype ? 300_000 : GROWTH_MS; }
    static long deadline(long deadline,long previousDuration,long duration) { return deadline-previousDuration+duration; }

    static boolean primary(String species,String item) {
        if(species==null || item==null)return false;
        return switch(species) {
            case "trex","velociraptor","dilophosaurus" -> List.of("BEEF","PORKCHOP","CHICKEN","MUTTON","RABBIT").contains(item);
            case "pteranodon" -> List.of("COD","SALMON","TROPICAL_FISH").contains(item);
            case "triceratops","stegosaurus","brachiosaurus" -> List.of("WHEAT","FERN","DANDELION").contains(item);
            default -> false;
        };
    }
    static int requiredFeeds(String species) {
        return switch(species) {
            case "triceratops","stegosaurus","brachiosaurus" -> 8;
            default -> 5;
        };
    }
    static boolean finisher(String species,String item) { return item!=null && item.equals(finisher(species)); }
    static String finisher(String species) {
        if(species==null)return "";
        return switch(species) {
            case "trex","velociraptor","dilophosaurus" -> "BONE";
            case "pteranodon" -> "SEAGRASS";
            case "triceratops","stegosaurus","brachiosaurus" -> "TORCHFLOWER_SEEDS";
            default -> "";
        };
    }
    static Feeding feed(String species,String item,String actor,Progress previous,long now) {
        int required=requiredFeeds(species);
        Progress p=previous!=null && previous.expiresAt()>now ? previous : null;
        if(p!=null && !p.actor().equals(actor))return new Feeding(Action.LOCKED,p);
        if(primary(species,item)) {
            if(p!=null && p.feeds()>=required)return new Feeding(Action.READY,p);
            if(p!=null && p.nextFeedAt()>now)return new Feeding(Action.WAIT,p);
            return new Feeding(Action.FEED,new Progress(actor,p==null?1:p.feeds()+1,now+(required==8?15_000:FEED_COOLDOWN_MS),now+PROGRESS_EXPIRY_MS));
        }
        if(finisher(species,item))return new Feeding(p!=null && p.feeds()==required ? Action.FINISH : Action.NEED_FEEDS,p);
        return new Feeding(Action.IGNORE,p);
    }
    static boolean pair(Parent a,Parent b,double distanceSquared,long now) {
        return a.adult() && b.adult() && a.owner()!=null && !a.owner().isEmpty() && a.owner().equals(b.owner())
                && a.species()!=null && a.species().equals(b.species()) && distanceSquared<=36 && distanceSquared>=0
                && a.loveUntil()>now && b.loveUntil()>now && a.cooldownUntil()<=now && b.cooldownUntil()<=now;
    }
}
