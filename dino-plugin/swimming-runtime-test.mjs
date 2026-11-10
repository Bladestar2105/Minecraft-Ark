#!/usr/bin/env node
// Disposable native swimming fixture. Run after scripts/build_plugins.py with JAVA_HOME (JDK25).
// Set DINO_PAPER_JAR and DINO_PAPER_CACHE to a cached Paper26.3 server; DINO_SWIM_BASELINE=1 characterizes the old sink.
// DINO_PLUGIN_JAR, DINO_DEPENDENCIES and DINO_SWIMMING_OUTPUT override the build jar, dependency directory and reports.
// DINO_SWIM_ONLY=mounted,dismount,roof,exclusions,lifecycle selects independent scenarios for debugging; omit for every scenario.
// No existing world is opened; direct protocol777 inputs do not establish Bedrock/console acceptance.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, copyFile, readFile, symlink } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = process.env.DINO_PAPER_CACHE ?? `${root}artifacts/renewal-20261103/server`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${root}artifacts/renewal-20261103/paper-26.3-236.jar`;
const output=process.env.DINO_SWIMMING_OUTPUT ?? `${plugin}build`;
const dependencies=process.env.DINO_DEPENDENCIES ?? `${root}.deps`;
const selected=new Set((process.env.DINO_SWIM_ONLY ?? '').split(',').filter(Boolean));
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/swimming-smoke-`);
const report = {startedAt:new Date().toISOString(),fixture:directory,selectedScenarios:[...selected],checks:[],limits:[
  'Direct protocol777 Java client exercises native entity physics and input; no official/Bedrock/PS5 client.',
  'No rendering stack; adult carrier scale is applied directly to exercise real collision and eye height.',
  'Mounted fixture reserves ArkDinos riding map before native addPassenger; actual protocol input controls movement.',
]};
const reportPath=`${output}/swimming-runtime-report.json`;
const clients=[];let server,log='';
const vi = value => { const out = []; do { let byte = value & 127; value >>>= 7; if (value) byte |= 128; out.push(byte); } while (value); return Buffer.from(out); };
const readVi = (buffer, start = 0) => { let value = 0; for (let n = 0; n < 5; n++) { if (start + n >= buffer.length) return null; const byte = buffer[start + n]; value |= (byte & 127) << (7 * n); if (!(byte & 128)) return { value, end: start + n + 1 }; } throw Error('Invalid VarInt'); };
const string = value => { const bytes = Buffer.from(value); return Buffer.concat([vi(bytes.length), bytes]); };
const uuidString = raw => { const s = raw.toString('hex'); return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`; };
async function freePort() { const listener = net.createServer(); listener.listen(0, '127.0.0.1'); await once(listener, 'listening'); const port = listener.address().port; await new Promise(resolve => listener.close(resolve)); return port; }
const port = await freePort(), rconPort = await freePort(), password = crypto.randomBytes(24).toString('hex');

