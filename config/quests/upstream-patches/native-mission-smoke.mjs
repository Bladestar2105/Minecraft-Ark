#!/usr/bin/env node
// Disposable loopback Paper fixture and direct protocol777 clients; no historical world is opened.
import assert from 'node:assert/strict';
import net from 'node:net';
import { resolve } from 'node:path';
import { tmpdir } from 'node:os';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { EventEmitter, once } from 'node:events';
import { spawn, execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, writeFile, copyFile, readFile, symlink, cp, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

const root = resolve(fileURLToPath(new URL('../../../', import.meta.url)))+'/';
const plugin = `${root}dino-plugin/`;
const javaHome = process.env.JAVA_HOME ?? `${plugin}.tools/jdk-25.0.5+8/Contents/Home`;
const testClasspath=process.env.QUEST_TEST_CLASSPATH ?? `${root}.deps/*`;
const cache=process.env.DINO_PAPER_CACHE;
if(!cache||!process.env.DINO_PAPER_JAR)throw Error('Set DINO_PAPER_CACHE and DINO_PAPER_JAR to the disposable Paper26.3 build252 cache and JAR');
const paperJar=process.env.DINO_PAPER_JAR;
const confirmationTest=process.env.QUEST_CONFIRMATION_TEST==='1';
const output=process.env.QUEST_TEST_OUTPUT ?? await mkdtemp(`${tmpdir()}/ark-mission-tests-`);
await mkdir(output, { recursive: true });
const directory = await mkdtemp(`${output}/controls-smoke-`);
const report={startedAt:new Date().toISOString(),fixture:directory,checks:[],limits:['Disposable Paper26.3 build252 with pinnedQuests5.3.2, real loopback protocol777 player; no production world/profile opened.','Actual loopback protocol777 unsigned command0x07 packets exercise native commands for a non-OP player. Optional confirmation checks also send chat0x09; fixture seeding is restricted to synthetic QuestNameProbe history/inventory, without rewards.','Outgoing native chat packets are captured as UTF8 NBT payloads; no rendered PS5/Switch/Bedrock client.']};
const reportPath=`${output}/quest-runtime-report.json`;
const clients=[];const priorLogs=[];let server,log='';
const vi = value => { const out = []; do { let byte = value & 127; value >>>= 7; if (value) byte |= 128; out.push(byte); } while (value); return Buffer.from(out); };
const readVi = (buffer, start = 0) => { let value = 0; for (let n = 0; n < 5; n++) { if (start + n >= buffer.length) return null; const byte = buffer[start + n]; value |= (byte & 127) << (7 * n); if (!(byte & 128)) return { value, end: start + n + 1 }; } throw Error('Invalid VarInt'); };
const string = value => { const bytes = Buffer.from(value); return Buffer.concat([vi(bytes.length), bytes]); };
const uuidString = raw => { const s = raw.toString('hex'); return `${s.slice(0, 8)}-${s.slice(8, 12)}-${s.slice(12, 16)}-${s.slice(16, 20)}-${s.slice(20)}`; };
async function freePort() { const listener = net.createServer(); listener.listen(0, '127.0.0.1'); await once(listener, 'listening'); const port = listener.address().port; await new Promise(resolve => listener.close(resolve)); return port; }
const port = await freePort(), rconPort = await freePort(), password = crypto.randomBytes(24).toString('hex');

// This minimal client only implements the pinned offline protocol's handshake, acknowledgements and controls.
class Client extends EventEmitter {
  constructor(name) {
    super(); this.name = name; this.phase = 'login'; this.buffer = Buffer.alloc(0); this.compression = -1; this.entities = new Map(); this.messages=[]; this.packets=[];
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
      const visible=body.toString('utf8'); if(body.length<4096)this.packets.push({id,text:visible});if(body.length<4096&&id===0x7c)this.messages.push({id,text:visible});
      if (id === 0x2d) this.send(0x1c, body);
      if (id === 0x49) { const token = readVi(body); this.send(0, Buffer.concat([vi(token.value), body.subarray(token.end, token.end + 24), body.subarray(token.end + 48, token.end + 56)])); this.send(0x2c); }
      if (id === 0x32) { this.send(0x2c); this.joined = true; }
      if (id === 0x01) { const entity = readVi(body); this.entities.set(uuidString(body.subarray(entity.end, entity.end + 16)), entity.value); }
      if (id === 0x20) this.failure = Error(`Play disconnect: ${body.toString('hex')}`);
    }
  }
  chatCommand(text) { this.send(0x07,string(text)); }
  chat(text) { const stamp=Buffer.alloc(8);stamp.writeBigInt64BE(BigInt(Date.now()));this.send(0x09,Buffer.concat([string(text),stamp,Buffer.alloc(8),Buffer.from([0]),vi(0),Buffer.alloc(4)])); }
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

const main=async()=>{
 for(const name of ['libraries','versions','cache'])await symlink(`${cache}/${name}`,`${directory}/${name}`);
 await copyFile(`${root}config/permissions.yml`,`${directory}/permissions.yml`);await mkdir(`${directory}/plugins/Quests/storage`,{recursive:true});await copyFile(paperJar,`${directory}/paper.jar`);await copyFile(process.env.QUEST_JAR ?? `${root}.deps/Quests-5.3.2.jar`,`${directory}/plugins/Quests-5.3.2.jar`);
 report.questsSha256=crypto.createHash('sha256').update(await readFile(`${directory}/plugins/Quests-5.3.2.jar`)).digest('hex');
 for(const [source,target]of [['config.yml','config.yml'],['quests.yml','storage/quests.yml'],['conditions.yml','storage/conditions.yml']])await copyFile(`${root}config/quests/${source}`,`${directory}/plugins/Quests/${target}`);
 if(confirmationTest){const path=`${directory}/plugins/Quests/config.yml`;await writeFile(path,(await readFile(path,'utf8')).replace('confirm-accept: false','confirm-accept: true'));}
 await mkdir(`${directory}/fixture-classes`);await mkdir(`${directory}/fixture-resources`);await writeFile(`${directory}/fixture-resources/plugin.yml`,"name: QuestTakeFixture\nversion: 1.0\nmain: de.ark.questtest.MissionFixture\napi-version: '26.3'\ndepend: [Quests]\ncommands:\n  questfixture: {}\n");
 report.fixtureClasspath=testClasspath;
 execFileSync(`${javaHome}/bin/javac`,['--release','25','-sourcepath','','-cp',`${testClasspath}:${directory}/plugins/Quests-5.3.2.jar`,'-d',`${directory}/fixture-classes`,`${root}config/quests/upstream-patches/MissionFixture.java`]);execFileSync(`${javaHome}/bin/jar`,['--create','--file',`${directory}/plugins/QuestTakeFixture.jar`,'-C',`${directory}/fixture-classes`,'.','-C',`${directory}/fixture-resources`,'.']);
 await mkdir(`${directory}/plugins/Quests/lang/de-DE`,{recursive:true});
 execFileSync('python3',['-c',`import json,pathlib,sys,zipfile
r=pathlib.Path(sys.argv[1]);q=pathlib.Path(sys.argv[2]);overrides=json.loads((r/'config/quests/messages-de-DE.json').read_text())
with zipfile.ZipFile(q/'plugins/Quests-5.3.2.jar') as z: content=z.read('lang/de-DE/strings.yml').decode('utf-8')
import re
for k,v in overrides.items(): content=re.sub('^'+re.escape(k)+':[^\\r\\n]*',lambda m:k+': '+json.dumps(v,ensure_ascii=False),content,flags=re.M)
(q/'plugins/Quests/lang/de-DE/strings.yml').write_text(content)`,root,directory]);
 await writeFile(`${directory}/eula.txt`,'eula=true\n');await writeFile(`${directory}/server.properties`,`server-ip=127.0.0.1\nserver-port=${port}\nonline-mode=false\nwhite-list=false\nlevel-name=ark\nlevel-type=minecraft:flat\ngenerator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}\ngenerate-structures=false\nview-distance=3\nsimulation-distance=3\nspawn-protection=0\nmax-tick-time=-1\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nenforce-secure-profile=false\nallow-flight=true\ndifficulty=peaceful\nspawn-monsters=false\n`);
 const boot=async()=>{server=spawn(`${javaHome}/bin/java`,['-Xms256M','-Xmx1G','-XX:ActiveProcessorCount=3','-Dterminal.jline=false','-Dterminal.ansi=false','-jar','paper.jar','--nogui'],{cwd:directory,stdio:['pipe','pipe','pipe']});server.stdout.on('data',b=>log+=b);server.stderr.on('data',b=>log+=b);await eventually(()=>log.includes('Done ('),'Quest fixture boot failed',90000);};await boot();
 const actor=new Client('QuestNameProbe');clients.push(actor);await eventually(()=>actor.joined,'Quest actor failed to join',30000);await command('gamemode survival QuestNameProbe');await eventually(async()=>/loading=false;loaded=10/.test(await command('questfixture inspect QuestNameProbe')),'Ten missions did not load',20000);
 const run=async cmd=>{const before=actor.messages.length;const dispatch=cmd.startsWith('chat:')?(actor.chat(cmd.slice(5)),'actual protocol777 chat packet 0x09'):(actor.chatCommand(cmd),'actual protocol777 command packet 0x07');await delay(1100);if(actor.failure)throw actor.failure;if(actor.closed)throw Error('Synthetic player disconnected');const state=await command('questfixture inspect QuestNameProbe');return{command:cmd,dispatch,state,messages:actor.messages.slice(before)}};
 const permissions=await command('questfixture inspect QuestNameProbe');assert.match(permissions,/op=false;permissions=true,true,true/);report.permissions=permissions;
 const names=['Werkzeugmacher','Eisenzeit','Ein treuer Begleiter','Kundschafter','Material fürs Lager','Nachtwache','Nether-Vorstoß','Gefährliche Expedition','Bossauftrag','Das letzte Artefakt'];
 const ids=['werkzeugmacher','eisenzeit','treuer_begleiter','kundschafter','lagermaterial','nachtwache','nether_vorstoss','gefaehrliche_expedition','bossauftrag','letztes_artefakt'];
 const text=result=>result.messages.map(x=>x.text).join(' ');
 const state=()=>command('questfixture inspect QuestNameProbe');
 await check('Player two-page order and prerequisite-aware colors',async()=>{const one=await run('quests list'),two=await run('quests liste 2');const rows=[...one.messages,...two.messages].filter(x=>/\b\d+\. /.test(x.text));assert.deepEqual(rows.map(x=>x.text.match(/\b\d+\. ([^\x00]+)/)[1]),names);for(let i=0;i<rows.length;i++)assert.ok(rows[i].text.includes(i===0||i===4?'yellow':'gray'),`Wrong availability color for ${names[i]}`);assert.match(text(one),/Grau: gesperrt/);assert.match(one.state,/active=\[\];completed=\[\];pending=null;questpoints=0;experience=0;items=0/);return{one,two};});
 await check('Native console and NPC menus preserve order; NPC locked colors and offer guard',async()=>{const consoleList=await command('quests list');assert.deepEqual([...consoleList.matchAll(/\b\d+\. ([^\n\r]+)/g)].map(x=>x[1].trim()),names);const before=actor.messages.length;const npc=await command('questfixture npc QuestNameProbe');await delay(200);assert.ok(npc.includes('npcOrder=['+ids.join(', ')+']'));const rendered=actor.messages.slice(before).map(x=>x.text).join(' ');assert.ok(rendered.indexOf('Werkzeugmacher')<rendered.indexOf('Eisenzeit'));assert.ok(/gray[^]*Eisenzeit/.test(rendered));const choice=await run('quests choice 8');assert.match(text(choice),/Nether-Vorstoß/);assert.match(choice.state,/active=\[\];completed=\[\];pending=null/);return{consoleList,npc,messages:actor.messages.slice(before),choice};});
 await check('Native loaded-set mutation and invalid prerequisite checks fail closed',async()=>{const collection=await command('questfixture collection QuestNameProbe');assert.ok(collection.includes('collectionOrder=['+ids.join(', ')+']'));const invalid=await command('questfixture invalid QuestNameProbe');assert.match(invalid,/invalidRequirements=closed/);return{collection,invalid};});
 await check('Every locked follow-up names its required mission before any confirmation',async()=>{const prerequisites={eisenzeit:['Werkzeugmacher'],treuer_begleiter:['Werkzeugmacher'],kundschafter:['Eisenzeit'],nachtwache:['Material fürs Lager'],nether_vorstoss:['Kundschafter','Nachtwache'],gefaehrliche_expedition:['Nether-Vorstoß'],bossauftrag:['Gefährliche Expedition'],letztes_artefakt:['Bossauftrag']};const results=[];for(let i=0;i<ids.length;i++){if(!prerequisites[ids[i]])continue;const result=await run('quests nehmen '+names[i]);for(const required of prerequisites[ids[i]])assert.ok(text(result).includes(required),`${names[i]} lacks ${required}`);assert.doesNotMatch(text(result),/Quest annehmen|Annehmen.*Ja/);assert.match(result.state,/active=\[\];completed=\[\];pending=null;questpoints=0;experience=0;items=0/);results.push(result);}return results;});
 if(confirmationTest){
 await check('Plain German Nein declines; Ja accepts both roots through native commands without rewards',async()=>{const offer=await run('quests take Werkzeugmacher');assert.match(offer.state,/pending=werkzeugmacher/);const decline=await run('chat:Nein');assert.match(text(decline),/Abgebrochen/);assert.match(decline.state,/active=\[\];completed=\[\];pending=werkzeugmacher;questpoints=0;experience=0;items=0/);const reoffer=await run('quests nehmen Werkzeugmacher');assert.match(reoffer.state,/pending=werkzeugmacher/);const accept=await run('chat:Ja');assert.match(accept.state,/active=\[werkzeugmacher\];completed=\[\];pending=werkzeugmacher;questpoints=0;experience=0;items=0/);await command('questfixture cost QuestNameProbe');const listing=await run('quests list');assert.match(listing.state,/items=1/);const costOffer=await run('quests nehmen Material fürs Lager');assert.match(costOffer.state,/items=1/);const costDecline=await run('chat:Nein');assert.match(costDecline.state,/items=1/);await run('quests take Material fürs Lager');const second=await run('chat:Ja');assert.match(second.state,/active=\[lagermaterial, werkzeugmacher\];completed=\[\];pending=lagermaterial;questpoints=0;experience=0;items=0/);return{offer,decline,accept,listing,costOffer,costDecline,second};});
 await check('Every dependency edge, including the two-branch Nether join and Expedition-to-Boss chain, unlocks only with native prerequisites',async()=>{const before=await state();const deps={eisenzeit:['werkzeugmacher'],treuer_begleiter:['werkzeugmacher'],kundschafter:['eisenzeit'],nachtwache:['lagermaterial'],nether_vorstoss:['kundschafter','nachtwache'],gefaehrliche_expedition:['nether_vorstoss'],bossauftrag:['gefaehrliche_expedition'],letztes_artefakt:['bossauftrag']};const results=[];for(let i=0;i<ids.length;i++){const required=deps[ids[i]];if(!required)continue;for(let missing=0;missing<required.length;missing++){await command('questfixture seed QuestNameProbe '+required.filter((_,j)=>j!==missing).join(' '));const lockedBefore=await state();const locked=await run('quests take '+names[i]);assert.equal(locked.state,lockedBefore);assert.ok(!text(locked).includes('Quest annehmen'));}await command('questfixture seed QuestNameProbe '+required.join(' '));const offer=await run('quests nehmen '+names[i]);assert.match(offer.state,new RegExp('pending='+ids[i]));const decline=await run('chat:Nein');assert.match(decline.state,/active=\[lagermaterial, werkzeugmacher\]/);assert.match(text(decline),/Abgebrochen/);assert.match(decline.state,/questpoints=0;experience=0;items=0/);results.push({offer,decline});}await command('questfixture seed QuestNameProbe');const after=await state();assert.equal(after.replace(/pending=[^;]+;/,'pending=transient;'),before.replace(/pending=[^;]+;/,'pending=transient;'));return results;});
 }else{
 await check('Explicit mission commands accept and save both roots without chat; requirements and costs still apply',async()=>{
  const first=await run('quests nehmen Werkzeugmacher');assert.match(first.state,/active=\[werkzeugmacher\];completed=\[\]/);assert.doesNotMatch(text(first),/Quest annehmen/);
  const locked=await run('quests nehmen Eisenzeit');assert.match(text(locked),/Werkzeugmacher/);assert.match(locked.state,/active=\[werkzeugmacher\];completed=\[\]/);
  await command('questfixture cost QuestNameProbe empty');const missing=await run('quests take Material fürs Lager');assert.match(missing.state,/active=\[werkzeugmacher\];completed=\[\]/);assert.match(missing.state,/items=0/);
  await command('give QuestNameProbe stone 1');const listing=await run('quests list');assert.match(listing.state,/items=1/);
  const second=await run('quests take Material fürs Lager');assert.match(second.state,/active=\[lagermaterial, werkzeugmacher\];completed=\[\]/);assert.match(second.state,/questpoints=0;experience=0;items=0/);assert.doesNotMatch(text(second),/Quest annehmen/);
  const profile=await readFile(`${directory}/plugins/Quests/data/${actor.uuid}.yml`,'utf8');assert.ok(profile.includes('- werkzeugmacher')&&profile.includes('- lagermaterial'));assert.match(profile,/quest-points: 0/);
  return{first,locked,missing,listing,second,profile};
 });
 }
 await check('Existing world condition pauses in ark_creative and resumes in ark',async()=>{const condition=await command('questfixture condition QuestNameProbe');assert.match(condition,/worldCondition=allowed-paused-resumed/);return condition;});
 await check('Accepted roots and empty completion/reward state survive a real server restart',async()=>{actor.close();await delay(500);server.stdin.write('stop\n');await once(server,'exit');const profile=await readFile(`${directory}/plugins/Quests/data/${actor.uuid}.yml`,'utf8');assert.ok(profile.includes('- werkzeugmacher')&&profile.includes('- lagermaterial'));assert.match(profile,/quest-points: 0/);priorLogs.push(log);log='';await boot();const reopened=new Client('QuestNameProbe');clients.push(reopened);await eventually(()=>reopened.joined,'Restarted actor did not join',30000);await eventually(async()=>/loading=false;loaded=10;active=\[lagermaterial, werkzeugmacher\];completed=\[\];pending=null;questpoints=0;experience=0;items=0/.test(await state()),'Accepted missions did not survive restart',20000);const before=reopened.messages.length;reopened.chatCommand('quests list');await delay(300);reopened.chatCommand('quests list 2');await delay(300);const rows=reopened.messages.slice(before).filter(x=>/\b\d+\. /.test(x.text));assert.deepEqual(rows.map(x=>x.text.match(/\b\d+\. ([^\x00]+)/)[1]),names);return{profile,state:await state(),messages:reopened.messages.slice(before)};});
 assert.doesNotMatch(log,/Could not pass event|Error occurred while enabling Quests|Exception in thread|ConditionFormatException|QuestFormatException/);
};
try{
 await mkdir(`${directory}/config/quests`,{recursive:true});await copyFile(`${root}config/quests/messages-de-DE.json`,`${directory}/config/quests/messages-de-DE.json`);await copyFile(`${root}dependencies-runtime.json`,`${directory}/dependencies-runtime.json`);
 // The public helper accepts an installation root with data/plugins; this fixture's Paper root is its data directory.
 await main();
}catch(error){console.error(error.stack);report.error=error.stack;process.exitCode=1;}
finally{clients.forEach(c=>c.close());if(server&&server.exitCode===null){server.stdin.write('stop\n');await Promise.race([once(server,'exit'),delay(15000,undefined,{ref:false})]);if(server.exitCode===null){server.kill('SIGTERM');await once(server,'exit')}}if(!report.error&&clients[0]?.uuid){const profile=await readFile(`${directory}/plugins/Quests/data/${clients[0].uuid}.yml`,'utf8');assert.match(profile,/currentQuests:/);assert.ok(profile.includes('- werkzeugmacher')&&profile.includes('- lagermaterial'));assert.match(profile,/quest-points: 0/);report.savedAcceptance={uuid:clients[0].uuid,profile,bothRootAcceptancesPersisted:true};}report.finishedAt=new Date().toISOString();report.packets=clients.map(c=>c.packets);await writeFile(reportPath,JSON.stringify(report,null,2)+'\n');await writeFile(`${directory}/console.log`,[...priorLogs,log].join('\n--- native fixture restart ---\n'));console.log(`Report: ${reportPath}`)}
