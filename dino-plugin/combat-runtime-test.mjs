#!/usr/bin/env node
// Disposable loopback Paper fixture and direct protocol777 clients; no historical world is opened.
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
const scopeMode = false;
const modelStack = process.env.DINO_COMBAT_MODELS==='1';
const stackCache = process.env.DINO_MODEL_STACK_CACHE ?? `${root}output/live-dependency-stack-20261110/private/server`;
const cache = process.env.DINO_PAPER_CACHE ?? stackCache;
const paperJar = process.env.DINO_PAPER_JAR ?? `${cache}/paper.jar`;
const output=process.env.DINO_COMBAT_OUTPUT??`${plugin}build`;
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/combat-smoke-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Damage and interaction stimuli use native Bukkit calls; mounted swing uses dispatched PlayerArmSwingEvent.',
  'The optional model-input case sends the actual native swing packet; GME outgoing property encoding is observed using a fixture viewer, not a Bedrock socket.',
] };
const reportPath = `${output}/combat-runtime-report.json`;
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
  interact(id, sneak = false) { this.send(0x1a, Buffer.concat([vi(id), vi(0), Buffer.from([0, sneak ? 1 : 0])])); }
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
async function check(name, action) { if(process.env.DINO_COMBAT_FOCUS&&!process.env.DINO_COMBAT_FOCUS.split('|').some(part=>name.includes(part)))return;try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }

