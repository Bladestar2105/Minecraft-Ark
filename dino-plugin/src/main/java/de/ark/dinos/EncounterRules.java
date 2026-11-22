package de.ark.dinos;

final class EncounterRules {
    enum Phase {IDLE,GRAZING,ALERT,RUNNING,COOLDOWN}
    static final int ACTORS=9;
    static boolean persistent(boolean owned,boolean natural,boolean scene) {
        return owned || !natural && !scene;
    }
    static Phase phase(int ticks,boolean viewers) {
        if(!viewers || ticks>=440) return Phase.COOLDOWN;
        return ticks<100?Phase.GRAZING:ticks<140?Phase.ALERT:Phase.RUNNING;
    }
    static boolean budget(int population,int worldLimit,int nearby,int nearbyLimit) {
        return population>=0 && nearby>=0 && population+ACTORS<=worldLimit && nearby+ACTORS<=nearbyLimit;
    }
}
