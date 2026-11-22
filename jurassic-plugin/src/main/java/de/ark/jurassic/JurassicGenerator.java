package de.ark.jurassic;

import java.util.List;
import java.util.Random;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

final class JurassicGenerator extends ChunkGenerator {
    final Terrain terrain = new Terrain();
    private BlockData jungleLeaves,spruceLeaves;
    void initialize() {
        Leaves jungle=(Leaves)Material.JUNGLE_LEAVES.createBlockData();jungle.setPersistent(true);
        Leaves spruce=(Leaves)Material.SPRUCE_LEAVES.createBlockData();spruce.setPersistent(true);
        jungleLeaves=jungle;spruceLeaves=spruce;
    }
    int surface(int x,int z,int min,int max) {
        return Math.clamp(terrain.height(x,z),min+2,max-32);
    }

    @Override public void generateNoise(WorldInfo world, Random ignored, int cx, int cz, ChunkData data) {
        int min=data.getMinHeight(), max=data.getMaxHeight();
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int wx=cx*16+x,wz=cz*16+z,h=surface(wx,wz,min,max);
            data.setBlock(x,min,z,Material.BEDROCK);
            data.setRegion(x,min+1,z,x+1,h-3,z+1,Material.STONE);
            Material surface = h<TerrainData.SEA+4 ? Material.SAND : Material.GRASS_BLOCK;
            data.setRegion(x,h-3,z,x+1,h,z+1,surface==Material.SAND?Material.SAND:Material.DIRT);
            data.setBlock(x,h,z,surface);
            if(h<TerrainData.SEA) data.setRegion(x,h+1,z,x+1,TerrainData.SEA+1,z+1,Material.WATER);
        }
    }
    @Override public void generateSurface(WorldInfo world, Random ignored, int cx, int cz, ChunkData data) {
        Painter p=new Painter(cx,cz,data);
        for(int z=0;z<16;z++) for(int x=0;x<16;x++) {
            int wx=cx*16+x,wz=cz*16+z,h=p.height(terrain,wx,wz);
            if(terrain.road(wx,wz)) p.block(wx,Math.max(h,TerrainData.SEA+1),wz,
                    h<=TerrainData.SEA?Material.DARK_OAK_PLANKS:Material.GRAVEL);
            else if(h<=TerrainData.SEA+4) continue;
            else if(Terrain.random(wx,wz,21)<.16) p.block(wx,h+1,wz,
                    terrain.field(wx,wz)?Material.SHORT_GRASS:Material.FERN);
        }
        // Trees are anchored in absolute cells and clipped to this chunk. Adjacent
        // chunks independently reconstruct the same canopy, regardless of order.
        for(int gz=Math.floorDiv(cz*16-8,20);gz<=Math.floorDiv(cz*16+23,20);gz++)
            for(int gx=Math.floorDiv(cx*16-8,20);gx<=Math.floorDiv(cx*16+23,20);gx++) {
                int x=gx*20+4+(int)(Terrain.random(gx,gz,31)*12),z=gz*20+4+(int)(Terrain.random(gx,gz,32)*12);
                Terrain.Island island=terrain.island(x,z);
                if(island==null || terrain.field(x,z) || terrain.road(x,z) || terrain.height(x,z)<68) continue;
                boolean clear=true;
                for(Terrain.Site s:TerrainData.SITES) if(Terrain.builtKind(s.kind())
                        && Math.abs(x-s.x())<s.width()/2+9 && Math.abs(z-s.z())<s.depth()/2+9) clear=false;
                if(!clear || Terrain.random(gx,gz,33)<.18) continue;
                int h=p.height(terrain,x,z),length=10+(int)(Terrain.random(gx,gz,34)*13);
                boolean sorna=island.id().equals("sorna");
                Material log=sorna?Material.SPRUCE_LOG:Material.JUNGLE_LOG;
                BlockData leaves=sorna?spruceLeaves:jungleLeaves;
                for(int y=h+1;y<=h+length;y++) p.block(x,y,z,log);
                for(int dy=-3;dy<=2;dy++) for(int dz=-5;dz<=5;dz++) for(int dx=-5;dx<=5;dx++)
                    if(dx*dx+dz*dz+dy*dy*3<26) p.block(x+dx,h+length+dy,z+dz,leaves);
            }
        // The fallen trunk has a traversable shelter and irregular exposed roots.
        for(int x=8060;x<8105;x++) for(int dy=-2;dy<=2;dy++) for(int dz=-2;dz<=2;dz++)
            if(dy*dy+dz*dz<=5) p.block(x,p.height(terrain,8082,3036)+3+dy,3036+dz,Material.DARK_OAK_LOG);
        for(int i=0;i<7;i++) p.block(8059-i,p.height(terrain,8082,3036)+1+i/2,3036+(i%2==0?i:-i),Material.DARK_OAK_WOOD);
        for(Terrain.Site site:TerrainData.SITES) {
            if(Math.abs(cx*16+8-site.x())>site.width()/2+24 || Math.abs(cz*16+8-site.z())>site.depth()/2+24) continue;
            Structures.paint(p,terrain,site);
        }
    }
    @Override public int getBaseHeight(WorldInfo w, Random r, int x, int z, HeightMap type) {
        int h=surface(x,z,w.getMinHeight(),w.getMaxHeight());
        return (type==HeightMap.OCEAN_FLOOR || type==HeightMap.OCEAN_FLOOR_WG ? h : Math.max(h,TerrainData.SEA))+1;
    }
    @Override public Location getFixedSpawnLocation(World w, Random r) {
        return new Location(w,8192.5,terrain.height(8192,3072)+1,3072.5);
    }
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo world,int x,int y,int z) {
                Terrain.Island i=terrain.island(x,z);
                if(i==null || terrain.height(x,z)<TerrainData.SEA) return Biome.WARM_OCEAN;
                if(terrain.field(x,z)) return Biome.PLAINS;
                return switch(i.style()) {
                    case "mangrove" -> Biome.MANGROVE_SWAMP;
                    case "cliffs" -> Biome.WINDSWEPT_FOREST;
                    case "valleys" -> Biome.FOREST;
                    default -> i.id().equals("sorna")?Biome.OLD_GROWTH_PINE_TAIGA:Biome.JUNGLE;
                };
            }
            @Override public List<Biome> getBiomes(WorldInfo w) {
                return List.of(Biome.WARM_OCEAN,Biome.PLAINS,Biome.MANGROVE_SWAMP,Biome.WINDSWEPT_FOREST,
                        Biome.FOREST,Biome.OLD_GROWTH_PINE_TAIGA,Biome.JUNGLE);
            }
        };
    }
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateDecorations(){return false;}
    @Override public boolean shouldGenerateMobs(){return false;}
    @Override public boolean shouldGenerateStructures(){return false;}

    static final class Painter {
        final int cx,cz; final ChunkData data;
        Painter(int cx,int cz,ChunkData data){this.cx=cx;this.cz=cz;this.data=data;}
        int height(Terrain t,int x,int z){return Math.clamp(t.height(x,z),data.getMinHeight()+2,data.getMaxHeight()-32);}
        void block(int x,int y,int z,Material m) {
            if(Math.floorDiv(x,16)==cx && Math.floorDiv(z,16)==cz && y>=data.getMinHeight() && y<data.getMaxHeight())
                data.setBlock(Math.floorMod(x,16),y,Math.floorMod(z,16),m);
        }
        void block(int x,int y,int z,BlockData state) {
            if(Math.floorDiv(x,16)==cx && Math.floorDiv(z,16)==cz && y>=data.getMinHeight() && y<data.getMaxHeight())
                data.setBlock(Math.floorMod(x,16),y,Math.floorMod(z,16),state);
        }
        void box(int x0,int y0,int z0,int x1,int y1,int z1,Material m) {
            int ax=Math.max(x0,cx*16),bx=Math.min(x1,cx*16+15),az=Math.max(z0,cz*16),bz=Math.min(z1,cz*16+15);
            if(ax<=bx && az<=bz) data.setRegion(ax-cx*16,Math.max(y0,data.getMinHeight()),az-cz*16,
                    bx-cx*16+1,Math.min(y1+1,data.getMaxHeight()),bz-cz*16+1,m);
        }
    }
}
