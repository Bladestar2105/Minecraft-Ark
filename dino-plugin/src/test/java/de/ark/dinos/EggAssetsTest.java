package de.ark.dinos;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class EggAssetsTest {
    public static void main(String[] args) throws Exception {
        try (var source = Files.newInputStream(Path.of(args[0]))) {
            var assets = EggAssets.read(source);
            if (assets.size() != 24) throw new AssertionError("All species assets required");
            for (String species : new String[]{"trex", "triceratops", "stegosaurus", "brachiosaurus", "velociraptor", "pteranodon", "dilophosaurus", "mosasaurus"}) {
                var texture = assets.get("assets/arkdinos/textures/item/egg_" + species + ".png");
                if (texture == null || !Arrays.equals(Arrays.copyOf(texture, 8), new byte[]{(byte)137,80,78,71,13,10,26,10})) {
                    throw new AssertionError("Missing PNG for " + species);
                }
            }
        }
        for (var source : new ByteArrayInputStream[]{null, new ByteArrayInputStream("../bad=YQ==".getBytes()), new ByteArrayInputStream(new byte[0])}) {
            try {
                EggAssets.read(source);
                throw new AssertionError("Invalid asset input accepted");
            } catch (IOException expected) {}
        }
        System.out.println("EggAssetsTest passed");
    }
}
