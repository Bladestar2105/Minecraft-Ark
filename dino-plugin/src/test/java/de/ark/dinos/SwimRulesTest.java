package de.ark.dinos;

import java.util.concurrent.atomic.AtomicInteger;

public final class SwimRulesTest {
    public static void main(String[] args) {
        for(String species:new String[]{"triceratops","stegosaurus","brachiosaurus","trex","velociraptor","dilophosaurus","pteranodon"})
            check(SwimRules.supports(species),"Land species must recover at the surface: "+species);
        check(!SwimRules.supports("mosasaurus")&&!SwimRules.supports(null)&&!SwimRules.supports("cow"),"Aquatic and unrelated entities must retain their own controller");
        check(SwimRules.verticalVelocity(10,14,-.5)>0,"A submerged, sinking animal must ascend even with no input");
        close(SwimRules.verticalVelocity(10,1000,-.5),.18,"Deep water cannot produce unbounded upward motion");
        close(SwimRules.verticalVelocity(14.12,14,.18),0,"Reaching the surface must stop the controller's upward momentum");
        close(SwimRules.verticalVelocity(15,14,-.02),-.02,"An animal above its floating height retains ordinary downward motion");
        check(SwimRules.verticalVelocity(14.1,14,0)>0&&SwimRules.verticalVelocity(14.1,14,0)<=.02,"Final approach cannot overshoot its head-clearance target");
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            close(SwimRules.verticalVelocity(invalid,14,0),0,"Invalid head position stops adjustment");
            close(SwimRules.verticalVelocity(10,invalid,0),0,"Invalid surface stops adjustment");
            close(SwimRules.verticalVelocity(10,14,invalid),0,"Invalid incoming velocity stops adjustment");
        }
        close(SwimRules.surface(2,4,320,y->y>=2&&y<7).orElseThrow(),7,"A contiguous water column ends at its first dry cell");
        close(SwimRules.surface(2,4,320,y->y==4||y==7).orElseThrow(),5,"A dry gap must not connect to a separate overhead pool");
        check(SwimRules.surface(2,4,320,y->y==7).isEmpty(),"Water outside the body must not activate buoyancy");
        close(SwimRules.surface(318,319,320,y->true).orElseThrow(),320,"Search respects world height");
        AtomicInteger samples=new AtomicInteger();
        close(SwimRules.surface(-64,-61,320,y->{samples.incrementAndGet();return true;}).orElseThrow(),-32,"Deep columns provide a bounded intermediate ascent target");
        check(samples.get()<=32,"A single water column must read at most thirty-two cells");
        check(SwimRules.surface(320,321,320,y->{throw new AssertionError("Read above world height");}).isEmpty(),"World ceiling needs no block read");
        check(SwimRules.surface(5,4,320,y->{throw new AssertionError("Read empty body");}).isEmpty(),"Empty body needs no block read");
        System.out.println("PASS: land species surface recovery, finite buoyancy and bounded water-column search");
    }

    private static void close(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-9,message+": "+actual);}
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
