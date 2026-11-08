#!/usr/bin/env node
// Disposable loopback Paper236 fixture and direct protocol777 clients; no historical world is opened.
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
const scopeMode = process.env.DINO_PROTOTYPE_SCOPE === '1';
const modelStack = process.env.DINO_MODEL_STACK === '1' || scopeMode;
const stackCache = `${root}output/dino-model-spike-20261107/private/server`;
const cache = process.env.DINO_PAPER_CACHE ?? (modelStack ? stackCache : `${root}artifacts/renewal-20261103/server`);
const paperJar = process.env.DINO_PAPER_JAR ?? (modelStack ? `${stackCache}/paper.jar` : `${root}artifacts/renewal-20261103/paper-26.3-236.jar`);
const paperBuild = process.env.DINO_PAPER_JAR?.match(/paper-26\.3-([0-9]+)\.jar$/)?.[1] ?? (modelStack ? '241' : '236');
await mkdir(`${plugin}build`, { recursive: true });
const directory = await mkdtemp(`${plugin}build/gameplay-smoke-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Chunk unload/load lifecycle uses dispatched native events; real world persistence is checked with a clean server restart.',
] };
const reportPath = `${plugin}build/${scopeMode ? 'prototype-scope-runtime-report.json' : modelStack ? 'gameplay-model-runtime-report.json' : 'gameplay-runtime-report.json'}`;
const clients = [];
let server;
let log = '';
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
  interact(id, sneak = false, hand = 0) {
    // Protocol777 uses hand + packed Vec3.ZERO + secondary-action, without an action enum.
    this.send(0x1a, Buffer.concat([vi(id), vi(hand), Buffer.from([0, sneak ? 1 : 0])]));
  }
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
const selector = tag => `@e[tag=${tag},limit=1]`;
const data = (tag, path) => command(`data get entity ${selector(tag)} ${path}`);
async function position(tag) { const response = await data(tag, 'Pos'); const match = response.match(/\[([^\]]+)\]\s*$/); assert.ok(match, `No position for ${tag}: ${response}`); return match[1].split(',').map(parseFloat); }
async function nativeUuid(tag) { const values = (await data(tag, 'UUID')).match(/\[I;([^\]]+)\]/)[1].split(',').map(Number); const raw = Buffer.alloc(16); values.forEach((value, index) => raw.writeInt32BE(value, index * 4)); return uuidString(raw); }
async function passengers(tag) { return command(`dinofixture inspect ${tag}`); }
async function tp(client, x, z, y = 4) { await command(`tp ${client.name} ${x} ${y} ${z}`); await delay(250); }
async function click(client, tag, sneak = false) { const [x, y, z] = await position(tag); await tp(client, x, z + 2, y); const uuid = await nativeUuid(tag); await eventually(() => client.entities.has(uuid), 'Native entity spawn not received'); client.input(sneak ? 32 : 0); await delay(100); client.interact(client.entities.get(uuid), sneak); await delay(250); client.input(0); }
async function mount(client, tag) { await command(`clear ${client.name}`); await click(client, tag); await eventually(async () => /passengers=1/.test(await passengers(tag)), `Owner failed to mount ${tag}`); }
async function stopped(tag) { const state = await passengers(tag); assert.match(state, /passengers=0;aware=false;path=false/); return state; }
async function tamePteranodon(client, tag, world='minecraft:overworld', interact=()=>click(client,tag)) {
  await command(`item replace entity ${client.name} weapon.mainhand with cod 5`);
  for(let n=0;n<5;n++) {
    // Advance only the saved cooldown; care-runtime-test separately checks real cooldown rejection.
    await command(`execute in ${world} run data modify entity ${selector(tag)} BukkitValues."arkdinos:tame-next" set value 0L`);
    await interact();
  }
  await command(`item replace entity ${client.name} weapon.mainhand with seagrass 1`);await interact();
}

try {
  for (const item of ['libraries', 'versions', 'cache']) await symlink(`${cache}/${item}`, `${directory}/${item}`);
  await mkdir(`${directory}/plugins`, { recursive: true });
  await copyFile(paperJar, `${directory}/paper.jar`);
  report.jarSha256 = crypto.createHash('sha256').update(await readFile(`${plugin}build/ArkDinos-1.1.0.jar`)).digest('hex');
  await copyFile(`${plugin}build/ArkDinos-1.1.0.jar`, `${directory}/plugins/ArkDinos.jar`);
  if(modelStack){
    for(const name of ['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar','GeyserModelEngine-1.0.9.jar','geyser-spigot.jar','floodgate-spigot.jar','geyserutils-spigot-1.0-SNAPSHOT.jar']) await copyFile(`${stackCache}/plugins/${name}`,`${directory}/plugins/${name}`);
    await copyFile(`${plugin}.deps/geyser-spigot-2.12.1-b1295.jar`,`${directory}/plugins/geyser-spigot.jar`);
    for(const name of ['BetterModel','GeyserModelEngine','Geyser-Spigot','floodgate']) await cp(`${stackCache}/plugins/${name}`,`${directory}/plugins/${name}`,{recursive:true});
    const generatedPack=`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack`;
    await rm(generatedPack,{recursive:true,force:true});await rm(`${generatedPack}.zip`,{force:true});
    const config=`${directory}/plugins/Geyser-Spigot/config.yml`;await writeFile(config,(await readFile(config,'utf8')).replace(/^port: .*/m,`port: ${await freePort()}`));
    await mkdir(`${directory}/plugins/BetterModel/models`,{recursive:true});
    for(const name of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus']){
      await copyFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,`${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`);
      await cp(`${root}dino-models/models/ark_${name}`,`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}`,{recursive:true});
    }
  }
  if(scopeMode){await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark\n  - ark_model_test\nprototype-worlds:\n  - ark_model_test\n');}
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`, ['--release', '25', '-sourcepath', '', '-cp', `${plugin}.deps/*`, '-d', `${directory}/fixture-classes`, `${plugin}src/gameplay/java/de/ark/dinos/GameplayFixture.java`]);
  execFileSync(`${javaHome}/bin/jar`, ['--create', '--file', `${directory}/plugins/RidingFixture.jar`, '-C', `${directory}/fixture-classes`, '.', '-C', `${plugin}src/gameplay/resources`, '.']);
  await writeFile(`${directory}/eula.txt`, 'eula=true\n');
  await writeFile(`${directory}/server.properties`, `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\n`);
  server = spawn(`${javaHome}/bin/java`, ['-Xms256M', '-Xmx1G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui'], { cwd: directory, stdio: ['pipe', 'pipe', 'pipe'] });
  (report.serverPids ??= []).push(server.pid);
  server.stdout.on('data', bytes => { log += bytes.toString(); }); server.stderr.on('data', bytes => { log += bytes.toString(); });
  await eventually(() => log.includes('Done (') || (server.exitCode !== null && assert.fail(log.slice(-4000))), 'Isolated Paper did not boot', 90000);
  assert.match(log, new RegExp(`Paper version 26\\.3-${paperBuild}-`), 'Fixture requires the pinned Paper build');
  report.paperVersion = log.match(/Paper version ([^\n]+)/)?.[1];
  if(modelStack) for(const name of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus']) assert.match(log,new RegExp(`Defined entity: modelengine:ark_${name}`));
  await command('whitelist off');
  await command('gamerule minecraft:advance_time false');
  await command('gamerule minecraft:spawn_mobs false');
  await command('difficulty normal');
  await command('setworldspawn 0 4 0');
  const alice = new Client('RideOwner'), bob = new Client('RideOther'); clients.push(alice, bob);
  await eventually(() => alice.joined && bob.joined, 'Native clients did not join', 30000);
  await command('gamemode survival @a'); await command('effect give @a resistance 9999 4 true'); await tp(alice, 0, 0); await tp(bob, 12, 12);
  assert.match(await command('fill -16 3 -16 32 3 32 stone'), /Successfully filled/i, 'Safe platform must exist before summoning mobs');
  if(scopeMode){
    await command('dinofixture create-world ark_model_test');
    const inspect=async tag=>Object.fromEntries((await command(`dinofixture inspect ${tag}`)).trim().split(';').map(field=>{const at=field.indexOf('=');return[field.slice(0,at),field.slice(at+1)];}));
    const scopeClick=async(tag,world,sneak=false)=>{const state=await inspect(tag);await command(`execute in ${world} run tp RideOwner ${state.x} ${state.y} ${Number(state.z)+2}`);await delay(350);alice.input(sneak?32:0);await delay(100);alice.interact(Number(state.id),sneak);await delay(300);alice.input(0);};
    const scopeControl=async(tag,world,slot)=>{await command('clear RideOwner');await scopeClick(tag,world,true);assert.match(await command(`dinofixture control-click ${tag} RideOwner ${slot}`),/cancelled=true/);await delay(150);};
    const scopeMount=async(tag,world)=>{await command('clear RideOwner');await scopeClick(tag,world);assert.equal((await inspect(tag)).passengers,'1');};
    await check('Native named tags rename all eight dinosaurs without mounting, changing ownership or toggling stay',async()=>{
      const world='minecraft:ark_model_test',samples=[];
      await command(`execute in ${world} run tp RideOwner 8 4 -6`);await delay(350);
      await command(`execute in ${world} run fill 0 3 -16 16 3 0 stone`);
      const names=['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus'];
      for(const [n,name]of names.entries()){
        const tag=`rename_${name}`,type=name==='mosasaurus'?'dolphin':['trex','velociraptor','dilophosaurus'].includes(name)?'ravager':'sniffer';
        const owner=name==='mosasaurus'?'':`,"arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b`;
        await command(`execute in ${world} run summon ${type} 8 4 -8 {Tags:["${tag}","ark_dino","${name==='mosasaurus'?'ark_dino_wild':'ark_dino_tamed'}"],PersistenceRequired:1b,Invulnerable:1b,CustomName:{text:"Before"},BukkitValues:{"arkdinos:species":"${name}"${owner}}}`);
        await command(`dinofixture load-event ${tag}`);
        const before=await inspect(tag),hand=n%2,label=`Named ${name}`;
        await command('clear RideOwner');
        await command(`item replace entity RideOwner weapon.${hand?'offhand':'mainhand'} with minecraft:name_tag[minecraft:custom_name={text:"${label}"}] 2`);
        await command(`execute in ${world} run tp RideOwner 8 4 -6`);await delay(300);
        alice.input(32);await delay(100);alice.interact(Number(before.id),true,hand);await delay(300);alice.input(0);
        const renamed=await command(`execute in ${world} run data get entity @e[tag=${tag},limit=1] CustomName`),after=await inspect(tag);
        assert.ok(renamed.includes(label),`${name} native name tag was blocked: ${renamed}`);
        assert.equal(after.uuid,before.uuid);assert.equal(after.owner,before.owner);assert.equal(after.stay,before.stay);assert.equal(after.passengers,'0');
        assert.match(await command('dinofixture player RideOwner'),new RegExp(`${hand?'off':'main'}=1(?:;|\\s|$)`),'One native name tag must be consumed');
        samples.push({name,hand,renamed,before,after});
        if(name==='trex'){
          await command('item replace entity RideOther weapon.mainhand with minecraft:name_tag[minecraft:custom_name={text:"Stolen"}] 2');
          await command(`execute in ${world} run tp RideOther 8 4 -6`);
          await eventually(()=>bob.entities.has(after.uuid),'Foreign observer did not receive dinosaur');
          await eventually(async()=>/main=2(?:;|$)/.test(await command('dinofixture player RideOther')),'Foreign observer inventory did not arrive');
          bob.interact(Number(after.id),false,0);await delay(250);
          assert.ok((await command(`execute in ${world} run data get entity @e[tag=${tag},limit=1] CustomName`)).includes(label));
          assert.match(await command('dinofixture player RideOther'),/main=2(?:;|$)/,'Foreign owner must retain their tag');
          await command('execute in minecraft:overworld run tp RideOther -10 4 24');
        }
        await command(`execute in ${world} run kill @e[tag=${tag}]`);
      }
      await command(`execute in ${world} run kill @e[type=item,distance=..32]`);
      await command('clear RideOwner');await command('gamemode survival RideOwner');
      return samples;
    });
    await check('Historical native juvenile scales survive real care migration and BetterModel attach, growth and fallback',async()=>{
      const world='minecraft:ark_model_test',samples=[];
      for(const [kind,name,base]of [['sniffer','triceratops',1],['ravager','trex',1.2]])for(const remaining of [780000,480000]){
        const tag=`historical_${name}_${remaining}`;await command(`execute in ${world} run summon ${kind} 8 4 -8 {Tags:["${tag}","ark_dino","ark_dino_tamed"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${name}","arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b}}`);
        await command(`dinofixture load-event ${tag}`);const before=await inspect(tag);
        const state=Object.fromEntries((await command(`dinofixture historical-growth ${tag} ${remaining} ${base}`)).trim().split(';').map(field=>{const at=field.indexOf('=');return[field.slice(0,at),field.slice(at+1)];}));
        const fraction=remaining===780000?.67:1;
        assert.equal(state.uuid,before.uuid);assert.equal(state.owner,alice.uuid);assert.equal(state.duration,'300000');
        assert.ok(Math.abs(Number(state.original)-base)<.001,`Historical adult base changed: ${JSON.stringify(state)}`);
        assert.ok(Math.abs(Number(state.render)-fraction)<.001);assert.ok(Math.abs(Number(state.fallback)-base*fraction)<.001);
        assert.equal(Number(state.adultRender),1);assert.ok(Math.abs(Number(state.adultFallback)-base)<.001);
        samples.push({name,base,remaining,before,state});await command(`execute in ${world} run kill @e[tag=${tag}]`);
      }
      return samples;
    });
    await check('Actual juvenile feeding advances detail growth and keeps identity',async()=>{
      const world='minecraft:ark_model_test',tag='detail_feed';
      await command(`execute in ${world} run summon sniffer 8 4 -8 {Tags:["${tag}","ark_dino","ark_dino_tamed"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"triceratops","arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b,"arkdinos:growth-duration":300000L}}`);
      await command(`dinofixture load-event ${tag}`);await command(`dinofixture growth-time ${tag} 180000`);
      const before=await inspect(tag);await eventually(async()=>/growth_baby/.test(await command(`dinofixture model-state ${tag}`)),'Baby detail pose missing');
      await command('clear RideOwner');await command('give RideOwner wheat 2');await scopeClick(tag,world);
      await eventually(async()=>/growth_juvenile/.test(await command(`dinofixture model-state ${tag}`)),'Feeding must advance details to juvenile pose');
      assert.match(await command('dinofixture player RideOwner'),/main=1(?:;|\s|$)/);
      const young=await inspect(tag);assert.equal(young.uuid,before.uuid);assert.equal(young.owner,before.owner);assert.equal(young.passengers,'0');
      await command(`dinofixture growth-time ${tag} 0`);await eventually(async()=>/growth_adult/.test(await command(`dinofixture model-state ${tag}`)),'Adult detail pose missing');
      const adult=await inspect(tag);assert.equal(adult.uuid,before.uuid);assert.equal(adult.owner,before.owner);
      await command(`execute in ${world} run kill @e[tag=${tag}]`);await command('clear RideOwner');
      return {before,young,adult};
    });
    await check('Prototype world scopes migration, models and flight; main owner ground controls remain native',async()=>{
      const worlds=[['scope_main','minecraft:overworld'],['scope_test','minecraft:ark_model_test']];const states={};
      for(const [tag,world]of worlds){
        await command(`execute in ${world} run tp RideOwner 0 4 3`);await delay(500);
        await command(`execute in ${world} run fill -8 3 -8 16 3 16 stone`);
        await command(`execute in ${world} run summon sniffer 0 4 0 {Tags:["${tag}","ark_dino","ark_renewal_habitat_05_dino_0"],NoAI:1b,PersistenceRequired:1b}`);
        await command(`dinofixture load-event ${tag}`);const state=await inspect(tag);const model=await command(`dinofixture model-state ${tag}`);
        if(tag==='scope_main'){assert.equal(state.species,'null','Main habitat must not migrate');assert.match(model,/trackers=0;/,'Main habitat must not gain a model');}
        else {assert.equal(state.species,'pteranodon');assert.match(model,/trackers=1;/);}
        if(tag==='scope_main'){await command('give RideOwner torchflower_seeds 1');await scopeClick(tag,world);}
        else await tamePteranodon(alice,tag,world,()=>scopeClick(tag,world));
        if(tag==='scope_main')await scopeClick(tag,world,true);
        else await scopeControl(tag,world,2);
        const owned=await inspect(tag);assert.equal(owned.owner,alice.uuid);assert.equal(owned.stay,'1');if(tag==='scope_main')assert.equal(owned.species,'null','Main taming must not migrate');
        await scopeMount(tag,world);const start=await inspect(tag);alice.look(0,0);alice.input(17);await delay(1000);alice.input(0);const moved=await inspect(tag);
        assert.ok(Number(moved.z)>Number(start.z)+1,'Owner directional input did not move native carrier');
        if(tag==='scope_main'){assert.equal(moved.gravity,'true');assert.ok(Math.abs(Number(moved.y)-Number(start.y))<.5,'Main legacy Pteranodon tag must not fly');}
        else {assert.equal(moved.gravity,'false');assert.ok(Number(moved.y)>Number(start.y)+3,'Prototype Pteranodon must take off');}
        alice.input(32);await delay(250);alice.input(0);await eventually(async()=> (await inspect(tag)).gravity==='true','Unridden prototype did not land');
        states[tag]={initial:state,model,start,moved,final:await inspect(tag)};
      }
      // Saved species must not bypass the world gate, even if an administrator or prior draft wrote it.
      await command('data modify entity @e[tag=scope_main,limit=1] BukkitValues."arkdinos:species" set value "pteranodon"');
      await scopeMount('scope_main','minecraft:overworld');const before=await inspect('scope_main');alice.input(16);await delay(800);alice.input(0);const after=await inspect('scope_main');assert.equal(after.gravity,'true');assert.ok(Math.abs(Number(after.y)-Number(before.y))<.5,'Saved species bypassed prototype world scope');alice.input(32);await delay(200);alice.input(0);
      await command('dinofixture clear-species scope_main');
      await command('dinofixture unload-event scope_main');await command('dinofixture load-event scope_main');
      await command('dinofixture unload-event scope_test');await command('dinofixture load-event scope_test');
      assert.equal((await inspect('scope_main')).species,'null');assert.match(await command('dinofixture model-state scope_main'),/trackers=0;/);assert.equal((await inspect('scope_test')).species,'pteranodon');assert.match(await command('dinofixture model-state scope_test'),/trackers=1;/);
      assert.equal((await inspect('scope_main')).owner,alice.uuid);assert.equal((await inspect('scope_main')).stay,'1');
      assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable/);
      return states;
    });
    await check('Ground walk returns to idle on normal and forced riding exits',async()=>{
      await command(`execute in minecraft:ark_model_test run summon sniffer 8 4 0 {Tags:["scope_ground","ark_dino_tamed"],PersistenceRequired:1b,BukkitValues:{"arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b,"arkdinos:species":"triceratops"}}`);await command('dinofixture load-event scope_ground');
      const exits={};
      for(const mode of ['sneak','game-mode']){
        await command('gamemode survival RideOwner');await scopeMount('scope_ground','minecraft:ark_model_test');assert.equal((await inspect('scope_ground')).stay,'1');
        alice.look(0,0);alice.input(1);await delay(400);const walking=await command('dinofixture model-state scope_ground');assert.match(walking,/name=walk/);
        if(mode==='sneak')alice.input(32);else await command('gamemode creative RideOwner');await delay(250);alice.input(0);
        const samples=[];(report.groundExitSamples??={})[mode]=samples;
        await eventually(async()=>{
          const native=await inspect('scope_ground'),model=await command('dinofixture model-state scope_ground');samples.push({native,model});
          assert.equal(native.passengers,'0');assert.equal(native.aware,'false');assert.equal(native.path,'false');
          exits[mode]=model;return /name=idle/.test(model);
        },`${mode} did not settle to idle after dismount`,3000);
      }
      await command('gamemode survival RideOwner');
      for(const [mode,state]of Object.entries(exits)){assert.match(state,/name=idle/,`${mode} left a ground model walking: ${JSON.stringify(exits)}`);assert.doesNotMatch(state,/name=walk/);}
      return exits;
    });
    await check('Native sprint input increases grounded riding speed without changing height',async()=>{
      await scopeMount('scope_ground','minecraft:ark_model_test');alice.look(0,0);
      const samples={};
      for(const [name,flags] of [['walk',1],['sprint',65]]){
        alice.input(flags);await delay(150);const before=await inspect('scope_ground');await delay(400);const after=await inspect('scope_ground');
        samples[name]={before,after,distance:Math.hypot(Number(after.x)-Number(before.x),Number(after.z)-Number(before.z))};alice.input(0);await delay(150);
      }
      alice.input(32);await delay(250);alice.input(0);report.sprintSamples=samples;
      assert.ok(samples.sprint.distance>samples.walk.distance*1.3,`Sprint did not speed up: ${JSON.stringify(samples)}`);
      assert.ok(Math.abs(Number(samples.sprint.after.y)-Number(samples.sprint.before.y))<.1);return samples;
    });
  } else {
  await command('gamemode creative @a');
  await check('All 24 legacy habitats persist six species without replacing native entities', async () => {
    await tp(bob, -10, 24); // Keep the observer out of the migration grid so native collision cannot push a fixture.
    const names=['triceratops','stegosaurus','trex','brachiosaurus','pteranodon','velociraptor'];
    const snapshots=[];
    for(let n=1;n<=12;n++) for(let d=0;d<2;d++) {
      const species=names[(n-1)%6], tag=`legacy_${n}_${d}`, kind=['trex','velociraptor'].includes(species)?'ravager':'sniffer';
      await command(`summon ${kind} ${n*2} 4 ${8+d*5} {Tags:["ark_dino","ark_renewal_habitat_${String(n).padStart(2,'0')}_dino_${d}","${tag}"],NoAI:1b,PersistenceRequired:1b,Health:12f}`);
      const before=await command(`dinofixture inspect ${tag}`);
      await command(`dinofixture load-event ${tag}`);
      assert.match(await data(tag,'BukkitValues."arkdinos:species"'),new RegExp(species));
      const after=await command(`dinofixture inspect ${tag}`);
      assert.equal(before.split(';species=')[0],after.split(';species=')[0],'Migration must preserve identity/health/location/owner/stay');
      await command(`dinofixture load-event ${tag}`); assert.equal(await nativeUuid(tag), before.match(/uuid=([^;]+)/)[1]);
      const modelState=modelStack ? await command(`dinofixture model-state ${tag}`) : null;if(modelStack)assert.match(modelState,/trackers=1;/);
      snapshots.push({tag,species,uuid:await nativeUuid(tag),modelState});
    }
    return snapshots;
  });
  await check('Ordinary mobs untouched; owned defaults, compatible PDC and conflicting tags restore safely', async () => {
    await command('summon sniffer 0 4 20 {Tags:["ordinary"],NoGravity:1b}'); await command('dinofixture load-event ordinary');
    assert.match(await data('ordinary','NoGravity'),/1b$/); assert.match(await data('ordinary','BukkitValues."arkdinos:species"'),/no elements|not found/i);
    for(const [tag,kind,species,extra] of [['owned_default','sniffer','triceratops',''],['owned_rex','ravager','trex',''],['pdc_wins','sniffer','pteranodon',',"arkdinos:species":"pteranodon"']]){
      await command(`summon ${kind} 0 4 25 {Tags:["${tag}","ark_dino_tamed","ark_renewal_habitat_01_dino_0"],NoGravity:1b,BukkitValues:{"arkdinos:owner":"${alice.uuid}","arkdinos:stay":1b${extra}}}`);
      const uuid=await nativeUuid(tag); await command(`dinofixture load-event ${tag}`);
      assert.match(await data(tag,'BukkitValues."arkdinos:species"'),new RegExp(species)); assert.equal(await nativeUuid(tag),uuid);
      assert.match(await data(tag,'BukkitValues."arkdinos:owner"'),new RegExp(alice.uuid)); assert.match(await data(tag,'BukkitValues."arkdinos:stay"'),/1b$/);
      if(species==='pteranodon') assert.match(await data(tag,'NoGravity'),/0b$|no elements/i);
    }
    await command('summon sniffer 3 4 20 {Tags:["conflict","ark_renewal_habitat_02_dino_0","ark_renewal_habitat_05_dino_0"]}'); await command('dinofixture load-event conflict');
    assert.match(await data('conflict','BukkitValues."arkdinos:species"'),/triceratops/);
  });
  await command('summon sniffer 0 4 0 {Tags:["ark_dino","ark_dino_wild","flight","ark_renewal_habitat_05_dino_0"],PersistenceRequired:1b}');
  // Combat has its own real-damage fixture; migration fixtures must stay out of the flight course.
  await command('effect give @e[type=ravager,tag=ark_dino] slowness infinite 255 true');
  await command('gamemode survival @a');
  await tamePteranodon(alice,'flight');await click(alice,'flight',true);
  await check('Single centered ceiling block cannot intersect the ascending native rider',async()=>{
    await command('effect clear RideOwner resistance');const beforeHealth=await command('data get entity RideOwner Health');
    await command('tp @e[tag=flight,limit=1] .5 4 .5');await command('setblock 0 8 0 stone');await mount(alice,'flight');
    alice.look(0,0);alice.input(16);await delay(2100);alice.input(0);const state=await command('dinofixture collision-state flight');assert.match(state,/riderOverlap=false;/,'Centered overhang intersects rider');
    const afterHealth=await command('data get entity RideOwner Health');assert.equal(afterHealth,beforeHealth,'Centered ceiling damaged the mounted rider');
    alice.input(32);await delay(250);alice.input(0);await command('setblock 0 8 0 air');await command('dinofixture unload-event flight');await command('tp @e[tag=flight,limit=1] 0 4 0');await command('dinofixture load-event flight');
    return {state,beforeHealth,afterHealth};
  });
  const sampleStart=Date.now(); report.flightSamples=[];
  let samplePhase='takeoff';
  async function sample(){const [x,y,z]=await position('flight');report.flightSamples.push({t:(Date.now()-sampleStart)/1000,phase:samplePhase,x,y,z,gravity:!/1b$/.test(await data('flight','NoGravity')),modelState:modelStack ? await command('dinofixture model-state flight') : undefined});}
  async function sampledDelay(ms){const until=Date.now()+ms;do{await sample();await delay(Math.min(250,Math.max(0,until-Date.now())));}while(Date.now()<until);}
  await check('Only survival owner may mount Pteranodon; native jump takes off and release hovers',async()=>{
    await click(bob,'flight'); await stopped('flight');
    await command('gamemode creative RideOwner'); await click(alice,'flight'); await stopped('flight'); await command('gamemode survival RideOwner');
    await mount(alice,'flight'); const start=await position('flight'); alice.input(16); samplePhase='takeoff'; await sampledDelay(1400); alice.input(0); samplePhase='hover'; await sampledDelay(300); const up=await position('flight');
    assert.ok(up[1]>start[1]+3,`Jump did not take off: ${start} -> ${up}`); assert.match(await data('flight','NoGravity'),/1b$/);
    await sampledDelay(600); const hover=await position('flight'); assert.ok(Math.abs(up[1]-hover[1])<.3,'Released controls must hover');
    samplePhase='yaw-turn';alice.look(90,0);alice.input(1);await sampledDelay(600);alice.input(0);const turned=await position('flight');assert.ok(turned[0]<hover[0]-1,'Native yaw did not steer west');
    return {start,up,hover};
  });
  await check('Yaw and pitch drive descent; controlled landing restores gravity and remount works',async()=>{
    const start=await position('flight'); samplePhase='steering-descent'; alice.look(0,65); alice.input(1); await sampledDelay(1000); const end=await position('flight');
    assert.ok(end[1]<start[1]-1&&end[2]>start[2]+1,`No pitch descent: ${start} -> ${end}`);
    await eventually(async()=> {await sample();return /0b$|no elements/i.test(await data('flight','NoGravity'));},'Flight did not restore gravity on landing',12000); samplePhase='landed';await sample();if(modelStack){assert.ok(report.flightSamples.some(sample=>/name=flap/.test(sample.modelState??'')),'Gameplay never selected flap');assert.ok(report.flightSamples.some(sample=>/name=glide/.test(sample.modelState??'')),'Gameplay never selected glide');} alice.input(0);
    assert.match(await data('flight','NoGravity'),/0b$|no elements/i); alice.input(32); await delay(300); alice.input(0); await stopped('flight'); await mount(alice,'flight');
    return {start,end};
  });
  async function takeoff(){ alice.look(0,0); if(!/passengers=1/.test(await passengers('flight')))await mount(alice,'flight'); alice.input(16); await delay(900);alice.input(0); await delay(200);assert.ok((await position('flight'))[1]>6); }
  await check('Sneak in air protects the rider and autonomously lands the mount',async()=>{
    await command('effect clear RideOwner resistance');const riderHealth=await command('data get entity RideOwner Health'),mountHealth=await data('flight','Health');
    await takeoff(); alice.input(32); await delay(300);alice.input(0); assert.match(await passengers('flight'),/passengers=0/);
    assert.match(await command('dinofixture player RideOwner'),/slowfall=true/);
    await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Unridden mount did not land',15000);
    assert.match(await data('flight','NoGravity'),/0b$|no elements/i);await delay(1000);assert.equal(await command('data get entity RideOwner Health'),riderHealth,'Air dismount damaged rider');assert.equal(await data('flight','Health'),mountHealth,'Autonomous landing damaged mount');
  });
  await check('Altitude cap, obstruction, water and unloaded destinations fail safely',async()=>{
    await command('effect clear RideOwner resistance');
    await takeoff(); alice.input(16); await delay(11000);alice.input(0);await delay(250);
    const capped=await position('flight');assert.ok(capped[1]<=52.1,`Exceeded takeoff +48 cap: ${capped}`);
    assert.match(await command('dinofixture boundary flight'),/vx=0.0;vy=0.0;vz=0.0/);
    alice.look(0,80);alice.input(1);await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Cap descent landing failed',20000);alice.input(0);alice.input(32);await delay(200);alice.input(0);
    await command('tp @e[tag=flight,limit=1] 0 4 0');await command('fill -2 8 -2 2 8 2 stone');await mount(alice,'flight');alice.input(16);await delay(900);alice.input(0);assert.ok((await position('flight'))[1]<6,'Head obstruction failed to stop ascent');alice.input(32);await delay(300);alice.input(0);await command('fill -2 8 -2 2 8 2 air');
    await command('tp @e[tag=flight,limit=1] 0 4 0');await mount(alice,'flight');alice.input(16);await delay(900);alice.input(0);await command('fill -3 4 -3 3 5 3 water');alice.look(0,80);alice.input(1);
    await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Water did not restore gravity');alice.input(0);alice.input(32);await delay(200);alice.input(0);await command('fill -3 4 -3 3 5 3 air');await command('tp @e[tag=flight,limit=1] 0 4 0');
    await command('effect give @e[tag=flight,limit=1] slow_falling 120 0 true');await command('tp @e[tag=flight,limit=1] 0 312 0');await mount(alice,'flight');alice.look(0,0);alice.input(16);await delay(2500);alice.input(0);
    const ceiling=await position('flight');assert.ok(ceiling[1]>313&&ceiling[1]<316,`World ceiling failed: ${ceiling}`);alice.input(32);await delay(250);alice.input(0);
    await command('dinofixture unload-event flight');await command('tp @e[tag=flight,limit=1] 0 4 0');await command('tp RideOwner 0 4 2');await command('dinofixture load-event flight');
    return {capped,ceiling};
  });
  await check('Game mode, world change, logout, death, unload and disable restore gravity',async()=>{
    await takeoff(); await command('gamemode creative RideOwner'); alice.input(0); assert.match(await passengers('flight'),/passengers=0/); await command('gamemode survival RideOwner');
    await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Mode landing failed');
    await takeoff(); await command('dinofixture world flight RideOwner'); alice.input(0); await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'World landing failed'); await command('execute in minecraft:overworld run tp RideOwner 0 4 0');
    await takeoff(); alice.close(); await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Logout landing failed');
    const rejoined=new Client('RideOwner');clients.push(rejoined);await eventually(()=>rejoined.joined,'Reconnect failed');
    await mount(rejoined,'flight');rejoined.input(16);await delay(900);rejoined.input(0);await command('kill RideOwner');await eventually(async()=> /0b$|no elements/i.test(await data('flight','NoGravity')),'Death landing failed');rejoined.send(0x0c,vi(0));await delay(500);
    await mount(rejoined,'flight');rejoined.input(16);await delay(900);rejoined.input(0);assert.match(await command('dinofixture unload-event flight'),/gravity=true/);await command('dinofixture load-event flight');
    await command('tp RideOwner 0 4 2');await command('tp @e[tag=flight,limit=1] 0 4 0');await mount(rejoined,'flight');rejoined.input(16);await delay(900);rejoined.input(0);assert.match(await command('dinofixture disable flight'),/gravity=true/);
    await command('dinofixture enable flight');assert.match(await data('flight','NoGravity'),/0b$|no elements/i);
    await command(`summon sniffer 5 4 0 {Tags:["flight_death","ark_dino_tamed"],BukkitValues:{"arkdinos:owner":"${rejoined.uuid}","arkdinos:stay":1b,"arkdinos:species":"pteranodon"}}`);await command('dinofixture load-event flight_death');
    await mount(rejoined,'flight_death');rejoined.input(16);await delay(800);rejoined.input(0);assert.match(await data('flight_death','NoGravity'),/1b$/);await command('kill @e[tag=flight_death,limit=1]');assert.match(await command('dinofixture death-state flight_death'),/gravity=true;flight=false/);
  });
  await check('Real clean server restart retains all 24 species and native UUIDs; no force-loads or plugin errors',async()=>{
    const before=[];for(let n=1;n<=12;n++)for(let d=0;d<2;d++)before.push([`legacy_${n}_${d}`,await nativeUuid(`legacy_${n}_${d}`),await data(`legacy_${n}_${d}`,'BukkitValues."arkdinos:species"')]);
    clients.forEach(client=>client.close()); await command('save-all flush');
    server.stdin.write('stop\n');await once(server,'exit');const offset=log.length;
    server = spawn(`${javaHome}/bin/java`, ['-Xms256M', '-Xmx1G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui'], { cwd: directory, stdio: ['pipe', 'pipe', 'pipe'] });
    (report.serverPids ??= []).push(server.pid);
    server.stdout.on('data',bytes=>{log+=bytes.toString();});server.stderr.on('data',bytes=>{log+=bytes.toString();});
    await eventually(()=>log.slice(offset).includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-4000))),'Restart failed',90000);
    const restarted=new Client('RideOwner');clients.push(restarted);await eventually(()=>restarted.joined,'Restarted client did not join',30000);await tp(restarted,12,12);
    await eventually(async()=> /\[I;/.test(await data('legacy_1_0','UUID')),'Restarted habitat chunk was not loaded');
    for(const [tag,uuid,species] of before){assert.equal(await nativeUuid(tag),uuid);assert.equal(await data(tag,'BukkitValues."arkdinos:species"'),species);}
    assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);
    assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable/);
  });
  }
  await writeFile(`${directory}/console.log`, log);
} catch (error) { console.error(error.stack); process.exitCode = 1; }
finally {
  clients.forEach(client => client.close());
  if (server && server.exitCode === null) { server.stdin.write('stop\n'); await Promise.race([once(server, 'exit'), delay(15000, undefined, { ref: false })]); if (server.exitCode === null) { server.kill('SIGTERM'); await once(server, 'exit'); } }
  report.finishedAt = new Date().toISOString(); await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`); console.log(`Report: ${reportPath}`);
  await writeFile(`${directory}/console.log`, log);
}
