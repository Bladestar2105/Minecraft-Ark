package de.ark.jurassic;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class WorldBindingTest {
    public static void main(String[] args) {
        UUID id=new UUID(0,1);
        JsonObject marker=new JsonObject();
        marker.addProperty("world_uuid",id.toString());marker.addProperty("seed",TerrainData.SEED);
        marker.addProperty("definition_sha256",TerrainData.HASH);marker.addProperty("generator_version",TerrainData.VERSION);
        marker.addProperty("implementation_sha256",TerrainData.IMPLEMENTATION);
        ArkJurassic.validateBinding(marker,id,TerrainData.SEED);
        for(int invalid=0;invalid<3;invalid++) {
            JsonObject candidate=marker.deepCopy();
            UUID actual=id;long seed=TerrainData.SEED;
            if(invalid==0) seed++;
            if(invalid==1) actual=new UUID(0,2);
            if(invalid==2) candidate.addProperty("implementation_sha256","changed");
            boolean rejected=false;
            try{ArkJurassic.validateBinding(candidate,actual,seed);}catch(IllegalStateException expected){rejected=true;}
            if(!rejected) throw new AssertionError("Wrong native seed/UUID/implementation accepted: "+invalid);
        }
        System.out.println("WorldBindingTest: matching native identity accepted; wrong seed, copied marker and changed implementation refused");
    }
}
