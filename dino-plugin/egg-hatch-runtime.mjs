import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile, writeFile, mkdir, copyFile} from 'node:fs/promises';

const species = ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus'];
const carnivores = new Set(['trex','velociraptor','dilophosaurus']);
const dimensions = [[1.860844,2.1],[1.9375,1.45],[1.875,1.55],[2.5,2.15],[1.489667,1],[1.619054,1],[1.191365,1.15]];

export async function configureHatchProtection({directory,modelCache,report}) {
  report.limits=[
    'Two loopback Java protocol 777 clients use the normal survival use-on-block packet. Bedrock evidence is read from the native GME actor and Geyser registry.',
    process.env.DINO_EGG_WAIT_REAL==='1'?'Ordinary ground uses a due-date fixture. The seven beginner-area nests wait their actual 20 minutes.':'After recording the real 20-minute deadline, the fixture makes each nest due so the native hatch runs immediately.',
  ];
  report.artifacts = {};
  for (const name of ['worldedit-bukkit-7.4.6.jar','worldguard-bukkit-7.0.21.jar','GriefPrevention.jar']) {
    const target=directory+'/plugins/'+name;
    await copyFile(name.startsWith('worldguard')?process.env.DINO_WORLDGUARD_JAR:modelCache+'/plugins/'+name,target);
  }
  for(const name of ['paper.jar','plugins/ArkDinos.jar','plugins/worldguard-bukkit-7.0.21.jar','plugins/worldedit-bukkit-7.4.6.jar','plugins/GriefPrevention.jar']) {
    report.artifacts[name]=createHash('sha256').update(await readFile(directory+'/'+name)).digest('hex');
  }
  const path=directory+'/plugins/WorldGuard/worlds/ark';
  await mkdir(path,{recursive:true});
  const region=(x1,x2,z1,z2,priority,flags)=>({type:'cuboid',min:{x:x1,y:-64,z:z1},max:{x:x2,y:319,z:z2},priority,owners:{},members:{},flags:{passthrough:'allow',...flags}});
  const deny={'deny-spawn':['minecraft:ravager']};
  await writeFile(path+'/regions.yml',JSON.stringify({regions:{
    adventure_beginner:region(-16,32,7,20,5,{...deny,'mob-damage':'deny',pvp:'deny'}),
    second_deny:region(17,22,14,27,8,deny),
    parent_barrier:region(50,55,50,55,8,deny),
    inherited_barrier:{...region(23,24,14,19,8,{}),parent:'parent_barrier'},
    all_mobs:region(25,28,14,19,10,{'mob-spawning':'deny'}),
  }}));
}