// This minimal client only implements the pinned offline protocol's handshake, acknowledgements and controls.
class Client extends EventEmitter {
  constructor(name) {
    super(); this.name = name; this.phase = 'login'; this.buffer = Buffer.alloc(0); this.compression = -1; this.entities = new Map();
    this.socket = net.createConnection({ host: '127.0.0.1', port });
    this.socket.on('connect', () => { const p = Buffer.alloc(2); p.writeUInt16BE(port); this.send(0, Buffer.concat([vi(777), string('localhost'), p, vi(2)])); this.send(0, Buffer.concat([string(name), crypto.randomBytes(16)])); });
    this.socket.on('error', error => { this.failure = error; });
    this.socket.on('close', () => { clearInterval(this.tick); this.closed = true; });
    this.socket.on('data', chunk => {
      try {
        this.buffer = Buffer.concat([this.buffer, chunk]);
        while (this.buffer.length) {
          const length = readVi(this.buffer); if (!length || this.buffer.length < length.end + length.value) break;
          let body = this.buffer.subarray(length.end, length.end + length.value); this.buffer = this.buffer.subarray(length.end + length.value);
          if (this.compression >= 0) { const size = readVi(body); body = size.value ? zlib.inflateSync(body.subarray(size.end)) : body.subarray(size.end); }
          const id = readVi(body); this.receive(id.value, body.subarray(id.end));
        }
      } catch (error) { this.failure = error; this.socket.destroy(); }
    });
  }
  send(id, body = Buffer.alloc(0)) {
    if (this.closed) return;
    let packet = Buffer.concat([vi(id), body]);
    if (this.compression >= 0) packet = packet.length >= this.compression ? Buffer.concat([vi(packet.length), zlib.deflateSync(packet)]) : Buffer.concat([vi(0), packet]);
    this.socket.write(Buffer.concat([vi(packet.length), packet]));
  }
  receive(id, body) {
    if (this.phase === 'login') {
      if (id === 0) this.failure = Error(`Login disconnect: ${body.toString('hex')}`);
      if (id === 3) this.compression = readVi(body).value;
      if (id === 2) { this.uuid = uuidString(body.subarray(0, 16)); this.send(3); this.phase = 'configuration'; this.send(0, Buffer.concat([string('en_us'), Buffer.from([3]), vi(0), Buffer.from([1, 127]), vi(1), Buffer.from([0, 1]), vi(2)])); }
    } else if (this.phase === 'configuration') {
      if (id === 4) this.send(4, body);
      if (id === 5) this.send(5, body);
      if (id === 15) this.send(7, vi(0));
      if (id === 20) this.send(9);
      if (id === 3) { this.send(3); this.phase = 'play'; this.tick = setInterval(() => { this.send(0x21, Buffer.from([1])); this.send(0x0d); }, 50); }
    } else {
      if (id === 0x2d) this.send(0x1c, body);
      if (id === 0x49) { const token = readVi(body); this.send(0, Buffer.concat([vi(token.value), body.subarray(token.end, token.end + 24), body.subarray(token.end + 48, token.end + 56)])); this.send(0x2c); }
      if (id === 0x32) { this.send(0x2c); this.joined = true; }
      if (id === 0x01) { const entity = readVi(body); this.entities.set(uuidString(body.subarray(entity.end, entity.end + 16)), entity.value); }
      if (id === 0x20) this.failure = Error(`Play disconnect: ${body.toString('hex')}`);
    }
  }
  input(flags) { this.send(0x2b, Buffer.from([flags])); }
  look(yaw, pitch = 0) { const body = Buffer.alloc(9); body.writeFloatBE(yaw); body.writeFloatBE(pitch, 4); body[8] = 1; this.send(0x20, body); }
  interact(id, sneak = false) { this.send(0x1a, Buffer.concat([vi(id), vi(0), Buffer.from([0, sneak ? 1 : 0])])); }
  useItem() { this.send(0x43,Buffer.concat([vi(0),vi(1),Buffer.alloc(8)])); }
  close() { clearInterval(this.tick); this.socket.destroy(); }
}
function rconPacket(id, type, value) { const text = Buffer.from(value); const out = Buffer.alloc(text.length + 14); out.writeInt32LE(text.length + 10); out.writeInt32LE(id, 4); out.writeInt32LE(type, 8); text.copy(out, 12); return out; }
async function command(text) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: '127.0.0.1', port: rconPort }); let buffer = Buffer.alloc(0);
    const timeout = setTimeout(() => finish(Error(`RCON timeout: ${text}`)), 10000);
    function finish(error, result) { clearTimeout(timeout); socket.destroy(); if (error) reject(error); else { const response = result.replace(/\u00a7[0-9a-fklmnorx]/gi, ''); (report.commands ??= []).push({ command: text, response }); resolve(response); } }
    socket.on('error', error => finish(error));
    socket.on('connect', () => socket.write(rconPacket(1, 3, password)));
    socket.on('data', chunk => { buffer = Buffer.concat([buffer, chunk]); while (buffer.length >= 4 && buffer.length >= buffer.readInt32LE(0) + 4) { const size = buffer.readInt32LE(0) + 4, id = buffer.readInt32LE(4), type = buffer.readInt32LE(8), value = buffer.subarray(12, size - 2).toString(); buffer = buffer.subarray(size); if (id === -1) return finish(Error('RCON authentication failed')); if (id === 1 && type === 2) socket.write(rconPacket(2, 2, text)); if (id === 2) return finish(null, value); } });
  });
}
async function eventually(test, message, timeout = 15000) { const deadline = Date.now() + timeout; while (Date.now() < deadline) { for (const client of clients) if (client.failure) throw client.failure; if (await test()) return; await delay(100); } throw Error(message); }
async function check(name, action) { try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }
async function scenario(group,name,action){if(selected.size&&!selected.has(group)){report.checks.push({name,status:'skipped',group});return;}await check(name,action);}
const fields=text=>Object.fromEntries(text.trim().split(';').map(v=>{const i=v.indexOf('=');return[v.slice(0,i),v.slice(i+1)];}));
const inspect=async tag=>fields(await command(`swimfixture inspect ${tag}`));
async function start(){
 const offset=log.length;
 server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});
 (report.serverPids??=[]).push(server.pid);server.stdout.on('data',b=>log+=b);server.stderr.on('data',b=>log+=b);
 await eventually(()=>log.slice(offset).includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-5000))),'Paper swimming fixture boot failed',90000);
}
async function stop(){clients.forEach(c=>c.close());await command('save-all flush');server.stdin.write('stop\n');await once(server,'exit');}

