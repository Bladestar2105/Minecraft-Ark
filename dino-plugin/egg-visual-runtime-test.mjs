#!/usr/bin/env node
// Disposable loopback Paper246 fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, writeFile, copyFile, symlink, cp, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = process.env.DINO_PAPER_CACHE ?? `${root}output/live-dependency-stack-20261110/private/server`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${cache}/paper.jar`;
const output=process.env.DINO_EGG_OUTPUT ?? `${root}output/dino-round4-20261108/placed-eggs/native`;
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/egg-smoke-`);
const report = {startedAt:new Date().toISOString(),fixture:directory,checks:[],limits:[
  'Direct protocol777 Java clients exercise real entity interactions, inventory ItemStacks and persistence; no official/Bedrock/PS5 client.',
  'Inventory clicks, protected placement, breaking and growth/fade use dispatched native Paper events against actual inventories/blocks.',
  'Cooldown and hatch deadlines advance through fixture-only PDC edits; real server restart verifies persistence.',
]};
const reportPath=`${output}/egg-runtime-report.json`;
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
      (this.packets??=[]).push({id,hex:body.toString('hex')});
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
const inspect=async tag=>fields(await command(`carefixture inspect ${tag}`));
const inventory=async client=>fields(await command(`carefixture inventory ${client.name}`));
const food=(client,type,amount)=>command(`carefixture food ${client.name} ${type} ${amount}`);
async function click(client,tag){const mob=await inspect(tag);await command(`tp ${client.name} ${mob.x} ${mob.y} ${Number(mob.z)+2}`);await delay(150);client.interact(Number(mob.id));await delay(150);}
async function spawnDino(tag,species,x=0,owner){
 const type=['trex','velociraptor','dilophosaurus'].includes(species)?'ravager':'sniffer';
 await command(`summon ${type} ${x} 4 0 {Tags:["${tag}","ark_dino"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${species}"${owner?`,"arkdinos:owner":"${owner}","arkdinos:stay":1b`:''}}}`);await delay(200);
}
async function tame(client,tag,primary,secondary){
 const count=['triceratops','stegosaurus','brachiosaurus'].includes((await inspect(tag)).species)?8:5;
 await food(client,primary,count);
 for(let n=0;n<count;n++){if(n)await command(`carefixture time ${tag} tame-next 0`);await click(client,tag);assert.equal((await inspect(tag)).feeds,String(n+1));}
 await food(client,secondary,1);await click(client,tag);assert.equal((await inspect(tag)).owner,client.uuid);assert.equal((await inventory(client)).amount,'0');
 await command(`data modify entity @e[tag=${tag},limit=1] BukkitValues."arkdinos:stay" set value 1b`);
}
async function start(){
 const offset=log.length;
 server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});
 (report.serverPids??=[]).push(server.pid);server.stdout.on('data',b=>log+=b);server.stderr.on('data',b=>log+=b);
 await eventually(()=>log.slice(offset).includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-5000))),'Paper care fixture boot failed',90000);
}
async function stop(){clients.forEach(c=>c.close());await command('save-all flush');server.stdin.write('stop\n');await once(server,'exit');}
try {
 for(const name of ['libraries','versions','cache'])await symlink(`${cache}/${name}`,`${directory}/${name}`);
 await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});await copyFile(paperJar,`${directory}/paper.jar`);
 await copyFile(process.env.DINO_EGG_PLUGIN_JAR??`${plugin}build/ArkDinos-1.2.1.jar`,`${directory}/plugins/ArkDinos.jar`);
 const modelCache=process.env.DINO_MODEL_STACK_CACHE??`${root}output/live-dependency-stack-20261110/private/server`;
 if(!process.env.DINO_EGG_FALLBACK){
  for(const name of ['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar','floodgate-spigot.jar','geyserutils-spigot-1.0-SNAPSHOT.jar'])await copyFile(`${modelCache}/plugins/${name}`,`${directory}/plugins/${name}`);
  await copyFile(`${plugin}.deps/geyser-spigot-2.12.1-b1298.jar`,`${directory}/plugins/geyser-spigot.jar`);
  await copyFile(process.env.DINO_GME_JAR??`${modelCache}/plugins/GeyserModelEngine-1.0.9.jar`,`${directory}/plugins/GeyserModelEngine-1.0.9.jar`);
  for(const name of ['BetterModel','GeyserModelEngine','Geyser-Spigot','floodgate'])await cp(`${modelCache}/plugins/${name}`,`${directory}/plugins/${name}`,{recursive:true});
  const generated=`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack`;
  await rm(generated,{recursive:true,force:true});await rm(`${generated}.zip`,{force:true});
  const gc=`${directory}/plugins/Geyser-Spigot/config.yml`;await writeFile(gc,(await readFile(gc,'utf8')).replace(/^port: .*/m,`port: ${await freePort()}`));
  for(const species of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus']){
   const name='ark_egg_'+species,source=`${root}dino-items/models/${name}`;
   await copyFile(`${source}/${name}.bbmodel`,`${directory}/plugins/BetterModel/models/${name}.bbmodel`);
   await cp(source,`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/${name}`,{recursive:true});
  }
 }

 await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark_model_test\n  - ark\nprototype-worlds:\n  - ark_model_test\nwild-spawning:\n  enabled: false\n');
 await mkdir(`${directory}/fixture-classes`);
 execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${plugin}.deps/*`,'-d',`${directory}/fixture-classes`,`${plugin}src/care/java/de/ark/dinos/CareFixture.java`]);
 execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/CareFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/care/resources`,'.']);
 await writeFile(`${directory}/eula.txt`,'eula=true\n');
 await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark_model_test\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\n`);
 await start();await command('whitelist off');await command('gamerule minecraft:spawn_mobs false');await command('difficulty normal');await command('fill -16 3 -16 32 3 32 stone');
 let alice=new Client('CareOwner'),bob=new Client('CareOther');clients.push(alice,bob);await eventually(()=>alice.joined&&bob.joined,'Care clients did not join',30000);
 await command('gamemode survival @a');await command('effect give @a resistance 9999 4 true');await command('tp @a 0 4 3');await delay(500);
 await eventually(async()=>/Successfully filled/.test(await command('fill -16 3 -16 32 3 32 stone')),'Care fixture platform chunks did not load');

 const species=['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus'];
 const visual=async x=>fields(await command(`carefixture placed-visual ark_model_test ${x} 4 8`));
 await check('Seven placed nests keep native egg selection and species-specific Java/Bedrock presentation',async()=>{
  for(const [i,name] of species.entries()){
   await command(`carefixture egg ${alice.name} ${name}`);assert.match(await command(`carefixture place ${alice.name} ${i*2} 4 8`),/cancelled=false;block=SNIFFER_EGG/);
  }
  for(const [i,name] of species.entries())await eventually(async()=>{const state=await visual(i*2);return state.actors===(process.env.DINO_EGG_FALLBACK?'0':'1')&&(process.env.DINO_EGG_FALLBACK||state.bridge==='1');},`Placed ${name} presentation was not ready`);
  const all=[];
  for(const [i,name] of species.entries()){
   const state=await visual(i*2);assert.equal(state.block,'SNIFFER_EGG');assert.equal(state.width,'0.875');assert.equal(state.height,'1.0');assert.equal(state.depth,'0.75');
   const nest=fields(await command(`carefixture nest ark_model_test ${i*2} 4 8`));assert.equal(nest.species,name);assert.equal(nest.owner,alice.uuid);assert.equal(nest.duration,'1200000');
   if(!process.env.DINO_EGG_FALLBACK){assert.equal(state.actors,'1');assert.equal(state.model,'ark_egg_'+name);assert.equal(state.trackers,'1');assert.equal(state.hitboxes,'0');assert.equal(state.bridge,'1');assert.equal(state.visible,'true');assert.equal(state.persistent,'false');assert.equal(state.marker,'true');}
   all.push({state,nest});
  }
  report.nestsBefore=all;return all;
 });
 if(!process.env.DINO_EGG_FALLBACK)await check('All seven native Java renderers and generated Bedrock entity definitions register',async()=>{
  const definitions=await command('carefixture egg-renderers');assert.equal(definitions.trim(),'java=7;bedrock=7');return definitions;
 });
 if(!process.env.DINO_EGG_FALLBACK)await check('All seven actual GME actor spawns and species identifiers reach the viewer',async()=>{
  alice.send(Number((await command(`carefixture egg-channel ${alice.name}`)).trim()),Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));
  const results=[];
  for(const [i,name] of species.entries()){
   const from=alice.packets.length,response=await command(`carefixture egg-spawn ark_model_test ${i*2} 4 8 ${alice.name}`),id=Number(response.match(/actor=(\d+)/)[1]);
   await eventually(()=>[...alice.entities.values()].includes(id),'Actual GME egg actor did not reach viewer');
   await eventually(()=>alice.packets.slice(from).some(packet=>Buffer.from(packet.hex,'hex').includes(Buffer.from('modelengine:ark_egg_'+name))),'Species identifier did not reach viewer');
   results.push({species:name,id});
  }
  return results;
 });
 await check('Repeated nesting ticks preserve one presentation actor and retain native vanilla eggs',async()=>{
  const before=await visual(0);for(let i=0;i<8;i++)await command('carefixture care-tick');
  const after=await visual(0);assert.equal(after.uuid,before.uuid);assert.equal(after.actors,before.actors);
  await command('setblock 15 4 8 sniffer_egg');assert.equal((await visual(15)).actors,'0');return after;
 });
 await check('Cancelled placement and native canBuild denial preserve eggs without presentation actors',async()=>{
  await command(`carefixture egg ${alice.name} trex`);await command('carefixture deny-place true');
  assert.match(await command(`carefixture place ${alice.name} 18 4 8`),/cancelled=true;block=AIR/);assert.equal((await visual(18)).actors,'0');
  await command('carefixture deny-place false');assert.match(await command(`carefixture place ${alice.name} 18 4 8 false`),/block=AIR;nest=false/);assert.equal((await visual(18)).actors,'0');
 });
 if(!process.env.DINO_EGG_FALLBACK)await check('BetterModel reload replaces only presentation actors and leaves nest identity/deadlines unchanged',async()=>{
  const before=await visual(0),nest=await command('carefixture nest ark_model_test 0 4 8');await command('bettermodel reload');
  await eventually(async()=>{const state=await visual(0);return state.actors==='1'&&state.uuid!==before.uuid&&state.bridge==='1';},'Reload did not restore one egg visual');
  assert.equal(await command('carefixture nest ark_model_test 0 4 8'),nest);return await visual(0);
 });
 await check('Allowed breaking removes one shell and returns exactly one species egg',async()=>{
  const before=Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]);
  assert.match(await command(`carefixture break ark_model_test 0 4 8 ${alice.name}`),/cancelled=false;vanillaDrops=false/);await command('carefixture care-tick');
  assert.equal((await visual(0)).actors,'0');assert.equal(Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]),before+1);
 });
 await check('Blocked hatching retains its shell; successful hatching removes only that shell',async()=>{
  await command('setblock 2 5 8 stone');assert.match(await command('carefixture ready ark_model_test 2 4 8'),/block=SNIFFER_EGG;nest=true/);
  assert.equal((await visual(2)).actors,process.env.DINO_EGG_FALLBACK?'0':'1');
  await command('setblock 2 5 8 air');assert.match(await command('carefixture ready ark_model_test 2 4 8'),/block=AIR;nest=false/);assert.equal((await visual(2)).actors,'0');
 });
 if(!process.env.DINO_EGG_FALLBACK)await check('Chunk presentation cleanup and subsequent index tick recreate one shell without touching nest data',async()=>{
  const before=await visual(4),nest=await command('carefixture nest ark_model_test 4 4 8');
  await command('carefixture visual-unload ark_model_test 4 4 8');assert.equal((await visual(4)).actors,'0');
  await command('carefixture care-tick');await eventually(async()=>{const state=await visual(4);return state.actors==='1'&&state.uuid!==before.uuid&&state.bridge==='1';},'Egg presentation did not return');
  assert.equal(await command('carefixture nest ark_model_test 4 4 8'),nest);
 });
 await check('Real restart persists nests and rebuilds nonpersistent presentation actors exactly once',async()=>{
  const before=await visual(4),nest=await command('carefixture nest ark_model_test 4 4 8');await stop();await start();
  alice=new Client('CareOwner');clients.push(alice);await eventually(()=>alice.joined,'Restarted egg client did not join',30000);await command(`tp ${alice.name} 4 4 8`);
  await eventually(async()=>Number((await visual(4)).actors)===(process.env.DINO_EGG_FALLBACK?0:1),'Restarted egg presentation did not return');
  const after=await visual(4);if(!process.env.DINO_EGG_FALLBACK){assert.notEqual(after.uuid,before.uuid);assert.equal(after.bridge,'1');}
  assert.equal(await command('carefixture nest ark_model_test 4 4 8'),nest);assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);return after;
 });
 report.ok=true;
} catch(error){report.ok=false;report.error=error.stack;console.error(error.stack);process.exitCode=1;}
finally {
 clients.forEach(c=>c.close());
 if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000)]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
 report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2));await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
