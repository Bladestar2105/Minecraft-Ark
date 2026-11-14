#!/usr/bin/env node
// Disposable loopback Paper fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { access, mkdir, mkdtemp, readFile, writeFile, copyFile, symlink, cp, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const gameplay = process.env.DINO_MODEL_GAMEPLAY === '1';
const fallback = process.env.DINO_MODEL_FALLBACK;
if (gameplay && fallback) throw Error('Gameplay verification requires the complete model stack');
if (fallback && !['without-models','without-bridge'].includes(fallback)) throw Error('Unknown fallback test');
const root = fileURLToPath(new URL('../', import.meta.url));
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const cache = process.env.DINO_MODEL_STACK_CACHE ?? `${root}output/live-dependency-stack-20261110/private/server`;
const paperCache = process.env.DINO_MODEL_CACHE ?? cache;
const paperJar = process.env.DINO_MODEL_PAPER ?? `${cache}/paper.jar`;
const output = process.env.DINO_MODEL_OUTPUT ?? `${root}output/dino-adapter-runtime-20261107`;
// Independent acceptance values from the agreed adult proportions, not the implementation under test.
const adultFactors={trex:2.1,triceratops:1.45,stegosaurus:1.55,brachiosaurus:2.15,velociraptor:1,pteranodon:1,dilophosaurus:1.15};
const backs={trex:1.860844,triceratops:1.9375,stegosaurus:1.875,brachiosaurus:2.5,velociraptor:1.489667,pteranodon:1.619054,dilophosaurus:1.191365};
const ravager=name=>['trex','velociraptor','dilophosaurus'].includes(name);
const expectedNativeScale=name=>backs[name]*adultFactors[name]/(ravager(name)?2.2625:2.09375);
// Java's plugin dependency resolver does not read the inherited proxy environment itself.
const javaNetworkArgs=[];
for(const [scheme,key] of [['http','HTTP_PROXY'],['https','HTTPS_PROXY']]) {
  const configured=process.env[key]??process.env[key.toLowerCase()];
  if(configured){const proxy=new URL(configured);javaNetworkArgs.push(`-D${scheme}.proxyHost=${proxy.hostname}`,`-D${scheme}.proxyPort=${proxy.port||(proxy.protocol==='https:'?'443':'80')}`);}
}
if(javaNetworkArgs.length){
  javaNetworkArgs.push('-Dhttp.nonProxyHosts=localhost|127.*|[::1]');
  const trustStore=process.env.DINO_MODEL_JAVA_TRUST_STORE??'/etc/ssl/certs/java/cacerts';
  if(await access(trustStore).then(()=>true,()=>false))javaNetworkArgs.push(`-Djavax.net.ssl.trustStore=${trustStore}`);
}
await mkdir(`${output}/private`, { recursive: true });
const directory = await mkdtemp(`${output}/private/model-`);
const report = { startedAt: new Date().toISOString(), fixture: directory, checks: [], limits: [
  'Direct Java protocol777 test clients, not an official Java client, Mineflayer, Bedrock or PS5.',
  'Model fixture calls the adapter directly; gameplay lifecycle is verified by the separate combined harness.',
] };
const reportPath = `${output}/${gameplay?'gameplay':fallback??'runtime'}-report.json`;
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

async function start() {
  const offset=log.length;
  server = spawn(`${javaHome}/bin/java`, ['-Xms256M', '-Xmx1G', '-XX:ActiveProcessorCount=3', ...javaNetworkArgs, '-Dterminal.jline=false', '-Dterminal.ansi=false', '-jar', 'paper.jar', '--nogui'], { cwd: directory, stdio: ['pipe', 'pipe', 'pipe'] });
  server.stdout.on('data', bytes => { log += bytes.toString(); }); server.stderr.on('data', bytes => { log += bytes.toString(); });
  await eventually(() => log.slice(offset).includes('Done (') || (server.exitCode !== null && assert.fail(log.slice(-4000))), 'Isolated Paper did not boot', 90000);
}

