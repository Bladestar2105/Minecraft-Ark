package de.ark.jurassic;

/** Pure absolute-coordinate terrain. No Bukkit objects or mutable random streams. */
final class Terrain {
    record Ridge(int[][] points, double height, double width) {}
    record River(int[][] points, double width) {}
    record Island(String id, String style, int[][] polygon, Ridge[] ridges, River[] rivers) {}
    record Site(String id, String kind, int x, int z, int width, int depth, int rotation, int[][] access) {}

    static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
    static long hash(int x, int z, long salt) {
        long value = x * 341873128712L + z * 132897987541L + TerrainData.SEED + salt;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
    static double random(int x, int z, long salt) {
        return (hash(x, z, salt) >>> 11) * 0x1.0p-53;
    }
    static double noise(double x, double z, int scale, long salt) {
        int a = (int) Math.floor(x / scale), b = (int) Math.floor(z / scale);
        double u = smooth(x / scale - a), v = smooth(z / scale - b);
        double p = random(a, b, salt) * (1-u) + random(a+1, b, salt) * u;
        double q = random(a, b+1, salt) * (1-u) + random(a+1, b+1, salt) * u;
        return p * (1-v) + q * v;
    }
    static double segmentDistance(double x, double z, int[] a, int[] b) {
        double dx = b[0]-a[0], dz = b[1]-a[1];
        double t = Math.clamp(((x-a[0])*dx+(z-a[1])*dz) / Math.max(1, dx*dx+dz*dz), 0, 1);
        return Math.hypot(x-a[0]-t*dx, z-a[1]-t*dz);
    }
    static double lineDistance(double x, double z, int[][] points) {
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 1; i < points.length; i++)
            distance = Math.min(distance, segmentDistance(x, z, points[i-1], points[i]));
        return distance;
    }
    static double coastDistance(int x, int z, Island island) {
        boolean inside = false;
        double distance = Double.POSITIVE_INFINITY;
        int[][] p = island.polygon();
        for (int i = 0, j = p.length-1; i < p.length; j=i++) {
            if ((p[i][1] > z) != (p[j][1] > z)
                    && x < (double)(p[j][0]-p[i][0])*(z-p[i][1])/(p[j][1]-p[i][1])+p[i][0]) inside = !inside;
            distance = Math.min(distance, segmentDistance(x, z, p[i], p[j]));
        }
        return inside ? distance : -distance;
    }
    Island island(int x, int z) {
        for (Island island : TerrainData.ISLANDS) if (coastDistance(x, z, island) >= 0) return island;
        return null;
    }
    double rawHeight(int x, int z) {
        double ocean = 34 + 8 * noise(x, z, 400, 1);
        if (Math.abs(x) >= 16384 || Math.abs(z) >= 16384) return ocean;
        double result = ocean;
        for (Island island : TerrainData.ISLANDS) {
            double coast = coastDistance(x, z, island);
            if (coast < -220) continue;
            double land = 72 + 22 * noise(x, z, 900, 3) + 7 * noise(x, z, 150, 4);
            for (Ridge ridge : island.ridges()) {
                double distance = lineDistance(x, z, ridge.points());
                land += ridge.height() * Math.exp(-distance*distance/(ridge.width()*ridge.width()));
            }
            if (island.style().equals("cliffs")) land += 70;
            double shore = smooth((coast+220)/320);
            double height = ocean * (1-shore) + land * shore;
            for (River river : island.rivers()) {
                double distance = lineDistance(x, z, river.points());
                double channel = smooth((river.width()+24-distance)/24);
                height = height*(1-channel)+(TerrainData.SEA-3)*channel;
            }
            result = Math.max(result, height);
        }
        // Film scene: broad field, gentle foreground hill, forested valley shoulders.
        double dx = (x-8192)/700.0, dz = (z-3072)/400.0;
        double meadow = smooth((1.32-Math.hypot(dx, dz))/.32);
        double hill = 10*Math.exp(-Math.pow((x-8005)/125.0,2)-Math.pow((z-3100)/100.0,2));
        double field = 76 + hill + 2*noise(x,z,220,7);
        return result*(1-meadow)+field*meadow;
    }
    int height(int x, int z) {
        double height = rawHeight(x,z);
        for (Site site : TerrainData.SITES) {
            if (!builtKind(site.kind())) continue;
            double distance = Math.max(Math.abs(x-site.x())-site.width()/2.0,
                    Math.abs(z-site.z())-site.depth()/2.0);
            double blend = smooth((18-distance)/18);
            double base=rawHeight(site.x(),site.z());
            if(!site.kind().equals("aviary") && !site.kind().equals("dock")) base=Math.max(base,TerrainData.SEA+2);
            if (blend > 0) height = height*(1-blend)+base*blend;
        }
        return (int)Math.round(height);
    }
    static boolean builtKind(String kind) {
        return switch(kind) {
            case "visitor_center", "gate", "helipad", "raptor_pen", "utility", "dock", "camp",
                    "village", "lab", "aviary", "airstrip", "crane" -> true;
            default -> false;
        };
    }
    boolean field(int x, int z) {
        return Math.hypot((x-8192)/700.0,(z-3072)/400.0) < 1.06;
    }
    boolean road(int x, int z) {
        for (Site s : TerrainData.SITES) {
            if (lineDistance(x,z,s.access()) < 3) return true;
        }
        return false;
    }
}
