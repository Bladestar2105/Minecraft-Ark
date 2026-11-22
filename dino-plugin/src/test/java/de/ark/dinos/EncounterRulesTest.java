package de.ark.dinos;

public final class EncounterRulesTest {
    public static void main(String[] args) {
        if(EncounterRules.phase(0,true)!=EncounterRules.Phase.GRAZING) throw new AssertionError();
        if(EncounterRules.phase(100,true)!=EncounterRules.Phase.ALERT) throw new AssertionError();
        if(EncounterRules.phase(140,true)!=EncounterRules.Phase.RUNNING) throw new AssertionError();
        if(EncounterRules.phase(440,true)!=EncounterRules.Phase.COOLDOWN) throw new AssertionError();
        if(EncounterRules.phase(10,false)!=EncounterRules.Phase.COOLDOWN) throw new AssertionError("no spectators aborts");
        if(!EncounterRules.budget(3,12,2,12) || EncounterRules.budget(4,12,2,12)
                || EncounterRules.budget(0,64,4,12)) throw new AssertionError("scene counts all nine models");
        if(EncounterRules.persistent(false,false,true) || EncounterRules.persistent(false,true,false)
                || !EncounterRules.persistent(true,false,true) || !EncounterRules.persistent(false,false,false))
            throw new AssertionError("temporary actors stay temporary across native restore; owned animals persist");
        System.out.println("EncounterRulesTest: states, abort and shared population budgets passed");
    }
}
