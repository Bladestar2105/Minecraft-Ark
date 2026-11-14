import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { once } from 'node:events';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';

export default async function run({check,command,alice,clients,Client,eventually,delay,report,start,server,directory,root,readVi,string}) {
 const audit=()=>JSON.parse(execFileSync('python3',[`${root}dino-models/verify_java_pack.py`,`${directory}/plugins/BetterModel/build.zip`],{encoding:'utf8'}));
 const auditBedrock=()=>JSON.parse(execFileSync('python3',[`${root}dino-models/verify_bedrock_pack.py`,`${directory}/plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack.zip`],{encoding:'utf8'}));
 const species=['trex','triceratops','stegosaurus','brachiosaurus','velociraptor','pteranodon','dilophosaurus','mosasaurus'];
 const factors={trex:2.1,triceratops:1.45,stegosaurus:1.55,brachiosaurus:2.15};
 const fields=text=>Object.fromEntries(text.trim().split(';').map(row=>{const at=row.indexOf('=');return[row.slice(0,at),row.slice(at+1)];}));
 const inspect=async tag=>fields(await command(`dm inspect ${tag}`));
 const player=async client=>fields(await command(`dm player ${client.name}`));
 let bob=new Client('ModelGuest');clients.push(bob);await eventually(()=>bob.joined,'Second model gameplay client did not join');
 let ids=fields(await command(`dm ids ${alice.name}`));
 const register=client=>client.send(Number(ids.pluginMessage),Buffer.concat([string('minecraft:register'),Buffer.from('geyserutils:main')]));
 register(alice);register(bob);
 await command('gamemode survival @a');await command('effect give @a resistance 9999 4 true');
 await command('fill -16 3 -16 64 3 16 stone');
 const mount=async(client,tag)=>{
  client.input(0);await command(`item replace entity ${client.name} weapon.mainhand with air`);
  const mob=await inspect(tag);await command(`tp ${client.name} ${mob.x} ${mob.y} ${Number(mob.z)+3}`);await delay(150);client.interact(Number(mob.id));await delay(250);
 };
 const exit=async()=>{alice.input(32);bob.input(32);await delay(150);alice.input(0);bob.input(0);await command('dm bridge-off');};
 const mounts=(client,from=0)=>client.packets.slice(from).filter(p=>p.id===Number(ids.passengers)).map(p=>{
  const b=Buffer.from(p.hex,'hex'),entity=readVi(b),count=readVi(b,entity.end),passengers=[];let at=count.end;
  for(let i=0;i<count.value;i++){const next=readVi(b,at);passengers.push(next.value);at=next.end;}
  return{vehicle:entity.value,passengers};
 });
 const assertMounted=async tag=>{
  const mob=await inspect(tag),driver=await player(alice),guest=await player(bob);
  assert.equal(driver.vehicle,mob.id);assert.equal(guest.type,'ARMOR_STAND');assert.notEqual(guest.vehicle,mob.id);
  assert.ok(Math.abs(Number(driver.y)-Number(guest.y))<.03);
  assert.ok(Math.abs(Math.hypot(Number(driver.x)-Number(guest.x),Number(driver.z)-Number(guest.z))-1.2)<.03);
  assert.match(mob.extra,new RegExp(`entity=${guest.vehicle},`),'Actual adapter must map the native second seat');
  assert.equal(mob.active,'1');assert.equal(mob.stale,'0');assert.equal(mob.trackers,'1');
  return{mob,driver,guest};
 };
 await check('Installed candidate and real Geyser registries expose all eight models and both resource packs',async()=>{
  const registrations=(await command('dm registrations')).trim().split(';');
  assert.deepEqual(registrations.slice(0,8),species.map(name=>`${name}:true:true`));
  const registry=fields(registrations.slice(8).join(';'));assert.equal(registry.eggs,'7');assert.equal(registry.bedrockEggs,'7');assert.ok(Number(registry.protocols)>0);assert.ok(Number(registry.packs)>=2);
  report.javaPackAudit=audit();report.bedrockPackAuditInitial=auditBedrock();
  report.packs={};
  for(const [kind,path] of Object.entries({java:'plugins/BetterModel/build.zip',bedrock:'plugins/Geyser-Spigot/extensions/geysermodelengineextension/ResourcePack/generated_pack.zip',bedrockEggs:'plugins/Geyser-Spigot/packs/ark-dino-eggs.mcpack'})) {
   const bytes=await readFile(`${directory}/${path}`);assert.ok(bytes.length>1000);report.packs[kind]={path,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
  }
  const protections=await command('dm protections');
  for(const name of ['WorldEdit','WorldGuard','GriefPrevention'])assert.match(protections,new RegExp(`${name}=true`),`${name} must be enabled in the combined stack`);
  assert.match(await command('version ArkDinos'),/1\.2\.1/);return{registrations,protections,packs:report.packs,pluginSha256:report.pluginSha256};
 });
 for(const [i,name] of species.entries()) {
  const type=['trex','velociraptor','dilophosaurus'].includes(name)?'ravager':name==='mosasaurus'?'dolphin':'sniffer';
  if(name==='mosasaurus')await command('fill 47 4 -4 60 13 4 water');
  const wild=name==='mosasaurus',owner=wild?'':`,"arkdinos:owner":"${alice.uuid}"`;
  await command(`summon ${type} ${i*7} ${wild?8:4} 0 {Tags:["model_game_${name}","ark_dino","${wild?'ark_dino_wild':'ark_dino_tamed'}"],NoAI:1b,PersistenceRequired:1b,BukkitValues:{"arkdinos:species":"${name}"${owner},"arkdinos:stay":1b,"arkdinos:growth-duration":900000L}}`);
 }
 for(const name of species)await eventually(async()=>!((await command(`dm inspect model_game_${name}`)).includes('ERROR')),`${name} candidate did not attach its real model`);
 for(const name of Object.keys(factors))await check(`Actual ${name} driver and passenger keep their native vehicles through real model rebuild`,async()=>{
  const tag=`model_game_${name}`;await command(`dm share ${tag}`);await mount(alice,tag);await mount(bob,tag);
  const before=await assertMounted(tag);assert.equal(Number(before.mob.render),factors[name]);
  await command(`dm snapshot ${tag}`);
  const beforePackets=alice.packets.length;
  await command('dm reloadmodel');
  await eventually(async()=>{const state=await inspect(tag);return state.replaced==='true'&&state.oldClosed==='true'&&state.active==='1'&&state.stale==='0';},'Real reload did not close old tracker, construct its replacement and remove stale Bridge mappings',30000);
  const after=await assertMounted(tag);assert.equal(after.mob.uuid,before.mob.uuid);assert.notEqual(after.mob.tracker,before.mob.tracker);assert.notEqual(after.mob.actor,before.mob.actor);
  assert.equal(after.driver.vehicle,before.driver.vehicle);assert.equal(after.guest.vehicle,before.guest.vehicle);
  await eventually(()=>mounts(alice,beforePackets).some(p=>p.vehicle===Number(after.mob.id)&&p.passengers[0]===alice.entityId),'Connected viewer did not receive the rebuilt player-first carrier mount');
  const observer=new Client(`ModelView${name.slice(0,6)}`);clients.push(observer);await eventually(()=>observer.joined,'New observer did not join after rebuild');
  await command(`gamemode creative ${observer.name}`);await command(`tp ${observer.name} ${after.mob.x} 4 4`);
  await eventually(()=>mounts(observer).some(p=>p.vehicle===Number(after.mob.id)&&p.passengers[0]===alice.entityId),'New viewer lacks the rebuilt driver mount');
  await eventually(()=>mounts(observer).some(p=>p.vehicle===Number(after.guest.vehicle)&&p.passengers.includes(bob.entityId)),'New viewer lacks the distinct passenger vehicle');
  const from=alice.packets.length,guestFrom=bob.packets.length;
  await command(`dm bridge ${tag} ${alice.name}`);await command(`dm bridge ${tag} ${bob.name}`);
  alice.input(1);await delay(400);alice.look(35);await delay(400);alice.input(0);await delay(150);
  const positions=(client,from)=>client.packets.slice(from).filter(p=>p.id===Number(ids.sync)).map(p=>{
   const b=Buffer.from(p.hex,'hex'),id=readVi(b),path=readVi(b,id.end);let at=path.end,position,rotation;
   if(path.value===1) {
    const count=readVi(b,at);assert.ok(count.value>0);at=count.end;
    for(let step=0;step<count.value;step++) {
     position={x:b.readDoubleBE(at),y:b.readDoubleBE(at+8),z:b.readDoubleBE(at+16)};
     at=readVi(b,at+24).end;
    }
    rotation=at;
   } else {
    assert.equal(path.value,0,'Unknown pinned protocol777 position path');
    position={x:b.readDoubleBE(at),y:b.readDoubleBE(at+8),z:b.readDoubleBE(at+16)};rotation=at+24;
   }
   return{id:id.value,...position,yaw:b.readFloatBE(rotation)};
  });
  const actor=Number(after.mob.actor),carrier=Number(after.mob.id),seat=Number(after.guest.vehicle);
  let bridgePoses=0;
  for(const [client,offset] of [[alice,from],[bob,guestFrom]]) {
   const sync=positions(client,offset),poses=sync.filter(p=>p.id===actor);assert.ok(poses.length>3,'Actual Bridge actor must move');bridgePoses+=poses.length;
   const angle=Number(after.mob.yaw)*Math.PI/180,rear=1.2+(Number(after.driver.x)-Number(after.mob.x))*Math.sin(angle)-(Number(after.driver.z)-Number(after.mob.z))*Math.cos(angle);
   for(const pose of poses) {
    assert.ok(sync.some(p=>p.id===carrier&&p.x===pose.x&&p.y===pose.y&&p.z===pose.z),'Driver lacks matching actual Bridge pose');
    const yaw=pose.yaw*Math.PI/180,x=pose.x+Math.sin(yaw)*rear,z=pose.z-Math.cos(yaw)*rear,y=pose.y+Number(after.guest.y)+.6-Number(after.mob.y);
    assert.ok(sync.some(p=>p.id===seat&&Math.abs(p.x-x)<.001&&Math.abs(p.y-y)<.001&&Math.abs(p.z-z)<.001),'Passenger lacks offset synchronized to actual Bridge pose');
   }
  }
  const moved=await assertMounted(tag);assert.ok(Math.hypot(Number(moved.mob.x)-Number(after.mob.x),Number(moved.mob.z)-Number(after.mob.z))>.2);
  const packAudit=audit();observer.close();await exit();return{before,after,moved,bridgePoses,packAudit};
 });
 await check('Actual candidate growth and all eight model registrations survive a clean server restart',async()=>{
  const before={};for(const name of species){const tag=`model_game_${name}`;if(name!=='mosasaurus')await command(`dm age ${tag} 450000`);before[name]=await inspect(tag);}
  await exit();clients.forEach(client=>client.close());await command('save-all flush');const stopped=server();stopped.stdin.write('stop\n');await once(stopped,'exit');await start();
  alice=new Client('ModelRider');bob=new Client('ModelGuest');clients.push(alice,bob);await eventually(()=>alice.joined&&bob.joined,'Both clients must reconnect after real restart',30000);
  await command('tp @a 0 4 3');await command('forceload add -16 -16 80 32');await delay(500);ids=fields(await command(`dm ids ${alice.name}`));register(alice);register(bob);
  const after={};for(const name of species) {
   const tag=`model_game_${name}`;await eventually(async()=>{after[name]=await inspect(tag);return after[name].active==='1'&&after[name].stale==='0';},`${name} must restore its actual model after restart`);
   assert.equal(after[name].uuid,before[name].uuid);assert.equal(after[name].adultAt,before[name].adultAt);assert.equal(after[name].extra,'none');
   const factor={...factors,velociraptor:1,pteranodon:1,dilophosaurus:1.15,mosasaurus:1}[name];
   const growth=name==='mosasaurus'?1:.45+.55*Math.min(1,Math.max(0,1-(Number(after[name].adultAt)-Number(after[name].time))/900000));
   assert.ok(Math.abs(Number(after[name].render)-factor*growth)<.002,`${name} must advance growth by the real reboot duration`);
  }
  const registrations=(await command('dm registrations')).trim().split(';');assert.deepEqual(registrations.slice(0,8),species.map(name=>`${name}:true:true`));assert.equal(fields(registrations.slice(8).join(';')).eggs,'7');assert.equal(fields(registrations.slice(8).join(';')).bedrockEggs,'7');
  const packAudit=audit(),bedrockPackAudit=auditBedrock();
  const fallback=[];
  for(const op of ['unload','native']) {
   if(op==='native')await delay(1200); // Includes a real ArkDinos native growth tick.
   const state=fields(await command(`dm ${op} model_game_pteranodon`));fallback.push(state);
   const expectedNative=.45+.55*Math.min(1,Math.max(0,1-(Number(state.adultAt)-Number(state.time))/900000));
   assert.ok(Math.abs(Number(state.scale)-expectedNative)<.002,'Restart must preserve the original native adult baseline when model fallback restores a growing animal');
  }
  await command('dm attach model_game_pteranodon');
  await eventually(async()=>{const state=await inspect('model_game_pteranodon');return state.active==='1'&&state.stale==='0';},'Growing fallback animal must reattach its model');
  assert.ok(Math.abs(Number((await inspect('model_game_pteranodon')).original)-1)<.0001,'Reattachment must retain the native adult baseline');
  await command('gamemode survival @a');await command('dm age model_game_trex -1');await mount(alice,'model_game_trex');await mount(bob,'model_game_trex');const remounted=await assertMounted('model_game_trex');await exit();
  return{before,after,registrations,packAudit,bedrockPackAudit,fallback,remounted};
 });
 report.packsAfterRestart={};
 for(const [kind,pack] of Object.entries(report.packs)) {const bytes=await readFile(`${directory}/${pack.path}`);report.packsAfterRestart[kind]={...pack,bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};}
}