export async function runHatchChecks({alice,bob,command,fields,check,eventually,delay,report}) {
  const now=()=>Date.now();
  const nest=async(x,z)=>fields(await command(`carefixture nest ark ${x} 4 ${z}`));
  const ready=async(x,z)=>fields(await command(`carefixture ready ark ${x} 4 ${z}`));
  const hatched=async(name)=>fields(await command(`carefixture hatched ${name}`));
  async function feedback(x,z,words) {
    await command(`tp ${alice.name} ${x+.5} 4 ${z+1.8}`);await delay(200);
    const first=alice.packets.length;alice.useOn(x,4,z);
    await eventually(()=>alice.packets.slice(first).some(packet=>Buffer.from(packet.hex,'hex').includes(Buffer.from(words))),
      'No native egg status containing '+words,3000);
  }
  async function place(name,x,z,expect=true) {
    await command(`tp ${alice.name} ${x+.5} 4 ${z+1.8}`);
    await command(`carefixture egg ${alice.name} ${name}`);await delay(200);
    alice.useOn(x,3,z);
    if(!expect){await delay(350);assert.equal((await nest(x,z)).nest,'false');return;}
    await eventually(async()=>(await nest(x,z)).species===name,`Survival packet failed to place ${name} at ${x} ${z}`);
    const saved=await nest(x,z);
    assert.equal(saved.owner,alice.uuid);assert.equal(saved.block,'SNIFFER_EGG');assert.equal(saved.duration,'1200000');
    assert.ok(Number(saved.hatchAt)>now()+1190000 && Number(saved.hatchAt)<=now()+1200100);
    const inv=fields(await command('carefixture inventory '+alice.name));assert.equal(inv.eggs,'0');
    return saved;
  }
  async function model(name,x,z,spawnBefore) {
    let baby;
    await eventually(async()=>{baby=await hatched(name);return baby.count==='1'&&baby.model==='ark_'+name&&baby.bedrock==='true'&&baby.bridge==='1';},'No native model for '+name);
    const i=species.indexOf(name),carrier=carnivores.has(name)?'RAVAGER':'SNIFFER';
    const [back,adultScale]=dimensions[i];
    const bornScale=.45;
    assert.equal(baby.owner,alice.uuid);assert.equal(baby.species,name);assert.equal(baby.persistent,'true');assert.equal(baby.tagged,'true');
    assert.equal(baby.permit,'false');assert.equal(baby.type,carrier);assert.equal(baby.growthDuration,'900000');
    assert.ok(Number(baby.adultAt)>=spawnBefore+899000 && Number(baby.adultAt)<=now()+900100);
    assert.equal(baby.beforeScale,'0.45');assert.equal(baby.reason,'EGG');
    assert.ok(Math.abs(Number(baby.scale)-back*adultScale/(carrier==='RAVAGER'?2.2625:2.09375)*bornScale)<.008,`bad carrier scale ${name}: ${baby.scale}`);
    assert.ok(Math.abs(Number(baby.render)-adultScale*bornScale)<.008,`bad render scale ${name}: ${baby.render}`);
    assert.equal((await nest(x,z)).block,'AIR');
    assert.equal(fields(await command(`carefixture placed-visual ark ${x} 4 ${z}`)).actors,'0');
    return baby;
  }
  async function removeBabies(){await command('kill @e[tag=care_hatch]');await delay(1150);}
  const baseline=process.env.DINO_EGG_HATCH_BASELINE==='1';
  if(baseline) {
    await check('Original WorldGuard blocks a ready owned Dilophosaurus in the beginner region on clear ground',async()=>{
      await place('dilophosaurus',0,10);
      assert.equal((await command('carefixture hatch-space ark 0 4 10 dilophosaurus')).trim(),'clear=true');
      const saved=await ready(0,10);assert.equal(saved.block,'SNIFFER_EGG');assert.equal(saved.nest,'true');assert.equal((await hatched('dilophosaurus')).count,'0');return saved;
    });
    return;
  }
  for(const [area,z] of [['ordinary ground',0],['beginner area',10]]) {
    await check('Seven owned species hatch on '+area,async()=>{
      const nests=[],babies=[];
      // Allow five blocks between the largest newborn's resting envelope and its neighbour's shell.
      const xs=species.map((_,i)=>i*5-12);
      for(const [i,name] of species.entries())nests.push(await place(name,xs[i],z));
      for(const [i,name] of species.entries()){
        await eventually(async()=>{
          const visual=fields(await command(`carefixture placed-visual ark ${xs[i]} 4 ${z}`));
          return visual.model==='ark_egg_'+name && visual.bridge==='1';
        },'Native egg display absent for '+name);
        assert.equal((await command(`carefixture hatch-space ark ${xs[i]} 4 ${z} ${name}`)).trim(),'clear=true');
      }
      const waitReal=process.env.DINO_EGG_WAIT_REAL==='1' && z===10;
      const due=waitReal?Math.min(...nests.map(n=>Number(n.hatchAt))):now();
      if(waitReal){
        await delay(Math.max(0,due-now()-5000));
        for(const [i,name] of species.entries())assert.equal((await nest(xs[i],z)).species,name);
        await eventually(async()=>(await nest(xs.at(-1),z)).nest==='false','Scheduled hatch did not finish',30000);
      }
      for(const [i,name] of species.entries()){
        if(!waitReal)assert.equal((await ready(xs[i],z)).nest,'false','Stored nest still present for '+name);
        babies.push(await model(name,xs[i],z,due));
      }
      await removeBabies();return {nests,babies,realClock:waitReal};
    });
  }
  await check('Ordinary and falsely tagged Ravagers still follow the regional denial',async()=>{
    for(const nbt of ['{Tags:["fixture_plain"]}',`{Tags:["fixture_forged","ark_dino","ark_dino_tamed"],BukkitValues:{"arkdinos:species":"dilophosaurus","arkdinos:owner":"${alice.uuid}","arkdinos:adult-at":9999999999999L,"arkdinos-owned-egg-hatch":1b}}`]){
      await command('summon minecraft:ravager 0 4 10 '+nbt);
    }
    assert.match(await command('execute unless entity @e[tag=fixture_plain] unless entity @e[tag=fixture_forged]'),/Test passed/i);
    assert.match(await command('carefixture unowned-egg-spawn '+alice.name+' 0 4 10'),/spawned=false/);
  });
  await check('An independent region, mob-spawning denial and another Bukkit listener each preserve the nest',async()=>{
    for(const [x,z] of [[19,18],[19,24],[23,18],[26,18]]) {
      await place('dilophosaurus',x,z);assert.equal((await ready(x,z)).nest,'true');
      await feedback(x,z,'Schlupf hier verhindert');
    }
    await place('dilophosaurus',4,8);await command('carefixture deny-spawn true');
    assert.equal((await ready(4,8)).nest,'true');
    await command('carefixture deny-spawn false');assert.equal((await ready(4,8)).nest,'false');
    await removeBabies();
  });
  await check('Global deny-spawn continues to apply inside the beginner region',async()=>{
    await command('region flag -w ark __global__ deny-spawn ravager');
    await place('dilophosaurus',1,11);assert.equal((await ready(1,11)).nest,'true');
    await command('region flag -w ark __global__ deny-spawn');
    await eventually(async()=>(await nest(1,11)).nest==='false','Nest did not resume after the global restriction was cleared');
    await removeBabies();
  });
  await check('Solid obstruction retains the ready nest and clearing it allows one hatch',async()=>{
    await place('dilophosaurus',8,9);await feedback(8,9,'s Brutzeit.');await command('setblock 8 5 9 stone');
    assert.equal((await ready(8,9)).nest,'true');assert.equal((await command('carefixture hatch-space ark 8 4 9 dilophosaurus')).trim(),'clear=false');
    await feedback(8,9,'braucht freien Platz');
    await command('setblock 8 5 9 air');await eventually(async()=>(await nest(8,9)).nest==='false','Eligible nest did not retry');
    assert.equal((await hatched('dilophosaurus')).count,'1');await removeBabies();
  });
  await check('Native building denial and the Mosasaurus sea item leave the inventory intact',async()=>{
    await command('carefixture deny-place true');await place('trex',6,9,false);await command('carefixture deny-place false');
    assert.equal(fields(await command('carefixture inventory '+alice.name)).eggs,'1');
    await place('mosasaurus',6,9,false);assert.equal(fields(await command('carefixture inventory '+alice.name)).eggs,'1');
    assert.match(await command(`carefixture gp-claim ${bob.name} 20 31 0 6`),/claimCreated=true/);
    await place('dilophosaurus',24,3,false);assert.equal(fields(await command('carefixture inventory '+alice.name)).eggs,'1');
  });
}
