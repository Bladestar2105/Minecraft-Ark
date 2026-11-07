#!/usr/bin/env node
// Disposable loopback Paper236 fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, writeFile, copyFile, symlink, cp } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const fallback = process.env.DINO_MODEL_FALLBACK;
if (fallback && !['without-models','without-bridge'].includes(fallback)) throw Error('Unknown fallback test');
const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = `${root}output/dino-model-spike-20261107/private/server`;
const paperJar = `${cache}/paper.jar`;
const output = `${root}output/dino-adapter-runtime-20261107`;
await mkdir(`${output}/private`, { recursive: true });
const directory = await mkdtemp(`${output}/private/model-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Model fixture calls the adapter directly; gameplay lifecycle is verified by the separate combined harness.',
] };
const reportPath = `${output}/${fallback??'runtime'}-report.json`;
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
      if (id === 0x32) { this.entityId=body.readInt32BE(0); this.send(0x2c); this.joined = true; }
      (this.packets ??= []).push({ id, hex: body.toString('hex') });
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

try {
  for (const item of ['libraries', 'versions', 'cache']) await symlink(`${cache}/${item}`, `${directory}/${item}`);
  await mkdir(`${directory}/plugins`, { recursive: true });
  await copyFile(paperJar, `${directory}/paper.jar`);
  for (const name of fallback==='without-models'?[]:fallback==='without-bridge'?['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar']:['bettermodel-3.6.0-paper.jar', 'packetevents-spigot-2.14.0.jar', 'GeyserModelEngine-1.0.9.jar', 'geyser-spigot.jar', 'floodgate-spigot.jar', 'geyserutils-spigot-1.0-SNAPSHOT.jar']) await copyFile(`${cache}/plugins/${name}`, `${directory}/plugins/${name}`);
  for (const name of ['BetterModel', 'GeyserModelEngine', 'Geyser-Spigot', 'floodgate']) await cp(`${cache}/plugins/${name}`, `${directory}/plugins/${name}`, { recursive: true });
  const geyserConfig = `${directory}/plugins/Geyser-Spigot/config.yml`;
  await writeFile(geyserConfig, (await readFile(geyserConfig, 'utf8')).replace(/^port: .*/m, `port: ${await freePort()}`));
  await mkdir(`${directory}/plugins/BetterModel/models`, { recursive: true });
  const species = ['trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon'];
  for (const name of species) {
    await copyFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`, `${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`);
    await cp(`${root}dino-models/models/ark_${name}`, `${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}`, { recursive: true });
  }
  await copyFile(`${root}dino-models/models/ark_trex/ark_trex.bbmodel`,`${directory}/plugins/BetterModel/models/shared_fixture.bbmodel`);
  if(process.env.DINO_SAVED_MODEL_WORLD)await cp(process.env.DINO_SAVED_MODEL_WORLD,`${directory}/ark`,{recursive:true});
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`, ['--release', '25', '-sourcepath', '', '-cp', `${plugin}.deps/*`, '-d', `${directory}/fixture-classes`, ...['DinoModels', 'BetterModelAdapter', 'BetterModelCleanup', 'DinoPassengerOrder'].map(name => `${plugin}src/main/java/de/ark/dinos/${name}.java`), `${plugin}src/runtime/java/de/ark/dinos/${fallback?'ModelFallbackFixture':'ModelFixture'}.java`]);
  await mkdir(`${directory}/fixture-resources`);
  let fixtureYaml = 'name: ModelFixture\nversion: 1.0.0\nmain: de.ark.dinos.ModelFixture\napi-version: \'26.3\'\ndepend: [BetterModel, packetevents, GeyserModelEngine]\ncommands:\n  dm: {}\n';
  if(fallback)fixtureYaml=fixtureYaml.replace('main: de.ark.dinos.ModelFixture','main: de.ark.dinos.ModelFallbackFixture').replace('depend: [BetterModel, packetevents, GeyserModelEngine]',fallback==='without-models'?'':'depend: [BetterModel, packetevents]');
  await writeFile(`${directory}/fixture-resources/plugin.yml`,fixtureYaml);
  execFileSync(`${javaHome}/bin/jar`, ['--create', '--file', `${directory}/plugins/ModelFixture.jar`, '-C', `${directory}/fixture-classes`, '.', '-C', `${directory}/fixture-resources`, '.']);
  await writeFile(`${directory}/eula.txt`, 'eula=true\n');
  await writeFile(`${directory}/server.properties`, `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
  server = spawn(`${javaHome}/bin/java`, ['-Xms256M', '-Xmx1G', '-XX:ActiveProcessorCount=3', '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui'], { cwd: directory, stdio: ['pipe', 'pipe', 'pipe'] });
  server.stdout.on('data', bytes => { log += bytes.toString(); }); server.stderr.on('data', bytes => { log += bytes.toString(); });
  await eventually(() => log.includes('Done (') || (server.exitCode !== null && assert.fail(log.slice(-4000))), 'Isolated Paper did not boot', 90000);
  await command('whitelist off'); await command('gamerule minecraft:spawn_mobs false'); await command('setworldspawn 0 4 0');
  const alice = new Client('ModelRider'); clients.push(alice); await eventually(() => alice.joined, 'Native client did not join', 30000);
  await command('gamemode creative ModelRider'); await command('fill -16 3 -16 64 3 16 stone');
  if(fallback) {
    await check(`Optional model fallback ${fallback} restores native carrier`,async()=>{
      const state=await command('dm');assert.match(state,/scale=1.2;invisible=false;passengers=1/);if(process.env.DINO_SAVED_MODEL_WORLD){
        assert.match(state,/loaded=true/);assert.match(state,/trackers=\[\];shared=\[shared_fixture\]/);
        await delay(400);const nativeId=Number(state.match(/;id=(\d+)/)[1]);let flags=0;assert.ok([...alice.entities.values()].includes(nativeId),'Native carrier spawn must reach client');
        for(const packet of alice.packets.filter(packet=>packet.id===0x65)) {
          const bytes=Buffer.from(packet.hex,'hex'),id=readVi(bytes);if(id.value!==nativeId||bytes[id.end]!==0)continue;
          const type=readVi(bytes,id.end+1);if(type.value===0)flags=bytes[type.end];
        }
        assert.equal(flags&32,0);report.nativeVisibilityFlags=flags;
      }
      await command('dm release');await delay(200);await command('save-all flush');
      return state;
    });
  } else {
  report.pipeline = await command('dm pipeline ModelRider');
  const parse = text => Object.fromEntries(text.trim().split(';').map(field=>{const at=field.indexOf('=');return [field.slice(0,at),field.slice(at+1)];}));
  const inspect = async name => parse(await command(`dm inspect ${name}`));
  const mountPackets = (client, state) => client.packets.filter(packet=>packet.id===Number(state.packetId)).map(packet=>{
    const body=Buffer.from(packet.hex,'hex'); const id=readVi(body), count=readVi(body,id.end); let index=count.end; const passengers=[];
    for(let i=0;i<count.value;i++){const next=readVi(body,index);passengers.push(next.value);index=next.end;}
    return {vehicle:id.value,passengers};
  }).filter(packet=>packet.vehicle===Number(state.id));
  await check('Pinned model stack registers all six models',async()=>{
    for(const name of species)assert.match(log,new RegExp(`Defined entity: modelengine:ark_${name}`));
    assert.match(log,/Using BetterModel handler!/);return {paper:'26.3-241',betterModel:'3.6.0',bridge:'1.0.9',geyser:'2.12.1-b1284',packetEvents:'2.14.0'};
  });
  await check('Startup attachment waits for BetterModel pack readiness',async()=>{
    const state=await inspect('early');assert.equal(state.trackers,'1');assert.equal(state.render,'1.0');return state;
  });
  await check('Unmodified BetterModel emitted packet reproduces display-first real rider',async()=>{
    await command('dm spawn baseline -7');await delay(500);await command('dm mount baseline ModelRider');await delay(400);
    const state=await inspect('baseline'), wire=mountPackets(alice,state).find(packet=>packet.passengers.includes(alice.entityId));
    assert.ok(wire);assert.notEqual(wire.passengers[0],alice.entityId);report.baselinePacket=wire;report.riderId=alice.entityId;
    await command('dm dismount baseline');await command('dm baselineclose baseline');return wire;
  });
  report.measurements = {};
  const bodyMids={trex:1.423344,triceratops:1.25,stegosaurus:1.21875,brachiosaurus:1.90625,velociraptor:1.145916,pteranodon:1.306554};
  const backs={trex:1.860844,triceratops:1.9375,stegosaurus:1.875,brachiosaurus:2.5,velociraptor:1.489667,pteranodon:1.619054};
  await check('Six actual native seat and collision measurements; emitted player-first passenger packets',async()=>{
    for (const [i, name] of species.entries()) {
      await command(`dm spawn ${name} ${i*7}`); await delay(500);
      await command(`dm mount ${name} ModelRider`); await delay(400);
      const state=await inspect(name);report.measurements[name]=state;
      assert.equal(state.trackers,'1');assert.equal(state.render,'1.0');assert.ok(Number(state.displays)>=7);assert.equal(Number(state.flags)&32,32);
      assert.ok(Math.abs(Number(state.riderY)+.6-backs[name])<.00001,`${name} back and seated pelvis differ`);
      assert.ok(Number(state.width)>1.2&&Number(state.width)<2.3);assert.ok(Number(state.height)>1.3&&Number(state.height)<2.1);
      const wire=mountPackets(alice,state).findLast(packet=>packet.passengers.includes(alice.entityId));assert.ok(wire);assert.equal(wire.passengers[0],alice.entityId);assert.equal(wire.passengers.length,Number(state.displays)+1);state.wire=wire;
      await command(`dm dismount ${name}`);
      assert.equal(await command(`dm ray ${name} ${bodyMids[name]}`),`ray=${state.id}\n`);
      // The protocol interaction targets the real native carrier, never a visual display/proxy.
      await command(`tp ModelRider ${i*7} 4 2`);alice.interact(Number(state.id));await delay(200);
      assert.equal((await inspect(name)).clicked,state.id);
    }
    return report.measurements;
  });
  await check('Repeated attach, adapter reset and original native scale restoration',async()=>{
    const before=await inspect('trex');await command('dm attach trex');await command('dm attach trex');assert.equal((await inspect('trex')).displays,before.displays);
    await command('dm unload trex');const native=await inspect('trex');assert.equal(native.scale,'1.0');assert.equal(native.trackers,'0');assert.equal(Number(native.flags)&32,0);
    await command('dm scale trex 1.2');await command('dm attach trex');await command('dm unload trex');assert.equal((await inspect('trex')).scale,'1.2');
    await command('dm attach trex');await command('dm reset');assert.equal((await inspect('trex')).scale,'1.2');
    for(const name of species){await command(`dm attach ${name}`);assert.equal((await inspect(name)).trackers,'1');}
    return 'No duplicate trackers; unload and reset restore caller scale; all six reattach';
  });
  await check('Missing model leaves native carrier visible and usable',async()=>{
    await command('dm spawn missing 48');const state=await inspect('missing');assert.equal(state.trackers,'0');assert.equal(state.scale,'1.0');
    await command('dm mount missing ModelRider');assert.notEqual((await inspect('missing')).riderY,'none');await command('dm dismount missing');return state;
  });
  await check('Explicit flight phases and deduplication survive BetterModel reload',async()=>{
    for(const [name,phase] of [['pteranodon','flap'],['pteranodon','glide'],['pteranodon','idle'],['trex','walk']]) {
      await command(`dm phase ${name} ${phase}`);await delay(200);
      assert.match((await inspect(name)).running,new RegExp(`name=${phase}`));
      assert.match(await command(`dm repeat ${name} ${phase}`),/same=true/);
    }
    await command('dm reloadmodel');await delay(1500);await command('dm phase pteranodon flap');await delay(200);
    const state=await inspect('pteranodon');assert.equal(state.trackers,'1');assert.equal(state.render,'1.0');assert.match(state.running,/name=flap/);
    return state;
  });
  await command('dm shared stegosaurus');await command('save-all flush');
  report.savedModelWorld=`${directory}/ark-saved`;await cp(`${directory}/ark`,report.savedModelWorld,{recursive:true});
  report.packetIds = alice.packets.filter(p=>p.hex.length<300&&p.id===Number(report.measurements.trex.packetId));
  }
  report.ok = true;
} catch (error) { report.ok = false; report.error = error.stack; console.error(error.stack); process.exitCode = 1; }
finally {
  clients.forEach(client => client.close());
  if (server && server.exitCode === null) { server.stdin.write('stop\n'); await Promise.race([once(server, 'exit'), delay(15000, undefined, { ref: false })]); if (server.exitCode === null) { server.kill('SIGTERM'); await once(server, 'exit'); } }
  report.finishedAt = new Date().toISOString(); await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`); console.log(`Report: ${reportPath}`);
  await writeFile(`${directory}/console.log`, log);
}
