#!/usr/bin/env node
// Disposable loopback Paper fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, copyFile, readFile, symlink, cp, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = process.env.DINO_PAPER_CACHE ?? `${root}artifacts/renewal-20261103/server`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${root}artifacts/renewal-20261103/paper-26.3-236.jar`;
const output=process.env.DINO_CONTROLS_OUTPUT ?? `${plugin}build`;
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/controls-smoke-`);
const report = {startedAt:new Date().toISOString(),fixture:directory,checks:[],limits:[
  'Direct protocol777 Java clients exercise real entity interactions, inventory ItemStacks and persistence; no official/Bedrock/PS5 client.',
  'Inventory clicks and cancelled interactions use dispatched native Paper events against actual inventories.',
  'Juvenile deadlines advance through fixture-only PDC edits; real server restart verifies mode and former-owner persistence.',
]};
const reportPath=`${output}/controls-runtime-report.json`;
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
const fields=text=>Object.fromEntries(text.trim().split(';').map(v=>{const i=v.indexOf('=');return[v.slice(0,i),v.slice(i+1)];}));
const inspect=async tag=>fields(await command(`controlsfixture inspect ${tag}`));
const inventory=async client=>fields(await command(`controlsfixture inventory ${client.name}`));
const food=(client,type,amount)=>command(`controlsfixture food ${client.name} ${type} ${amount}`);
async function spawnDino(tag,species,x,owner){const type=['trex','velociraptor','dilophosaurus'].includes(species)?'ravager':'sniffer';await command(`summon ${type} ${x} 4 0 {Tags:["${tag}","ark_dino","ark_dino_tamed"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${species}","arkdinos:owner":"${owner}","arkdinos:stay":1b,"arkdinos:growth-duration":300000L}}`);await delay(200);}
async function open(client,tag){const mob=await inspect(tag);await command(`tp ${client.name} ${mob.x} ${mob.y} ${Number(mob.z)+3}`);await food(client,'AIR',0);client.input(32);await delay(100);client.interact(Number(mob.id),true);await delay(200);}
async function menuClick(client,slot,type='LEFT'){const result=await command(`controlsfixture click ${client.name} ${slot} ${type}`);await delay(200);return result;}
async function start(){
 const offset=log.length;
 server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});
 (report.serverPids??=[]).push(server.pid);server.stdout.on('data',b=>log+=b);server.stderr.on('data',b=>log+=b);
 await eventually(()=>log.slice(offset).includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-5000))),'Paper controls fixture boot failed',90000);
}
async function stop(){clients.forEach(c=>c.close());await command('save-all flush');server.stdin.write('stop\n');await once(server,'exit');}
try {
 for(const name of ['libraries','versions','cache'])await symlink(`${cache}/${name}`,`${directory}/${name}`);
 await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});await copyFile(paperJar,`${directory}/paper.jar`);
 if(process.env.DINO_PTERA_MODELS==='1'||process.env.DINO_SPEED_MODELS==='1') {
  const modelCache=`${root}output/dino-model-spike-20261107/private/server`;
  for(const name of ['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar','geyser-spigot.jar','floodgate-spigot.jar','geyserutils-spigot-1.0-SNAPSHOT.jar'])await copyFile(`${modelCache}/plugins/${name}`,`${directory}/plugins/${name}`);
  await copyFile(process.env.DINO_GME_JAR??`${root}output/dino-round3-20261108/gme-race/GeyserModelEngine-1.0.9-publication-fixed.jar`,`${directory}/plugins/GeyserModelEngine-1.0.9.jar`);
  report.bridgeSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/GeyserModelEngine-1.0.9.jar`)).digest('hex');
  await copyFile(`${plugin}.deps/geyser-spigot-2.12.1-b1295.jar`,`${directory}/plugins/geyser-spigot.jar`);
  for(const name of ['BetterModel','GeyserModelEngine','Geyser-Spigot','floodgate'])await cp(`${modelCache}/plugins/${name}`,`${directory}/plugins/${name}`,{recursive:true});
  const generated=`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack`;await rm(generated,{recursive:true,force:true});await rm(`${generated}.zip`,{force:true});
  const geyserConfig=`${directory}/plugins/Geyser-Spigot/config.yml`;await writeFile(geyserConfig,(await readFile(geyserConfig,'utf8')).replace(/^port: .*/m,`port: ${await freePort()}`));
  for(const name of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus']) {
   await copyFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,`${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`);
   await cp(`${root}dino-models/models/ark_${name}`,`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}`,{recursive:true});
  }
  report.pteraModelSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/BetterModel/models/ark_pteranodon.bbmodel`)).digest('hex');
 }

 await copyFile(process.env.DINO_PLUGIN_JAR ?? `${plugin}build/ArkDinos-1.1.0.jar`,`${directory}/plugins/ArkDinos.jar`);
 report.pluginSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
 await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark_model_test\n  - ark\nprototype-worlds:\n  - ark_model_test\n');
 await mkdir(`${directory}/fixture-classes`);
 execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${plugin}.deps/*`,'-d',`${directory}/fixture-classes`,`${plugin}src/controls/java/de/ark/dinos/ControlsFixture.java`]);
 execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/ControlsFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/controls/resources`,'.']);
 await writeFile(`${directory}/eula.txt`,'eula=true\n');
 await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark_model_test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
 await start();await command('whitelist off');await command('gamerule minecraft:spawn_mobs false');await command('difficulty normal');await command('fill -16 3 -16 32 3 32 stone');
 let alice=new Client('ControlsOwner'),bob=new Client('ControlsOther');clients.push(alice,bob);await eventually(()=>alice.joined&&bob.joined,'Controls clients did not join',30000);
 await command('gamemode survival @a');await command('effect give @a resistance 9999 4 true');await command('tp @a 0 4 3');await delay(500);
 await eventually(async()=>/Successfully filled/.test(await command('fill -16 3 -16 32 3 32 stone')),'Controls fixture platform chunks did not load');
 if(process.env.DINO_SPEED_ONLY==='1') {
  await command('gamemode creative @a');await command('fill -24 3 -24 40 3 40 stone');
  const species=process.env.DINO_SPEED_MODELS==='1'?['velociraptor','brachiosaurus']:['triceratops','stegosaurus','brachiosaurus','pteranodon','trex','velociraptor','dilophosaurus'];
  const targets={brachiosaurus:[1,2],stegosaurus:[1,2.8],triceratops:[1.4,4.6],trex:[1.5,6],velociraptor:[2,7],dilophosaurus:[1.8,6],pteranodon:[1.4,3]};
  const within=(actual,target)=>assert.ok(Math.abs(actual-target)<=target*.1,`Measured ${actual}b/s differs from target ${target}b/s`);
  const samples={};const distance=(a,b)=>Math.hypot(Number(b.x)-Number(a.x),Number(b.z)-Number(a.z));const measured=(a,b)=>({before:a,after:b,blocksPerSecond:distance(a,b)*20/(Number(b.tick)-Number(a.tick))});
  await check(`${species.length} species match ground walk/run and real rider normal/sprint targets, return from sprint and stop without sliding`,async()=>{
   for(let i=0;i<species.length;i++){const name=species[i],type=['trex','velociraptor','dilophosaurus'].includes(name)?'ravager':'sniffer';await command(`summon ${type} -12 4 ${-12+i*7} {Tags:["ptera_speed_${name}","ark_dino","ark_dino_wild"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${name}","arkdinos:ptera-ground-until":9999999999999L}}`);samples[name]={};}await delay(300);
   for(const [mode,multiplier]of [['wander',.85],['followChase',1.15]]){
    for(let i=0;i<species.length;i++){const name=species[i],z=-12+i*7;await command(`tp @e[tag=ptera_speed_${name},limit=1] -12 4 ${z}`);await command(`controlsfixture ground-path ptera_speed_${name} ${process.env.DINO_SPEED_MODELS==='1'&&name==='brachiosaurus'?0:30} ${z} ${multiplier}`);}await delay(600);
    const before={};for(const name of species)before[name]=await inspect(`ptera_speed_${name}`);await delay(2000);
    for(const name of species){const after=await inspect(`ptera_speed_${name}`);samples[name][mode]=measured(before[name],after);within(samples[name][mode].blocksPerSecond,targets[name][mode==='wander'?0:1]);if(process.env.DINO_SPEED_MODELS==='1'){const expected=name==='velociraptor'?1.489667/2.2625:2.5/2.09375;assert.ok(Math.abs(Number(after.scale)-expected)<.00001,'Modeled native scale is calibrated');}}
   }
   await command('kill @e[tag=ark_dino]');await command(`gamemode survival ${alice.name}`);alice.look(0,0);alice.input(0);
   for(let i=0;i<species.length;i++){
    const name=species[i],type=['trex','velociraptor','dilophosaurus'].includes(name)?'ravager':'sniffer',tag=`ptera_speed_mount_${name}`;await command(`summon ${type} 0 4 -12 {Tags:["${tag}","ark_dino","ark_dino_tamed"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${name}","arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b,"arkdinos:ptera-ground-until":9999999999999L}}`);await delay(200);await command(`controlsfixture occupy ${tag} ${alice.name}`);
    for(const [mode,flags]of [['mountedWalk',1],['mountedSprint',65]]){alice.input(flags);await delay(400);const before=await inspect(tag);await delay(1500);const after=await inspect(tag);samples[name][mode]=measured(before,after);within(samples[name][mode].blocksPerSecond,targets[name][mode==='mountedWalk'?0:1]);alice.input(0);await delay(150);}
    assert.ok(samples[name].mountedSprint.blocksPerSecond>samples[name].mountedWalk.blocksPerSecond*1.3);
    alice.input(1);await delay(250);let before=await inspect(tag);await delay(700);let after=await inspect(tag);samples[name].releasedSprint=measured(before,after);assert.ok(Math.abs(samples[name].releasedSprint.blocksPerSecond-samples[name].mountedWalk.blocksPerSecond)<.3);
    alice.input(0);await delay(200);before=await inspect(tag);await delay(600);after=await inspect(tag);samples[name].stopped=measured(before,after);assert.ok(distance(before,after)<.05,`${name} slid after releasing all input`);await command(`ride ${alice.name} dismount`);await command('kill @e[tag=ark_dino]');
   }
   report.groundSpeeds=samples;return samples;
  });
 } else if(process.env.DINO_PTERA_ONLY==='1') {
  await command('gamemode creative @a');await command('fill -24 3 -24 40 3 40 stone');
  const ptera=async(tag,x=0,z=0,values='')=>{await command(`summon sniffer ${x} 4 ${z} {Tags:["${tag}","ark_dino","ark_dino_wild"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"pteranodon"${values}}}`);await delay(250);return inspect(tag);};
  const attempt=async tag=>fields(await command(`controlsfixture ptera-attempt ${tag}`));
  const airborne=tag=>eventually(async()=>{const s=await inspect(tag);return s.pteraFlight==='true'&&Number(s.y)>6;},`${tag} must take off`,10000);
  const landed=tag=>eventually(async()=>{const s=await inspect(tag);return s.pteraFlight==='false'&&s.gravity==='true'&&s.ground==='true';},`${tag} must land and restore gravity`,12000);
  const cleanup=()=>command('kill @e[tag=ark_dino]');
  if(process.env.DINO_PTERA_MODELS==='1') {
   await check('Exact model stack keeps full-wing clearance, autonomous flight, hit landing and bounded normal/sprint flight input on scaled carrier',async()=>{
    await ptera('ptera_modeled',0,0,`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":0b,"arkdinos:free-wander":1b`);
    await eventually(async()=>Math.abs(Number((await inspect('ptera_modeled')).scale)-1.619054/2.09375)<.00001,'Ptera visual model must calibrate native carrier scale');const before=await inspect('ptera_modeled');
    await command('setblock 4 6 0 glass_pane');assert.equal((await attempt('ptera_modeled')).flight,'false');await command('setblock 4 6 0 air');assert.equal((await attempt('ptera_modeled')).flight,'true');await airborne('ptera_modeled');const flying=await inspect('ptera_modeled');
    await command(`controlsfixture ptera-hit ptera_modeled ${alice.name} projectile`);await landed('ptera_modeled');const hit=await inspect('ptera_modeled');assert.ok(Number(hit.groundUntil)>Date.now()+50000);assert.ok(Number(hit.health)<Number(before.health));assert.equal((await attempt('ptera_modeled')).flight,'false');
    await command('controlsfixture time ptera_modeled ptera-ground-until 0');assert.equal((await attempt('ptera_modeled')).flight,'true');await airborne('ptera_modeled');
    await command(`gamemode survival ${alice.name}`);alice.input(0);await delay(100);await command(`controlsfixture occupy ptera_modeled ${alice.name}`);alice.input(16);await delay(300);const mounted=await inspect('ptera_modeled');assert.equal(mounted.passengers,'1');assert.equal(mounted.pteraFlight,'false');assert.equal(mounted.gravity,'false');alice.look(0,0);const flightInput={};for(const [mode,flags]of [['normal',1],['sprint',65]]){alice.input(flags);await delay(150);const a=await inspect('ptera_modeled');await delay(400);const b=await inspect('ptera_modeled');const speed=Math.hypot(Number(b.x)-Number(a.x),Number(b.z)-Number(a.z))*20/(Number(b.tick)-Number(a.tick));assert.ok(Math.abs(speed-7)<.3);flightInput[mode]=speed;}report.pteraFlightInput=flightInput;alice.input(0);await command(`ride ${alice.name} dismount`);await landed('ptera_modeled');const after=await inspect('ptera_modeled');assert.equal(after.uuid,before.uuid);assert.equal(after.owner,before.owner);assert.equal(after.gravity,'true');return {before,flying,hit,mounted,after};
   });
  } else {
  await check('Ptera walks at a practical pace and adult wild Ptera takes off, cruises and lands without a rider',async()=>{
   const start=await ptera('ptera_ground',0,0,',"arkdinos:adult-at":9999999999999L');const path=await command('controlsfixture ground-path ptera_ground 12 0');await delay(3000);const walked=await inspect('ptera_ground');
   await ptera('ptera_wild',12,12);const forced=await attempt('ptera_wild');await airborne('ptera_wild');await landed('ptera_wild');const flown=await inspect('ptera_wild');
   report.pteraBaseline={start,path,walked,forced,flown};assert.ok(Number(walked.horizontal)-Number(start.horizontal)>3.5,'Ground Ptera must cover3.5b in3s');assert.ok(Number(flown.maxY)>7);assert.ok(Number(flown.horizontal)>5);assert.ok(Number(flown.airTicks)>30);assert.equal(flown.health,'14.0');return report.pteraBaseline;
  });
  await cleanup();
  await check('Cancelled and zero damage keep flying; actual player and projectile hits land and impose sixty-second hold',async()=>{
   const results=[];
   for(const kind of ['cancelled','zero','melee','projectile']) {
    const tag=`ptera_hit_${kind}`;await ptera(tag);assert.equal((await attempt(tag)).flight,'true');await airborne(tag);
    const hit=await command(`controlsfixture ptera-hit ${tag} ${alice.name} ${kind}`);await delay(350);const after=await inspect(tag);
    if(kind==='cancelled'||kind==='zero'){assert.equal(after.groundUntil,'0');assert.equal(after.pteraFlight,'true');assert.match(hit,/loss=0.0/);}
    else {assert.ok(Number(after.groundUntil)>Date.now()+55000);await landed(tag);assert.equal((await attempt(tag)).flight,'false');assert.equal((await inspect(tag)).gravity,'true');}
    results.push({kind,hit,after});await cleanup();
   }
   return results;
  });
  await check('Accepted raw-fish feeding holds wild Ptera on ground; failed and cancelled feeding cannot manufacture progress',async()=>{
   await ptera('ptera_feed');await command(`gamemode survival ${alice.name}`);await food(alice,'COD',3);await command(`tp ${alice.name} 0 4 3`);
   await command('controlsfixture deny-interact true');await command(`controlsfixture interact ${alice.name} ptera_feed false`);await delay(150);assert.equal((await inspect('ptera_feed')).tameExpiry,'0');await command('controlsfixture deny-interact false');
   await command(`controlsfixture interact ${alice.name} ptera_feed false`);await delay(150);const fed=await inspect('ptera_feed');assert.equal(fed.feeds,'1');assert.ok(Number(fed.tameExpiry)>Date.now()+200000);assert.equal((await attempt('ptera_feed')).flight,'false');await delay(2000);assert.equal((await inspect('ptera_feed')).pteraFlight,'false');await command(`gamemode creative ${alice.name}`);await cleanup();return fed;
  });
  await check('Babies and Follow stay on ground; owned Free flies and Stay orders a safe landing',async()=>{
   await ptera('ptera_baby',0,0,',"arkdinos:adult-at":9999999999999L');assert.equal((await attempt('ptera_baby')).flight,'false');
   await ptera('ptera_owned',12,12,`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":0b`);await delay(100);assert.equal((await attempt('ptera_owned')).flight,'false');
   await command(`data modify entity @e[tag=ptera_owned,limit=1] BukkitValues."arkdinos:free-wander" set value 1b`);assert.equal((await attempt('ptera_owned')).flight,'true');await airborne('ptera_owned');
   await command(`gamemode survival ${alice.name}`);await command('team add ptera_stop');await command('team modify ptera_stop collisionRule never');await command('team join ptera_stop @a');await command('team join ptera_stop @e[tag=ptera_owned]');await open(alice,'ptera_owned');await menuClick(alice,2);await landed('ptera_owned');const stopped=await inspect('ptera_owned');assert.equal(stopped.stay,'1');assert.equal((await attempt('ptera_owned')).flight,'false');await delay(1000);const settled=await inspect('ptera_owned');assert.ok(Math.hypot(Number(settled.x)-Number(stopped.x),Number(settled.z)-Number(stopped.z))<.1);await command('team remove ptera_stop');await command(`gamemode creative ${alice.name}`);await cleanup();return {stopped,settled};
  });
  await check('Full-wing clearance refuses thin wing obstruction, low roof, water and unloaded chunks without adding tickets',async()=>{
   await ptera('ptera_blocked');await command('setblock 4 6 0 glass_pane');assert.equal((await attempt('ptera_blocked')).flight,'false');await command('setblock 4 6 0 air');
   await command('fill -8 8 -8 8 8 8 stone');assert.equal((await attempt('ptera_blocked')).flight,'false');await command('fill -8 8 -8 8 8 8 air');
   await command('fill -8 4 -8 8 4 8 water');assert.equal((await attempt('ptera_blocked')).flight,'false');await command('fill -8 4 -8 8 4 8 air');await delay(250);
   const edge=fields(await command('controlsfixture ptera-edge-probe ptera_blocked'));assert.equal(edge.clear,'false');assert.equal(edge.before,edge.after);assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);await cleanup();return edge;
  });
  await check('Mounting takes priority over autonomous flight and dismount restores normal landing',async()=>{
   await ptera('ptera_mount',0,0,`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":0b,"arkdinos:free-wander":1b`);assert.equal((await attempt('ptera_mount')).flight,'true');await airborne('ptera_mount');
   await command(`gamemode survival ${alice.name}`);alice.input(0);await delay(100);await command(`controlsfixture occupy ptera_mount ${alice.name}`);alice.input(16);await delay(300);const flying=await inspect('ptera_mount');assert.equal(flying.passengers,'1');assert.equal(flying.pteraFlight,'false');assert.equal(flying.gravity,'false');alice.input(0);
   await command(`ride ${alice.name} dismount`);await landed('ptera_mount');await command(`gamemode creative ${alice.name}`);await cleanup();return flying;
  });
  await check('Airborne unload safely restores gravity for wild and owned Ptera without changing identity',async()=>{
   const result=[];for(const owned of [false,true]){const tag=`ptera_unload_${owned}`;const before=await ptera(tag,0,0,owned?`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":0b,"arkdinos:free-wander":1b`:'');assert.equal((await attempt(tag)).flight,'true');await airborne(tag);await command(`controlsfixture ptera-unload ${tag}`);await landed(tag);const after=await inspect(tag);assert.equal(after.uuid,before.uuid);assert.equal(after.owner,before.owner);assert.equal(after.health,before.health);await command(`controlsfixture restore ${tag}`);result.push(after);await cleanup();}return result;
  });
  await check('Real restart during flight recovers the same wild and owned Ptera and keeps the sixty-second hit hold',async()=>{
   const wild=await ptera('ptera_restart_wild',0,0);const owned=await ptera('ptera_restart_owned',16,16,`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":0b,"arkdinos:free-wander":1b`);await attempt('ptera_restart_wild');await attempt('ptera_restart_owned');await airborne('ptera_restart_wild');await airborne('ptera_restart_owned');
   await ptera('ptera_restart_hold',24,0);await command(`controlsfixture ptera-hit ptera_restart_hold ${alice.name} melee`);const hold=await inspect('ptera_restart_hold');await stop();await start();alice=new Client('ControlsOwner');bob=new Client('ControlsOther');clients.push(alice,bob);await eventually(()=>alice.joined&&bob.joined,'Restarted Ptera clients did not join',30000);await command('gamemode creative @a');await command('tp @a 0 4 3');
   await landed('ptera_restart_wild');await landed('ptera_restart_owned');const afterWild=await inspect('ptera_restart_wild'),afterOwned=await inspect('ptera_restart_owned'),afterHold=await inspect('ptera_restart_hold');for(const [before,after] of [[wild,afterWild],[owned,afterOwned]]){assert.equal(after.uuid,before.uuid);assert.equal(after.owner,before.owner);assert.equal(after.health,before.health);assert.equal(after.gravity,'true');}assert.equal(afterHold.groundUntil,hold.groundUntil);assert.equal((await attempt('ptera_restart_hold')).flight,'false');return {afterWild,afterOwned,afterHold};
  });
  }
 } else {
 await spawnDino('control_rex','trex',0,alice.uuid);
 await check('Sneak empty-hand opens owned prototype control menu',async()=>{await open(alice,'control_rex');assert.equal((await inventory(alice)).top,'9','Owned prototype must open three-mode control menu');});
 await check('Menu blocks transfers, nonowners and cancelled native interactions',async()=>{
  for(const kind of ['SHIFT_LEFT','NUMBER_KEY','DOUBLE_CLICK','DROP'])assert.match(await menuClick(alice,0,kind),/cancelled=true/);
  assert.match(await command(`controlsfixture drag ${alice.name}`),/cancelled=true/);assert.equal((await inspect('control_rex')).stay,'1');assert.equal((await inventory(alice)).cursor,'AIR');
  await command(`controlsfixture close ${alice.name}`);await open(bob,'control_rex');assert.notEqual((await inventory(bob)).top,'9');
  await command('controlsfixture deny-interact true');await open(alice,'control_rex');assert.notEqual((await inventory(alice)).top,'9');await command('controlsfixture deny-interact false');
  await open(alice,'control_rex');await command('controlsfixture deny-click true');await menuClick(alice,0);assert.equal((await inspect('control_rex')).stay,'1');await command('controlsfixture deny-click false');await command(`controlsfixture close ${alice.name}`);
 });
 await check('Follow, stop and free wander persist distinct modes and move without owner following',async()=>{
  await open(alice,'control_rex');await menuClick(alice,0);assert.equal((await inspect('control_rex')).stay,'0');assert.equal((await inspect('control_rex')).roam,'0');
  await command(`tp ${alice.name} 0 4 14`);await eventually(async()=>Number((await inspect('control_rex')).z)>2,'Follow mode must walk toward owner');
  // Isolate retained AI input from native player collision pushes while opening the menu.
  await command('team add stop_probe');await command('team modify stop_probe collisionRule never');
  await command('team join stop_probe @a');await command('team join stop_probe @e[tag=control_rex]');
  await open(alice,'control_rex');await menuClick(alice,2);const stopped=await inspect('control_rex');assert.equal(stopped.stay,'1');assert.equal(stopped.aware,'false');assert.equal(stopped.path,'false');
  await delay(1500);const settled=await inspect('control_rex');assert.ok(Math.hypot(Number(settled.x)-Number(stopped.x),Number(settled.z)-Number(stopped.z))<.1,'Stop remains stationary');
  await command('team remove stop_probe');
  await open(alice,'control_rex');await menuClick(alice,4);assert.equal((await inspect('control_rex')).stay,'0');assert.equal((await inspect('control_rex')).roam,'1');
  const before=await inspect('control_rex');await command(`tp ${alice.name} 80 4 0`);await command(`tp ${bob.name} 80 4 4`);
  await eventually(async()=>{const m=await inspect('control_rex');return Math.hypot(Number(m.x)-Number(before.x),Number(m.z)-Number(before.z))>.5;},'Free wander must move with owner far away',12000);
  assert.ok(Math.abs(Number((await inspect('control_rex')).x))<20,'Free wander must not follow owner to x80');
 });
 await check('Free wander still defends owner; stop immediately cancels combat and path',async()=>{
  await command('summon husk 2 4 2 {Tags:["control_attacker"],NoAI:1b,PersistenceRequired:1b}');
  await open(alice,'control_rex');await menuClick(alice,4);assert.match(await command(`controlsfixture defend ${alice.name} control_attacker control_rex`),/active=true/);
  await open(alice,'control_rex');await menuClick(alice,2);assert.match(await command('controlsfixture active control_rex'),/active=false/);assert.equal((await inspect('control_rex')).path,'false');
 });
 await check('Juveniles follow and roam while riding, combat and release remain adult-only; occupied vehicles cannot release',async()=>{
  await spawnDino('control_baby','triceratops',8,alice.uuid);await command('controlsfixture time control_baby adult-at 300000');
  await open(alice,'control_baby');await menuClick(alice,0);assert.equal((await inspect('control_baby')).stay,'0');assert.equal((await inspect('control_baby')).roam,'0');alice.input(0);
  await command(`tp ${alice.name} 8 4 14`);await eventually(async()=>Number((await inspect('control_baby')).z)>2,'Juvenile must follow its owner');
  let baby=await inspect('control_baby');await command(`tp ${alice.name} ${baby.x} ${baby.y} ${Number(baby.z)+3}`);await food(alice,'AIR',0);alice.input(0);await delay(150);alice.interact(Number(baby.id));await delay(200);assert.equal((await inspect('control_baby')).passengers,'0','Juvenile riding remains blocked');
  assert.match(await command(`controlsfixture defend ${alice.name} control_attacker control_baby`),/active=false/,'Juvenile defense remains blocked');
  await open(alice,'control_baby');await menuClick(alice,4);assert.equal((await inspect('control_baby')).roam,'1');baby=await inspect('control_baby');await command(`tp ${alice.name} 24 4 26`);
  await eventually(async()=>{const m=await inspect('control_baby');return Math.hypot(Number(m.x)-Number(baby.x),Number(m.z)-Number(baby.z))>.5;},'Juvenile must wander in free mode',12000);
  await open(alice,'control_baby');await menuClick(alice,8);assert.equal((await inspect('control_baby')).owner,alice.uuid,'Juvenile release remains blocked');await open(alice,'control_baby');await menuClick(alice,2);
  await command(`controlsfixture time control_baby adult-at 0`);await open(alice,'control_baby');await menuClick(alice,8);alice.input(0);await delay(100);assert.match(await command(`controlsfixture occupy control_baby ${alice.name}`),/mounted=true/);assert.equal((await inspect('control_baby')).passengers,'1');await menuClick(alice,2);assert.equal((await inspect('control_baby')).owner,alice.uuid);await command(`ride ${alice.name} dismount`);await command(`controlsfixture close ${alice.name}`);
 });
 await check('Wandering paths only use loaded reachable terrain without loading more chunks',async()=>{
  const probe=fields(await command('controlsfixture wander-probe control_baby'));assert.equal(probe.after,probe.before);assert.equal(probe.pathLoaded,'true');assert.ok(Number(probe.reachable)>0,'At least one solid loaded candidate is reachable');
 });
 await check('Release needs separate confirmation and rejects stale distance, world and ownership',async()=>{
  await open(alice,'control_rex');await menuClick(alice,8);assert.equal((await inspect('control_rex')).owner,alice.uuid);assert.equal((await inventory(alice)).top,'9');await menuClick(alice,6);assert.equal((await inspect('control_rex')).owner,alice.uuid);
  await open(alice,'control_rex');await menuClick(alice,8);await command(`tp ${alice.name} 80 4 0`);await menuClick(alice,2);assert.equal((await inspect('control_rex')).owner,alice.uuid);
  await command('controlsfixture create-world ark');await open(alice,'control_rex');await menuClick(alice,8);await command(`execute in minecraft:ark run tp ${alice.name} 0 4 0`);await menuClick(alice,2);assert.equal((await inspect('control_rex')).owner,alice.uuid);
  await command(`execute in minecraft:ark_model_test run tp ${alice.name} 0 4 0`);await open(alice,'control_rex');await menuClick(alice,8);await command(`data modify entity @e[tag=control_rex,limit=1] BukkitValues."arkdinos:owner" set value "${bob.uuid}"`);await menuClick(alice,2);assert.equal((await inspect('control_rex')).owner,bob.uuid);await command(`data modify entity @e[tag=control_rex,limit=1] BukkitValues."arkdinos:owner" set value "${alice.uuid}"`);await command(`controlsfixture close ${alice.name}`);
 });
 await check('Adult release preserves entity identity, species, health, scale and clears ownership/progress',async()=>{
  await command(`tp ${bob.name} 80 4 4`);await open(alice,'control_rex');await menuClick(alice,2);
  await command(`data modify entity @e[tag=control_rex,limit=1] BukkitValues."arkdinos:tame-feeds" set value 5`);const before=await inspect('control_rex');
  await open(alice,'control_rex');await menuClick(alice,8);await menuClick(alice,2);const after=await inspect('control_rex');
  for(const key of ['uuid','species','health','scale'])assert.equal(after[key],before[key],`Release preserves ${key}`);
  assert.equal(after.owner,'null');assert.equal(after.former,alice.uuid);assert.equal(after.roam,'0');assert.equal(after.stay,'0');assert.equal(after.feeds,'0');assert.match(after.tags,/ark_dino_wild/);assert.doesNotMatch(after.tags,/ark_dino_tamed/);
  assert.ok(Math.hypot(Number(after.x)-Number(before.x),Number(after.z)-Number(before.z))<1,'No teleport on release');
  report.released=after;
 });
 await check('Released carnivore never targets or harms former owner and remains wild toward other players',async()=>{
  await command(`effect clear ${alice.name}`);await command(`effect clear ${bob.name}`);await command(`gamemode survival @a`);
  assert.match(await command(`controlsfixture valid control_rex ${alice.name}`),/valid=false/);assert.match(await command(`controlsfixture damage control_rex ${alice.name}`),/landed=false;loss=0.0/);
  const dino=await inspect('control_rex');await command(`tp ${bob.name} ${Number(dino.x)+3} 4 ${Number(dino.z)+1}`);assert.match(await command(`controlsfixture valid control_rex ${bob.name}`),/valid=true/);assert.match(await command(`controlsfixture damage control_rex ${bob.name}`),/landed=true;loss=2.0/);await command(`tp ${bob.name} 80 4 4`);
 });
 await check('Released Sniffer keeps custom bounded wandering without native digging and preserves UUID',async()=>{
  await spawnDino('control_sniffer','stegosaurus',12,alice.uuid);const before=await inspect('control_sniffer');assert.equal(before.behaviors,'0');
  await open(alice,'control_sniffer');await menuClick(alice,8);await menuClick(alice,2);const after=await inspect('control_sniffer');assert.equal(after.owner,'null');assert.equal(after.uuid,before.uuid);assert.equal(after.ai,'true');assert.equal(after.behaviors,'0','Wild Sniffer digging stays disabled');report.sniffer=after;
 });
 await check('Mode and peaceful former-owner identity survive real restart',async()=>{
  await spawnDino('control_persist','triceratops',18,alice.uuid);await open(alice,'control_persist');await menuClick(alice,4);const before=await inspect('control_persist');await stop();await start();
  alice=new Client('ControlsOwner');bob=new Client('ControlsOther');clients.push(alice,bob);await eventually(()=>alice.joined&&bob.joined,'Restarted controls clients did not join',30000);await command('gamemode survival @a');await command('tp @a 0 4 3');await delay(500);
  const after=await inspect('control_persist');assert.equal(after.uuid,before.uuid);assert.equal(after.owner,alice.uuid);assert.equal(after.roam,'1');
  const rex=await inspect('control_rex');assert.equal(rex.uuid,report.released.uuid);assert.equal(rex.former,alice.uuid);assert.equal(rex.owner,'null');assert.match(await command(`controlsfixture valid control_rex ${alice.name}`),/valid=false/);assert.match(await command(`controlsfixture damage control_rex ${alice.name}`),/landed=false;loss=0.0/);
  const sniffer=await inspect('control_sniffer');assert.equal(sniffer.uuid,report.sniffer.uuid);assert.equal(sniffer.behaviors,'0');
  assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);
 });
 await check('A later tame/release remembers the latest former owner',async()=>{
  await command(`effect give @a resistance 9999 4 true`);const dino=await inspect('control_rex');await command(`tp ${bob.name} ${dino.x} 4 ${Number(dino.z)+3}`);assert.match(await command(`controlsfixture tame control_rex ${bob.name}`),/tamed=true/);
  await open(bob,'control_rex');await menuClick(bob,8);await menuClick(bob,2);assert.equal((await inspect('control_rex')).former,bob.uuid);assert.match(await command(`controlsfixture valid control_rex ${bob.name}`),/valid=false/);
  const released=await inspect('control_rex');await command(`tp ${alice.name} ${released.x} 4 ${Number(released.z)+3}`);assert.match(await command(`controlsfixture valid control_rex ${alice.name}`),/valid=true/);
 });
 await check('All seven initially wild NoAI carriers wake and wander without native Sniffer digging',async()=>{
  await command('gamemode creative @a');await command('tp @a 0 4 20');
  const species=['triceratops','stegosaurus','brachiosaurus','pteranodon','trex','velociraptor','dilophosaurus'];
  const origins=[];
  for(let i=0;i<species.length;i++){
   const tag=`wild_roam_${species[i]}`,type=i<4?'sniffer':'ravager',x=-10+(i%4)*10,z=i<4?18:28;
   await command(`summon ${type} ${x} 4 ${z} {Tags:["${tag}","ark_dino","ark_dino_wild"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${species[i]}"}}`);await delay(150);
   const before=await inspect(tag);assert.equal(before.ai,'true',`${species[i]} wakes`);assert.equal(before.owner,'null');assert.equal(before.passengers,'0');if(i<4)assert.equal(before.behaviors,'0','Native digging stays disabled');origins.push({tag,...before});
  }
  const moved=new Set();
  await eventually(async()=>{for(const before of origins){const after=await inspect(before.tag);assert.equal(after.uuid,before.uuid);assert.equal(after.owner,'null');if(Math.hypot(Number(after.x)-Number(before.x),Number(after.z)-Number(before.z))>.5)moved.add(before.species);}return moved.size===7;},'Every wild land/flying species must wander on its own',18000);
  report.wildMovement=origins.map(before=>({species:before.species,uuid:before.uuid,moved:moved.has(before.species)}));
  assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);
 });
 await check('Wild roaming keeps mounting blocked and normal taming takes over the same entity',async()=>{
  await command(`gamemode survival ${alice.name}`);const before=await inspect('wild_roam_triceratops');await command(`tp ${alice.name} ${before.x} ${before.y} ${Number(before.z)+3}`);await food(alice,'AIR',0);alice.input(0);await delay(150);alice.interact(Number(before.id));await delay(200);
  const wild=await inspect('wild_roam_triceratops');assert.equal(wild.owner,'null');assert.equal(wild.passengers,'0');assert.match(await command(`controlsfixture tame wild_roam_triceratops ${alice.name}`),/tamed=true/);
  await open(alice,'wild_roam_triceratops');await menuClick(alice,2);const owned=await inspect('wild_roam_triceratops');assert.equal(owned.uuid,before.uuid);assert.equal(owned.owner,alice.uuid);assert.equal(owned.stay,'1');assert.equal(owned.aware,'false');assert.equal(owned.path,'false');await command(`gamemode creative ${alice.name}`);
 });
 await check('Wild predators hunt eligible players and resume idle roaming when the player becomes Creative',async()=>{
  const rex=await inspect('wild_roam_trex');await command(`tp ${bob.name} ${Number(rex.x)+5} ${rex.y} ${rex.z}`);await command(`gamemode survival ${bob.name}`);
  await eventually(async()=>/active=true/.test(await command('controlsfixture active wild_roam_trex')),'Wild predator must still hunt a Survival player');
  await command(`gamemode creative ${bob.name}`);await eventually(async()=>/active=false/.test(await command('controlsfixture active wild_roam_trex')),'Creative player must cancel hunting');const before=await inspect('wild_roam_trex');
  await eventually(async()=>{const after=await inspect('wild_roam_trex');return Math.hypot(Number(after.x)-Number(before.x),Number(after.z)-Number(before.z))>.5;},'Predator must resume idle wandering',12000);
 });
 await check('Prototype-disabled main world leaves wild NoAI fixtures unchanged',async()=>{
  await command('controlsfixture create-world ark');assert.match(await command(`execute in minecraft:ark run tp ${bob.name} 0 4 0`),/Teleported/);await delay(300);await command('execute in minecraft:ark run fill -4 3 -4 4 3 4 stone');
  await command('execute in minecraft:ark run summon sniffer 0 4 0 {Tags:["wild_main_off","ark_dino","ark_dino_wild"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"triceratops"}}');await delay(150);const before=await inspect('wild_main_off');await delay(4500);const after=await inspect('wild_main_off');
  assert.equal(after.ai,'false');assert.equal(after.owner,'null');assert.equal(after.uuid,before.uuid);assert.equal(after.x,before.x);assert.equal(after.z,before.z);
 });
 }
 assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable|Wild dino controller unavailable/);
}catch(error){console.error(error.stack);process.exitCode=1;}
finally{
 clients.forEach(c=>c.close());if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
 report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
