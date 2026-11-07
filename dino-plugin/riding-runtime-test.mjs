#!/usr/bin/env node
// Disposable loopback Paper236 fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, writeFile, copyFile, symlink } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = process.env.DINO_PAPER_CACHE ?? `${root}artifacts/renewal-20261103/server`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${root}artifacts/renewal-20261103/paper-26.3-236.jar`;
await mkdir(`${plugin}build`, { recursive: true });
const directory = await mkdtemp(`${plugin}build/riding-smoke-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Chunk-unload handler is exercised with a fixture-dispatched EntitiesUnloadEvent; actual unload while riding is not forced.',
] };
const reportPath = `${plugin}build/riding-runtime-report.json`;
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
  look(yaw) { const body = Buffer.alloc(9); body.writeFloatBE(yaw); body.writeFloatBE(0, 4); body[8] = 1; this.send(0x20, body); }
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
async function check(name, action) { try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }
const selector = tag => `@e[tag=${tag},limit=1]`;
const data = (tag, path) => command(`data get entity ${selector(tag)} ${path}`);
async function position(tag) { const response = await data(tag, 'Pos'); const match = response.match(/\[([^\]]+)\]\s*$/); assert.ok(match, `No position for ${tag}: ${response}`); return match[1].split(',').map(parseFloat); }
async function nativeUuid(tag) { const values = (await data(tag, 'UUID')).match(/\[I;([^\]]+)\]/)[1].split(',').map(Number); const raw = Buffer.alloc(16); values.forEach((value, index) => raw.writeInt32BE(value, index * 4)); return uuidString(raw); }
async function passengers(tag) { return command(`dinofixture inspect ${tag}`); }
async function tp(client, x, z) { await command(`tp ${client.name} ${x} 4 ${z}`); await delay(250); }
async function click(client, tag, sneak = false) { const [x, , z] = await position(tag); await tp(client, x, z + 2); const uuid = await nativeUuid(tag); await eventually(() => client.entities.has(uuid), 'Native entity spawn not received'); client.input(sneak ? 32 : 0); await delay(100); client.interact(client.entities.get(uuid), sneak); await delay(250); client.input(0); }
async function mount(client, tag) { await command(`clear ${client.name}`); await click(client, tag); await eventually(async () => /passengers=1/.test(await passengers(tag)), `Owner failed to mount ${tag}`); }
async function stopped(tag) { const state = await passengers(tag); assert.match(state, /passengers=0;aware=false;path=false/); return state; }

try {
  for (const item of ['libraries', 'versions', 'cache']) await symlink(`${cache}/${item}`, `${directory}/${item}`);
  await mkdir(`${directory}/plugins`, { recursive: true });
  await copyFile(paperJar, `${directory}/paper.jar`);
  report.jarSha256 = crypto.createHash('sha256').update(await readFile(`${plugin}build/ArkDinos-1.1.0.jar`)).digest('hex');
  await copyFile(`${plugin}build/ArkDinos-1.1.0.jar`, `${directory}/plugins/ArkDinos.jar`);
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`, ['--release', '25', '-sourcepath', '', '-cp', `${plugin}.deps/*`, '-d', `${directory}/fixture-classes`, `${plugin}src/runtime/java/de/ark/dinos/RidingFixture.java`]);
  execFileSync(`${javaHome}/bin/jar`, ['--create', '--file', `${directory}/plugins/RidingFixture.jar`, '-C', `${directory}/fixture-classes`, '.', '-C', `${plugin}src/runtime/resources`, '.']);
  await writeFile(`${directory}/eula.txt`, 'eula=true\n');
  await writeFile(`${directory}/server.properties`, `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\n`);
  server = spawn(`${javaHome}/bin/java`, ['-Xms256M', '-Xmx1G', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui'], { cwd: directory, stdio: ['pipe', 'pipe', 'pipe'] });
  server.stdout.on('data', bytes => { log += bytes.toString(); }); server.stderr.on('data', bytes => { log += bytes.toString(); });
  await eventually(() => log.includes('Done (') || (server.exitCode !== null && assert.fail(log.slice(-4000))), 'Isolated Paper did not boot', 90000);
  assert.match(log, /Paper version 26\.3-236-/, 'Riding fixture requires the pinned Paper build');
  await command('whitelist off');
  await command('gamerule minecraft:advance_time false');
  await command('gamerule minecraft:spawn_mobs false');
  await command('difficulty normal');
  await command('setworldspawn 0 4 0');
  const alice = new Client('RideOwner'), bob = new Client('RideOther'); clients.push(alice, bob);
  await eventually(() => alice.joined && bob.joined, 'Native clients did not join', 30000);
  await command('gamemode survival @a'); await command('effect give @a resistance 9999 4 true'); await tp(alice, 0, 0); await tp(bob, 12, 12);
  assert.match(await command('fill -16 3 -16 32 3 32 stone'), /Successfully filled/i, 'Safe platform must exist before summoning mobs');
  for (const [kind, x] of [['sniffer', 0], ['ravager', 10]]) {
    await command(`summon ${kind} ${x} 4 0 {Tags:["ark_dino","ark_dino_wild","ride_${kind}"],PersistenceRequired:1b}`);
    await command('give RideOwner torchflower_seeds 1'); await click(alice, `ride_${kind}`);
    assert.match(await data(`ride_${kind}`, 'BukkitValues."arkdinos:owner"'), new RegExp(alice.uuid));
    await click(alice, `ride_${kind}`, true); // Persist stay so dismount tests can distinguish riding from following.
  }
  await check('Owner native empty-hand interaction mounts both species; normal input moves and sneak dismounts', async () => {
    const result = {};
    for (const kind of ['sniffer', 'ravager']) {
      const tag = `ride_${kind}`; await mount(alice, tag); const start = await position(tag); alice.look(0); alice.input(1); await delay(1000); const end = await position(tag); alice.input(0);
      assert.ok(end[2] - start[2] > 1, `${kind} did not move forward: ${start} -> ${end}`);
      assert.ok(Math.abs(end[1] - start[1]) < 0.5, 'Riding must not fly');
      alice.input(17); await delay(500); alice.input(0); assert.ok(Math.abs((await position(tag))[1] - start[1]) < .5, 'Jump must not make ground species fly');
      assert.match(await passengers(tag), /passengers=1;aware=false;path=false/);
      alice.input(32); await eventually(async () => /passengers=0/.test(await passengers(tag)), 'Sneak did not dismount'); alice.input(0); await delay(100); await stopped(tag);
      assert.match(await data(tag, 'BukkitValues."arkdinos:stay"'), /1b$/);
      result[kind] = { start, end };
    }
    return result;
  });
  await check('Nonowner, held item, Creative and third-party passengers are refused', async () => {
    const tag = 'ride_sniffer'; await click(bob, tag); await stopped(tag);
    await command('give RideOwner stick'); await click(alice, tag); await stopped(tag); await command('clear RideOwner');
    await command('gamemode creative RideOwner'); await click(alice, tag); await stopped(tag); await command('gamemode survival RideOwner');
    await command('summon cow 0 4 0 {Tags:["ride_intruder"],NoAI:1b}'); await command(`ride ${selector('ride_intruder')} mount ${selector(tag)}`); await stopped(tag);
    assert.match(await data(tag, 'BukkitValues."arkdinos:owner"'), new RegExp(alice.uuid));
  });
  await check('Game mode and world changes remove riders and stop control', async () => {
    await mount(alice, 'ride_sniffer'); alice.input(1); await command('gamemode creative RideOwner'); alice.input(0); await stopped('ride_sniffer'); await command('gamemode survival RideOwner');
    await mount(alice, 'ride_ravager'); alice.input(1); await command('dinofixture world ride_ravager RideOwner'); alice.input(0); await stopped('ride_ravager');
    await command('execute in minecraft:overworld run tp RideOwner 0 4 0');
  });
  await check('Owner logout clears the native passenger and stops movement', async () => {
    await mount(alice, 'ride_sniffer'); alice.input(1); alice.close(); await eventually(async () => /passengers=0/.test(await passengers('ride_sniffer')), 'Logout left rider'); await stopped('ride_sniffer');
  });
  const rejoined = new Client('RideOwner'); clients.push(rejoined); await eventually(() => rejoined.joined, 'Owner reconnect failed');
  await check('Owner death clears riding without changing pet ownership', async () => {
    await mount(rejoined, 'ride_sniffer'); rejoined.input(1); await command('kill RideOwner'); rejoined.input(0); await stopped('ride_sniffer'); assert.match(await data('ride_sniffer', 'BukkitValues."arkdinos:owner"'), new RegExp(rejoined.uuid));
    rejoined.send(0x0c, vi(0)); await delay(500);
  });
  await check('Chunk-unload event fixture ejects and stops its controlled pet', async () => {
    await mount(rejoined, 'ride_sniffer'); rejoined.input(1); assert.match(await command('dinofixture unload-event ride_sniffer'), /passengers=0;aware=false;path=false;vx=0\.0;vz=0\.0/); rejoined.input(0);
  });
  await check('Plugin disable ejects its remaining rider and clears native movement', async () => {
    await mount(rejoined, 'ride_ravager'); rejoined.input(1); assert.match(await command('dinofixture disable ride_ravager'), /passengers=0;aware=false;path=false;vx=0\.0;vz=0\.0/); rejoined.input(0);
  });
  await check('No force-loaded chunks and no native plugin errors', async () => {
    assert.match(await command('forceload query'), /No force loaded chunks|No force-loaded chunks/i);
    assert.doesNotMatch(log, /Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable/);
  });
  await writeFile(`${directory}/console.log`, log);
} catch (error) { console.error(error.stack); process.exitCode = 1; }
finally {
  clients.forEach(client => client.close());
  if (server && server.exitCode === null) { server.stdin.write('stop\n'); await Promise.race([once(server, 'exit'), delay(15000, undefined, { ref: false })]); if (server.exitCode === null) { server.kill('SIGTERM'); await once(server, 'exit'); } }
  report.finishedAt = new Date().toISOString(); await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`); console.log(`Report: ${reportPath}`);
  await writeFile(`${directory}/console.log`, log);
}
