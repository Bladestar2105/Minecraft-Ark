import assert from 'node:assert/strict';

export default async function run({check,command,inspect,fields,food,spawnDino,alice,bob,clients,Client,eventually,delay,report,start,stop}) {
 const player=async c=>fields(await command(`controlsfixture player ${c.name}`));
 let tag='seat_test';
 const mount=async(c)=>{
  const mob=await inspect(tag);c.input(0);await food(c,'AIR',0);
  await command(`tp ${c.name} ${mob.x} ${mob.y} ${Number(mob.z)+3}`);await delay(150);
  c.interact(Number(mob.id));await delay(250);
 };
 const exit=async()=>{alice.input(32);bob.input(32);await delay(200);alice.input(0);bob.input(0);};
 const fresh=async(species='trex')=>{
  await exit();await command(`kill @e[tag=${tag}]`);tag=`seat_${species}`;
  await command('gamemode survival @a');await command('tp @a 0 4 3');
  await spawnDino(tag,species,0,alice.uuid);await command(`controlsfixture share ${tag} true`);
  alice.look(0);bob.look(45);await mount(alice);await mount(bob);
 };
 await check('Public adult dinosaur accepts a second rider in a distinct native seat, while private access remains denied',async()=>{
  await spawnDino(tag,'trex',0,alice.uuid);alice.look(0);bob.look(45);
  await mount(alice);assert.equal((await player(alice)).vehicle,'RAVAGER');
  await mount(bob);assert.equal((await player(bob)).vehicle,'none','Private access must remain private');
  await command(`controlsfixture share ${tag} true`);await mount(bob);
  const driver=await player(alice),guest=await player(bob),dino=await inspect(tag);
  assert.equal(guest.vehicle,'ARMOR_STAND','Second rider must occupy an independent seat');
  assert.equal(driver.vehicleUuid,dino.uuid);assert.notEqual(guest.vehicleUuid,dino.uuid);
  assert.ok(Math.abs(Number(driver.y)-Number(guest.y))<.02,'Both players should sit at the same height');
  assert.ok(Math.abs(Math.hypot(Number(driver.x)-Number(guest.x),Number(driver.z)-Number(guest.z))-1.2)<.03,'Riders must be 1.2 blocks apart');
  assert.equal(guest.markers,'1');report.initialSeats={driver,guest,dino};return report.initialSeats;
 });
 await check('The passenger follows movement and turns without self teleport spam and keeps independent look direction',async()=>{
  alice.look(0);bob.look(45);bob.input(2);await delay(150);
  const before=await inspect(tag),guestBefore=await player(bob),selfBefore=bob.selfTeleports??0;
  alice.input(1);await delay(650);alice.look(40);await delay(650);alice.input(0);bob.input(0);await delay(150);
  const after=await inspect(tag),driver=await player(alice),guest=await player(bob);
  assert.ok(Math.hypot(Number(after.x)-Number(before.x),Number(after.z)-Number(before.z))>2,'Driver input must move the dinosaur');
  assert.equal(guest.vehicle,'ARMOR_STAND');assert.ok(Math.abs(Number(guest.yaw)-45)<.1,'Guest look must remain independent');
  assert.equal(guest.teleports,guestBefore.teleports,'Normal travel must not teleport the player');
  assert.equal(bob.selfTeleports??0,selfBefore,'Normal travel must not send self teleport corrections');
  assert.ok(Math.abs(Math.hypot(Number(driver.x)-Number(guest.x),Number(driver.z)-Number(guest.z))-1.2)<.03);
  return {before,after,driver,guest,selfTeleports:(bob.selfTeleports??0)-selfBefore};
 });
 await check('Only the driver commands dinosaur attacks and the dinosaur cannot target its co-rider',async()=>{
  assert.match(await command(`controlsfixture manual-attack ${bob.name}`),/accepted=false/);
  assert.match(await command(`controlsfixture valid ${tag} ${bob.name}`),/valid=false/);
  assert.match(await command(`controlsfixture seat-target ${tag} ${bob.name}`),/valid=false/,'Seat helper must not become a combat target');
  assert.match(await command(`controlsfixture manual-attack ${alice.name}`),/accepted=true/);
 });
 await check('Forced passenger exit preserves safe feet below a low ceiling without resistance masking collision',async()=>{
  const guest=await player(bob),x=Math.floor(Number(guest.x)),z=Math.floor(Number(guest.z)),y=Math.ceil(Number(guest.y)+1.8);
  await command(`effect clear ${bob.name} resistance`);await command(`setblock ${x} ${y} ${z} stone`);
  const exit=fields(await command(`controlsfixture revoke-inspect ${tag} ${bob.name}`));
  assert.equal(exit.vehicle,'none');assert.equal(exit.blocked,'false','Forced exit must not raise the passenger into the ceiling');
  assert.ok(Math.abs(Number(exit.y)-Number(guest.y))<.02,'Forced exit should preserve the clear passenger feet');
  await command(`setblock ${x} ${y} ${z} air`);await command(`effect give ${bob.name} resistance 9999 4 true`);
  await command(`controlsfixture share ${tag} true`);await mount(bob);assert.equal((await player(bob)).vehicle,'ARMOR_STAND');
  return {guest,exit};
 });
 await check('Guest Sneak removes only the guest; revoking public access preserves the owner driver',async()=>{
  bob.input(32);await eventually(async()=>(await player(bob)).vehicle==='none','Guest Sneak must detach the second seat');bob.input(0);
  assert.equal((await player(alice)).vehicle,'RAVAGER');assert.equal((await player(bob)).markers,'0');
  assert.equal((await player(bob)).slowFall,'0','A low ground dismount must not grant a long Slow Falling effect');
  await mount(bob);assert.equal((await player(bob)).vehicle,'ARMOR_STAND');
  await command(`controlsfixture share ${tag} false`);await delay(150);
  assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(alice)).vehicle,'RAVAGER');assert.equal((await player(bob)).markers,'0');
 });
 await check('Driver Sneak ends both seats without promoting the passenger',async()=>{
  await command(`controlsfixture share ${tag} true`);await mount(bob);assert.equal((await player(bob)).vehicle,'ARMOR_STAND');
  alice.input(32);await eventually(async()=>(await player(alice)).vehicle==='none'&&(await player(bob)).vehicle==='none','Driver exit must end both seats');alice.input(0);
  assert.equal((await player(bob)).markers,'0');assert.equal((await inspect(tag)).owner,alice.uuid);
 });
 await check('High-seat exit protection follows actual ground distance and preserves longer or infinite effects',async()=>{
  await fresh();await command(`effect clear ${bob.name} slow_falling`);
  await command(`attribute @e[tag=${tag},limit=1] minecraft:scale base set 3`);await delay(250);
  bob.input(32);await eventually(async()=>(await player(bob)).vehicle==='none','Scaled passenger did not dismount');bob.input(0);
  const bounded=await player(bob);assert.ok(Number(bounded.slowFall)>0&&Number(bounded.slowFall)<120,'Normal high-seat exit must get seconds of protection, not a world-depth duration');
  await command(`effect give ${bob.name} slow_falling 60 0 true`);await mount(bob);bob.input(32);await delay(150);bob.input(0);
  assert.ok(Number((await player(bob)).slowFall)>1000,'Existing longer protection must remain');
  await command(`effect give ${bob.name} slow_falling infinite 0 true`);await mount(bob);bob.input(32);await delay(150);bob.input(0);
  assert.equal((await player(bob)).slowFall,'-1','Existing infinite protection must remain');
  await command(`effect clear ${bob.name} slow_falling`);return bounded;
 });
 await check('All four large adult species accept exactly two riders; small species and juveniles remain excluded',async()=>{
  const third=new Client('SeatThird');clients.push(third);await eventually(()=>third.joined,'Third client did not join',30000);
  await command(`gamemode survival ${third.name}`);await command(`effect give ${third.name} resistance 9999 4 true`);
  for(const species of ['trex','triceratops','stegosaurus','brachiosaurus']) {
   await fresh(species);const driver=await player(alice),guest=await player(bob);
   assert.equal(guest.vehicle,'ARMOR_STAND',species);assert.ok(Math.abs(Number(driver.y)-Number(guest.y))<.02,species);
   assert.ok(Math.abs(Math.hypot(Number(driver.x)-Number(guest.x),Number(driver.z)-Number(guest.z))-1.2)<.03,species);
   await mount(third);assert.equal((await player(third)).vehicle,'none','A third rider must stay on foot');
  }
  third.close();
  for(const species of ['velociraptor','dilophosaurus','pteranodon']) {
   await fresh(species);assert.equal((await player(bob)).vehicle,'none',species);assert.notEqual((await player(alice)).vehicle,'none',species);
  }
  await fresh('trex');await command(`controlsfixture time ${tag} adult-at 900000`);await delay(200);
  assert.equal((await player(alice)).vehicle,'none');assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
  await mount(alice);assert.equal((await player(alice)).vehicle,'none','Juveniles must remain unrideable');
 });
 await check('Revoking a public driver ends both seats even when the owner is the passenger',async()=>{
  await fresh();await exit();await mount(bob);await mount(alice);
  assert.equal((await player(bob)).vehicle,'RAVAGER');assert.equal((await player(alice)).vehicle,'ARMOR_STAND');
  await command(`controlsfixture share ${tag} false`);await delay(150);
  assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(alice)).vehicle,'none');
  assert.equal((await player(alice)).markers,'0');assert.equal((await inspect(tag)).owner,alice.uuid);
 });
 await check('Turning sweeps the rear passenger arc and stops before a wall; queries do not load chunks',async()=>{
  await fresh();const before=await inspect(tag),guestBefore=await player(bob);
  await command('setblock -1 6 0 stone');
  const blocked=fields(await command(`controlsfixture seat-motion ${tag} 0 0 0 -180`));assert.equal(blocked.clear,'false');assert.equal(blocked.before,blocked.after);
  alice.look(-180);await delay(250);const after=await inspect(tag),guestAfter=await player(bob);
  assert.equal(after.yaw,before.yaw,'Blocked passenger arc must also block rotation');assert.equal(guestAfter.vehicle,'ARMOR_STAND');
  assert.ok(Math.hypot(Number(guestAfter.x)-Number(guestBefore.x),Number(guestAfter.z)-Number(guestBefore.z))<.02);
  await command('setblock -1 6 0 air');await eventually(async()=>Math.abs(Math.abs(Number((await inspect(tag)).yaw))-180)<1,'Rotation should resume when clear');
  return {before,after,blocked};
 });
 await check('Swimming checks the second rider headroom and keeps both riders aboard in open water',async()=>{
  await fresh();const guest=await player(bob);const x=Math.floor(Number(guest.x)),z=Math.floor(Number(guest.z)),ceiling=Math.ceil(Number(guest.y)+1.8);
  await command(`setblock ${x} ${ceiling} ${z} stone`);
  assert.match(await command(`controlsfixture swim-clear ${tag} 1`),/clear=false/,'A ceiling only above the guest must block ascent');
  await command(`setblock ${x} ${ceiling} ${z} air`);assert.match(await command(`controlsfixture swim-clear ${tag} 1`),/clear=true/);
  await command('fill -5 4 -5 5 10 5 water');await delay(2500);
  const driver=await player(alice),passenger=await player(bob),dino=await inspect(tag);
  assert.equal(driver.vehicle,'RAVAGER');assert.equal(passenger.vehicle,'ARMOR_STAND');assert.ok(Number(dino.y)>7,'Buoyancy must lift the loaded dinosaur');
  assert.ok(Math.abs(Number(driver.y)-Number(passenger.y))<.03);assert.equal(passenger.health,'20.0');
  await command('fill -5 4 -5 5 10 5 air');await exit();await command('tp @a 0 4 3');
  return {driver,passenger,dino};
 });
 await check('Cancelled mounts, external teleports, mode changes, unload and carrier death remove reservations and helpers',async()=>{
  await fresh();await exit();await mount(alice);await command('controlsfixture deny-interact true');await mount(bob);
  assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');await command('controlsfixture deny-interact false');
  await command('controlsfixture deny-seat-mount true');await mount(bob);assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
  await command('controlsfixture deny-seat-mount false');
  await mount(bob);await command(`tp ${bob.name} 6 4 3`);await delay(150);assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(alice)).vehicle,'RAVAGER');assert.equal((await player(bob)).markers,'0');
  await mount(bob);await command(`gamemode creative ${bob.name}`);await delay(100);assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(alice)).vehicle,'RAVAGER');
  await command(`gamemode survival ${bob.name}`);await mount(bob);await command(`controlsfixture ptera-unload ${tag}`);await delay(100);
  assert.equal((await player(alice)).vehicle,'none');assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
  await command(`controlsfixture restore ${tag}`);await mount(alice);await mount(bob);assert.equal((await player(bob)).vehicle,'ARMOR_STAND');
  await command(`kill @e[tag=${tag}]`);await delay(100);assert.equal((await player(alice)).vehicle,'none');assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
 });
 await check('Shutdown and a real restart leave no passenger helpers or inherited driving authority',async()=>{
  await fresh();const uuid=(await inspect(tag)).uuid;
  await command('controlsfixture disable-dinos');await delay(150);
  assert.equal((await player(alice)).vehicle,'none');assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
  await stop();await start();alice=new Client('ControlsOwner');bob=new Client('ControlsOther');clients.push(alice,bob);
  await eventually(()=>alice.joined&&bob.joined,'Clients failed to reconnect after restart',30000);await delay(500);
  assert.equal((await inspect(tag)).uuid,uuid);assert.equal((await inspect(tag)).owner,alice.uuid);assert.equal((await player(alice)).vehicle,'none');
  assert.equal((await player(bob)).vehicle,'none');assert.equal((await player(bob)).markers,'0');
 });
}
