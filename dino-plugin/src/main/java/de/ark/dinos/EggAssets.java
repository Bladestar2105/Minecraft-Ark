package de.ark.dinos;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

final class EggAssets {
    private EggAssets() {}

    static Map<String, byte[]> read(InputStream source) throws IOException {
        if (source == null) throw new IOException("Missing egg-assets.properties");
        var properties = new Properties();
        properties.load(source);
        Map<String, byte[]> assets = new HashMap<>();
        for (String path : properties.stringPropertyNames()) {
            if (!path.matches("assets/arkdinos/(?:items|models/item|textures/item)/egg_[a-z]+\\.(?:json|png)")) {
                throw new IOException("Invalid dinosaur egg asset path: " + path);
            }
            assets.put(path, Base64.getDecoder().decode(properties.getProperty(path)));
        }
        if (assets.size() != 24) throw new IOException("Expected 24 dinosaur egg assets");
        return Map.copyOf(assets);
    }
}