try {
  for (const item of ['libraries', 'versions', 'cache']) await symlink(`${paperCache}/${item}`, `${directory}/${item}`);
  await mkdir(`${directory}/plugins`, { recursive: true });
  await copyFile(paperJar, `${directory}/paper.jar`);
  for (const name of fallback==='without-models'?[]:fallback==='without-bridge'?['bettermodel-3.6.0-paper.jar','packetevents-spigot-2.14.0.jar']:['bettermodel-3.6.0-paper.jar', 'packetevents-spigot-2.14.0.jar', 'GeyserModelEngine-1.0.9.jar', 'geyser-spigot.jar', 'floodgate-spigot.jar', 'geyserutils-spigot-1.0-SNAPSHOT.jar']) await copyFile(`${cache}/plugins/${name}`, `${directory}/plugins/${name}`);
  if(!fallback&&process.env.DINO_GME_JAR)await copyFile(process.env.DINO_GME_JAR,`${directory}/plugins/GeyserModelEngine-1.0.9.jar`);
  if(!fallback)report.bridgeSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/GeyserModelEngine-1.0.9.jar`)).digest('hex');
  if (!fallback) await copyFile(`${plugin}.deps/geyser-spigot-2.12.1-b1298.jar`, `${directory}/plugins/geyser-spigot.jar`);
  if (gameplay) {
    report.protectionSha256={};
    for(const name of ['worldedit-bukkit-7.4.6.jar','worldguard-bukkit-7.0.21.jar','GriefPrevention.jar']) {
      await copyFile(`${cache}/plugins/${name}`,`${directory}/plugins/${name}`);
      report.protectionSha256[name]=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/${name}`)).digest('hex');
    }
    await copyFile(process.env.DINO_PLUGIN_JAR ?? `${plugin}build/ArkDinos-1.2.0.jar`, `${directory}/plugins/ArkDinos.jar`);
    report.pluginSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
    report.limits=['Direct Java protocol777 clients verify real ArkDinos gameplay and actual model/registry/packet state; visible Java/Bedrock/console rendering is a separate acceptance step.', 'Bridge pose checks enroll these Java clients in the actual Bridge viewer sets to receive real encoded GeyserUtils actors; they do not establish real Geyser/Bedrock sessions.'];
    await mkdir(`${directory}/plugins/ArkDinos`, {recursive:true});
    await writeFile(`${directory}/plugins/ArkDinos/config.yml`, 'survival-worlds:\n  - ark\nprototype-worlds:\n  - ark\nwild-spawning:\n  enabled: false\n');
  }
  for (const name of ['BetterModel', 'GeyserModelEngine', 'Geyser-Spigot', 'floodgate']) await cp(`${cache}/plugins/${name}`, `${directory}/plugins/${name}`, { recursive: true });
  if (!fallback) execFileSync('python3', ['-c',
    // PNG previews are untracked; use the installer's embedded-texture extraction on a fresh checkout.
    'import sys; from pathlib import Path; sys.path.insert(0,sys.argv[1]); from install_plugins import dino_item_files,dino_model_files; root=Path(sys.argv[2]); server=Path(sys.argv[3]); models=dino_model_files(root); files=dino_item_files(root)+(models if sys.argv[4]=="1" else [(name,content) for name,content in models if name.endswith(".png") and "/ark_egg_" not in name]); [(p.parent.mkdir(parents=True,exist_ok=True),p.write_bytes(content)) for name,content in files for p in [server/name.removeprefix("data/")]]',
    `${root}scripts`, root, directory, gameplay?'1':'0']);
  const generatedPack = `${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack`;
  await rm(generatedPack, { recursive: true, force: true });
  await rm(`${generatedPack}.zip`, { force: true });
  const geyserConfig = `${directory}/plugins/Geyser-Spigot/config.yml`;
  execFileSync('python3', ['-c', 'import pathlib,sys,yaml; p=pathlib.Path(sys.argv[1]); c=yaml.safe_load(p.read_text()); c["bedrock"]["address"]="127.0.0.1"; c["bedrock"]["port"]=int(sys.argv[2]); p.write_text(yaml.safe_dump(c,sort_keys=False))', geyserConfig, String(await freePort())]);
  await mkdir(`${directory}/plugins/BetterModel/models`, { recursive: true });
  report.modelSources={};
  const rideSpecies = ['trex', 'triceratops', 'stegosaurus', 'brachiosaurus', 'velociraptor', 'pteranodon', 'dilophosaurus'];
  const species = [...rideSpecies, 'mosasaurus'];
  for (const name of species) {
    await copyFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`, `${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`);
    await cp(`${root}dino-models/models/ark_${name}`, `${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}`, { recursive: true });
    report.modelSources[name]={};
    for(const extension of ['bbmodel','geo.json','animation.json']) {
      const path=extension==='bbmodel'?`${directory}/plugins/BetterModel/models/ark_${name}.bbmodel`:`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_${name}/ark_${name}.${extension}`;
      report.modelSources[name][extension]=crypto.createHash('sha256').update(await readFile(path)).digest('hex');
    }
  }
  if(gameplay)for(const name of rideSpecies) {
    report.modelSources[`egg_${name}`]={};
    for(const extension of ['bbmodel','geo.json','animation.json']) {
      const path=extension==='bbmodel'?`${directory}/plugins/BetterModel/models/ark_egg_${name}.bbmodel`:`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/input/ark_egg_${name}/ark_egg_${name}.${extension}`;
      report.modelSources[`egg_${name}`][extension]=crypto.createHash('sha256').update(await readFile(path)).digest('hex');
    }
  }
  if(!gameplay)await copyFile(`${root}dino-models/models/ark_trex/ark_trex.bbmodel`,`${directory}/plugins/BetterModel/models/shared_fixture.bbmodel`);
  if(process.env.DINO_SAVED_MODEL_WORLD)await cp(process.env.DINO_SAVED_MODEL_WORLD,`${directory}/ark`,{recursive:true});
  await mkdir(`${directory}/fixture-classes`);
  execFileSync(`${javaHome}/bin/javac`, ['--release', '25', '-sourcepath', '', '-cp', `${plugin}.deps/*:${directory}/plugins/GeyserModelEngine-1.0.9.jar`, '-d', `${directory}/fixture-classes`, ...(gameplay ? [`${plugin}src/runtime/java/de/ark/dinos/ModelGameplayFixture.java`] : [...['DinoModels', 'BetterModelAdapter', 'BetterModelCleanup', 'DinoPassengerOrder', 'BedrockSeats', 'DinoGrowth', 'DinoSizes', 'EggAssets'].map(name => `${plugin}src/main/java/de/ark/dinos/${name}.java`), ...(!fallback?[`${plugin}src/runtime/java/de/ark/dinos/BridgeRaceProbe.java`]:[]), `${plugin}src/runtime/java/de/ark/dinos/${fallback?'ModelFallbackFixture':'ModelFixture'}.java`])]);
  await mkdir(`${directory}/fixture-resources`);
  await copyFile(`${plugin}src/main/resources/egg-assets.properties`,`${directory}/fixture-resources/egg-assets.properties`);
  let fixtureYaml = 'name: ModelFixture\nversion: 1.0.0\nmain: de.ark.dinos.ModelFixture\napi-version: \'26.3\'\ndepend: [BetterModel, packetevents, GeyserModelEngine]\ncommands:\n  dm: {}\n';
  if(gameplay)fixtureYaml=fixtureYaml.replace('main: de.ark.dinos.ModelFixture','main: de.ark.dinos.ModelGameplayFixture').replace('depend: [BetterModel, packetevents, GeyserModelEngine]','depend: [BetterModel, packetevents, GeyserModelEngine, ArkDinos]');
  if(fallback)fixtureYaml=fixtureYaml.replace('main: de.ark.dinos.ModelFixture','main: de.ark.dinos.ModelFallbackFixture').replace('depend: [BetterModel, packetevents, GeyserModelEngine]',fallback==='without-models'?'':'depend: [BetterModel, packetevents]');
  await writeFile(`${directory}/fixture-resources/plugin.yml`,fixtureYaml);
  execFileSync(`${javaHome}/bin/jar`, ['--create', '--file', `${directory}/plugins/ModelFixture.jar`, '-C', `${directory}/fixture-classes`, '.', '-C', `${directory}/fixture-resources`, '.']);
  report.fixtureSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ModelFixture.jar`)).digest('hex');
  await writeFile(`${directory}/eula.txt`, 'eula=true\n');
  await writeFile(`${directory}/server.properties`, `server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\n`);
  await start();
  await command('whitelist off'); await command('gamerule minecraft:spawn_mobs false'); await command('setworldspawn 0 4 0');
  let alice = new Client('ModelRider'); clients.push(alice); await eventually(() => alice.joined, 'Native client did not join', 30000);
  await command('gamemode creative ModelRider');await command('forceload add -16 -16 80 32');
  await delay(300);assert.match(await command('fill -16 3 -16 64 3 16 stone'),/Successfully filled|No blocks were filled/);
  assert.match(await command('execute if block 0 3 0 stone'),/Test passed/,'Fixture needs its loaded stone floor');
  if(gameplay) {
    await (await import('./model-gameplay-runtime-checks.mjs')).default({check,command,alice,clients,Client,eventually,delay,report,start,server:()=>server,directory,root,readVi,string});
  } else if(fallback) {
    await check(`Optional model fallback ${fallback} restores native carrier`,async()=>{
      const state=await command('dm');assert.match(state,/scale=1.2;invisible=false;passengers=1/);if(process.env.DINO_SAVED_MODEL_WORLD){
        assert.match(state,/loaded=true/);assert.match(state,fallback==='without-models'?/trackers=unavailable;shared=unavailable/:/trackers=\[\];shared=\[shared_fixture\]/);
        const nativeAll=state.match(/;nativeAll=(.*)/)[1].trim().split('|').map(row=>row.split(':'));
        assert.equal(nativeAll.length,8,'Saved fallback must include all eight species');
        for(const[name,scale,invisible,trackers]of nativeAll){
          assert.ok(Math.abs(Number(scale)-(name==='trex'?1.2:name==='mosasaurus'?4:1))<.002,`${name} retained model calibration`);assert.equal(invisible,'false');
          assert.equal(trackers,fallback==='without-models'?'unavailable':name==='stegosaurus'?'[shared_fixture]':'[]');
        }
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
  const nativeIds=Object.fromEntries((await command('dm client-ids ModelRider')).trim().split(';').map(p=>{const[k,v]=p.split('=');return[k,Number(v)];})),pluginMessageId=nativeIds.pluginMessage;
  alice.send(pluginMessageId,Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));await delay(100);
  const parse = text => Object.fromEntries(text.trim().split(';').map(field=>{const at=field.indexOf('=');return [field.slice(0,at),field.slice(at+1)];}));
  const inspect = async name => parse(await command(`dm inspect ${name}`));
  const mountPackets = (client, state) => client.packets.filter(packet=>packet.id===Number(state.packetId)).map(packet=>{
    const body=Buffer.from(packet.hex,'hex'); const id=readVi(body), count=readVi(body,id.end); let index=count.end; const passengers=[];
    for(let i=0;i<count.value;i++){const next=readVi(body,index);passengers.push(next.value);index=next.end;}
    return {vehicle:id.value,passengers};
  }).filter(packet=>packet.vehicle===Number(state.id));
  const spawns=(client,from=0)=>client.packets.slice(from).filter(packet=>packet.id===1).map(packet=>{const bytes=Buffer.from(packet.hex,'hex'),id=readVi(bytes),type=readVi(bytes,id.end+16);return{id:id.value,type:type.value};});
  const verifyBridgeSpawn=async(name,first=false,modelName='')=>{
    const from=alice.packets.length,ids=await command(`dm bridge-viewer ${name} ModelRider ${modelName||(first?'first':'')}`);await delay(100);
    assert.match(ids,/sameTracker=true;bridgeClosed=false/);const id=Number(ids.match(/bridge=(\d+)/)[1]);
    const wire=spawns(alice,from).find(packet=>packet.id===id);assert.ok(wire,'Actual GME actor spawn did not reach viewer');
    assert.equal(wire.type,modelName?nativeIds.pig:nativeIds.ravager,modelName?'Unrelated model factory changed':'Owned GME actor must share native carrier interpolation in both prediction states');
    await command(`dm bridge-off ${name}`);return{ids,wire};
  };
  if(process.env.DINO_MODEL_JOINTS_ONLY==='1') {
    await (await import('./model-joint-runtime-checks.mjs')).default({check,command,inspect,delay,directory,report,alice});
  } else if(process.env.DINO_BRIDGE_RACE==='1') {
    await check('Zero-delay bridge task waits for publication and sends the initial actor exactly once',async()=>{
      await command('dm spawn trex 0');await eventually(async()=>(await inspect('trex')).trackers==='1','Race carrier model not ready');
      const from=alice.packets.length,state=parse(await command('dm bridge-race trex ModelRider'));await delay(1000);
      const wire=spawns(alice,from).filter(packet=>packet.id===Number(state.actor));report.race={state,wire};
      assert.equal(state.earlyViewer,'false','Unpublished task inserted viewer before it could send the actor');assert.equal(state.volatile,'true');assert.equal(wire.length,1,'Initial actor spawn was lost or duplicated');
      const masks=JSON.parse((await command(`dm animation-masks trex ${state.actor}`)).trim());report.race.masks=masks;assert.ok(masks.some(mask=>mask>0),'Initial actor animation properties did not reach the viewer');
      const replaced=await command('dm bridge-race-replace');report.race.replaced=replaced;assert.match(replaced,/replaced=true;oldChecks=0;newChecks=1;viewers=1/);await command('dm bridge-race-finish');
      assert.doesNotMatch(log,/Cannot invoke .*sendEntityData.*because .*task.*null|Exception.*bridge-publication-probe/);return report.race;
    });
  } else {
  await check('Pinned model stack registers all eight models',async()=>{
    const registered=(await command('dm model-registrations')).trim().split(';');
    report.modelRegistrations=registered;
    assert.deepEqual(registered,species.map(name=>`${name}:java=true,bedrock=true`));
    assert.match(log,/Using BetterModel handler!/);return {paper:log.match(/This server is running Paper version ([^ ]+)/)?.[1],betterModel:'3.6.0',bridge:'1.0.9',geyser:'2.12.1-b1298',packetEvents:'2.14.0',registered};
  });
  if(process.env.DINO_MODEL_NAMES_ONLY) {
    await (await import('./name-runtime-checks.mjs')).default({check,command,alice,clients,Client,eventually,delay,inspect,species,nativeIds,readVi,spawns,report});
  } else {
  await check('Generated Java pack and pinned Geyser register eight distinct species egg items',async()=>{
    report.javaPackAudit=JSON.parse(execFileSync('python3',[`${root}dino-models/verify_java_pack.py`,`${directory}/plugins/BetterModel/build.zip`],{encoding:'utf8'}));
    const result=await command('dm egg-mappings');
    assert.match(result,/javaAssets=24;/);assert.match(result,/eggPack=true;/);
    assert.match(result,/bedrockDefinitions=8;/);assert.match(result,/modelEntities=8;/);
    for(const name of species)assert.ok(result.includes(`arkdinos:egg_${name}`),`Missing native ${name} item mapping`);
    assert.ok(!log.match(/Error reading definition.*ark-dino-eggs|Failed to register custom item.*arkdinos/));
    return result;
  });
  await check('Startup attachment waits for BetterModel pack readiness',async()=>{
    const state=await inspect('early');report.earlyStartup=state;
    report.earlyStartupTrace=await command('dm startup early');
    assert.equal(state.valid,'true');assert.equal(state.dead,'false');assert.ok(Number(state.health)>0);
    assert.equal(Number(state.scale),expectedNativeScale('trex'),'Startup carrier must retain native model scale');
    assert.equal(state.trackers,'1');assert.equal(Number(state.render),adultFactors.trex);return state;
  });
  await check('Unmodified BetterModel emitted packet reproduces display-first real rider',async()=>{
    await command('dm spawn baseline -7');await delay(500);await command('dm mount baseline ModelRider');await delay(400);
    const state=await inspect('baseline'), wire=mountPackets(alice,state).find(packet=>packet.passengers.includes(alice.entityId));
    assert.ok(wire);assert.notEqual(wire.passengers[0],alice.entityId);report.baselinePacket=wire;report.riderId=alice.entityId;
    await command('dm dismount baseline');await command('dm baselineclose baseline');return wire;
  });
  report.measurements = {};
  const bodyMids={trex:1.423344,triceratops:1.25,stegosaurus:1.21875,brachiosaurus:1.90625,velociraptor:1.145916,pteranodon:1.306554,dilophosaurus:.9};
  await check('Seven actual native seat and collision measurements; emitted player-first passenger packets',async()=>{
    for (const [i, name] of rideSpecies.entries()) {
      await command(`dm spawn ${name} ${i*7}`); await delay(500);
      await command(`dm mount ${name} ModelRider`); await delay(400);
      const state=await inspect(name);report.measurements[name]=state;
      assert.equal(state.trackers,'1');assert.equal(Number(state.render),adultFactors[name]);assert.ok(Number(state.displays)>=7);assert.equal(Number(state.flags)&32,32);
      assert.ok(Math.abs(Number(state.riderY)+.6-backs[name]*adultFactors[name])<.00001,`${name} back and seated pelvis differ`);
      assert.ok(Math.abs(Number(state.scale)-expectedNativeScale(name))<.000001,`${name} native scale differs`);
      assert.ok(Math.abs(Number(state.width)-(ravager(name)?1.95:1.9)*expectedNativeScale(name))<.00001,`${name} native width differs`);
      assert.ok(Math.abs(Number(state.height)-(ravager(name)?2.2:1.75)*expectedNativeScale(name))<.00001,`${name} native height differs`);
      const wire=mountPackets(alice,state).findLast(packet=>packet.passengers.includes(alice.entityId));assert.ok(wire);assert.equal(wire.passengers[0],alice.entityId);assert.equal(wire.passengers.length,Number(state.displays)+1);state.wire=wire;
      await command(`dm dismount ${name}`);
      assert.equal(await command(`dm ray ${name} ${bodyMids[name]*adultFactors[name]}`),`ray=${state.id}\n`);
      // The protocol interaction targets the real native carrier, never a visual display/proxy.
      await command(`tp ModelRider ${i*7} 4 2`);alice.interact(Number(state.id));await delay(200);
      assert.equal((await inspect(name)).clicked,state.id);
    }
    return report.measurements;
  });
  await check('All three detail stages preserve real walk and combat transforms plus encoded Bedrock animation flags',async()=>{
    const samples={};
    for(const [index,name] of ['triceratops','stegosaurus','pteranodon','dilophosaurus'].entries()) {
      const asset=JSON.parse(await readFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,'utf8'));
      const clips=Object.fromEntries(asset.animations.map(a=>[a.name,a])),animationNames=Object.keys(clips).sort();
      const bedrock=JSON.parse(await readFile(`${generatedPack}/animations/ark_${name}.json`,'utf8')).animations;
      const sourceBedrock=JSON.parse(await readFile(`${root}dino-models/models/ark_${name}/ark_${name}.animation.json`,'utf8')).animations;
      const controllers=JSON.parse(await readFile(`${generatedPack}/animation_controllers/ark_${name}.json`,'utf8')).animation_controllers;
      for(const stage of ['growth_baby','growth_juvenile','growth_adult']) {
        assert.deepEqual(bedrock[`animation.ark_${name}.${stage}`],sourceBedrock[stage],`${name}/${stage} compiled Bedrock pose changed`);
        assert.ok(controllers[`controller.animation.ark_${name}.${stage}`].states.stop.transitions[0].play.includes(`/ ${2**animationNames.indexOf(stage)})`),'Bedrock stage controller bit differs from native GME bit order');
      }
      assert.ok(clips.growth_baby,`${name} requires a baby detail pose`);
      const bones=Object.values(clips.growth_baby.animators).map(a=>a.name),original=await inspect(name),stages=[];
      const bit=clip=>2**animationNames.indexOf(clip);
      const verifyDetails=async stage=>{
        const detail=await command(`dm detail-state ${name} ${bones.join(',')}`);
        for(const row of detail.trim().split('|')) {
          const [bone,actual,clip]=row.split(':');const expected=Number(Object.values(clips[stage].animators).find(a=>a.name===bone).keyframes[0].data_points[0].x);
          assert.ok(['idle','walk','attack','spit'].includes(clip),`${name}/${bone} must share a movement/combat clock: ${row}`);assert.ok(Math.abs(Number(actual)-expected)<.002,`${name}/${bone} runtime detail scale differs: ${row}`);
        }
        return detail;
      };
      const verifyClocks=async stage=>{
        await command(`dm detail-samples ${name}`);
        const rows=JSON.parse(await readFile(`${directory}/plugins/ModelFixture/detail-${name}.json`,'utf8'));
        assert.equal(rows.length,bones.length*31,'Each growth detail needs all31 real native frames');
        let moving=0;
        for(const row of rows){
          const expected=Number(Object.values(clips[stage].animators).find(a=>a.name===row.bone).keyframes[0].data_points[0].x);
          assert.ok(Math.abs(row.scale-expected)<.002,`${name}/${row.bone} lost its age scale at tick${row.tick}`);
          if(!row.reference||!row.stable)continue;
          const reference=row.reference;assert.equal(row.clip,reference.clip,`${name}/${row.bone} has an independent animation clock`);
          assert.equal(row.delay,reference.delay);assert.ok(Math.abs(row.frame-reference.frame)<.001);assert.equal(row.duration,reference.duration);
          const normalize=q=>q.map(v=>v/Math.hypot(...q)),a=normalize(row.rotation),b=normalize(reference.rotation),dot=Math.min(1,Math.abs(a.reduce((sum,v,i)=>sum+v*b[i],0)));
          assert.ok(2*Math.acos(dot)<.0002,`${name}/${row.bone} lags its animated ancestor at tick${row.tick}`);moving++;
        }
        assert.ok(moving>=10,`${name} must prove its animated ancestor during the finite combat pose`);return rows;
      };
      for(const [remaining,stage] of [[900000,'growth_baby'],[450000,'growth_juvenile'],[0,'growth_adult']]) {
        await command(`tp ModelRider ${index*7} 4 -8`);await command(`dm position ${name} ${index*7} 4 -13`);await command(`dm age ${name} ${remaining}`);await delay(250);
        const state=await inspect(name);assert.match(state.running,new RegExp(`name=${stage}`));assert.match(state.running,/name=idle/);
        const detail=await verifyDetails(stage),bridge=await command(`dm bridge-viewer ${name} ModelRider`),bridgeId=Number(bridge.match(/bridge=(\d+)/)[1]);
        await command(`dm motion ${name} .12 -.08 0`);await delay(300);const walking=await inspect(name),token=await command(`dm token ${name} walk`);
        assert.match(walking.running,/name=walk/);assert.match(walking.running,new RegExp(`name=${stage}`));
        await command(`dm attack ${name}`);await command(`dm sample-details ${name}`);await delay(200);const attacking=await inspect(name);
        for(const clip of ['walk','attack',stage])assert.match(attacking.running,new RegExp(`name=${clip}`));
        assert.equal(await command(`dm token ${name} walk`),token,'Detail growth/attack restarted the movement clock');await verifyDetails(stage);
        await delay(850);const ending=await inspect(name);report.detailDiagnostics={name,stage,state,walking,attacking,ending};assert.equal(await command(`dm token ${name} walk`),token);assert.doesNotMatch(ending.running,/name=attack/);
        await delay(600);const clocks=await verifyClocks(stage);
        let frill;
        if(name==='dilophosaurus') {
          const before=await command('dm rotation-state dilophosaurus frill_left');await command('dm attack dilophosaurus spit');await command('dm sample-details dilophosaurus');await delay(350);const opened=await command('dm rotation-state dilophosaurus frill_left');
          assert.ok(Math.abs(Number(opened.split(':')[1].split(',')[1])-Number(before.split(':')[1].split(',')[1]))>.3,'Spit must still rotate the scaled frill open');
          const spit=await inspect(name);for(const clip of ['walk','spit',stage])assert.match(spit.running,new RegExp(`name=${clip}`));await verifyDetails(stage);
          await delay(1250);frill={before,opened,spit,clocks:await verifyClocks(stage)};
        }
        const masks=JSON.parse((await command(`dm animation-masks ${name} ${bridgeId}`)).trim());
        assert.ok(masks.some(mask=>(mask&bit(stage)) && (mask&bit('walk')) && (mask&bit('attack'))),'Growth, walk and attack must overlap in real GME packets');
        if(frill)assert.ok(masks.some(mask=>(mask&bit(stage)) && (mask&bit('spit'))),'Growth and opened frill must overlap in real GME packets');
        const otherStages=['growth_baby','growth_juvenile','growth_adult'].filter(clip=>clip!==stage).reduce((value,clip)=>value|bit(clip),0);
        assert.equal(masks.at(-1)&otherStages,0,'An old growth stage remains active');
        await command(`dm stopmotion ${name}`);await delay(200);await command(`dm bridge-off ${name}`);stages.push({stage,state,detail,walking,attacking,masks,frill,clocks});
      }
      await command(`dm position ${name} ${original.x} ${original.y} ${original.z}`);samples[name]=stages;
    }
    return samples;
  });
  await check('A fresh baby keeps its detail phase and Bedrock stage after its viewer rejoins',async()=>{
    const name='pteranodon',bone='growth_head_crest',asset=JSON.parse(await readFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,'utf8'));
    const animationNames=asset.animations.map(a=>a.name).sort(),stageBit=2**animationNames.indexOf('growth_baby');
    const detail=async()=>{const row=(await command(`dm detail-state ${name} ${bone}`)).trim().split(':');return{bone:row[0],scale:Number(row[1]),phase:row[2]};};
    await command(`dm stopmotion ${name}`);await command(`dm position ${name} 0 4 -5`);await command(`dm prepare-juvenile ${name}`);await command(`dm attach ${name}`);
    await eventually(async()=>{const row=await detail();return Math.abs(row.scale-.2)<.002&&row.phase==='idle';},'Fresh baby was replaced by BetterModel built-in idle');
    const before=await detail();await command(`dm bridge-off ${name}`);alice.close();await delay(250);
    alice=new Client('ModelRider');clients.push(alice);await eventually(()=>alice.joined,'Rejoining model viewer did not join',30000);await command('gamemode creative ModelRider');await command('tp ModelRider 0 4 3');
    alice.send(pluginMessageId,Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));
    const bridge=await command(`dm bridge-viewer ${name} ModelRider`),bridgeId=Number(bridge.match(/bridge=(\d+)/)[1]);
    await eventually(async()=>JSON.parse((await command(`dm animation-masks ${name} ${bridgeId}`)).trim()).some(mask=>(mask&stageBit)!==0),'Rejoined viewer lost the actual encoded baby stage');
    const after=await detail();assert.ok(Math.abs(after.scale-.2)<.002);assert.equal(after.phase,'idle');
    // Allow the one startup repair after BetterModel restarts for the rejoining viewer.
    await delay(400);const token=await command(`dm token ${name} idle`);await delay(400);assert.equal(await command(`dm token ${name} idle`),token,'Stable idle repeatedly restarted after rejoin');
    await command(`dm bridge-off ${name}`);await command(`dm adult ${name}`);return{before,after,bridge};
  });
  await check('Wild aquatic presentation preserves Dolphin collision, swims in three dimensions and returns from its bite',async()=>{
    assert.match(await command('fill 49 4 -9 73 23 25 glass'),/Successfully filled|No blocks were filled/);
    assert.match(await command('fill 50 5 -8 72 22 24 water'),/Successfully filled|No blocks were filled/);
    assert.match(await command('execute if block 42 4 8 air'),/Test passed/,'Aquatic fixture must not flood the ground gait lane');
    await command('dm spawn mosasaurus 58 8');await command('dm position mosasaurus 58 12 8');await delay(500);
    const before=await inspect('mosasaurus');report.mosasaurus=before;
    assert.equal(before.trackers,'1');assert.equal(before.scale,'4.0');assert.equal(before.render,'1.0');assert.equal(before.water,'true');
    assert.ok(Number(before.width)>3.5&&Number(before.width)<3.7);assert.ok(Number(before.height)>2.3&&Number(before.height)<2.5);
    assert.equal(Number(before.pitch),0);assert.equal(Number(before.modelPitch),0);assert.equal(before.riderY,'none');assert.equal(before.seat,'none','Wild swimmer must have no custom rider seat');
    assert.equal(before.original,'4.0','Active presentation marker must preserve the aquatic controller collision baseline');
    await command('tp ModelRider 58 15 12');
    await command('dm swim-motion mosasaurus 0 .12 0');await delay(250);
    const vertical=await inspect('mosasaurus');assert.ok(Number(vertical.y)>Number(before.y)+.1);assert.match(vertical.running,/name=swim/);
    await command('dm stopmotion mosasaurus');await delay(250);assert.match((await inspect('mosasaurus')).running,/name=idle/);
    await command('dm swim-motion mosasaurus .15 0 0');await delay(200);
    const bridge=await command('dm bridge-viewer mosasaurus ModelRider'),bridgeId=Number(bridge.match(/bridge=(\d+)/)[1]);
    const token=await command('dm token mosasaurus swim');await command('dm attack mosasaurus');await delay(200);
    const attacking=await inspect('mosasaurus');assert.match(attacking.running,/name=attack, type=PLAY_ONCE/);assert.match(attacking.running,/name=swim/);
    assert.equal(await command('dm token mosasaurus swim'),token);await delay(850);
    const ended=await inspect('mosasaurus');assert.doesNotMatch(ended.running,/name=attack/);assert.match(ended.running,/name=swim/);
    const masks=JSON.parse((await command(`dm animation-masks mosasaurus ${bridgeId}`)).trim());assert.ok(masks.some(mask=>(mask&1)!==0));assert.equal(masks.at(-1)&1,0);assert.notEqual(masks.at(-1)&4,0);
    await command('dm bridge-off mosasaurus');await command('dm stopmotion mosasaurus');
    await command('dm unload mosasaurus');const unloaded=await inspect('mosasaurus');assert.equal(unloaded.scale,'4.0');assert.equal(unloaded.trackers,'0');assert.equal(unloaded.original,'none');
    await command('dm attach mosasaurus');assert.equal((await inspect('mosasaurus')).scale,'4.0');
    return{before,vertical,attacking,ended,masks,unloaded};
  });
  await check('Repeated attach, adapter reset and original native scale restoration',async()=>{
    const before=await inspect('trex');await command('dm attach trex');await command('dm attach trex');assert.equal((await inspect('trex')).displays,before.displays);
    await command('dm unload trex');const native=await inspect('trex');assert.equal(native.scale,'1.0');assert.equal(native.trackers,'0');assert.equal(Number(native.flags)&32,0);
    await command('dm scale trex 1.2');await command('dm attach trex');await command('dm unload trex');assert.equal((await inspect('trex')).scale,'1.2');
    await command('dm attach trex');await command('dm reset');assert.equal((await inspect('trex')).scale,'1.2');
    const earlyNative=await inspect('early');assert.equal(earlyNative.scale,'1.0');assert.equal(earlyNative.trackers,'0');
    for(const name of species){await command(`dm attach ${name}`);assert.equal((await inspect(name)).trackers,'1');}
    for(const name of species)await eventually(async()=>/active=1;stale=0/.test(await command(`dm bridge-state ${name}`)),`${name} GME tracker identity was not replaced after adapter reset`);
    await command('dm unload trex');await eventually(async()=>/active=0;stale=0/.test(await command('dm bridge-state trex')),'Unloaded GME actor survived its tracker');
    await command('dm attach trex');await eventually(async()=>/active=1;stale=0/.test(await command('dm bridge-state trex')),'Reattached GME actor does not use the current tracker');
    await command('dm attach early');assert.equal(Number((await inspect('early')).scale),expectedNativeScale('trex'));
    return 'No duplicate trackers; unload and reset restore caller scale; all seven reattach';
  });
  await check('Only owned bridge actor spawn factories change, including an initially uncached actor',async()=>{
    const result={};
    for(const name of species){
      await command(`tp ModelRider ${(species.indexOf(name))*7} 4 12`);result[name]=await verifyBridgeSpawn(name,true);
      const state=await inspect(name),native=spawns(alice).find(packet=>packet.id===Number(state.id));
      assert.equal(native.type,['trex','velociraptor','dilophosaurus'].includes(name)?nativeIds.ravager:name==='mosasaurus'?nativeIds.dolphin:nativeIds.sniffer,'Native Java carrier factory must remain unchanged');
    }
    const pig=await command('dm pig ordinary');await delay(100);const pigId=Number(pig.match(/id=(\d+)/)[1]);assert.equal(spawns(alice).find(packet=>packet.id===pigId).type,nativeIds.pig);
    return result;
  });
  await check('Missing model leaves native carrier visible and usable',async()=>{
    await command('dm spawn missing 48');const state=await inspect('missing');assert.equal(state.trackers,'0');assert.equal(state.scale,'1.0');
    await command('dm mount missing ModelRider');assert.notEqual((await inspect('missing')).riderY,'none');await command('dm dismount missing');return state;
  });
  await check('Actual normal and sprint displacement keep continuous bounded walking; stopping returns to idle',async()=>{
    const samples = {};
    assert.match(await command('execute if block 62 4 -13 air'),/Test passed/,'Long gait lane must clear the aquatic glass basin');
    for (const [i,name] of rideSpecies.entries()) {
      await command(`tp ModelRider ${i*7} 4 12`);
      await command(`dm position ${name} ${i*7} 4 -13`);
      const before=await inspect(name);
      await command(`dm motion ${name} .22 -.08 0`);await delay(250);
      const walking=await inspect(name);
      assert.ok(Number(walking.x)>Number(before.x)+.1,`${name} did not actually move`);
      assert.equal(walking.ground,'true');assert.match(walking.running,/name=walk/,`${name} moved while idle`);
      const speed=Number((await command(`dm gait ${name} walk`)).match(/speed=([\d.]+)/)[1]);
      assert.ok(Math.abs(speed-1.1)<.05,`${name} normal gait must remain readable at 4.4 blocks/sec: ${speed}`);
      const poses=async()=>{
        const values=[];report.gaitDiagnostics={name,values};
        for(let tick=0;tick<25;tick++){
          const pose=parse(await command(`dm gaitpose ${name}`));values.push(Object.fromEntries(Object.entries(pose).map(([bone,angle])=>[bone,Number(angle)])));await delay(50);
        }
        const elapsed=values.at(-1).timeSeconds-values[0].timeSeconds;
        for(const bone of Object.keys(values[0]).filter(name=>name!=='timeSeconds')){
          const angles=values.map(p=>p[bone]),range=Math.max(...angles)-Math.min(...angles);
          assert.ok(range>15,`${name}/${bone} actual BetterModel gait pose is static or too small: ${range}`);
          const turns=angles.slice(1).filter((angle,i)=>angle*angles[i]<0).length;
          assert.ok(turns<=Math.ceil(4*elapsed+2),`${name}/${bone} changes direction too often in ${elapsed} seconds: ${turns}`);
          // BetterModel samples at 40 Hz; RCON wall intervals can straddle one extra animation frame.
          assert.ok(angles.slice(1).every((angle,i)=>Math.abs(angle-angles[i])<360*Math.ceil((values[i+1].timeSeconds-values[i].timeSeconds)*40)/40+2),`${name}/${bone} actual pose exceeds the bounded gait's maximum angular speed`);
        }
        return values;
      };
      const normalPoses=await poses();
      assert.match(await command(`dm repeat ${name} walk`),/same=true/);
      const token=await command(`dm token ${name} walk`);
      await command(`dm motion ${name} .34 -.08 0`);await delay(250);
      const sprint=await inspect(name),sprintSpeed=Number((await command(`dm gait ${name} walk`)).match(/speed=([\d.]+)/)[1]);
      assert.ok(Math.abs(sprintSpeed-1.7)<.05,`${name} sprint gait must stay below two cycles/sec: ${sprintSpeed}`);
      assert.equal(await command(`dm token ${name} walk`),token,'Speed changes restarted the movement clock');
      const sprintPoses=await poses();
      await command(`dm stopmotion ${name}`);await delay(250);
      const stopped=await inspect(name);assert.match(stopped.running,/name=idle/);assert.doesNotMatch(stopped.running,/name=walk/);
      samples[name]={before,walking,normalCyclesPerSecond:speed,normalPoses,sprint,sprintCyclesPerSecond:sprintSpeed,sprintPoses,stopped};
      await command(`dm position ${name} ${i*7} 4 8`);
    }
    return samples;
  });
  await check('Blocked velocity and pure falling do not play walk',async()=>{
    await command('tp ModelRider 0 4 12');
    await command('dm position trex 0 4 8');await command('fill 2 4 5 2 10 11 stone');
    await command('dm motion trex .35 -.08 0');await delay(700);
    const blocked=await inspect('trex');await delay(200);const still=await inspect('trex');
    assert.ok(Math.abs(Number(still.x)-Number(blocked.x))<.001,'Wall did not block actual movement');
    assert.match(still.running,/name=idle/);assert.doesNotMatch(still.running,/name=walk/);
    await command('dm position trex 0 18 8');await command('dm motion trex 0 -.25 0');await delay(250);
    const falling=await inspect('trex');assert.equal(falling.ground,'false');assert.ok(Number(falling.y)<18);
    assert.match(falling.running,/name=idle/);assert.doesNotMatch(falling.running,/name=walk/);
    await command('dm stopmotion trex');await command('fill 2 4 5 2 10 11 air');
    return {blocked,still,falling};
  });
  if(process.env.DINO_BRIDGE_TRACE==='1')await check('Capture real native and bridge actor movement while turning',async()=>{
    await command('tp ModelRider 7 4 12');await command('dm position triceratops 7 4 8');
    const observer=new Client('JavaObserver');clients.push(observer);await eventually(()=>observer.joined,'Java observer did not join');
    await command('gamemode creative JavaObserver');await command('tp JavaObserver 7 4 12');await delay(300);
    await command('dm mount triceratops ModelRider');await delay(200);const start=alice.packets.length;
    const observerStart=observer.packets.length;
    const ids=await command('dm bridge-viewer triceratops ModelRider');
    assert.match(ids,/sameTracker=true;bridgeClosed=false/);
    await command('dm rotation triceratops 0');await command('dm motion triceratops 0 -.08 .22');await delay(450);
    await command('dm rotation triceratops 90');await command('dm motion triceratops -.22 -.08 0');await delay(450);
    await command('dm stopmotion triceratops');
    const packets=await command('dm movement-packets triceratops'),packetIds=parse(await command('dm movement-id triceratops')),syncId=Number(packetIds.sync);
    const modelId=Number(ids.match(/bridge=(\d+)/)[1]),nativeId=Number(ids.match(/native=(\d+)/)[1]);
    const poses=alice.packets.slice(start).filter(packet=>packet.id===syncId).map(packet=>{const body=Buffer.from(packet.hex,'hex'),id=readVi(body);return{id:id.value,payload:body.subarray(id.end).toString('hex')};});
    const modelPoses=poses.filter(packet=>packet.id===modelId),nativePoses=poses.filter(packet=>packet.id===nativeId);
    report.bridgeMovement={ids,packets,modelPoses,nativePoses};
    assert.ok(modelPoses.length>5,'Expected real bridge movement packets');
    for(const pose of modelPoses)assert.ok(nativePoses.some(native=>native.payload===pose.payload),'Rider carrier lacks the visible model pose');
    const nativePackets=(client,from,type)=>client.packets.slice(from).filter(packet=>packet.id===type&&readVi(Buffer.from(packet.hex,'hex')).value===nativeId);
    const vanillaRelative=nativePackets(observer,observerStart,Number(packetIds.relative)).length+nativePackets(observer,observerStart,Number(packetIds.relativeRotation)).length;
    assert.ok(vanillaRelative>=2,'Normal Java observer must retain vanilla relative movement');
    const fallbackStart=alice.packets.length;await command('dm bridge-off triceratops');await delay(350);
    await command('dm motion triceratops .22 -.08 0');await delay(450);await command('dm stopmotion triceratops');
    const fallbackPoses=nativePackets(alice,fallbackStart,syncId);
    report.bridgeMovement.fallback={absolutePackets:fallbackPoses.length,vanillaRelative};
    assert.ok(fallbackPoses.length>=1,'Stopping the bridge stream must restore an absolute native carrier pose');
    await command('dm dismount triceratops');return report.bridgeMovement;
  });
  await check('Juvenile growth scales the real native carrier and model together for all seven species',async()=>{
    const samples={};
    for(const name of rideSpecies){
      const adult=await inspect(name);await command(`dm prepare-juvenile ${name}`);const birth=await inspect(name);
      assert.equal(birth.scale,'0.45');assert.equal(birth.trackers,'0');
      await command(`dm attach ${name}`);await delay(100);const juvenile=await inspect(name);
      assert.ok(Math.abs(Number(juvenile.scale)-expectedNativeScale(name)*.45)<.002,`${name} native juvenile scale differs`);
      assert.ok(Math.abs(Number(juvenile.render)-adultFactors[name]*.45)<.002,`${name} model juvenile scale differs`);
      await command(`dm age ${name} 450000`);const halfway=await inspect(name);
      assert.ok(Math.abs(Number(halfway.scale)-expectedNativeScale(name)*.725)<.002);assert.ok(Math.abs(Number(halfway.render)-adultFactors[name]*.725)<.002);
      await command(`dm unload ${name}`);const nativeHalfway=await inspect(name);assert.ok(Math.abs(Number(nativeHalfway.scale)-.725)<.002);assert.equal(nativeHalfway.trackers,'0');
      await command(`dm attach ${name}`);await command(`dm age ${name} -1`);const restored=await inspect(name);assert.equal(Number(restored.scale),expectedNativeScale(name));assert.equal(Number(restored.render),adultFactors[name]);
      await command(`dm unload ${name}`);const nativeAdult=await inspect(name);assert.ok(Math.abs(Number(nativeAdult.scale)-1)<.002,'Growing carrier must restore its original adult baseline');await command(`dm adult ${name}`);await command(`dm attach ${name}`);
      samples[name]={adult,birth,juvenile,halfway,nativeHalfway,restored,nativeAdult};
    }
    return samples;
  });
  await check('Finite combat overlays preserve mounted seat and the continuing movement phase',async()=>{
    const samples={};
    for(const [i,name] of rideSpecies.entries()){
      await command(`dm position ${name} ${i*7} 4 8`);await command(`dm mount ${name} ModelRider`);
      await command(`dm motion ${name} 0 -.08 .1`);await delay(200);
      const bridge=await command(`dm bridge-viewer ${name} ModelRider`),bridgeId=Number(bridge.match(/bridge=(\d+)/)[1]);
      assert.match(bridge,/sameTracker=true;bridgeClosed=false/);
      const before=await inspect(name),token=await command(`dm token ${name} walk`);
      assert.match(before.running,/name=walk/);
      assert.doesNotMatch(await command(`dm attack ${name}`),/ERROR/);await delay(200);const attacking=await inspect(name);
      assert.match(attacking.running,/name=attack, type=PLAY_ONCE/);
      assert.match(attacking.running,/name=walk/,'Attack must leave untouched gait bones moving');
      assert.equal(await command(`dm token ${name} walk`),token,'Attack restarted the movement clock');
      assert.ok(Math.abs(Number(attacking.riderY)-Number(before.riderY))<.00001,'Attack moved the native saddle');
      await delay(850);const ended=await inspect(name);assert.doesNotMatch(ended.running,/name=attack/);assert.match(ended.running,/name=walk/);
      assert.equal(await command(`dm token ${name} walk`),token,'Finishing the attack restarted the movement clock');
      const masks=JSON.parse((await command(`dm animation-masks ${name} ${bridgeId}`)).trim());
      report.combatDiagnostics={name,before,attacking,ended,masks};
      const animationNames=JSON.parse(await readFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,'utf8')).animations.map(a=>a.name).sort();
      const attackBit=2**animationNames.indexOf('attack'),walkBit=2**animationNames.indexOf('walk');
      assert.ok(masks.some(mask=>(mask&attackBit)!==0),'Attack must reach actual encoded GME animation property packets');
      assert.ok((masks.at(-1)&attackBit)===0&&(masks.at(-1)&walkBit)!==0,'Encoded GME property must return to the ongoing walk');
      await command(`dm stopmotion ${name}`);await command(`dm dismount ${name}`);
      await command(`dm bridge-off ${name}`);samples[name]={before,attacking,ended,masks};
    }
    const diloBridge=await command('dm bridge-viewer dilophosaurus ModelRider'),diloId=Number(diloBridge.match(/bridge=(\d+)/)[1]);
    await command('dm attack dilophosaurus spit');await delay(350);const spit=await inspect('dilophosaurus');assert.match(spit.running,/name=spit, type=PLAY_ONCE/);
    await delay(950);const folded=await inspect('dilophosaurus');assert.doesNotMatch(folded.running,/name=spit/);
    const spitNames=JSON.parse(await readFile(`${root}dino-models/models/ark_dilophosaurus/ark_dilophosaurus.bbmodel`,'utf8')).animations.map(a=>a.name).sort();
    const spitBit=2**spitNames.indexOf('spit');
    const spitMasks=JSON.parse((await command(`dm animation-masks dilophosaurus ${diloId}`)).trim());assert.ok(spitMasks.some(mask=>(mask&spitBit)!==0));assert.equal(spitMasks.at(-1)&spitBit,0);
    await command('dm bridge-off dilophosaurus');samples.diloSpit={spit,folded,spitMasks};
    return samples;
  });
  await check('Explicit airborne flight phases and deduplication survive BetterModel reload',async()=>{
    await command('data merge entity @e[tag=model_fixture_pteranodon,limit=1] {NoGravity:1b,Motion:[0.0d,0.0d,0.0d]}');
    await command('dm position pteranodon 35 12 8');await delay(150);
    assert.equal((await inspect('pteranodon')).ground,'false');
    for(const [name,phase] of [['pteranodon','flap'],['pteranodon','glide'],['pteranodon','idle']]) {
      await command(`dm phase ${name} ${phase}`);await delay(200);
      assert.match((await inspect(name)).running,new RegExp(`name=${phase}`));
      assert.match(await command(`dm repeat ${name} ${phase}`),/same=true/);
      if(phase!=='idle'){
        const token=await command(`dm token ${name} ${phase}`);await command(`dm attack ${name}`);await delay(200);
        const attacking=await inspect(name);assert.equal(attacking.ground,'false');assert.match(attacking.running,/name=attack/);assert.match(attacking.running,new RegExp(`name=${phase}`));
        assert.equal(await command(`dm token ${name} ${phase}`),token);
        await delay(850);const resumed=await inspect(name);assert.doesNotMatch(resumed.running,/name=attack/);assert.match(resumed.running,new RegExp(`name=${phase}`));
        assert.equal(await command(`dm token ${name} ${phase}`),token);
      }
    }
    await command('dm reloadmodel');await delay(1500);assert.equal((await inspect('pteranodon')).ground,'false');
    report.reloadedJavaPackAudit=JSON.parse(execFileSync('python3',[`${root}dino-models/verify_java_pack.py`,`${directory}/plugins/BetterModel/build.zip`],{encoding:'utf8'}));
    for(const name of species)await eventually(async()=>/active=1;stale=0/.test(await command(`dm bridge-state ${name}`)),`${name} GME tracker identity was not replaced after BetterModel reload`);
    for(const name of species)await verifyBridgeSpawn(name,true);
    report.earlyReload=await inspect('early');assert.equal(report.earlyReload.valid,'true');assert.equal(report.earlyReload.dead,'false');
    assert.equal(Number(report.earlyReload.scale),expectedNativeScale('trex'));assert.equal(Number(report.earlyReload.render),adultFactors.trex);assert.equal(report.earlyReload.trackers,'1');
    await command('dm phase pteranodon flap');await delay(200);
    const state=await inspect('pteranodon');assert.equal(state.ground,'false');assert.equal(state.trackers,'1');assert.equal(state.render,'1.0');assert.match(state.running,/name=flap/);
    return state;
  });
  await check('A custom adult native baseline remains intact through growing detach and reattachment',async()=>{
    await command('dm adult velociraptor');await command('dm unload velociraptor');
    await command('dm scale velociraptor 1.2');await command('dm attach velociraptor');await command('dm age velociraptor 900000');
    await command('dm age velociraptor 450000');await command('dm unload velociraptor');const fallback=await inspect('velociraptor');
    const nowGrowth=.45+.55*Math.min(1,Math.max(0,1-(Number(fallback.adultAt)-Number(fallback.time))/900000));
    assert.ok(Math.abs(Number(fallback.scale)-1.2*nowGrowth)<.002,'Growing detach must preserve the caller baseline');
    await command('dm attach velociraptor');const restored=await inspect('velociraptor');
    assert.ok(Math.abs(Number(restored.original)-1.2)<.002,'Growing reattachment lost the caller baseline');
    await command('dm adult velociraptor');await command('dm unload velociraptor');await command('dm scale velociraptor 1');await command('dm attach velociraptor');
    return{fallback,restored};
  });
  await check('Saved juvenile detail ages restore on the same entities after a real clean server restart',async()=>{
    const expected={triceratops:[840000,'growth_baby'],stegosaurus:[360000,'growth_juvenile'],pteranodon:[0,'growth_adult'],dilophosaurus:[840000,'growth_baby']},before={};
    for(const [name,[remaining,stage]] of Object.entries(expected)) {
      await command(`dm age ${name} ${remaining}`);
      await eventually(async()=>{before[name]=await inspect(name);return before[name].running.includes(`name=${stage}`);},`${name} detail stage did not settle before saving`);
    }
    clients.forEach(client=>client.close());await command('save-all flush');server.stdin.write('stop\n');await once(server,'exit');await start();
    alice=new Client('ModelRider');clients.push(alice);await eventually(()=>alice.joined,'Restarted model client did not join',30000);
    await command('gamemode creative ModelRider');await command('tp ModelRider 0 4 3');await command('forceload add -16 -16 80 32');await delay(500);assert.doesNotMatch(await command('dm restore-saved'),/ERROR/);
    alice.send(pluginMessageId,Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));
    const after={};
    for(const [name,[remaining,stage]] of Object.entries(expected)) {
      await eventually(async()=>{after[name]=await inspect(name);return after[name].running.includes(`name=${stage}`);},`${name} lost its detail growth stage on restart`);
      assert.equal(after[name].uuid,before[name].uuid);assert.equal(after[name].trackers,'1');assert.equal(after[name].adultAt,before[name].adultAt);
      const growth=.45+.55*Math.min(1,Math.max(0,1-(Number(after[name].adultAt)-Number(after[name].time))/900000));
      assert.ok(Math.abs(Number(after[name].render)-adultFactors[name]*growth)<.002,`${name} render growth must include real elapsed restart time`);
      assert.ok(Math.abs(Number(after[name].scale)-expectedNativeScale(name)*growth)<.002,`${name} native growth must include real elapsed restart time`);
      const asset=JSON.parse(await readFile(`${root}dino-models/models/ark_${name}/ark_${name}.bbmodel`,'utf8')),animationNames=asset.animations.map(a=>a.name).sort();
      const bridge=await command(`dm bridge-viewer ${name} ModelRider`),bridgeId=Number(bridge.match(/bridge=(\d+)/)[1]),stageBit=2**animationNames.indexOf(stage);
      await eventually(async()=>JSON.parse((await command(`dm animation-masks ${name} ${bridgeId}`)).trim()).some(mask=>(mask&stageBit)!==0),`${name} restarted growth stage did not reach Bedrock bridge`);
      await command(`dm bridge-off ${name}`);await command(`dm adult ${name}`);
    }
    report.restartedJavaPackAudit=JSON.parse(execFileSync('python3',[`${root}dino-models/verify_java_pack.py`,`${directory}/plugins/BetterModel/build.zip`],{encoding:'utf8'}));
    for(const name of species)await command(`dm attach ${name}`);return{before,after};
  });
  await command('dm shared stegosaurus');await delay(200);await verifyBridgeSpawn('stegosaurus',false,'shared_fixture');
  await check('Unloading an actor before the first sampler tick clears its bridge mapping',async()=>{
    await command('dm pause-sampler trex');await command('dm unload trex');await command('dm attach trex');
    await eventually(async()=>/active=1;stale=0/.test(await command('dm bridge-state trex')),'New pre-sampler actor must enter the actual GME cache');
    await verifyBridgeSpawn('trex',true);assert.match(await command('dm visual-count trex'),/visuals=1/);
    await command('dm unload trex');const remaining=await command('dm visual-count trex');assert.match(remaining,/visuals=0/,'Unload left an unsampled visual actor mapping');
    await command('dm reset');for(const name of species)await command(`dm attach ${name}`);
    return remaining;
  });
  // Keep the saved-world fallback fixture's deliberate caller scale separate from growth setup.
  await command('dm unload trex');await command('dm scale trex 1.2');await command('dm attach trex');
  await eventually(async()=>/active=1;stale=0/.test(await command('dm bridge-state trex')),'Saved-world carrier must retain the current tracker');
  await command('save-all flush');
  report.savedModelWorld=`${directory}/ark-saved`;await cp(`${directory}/ark`,report.savedModelWorld,{recursive:true});
  report.packetIds = alice.packets.filter(p=>p.hex.length<300&&p.id===Number(report.measurements.trex.packetId));
  }
  }
  }
  report.ok = true;
} catch (error) { report.ok = false; report.error = error.stack; console.error(error.stack); process.exitCode = 1; }
finally {
  clients.forEach(client => client.close());
  if (server && server.exitCode === null) { server.stdin.write('stop\n'); await Promise.race([once(server, 'exit'), delay(15000, undefined, { ref: false })]); if (server.exitCode === null) { server.kill('SIGTERM'); await once(server, 'exit'); } }
  report.finishedAt = new Date().toISOString(); await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`); console.log(`Report: ${reportPath}`);
  await writeFile(`${directory}/console.log`, log);
}
