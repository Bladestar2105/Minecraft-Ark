package de.ark.jurassic;

import java.nio.file.Path;

public final class TerrainTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Terrain terrain = new Terrain();
        check(TerrainData.ISLANDS.length == 6, "six islands");
        check(terrain.height(20000, 0) < TerrainData.SEA, "outside border is ocean");
        for (Terrain.Island island : TerrainData.ISLANDS) {
            check(island.polygon().length >= 12, "individual coast outline");
            for (int[] point : island.polygon())
                check(Math.abs(point[0]) < 16384 && Math.abs(point[1]) < 16384, "coast inside border");
        }
        for (int z = 2048; z < 4096; z += 31) for (int x = 7168; x < 9216; x += 29) {
            int first = terrain.height(x, z);
            terrain.height(-x, -z);
            check(first == terrain.height(x, z), "order independent absolute-coordinate terrain");
            if(Math.hypot((x-8192)/700.0,(z-3072)/400.0)<.95)
                check(Math.abs(first - terrain.height(x + 1, z)) <= 1, "walkable meadow at "+x+","+z);
        }
        check(terrain.height(8192, 3072) > TerrainData.SEA, "reference on land");
        check(terrain.height(8080, 3100) > terrain.height(8192, 3072), "low scene hill");
        for (Terrain.Site site : TerrainData.SITES) {
            check(terrain.island(site.x(), site.z()) != null, "landmark on land: " + site.id());
            check(!Terrain.builtKind(site.kind()) || terrain.height(site.x(), site.z()) > TerrainData.SEA || site.kind().equals("dock")
                    || site.kind().equals("river") || site.kind().equals("aviary"), "dry landmark: " + site.id());
        }
        check(Math.floorDiv(-1, 16) == -1, "negative chunk coordinate");
        if (args.length == 1) TerrainPreview.write(terrain, Path.of(args[0]));
        System.out.println("TerrainTest: six outlines, border, deterministic sampling, slopes, hill, landmark land checks passed");
    }
}
