package de.ark.jurassic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.ServerLoadEvent;

/** No automatic creation/import, block commands, entities, or permanent tickets. */
public final class ArkJurassic extends JavaPlugin implements Listener {
    private final Gson json=new GsonBuilder().create();
    private final JurassicGenerator generator=new JurassicGenerator();
    private World world;
    private Job job;
    private boolean active,inflight,disabled;
    private int verifyCursor,verifyTarget;
    private long runStart,runningMillis;
    static final class Job {
        String world_uuid,definition_sha256,generator_version,implementation_sha256;
        long seed;
        int[] bounds;
        int done,total;
    }
    @Override public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();
        generator.initialize();
        getServer().getPluginManager().registerEvents(this,this);
        getLogger().info("Explicit test-world creation only: /jurassic open; definitions "+TerrainData.HASH);
    }
    @EventHandler public void serverLoaded(ServerLoadEvent event) {
        Path marker=Bukkit.getServer().getLevelDirectory().resolve("dimensions/minecraft/"+TerrainData.WORLD+"/ark-jurassic.json");
        if(getConfig().getBoolean("open-bound-world-on-start",true) && Files.isRegularFile(marker)) {
            try{open();}catch(Exception error){getLogger().severe("Bound world was not opened: "+error);}
        }
    }
    @Override public void onDisable() {
        disabled=true;
        pause();
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String name,String id) {
        if(!TerrainData.WORLD.equals(name)) throw new IllegalArgumentException("Generator is restricted to its isolated world");
        return generator;
    }
    private Map<String,Object> binding() {
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("world_uuid",world.getUID().toString());data.put("seed",world.getSeed());
        data.put("generator_version",TerrainData.VERSION);data.put("definition_sha256",TerrainData.HASH);
        data.put("implementation_sha256",TerrainData.IMPLEMENTATION);
        return data;
    }
    static void validateBinding(JsonObject value,java.util.UUID uid,long nativeSeed) {
        if(!value.get("definition_sha256").getAsString().equals(TerrainData.HASH)
                || !value.get("generator_version").getAsString().equals(TerrainData.VERSION)
                || !value.has("implementation_sha256") || !value.get("implementation_sha256").getAsString().equals(TerrainData.IMPLEMENTATION)
                || value.get("seed").getAsLong()!=TerrainData.SEED
                || nativeSeed!=TerrainData.SEED
                || uid!=null && !value.get("world_uuid").getAsString().equals(uid.toString()))
            throw new IllegalStateException("World/job identity mismatch. Use a separately reviewed migration or a new world; never regenerate played chunks.");
    }
    private void write(Path path,Object value) throws IOException {
        Path temporary=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temporary,json.toJson(value)+"\n");
        Files.move(temporary,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    private void open() throws IOException {
        if(world!=null) return;
        // 26.3 stores dimensions under the level directory, not beside it.
        Path folder=Bukkit.getServer().getLevelDirectory().resolve("dimensions/minecraft/"+TerrainData.WORLD);
        Path marker=folder.resolve("ark-jurassic.json");
        Path checkpoint=getDataFolder().toPath().resolve("job.json");
        boolean existing=Files.exists(folder);
        JsonObject recorded=null;
        if(existing) {
            if(!Files.isRegularFile(marker)) throw new IllegalStateException("Existing unbound world refused; no automatic adoption");
            recorded=json.fromJson(Files.readString(marker),JsonObject.class);
            validateBinding(recorded,null,TerrainData.SEED);
        } else if(Files.exists(checkpoint)) {
            throw new IllegalStateException("Checkpoint exists without its bound world; refusing a new world");
        }
        if(Bukkit.getWorld(TerrainData.WORLD)!=null) throw new IllegalStateException("World already loaded by another generator/import; restart with ArkJurassic only");
        try {
            world=new WorldCreator(org.bukkit.NamespacedKey.minecraft(TerrainData.WORLD)).seed(TerrainData.SEED)
                    .generator(generator).generateStructures(false).createWorld();
            if(world==null) throw new IllegalStateException("Paper could not create test world");
            if(!folder.toAbsolutePath().normalize().equals(world.getWorldPath().toAbsolutePath().normalize()))
                throw new IllegalStateException("Unexpected native world path; refusing to bind");
            validateBinding(existing?recorded:json.toJsonTree(binding()).getAsJsonObject(),world.getUID(),world.getSeed());
            Job validated=null;
            if(Files.isRegularFile(checkpoint)) {
                JsonObject state=json.fromJson(Files.readString(checkpoint),JsonObject.class);
                validateBinding(state,world.getUID(),world.getSeed());
                validated=json.fromJson(state,Job.class);
                validateJob(validated);
            }
            if(!existing) {
                world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(32768);
                world.setSpawnLocation(8192,generator.terrain.height(8192,3072)+1,3072);
                write(world.getWorldPath().resolve("ark-jurassic.json"),binding());
            }
            job=validated;
        } catch(IOException | RuntimeException error) {
            if(world!=null) Bukkit.unloadWorld(world,false);
            world=null;job=null;
            throw error;
        }
    }
    static void validateJob(Job job) {
        if(job.bounds==null || job.bounds.length!=4 || job.bounds[0]<-1024 || job.bounds[1]<-1024
                || job.bounds[2]>1023 || job.bounds[3]>1023 || job.bounds[0]>job.bounds[2] || job.bounds[1]>job.bounds[3]
                || job.total!=(job.bounds[2]-job.bounds[0]+1)*(job.bounds[3]-job.bounds[1]+1)
                || job.done<0 || job.done>job.total) throw new IllegalStateException("Invalid job checkpoint");
    }
    private void checkpoint() throws IOException {
        if(job!=null) write(getDataFolder().toPath().resolve("job.json"),job);
    }
    private void pause() {
        if(active) runningMillis+=System.currentTimeMillis()-runStart;
        active=false;
        try {checkpoint();} catch(IOException e){getLogger().severe("Checkpoint failed: "+e);}
    }
    private void resume() {
        if(world==null || job==null) throw new IllegalStateException("Open world and create a job first");
        if(active) return;
        active=true;runStart=System.currentTimeMillis();
        // A persisted cursor can be ahead of the region writer after a crash.
        // Recheck the completed prefix in bounded slices before trusting it.
        verifyCursor=0;verifyTarget=job.done;
        next();
    }
    private void next() {
        if(!active || inflight || disabled) return;
        int width=job.bounds[2]-job.bounds[0]+1;
        if(verifyCursor<verifyTarget) {
            for(int i=0;i<16 && verifyCursor<verifyTarget;i++,verifyCursor++) {
                int x=job.bounds[0]+verifyCursor%width,z=job.bounds[1]+verifyCursor/width;
                if(!world.isChunkGenerated(x,z)) {
                    job.done=verifyCursor;verifyTarget=verifyCursor;
                    try {checkpoint();} catch(IOException error){pause();return;}
                    break;
                }
            }
            Bukkit.getScheduler().runTaskLater(this,this::next,1);
            return;
        }
        if(job.done==job.total){pause();return;}
        int start=job.done,count=Math.min(4,job.total-start);
        int[] xs=new int[count],zs=new int[count];
        CompletableFuture<?>[] requests=new CompletableFuture<?>[count];
        inflight=true;
        // Submission occurs on the server thread. Generation uses only ChunkData.
        for(int i=0;i<count;i++) {
            xs[i]=job.bounds[0]+(start+i)%width;zs[i]=job.bounds[1]+(start+i)/width;
            requests[i]=world.getChunkAtAsync(xs[i],zs[i],true);
        }
        CompletableFuture.allOf(requests).whenComplete((ignored,error)->{
            if(disabled) return; // persisted cursor safely repeats the native chunk request on restart
            Bukkit.getScheduler().runTask(this,()->{
                inflight=false;
                if(error!=null){pause();getLogger().severe("Chunk job paused: "+error);return;}
                job.done+=count;
                try {checkpoint();} catch(IOException e){pause();getLogger().severe("Checkpoint failed: "+e);return;}
                for(int i=0;i<count;i++) world.unloadChunkRequest(xs[i],zs[i]);
                if(active) Bukkit.getScheduler().runTaskLater(this,this::next,1);
            });
        });
    }
    private Map<String,Object> status() {
        Map<String,Object> value=new LinkedHashMap<>(world==null?Map.of():binding());
        value.put("world",TerrainData.WORLD);value.put("opened",world!=null);
        if(world!=null) {
            value.put("world_folder",world.getWorldPath().toAbsolutePath().normalize().toString());
            value.put("min_height",world.getMinHeight());value.put("max_height",world.getMaxHeight());
            value.put("loaded_chunks",world.getLoadedChunks().length);value.put("entities",world.getEntityCount());
        }
        value.put("mspt",Bukkit.getAverageTickTime());value.put("tps",Bukkit.getTPS());
        value.put("running_ms",runningMillis+(active?System.currentTimeMillis()-runStart:0));
        if(job!=null){Map<String,Object> j=new LinkedHashMap<>();j.put("done",job.done);j.put("total",job.total);
            j.put("active",active);j.put("inflight",inflight);j.put("bounds",job.bounds);
            j.put("verified_prefix",verifyCursor);j.put("verify_target",verifyTarget);value.put("job",j);}
        return value;
    }
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!sender.hasPermission("ark.jurassic.admin")) return true;
        try {
            if(args.length==0) return false;
            switch(args[0]) {
                case "open" -> open();
                case "status" -> {sender.sendMessage(json.toJson(status()));return true;}
                case "pause" -> pause();
                case "resume" -> resume();
                case "generate" -> {
                    if(world==null) throw new IllegalStateException("Use /jurassic open first");
                    if(args.length!=2 || !(args[1].equals("reference") || args[1].equals("all"))) return false;
                    if(inflight || job!=null && job.done<job.total) throw new IllegalStateException("An unfinished job already exists; pause/resume it");
                    Job created=json.fromJson(json.toJson(binding()),Job.class);
                    created.bounds=args[1].equals("reference")?TerrainData.REFERENCE.clone():new int[]{-1024,-1024,1023,1023};
                    created.total=(created.bounds[2]-created.bounds[0]+1)*(created.bounds[3]-created.bounds[1]+1);
                    validateJob(created);job=created;checkpoint();resume();
                }
                case "probe" -> {
                    if(world==null || args.length!=3) return false;
                    int x=Integer.parseInt(args[1]),z=Integer.parseInt(args[2]);
                    if(Math.abs(x)>=16384 || Math.abs(z)>=16384) throw new IllegalArgumentException("Probe outside border");
                    int y=generator.surface(x,z,world.getMinHeight(),world.getMaxHeight());
                    if(!world.isChunkGenerated(Math.floorDiv(x,16),Math.floorDiv(z,16))) throw new IllegalStateException("Probe chunk has not been generated");
                    sender.sendMessage(json.toJson(Map.of("x",x,"z",z,"height",y,"ground",world.getBlockAt(x,y,z).getType().name(),
                            "above",world.getBlockAt(x,y+1,z).getType().name(),"biome",world.getBiome(x,y,z).toString())));
                    return true;
                }
                case "block" -> {
                    if(world==null || args.length!=4) return false;
                    int x=Integer.parseInt(args[1]),y=Integer.parseInt(args[2]),z=Integer.parseInt(args[3]);
                    if(Math.abs(x)>=16384 || Math.abs(z)>=16384 || y<world.getMinHeight() || y>=world.getMaxHeight())
                        throw new IllegalArgumentException("Block outside world bounds");
                    if(!world.isChunkGenerated(Math.floorDiv(x,16),Math.floorDiv(z,16))) throw new IllegalStateException("Chunk not generated");
                    sender.sendMessage(json.toJson(Map.of("x",x,"y",y,"z",z,"block_data",world.getBlockAt(x,y,z).getBlockData().getAsString())));
                    return true;
                }
                default -> {return false;}
            }
            sender.sendMessage(json.toJson(status()));
        } catch(Exception e){sender.sendMessage("Jurassic refused: "+e.getMessage());getLogger().warning(e.toString());}
        return true;
    }
}