const state = async tag => Object.fromEntries((await command(`combatfixture inspect ${tag}`)).split(';').map(field => field.split('=')));
const setMode=async(tag,mode)=>{await command(`combatfixture click ${tag} CombatOwner ${mode}`);await eventually(async()=>{const value=await state(tag);return /open=false/.test(await command('combatfixture menu CombatOwner'))&&Number(value.stay)===(mode==='stay'?1:0)&&Number(value.free)===(mode==='free'?1:0);},`Menu did not apply ${mode}`);};
const modelState=async(tag='combat_owned')=>Object.fromEntries((await command(`combatfixture model-state ${tag} CombatOwner`)).trim().split(';').map(field=>{const i=field.indexOf('=');return[field.slice(0,i),field.slice(i+1)];}));
const health = async tag => Number((await command(`data get entity @e[tag=${tag},limit=1] Health`)).match(/([\d.]+)f$/)?.[1]);
const phealth = async name => Number((await command(`combatfixture health ${name}`)).split('=')[1]);
const gpTimestamp = async name => Number((await command(`combatfixture gp-state ${name}`)).match(/timestamp=(\d+)/)[1]);
const move = async (name,x,z,y=4) => {await command(`tp ${name} ${x} ${y} ${z}`);await delay(150);};
const aim = async (tag,name='CombatOwner') => {const angle=Object.fromEntries((await command(`combatfixture look ${name} ${tag}`)).trim().split(';').map(field=>field.split('=')));clients.find(client=>client.name===name&&!client.closed).look(Number(angle.yaw),Number(angle.pitch));await delay(100);};
const spawnDino = async spec => {await command(`combatfixture spawn ${spec}`);await delay(150);};
const clear = async () => {await command('combatfixture cleanup');await delay(250);await command('gamemode creative @a');await command('tp @a 30 4 30');for(const name of ['CombatOwner','CombatOther','CombatRider'])await command(`combatfixture gp-immune ${name} false`);};
try {
  await symlink(`${cache}/libraries`,`${directory}/libraries`);
  for(const item of ['versions','cache']) await cp(`${cache}/${item}`,`${directory}/${item}`,{recursive:true});
  await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});
  await mkdir(`${directory}/plugins/WorldGuard/worlds/ark`,{recursive:true});
  await copyFile(paperJar,`${directory}/paper.jar`);
  await copyFile(process.env.DINO_PLUGIN_JAR??`${plugin}build/ArkDinos-1.2.1.jar`,`${directory}/plugins/ArkDinos.jar`);
  report.jarSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
  for(const jar of ['worldedit-bukkit-7.4.6.jar','worldguard-bukkit-7.0.21.jar']) await copyFile((jar.startsWith('worldguard-')?process.env.DINO_WORLDGUARD_JAR:process.env.DINO_WORLDEDIT_JAR)??`${cache}/plugins/${jar}`,`${directory}/plugins/${jar}`);
  report.worldguardSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/worldguard-bukkit-7.0.21.jar`)).digest('hex');
  await copyFile(process.env.DINO_GRIEFPREVENTION_JAR??`${cache}/plugins/GriefPrevention.jar`,`${directory}/plugins/GriefPrevention.jar`);
  report.griefPreventionSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/GriefPrevention.jar`)).digest('hex');
  if(modelStack) {
    for(const name of ['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar','geyser-spigot.jar','floodgate-spigot.jar','geyserutils-spigot-1.0-SNAPSHOT.jar'])await copyFile(`${stackCache}/plugins/${name}`,`${directory}/plugins/${name}`);
    await copyFile(process.env.DINO_GME_JAR??`${stackCache}/plugins/GeyserModelEngine-1.0.9.jar`,`${directory}/plugins/GeyserModelEngine-1.0.9.jar`);
    await copyFile(`${plugin}.deps/geyser-spigot-2.12.1-b1298.jar`,`${directory}/plugins/geyser-spigot.jar`);
    for(const name of ['BetterModel','GeyserModelEngine','Geyser-Spigot','floodgate'])await cp(`${stackCache}/plugins/${name}`,`${directory}/plugins/${name}`,{recursive:true});
    const generated=`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack`;
    await rm(generated,{recursive:true,force:true});await rm(`${generated}.zip`,{force:true});
    await mkdir(`${directory}/plugins/BetterModel/models`,{recursive:true});
    for(const name of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus']) {
      await copyFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,`${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`);
      await cp(`${root}dino-models/models/ark_${name}`,`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}`,{recursive:true});
    }
    const config=`${directory}/plugins/Geyser-Spigot/config.yml`;
    await writeFile(config,(await readFile(config,'utf8')).replace(/^port: .*/m,`port: ${await freePort()}`));
  }
  await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark\n  - ark_legacy\nprototype-worlds:\n  - ark\nwild-spawning:\n  enabled: false\n');
  await writeFile(`${directory}/plugins/WorldGuard/worlds/ark/regions.yml`,JSON.stringify({regions:{pvp_zone:{type:'cuboid',min:{x:12,y:-64,z:0},max:{x:14,y:319,z:1},priority:10,owners:{},members:{},flags:{pvp:'deny',build:'allow',interact:'allow',use:'allow',ride:'allow'}},__global__:{type:'global',priority:0,owners:{},members:{},flags:{'mob-damage':'allow','damage-animals':'allow',pvp:'deny'}}}}));
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${plugin}.deps/*`,'-d',`${directory}/fixture-classes`,...['CombatFixture','CombatModelProbe'].map(name=>`${plugin}src/combat/java/de/ark/dinos/${name}.java`)]);
  execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/CombatFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/combat/resources`,'.']);
  await writeFile(`${directory}/eula.txt`,'eula=true\n');
  await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
  server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});
  server.stdout.on('data',bytes=>{log+=bytes.toString();});server.stderr.on('data',bytes=>{log+=bytes.toString();});
  await eventually(()=>log.includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-4000))),'Paper failed to boot',90000);
  report.paperVersion=log.match(/Paper version ([^\n]+)/)?.[1];report.worldguardVersion=log.match(/Enabling WorldGuard v([^\n]+)/)?.[1];assert.ok(report.paperVersion&&report.worldguardVersion,'Native Paper/WorldGuard versions missing');
  report.griefPreventionVersion=log.match(/Enabling GriefPrevention v([^\n]+)/)?.[1];assert.ok(report.griefPreventionVersion,'Native GriefPrevention version missing');
  await command('whitelist off');await command('setworldspawn 0 4 0');await command('gamerule minecraft:spawn_mobs false');await command('gamerule minecraft:advance_time false');await command('gamerule minecraft:natural_health_regeneration false');await command('difficulty normal');
  await command('combatfixture gp-config spam_loginCooldownSeconds 0');
  const owner=new Client('CombatOwner'),other=new Client('CombatOther'),rider=new Client('CombatRider');clients.push(owner,other,rider);
  await eventually(()=>owner.joined&&other.joined&&rider.joined,'Native clients did not join',30000);
  await move('CombatOwner',0,0);await move('CombatOther',10,10);await command('fill -16 3 -16 40 3 40 stone');
  for(const name of ['CombatOwner','CombatOther','CombatRider']){await command(`attribute ${name} minecraft:max_health base set 500`);await command(`effect give ${name} instant_health 1 10 true`);}
  await check('Wild Pteranodon cannot bite while falling without an aerial controller and retains retaliation until landing',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_ptera pteranodon wild 0 0');
    await command('combatfixture suspend combat_ptera');await delay(150);const airState=await command('combatfixture air-state combat_ptera');assert.match(airState,/ground=false/);assert.match(airState,/aerial=false/);const before=await phealth('CombatOwner');
    await command('combatfixture strike combat_ptera CombatOwner');assert.match(await command('combatfixture valid-target combat_ptera CombatOwner'),/valid=true/,'Landing must retain the attacker as its target');
    await command('combatfixture commanded-hit combat_ptera CombatOwner');
    await delay(1200);const airHits=await command('combatfixture ptera-hits');report.pteraGroundDiagnostics={airState,airHits,before,after:await phealth('CombatOwner')};
    assert.equal(await phealth('CombatOwner'),before,'An uncontrolled airborne Pteranodon must never bite a nearby player');assert.equal(airHits.trim(),'none');
    await command('combatfixture ground combat_ptera');await eventually(async()=>await phealth('CombatOwner')<before,'A retained attacker must be bitten after actual landing');
    const groundHits=await command('combatfixture ptera-hits');assert.doesNotMatch(groundHits,/ground=false|aerial=true/);report.pteraGroundDiagnostics.groundHits=groundHits;
    await clear();return report.pteraGroundDiagnostics;
  });
  await check('Wild adult Pteranodon stays peaceful until a positive player hit and retaliates only against that attacker',async()=>{
    await clear();await command('gamemode survival CombatOwner');await command('gamemode survival CombatOther');
    await move('CombatOwner',0,2);await move('CombatOther',8,2);await spawnDino('combat_ptera pteranodon wild 0 0');
    await command('data merge entity @e[tag=combat_ptera,limit=1] {NoAI:1b}');
    const valid=async name=>(await command(`combatfixture valid-target combat_ptera ${name}`)).trim();
    const initial=await phealth('CombatOwner');await delay(1100);assert.equal(await phealth('CombatOwner'),initial);assert.equal(await valid('CombatOwner'),'valid=false');
    for(const gate of ['cancel','zero']) {
      await command(`combatfixture incoming-damage ${gate}`);await command('combatfixture strike combat_ptera CombatOwner');assert.equal(await valid('CombatOwner'),'valid=false');
    }
    await command('combatfixture incoming-damage allow');await command('gamemode creative CombatOwner');await command('combatfixture strike combat_ptera CombatOwner');await command('gamemode survival CombatOwner');
    assert.equal(await valid('CombatOwner'),'valid=false','A Creative hit must not start retaliation');
    await command('combatfixture strike combat_ptera CombatOwner');assert.equal(await valid('CombatOwner'),'valid=true','A real Survival hit must allow ground retaliation');
    assert.equal(await valid('CombatOther'),'valid=false','The peaceful Pteranodon must not hunt a bystander');
    await eventually(async()=>await phealth('CombatOwner')<initial,'The provoked ground Pteranodon did not retaliate');assert.ok(await phealth('CombatOwner')>=initial-8,'Pteranodon retains its small four-point native bite');
    await command('gamemode creative CombatOwner');await command('gamemode survival CombatOwner');assert.equal(await valid('CombatOwner'),'valid=false','A mode change must discard the old provocation');
    await command('combatfixture arrow combat_ptera CombatOwner');await eventually(async()=>await valid('CombatOwner')==='valid=true','A real player arrow must provoke its shooter');
    await command('item replace entity CombatOwner weapon.mainhand with minecraft:cod 1');owner.interact(Number((await command('combatfixture entity-id combat_ptera')).trim().split('=')[1]));await delay(200);
    assert.equal(await valid('CombatOwner'),'valid=false','An accepted fish feed must preserve taming peace');
    await command('combatfixture strike combat_ptera CombatOwner');assert.equal(await valid('CombatOwner'),'valid=true','Hitting the fed animal revokes peace and permits retaliation');
    await command('combatfixture unload combat_ptera');assert.equal(await valid('CombatOwner'),'valid=false','Unloading must discard retaliation state');
    await command('combatfixture incoming-damage allow');await clear();
  });
  await check('Accepted feeding protects only its tamer until expiry or a real uncancelled melee or arrow hit',async()=>{
    await clear();await command('gamemode survival CombatOwner');await command('gamemode survival CombatOther');
    await move('CombatOwner',0,2);await move('CombatOther',8,2);await spawnDino('combat_taming trex wild 0 0');
    const valid=async name=>(await command(`combatfixture valid-target combat_taming ${name}`)).trim();
    const feed=async()=>{
      await command('item replace entity CombatOwner weapon.mainhand with minecraft:beef 1');
      await command('data modify entity @e[tag=combat_taming,limit=1] BukkitValues."arkdinos:tame-next" set value 0L');
      owner.interact(Number((await command('combatfixture entity-id combat_taming')).trim().split('=')[1]));await delay(200);
    };
    assert.equal(await valid('CombatOwner'),'valid=true');await feed();
    assert.equal(await valid('CombatOwner'),'valid=false','An accepted feed must stop attacks against its tamer');
    assert.equal(await valid('CombatOther'),'valid=true','Other Survival players remain normal wild targets');
    await command('combatfixture incoming-damage cancel');await command('combatfixture strike combat_taming CombatOwner');
    assert.equal(await valid('CombatOwner'),'valid=false','A cancelled hit must not revoke taming peace');
    await command('combatfixture incoming-damage zero');await command('combatfixture strike combat_taming CombatOwner');
    assert.equal(await valid('CombatOwner'),'valid=false','A zero-damage hit must not revoke taming peace');
    await command('combatfixture incoming-damage allow');await command('combatfixture strike combat_taming CombatOther');
    assert.equal(await valid('CombatOwner'),'valid=false','Another player cannot revoke the tamer protection');
    await command('combatfixture strike combat_taming CombatOwner');assert.equal(await valid('CombatOwner'),'valid=true','A real tamer hit must revoke peace');
    await feed();assert.equal(await valid('CombatOwner'),'valid=false');
    await command('data modify entity @e[tag=combat_taming,limit=1] BukkitValues."arkdinos:tame-expiry" set value 0L');
    assert.equal(await valid('CombatOwner'),'valid=true','Expired progress must not protect a tamer');
    await feed();assert.equal(await valid('CombatOwner'),'valid=false');await command('combatfixture arrow combat_taming CombatOwner');
    await eventually(async()=>await valid('CombatOwner')==='valid=true','A real arrow shot must revoke its shooter protection');
    await command('combatfixture incoming-damage allow');await clear();
  });
  await check('Native horse REGEN is isolated to marked fixture pets while unmarked controls still heal',async()=>{
    await clear();
    const pets=[];
    for(let i=0;i<32;i++) {
      const marked=i<16,tag=`combat_regen_${i}`;
      await command(`combatfixture pet ${tag} horse CombatOwner`);
      if(!marked)await command(`tag @e[tag=${tag},limit=1] remove combat_fixture_pet`);
      await command(`tag @e[tag=${tag},limit=1] add combat_regen_probe`);
      await command(`tp @e[tag=${tag},limit=1] ${-12+3*(i%8)} 4 ${6+3*Math.floor(i/8)}`);
      assert.match(await command(`data get entity @e[tag=${tag},limit=1] NoAI`),/1b/);
      assert.equal((await command(`combatfixture strike ${tag} CombatOwner`)).trim(),'health=499.0');
      pets.push({tag,marked});
    }
    let events;
    await eventually(async()=>{
      events=Object.fromEntries((await command('combatfixture regen-events')).trim().split(';').map(field=>field.split('=')));
      return Number(events.marked)>0&&Number(events.control)>0;
    },'No native REGEN event observed in both wounded NoAI horse groups within sixty seconds',60000);
    assert.equal(events.reason,'REGEN');assert.equal(Number(events.amount),1);assert.equal(Number(events.unexpected),0);
    assert.equal(Number(events.markedCancelled),Number(events.marked),'Every marked native REGEN event must be cancelled');
    assert.equal(Number(events.controlCancelled),0,'Unmarked native horse healing must remain enabled');
    const observed=[];
    for(const pet of pets) {
      const current=await health(pet.tag);observed.push({...pet,health:current});
      if(pet.marked)assert.equal(current,499,'A marked fixture horse must retain its exact wounded health');
      else assert.ok(current===499||current===500,'Control horse health must reflect native one-point healing');
    }
    assert.ok(observed.some(pet=>!pet.marked&&pet.health===500),'At least one unmarked control must actually heal');
    return {events,horses:observed,nativeEventsOnly:true};
  });
  await check('Self-defense selects the permitted attacker of an unmounted tame',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 2 0');
    const ownerBefore=await phealth('CombatOwner');
    await command('combatfixture strike combat_owned combat_enemy');
    await eventually(async()=>await health('combat_enemy')<500,'An injured tame never selected its own attacker');
    assert.equal(await phealth('CombatOwner'),ownerBefore,'Self-defense fixture must not damage the owner');
    return state('combat_owned');
  });
  await check('Self-defense ignores cancelled damage, zero damage, Stay, babies and owner hits',async()=>{
    for(const gate of ['cancel','zero','stay','baby','owner','owner-mode','mounted']) {
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
      await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 2 0');
      if(gate==='stay')await setMode('combat_owned','stay');
      if(gate==='baby')await command('combatfixture age combat_owned 600000');
      if(gate==='owner-mode')await command('gamemode creative CombatOwner');
      if(gate==='mounted'){await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);}
      await command(`combatfixture incoming-damage ${['cancel','zero'].includes(gate)?gate:'allow'}`);
      const ownerBefore=await phealth('CombatOwner');
      await command(`combatfixture strike combat_owned ${gate==='owner'?'CombatOwner':'combat_enemy'}`);await delay(1200);
      assert.equal(await health('combat_enemy'),500,`${gate} must not provoke automatic combat`);
      assert.equal((await state('combat_owned')).active,'false');assert.equal(await phealth('CombatOwner'),ownerBefore);
      await command('combatfixture incoming-damage allow');
    }
  });
  await check('Self-defense resolves a projectile shooter and retains PvP protection',async()=>{
    await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',3,0);
    await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');
    await spawnDino('combat_owned trex CombatOwner 0 0');const before=await phealth('CombatOther');
    await command('combatfixture arrow combat_owned CombatOther');
    await eventually(async()=>await phealth('CombatOther')<before,'Projectile damage to the tame did not select its shooter');
    await setMode('combat_owned','stay');await setMode('combat_owned','follow');await command('combatfixture pvp false');
    const protectedHealth=await phealth('CombatOther');await command('combatfixture arrow combat_owned CombatOther');await delay(1300);
    assert.equal(await phealth('CombatOther'),protectedHealth,'Self-defense cannot bypass disabled PvP');
    await command('region flag -w ark __global__ pvp deny');
  });
  await check('Tamed wolves, cats and horses are protected during owner offense and mounted attacks',async()=>{
    for(const pet of ['wolf','cat','horse'])for(const petOwner of ['CombatOwner','CombatOther']) {
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
      await spawnDino('combat_owned trex CombatOwner 0 0');await command(`combatfixture pet combat_pet ${pet} ${petOwner}`);
      await command('combatfixture strike combat_pet CombatOwner');const afterPunch=await health('combat_pet');await delay(1200);
      assert.equal(await health('combat_pet'),afterPunch,`${petOwner}'s ${pet} was selected for autonomous dinosaur damage`);
      await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);
      await delay(200);await aim('combat_pet');await command('combatfixture swing CombatOwner');await delay(150);
      assert.equal(await health('combat_pet'),afterPunch,`${petOwner}'s ${pet} was hit by a mounted dinosaur`);
    }
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture pet combat_pet wolf wild');
    await command('combatfixture strike combat_pet CombatOwner');const before=await health('combat_pet');
    await eventually(async()=>await health('combat_pet')<before,'Untamed living animals must remain eligible opponents');
  });
  await check('A mounted Rex applies ten damage on a tall native carrier',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await command('clear CombatOwner');
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture scale combat_owned 2.1');
    await command('combatfixture victim combat_enemy 0 2.25');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);
    await delay(250);await aim('combat_enemy');await command('combatfixture swing CombatOwner');
    await eventually(async()=>await health('combat_enemy')<500,'Elevated rider ray ended before its ground-level melee target');
    assert.equal(await health('combat_enemy'),490,'A tall Rex must apply one ten-point native hit');
  });
  await check('A scaled Brachiosaurus rider can aim at its ground-level melee target',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await command('clear CombatOwner');
    await spawnDino('combat_owned brachiosaurus CombatOwner 0 0');await command('combatfixture scale combat_owned 2.5671641791044775');
    await command('combatfixture victim combat_enemy 0 2.25');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);
    await delay(250);await aim('combat_enemy');await command('combatfixture swing CombatOwner');
    await eventually(async()=>await health('combat_enemy')===490,'Brachiosaurus rider ray did not reach a ground target within native melee range');
  });
  await check('Public riding attacks use the driver while protecting the persistent owner',async()=>{
    await clear();await command('gamemode survival CombatRider');await move('CombatRider',0,2);await command('clear CombatRider');
    await spawnDino('combat_owned trex CombatOwner 0 0');
    await command('data modify entity @e[tag=combat_owned,limit=1] BukkitValues."arkdinos:public-ride" set value 1b');
    await command('combatfixture victim combat_enemy 0 2.25');assert.match(await command('combatfixture click combat_owned CombatRider'),/passengers=1/);
    await delay(250);await aim('combat_enemy','CombatRider');await command('combatfixture swing CombatRider');
    await eventually(async()=>await health('combat_enemy')===490,'Authorized public driver could not attack while its owner was unavailable');
    await command('combatfixture remove combat_enemy');await command('gamemode survival CombatOwner');await move('CombatOwner',0,2.25);
    await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');await delay(1100);
    const ownerHealth=await phealth('CombatOwner');await aim('CombatOwner','CombatRider');await command('combatfixture swing CombatRider');await delay(200);
    assert.equal(await phealth('CombatOwner'),ownerHealth,'A public driver must never attack the persistent owner');
    await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('Public riding uses driver team permissions and driver GriefPrevention combat attribution',async()=>{
    await clear();await command('gamemode survival @a');await move('CombatOwner',10,10);await move('CombatRider',0,2);await move('CombatOther',0,2.25);await command('clear CombatRider');
    await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');
    await command('team add combat_actor_team');await command('team join combat_actor_team CombatOwner');await command('team join combat_actor_team CombatOther');await command('team modify combat_actor_team friendlyFire false');
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('data modify entity @e[tag=combat_owned,limit=1] BukkitValues."arkdinos:public-ride" set value 1b');
    assert.match(await command('combatfixture click combat_owned CombatRider'),/passengers=1/);await delay(200);await aim('CombatOther','CombatRider');
    const before=await phealth('CombatOther');await command('combatfixture swing CombatRider');
    await eventually(async()=>await phealth('CombatOther')===before-10,'The unrelated owner team must not replace driver permissions');
    assert.ok(await gpTimestamp('CombatRider')>0);assert.ok(await gpTimestamp('CombatOther')>0);assert.equal(await gpTimestamp('CombatOwner'),0,'Absent owner must not receive driver combat attribution');
    await command('team leave CombatOwner');await command('team join combat_actor_team CombatRider');await delay(1100);
    const protectedHealth=await phealth('CombatOther');await command('combatfixture swing CombatRider');await delay(200);
    assert.equal(await phealth('CombatOther'),protectedHealth,'Driver friendly-fire restrictions must block the dinosaur attack');
    await command('team remove combat_actor_team');await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('A public Dilo shot is discarded when its driver changes or loses riding permission',async()=>{
    for(const mutation of ['dismount','revoke','mode']) {
      await clear();await command('gamemode survival CombatRider');await move('CombatRider',0,2);await command('clear CombatRider');
      await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('data modify entity @e[tag=combat_owned,limit=1] BukkitValues."arkdinos:public-ride" set value 1b');
      await command('combatfixture victim combat_enemy 0 6');assert.match(await command('combatfixture click combat_owned CombatRider'),/passengers=1/);await delay(200);
      await command('combatfixture hold-shots true');await aim('combat_enemy','CombatRider');await command('combatfixture swing CombatRider');assert.match(await command('combatfixture shots'),/count=1;/);
      if(mutation==='dismount')await command('ride CombatRider dismount');
      if(mutation==='revoke')await command('data modify entity @e[tag=combat_owned,limit=1] BukkitValues."arkdinos:public-ride" set value 0b');
      if(mutation==='mode')await command('gamemode creative CombatRider');
      await eventually(async()=>/count=0;/.test(await command('combatfixture shots')),`${mutation} did not invalidate the captured driver`);
      await command('combatfixture release-shots');await delay(200);assert.equal(await health('combat_enemy'),500);assert.match(await command('combatfixture effects combat_enemy'),/blind=false;slow=false/);
    }
  });
  if(modelStack)await check('Mounted real input starts each species attack even when its elevated rider aims above the target',async()=>{
    const outcomes={};
    for(const species of ['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus']) {
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await command('clear CombatOwner');
      await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command('combatfixture victim combat_enemy 0 2.25');
      await command('data modify entity @e[tag=combat_owned,limit=1] BukkitValues."arkdinos:stay" set value 1b');
      await eventually(async()=>Number((await state('combat_owned')).passengers)===0,'Fresh dino must be unmounted');
      await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_owned CombatOwner')),'Model attachment was not ready');
      assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(250);
      const model=async()=>Object.fromEntries((await command('combatfixture model-state combat_owned CombatOwner')).trim().split(';').map(field=>{const i=field.indexOf('=');return[field.slice(0,i),field.slice(i+1)];}));
      const protocol=await model();owner.send(Number(protocol.pluginMessagePacket),Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));await delay(100);
      await command('combatfixture model-trace combat_owned CombatOwner');const before=await model();
      await aim('combat_enemy');
      const victimId=Number((await command('combatfixture entity-id combat_enemy')).trim().split('=')[1]);
      owner.send(Number(before.attackPacket),vi(victimId));
      await eventually(async()=>Number((await model()).attacks)>Number(before.attacks),'Actual ATTACK packet did not dispatch the native pre-attack event');
      const targetAttack=await model();report.manualAttackDiagnostics={species,before,targetAttack};
      assert.match(targetAttack.running,/name=attack, type=PLAY_ONCE/,`${species} real targeted ATTACK gave no dino attack`);
      assert.equal(await health('combat_enemy'),500-({trex:10,triceratops:8,stegosaurus:8,brachiosaurus:10,velociraptor:6,pteranodon:4,dilophosaurus:5}[species]),'Targeted input must use exactly one dinosaur hit, with no extra player punch');
      assert.equal(Number((await state('combat_owned')).stay),1,'Manual command must preserve Stay');
      await delay(250);const targetEncoded=await model();
      const names=JSON.parse(await readFile(`${root}dino-models/models/ark_${species}/ark_${species}.bbmodel`,'utf8')).animations.map(x=>x.name).sort();
      assert.ok(Number(targetEncoded.javaTransforms)>0,'Targeted attack must emit Java display transforms');
      assert.ok(JSON.parse(targetEncoded.masks).some(mask=>(mask&2**names.indexOf('attack'))!==0),'Targeted attack must emit the Bedrock attack flag');
      await delay(1100);await command('combatfixture model-trace combat_owned CombatOwner');const beforeMiss=await model();
      owner.look(0,0);await delay(100);owner.send(Number(before.swingPacket));
      await eventually(async()=>Number((await model()).swings)>Number(beforeMiss.swings),'Actual swing packet did not dispatch the native arm event');
      const attacking=await model();report.manualAttackDiagnostics={species,before,attacking};
      assert.match(attacking.running,/name=attack, type=PLAY_ONCE/,`${species} real mounted swing gave no visible attack`);
      await delay(250);const after=await model();assert.ok(Number(after.javaTransforms)>0,'Java model must emit changed display transforms');
      assert.ok(JSON.parse(after.masks).some(mask=>(mask&2**names.indexOf('attack'))!==0),'Bedrock attack flag was not emitted');
      assert.equal(await health('combat_enemy'),500-({trex:10,triceratops:8,stegosaurus:8,brachiosaurus:10,velociraptor:6,pteranodon:4,dilophosaurus:5}[species]),'An unaimed swing cannot damage the target');outcomes[species]={before,targetAttack,targetEncoded,attacking,after};
      await command('ride CombatOwner dismount');const healthBeforeDefense=await health('combat_enemy');
      await command('combatfixture strike CombatOwner combat_enemy');await delay(1100);
      assert.equal(await health('combat_enemy'),healthBeforeDefense,'Dismounted Stay must not defend or attack');
      assert.match(await command('combatfixture inspect combat_owned'),/active=false;path=false/);
    }
    return outcomes;
  });
  if(modelStack)await check('Unridden wild predators and owned Follow or Free adults pair actual combat damage with Java and Bedrock attack poses',async()=>{
    const outcomes=[];
    const scenarios=[...['trex','velociraptor','dilophosaurus'].map(species=>({species,mode:'wild'})),
      ...['follow','free'].flatMap(mode=>['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus'].map(species=>({species,mode})))];
    for(const {species,mode}of scenarios) {
      await clear();await move('CombatOwner',0,2);await command('clear CombatOwner');
      await spawnDino(`combat_owned ${species} ${mode==='wild'?'wild':'CombatOwner'} 0 0`);
      await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_owned CombatOwner')),'Autonomous actor model attachment was not ready');
      const initial=await modelState();owner.send(Number(initial.pluginMessagePacket),Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));await delay(100);
      await command('tp @e[tag=combat_owned,limit=1] 0 4 0');
      const mouth=await modelState();
      const blueprint=JSON.parse(await readFile(`${root}dino-models/models/ark_${species}/ark_${species}.bbmodel`,'utf8'));
      const jaw=blueprint.elements.find(element=>element.name==='jaw')??blueprint.elements.find(element=>element.name==='lower_jaw')
        ??blueprint.elements.find(element=>element.name==='lower_beak0')??blueprint.elements.find(element=>element.name==='head');
      const targetX=.5,targetZ=.5-(jaw.from[2]+jaw.to[2])/32;
      mouth.sourceJawBounds={from:jaw.from,to:jaw.to};mouth.groundTarget={x:targetX,y:4,z:targetZ};
      if(mode!=='wild')await command('gamemode survival CombatOwner');
      if(mode!=='wild') {
        await setMode('combat_owned',mode);
        await command(`combatfixture victim combat_enemy ${targetX} ${targetZ}`);
      } else await move('CombatOwner',targetX,targetZ);
      await command('combatfixture model-trace combat_owned CombatOwner');
      const before=mode==='wild'?await phealth('CombatOwner'):await health('combat_enemy');
      const beforeHits=Number((await state('combat_owned')).hits);
      if(mode==='wild')await command('gamemode survival CombatOwner');else await command('combatfixture strike combat_enemy CombatOwner');
      await eventually(async()=>Number((await state('combat_owned')).hits)>beforeHits,`${mode} ${species} caused no native dinosaur hit`);
      const attacking=await modelState();
      assert.match(attacking.running,/name=(attack|spit), type=PLAY_ONCE/,`${mode} ${species} damaged its opponent without a combat pose`);
      await delay(200);const encoded=await modelState();
      const names=JSON.parse(await readFile(`${root}dino-models/models/ark_${species}/ark_${species}.bbmodel`,'utf8')).animations.map(x=>x.name).sort();
      const combatBits=2**names.indexOf('attack')+(names.includes('spit')?2**names.indexOf('spit'):0);
      assert.ok(Number(encoded.javaTransforms)>0,'Actual unmounted combat must emit Java display transforms');
      assert.ok(JSON.parse(encoded.masks).some(mask=>(mask&combatBits)!==0),'Actual unmounted combat must emit a Bedrock attack or spit flag');
      const after=mode==='wild'?await phealth('CombatOwner'):await health('combat_enemy');assert.ok(after<before,'Native opponent health must fall');
      outcomes.push({species,mode,mouth,attacking,encoded,before,after});report.autonomousDiagnostics=outcomes;
    }
    return outcomes;
  });
  if(modelStack)await check('Mosa proxy contact keeps original native damage identity and protection for a mounted dinosaur attack',async()=>{
    await clear();await command('forceload add -32 -32 32 32');await command('fill -24 4 -24 24 16 24 water');
    await command('combatfixture mosa-spawn');
    await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_mosa CombatOwner')),'Mosasaurus model was not attached');
    await command('combatfixture mosa-freeze');await command('fill -24 11 -24 24 16 24 air');await delay(500);
    const parse=text=>Object.fromEntries(text.trim().split(';').map(field=>{const i=field.indexOf('=');return[field.slice(0,i),field.slice(i+1)];}));
    const initialProxy=parse(await command('combatfixture model-proxy combat_mosa CombatOwner'));
    const x=Number(initialProxy.x),z=Number(initialProxy.z)+2.75;
    await command(`setblock ${Math.floor(x)} 9 ${Math.floor(z)} stone`);
    await command(`combatfixture spawn combat_owned trex CombatOwner ${x} ${z}`);
    await command(`tp @e[tag=combat_owned,limit=1] ${x} 10 ${z} 180 0`);await move('CombatOwner',x,z,10);
    await command('gamemode survival CombatOwner');await command('clear CombatOwner');
    await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_owned CombatOwner')),'Rider dinosaur model was not attached');
    assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);
    const proxy=parse(await command('combatfixture model-proxy combat_mosa CombatOwner'));
    assert.ok(Math.hypot(Number(proxy.x)-.5,Number(proxy.z)-.5)>6,'Head contact must be far from the original Dolphin collision box');
    const model=await modelState();owner.look(Number(proxy.yaw),Number(proxy.pitch));await delay(100);
    assert.equal(Number((await state('combat_owned')).passengers),1,'Trex must remain mounted at actual input');
    report.proxyContactBefore=await command('combatfixture contact-state CombatOwner');
    owner.send(Number(model.attackPacket),vi(Number(proxy.id)));await delay(200);
    report.proxyContactAfter={lastDamage:await command('combatfixture last-damage'),model:await modelState()};
    assert.equal(await health('combat_mosa'),190,'One proxy attack must cause one ten-point original-Dolphin dinosaur hit');
    assert.match(await command('combatfixture last-damage'),/RAVAGER:DOLPHIN/);
    await delay(1100);await command('region flag -w ark __global__ damage-animals deny');
    owner.send(Number(model.attackPacket),vi(Number(proxy.id)));await delay(200);
    assert.equal(await health('combat_mosa'),180,'Proxy contact cannot bypass protection on the original Dolphin');
    await command('region flag -w ark __global__ damage-animals allow');const finalHealth=await health('combat_mosa');
    await command('combatfixture cleanup');await command('fill -24 4 -24 24 16 24 air');return{proxy,model,health:finalHealth};
  });
  if(modelStack)await check('Dilo native spit resolves a Mosa model contact to the intended original Dolphin',async()=>{
    await clear();await command('forceload add -32 -32 32 32');await command('fill -24 4 -24 24 16 24 water');
    await command('combatfixture mosa-spawn');await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_mosa CombatOwner')),'Mosa model missing');
    await command('combatfixture mosa-freeze');await delay(500);
    await command('fill -1 7 -7 1 7 -5 stone');await command('combatfixture spawn combat_owned dilophosaurus CombatOwner .5 -6');
    await command('tp @e[tag=combat_owned,limit=1] .5 8 -6 0 0');await move('CombatOwner',.5,-5,8);await command('gamemode survival CombatOwner');
    await eventually(async()=>/trackers=1/.test(await command('combatfixture model-state combat_owned CombatOwner')),'Dilo model missing');
    const shotsBefore=await command('combatfixture shots');const impactsBefore=Number(shotsBefore.match(/impacts=(\d+)/)[1]);
    // Isolate the new projectile hit from the triggering one-point punch's native damage immunity.
    await command('combatfixture strike combat_mosa CombatOwner clear-iframe');const before=await health('combat_mosa');
    await eventually(async()=>Number((await command('combatfixture shots')).match(/impacts=(\d+)/)[1])>impactsBefore,'Spit did not cause a native impact');
    await delay(100);const shots=await command('combatfixture shots');const after=await health('combat_mosa');
    report.spitProxyDiagnostics={before,after,shots,model:await modelState()};
    assert.equal(after,before-3,'A spit impact on the intended model contact must damage its original Dolphin once');
    assert.match(await command('combatfixture effects combat_mosa'),/blind=true;slow=true/);
    await command('combatfixture cleanup');await command('fill -24 4 -24 24 16 24 air');return report.spitProxyDiagnostics;
  });
  await check('GriefPrevention fresh-spawn immunity blocks both carrier families as well as direct player hits',async()=>{
    await clear();await command('gamemode survival @a');await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');
    const outcomes=[];
    for(const species of ['trex','triceratops']){
      await command('combatfixture cleanup');await move('CombatOwner',0,2);await move('CombatOther',0,2.25);
      await command('combatfixture gp-immune CombatOwner false');assert.match(await command('combatfixture gp-immune CombatOther true'),/gpEnabled=true;immune=true;pvpRules=true/);
      const initial=await phealth('CombatOther');await command('combatfixture strike CombatOther CombatOwner');assert.equal(await phealth('CombatOther'),initial,'GP must block direct player damage');
      await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await aim('CombatOther');await command('combatfixture swing CombatOwner');await delay(150);
      const after=await phealth('CombatOther');outcomes.push({species,initial,after});assert.equal(after,initial,`${species} bypassed GP fresh-spawn immunity`);
      assert.equal(await gpTimestamp('CombatOwner'),0);assert.equal(await gpTimestamp('CombatOther'),0);
      await command('combatfixture gp-immune CombatOther false');await command('combatfixture gp-immune CombatOwner true');await command('combatfixture swing CombatOwner');assert.equal(await phealth('CombatOther'),initial,'Immune owner must not attack through a companion');
      await command('combatfixture gp-immune CombatOwner false');await delay(1100);const hits=Number((await state('combat_owned')).hits);await command('combatfixture swing CombatOwner');await eventually(async()=>await phealth('CombatOther')<initial,'GP-permitted mounted attack failed');
      assert.equal(await phealth('CombatOther'),initial-(species==='trex'?10:8));assert.equal(Number((await state('combat_owned')).hits),hits+1,'Permission query must not duplicate native damage');
      assert.ok(await gpTimestamp('CombatOwner')>0);assert.ok(await gpTimestamp('CombatOther')>0);
    }
    await command('combatfixture gp-immune CombatOther false');await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');return outcomes;
  });
  await check('GriefPrevention safe claims and native PreventPvPEvent exceptions apply to both carrier families',async()=>{
    await command('combatfixture gp-config pvp_noCombatInPlayerLandClaims true');
    for(const species of ['trex','triceratops'])for(const [kind,minZ,maxZ] of [['admin',-2,1],['CombatOther',2,5]]){
      await clear();await command('gamemode survival @a');await move('CombatOwner',0,0);await move('CombatOther',0,2.25);await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');
      await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);
      assert.match(await command(`combatfixture gp-claim ${kind} -5 5 ${minZ} ${maxZ}`),/claimCreated=true/);
      const before=await phealth('CombatOther');await command('combatfixture strike CombatOther CombatOwner');assert.equal(await phealth('CombatOther'),before,'GP safe claim must block direct player hit');
      await aim('CombatOther');await command('combatfixture swing CombatOwner');assert.equal(await phealth('CombatOther'),before,'Companion bypassed safe claim');
      const prevented=Number((await command('combatfixture gp-allow-pvp true')).match(/preventions=(\d+)/)[1]);assert.ok(prevented>0);await delay(1100);await command('combatfixture swing CombatOwner');await eventually(async()=>await phealth('CombatOther')<before,'Native GP PreventPvPEvent exception did not allow attack');
      assert.equal(await phealth('CombatOther'),before-(species==='trex'?10:8));await command('combatfixture gp-allow-pvp false');await command('combatfixture gp-clear-claims');
    }
    await command('combatfixture gp-config pvp_noCombatInPlayerLandClaims false');await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('GriefPrevention creature trust denies both carrier families and Dilo until container trust is granted',async()=>{
    for(const species of ['trex','triceratops','dilophosaurus']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command(`combatfixture victim combat_enemy 0 ${species==='dilophosaurus'?6:2.25}`);
      assert.match(await command('combatfixture gp-claim CombatOther -5 5 2 10'),/claimCreated=true/);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await aim('combat_enemy');
      await command('combatfixture swing CombatOwner');await delay(500);assert.equal(await health('combat_enemy'),500);assert.match(await command('combatfixture effects combat_enemy'),/blind=false;slow=false/);
      assert.match(await command('combatfixture gp-trust CombatOwner combat_enemy'),/trusted=true/);await delay(1100);await command('combatfixture swing CombatOwner');await eventually(async()=>await health('combat_enemy')<500,'GP trusted creature attack was blocked');
      assert.equal(await health('combat_enemy'),500-({trex:10,triceratops:8,dilophosaurus:3}[species]));await command('combatfixture gp-clear-claims');
    }
  });
  await check('GriefPrevention immunity and claim changes cancel Dilo spit in flight before impact',async()=>{
    await command('combatfixture gp-config pvp_noCombatInPlayerLandClaims true');
    for(const mutation of ['victim-immunity','owner-immunity','safe-claim','creature-claim']){
      await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',0,6);await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');
      const target=mutation==='creature-claim'?'combat_enemy':'CombatOther';if(target==='combat_enemy')await command('combatfixture victim combat_enemy 0 6');await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await aim(target);await command('combatfixture hold-shots true');await command('combatfixture swing CombatOwner');assert.match(await command('combatfixture shots'),/count=1;/);
      const before=target==='combat_enemy'?await health(target):await phealth(target);
      if(mutation==='victim-immunity')await command('combatfixture gp-immune CombatOther true');if(mutation==='owner-immunity')await command('combatfixture gp-immune CombatOwner true');
      if(mutation==='safe-claim'||mutation==='creature-claim')assert.match(await command('combatfixture gp-claim CombatOther -5 5 4 10'),/claimCreated=true/);
      await command('combatfixture release-shots');await delay(600);assert.match(await command('combatfixture shots'),/count=0;/);assert.equal(target==='combat_enemy'?await health(target):await phealth(target),before);assert.match(await command(`combatfixture effects ${target}`),/blind=false;slow=false/);await command('combatfixture gp-clear-claims');
    }
    await command('combatfixture gp-config pvp_noCombatInPlayerLandClaims false');await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('GriefPrevention records real positive melee and spit PvP, with no combat state from cancelled or zero damage',async()=>{
    for(const species of ['trex','triceratops','dilophosaurus'])for(const gate of ['cancel-damage','zero-damage','allow']){
      await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',0,species==='dilophosaurus'?6:2.25);await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(150);await aim('CombatOther');
      if(gate!=='allow')await command(`combatfixture ${gate} true`);const before=await phealth('CombatOther');await command('combatfixture swing CombatOwner');await delay(750);
      if(gate==='allow'){assert.equal(await phealth('CombatOther'),before-({trex:10,triceratops:8,dilophosaurus:3}[species]));assert.ok(await gpTimestamp('CombatOwner')>0);assert.ok(await gpTimestamp('CombatOther')>0);}
      else {assert.equal(await phealth('CombatOther'),before);assert.equal(await gpTimestamp('CombatOwner'),0);assert.equal(await gpTimestamp('CombatOther'),0);await command(`combatfixture ${gate} false`);}
      await command('effect clear CombatOther');
    }
    await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('Owned adult attacks the living opponent that damaged its owner',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 3 0');
    await command('combatfixture strike CombatOwner combat_enemy');
    await eventually(async()=>await health('combat_enemy')<500,'Owned dino did not defend its owner');
    assert.ok(Number((await state('combat_owned')).hits)>0);return state('combat_owned');
  });
  await check('Carrier damage outside the commanded bite cannot bypass the cooldown',async()=>{
    const before=await health('combat_enemy');await command('combatfixture native-bite combat_owned combat_enemy');assert.equal(await health('combat_enemy'),before);
  });
  await check('Owner attacks and owner-shot arrows select the opponent; cooldown stays at least 20 ticks',async()=>{
    await setMode('combat_owned','stay');await setMode('combat_owned','follow');
    let beforeHits=Number((await state('combat_owned')).hits);await command('combatfixture strike combat_enemy CombatOwner');
    await eventually(async()=>Number((await state('combat_owned')).hits)>beforeHits,'Owner melee opponent was not selected');
    await setMode('combat_owned','stay');await setMode('combat_owned','follow');
    beforeHits=Number((await state('combat_owned')).hits);await command('combatfixture arrow combat_enemy CombatOwner');
    await eventually(async()=>Number((await state('combat_owned')).hits)>beforeHits,'Projectile shooter was not resolved');
    await delay(2300);assert.ok(Number((await state('combat_owned')).minimumGap)>=20);return state('combat_owned');
  });
  await check('Owner range and game-mode changes cancel an active chase',async()=>{
    await command('combatfixture strike CombatOwner combat_enemy');await delay(150);assert.equal((await state('combat_owned')).active,'true');
    await move('CombatOwner',0,51);await eventually(async()=>{const s=await state('combat_owned');return s.active==='false'&&s.path==='false';},'Out-of-range owner left combat active');
    await move('CombatOwner',0,2);await command('combatfixture strike CombatOwner combat_enemy');await delay(150);await command('gamemode creative CombatOwner');
    assert.match(await command('combatfixture inspect combat_owned'),/active=false;path=false/);await command('gamemode survival CombatOwner');
  });
  await check('Stay cancels active combat immediately and babies do not defend',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 2 0');
    await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>await health('combat_enemy')<500,'Adult defense must work before Stay is selected');
    await command('tp @e[tag=combat_owned,limit=1] 0 4 0');await move('CombatOwner',0,2);
    await setMode('combat_owned','stay');assert.match(await command('combatfixture inspect combat_owned'),/active=false;path=false;.*stay=1/);
    const before=await health('combat_enemy');await command('combatfixture strike CombatOwner combat_enemy');await delay(1300);assert.equal(await health('combat_enemy'),before);
    await command('combatfixture age combat_owned 600000');await setMode('combat_owned','follow');
    await command('combatfixture strike CombatOwner combat_enemy');await delay(1300);assert.equal(await health('combat_enemy'),before);return state('combat_owned');
  });
  await check('Wild predators attack Survival while Creative and Spectator remain excluded',async()=>{
    await clear();await move('CombatOwner',0,2);await spawnDino('combat_wild velociraptor wild 0 0');
    let before=await phealth('CombatOwner');await delay(1400);assert.equal(await phealth('CombatOwner'),before);
    await command('gamemode spectator CombatOwner');await delay(1200);assert.equal(await phealth('CombatOwner'),before);
    await command('gamemode survival CombatOwner');await eventually(async()=>await phealth('CombatOwner')<before,'Wild predator did not attack Survival');
    return state('combat_wild');
  });
  await check('World PvP false, WorldGuard PvP deny, and friendly-fire teams each block indirect player attacks',async()=>{
    await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',3,0);
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture pvp false');
    let before=await phealth('CombatOther');await command('combatfixture strike CombatOther CombatOwner');await delay(1300);assert.equal(await phealth('CombatOther'),before);
    await command('combatfixture pvp true');await command('combatfixture strike CombatOther CombatOwner');await delay(1300);assert.equal(await phealth('CombatOther'),before);
    await command('region flag -w ark __global__ pvp allow');
    await command('team add combat_team');await command('team join combat_team CombatOwner');await command('team join combat_team CombatOther');await command('team modify combat_team friendlyFire false');
    await command('combatfixture strike CombatOther CombatOwner');await delay(1300);assert.equal(await phealth('CombatOther'),before);
    await command('team modify combat_team friendlyFire true');await command('combatfixture strike CombatOther CombatOwner');
    await eventually(async()=>await phealth('CombatOther')<before-1,'Allowed PvP did not produce native dino damage');
    await command('team remove combat_team');await command('region flag -w ark __global__ pvp deny');await command('combatfixture pvp false');
    return state('combat_owned');
  });
  await check('WorldGuard animal and mob-damage protection cancel native attacks',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 3 0');
    await command('region flag -w ark __global__ damage-animals deny');let before=await health('combat_enemy');await command('combatfixture strike CombatOwner combat_enemy');await delay(1400);assert.equal(await health('combat_enemy'),before);
    await command('region flag -w ark __global__ damage-animals allow');await command('combatfixture remove combat_owned');await spawnDino('combat_wild trex wild 0 0');
    await command('region flag -w ark __global__ mob-damage deny');before=await phealth('CombatOwner');await delay(1400);assert.equal(await phealth('CombatOwner'),before);await command('region flag -w ark __global__ mob-damage allow');
  });
  await check('Mounted swing requires a visible nearby target and one second between bites',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await command('clear CombatOwner');
    await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 0 2.5');
    assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(250);await aim('combat_enemy');
    let before=await health('combat_enemy');await command('combatfixture swing CombatOwner');await eventually(async()=>await health('combat_enemy')<before,'Mounted bite did not hit nearby visible enemy');
    before=await health('combat_enemy');await command('combatfixture swing CombatOwner');await command('combatfixture swing CombatOwner');assert.equal(await health('combat_enemy'),before);
    const wall=(await command('combatfixture wall CombatOwner combat_enemy')).trim().split('=')[1].replaceAll(',',' ');await delay(1100);before=await health('combat_enemy');await aim('combat_enemy');assert.match(await command('combatfixture swing CombatOwner'),/vehicle=true;ray=block/);await delay(150);assert.equal(await health('combat_enemy'),before);await command(`setblock ${wall} air`);
    return state('combat_owned');
  });
  await check('Mounted bites enforce world PvP, WorldGuard and friendly-fire rules independently of player damage events',async()=>{
    await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',0,2.25);await command('clear CombatOwner');await spawnDino('combat_owned trex CombatOwner 0 0');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(250);await aim('CombatOther');
    const biteBlocked=async()=>{await delay(1100);const before=await phealth('CombatOther');await command('combatfixture swing CombatOwner');await delay(100);assert.equal(await phealth('CombatOther'),before);};
    await command('region flag -w ark __global__ pvp allow');await command('combatfixture pvp false');await biteBlocked();
    await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp deny');await biteBlocked();
    await command('region flag -w ark __global__ pvp allow');await command('team add combat_team');await command('team join combat_team CombatOwner');await command('team join combat_team CombatOther');await command('team modify combat_team friendlyFire false');await biteBlocked();
    await command('team modify combat_team friendlyFire true');await delay(1100);const before=await phealth('CombatOther');await command('combatfixture swing CombatOwner');await eventually(async()=>await phealth('CombatOther')<before,'Allowed mounted PvP did not hit');await command('team remove combat_team');
    for(const [riderZ,targetZ] of [[0,2.5],[-2,.5]]){
      await clear();await command('gamemode survival @a');await move('CombatOwner',13,riderZ+2);await move('CombatOther',13,targetZ);await spawnDino(`combat_owned trex CombatOwner 13 ${riderZ}`);
      assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(250);await aim('CombatOther');await biteBlocked();
    }
    await command('region flag -w ark __global__ pvp deny');await command('combatfixture pvp false');
  });
  await check('Seven owned adult species defend and bite with their defined damage; wild herbivores stay passive',async()=>{
    for(const [species,damage] of Object.entries({triceratops:8,stegosaurus:8,brachiosaurus:10,pteranodon:4,trex:10,velociraptor:6,dilophosaurus:5})){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command('combatfixture victim combat_enemy 2 0');await command('combatfixture strike CombatOwner combat_enemy');
      await eventually(async()=>await health('combat_enemy')<500,`${species} did not defend`);assert.equal(await health('combat_enemy'),500-damage);
      await command('tp @e[tag=combat_owned,limit=1] 0 4 0');await command('tp @e[tag=combat_enemy,limit=1] 0 4 2.25');await move('CombatOwner',0,2);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(1100);await aim('combat_enemy');const before=await health('combat_enemy');await command('combatfixture swing CombatOwner');await eventually(async()=>await health('combat_enemy')===before-damage,`${species} mounted bite did not hit`);
      if(!['trex','velociraptor','dilophosaurus'].includes(species)){
        await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino(`combat_wild ${species} wild 0 0`);const before=await phealth('CombatOwner');await delay(1100);assert.equal(await phealth('CombatOwner'),before);
      }
    }
  });
  await check('Dilo visible black native spit hits at range, adds debuffs, and observes three-second cooldown',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await command('combatfixture victim combat_enemy 0 6');await aim('combat_enemy');
    const beforeLaunch=Number(Object.fromEntries((await command('combatfixture shots')).trim().split(';').map(x=>x.split('='))).launches);
    await command('combatfixture swing CombatOwner');await eventually(async()=>await health('combat_enemy')===497,'Dilo ranged spit did not hit');assert.match(await command('combatfixture effects combat_enemy'),/blind=true;slow=true/);
    await command('combatfixture swing CombatOwner');assert.equal(Number(Object.fromEntries((await command('combatfixture shots')).trim().split(';').map(x=>x.split('='))).launches),beforeLaunch+1);
    await command('combatfixture hold-shots true');await delay(3100);await aim('combat_enemy');await command('combatfixture swing CombatOwner');assert.match(await command('combatfixture shots'),/count=1;.*black=true/);
    await delay(2200);assert.match(await command('combatfixture shots'),/count=0;/);await command('combatfixture hold-shots false');
  });
  await check('Dilo native spit starts at the visible adult mouth and cannot cross a wall before the muzzle',async()=>{
    for(const [x,z] of [[0,6],[-6,0]]){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command(`combatfixture victim combat_enemy ${x} ${z}`);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await aim('combat_enemy');await command('combatfixture hold-shots true');await command('combatfixture swing CombatOwner');assert.match(await command('combatfixture shots'),/count=1;/);
      const point=Object.fromEntries((await command('combatfixture launch-point')).trim().split(';').map(x=>x.split('=')));assert.ok(Number(point.height)>0);assert.equal(Number(point.forward),0,'Native fallback must use the actual native eye');
      await delay(3100);await command('combatfixture model-calibrated combat_owned');await delay(150);await aim('combat_enemy');await command('combatfixture swing CombatOwner');
      const mouth=Object.fromEntries((await command('combatfixture launch-point')).trim().split(';').map(x=>x.split('=')));assert.ok(Math.abs(Number(mouth.height)-1.643194529315)<1e-5,JSON.stringify(mouth));assert.ok(Math.abs(Number(mouth.forward)-2.199375)<1e-5,JSON.stringify(mouth));assert.ok(Math.abs(Number(mouth.side))<1e-5,JSON.stringify(mouth));await command('combatfixture hold-shots false');
      await delay(3100);await move('CombatOwner',0,2);await command('attribute @e[tag=combat_enemy,limit=1] minecraft:scale base set 0.5');await command('combatfixture model-calibrated combat_owned');await delay(150);const launches=Number((await command('combatfixture shots')).match(/launches=(\d+)/)[1]);const wall=(await command('combatfixture muzzle-wall combat_owned combat_enemy')).trim();assert.match(wall,/los=true/);await command('combatfixture strike CombatOwner combat_enemy');await delay(500);assert.equal(Number((await command('combatfixture shots')).match(/launches=(\d+)/)[1]),launches,'A clipped model muzzle must not spit through the wall');await command(`setblock ${wall.match(/wall=([^;]+)/)[1].replaceAll(',',' ')} air`);
    }
  });
  await check('Dilo wild ranged aggro uses native spit; blocked and missed spit cannot hurt the target',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,6);const before=await phealth('CombatOwner');await spawnDino('combat_wild dilophosaurus wild 0 0');await eventually(async()=>await phealth('CombatOwner')<before,'Wild Dilo did not spit');assert.match(await command('combatfixture effects CombatOwner'),/blind=true;slow=true/);await command('effect clear CombatOwner');
    for(const obstruction of ['wall','miss']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(150);await command('combatfixture victim combat_enemy 0 6');await aim('combat_enemy');await command('combatfixture hold-shots true');await command('combatfixture swing CombatOwner');assert.match(await command('combatfixture shots'),/count=1;/);
      let wall;if(obstruction==='wall')wall=(await command('combatfixture spit-wall combat_owned combat_enemy')).trim().split('=')[1].replaceAll(',',' ');else await command('tp @e[tag=combat_enemy,limit=1] 3 4 6');
      await command('combatfixture release-shots');await delay(1200);assert.match(await command('combatfixture shots'),/count=0;/);assert.equal(await health('combat_enemy'),500);assert.match(await command('combatfixture effects combat_enemy'),/blind=false;slow=false/);if(wall)await command(`setblock ${wall} air`);
    }
  });
  await check('Dilo launch, hit and native damage cancellation leave no effects or leaked shots',async()=>{
    for(const gate of ['cancel-launch','cancel-hit','cancel-damage','zero-damage']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(150);await command('combatfixture victim combat_enemy 0 6');await aim('combat_enemy');await command(`combatfixture ${gate} true`);await command('combatfixture swing CombatOwner');await delay(1200);
      assert.equal(await health('combat_enemy'),500);assert.match(await command('combatfixture effects combat_enemy'),/blind=false;slow=false/);assert.match(await command('combatfixture shots'),/count=0;/);await command(`combatfixture ${gate} false`);
    }
  });
  await check('Dilo in-flight cleanup respects owner mode, target removal and source unload; foreign shots stay untouched',async()=>{
    for(const mutation of ['mode','stay','range','world','quit','death','target','source','unload']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('combatfixture victim combat_enemy 0 6');await command('combatfixture hold-shots true');
      await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>/count=1;/.test(await command('combatfixture shots')),'Defense did not release held spit');
      if(mutation==='mode')await command('gamemode creative CombatOwner');
      if(mutation==='stay')await setMode('combat_owned','stay');
      if(mutation==='range')await move('CombatOwner',0,51);
      if(mutation==='world')await command('execute in minecraft:the_nether run tp CombatOwner 0 80 0');
      if(mutation==='quit')clients.find(c=>c.name==='CombatOwner'&&!c.closed).close();
      if(mutation==='death')await command('kill CombatOwner');
      if(mutation==='target')await command('combatfixture remove combat_enemy');
      if(mutation==='source')await command('combatfixture remove combat_owned');
      if(mutation==='unload')await command('combatfixture unload combat_owned');
      await delay(200);assert.match(await command('combatfixture shots'),/count=0;/);
      if(mutation==='quit'){const rejoined=new Client('CombatOwner');clients.push(rejoined);await eventually(()=>rejoined.joined,'Owner did not reconnect');}
      if(mutation==='death'){clients.find(c=>c.name==='CombatOwner'&&!c.closed).send(0x0c,vi(0));await delay(500);}
      await command('combatfixture hold-shots false');
    }
    await command('combatfixture victim combat_enemy 2 0');assert.match(await command('combatfixture foreign-shot combat_enemy'),/cancelled=false;valid=true/);
  });
  await check('Dilo in-flight PvP, team and WorldGuard changes deny damage before impact',async()=>{
    for(const mutation of ['pvp','team','worldguard']){
      await clear();await command('gamemode survival @a');await move('CombatOwner',0,2);await move('CombatOther',0,6);await command('combatfixture pvp true');await command('region flag -w ark __global__ pvp allow');await command('combatfixture hold-shots true');
      await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(150);await aim('CombatOther');await command('combatfixture swing CombatOwner');assert.match(await command('combatfixture shots'),/count=1;/);const before=await phealth('CombatOther');
      if(mutation==='pvp')await command('combatfixture pvp false');
      if(mutation==='team'){await command('team add combat_team');await command('team join combat_team CombatOwner');await command('team join combat_team CombatOther');await command('team modify combat_team friendlyFire false');}
      if(mutation==='worldguard')await command('region flag -w ark __global__ pvp deny');
      await delay(200);assert.match(await command('combatfixture shots'),/count=0;/);assert.equal(await phealth('CombatOther'),before);assert.match(await command('combatfixture effects CombatOther'),/blind=false;slow=false/);
      if(mutation==='team')await command('team remove combat_team');await command('combatfixture hold-shots false');
    }
    await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('Legacy owned dinos outside prototype worlds retain passive combat',async()=>{
    await clear();await command('combatfixture create-world ark_legacy');await command('execute in minecraft:ark_legacy run tp CombatOwner 0 4 2');await delay(350);await command('gamemode survival CombatOwner');await command('execute in minecraft:ark_legacy run fill -8 3 -8 8 3 8 stone');
    await spawnDino('combat_owned trex CombatOwner 0 0 ark_legacy');await command('combatfixture victim combat_enemy 2 0 ark_legacy');await command('combatfixture strike CombatOwner combat_enemy');await delay(1300);assert.match(await command('execute in minecraft:ark_legacy run data get entity @e[tag=combat_enemy,limit=1] Health'),/500.0f/);assert.equal((await state('combat_owned')).active,'false');
    await command('execute in minecraft:overworld run tp @a 30 4 30');
  });
  await check('GriefPrevention installed handler failure denies companion attacks and warns once',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 3 0');
    await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>await health('combat_enemy')<500,'GP handler must be initialized by a successful attack');
    await command('combatfixture break-gp-protection combat_owned');const before=await health('combat_enemy');
    for(let i=0;i<2;i++){await command('combatfixture strike CombatOwner combat_enemy');await delay(1100);assert.equal(await health('combat_enemy'),before);}
    assert.equal((log.match(/GriefPrevention combat protection unavailable/g)||[]).length,1);await command('combatfixture restore-protection');
  });
  await check('Installed WorldGuard API failure denies companion attacks and warns once',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 3 0');
    await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>await health('combat_enemy')<500,'WorldGuard query must be initialized by a successful attack');
    await command('combatfixture break-protection combat_owned');const before=await health('combat_enemy');
    for(let i=0;i<2;i++){await command('combatfixture strike CombatOwner combat_enemy');await delay(1100);assert.equal(await health('combat_enemy'),before);}
    assert.equal((log.match(/WorldGuard combat protection unavailable/g)||[]).length,1);await command('combatfixture restore-protection');
  });
  await check('Plugin disable removes pending native spit before shutdown',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);
    await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('combatfixture victim combat_enemy 0 6');await command('combatfixture hold-shots true');
    await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>/count=1;/.test(await command('combatfixture shots')),'Native held shot was not launched');
    await command('combatfixture disable');assert.match(await command('combatfixture shots'),/count=0;/);
  });
  assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos/);
} catch(error){console.error(error.stack);process.exitCode=1;}
finally {
  clients.forEach(client=>client.close());
  if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
  report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
