package de.ark.dinos;

public final class GroundSpeedsTest {
    public static void main(String[] args) {
        var slow=GroundSpeeds.pace("brachiosaurus");var fast=GroundSpeeds.pace("velociraptor");
        check(slow.walk()==1&&slow.run()==2,"Heavy browser uses deliberate ground paces");
        check(fast.walk()==2&&fast.run()==7,"Small runner stays within the bounded gameplay cap");
        var rex=GroundSpeeds.pace("trex");
        check(rex.walk()==1.5&&rex.run()==6,"T-Rex receives the requested modest pace increase");
        for(var species:new String[]{"brachiosaurus","stegosaurus","triceratops","trex","velociraptor","dilophosaurus","pteranodon"}) {
            var pace=GroundSpeeds.pace(species);check(pace.walk()>0&&pace.run()>pace.walk()&&pace.run()<=7,"Every species walks and sprints within bounds");
            for(boolean running:new boolean[]{false,true}) {
                double sniffer=GroundSpeeds.pathMultiplier(pace,running,.1),ravager=GroundSpeeds.pathMultiplier(pace,running,.3);
                check(Math.abs(sniffer*.1-ravager*.3)<1e-12,"Native carriers produce the same effective input");
                var straight=RidingRules.movement(0,true,false,false,false,pace.speed(running)/20);
                var diagonal=RidingRules.movement(45,true,false,true,false,pace.speed(running)/20);
                check(Math.abs(straight.z()*20-pace.speed(running))<1e-9,"Rider velocity uses the same pace");
                check(Math.abs(Math.hypot(diagonal.x(),diagonal.z())-Math.abs(straight.z()))<1e-9,"Diagonal input cannot exceed the cap");
            }
        }
        check(GroundSpeeds.pace(null)==null&&GroundSpeeds.pace("mosasaurus")==null,"Unknown/aquatic species keep existing movement");
        check(GroundSpeeds.pathMultiplier(slow,false,0)==0&&GroundSpeeds.pathMultiplier(slow,false,Double.NaN)==0,"Invalid carrier speed fails stationary");
        System.out.println("PASS: species paces, carrier normalization and rider caps");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
