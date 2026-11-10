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
const cache = process.env.DINO_PAPER_CACHE ?? `${root}artifacts/renewal-20261103/server`;
const paperJar = process.env.DINO_PAPER_JAR ?? `${root}artifacts/renewal-20261103/paper-26.3-236.jar`;
const output=process.env.DINO_CARE_OUTPUT ?? `${plugin}build`;
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/care-smoke-`);
const report = {startedAt:new Date().toISOString(),fixture:directory,checks:[],limits:[
  'Direct protocol777 Java clients exercise real entity interactions, inventory ItemStacks and persistence; no official/Bedrock/PS5 client.',
  'Inventory clicks, protected placement, breaking and growth/fade use dispatched native Paper events against actual inventories/blocks.',
  'The dedicated wall-clock Rex check never edits feeding deadlines; other cooldown/hatch checks advance fixture PDC. Real restart verifies persistence.',
]};
const reportPath=`${output}/care-runtime-report.json`;
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
async function check(name, action) { if(process.env.DINO_CARE_FOCUS&&!process.env.DINO_CARE_FOCUS.split('|').some(part=>name.includes(part)))return;try { const detail = await action(); report.checks.push({ name, status: 'passed', detail }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: 'failed', error: error.stack }); throw error; } finally { await writeFile(reportPath, JSON.stringify(report, null, 2)); } }
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
 const species=(await inspect(tag)).species;
 const count=species==='trex'?12:['triceratops','stegosaurus','brachiosaurus'].includes(species)?8:5;
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
 await copyFile(process.env.DINO_PLUGIN_JAR??`${plugin}build/ArkDinos-1.2.0.jar`,`${directory}/plugins/ArkDinos.jar`);
 report.pluginSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/ArkDinos.jar`)).digest('hex');
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
 await check('Spawn-tick Rex identification prevents legacy one-seed taming before deferred restore',async()=>{
  const tag='care_spawn_tick';
  try {
   const initial=fields(await command(`carefixture spawn-taming-race ${alice.name} ${tag}`));
   assert.equal(initial.speciesBefore,'null','The native spawn must begin without species PDC');
   assert.equal(initial.interactionTick,initial.spawnTick,'The seed interaction must run in the native spawn tick');
   assert.equal(initial.species,'trex','Interaction must identify the tagged Ravager before choosing its recipe');
   assert.equal(initial.owner,'null','A same-tick seed interaction must not enter legacy instant taming');
   assert.equal(initial.feeds,'0');assert.equal(initial.actor,'null');assert.equal(initial.cancelled,'true');
   assert.equal(initial.hand,'TORCHFLOWER_SEEDS');assert.equal(initial.amount,'1','Rejected seed must stay in the actual inventory');
   assert.equal(initial.wild,'true');assert.equal(initial.tamed,'false');
   await food(alice,'BEEF',1);await click(alice,tag);
   const firstFeed=await inspect(tag);
   assert.equal(firstFeed.species,'trex');assert.equal(firstFeed.owner,'null');assert.equal(firstFeed.feeds,'1');assert.equal(firstFeed.actor,alice.uuid);
   assert.equal((await inventory(alice)).amount,'0','The first accepted meat item must be consumed');
   assert.ok(Number(firstFeed.nextFeedAt)>Date.now()+17_000 && Number(firstFeed.nextFeedAt)<=Date.now()+20_000,'The first of twelve Rex feeds must use its twenty-second cooldown');
   return {initial,firstFeed};
  } finally { await command(`kill @e[tag=${tag},limit=1]`); }
 });
 await check('Hatch clearance covers the enlarged baby neck and tail before spawning',async()=>{
  await command('setblock 20 8 20 stone');
  assert.match(await command('carefixture hatch-space ark_model_test 20 4 20 brachiosaurus'),/clear=false/,'A low ceiling intersects the enlarged Brachiosaurus hatchling neck');
  await command('setblock 20 8 20 air');assert.match(await command('carefixture hatch-space ark_model_test 20 4 20 brachiosaurus'),/clear=true/);
  await command('setblock 20 4 23 stone');
  assert.match(await command('carefixture hatch-space ark_model_test 20 4 20 trex'),/clear=false/,'A side block intersects the Rex hatchling tail despite a clear carrier box');
  await command('setblock 20 4 23 air');assert.match(await command('carefixture hatch-space ark_model_test 20 4 20 trex'),/clear=true/);
 });
 await check('Rex requires twelve real meat items with twenty-second spacing and protected taming owner',async()=>{
  await spawnDino('care_rex','trex');await food(alice,'TORCHFLOWER_SEEDS',1);await click(alice,'care_rex');assert.equal((await inspect('care_rex')).owner,'null');assert.equal((await inventory(alice)).amount,'1');
  await food(alice,'BEEF',14);await click(alice,'care_rex');assert.equal((await inspect('care_rex')).feeds,'1');assert.equal((await inventory(alice)).amount,'13');
  const nextFeedAt=Number((await inspect('care_rex')).nextFeedAt);
  assert.ok(nextFeedAt>Date.now()+17_000 && nextFeedAt<=Date.now()+20_000,'The accepted Rex feed must persist a twenty-second cooldown');
  await click(alice,'care_rex');assert.equal((await inventory(alice)).amount,'13','Cooldown must retain food');
  await food(bob,'BEEF',2);await click(bob,'care_rex');assert.equal((await inspect('care_rex')).actor,alice.uuid);assert.equal((await inventory(bob)).amount,'2');
  for(let n=2;n<=5;n++){await command('carefixture time care_rex tame-next 0');await click(alice,'care_rex');assert.equal((await inspect('care_rex')).feeds,String(n));}
  await food(alice,'BONE',1);await click(alice,'care_rex');assert.equal((await inspect('care_rex')).owner,'null','The former five-feed recipe must no longer tame a Rex');assert.equal((await inventory(alice)).amount,'1');
  await food(alice,'BEEF',7);
  for(let n=6;n<=12;n++){await command('carefixture time care_rex tame-next 0');await click(alice,'care_rex');assert.equal((await inspect('care_rex')).feeds,String(n));}
  await food(bob,'BONE',1);await click(bob,'care_rex');assert.equal((await inspect('care_rex')).owner,'null');
  await food(alice,'BONE',1);await click(alice,'care_rex');assert.equal((await inspect('care_rex')).owner,alice.uuid);assert.equal((await inventory(alice)).amount,'0');
  await command('data modify entity @e[tag=care_rex,limit=1] BukkitValues."arkdinos:stay" set value 1b');
 });
 await check('Wall-clock Rex taming waits twenty seconds per feed and consumes twelve meat items then one bone',async()=>{
  const tag='care_rex_wall_clock',accepted=[];
  await spawnDino(tag,'trex',-10);
  // Keep the timed food check on its platform; protocol clients do not simulate falling after following a roaming animal.
  await command(`data merge entity @e[tag=${tag},limit=1] {NoAI:1b}`);
  assert.match(await command(`data get entity @e[tag=${tag},limit=1] NoAI`),/1b/);
  try {
   for(let n=1;n<=12;n++) {
    if(n>1)await delay(Math.max(0,Number((await inspect(tag)).nextFeedAt)-Date.now()+100));
    assert.ok(!alice.closed,'The real feeding client must remain connected');
    await food(alice,'BEEF',13-n);await click(alice,tag);
    const state=await inspect(tag),observedAt=Date.now();
    assert.equal(state.feeds,String(n));assert.equal(state.owner,'null');
    assert.equal((await inventory(alice)).amount,String(12-n),'Each accepted feed consumes exactly one real item');
    const acceptedAt=Number(state.nextFeedAt)-20_000;
    assert.ok(acceptedAt<=observedAt&&observedAt-acceptedAt<3000,'Each deadline must be twenty seconds after the real interaction');
    if(n>1)assert.ok(acceptedAt-accepted.at(-1).acceptedAt>=20_000,'No feeding interval may be shortened');
    accepted.push({feed:n,acceptedAt,observedAt,nextFeedAt:Number(state.nextFeedAt)});
    await food(alice,'BONE',1);await click(alice,tag);
    if(n<12) {
     assert.equal((await inspect(tag)).owner,'null');assert.equal((await inventory(alice)).amount,'1','A premature finisher is retained');
     await food(alice,'BEEF',1);await click(alice,tag);
     assert.equal((await inspect(tag)).feeds,String(n));assert.equal((await inventory(alice)).amount,'1','Early meat is retained');
     await food(alice,'WHEAT',1);await click(alice,tag);
     assert.equal((await inspect(tag)).feeds,String(n));assert.equal((await inventory(alice)).amount,'1','Wrong food is retained');
    } else {
     assert.equal((await inspect(tag)).owner,alice.uuid);assert.equal((await inventory(alice)).amount,'0');
    }
   }
   assert.ok(accepted.at(-1).acceptedAt-accepted[0].acceptedAt>=220_000,'Twelve feeds require at least 220 seconds from first to last');
   return {accepted,elapsedMs:accepted.at(-1).acceptedAt-accepted[0].acceptedAt,deadlineEdits:0};
  } finally {await command(`kill @e[tag=${tag},limit=1]`);}
 });
 await check('Pteranodon fish/seagrass, eight plant feeds/seeds and expired progress',async()=>{
  await spawnDino('care_ptera','pteranodon',5);await tame(alice,'care_ptera','COD','SEAGRASS');
  await spawnDino('care_herb','triceratops',10);await food(alice,'WHEAT',1);await click(alice,'care_herb');await command('carefixture time care_herb tame-expiry 0');
  await food(bob,'FERN',2);await click(bob,'care_herb');assert.equal((await inspect('care_herb')).actor,bob.uuid);assert.equal((await inspect('care_herb')).feeds,'1');
  await click(bob,'care_herb');assert.equal((await inventory(bob)).amount,'1','Early plant feeding retains food');
  await command('carefixture time care_herb tame-expiry 0');await tame(alice,'care_herb','WHEAT','TORCHFLOWER_SEEDS');
 });
 await check('Same-owner adult pair produces one real inventory egg and cooldown blocks repeats',async()=>{
  await spawnDino('care_mate','trex',3,alice.uuid);await command(`tp @e[tag=care_rex,limit=1] 0 4 0`);await command(`tp @e[tag=care_mate,limit=1] 3 4 0`);
  await command(`clear ${alice.name}`);await food(alice,'BEEF',3);await click(alice,'care_rex');await click(alice,'care_mate');assert.equal((await inventory(alice)).eggs,'1');
  const amount=(await inventory(alice)).amount;await click(alice,'care_rex');assert.equal((await inventory(alice)).amount,amount);assert.equal((await inventory(alice)).eggs,'1');
 });
 await check('Full inventory breeding drops exactly one real tagged overflow egg',async()=>{
  for(const tag of ['care_rex','care_mate'])await command(`carefixture time ${tag} breed-until 0`);
  await command(`carefixture fill-inventory ${alice.name}`);await food(alice,'BEEF',64);
  const before=Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]);
  await click(alice,'care_rex');await click(alice,'care_mate');assert.equal((await inventory(alice)).eggs,'0');
  const after=Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]);assert.equal(after,before+1);
 });
 await check('Creative menu blocks transfers and grants a real inventory egg on selection',async()=>{
  await command(`clear ${alice.name}`);await command(`gamemode creative ${alice.name}`);await command(`carefixture menu ${alice.name}`);
  for(const clickType of ['SHIFT_LEFT','NUMBER_KEY','DOUBLE_CLICK','DROP'])assert.match(await command(`carefixture click ${alice.name} 0 ${clickType}`),/cancelled=true/);
  assert.match(await command(`carefixture drag ${alice.name}`),/cancelled=true/);assert.equal((await inventory(alice)).eggs,'0');
  assert.match(await command(`carefixture click ${alice.name} 5 LEFT`),/cancelled=true/);await delay(150);assert.equal((await inventory(alice)).eggs,'1');assert.equal((await inventory(alice)).eggSpecies,'pteranodon');assert.equal((await inventory(alice)).cursor,'AIR');
  assert.equal((await inventory(alice)).eggModel,'arkdinos:egg_pteranodon');assert.match((await inventory(alice)).lore,/20 Minuten Brutzeit.*15 Minuten Wachstum/);assert.doesNotMatch((await inventory(alice)).lore,/Testwelt/);
  await command(`clear ${alice.name}`);await command(`gamemode survival ${alice.name}`);await command(`carefixture menu ${alice.name}`);assert.notEqual((await inventory(alice)).top,'9');
 });
 await check('Protection cancellation retains the inventory egg and creates no nest',async()=>{
  await command(`carefixture egg ${alice.name} trex`);await command('carefixture deny-place true');assert.match(await command(`carefixture place ${alice.name} 12 4 12`),/cancelled=true;block=AIR/);assert.equal((await inventory(alice)).eggs,'1');
  assert.match(await command('carefixture nest ark_model_test 12 4 12'),/nest=false/);await command('carefixture deny-place false');
  assert.match(await command(`carefixture place ${alice.name} 12 4 12 false`),/block=AIR;nest=false/,'Native canBuild=false must not create a nest');
 });
 await check('Placed eggs suppress native cracking/hatching; cancelled species spawn preserves egg',async()=>{
  assert.match(await command(`carefixture place ${alice.name} 12 4 12`),/cancelled=false;block=SNIFFER_EGG/);assert.equal((await inventory(alice)).eggs,'0');
  assert.match(await command('carefixture grow ark_model_test 12 4 12'),/cancelled=true/);assert.match(await command('carefixture fade ark_model_test 12 4 12'),/cancelled=true/);
  await command('carefixture deny-spawn true');assert.match(await command('carefixture ready ark_model_test 12 4 12'),/block=SNIFFER_EGG;nest=true/);
  const identity=await command('carefixture spawn-identity');assert.match(identity,new RegExp(`owner=${alice.uuid};species=trex`));assert.match(identity,/adultAt=[1-9]/);await command('carefixture deny-spawn false');
 });
 await check('Obstructed nests wait safely and then hatch a persistent owned juvenile',async()=>{
  await command('setblock 12 5 12 stone');assert.match(await command('carefixture ready ark_model_test 12 4 12'),/block=SNIFFER_EGG;nest=true/,'Blocked headroom must retain egg');
  await command('setblock 12 5 12 air');assert.match(await command('carefixture ready ark_model_test 12 4 12'),/block=AIR;nest=false/);
  const baby=await inspect('care_hatch');assert.equal(baby.owner,alice.uuid);assert.equal(baby.species,'trex');assert.equal(baby.growthDuration,'900000');assert.ok(Number(baby.adultAt)>Date.now()+890_000 && Number(baby.adultAt)<=Date.now()+900_000);
  await command(`clear ${alice.name}`);await click(alice,'care_hatch');assert.equal((await inspect('care_hatch')).passengers,'0','Juvenile cannot mount');
  await food(alice,'BEEF',1);await click(alice,'care_hatch');assert.equal((await inventory(alice)).amount,'0','Juvenile food accelerates growth');
  const fed=await inspect('care_hatch');assert.equal(fed.love,'0');assert.ok(Number(baby.adultAt)-Number(fed.adultAt)>=59999);
  await food(alice,'BEEF',1);await click(alice,'care_hatch');assert.equal((await inventory(alice)).amount,'1','Growth feeding cooldown retains food');
  await command('carefixture time care_hatch grow-next 0');await click(alice,'care_hatch');assert.equal((await inventory(alice)).amount,'0');
  const fedAgain=await inspect('care_hatch');assert.ok(Number(fed.adultAt)-Number(fedAgain.adultAt)>=59999);report.juvenileBeforeRestart=fedAgain;
 });
 await check('Real clean restart retains incubation, species and owner without force loading',async()=>{
  await command(`carefixture egg ${alice.name} brachiosaurus`);await command(`carefixture place ${alice.name} 14 4 12`);
  const before=await command('carefixture nest ark_model_test 14 4 12');assert.match(before,/nest=true;species=brachiosaurus/);const placed=fields(before);assert.equal(placed.duration,'1200000');assert.ok(Number(placed.hatchAt)>Date.now()+1190000 && Number(placed.hatchAt)<=Date.now()+1200000);await command(`carefixture legacy-eggs ${alice.name}`);await stop();await start();
  alice=new Client('CareOwner');clients.push(alice);await eventually(()=>alice.joined,'Restarted care client did not join',30000);await command(`tp ${alice.name} 12 4 12`);await delay(500);
  assert.equal(await command('carefixture nest ark_model_test 14 4 12'),before);assert.match(await command('forceload query'),/No force loaded chunks|No force-loaded chunks/i);
  const baby=await inspect('care_hatch');assert.equal(baby.uuid,report.juvenileBeforeRestart.uuid);assert.equal(baby.owner,alice.uuid);assert.equal(baby.adultAt,report.juvenileBeforeRestart.adultAt);
 });
 await check('Joining and opening native inventories refresh only tagged egg models and default lore, preserving custom metadata',async()=>{
  const checkEgg=state=>{assert.equal(state.amount,'16');assert.equal(state.model,'arkdinos:egg_trex');assert.equal(state.owner,'8b26dbfd-7e37-4304-8362-ccc4816aee1f');assert.equal(state.species,'trex');assert.equal(state.name,'true');assert.equal(state.extra,'keep');assert.match(state.lore,/20 Minuten Brutzeit.*15 Minuten Wachstum/);assert.doesNotMatch(state.lore,/Testwelt/);assert.match(state.mosaLore,/Nur Kreativmodus/);assert.doesNotMatch(state.mosaLore,/Testwelt/);};
  const joined=fields(await command(`carefixture legacy-egg-state ${alice.name}`));checkEgg(joined);assert.equal(joined.customLore,'true');assert.equal(joined.vanilla,'true');
  const unopened=fields(await command(`carefixture legacy-egg-state ${alice.name} chest`));assert.equal(unopened.model,'arkdinos:egg_trex');assert.match(unopened.lore,/Testwelt: 5 Minuten/,'Join refresh must not rewrite unopened chests');
  await command(`carefixture open-legacy-chest ${alice.name}`);checkEgg(fields(await command(`carefixture legacy-egg-state ${alice.name} chest`)));await command(`carefixture close ${alice.name}`);
 });
 await check('Persisted growth updates native body size through adulthood without a model plugin',async()=>{
  await command('carefixture time care_hatch adult-at 450000');await delay(1200);
  assert.ok(Math.abs(Number((await inspect('care_hatch')).scale)-.725)<.003);
  await command('carefixture time care_hatch adult-at 0');await delay(1200);
  assert.equal((await inspect('care_hatch')).scale,'1.0');
 });
 await check('Breaking returns exactly one tagged egg and suppresses vanilla item drops',async()=>{
  await command(`tp ${alice.name} 0 4 0`);const before=Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]);
  assert.match(await command(`carefixture break ark_model_test 14 4 12 ${alice.name}`),/cancelled=false;vanillaDrops=false/);assert.match(await command('carefixture nest ark_model_test 14 4 12'),/block=AIR;nest=false/);
  const after=Number((await command('carefixture drops ark_model_test')).match(/=(\d+)/)[1]);assert.equal(after,before+1);
 });
 await check('Pistons cannot move nests; cancelled explosions retain them and allowed explosions clean them without egg duplication',async()=>{
  await command(`carefixture egg ${alice.name} pteranodon`);await command(`carefixture place ${alice.name} 14 4 14`);
  assert.match(await command('carefixture piston ark_model_test 14 4 14'),/cancelled=true/);
  assert.match(await command('carefixture explode ark_model_test 14 4 14 true'),/block=SNIFFER_EGG;nest=true/);
  const before=await command('carefixture drops ark_model_test');assert.match(await command('carefixture explode ark_model_test 14 4 14'),/block=AIR;nest=false/);assert.equal(await command('carefixture drops ark_model_test'),before);
 });
 await check('Dilophosaurus tames with meat and bone, breeds an inventory egg and hatches the correct native species',async()=>{
  await spawnDino('care_dilo','dilophosaurus',-5);await tame(alice,'care_dilo','BEEF','BONE');
  assert.equal((await inspect('care_dilo')).type,'RAVAGER');
  await spawnDino('care_dilo_mate','dilophosaurus',-2,alice.uuid);
  await command(`clear ${alice.name}`);await food(alice,'BEEF',2);await click(alice,'care_dilo');await click(alice,'care_dilo_mate');
  assert.equal((await inventory(alice)).eggSpecies,'dilophosaurus');
  await command('tag @e[tag=care_hatch] remove care_hatch');
  await command(`carefixture place ${alice.name} 14 4 16`);assert.match(await command('carefixture ready ark_model_test 14 4 16'),/block=AIR;nest=false/);
  const baby=await inspect('care_hatch');assert.equal(baby.type,'RAVAGER');assert.equal(baby.species,'dilophosaurus');assert.equal(baby.owner,alice.uuid);assert.ok(Number(baby.scale)<.46);
  await command(`clear ${alice.name}`);await command(`gamemode creative ${alice.name}`);await command(`carefixture menu ${alice.name}`);
  await command(`carefixture click ${alice.name} 6 LEFT`);await delay(150);assert.equal((await inventory(alice)).eggSpecies,'dilophosaurus');
 });
 await check('Old five-minute care durations migrate to normal timing once, retaining elapsed age and native size',async()=>{
  await command(`carefixture egg ${alice.name} triceratops`);await command(`carefixture place ${alice.name} 10 4 14`);
  const migrated=fields(await command('carefixture legacy-nest ark_model_test 10 4 14 180000 300000'));
  assert.equal(migrated.duration,'1200000');assert.equal(migrated.owner,alice.uuid);assert.ok(Math.abs(Number(migrated.hatchAt)-Date.now()-1080000)<3000);
  assert.equal((fields(await command('carefixture nest ark_model_test 10 4 14'))).hatchAt,migrated.hatchAt,'Migration must not extend twice');
  await command('setblock 10 5 14 stone');await command('carefixture legacy-nest ark_model_test 10 4 14 180000 300000');
  assert.match(await command('carefixture nest ark_model_test 10 4 14'),/nest=true/,'Old obstructed eggs survive migration');
  await command('setblock 10 5 14 air');await command('carefixture ready ark_model_test 10 4 14');
  const before=await inspect('care_hatch');await command('carefixture legacy-growth care_hatch 180000 300000');await command('carefixture restore care_hatch');
  const young=await inspect('care_hatch');assert.equal(young.uuid,before.uuid);assert.equal(young.owner,before.owner);assert.equal(young.growthDuration,'900000');assert.ok(Math.abs(Number(young.adultAt)-Date.now()-780000)<3000);
  await command('carefixture restore care_hatch');assert.equal((await inspect('care_hatch')).adultAt,young.adultAt,'Restore must not extend twice');
  for(const base of [1,1.2]) {
   const scaled=fields(await command(`carefixture legacy-scale care_hatch 180000 ${base} 300000`));assert.ok(Math.abs(Number(scaled.scale)-base*(.45+.55*2/15))<.001,'Legacy native size must preserve its adult base when duration changes');
   const mature=fields(await command(`carefixture legacy-scale care_hatch 0 ${base} 300000`));assert.ok(Math.abs(Number(mature.scale)-base)<.001,'Migrated adult retains its native base scale');
  }
  await command('carefixture legacy-growth care_hatch 480000');await command('carefixture restore care_hatch');await delay(1200);
  assert.ok(Math.abs(Number((await inspect('care_hatch')).scale)-(.45+.55*7/15))<.003,'An existing seven-minute juvenile retains normal growth age');
  await command('carefixture create-world ark');await command(`gamemode creative ${alice.name}`);await command(`execute in minecraft:ark run tp ${alice.name} 0 5 0`);await delay(500);await command('execute in minecraft:ark run summon ravager 0 4 0 {Tags:["care_main","ark_dino"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"trex"}}');
  await command('carefixture legacy-growth care_main 780000');await command('carefixture restore care_main');const main=await inspect('care_main');assert.ok(Math.abs(Number(main.adultAt)-Date.now()-780000)<3000);assert.ok(main.growthDuration==='0' || main.growthDuration==='900000');
  await command(`execute in minecraft:overworld run tp ${alice.name} 0 5 0`);
 });
 await check('Eighth creative egg creates only a wild aquatic Mosa and respects gamemode, world, space and protection',async()=>{
  await command(`clear ${alice.name}`);await command(`gamemode creative ${alice.name}`);await command(`carefixture menu ${alice.name}`);await command(`carefixture click ${alice.name} 7 LEFT`);await delay(150);
  const item=await inventory(alice);assert.equal(item.eggSpecies,'mosasaurus');assert.equal(item.eggModel,'arkdinos:egg_mosasaurus');assert.match(item.lore,/wilden Mosasaurus/);
  await command(`tp ${alice.name} 0 5 0 0 0`);assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/);assert.equal((await inventory(alice)).eggs,'1');
  await command('fill -18 5 -18 18 15 18 water');await command(`tp ${alice.name} 0 9 0 0 0`);await delay(150);
  await command(`gamemode survival ${alice.name}`);assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/);await command(`gamemode creative ${alice.name}`);
  await command('setblock 1 10 5 stone');assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/,'A submerged obstruction must reject manual spawn');await command('setblock 1 10 5 water');
  assert.match(await command(`carefixture wild-egg ${alice.name} true`),/mosa=0/,'Cancelled interaction must reject manual spawn');
  await command('carefixture deny-place true');assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/);await command('carefixture deny-place false');
  await command('carefixture deny-spawn true');assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/);await command('carefixture deny-spawn false');
  alice.useItem();await eventually(async()=>/mosa=1/.test(await command('carefixture mosa-count ark_model_test')),'Native use-item packet must create one wild Mosa');assert.equal((await inventory(alice)).eggs,'1','Creative spawn retains reusable egg');
  const mosa=await inspect('care_mosa');assert.equal(mosa.type,'DOLPHIN');assert.equal(mosa.owner,'null');assert.equal(mosa.adultAt,'0');assert.equal(mosa.growthDuration,'0');assert.equal(mosa.scale,'4.0');
  await command(`gamemode survival ${alice.name}`);await food(alice,'COD',8);await click(alice,'care_mosa');assert.equal((await inspect('care_mosa')).owner,'null');assert.equal((await inventory(alice)).amount,'8');assert.equal((await inspect('care_mosa')).passengers,'0');
  await command(`gamemode creative ${alice.name}`);await command(`carefixture egg ${alice.name} mosasaurus`);await command(`execute in minecraft:ark run tp ${alice.name} 0 10 0`);assert.match(await command(`carefixture wild-egg ${alice.name}`),/mosa=0/,'Main-world manual Mosa spawning is rejected');
 });
 assert.doesNotMatch(log,/Could not pass event|Task .*ArkDinos.*exception|Error occurred while (enabling|disabling) ArkDinos|Sniffer controller unavailable/);
}catch(error){console.error(error.stack);process.exitCode=1;}
finally{
 clients.forEach(c=>c.close());if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit');}}
 report.finishedAt=new Date().toISOString();await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,log);console.log(`Report: ${reportPath}`);
}
