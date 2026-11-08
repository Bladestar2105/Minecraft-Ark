package de.ark.dinos;

public final class CareRulesTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        check(CareRules.primary("trex", "BEEF") && CareRules.primary("velociraptor", "CHICKEN"), "Predators eat raw meat");
        check(CareRules.SPECIES.size()==7 && CareRules.SPECIES.contains("dilophosaurus"), "Creative and breeding catalog includes Dilo");
        check(CareRules.primary("dilophosaurus", "BEEF") && CareRules.finisher("dilophosaurus", "BONE"), "Dilo has carnivore taming recipe");
        check(!CareRules.primary("trex", "COOKED_BEEF") && !CareRules.primary("trex", "WHEAT"), "No cooked or plant predator feed");
        check(CareRules.primary("pteranodon", "COD") && CareRules.primary("pteranodon", "SALMON"), "Pteranodon eats fish");
        check(CareRules.primary("brachiosaurus", "WHEAT") && CareRules.finisher("stegosaurus", "TORCHFLOWER_SEEDS"), "Herbivore foods");
        check(CareRules.primary("stegosaurus", "FERN") && CareRules.primary("triceratops", "DANDELION"), "Herbivores accept plants");
        check(!CareRules.primary("brachiosaurus", "CARROT"), "No carrots in the approved herbivore recipe");
        check(CareRules.finisher("trex", "BONE") && CareRules.finisher("pteranodon", "SEAGRASS"), "Species finishers");
        check(!CareRules.primary("bogus", "WHEAT") && !CareRules.finisher(null, "BONE"), "Unknown species rejected");
        long now=1_000_000;
        var herb=CareRules.feed("triceratops","WHEAT","alice",null,now).progress();
        check(CareRules.feed("triceratops","FERN","alice",herb,now+14_999).action()==CareRules.Action.WAIT, "Herbivore feed spacing is fifteen seconds");
        for(int n=2;n<=8;n++) {
            check(CareRules.feed("triceratops","TORCHFLOWER_SEEDS","alice",herb,now+n*15_000).action()==CareRules.Action.NEED_FEEDS, "Seeds cannot skip eight plant feeds");
            var result=CareRules.feed("triceratops",n%2==0?"FERN":"DANDELION","alice",herb,now+(n-1)*15_000);
            check(result.action()==CareRules.Action.FEED && result.progress().feeds()==n,"Eight mixed plant feeds");herb=result.progress();
        }
        check(CareRules.feed("triceratops","TORCHFLOWER_SEEDS","alice",herb,now+120_000).action()==CareRules.Action.FINISH,"Seeds finish only after eight plant feeds");
        var first=CareRules.feed("trex", "BEEF", "alice", null, now);
        check(first.action()==CareRules.Action.FEED && first.progress().feeds()==1, "First feeding records progress");
        check(CareRules.feed("trex", "BEEF", "alice", first.progress(), now+9_999).action()==CareRules.Action.WAIT, "Ten-second feeding cooldown");
        check(CareRules.feed("trex", "BONE", "alice", first.progress(), now+10_000).action()==CareRules.Action.NEED_FEEDS, "Bone cannot skip five feeds");
        check(CareRules.feed("trex", "BEEF", "bob", first.progress(), now+10_000).action()==CareRules.Action.LOCKED, "Progress cannot be stolen");
        var progress=first.progress();
        for(int i=1;i<5;i++) progress=CareRules.feed("trex", "BEEF", "alice", progress, now+i*10_000).progress();
        check(progress.feeds()==5, "Five primary feeds");
        check(CareRules.feed("trex", "BEEF", "alice", progress, now+50_000).action()==CareRules.Action.READY, "Extra primary food is retained");
        check(CareRules.feed("trex", "BONE", "bob", progress, now+50_000).action()==CareRules.Action.LOCKED, "Finisher cannot steal ownership");
        check(CareRules.feed("trex", "BONE", "alice", progress, now+40_000).action()==CareRules.Action.FINISH, "Finisher accepted after fifth feed");
        var expired=CareRules.feed("trex", "BEEF", "bob", progress, progress.expiresAt());
        check(expired.action()==CareRules.Action.FEED && expired.progress().actor().equals("bob") && expired.progress().feeds()==1, "Expired progress starts over");
        var adult=new CareRules.Parent("trex", "alice", true, now+60_000, 0);
        check(CareRules.pair(adult,adult,36,now), "Same owner's adult pair within six blocks");
        check(!CareRules.pair(adult,adult,36.01,now), "Pair distance bounded");
        check(!CareRules.pair(adult,new CareRules.Parent("trex","bob",true,now+60_000,0),1,now), "No cross-owner breeding");
        check(!CareRules.pair(adult,new CareRules.Parent("pteranodon","alice",true,now+60_000,0),1,now), "No cross-species breeding");
        check(!CareRules.pair(adult,new CareRules.Parent("trex","alice",false,now+60_000,0),1,now), "Babies cannot breed");
        check(!CareRules.pair(adult,new CareRules.Parent("trex","alice",true,now,0),1,now), "Love expires after sixty seconds");
        check(!CareRules.pair(adult,new CareRules.Parent("trex","alice",true,now+60_000,now+1),1,now), "Breeding cooldown enforced");
        check(CareRules.INCUBATION_MS==1_200_000 && CareRules.GROWTH_MS==900_000 && CareRules.BREED_COOLDOWN_MS==300_000, "Approved timing");
        check(CareRules.incubationMs(true)==300_000 && CareRules.growthMs(true)==300_000, "Prototype care takes five minutes");
        check(CareRules.incubationMs(false)==1_200_000 && CareRules.growthMs(false)==900_000, "Main world timings remain unchanged");
        check(CareRules.deadline(now+1_080_000,1_200_000,300_000)==now+180_000, "Existing eggs keep their elapsed two minutes");
        check(CareRules.deadline(now+480_000,900_000,300_000)==now-120_000, "Existing juveniles older than five minutes finish immediately");
        check(CareRules.deadline(now+180_000,300_000,300_000)==now+180_000, "Normalized timing does not shorten again");
        check(!CareRules.SPECIES.contains("mosasaurus") && !CareRules.primary("mosasaurus","COD") && !CareRules.finisher("mosasaurus","SEAGRASS"), "Wild Mosa is excluded from care");
        System.out.println("PASS care foods, five feeds, cooldown, expiry, owner isolation and adult breeding rules");
    }
}