const baseline=process.env.DINO_SWIM_BASELINE==='1';
const offline='00000000-0000-0000-0000-000000000001';
const names=['triceratops','stegosaurus','brachiosaurus','trex','velociraptor','dilophosaurus','pteranodon'];
const scales={triceratops:1.9375*1.45/2.09375,stegosaurus:1.875*1.55/2.09375,brachiosaurus:2.5*2.15/2.09375,trex:1.860844*2.1/2.2625,velociraptor:1.489667/2.2625,dilophosaurus:1.191365*1.15/2.2625,pteranodon:1.619054/2.09375};
async function spawnSwimmer(tag,species,x,z,{mode='stay',owner=offline,scale=1,baby=false,height=7}={}) {
 const type=['trex','velociraptor','dilophosaurus'].includes(species)?'ravager':'sniffer';
 const own=mode==='wild'?'':`,"arkdinos:owner":"${owner}","arkdinos:stay":${mode==='stay'?1:0}b${mode==='free'?',"arkdinos:free-wander":1b':''}`;
 const growth=baby?',"arkdinos:adult-at":9999999999999L,"arkdinos:growth-duration":900000L':'';
 await command(`summon ${type} ${x} ${height} ${z} {Tags:["${tag}","ark_dino","ark_dino_${mode==='wild'?'wild':'tamed'}"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${species}","arkdinos:ptera-ground-until":9999999999999L${own}${growth}}}`);
 await command(`swimfixture scale ${tag} ${scale}`);
 // Spawn restoration is deferred by ArkDinos; mounting earlier would be ejected by that restore.
 await eventually(async()=> (await inspect(tag)).ai==='true',`${tag} was not restored`);
 return inspect(tag);
}
try {
 for(const name of ['libraries','versions','cache'])await symlink(`${cache}/${name}`,`${directory}/${name}`);
 await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});await copyFile(paperJar,`${directory}/paper.jar`);
 await copyFile(process.env.DINO_PLUGIN_JAR ?? `${plugin}build/ArkDinos-1.2.0.jar`,`${directory}/plugins/ArkDinos.jar`);
 report.pluginSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
 await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark_model_test\nprototype-worlds:\n  - ark_model_test\nwild-spawning:\n  enabled: false\n');
 await mkdir(`${directory}/fixture-classes`);
 execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${dependencies}/*`,'-d',`${directory}/fixture-classes`,`${plugin}src/swimming/java/de/ark/dinos/SwimFixture.java`]);
 execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/SwimFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/swimming/resources`,'.']);
 await writeFile(`${directory}/eula.txt`,'eula=true\n');
 await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark_model_test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=4\nsimulation-distance=4\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
 await start();await command('whitelist off');await command('gamerule minecraft:spawn_mobs false');await command('difficulty normal');await command('setworldspawn 0 20 0');
 const alice=new Client('SwimOwner');clients.push(alice);await eventually(()=>alice.joined,'Swim client did not join',30000);await command('gamemode creative @a');await command('tp @a 0 20 0');
 await eventually(async()=>/Successfully filled|No blocks were filled/.test(await command('fill -24 4 -24 24 15 24 water')),'Pool chunks did not load');
 await command('fill -25 3 -25 25 3 25 stone');
 if(baseline) {
  await check('Baseline reproduces submerged owner-offline Stay Sniffer losing air',async()=>{
   const before=await spawnSwimmer('baseline_sink','triceratops',0,0);let after=before;
   for(let i=0;i<18;i++){await delay(1000);after=await inspect('baseline_sink');if(Number(after.health)<Number(before.health))break;}
   assert.ok(Number(after.eye)<15.9,'Baseline unexpectedly reached surface');assert.ok(Number(after.air)<50||Number(after.health)<Number(before.health),'Baseline retained air and health');return {before,after};
  });
 } else {
  await scenario('unridden','Seven land species float in wild, Stay, Free and owner-offline Follow modes for sixty seconds at adult carrier scales',async()=>{
   const before={},after={};let index=0;
   for(const mode of ['wild','stay','free','follow'])for(const [i,name]of names.entries()){const tag=`sw_${mode}_${name}`;before[tag]=await spawnSwimmer(tag,name,-18+i*6,-15+Math.floor(index/7)*10,{mode,scale:scales[name]});index++;}
   await eventually(async()=>{for(const tag of Object.keys(before)){const s=await inspect(tag);if(Number(s.eye)<15.85)return false;}return true;},'All species must reach the actual water surface',15000);
   for(let i=0;i<12;i++)await delay(5000);
   for(const tag of Object.keys(before)){after[tag]=await inspect(tag);assert.equal(after[tag].health,before[tag].health,`${tag} lost health`);assert.ok(Number(after[tag].eye)>=15.85,`${tag} submerged`);assert.ok(Number(after[tag].air)>=Number(after[tag].maxAir)-2,`${tag} lost air`);}
   await command('kill @e[tag=ark_dino]');return {before,after};
  });
  await scenario('juvenile','A growing small Sniffer uses its current native eye height',async()=>{
   const before=await spawnSwimmer('sw_baby','triceratops',0,0,{baby:true,scale:.28});await eventually(async()=>Number((await inspect('sw_baby')).eye)>=15.85,'Baby remained submerged',15000);await delay(3000);const after=await inspect('sw_baby');assert.equal(after.health,before.health);assert.ok(Number(after.air)>=Number(after.maxAir)-2);await command('kill @e[tag=sw_baby]');return {before,after};
  });
  await scenario('mounted','All seven mounted species recover, preserve health and accept real forward input in deep water',async()=>{
   const samples={};await command(`gamemode survival ${alice.name}`);alice.input(0);alice.look(0,0);
   for(const name of names){
    const tag=`sw_mount_${name}`;const before=await spawnSwimmer(tag,name,0,-12,{owner:alice.uuid,scale:scales[name]});await command(`swimfixture occupy ${tag} ${alice.name}`);await delay(100);assert.equal((await inspect(tag)).passengers,'1');
    await eventually(async()=>Number((await inspect(tag)).eye)>=15.85,`${name} mounted stayed underwater`,15000);const surfaced=await inspect(tag);assert.equal(surfaced.health,before.health);
    alice.input(1);await delay(200);const start=await inspect(tag);await delay(1500);const moved=await inspect(tag);alice.input(0);await delay(200);const speed=Math.hypot(Number(moved.x)-Number(start.x),Number(moved.z)-Number(start.z))*20/(Number(moved.tick)-Number(start.tick));assert.ok(speed>.5,`${name} failed water steering`);assert.ok(Number(moved.eye)>=15.8);assert.equal(moved.health,before.health);
    if(name==='pteranodon'){alice.input(16);await delay(1000);assert.equal((await inspect(tag)).gravity,'true','Underwater Ptera incorrectly took off');alice.input(0);}
    samples[name]={before,surfaced,moved,blocksPerSecond:speed};await command(`ride ${alice.name} dismount`);await command(`gamemode creative ${alice.name}`);await command(`tp ${alice.name} 0 20 0`);await command(`kill @e[tag=${tag}]`);await command(`gamemode survival ${alice.name}`);
   }
   await command('gamemode creative @a');return samples;
  });
  await scenario('dismount','Submerged Ravager keeps its rider but native Sneak and game-mode cleanup still dismount',async()=>{
   const samples={};
   for(const method of ['sneak','gamemode']){
    await command(`gamemode survival ${alice.name}`);alice.input(0);await command(`tp ${alice.name} 0 20 0`);
    const tag=`sw_exit_${method}`;await spawnSwimmer(tag,'trex',0,0,{owner:alice.uuid,scale:scales.trex});await command(`swimfixture occupy ${tag} ${alice.name}`);await delay(250);const before=await inspect(tag);assert.equal(before.passengers,'1');assert.equal(before.riderUnderwater,'true','Exit must be exercised while the native underwater cancellation is active');
    if(method==='sneak')alice.input(32);else await command(`gamemode creative ${alice.name}`);
    await eventually(async()=> (await inspect(tag)).passengers==='0',`${method} failed to dismount the submerged Ravager`);const after=await inspect(tag);alice.input(0);assert.equal(fields(await command('swimfixture count')).riding,'0');samples[method]={before,after};await command(`gamemode creative ${alice.name}`);await command(`tp ${alice.name} 0 20 0`);await command(`kill @e[tag=${tag}]`);
   }
   return samples;
  });
  await scenario('roof','Water ceiling respects real rider collision without granting drowning immunity',async()=>{
   await command('fill -4 17 -4 4 17 4 stone');await command(`gamemode survival ${alice.name}`);const before=await spawnSwimmer('sw_roof','triceratops',0,0,{owner:alice.uuid,scale:scales.triceratops});await command(`swimfixture occupy sw_roof ${alice.name}`);await delay(4000);const after=await inspect('sw_roof');assert.ok(Number(after.eye)<15.85,'Rider roof should stop surface recovery');assert.ok(Number(after.riderTop)<=17.00001,'Rider crossed the solid ceiling');assert.ok(Number(after.air)<Number(after.maxAir),'Obstructed underwater animal should still consume air');
   await command(`ride ${alice.name} dismount`);await command('gamemode creative @a');await command('tp @a 0 20 0');await command('fill -4 17 -4 4 17 4 air');await command('kill @e[tag=sw_roof]');return {before,after};
  });
  await scenario('exclusions','Water plants and waterlogged blocks count as water; lava, Mosasaurus and ordinary mobs are excluded',async()=>{
   const waterSamples={};
   for(const [material,wet,expected]of [['WATER','',true],['BUBBLE_COLUMN','',true],['KELP','',true],['SEAGRASS','',true],['OAK_SLAB','true',true],['OAK_SLAB','false',false],['LAVA','',false],['AIR','',false]]){
    const result=fields(await command(`swimfixture water-material ${material} ${wet}`));assert.equal(result.water,String(expected));waterSamples[`${material}/${wet}`]=result.water;
   }
   const eyeSamples={};for(const [level,height,expected]of [[0,.88,true],[0,.9,false],[5,.32,true],[5,.34,false]]){
    const result=fields(await command(`swimfixture water-material WATER ${level} ${height}`));assert.equal(result.eyeWater,String(expected));eyeSamples[`${level}/${height}`]=result.eyeWater;
   }
   await command('summon dolphin 0 9 0 {Tags:["sw_mosa","ark_dino","ark_dino_wild"],PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"mosasaurus"}}');
   await command('summon sniffer 8 7 0 {Tags:["sw_ordinary"],PersistenceRequired:1b}');await delay(300);assert.equal(fields(await command('swimfixture count')).count,'0');
   await command('kill @e[tag=sw_mosa]');await command('kill @e[tag=sw_ordinary]');
   await command('fill -4 22 -4 4 22 4 stone');await command('fill -4 23 -4 4 26 4 lava');
   const before=await spawnSwimmer('sw_lava','triceratops',0,0,{height:24});await delay(600);const after=await inspect('sw_lava');assert.equal(after.lava,'true');assert.ok(Number(after.health)<Number(before.health),'Lava must continue damaging land dinosaurs');
   await command('kill @e[tag=sw_lava]');await command('fill -4 22 -4 4 26 4 air');return {waterSamples,eyeSamples,before,after};
  });
  await scenario('lifecycle','Unloaded water probes do not load chunks; death, unload and shutdown remove swimmer state',async()=>{
   await spawnSwimmer('sw_cleanup','triceratops',0,0);assert.equal(fields(await command('swimfixture count')).count,'1');const boundary=fields(await command('swimfixture boundary sw_cleanup'));assert.equal(boundary.loaded,'false');assert.equal(boundary.before,boundary.after);assert.equal(boundary.result,'OptionalInt.empty');
   await command('swimfixture unload sw_cleanup');assert.equal(fields(await command('swimfixture count')).count,'0');await command('swimfixture restore sw_cleanup');assert.equal(fields(await command('swimfixture count')).count,'1');await command('kill @e[tag=sw_cleanup]');assert.equal(fields(await command('swimfixture count')).count,'0');
   await spawnSwimmer('sw_disable','triceratops',0,0);await command('swimfixture disable');assert.equal(fields(await command('swimfixture count')).count,'0');return boundary;
  });
 }
 assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable/);
}catch(error){console.error(error.stack);process.exitCode=1;}
finally{
 clients.forEach(c=>c.close());if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
 report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
