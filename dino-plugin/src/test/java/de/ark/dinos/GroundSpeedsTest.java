package de.ark.dinos;

public final class GroundSpeedsTest {
    public static void main(String[] args) {
        String[] speciesNames={"brachiosaurus","stegosaurus","triceratops","trex","velociraptor","dilophosaurus","pteranodon"};
        double[][] expected={{1.4,3.5},{1.5,4.5},{2,7},{2.5,9},{3,12},{2.8,10},{2,4.5}};
        var slow=GroundSpeeds.pace("brachiosaurus");
        for(int i=0;i<speciesNames.length;i++) {
            String species=speciesNames[i];
            var pace=GroundSpeeds.pace(species);check(pace.walk()>0&&pace.run()>pace.walk()&&pace.run()<=12,"Every species walks and sprints within bounds");
            for(boolean running:new boolean[]{false,true}) {
                double sniffer=GroundSpeeds.pathMultiplier(pace,running,.1),ravager=GroundSpeeds.pathMultiplier(pace,running,.3);
                check(Math.abs(sniffer*.1-ravager*.3)<1e-12,"Native carriers produce the same effective input");
                var straight=RidingRules.movement(0,true,false,false,false,pace.speed(running)/20);
                var diagonal=RidingRules.movement(45,true,false,true,false,pace.speed(running)/20);
                check(Math.abs(straight.z()*20-expected[i][running?1:0])<1e-9,species+" must travel the selected walk/run distance in twenty ticks");
                check(Math.abs(Math.hypot(diagonal.x(),diagonal.z())-Math.abs(straight.z()))<1e-9,"Diagonal input cannot exceed the cap");
            }
        }
        check(GroundSpeeds.pace(null)==null&&GroundSpeeds.pace("mosasaurus")==null,"Unknown/aquatic species keep existing movement");
        for(double invalid:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})check(GroundSpeeds.pathMultiplier(slow,false,invalid)==0,"Invalid carrier speed fails stationary");
        System.out.println("PASS: species paces, carrier normalization and rider caps");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
