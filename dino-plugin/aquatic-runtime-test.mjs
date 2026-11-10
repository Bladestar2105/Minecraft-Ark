#!/usr/bin/env node
// Disposable loopback Paper244 fixture and direct protocol777 clients; no historical world is opened.
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
const cache = process.env.DINO_PAPER_CACHE ?? `${root}output/dino-round2-20261108/paper244-cache`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${root}output/dino-round2-20261108/paper-26.3-244.jar`;
const output=process.env.DINO_AQUATIC_OUTPUT ?? `${plugin}build`;
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/aquatic-smoke-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Damage uses native Mob.attack and interactions use Bukkit calls or dispatched native events.',
  'The unloaded-neighbor guard uses a remote water pocket, a dispatched EntityMoveEvent, and an AI/velocity stimulus; natural entity ticking at an unloaded frontier is not reproduced.',
] };
const reportPath = `${output}/aquatic-runtime-report.json`;
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
      if (id === 2) { this.uuid = uuidString(body.subarray(0, 16)); this.send(3); this.phase = 'configuration'; this.send(0, Buffer.concat([string('en_us'), Buffer.from([6]), vi(0), Buffer.from([1, 127]), vi(1), Buffer.from([0, 1]), vi(2)])); }
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
async function check(name, action) { if(process.env.DINO_AQUATIC_FOCUS&&!name.includes(process.env.DINO_AQUATIC_FOCUS))return;try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }


const inspect = async tag => Object.fromEntries((await command(`aquaticfixture inspect ${tag}`)).trim().split(';').map(field=>field.split('=')));
const phealth = async name => Number((await command(`aquaticfixture health ${name}`)).split('=')[1]);
const move = async (name,x,y,z) => {await command(`tp ${name} ${x} ${y} ${z}`);await delay(150);};
const spawnMosa = async (x=0,y=10,z=0) => {await command(`aquaticfixture spawn aquatic_mosa mosasaurus ${x} ${y} ${z}`);await delay(250);};
const resetMosa = async (calibrated=true,x=0,y=10,z=10.5) => {
  await command('gamemode creative AquaticObserver');await command('aquaticfixture cleanup');await spawnMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command(`aquaticfixture calibrated aquatic_mosa ${calibrated}`);await move('AquaticObserver',x,y,z);await command('effect give AquaticObserver instant_health 1 10 true');await eventually(async()=>await phealth('AquaticObserver')===500,'Observer healing did not apply');
};
async function boot() {
  const offset=log.length;server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});server.stdout.on('data',bytes=>{log+=bytes.toString();});server.stderr.on('data',bytes=>{log+=bytes.toString();});
  await eventually(()=>log.slice(offset).includes('Done (')||(server.exitCode!==null&&assert.fail(log.slice(-4000))),'Paper failed to boot',90000);
}
try {
  await symlink(`${cache}/libraries`,`${directory}/libraries`);for(const item of ['versions','cache'])await cp(`${cache}/${item}`,`${directory}/${item}`,{recursive:true});
  await mkdir(`${directory}/plugins/ArkDinos`,{recursive:true});await mkdir(`${directory}/plugins/WorldGuard/worlds/ark`,{recursive:true});
  await copyFile(paperJar,`${directory}/paper.jar`);await copyFile(process.env.DINO_PLUGIN_JAR ?? `${plugin}build/ArkDinos-1.2.0.jar`,`${directory}/plugins/ArkDinos.jar`);
  report.jarSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
  await copyFile(`${root}artifacts/renewal-20261103/server/plugins/worldedit-bukkit-7.4.6.jar`,`${directory}/plugins/WorldEdit.jar`);
  await copyFile(process.env.DINO_WORLDGUARD_JAR??`${root}output/dino-round2-20261108/candidate-plugins/worldguard-bukkit-7.0.21.jar`,`${directory}/plugins/WorldGuard.jar`);
  await copyFile(process.env.DINO_GRIEFPREVENTION_JAR??`${root}output/dino-round2-20261108/candidate-plugins/GriefPrevention.jar`,`${directory}/plugins/GriefPrevention.jar`);
  await writeFile(`${directory}/plugins/ArkDinos/config.yml`,'survival-worlds:\n  - ark\nprototype-worlds:\n  - ark\nwild-spawning:\n  enabled: false\n');
  await writeFile(`${directory}/plugins/WorldGuard/worlds/ark/regions.yml`,JSON.stringify({regions:{__global__:{type:'global',priority:0,owners:{},members:{},flags:{'mob-damage':'allow','damage-animals':'allow',pvp:'deny'}}}}));
  await mkdir(`${directory}/fixture-classes`);execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${plugin}.deps/*`,'-d',`${directory}/fixture-classes`,`${plugin}src/aquatic/java/de/ark/dinos/AquaticFixture.java`]);
  execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/AquaticFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${plugin}src/aquatic/resources`,'.']);
  await writeFile(`${directory}/eula.txt`,'eula=true\n');
  await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
  await boot();
  report.paperVersion=log.match(/Paper version ([^\n]+)/)?.[1];report.worldguardVersion=log.match(/Enabling WorldGuard v([^\n]+)/)?.[1];report.griefPreventionVersion=log.match(/Enabling GriefPrevention v([^\n]+)/)?.[1];assert.ok(report.paperVersion&&report.worldguardVersion&&report.griefPreventionVersion);
  await command('whitelist off');await command('setworldspawn 0 4 0');await command('gamerule minecraft:spawn_mobs false');await command('gamerule minecraft:natural_health_regeneration false');await command('difficulty normal');
  const observer=new Client('AquaticObserver');clients.push(observer);await eventually(()=>observer.joined,'Native observer did not join',30000);await command('gamemode creative AquaticObserver');
  await command('fill -25 4 -25 25 4 25 stone');await command('fill -25 5 -25 25 16 -25 glass');await command('fill -25 5 25 25 16 25 glass');await command('fill -25 5 -24 -25 16 24 glass');await command('fill 25 5 -24 25 16 24 glass');await command('fill -24 5 -24 24 15 24 water');
  await move('AquaticObserver',0,10,3);await command('attribute AquaticObserver minecraft:max_health base set 500');await command('effect give AquaticObserver instant_health 1 10 true');await command('effect give AquaticObserver water_breathing infinite 0 true');
  await spawnMosa();await command('aquaticfixture spawn aquatic_ordinary ordinary 12 8 0');await delay(150);
  await check('Mosa has a large wild Dolphin carrier while ordinary dolphins retain native goals',async()=>{
    const mosa=await inspect('aquatic_mosa'),ordinary=await inspect('aquatic_ordinary');assert.equal(Number(mosa.scale),4);assert.equal(Number(mosa.maxHealth),200);assert.equal(Number(ordinary.scale),1);assert.ok(ordinary.goals.includes('dolphin_jump'));assert.ok(!mosa.goals.includes('dolphin_jump'));assert.ok(!mosa.goals.includes('breath_air'));assert.ok(mosa.goals.includes('random_swimming'));return {mosa,ordinary};
  });
  await check('Native swimming moves continuously underwater without air jumps or creative attacks',async()=>{
    const before=await phealth('AquaticObserver'),start=await inspect('aquatic_mosa');await delay(5000);const state=await inspect('aquatic_mosa');assert.ok(Number(state.distance)-Number(start.distance)>1,JSON.stringify(state));assert.ok(Number(state.spanX)>1||Number(state.spanZ)>1,JSON.stringify(state));assert.equal(Number(state.dry),0);assert.equal(Number(state.airJumps),0);assert.equal(await phealth('AquaticObserver'),before);return state;
  });
  await check('Submerged Mosa replenishes air and moisture without drowning',async()=>{
    const before=Number((await inspect('aquatic_mosa')).health);await command('aquaticfixture air aquatic_mosa');await delay(500);const state=await inspect('aquatic_mosa');assert.ok(Number(state.air)>100);assert.ok(Number(state.moistness)>100);assert.equal(Number(state.health),before);return state;
  });
  await check('Mosa cannot be tamed, mounted, leashed, portalled or teleported to another world',async()=>{
    await resetMosa();assert.match(await command('aquaticfixture click aquatic_mosa AquaticObserver'),/cancelled=true;seeds=2;owned=false/);
    assert.match(await command('aquaticfixture mount aquatic_mosa AquaticObserver'),/mounted=false;passengers=0/);assert.match(await command('aquaticfixture leash aquatic_mosa AquaticObserver'),/cancelled=true/);assert.match(await command('aquaticfixture portal aquatic_mosa'),/cancelled=true/);assert.match(await command('aquaticfixture cross-world aquatic_mosa'),/teleported=false/);
  });
  await check('A modeled Mosa bites from its visible mouth, and raw native bites cannot bypass the one-second cooldown',async()=>{
    await resetMosa();const before=await phealth('AquaticObserver');await command('gamemode survival AquaticObserver');await eventually(async()=>await phealth('AquaticObserver')<before,'Mosa did not bite water player beside its visible mouth');assert.equal(await phealth('AquaticObserver'),before-16);
    const after=await phealth('AquaticObserver');await command('aquaticfixture native-bite aquatic_mosa AquaticObserver');assert.equal(await phealth('AquaticObserver'),after);await delay(1200);assert.ok(Number((await inspect('aquatic_mosa')).minimumGap)>=20);
  });
  await check('Mosa protects Creative, Spectator, land players and players beside the tail instead of the mouth',async()=>{
    for(const scenario of ['creative','spectator','land','tail']){
      await resetMosa(true,0,scenario==='land'?17:10,scenario==='tail'?1:10.5);const before=await phealth('AquaticObserver');await command(`gamemode ${scenario==='spectator'?'spectator':scenario==='creative'?'creative':'survival'} AquaticObserver`);await delay(scenario==='tail'?350:1100);assert.equal(await phealth('AquaticObserver'),before,scenario);
      await command('aquaticfixture native-bite aquatic_mosa AquaticObserver');assert.equal(await phealth('AquaticObserver'),before,`uncommanded ${scenario}`);
    }
  });
  await check('Native fallback uses the real carrier mouth instead of an invisible nine-block bite',async()=>{
    await resetMosa(false);const before=await phealth('AquaticObserver');await command('gamemode survival AquaticObserver');await delay(500);assert.equal(await phealth('AquaticObserver'),before);
    await resetMosa(false,0,10,2);await command('gamemode survival AquaticObserver');await eventually(async()=>await phealth('AquaticObserver')<before,'Native fallback failed to bite close water target');
  });
  await check('WorldGuard mob-damage and native event cancellation or zero damage stop Mosa bites',async()=>{
    for(const gate of ['worldguard','cancel-damage','zero-damage']){
      await resetMosa();if(gate==='worldguard')await command('region flag -w ark __global__ mob-damage deny');else await command(`aquaticfixture ${gate} true`);
      const before=await phealth('AquaticObserver');await command('gamemode survival AquaticObserver');await delay(1300);assert.equal(await phealth('AquaticObserver'),before,gate);
      if(gate==='worldguard')await command('region flag -w ark __global__ mob-damage allow');else await command(`aquaticfixture ${gate} false`);
    }
  });
  await check('Stranded Mosa freezes on land without jumping and resumes swimming when returned to water',async()=>{
    await resetMosa();await command('aquaticfixture teleport aquatic_mosa 0 4 30 0');await delay(1100);const dry=await inspect('aquatic_mosa');assert.equal(dry.ai,'false');assert.equal(Number(dry.airJumps),0);assert.equal(Number(dry.z),30);assert.equal(Number(dry.x),0);
    await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await delay(150);assert.equal((await inspect('aquatic_mosa')).ai,'true');return dry;
  });
  await check('Controlled native movement and timer guards reject an unloaded neighbor without chunk loads or force tickets',async()=>{
    await command('gamemode creative AquaticObserver');await move('AquaticObserver',0,10,3);await command('aquaticfixture cleanup');assert.match(await command('aquaticfixture chunk 6 2'),/loaded=false;forced=0/);
    await command('aquaticfixture load-pocket');
    await command('fill 95 4 -9 126 4 9 stone');await command('fill 95 5 -9 126 16 -9 glass');await command('fill 95 5 9 126 16 9 glass');await command('fill 95 5 -8 95 16 8 glass');await command('fill 126 5 -8 126 16 8 glass');await command('fill 96 5 -8 125 15 8 water');await command('aquaticfixture spawn-paused aquatic_mosa mosasaurus 110 10 0');await delay(250);await command('aquaticfixture teleport aquatic_mosa 110 10 0 90');await delay(150);
    assert.match(await command('aquaticfixture chunk 6 2'),/loaded=false;forced=0/);const before=await inspect('aquatic_mosa');assert.equal(before.managed,'true');assert.equal(before.blockWater,'true');assert.match(await command('aquaticfixture boundary-move aquatic_mosa'),/cancelled=true/);await command('aquaticfixture velocity aquatic_mosa 0 0 2');await delay(700);const after=await inspect('aquatic_mosa');assert.equal(after.ai,'false');assert.equal(after.path,'false');assert.equal(Number(after.velocity),0);assert.equal(Number(after.z),Number(before.z));assert.match(await command('aquaticfixture chunk 6 2'),/loaded=false;forced=0/);
    await command('aquaticfixture breach aquatic_mosa');await delay(300);const denied=await inspect('aquatic_mosa');assert.equal(denied.breachOrigin,'null');assert.equal(denied.ai,'false');await command('aquaticfixture breach aquatic_mosa 1200');
    await move('AquaticObserver',110,10,0);await eventually(async()=>/safe=true;navigation=true/.test(await command('aquaticfixture safety aquatic_mosa')),'Approach did not naturally load the navigation perimeter',20000);
    const distance=Number((await inspect('aquatic_mosa')).distance);await delay(5000);const resumed=await inspect('aquatic_mosa');assert.ok(Number(resumed.distance)>distance+1,'Mosa did not keep swimming when the observer approached');assert.ok(Math.hypot(Number(resumed.x)-Number(before.x),Number(resumed.z)-Number(before.z))>1,'Only vertical settling occurred after approach');assert.equal(resumed.uuid,before.uuid);assert.equal(resumed.dry,'0');assert.match(await command('aquaticfixture safety aquatic_mosa'),/forced=0/);return {before,after,denied,resumed};
  });
  await check('Unapproved worlds retain an ordinary Dolphin instead of activating Mosa swimming or aggression',async()=>{
    await command('aquaticfixture cleanup');await command('aquaticfixture create-world ark_outside');await command('execute in minecraft:ark_outside run fill -16 4 -16 16 4 16 stone');await command('execute in minecraft:ark_outside run fill -16 5 -16 16 15 16 water');
    await command('execute in minecraft:ark_outside run tp AquaticObserver 0 10 3');await delay(200);await command('aquaticfixture spawn aquatic_outside mosasaurus 0 10 0 ark_outside');await command('gamemode survival AquaticObserver');const before=await phealth('AquaticObserver');await delay(1100);const outside=await inspect('aquatic_outside');assert.equal(outside.managed,'false');assert.equal(Number(outside.scale),1);assert.equal(await phealth('AquaticObserver'),before);assert.ok(outside.goals.includes('dolphin_jump'));await command('execute in minecraft:overworld run tp AquaticObserver 0 10 3');return outside;
  });
  await check('Real clean restart restores the same injured wild Mosa, its controller, swimming, oxygen and mouth attack',async()=>{
    await resetMosa();await command('aquaticfixture health-set aquatic_mosa 173');const original=await inspect('aquatic_mosa');await command('aquaticfixture disable');const disabled=await inspect('aquatic_mosa');assert.equal(disabled.ai,'false');assert.equal(disabled.managed,'false');await command('save-all flush');
    clients.filter(client=>!client.closed).forEach(client=>client.close());const exited=once(server,'exit');server.stdin.write('stop\n');await exited;await boot();
    const returned=new Client('AquaticObserver');clients.push(returned);await eventually(()=>returned.joined,'Observer did not reconnect after restart',30000);await command('gamemode creative AquaticObserver');await delay(1200);const restored=await inspect('aquatic_mosa');assert.equal(restored.uuid,original.uuid);assert.equal(Number(restored.health),173);assert.equal(restored.managed,'true');assert.equal(restored.ai,'true');assert.equal(Number(restored.scale),4);
    await command('aquaticfixture air aquatic_mosa');await delay(300);assert.ok(Number((await inspect('aquatic_mosa')).air)>100);const distance=Number((await inspect('aquatic_mosa')).distance);await eventually(async()=>Number((await inspect('aquatic_mosa')).distance)>distance+1,'Restarted Mosa did not swim',12000);
    await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command('aquaticfixture calibrated aquatic_mosa true');await move('AquaticObserver',0,10,10.5);const before=await phealth('AquaticObserver');await command('gamemode survival AquaticObserver');await eventually(async()=>await phealth('AquaticObserver')<before,'Restarted Mosa controller did not attack at its mouth');return {original,disabled,restored};
  });
  await check('Controlled breach clears the full body above the surface, lands in water and resumes swimming',async()=>{
    await resetMosa();await command('aquaticfixture activation 16 6 4');await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');
    assert.match(await command('aquaticfixture breach aquatic_mosa'),/forced=true/);await delay(8000);const landed=await inspect('aquatic_mosa');
    assert.ok(Number(landed.fullAir)>0,`Full model never cleared surface: ${JSON.stringify(landed)}`);assert.ok(Number(landed.dry)>0);assert.match(await command('aquaticfixture safety aquatic_mosa'),/safe=true;navigation=true/);
    const distance=Number(landed.distance);await delay(3000);const swimming=await inspect('aquatic_mosa');assert.ok(Number(swimming.distance)>distance+1);assert.equal(swimming.uuid,landed.uuid);assert.equal(swimming.health,'200.0');return {landed,swimming};
  });
  await check('Breach preflight rejects thin interior glass, a low roof and an obstructed landing',async()=>{
    const results=[];
    for(const [name,x,y,z] of [['interior',2,20,2],['roof',0,16,0],['landing',2,9,2]]) {
      await resetMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command(`setblock ${x} ${y} ${z} glass`);await command('aquaticfixture breach aquatic_mosa');await delay(500);
      const state=await inspect('aquatic_mosa');assert.equal(state.breachOrigin,'null',name);assert.equal(state.fullAir,'0');results.push({name,state});await command(`setblock ${x} ${y} ${z} ${y<16?'water':'air'}`);
    }
    return results;
  });
  await check('Breach returns safely after a cancelled movement or a new obstruction in its air corridor',async()=>{
    const results=[];
    for(const scenario of ['cancel','new-glass']) {
      await resetMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command('aquaticfixture breach aquatic_mosa');await eventually(async()=>(await inspect('aquatic_mosa')).breachOrigin!=='null','Breach did not start');
      if(scenario==='cancel')await command('aquaticfixture cancel-move true');else await command('setblock 2 23 2 glass');
      await delay(1500);if(scenario==='new-glass')await command('setblock 2 23 2 air');
      await eventually(async()=>(await inspect('aquatic_mosa')).breachOrigin==='null','Interrupted breach did not land',12000);
      const state=await inspect('aquatic_mosa');assert.match(await command('aquaticfixture safety aquatic_mosa'),/safe=true;navigation=true/);assert.equal(state.health,'200.0');results.push({scenario,state});
    }
    return results;
  });
  await check('Breach survives controller close and restore in midair with the same entity and a safe landing',async()=>{
    await resetMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command('aquaticfixture breach aquatic_mosa');await eventually(async()=>Number((await inspect('aquatic_mosa')).y)>18.4,'Breach did not reach midair');
    const original=await inspect('aquatic_mosa');assert.match(await command('aquaticfixture cycle-controller aquatic_mosa'),/restored=true/);
    await eventually(async()=>(await inspect('aquatic_mosa')).breachOrigin==='null','Restored Mosa did not safely descend',15000);const landed=await inspect('aquatic_mosa');assert.equal(landed.uuid,original.uuid);assert.equal(landed.health,'200.0');assert.match(await command('aquaticfixture safety aquatic_mosa'),/safe=true;navigation=true/);return {original,landed};
  });
  await check('Breach survives a real clean midair restart and resumes submerged native swimming',async()=>{
    await resetMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');await command('aquaticfixture breach aquatic_mosa');await eventually(async()=>Number((await inspect('aquatic_mosa')).y)>18.4,'Breach did not reach restart altitude');const original=await inspect('aquatic_mosa');
    await command('aquaticfixture disable');const disabled=await inspect('aquatic_mosa');assert.equal(disabled.ai,'false');assert.notEqual(disabled.breachOrigin,'null');
    clients.filter(client=>!client.closed).forEach(client=>client.close());const exited=once(server,'exit');server.stdin.write('stop\n');await exited;await boot();
    const returned=new Client('AquaticObserver');clients.push(returned);await eventually(()=>returned.joined,'Breach observer did not reconnect',30000);await command('gamemode creative AquaticObserver');await move('AquaticObserver',0,10,3);
    await eventually(async()=>(await inspect('aquatic_mosa')).breachOrigin==='null','Restarted Mosa remained in midair',15000);const landed=await inspect('aquatic_mosa');assert.equal(landed.uuid,original.uuid);assert.equal(landed.health,'200.0');assert.match(await command('aquaticfixture safety aquatic_mosa'),/safe=true;navigation=true/);
    const distance=Number(landed.distance);await eventually(async()=>Number((await inspect('aquatic_mosa')).distance)>distance+1,'Restarted breached Mosa did not resume swimming',10000);return {original,disabled,landed};
  });
  await check('Boundary recovery clears a rejected upward native path without stranding the Mosa',async()=>{
    await resetMosa();await command('aquaticfixture activation 16 6 4');await move('AquaticObserver',40,10,0);
    await command('aquaticfixture teleport aquatic_mosa 0 12.4974 0 0');await command('aquaticfixture path aquatic_mosa 3 14 0');await delay(2500);
    const blocked=await inspect('aquatic_mosa'),safety=await command('aquaticfixture safety aquatic_mosa');await delay(5000);const beforeClear=await inspect('aquatic_mosa');
    report.boundaryProbe={blocked,safety,beforeClear};assert.ok(Number(beforeClear.distance)-Number(blocked.distance)>1,'Mosa remained stuck at a safe water boundary');
    await command('aquaticfixture teleport aquatic_mosa 11.4 10 0 0');await command('aquaticfixture path aquatic_mosa -10 10 0');await delay(2500);const wall=await inspect('aquatic_mosa');await delay(5500);const wallAfter=await inspect('aquatic_mosa');
    Object.assign(report.boundaryProbe,{wall,wallAfter,wallSafety:await command('aquaticfixture safety aquatic_mosa')});assert.ok(Number(wallAfter.distance)-Number(wall.distance)>1,'Mosa remained stuck at a yaw boundary');assert.equal(wallAfter.dry,'0');return report.boundaryProbe;
  });
  await check('Shore-distance water activation 16 versus 64 on the same safe loaded Mosa',async()=>{
    await command('gamemode creative AquaticObserver');await command('aquaticfixture cleanup');
    const prior=Object.fromEntries((await command('aquaticfixture activation')).trim().split(';').map(row=>row.split('=')));
    await command('aquaticfixture activation 16 6 4');await move('AquaticObserver',40,10,0);
    await eventually(async()=>/loaded=true/.test(await command('aquaticfixture chunk -2 -2')),'Shore observer must load the navigation perimeter',20000);
    await spawnMosa();await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');
    await eventually(async()=>/safe=true;navigation=true/.test(await command('aquaticfixture safety aquatic_mosa')),'Shore fixture must have safe water and all 25 navigation chunks',15000);
    const nativePath=async()=>{await command('aquaticfixture teleport aquatic_mosa 0 10 0 0');return command('aquaticfixture path aquatic_mosa 4 9 4');};
    await delay(12000);const path16=await nativePath(),before16=await inspect('aquatic_mosa'),safe16=await command('aquaticfixture safety aquatic_mosa');assert.match(safe16,/safe=true;navigation=true;activation=WATER/);assert.match(safe16,/forced=0/);
    await delay(10000);const after16=await inspect('aquatic_mosa');
    assert.match(await command('aquaticfixture activation 64'),/waterActivation=64;view=6;simulation=4/);
    const path64=await nativePath(),before64=await inspect('aquatic_mosa'),safe64=await command('aquaticfixture safety aquatic_mosa');
    await delay(10000);const after64=await inspect('aquatic_mosa'),afterSafe64=await command('aquaticfixture safety aquatic_mosa');
    const travel16=Number(after16.horizontal)-Number(before16.horizontal),travel64=Number(after64.horizontal)-Number(before64.horizontal);
    report.activationProbe={before16,after16,safe16,path16,before64,after64,safe64,path64,afterSafe64,travel16,travel64};
    assert.equal(after64.uuid,before16.uuid);assert.ok(travel16>1,`16-block activation should swim horizontally from shore, moved ${travel16}`);
    await command(`aquaticfixture activation ${prior.waterActivation} ${prior.view} ${prior.simulation}`);return report.activationProbe;
  });
  await check('Persisted live surface-margin breach resumes from stopped NoAI when loaded without changing entity',async()=>{
    await resetMosa();await command('aquaticfixture activation 16 6 4');await command('aquaticfixture teleport aquatic_mosa 0 12.646769972037134 0 -157.90941');
    await command('data modify entity @e[tag=aquatic_mosa,limit=1] BukkitValues."arkdinos:breach-origin-y" set value 12.49884014719662d');await command('data modify entity @e[tag=aquatic_mosa,limit=1] NoAI set value 1b');
    const before=await inspect('aquatic_mosa'),safetyBefore=await command('aquaticfixture safety aquatic_mosa');await delay(5000);const after=await inspect('aquatic_mosa'),safetyAfter=await command('aquaticfixture safety aquatic_mosa');
    report.surfaceMarginProbe={before,safetyBefore,after,safetyAfter};assert.equal(after.uuid,before.uuid);assert.equal(after.breachOrigin,'null');assert.match(safetyAfter,/safe=true;navigation=true/);assert.ok(Number(after.horizontal)-Number(before.horizontal)>1,'Recovered Mosa must resume horizontal swimming');assert.equal(after.health,before.health);return report.surfaceMarginProbe;
  });
  await check('Corner recovery observation separates geometric yaw rejection from activation',async()=>{
    await resetMosa();await command('aquaticfixture activation 64 6 4');await command('aquaticfixture teleport aquatic_mosa -0.04691707458942336 9.975689623215539 13.387228889792365 117.513275');await command('aquaticfixture path aquatic_mosa 0 9 0');
    const before=await inspect('aquatic_mosa');await delay(25000);const natural=await inspect('aquatic_mosa');await command('aquaticfixture path aquatic_mosa 0 9 0');await delay(5000);const retried=await inspect('aquatic_mosa');
    report.cornerProbe={before,natural,retried,safety:await command('aquaticfixture safety aquatic_mosa')};assert.ok(Number(retried.horizontal)-Number(before.horizontal)>1,'Corner still had no horizontal swimming after30s and a fresh inward path');return report.cornerProbe;
  });
  assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos/);
} catch(error){console.error(error.stack);process.exitCode=1;}
finally {
  clients.forEach(client=>client.close());if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
  report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
