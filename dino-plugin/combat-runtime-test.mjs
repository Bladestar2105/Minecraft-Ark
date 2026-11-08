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
const scopeMode = false;
const modelStack = false;
const stackCache = `${root}output/dino-model-spike-20261107/private/server`;
const cache = process.env.DINO_PAPER_CACHE ?? (modelStack ? stackCache : `${root}artifacts/renewal-20261103/server`);
const paperJar = process.env.DINO_PAPER_JAR ?? (modelStack ? `${stackCache}/paper.jar` : `${root}artifacts/renewal-20261103/paper-26.3-236.jar`);
await mkdir(`${plugin}build`, { recursive: true });
const directory = await mkdtemp(`${plugin}build/combat-smoke-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Damage and interaction stimuli use native Bukkit calls; mounted swing uses dispatched PlayerArmSwingEvent.',
] };
const reportPath = `${plugin}build/combat-runtime-report.json`;
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
async function check(name, action) { if(process.env.DINO_COMBAT_FOCUS&&!name.includes(process.env.DINO_COMBAT_FOCUS))return;try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }

const state = async tag => Object.fromEntries((await command(`combatfixture inspect ${tag}`)).split(';').map(field => field.split('=')));
const health = async tag => Number((await command(`data get entity @e[tag=${tag},limit=1] Health`)).match(/([\d.]+)f$/)?.[1]);
const phealth = async name => Number((await command(`combatfixture health ${name}`)).split('=')[1]);
const gpTimestamp = async name => Number((await command(`combatfixture gp-state ${name}`)).match(/timestamp=(\d+)/)[1]);
const move = async (name,x,z,y=4) => {await command(`tp ${name} ${x} ${y} ${z}`);await delay(150);};
const aim = async tag => {const angle=Object.fromEntries((await command(`combatfixture look CombatOwner ${tag}`)).trim().split(';').map(field=>field.split('=')));clients.find(client=>client.name==='CombatOwner'&&!client.closed).look(Number(angle.yaw),Number(angle.pitch));await delay(100);};
const spawnDino = async spec => {await command(`combatfixture spawn ${spec}`);await delay(150);};
const clear = async () => {await command('combatfixture cleanup');await delay(250);await command('gamemode creative @a');await command('tp @a 30 4 30');for(const name of ['CombatOwner','CombatOther'])await command(`combatfixture gp-immune ${name} false`);};
try {
  await symlink(`${cache}/libraries`,`${directory}/libraries`);
  for(const item of ['versions','cache']) await cp(`${cache}/${item}`,`${directory}/${item}`,{recursive:true});
  await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});
  await mkdir(`${directory}/plugins/WorldGuard/worlds/ark`,{recursive:true});
  await copyFile(paperJar,`${directory}/paper.jar`);
  await copyFile(`${plugin}build/ArkDinos-1.1.0.jar`,`${directory}/plugins/ArkDinos.jar`);
  report.jarSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
  for(const jar of ['worldedit-bukkit-7.4.6.jar','worldguard-bukkit-7.0.20.jar']) await copyFile(jar.startsWith('worldguard-')&&process.env.DINO_WORLDGUARD_JAR?process.env.DINO_WORLDGUARD_JAR:`${root}artifacts/renewal-20261103/server/plugins/${jar}`,`${directory}/plugins/${jar}`);
  report.worldguardSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/worldguard-bukkit-7.0.20.jar`)).digest('hex');
  await copyFile(process.env.DINO_GRIEFPREVENTION_JAR??`${root}output/dino-round2-20261108/candidate-plugins/GriefPrevention.jar`,`${directory}/plugins/GriefPrevention.jar`);
  report.griefPreventionSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/GriefPrevention.jar`)).digest('hex');
  await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark\n  - ark_legacy\nprototype-worlds:\n  - ark\n');
  await writeFile(`${directory}/plugins/WorldGuard/worlds/ark/regions.yml`,JSON.stringify({regions:{pvp_zone:{type:'cuboid',min:{x:12,y:-64,z:0},max:{x:14,y:319,z:1},priority:10,owners:{},members:{},flags:{pvp:'deny',build:'allow',interact:'allow',use:'allow',ride:'allow'}},__global__:{type:'global',priority:0,owners:{},members:{},flags:{'mob-damage':'allow','damage-animals':'allow',pvp:'deny'}}}}));
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${plugin}.deps/*`,'-d',`${directory}/fixture-classes`,`${plugin}src/combat/java/de/ark/dinos/CombatFixture.java`]);
  execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/CombatFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/combat/resources`,'.']);
  await writeFile(`${directory}/eula.txt`,'eula=true\n');
  await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
  server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});
  server.stdout.on('data',bytes=>{log+=bytes.toString();});server.stderr.on('data',bytes=>{log+=bytes.toString();});
  await eventually(()=>log.includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-4000))),'Paper failed to boot',90000);
  report.paperVersion=log.match(/Paper version ([^\n]+)/)?.[1];report.worldguardVersion=log.match(/Enabling WorldGuard v([^\n]+)/)?.[1];assert.ok(report.paperVersion&&report.worldguardVersion,'Native Paper/WorldGuard versions missing');
  report.griefPreventionVersion=log.match(/Enabling GriefPrevention v([^\n]+)/)?.[1];assert.ok(report.griefPreventionVersion,'Native GriefPrevention version missing');
  await command('whitelist off');await command('setworldspawn 0 4 0');await command('gamerule minecraft:spawn_mobs false');await command('gamerule minecraft:advance_time false');await command('gamerule minecraft:natural_health_regeneration false');await command('difficulty normal');
  const owner=new Client('CombatOwner'),other=new Client('CombatOther');clients.push(owner,other);
  await eventually(()=>owner.joined&&other.joined,'Native clients did not join',30000);
  await move('CombatOwner',0,0);await move('CombatOther',10,10);await command('fill -16 3 -16 40 3 40 stone');
  for(const name of ['CombatOwner','CombatOther']){await command(`attribute ${name} minecraft:max_health base set 500`);await command(`effect give ${name} instant_health 1 10 true`);}
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
      await command('combatfixture gp-immune CombatOwner false');const hits=Number((await state('combat_owned')).hits);await command('combatfixture swing CombatOwner');await eventually(async()=>await phealth('CombatOther')<initial,'GP-permitted mounted attack failed');
      assert.equal(await phealth('CombatOther'),initial-(species==='trex'?12:8));assert.equal(Number((await state('combat_owned')).hits),hits+1,'Permission query must not duplicate native damage');
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
      const prevented=Number((await command('combatfixture gp-allow-pvp true')).match(/preventions=(\d+)/)[1]);assert.ok(prevented>0);await command('combatfixture swing CombatOwner');await eventually(async()=>await phealth('CombatOther')<before,'Native GP PreventPvPEvent exception did not allow attack');
      assert.equal(await phealth('CombatOther'),before-(species==='trex'?12:8));await command('combatfixture gp-allow-pvp false');await command('combatfixture gp-clear-claims');
    }
    await command('combatfixture gp-config pvp_noCombatInPlayerLandClaims false');await command('combatfixture pvp false');await command('region flag -w ark __global__ pvp deny');
  });
  await check('GriefPrevention creature trust denies both carrier families and Dilo until container trust is granted',async()=>{
    for(const species of ['trex','triceratops','dilophosaurus']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino(`combat_owned ${species} CombatOwner 0 0`);await command(`combatfixture victim combat_enemy 0 ${species==='dilophosaurus'?6:2.25}`);
      assert.match(await command('combatfixture gp-claim CombatOther -5 5 2 10'),/claimCreated=true/);await command('clear CombatOwner');assert.match(await command('combatfixture click combat_owned CombatOwner'),/passengers=1/);await delay(200);await aim('combat_enemy');
      await command('combatfixture swing CombatOwner');await delay(500);assert.equal(await health('combat_enemy'),500);assert.match(await command('combatfixture effects combat_enemy'),/blind=false;slow=false/);
      assert.match(await command('combatfixture gp-trust CombatOwner combat_enemy'),/trusted=true/);await command('combatfixture swing CombatOwner');await eventually(async()=>await health('combat_enemy')<500,'GP trusted creature attack was blocked');
      assert.equal(await health('combat_enemy'),500-({trex:12,triceratops:8,dilophosaurus:3}[species]));await command('combatfixture gp-clear-claims');
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
      if(gate==='allow'){assert.equal(await phealth('CombatOther'),before-({trex:12,triceratops:8,dilophosaurus:3}[species]));assert.ok(await gpTimestamp('CombatOwner')>0);assert.ok(await gpTimestamp('CombatOther')>0);}
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
    await command('combatfixture click combat_owned CombatOwner stay');await command('combatfixture click combat_owned CombatOwner stay');
    let beforeHits=Number((await state('combat_owned')).hits);await command('combatfixture strike combat_enemy CombatOwner');
    await eventually(async()=>Number((await state('combat_owned')).hits)>beforeHits,'Owner melee opponent was not selected');
    await command('combatfixture click combat_owned CombatOwner stay');await command('combatfixture click combat_owned CombatOwner stay');
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
    assert.match(await command('combatfixture click combat_owned CombatOwner stay'),/active=false;path=false/);
    const before=await health('combat_enemy');await command('combatfixture strike CombatOwner combat_enemy');await delay(1300);assert.equal(await health('combat_enemy'),before);
    await command('combatfixture age combat_owned 600000');await command('combatfixture click combat_owned CombatOwner stay');
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
    for(const [species,damage] of Object.entries({triceratops:8,stegosaurus:8,brachiosaurus:10,pteranodon:4,trex:12,velociraptor:6,dilophosaurus:5})){
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
      await delay(3100);await command('combatfixture model-calibrated combat_owned');await command('combatfixture swing CombatOwner');
      const mouth=Object.fromEntries((await command('combatfixture launch-point')).trim().split(';').map(x=>x.split('=')));assert.ok(Math.abs(Number(mouth.height)-1.4288648081)<1e-5,JSON.stringify(mouth));assert.ok(Math.abs(Number(mouth.forward)-1.9125)<1e-5,JSON.stringify(mouth));assert.ok(Math.abs(Number(mouth.side))<1e-5,JSON.stringify(mouth));await command('combatfixture hold-shots false');
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
  await check('Dilo in-flight cleanup respects owner mode, target removal, source unload and plugin disable; foreign shots stay untouched',async()=>{
    for(const mutation of ['mode','stay','range','world','quit','death','target','source','unload','disable']){
      await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned dilophosaurus CombatOwner 0 0');await command('combatfixture victim combat_enemy 0 6');await command('combatfixture hold-shots true');
      await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>/count=1;/.test(await command('combatfixture shots')),'Defense did not release held spit');
      if(mutation==='mode')await command('gamemode creative CombatOwner');
      if(mutation==='stay')await command('combatfixture click combat_owned CombatOwner stay');
      if(mutation==='range')await move('CombatOwner',0,51);
      if(mutation==='world')await command('execute in minecraft:the_nether run tp CombatOwner 0 80 0');
      if(mutation==='quit')clients.find(c=>c.name==='CombatOwner'&&!c.closed).close();
      if(mutation==='death')await command('kill CombatOwner');
      if(mutation==='target')await command('combatfixture remove combat_enemy');
      if(mutation==='source')await command('combatfixture remove combat_owned');
      if(mutation==='unload')await command('combatfixture unload combat_owned');
      if(mutation==='disable')await command('combatfixture disable');
      await delay(200);assert.match(await command('combatfixture shots'),/count=0;/);if(mutation==='disable')await command('combatfixture enable');
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
    assert.equal((log.match(/GriefPrevention combat protection unavailable/g)||[]).length,1);await command('combatfixture disable');await command('combatfixture enable');
  });
  await check('Installed WorldGuard API failure denies companion attacks and warns once',async()=>{
    await clear();await command('gamemode survival CombatOwner');await move('CombatOwner',0,2);await spawnDino('combat_owned trex CombatOwner 0 0');await command('combatfixture victim combat_enemy 3 0');
    await command('combatfixture strike CombatOwner combat_enemy');await eventually(async()=>await health('combat_enemy')<500,'WorldGuard query must be initialized by a successful attack');
    await command('combatfixture break-protection combat_owned');const before=await health('combat_enemy');
    for(let i=0;i<2;i++){await command('combatfixture strike CombatOwner combat_enemy');await delay(1100);assert.equal(await health('combat_enemy'),before);}
    assert.equal((log.match(/WorldGuard combat protection unavailable/g)||[]).length,1);
  });
  assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos/);
} catch(error){console.error(error.stack);process.exitCode=1;}
finally {
  clients.forEach(client=>client.close());
  if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
  report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
