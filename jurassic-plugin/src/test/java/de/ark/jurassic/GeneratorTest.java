package de.ark.jurassic;

import java.lang.reflect.Proxy;
import java.util.Random;
import org.bukkit.HeightMap;
import org.bukkit.generator.WorldInfo;

public final class GeneratorTest {
    public static void main(String[] args) {
        WorldInfo world=(WorldInfo)Proxy.newProxyInstance(WorldInfo.class.getClassLoader(),new Class<?>[]{WorldInfo.class},
                (proxy,method,values)->switch(method.getName()) {
                    case "getMinHeight" -> -64;
                    case "getMaxHeight" -> 320;
                    default -> throw new AssertionError("Unexpected API access: "+method.getName());
                });
        // Peña's planned ridge is taller than the native generator's reserved
        // building/vegetation clearance. Its base-height API must match blocks.
        int height=new JurassicGenerator().getBaseHeight(world,new Random(1),2400,3700,HeightMap.OCEAN_FLOOR);
        if(height!=289) throw new AssertionError("Peña base height differs from actual clamped ground: "+height);
        System.out.println("GeneratorTest: native API height agrees with Peña's clamped physical surface");
    }
}
