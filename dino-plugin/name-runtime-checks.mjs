import assert from 'node:assert/strict';

// The normal model harness supplies the real Paper server and its protocol client.
export default async function checkNames({check,command,alice,clients,Client,eventually,delay,inspect,species,nativeIds,readVi,spawns,report}) {
  const metadata = (client,id,from=0) => client.packets.slice(from).filter(packet=>packet.id===0x65&&readVi(Buffer.from(packet.hex,'hex')).value===id);
  const destroyed = (client,id,from=0) => client.packets.slice(from).some(packet=>{
    if(packet.id!==nativeIds.destroy)return false;
    const bytes=Buffer.from(packet.hex,'hex'),count=readVi(bytes);let offset=count.end;
    for(let i=0;i<count.value;i++){const next=readVi(bytes,offset);if(next.value===id)return true;offset=next.end;}
    return false;
  });
  const hasText = (client,id,text,from=0) => metadata(client,id,from).some(packet=>packet.hex.includes(Buffer.from(text).toString('hex')));
  const state = async name => {
    const raw=await command(`dm name-state ${name}`);
    return {raw,id:Number(raw.match(/;label=(-?\d+)/)?.[1]),count:Number(raw.match(/;labels=(\d+)/)?.[1])};
  };
  await command('tp ModelRider 0 4 3 180 0');
  const labels={};
  await check('All eight dinosaurs emit actual TextDisplay labels when native CustomNameVisible is false',async()=>{
    for(const name of species) {
      await command(`dm spawn ${name} 0`);
      const text=`Visible_${name}`;
      await command(`dm name-state ${name} ${text}`);
      await eventually(async()=>{labels[name]=await state(name);return labels[name].count===1;},`${name} has no model nametag`);
      assert.match(labels[name].raw,/visible=false/);assert.match(labels[name].raw,/persistent=false;bmLabels=0;/);
      await eventually(()=>hasText(alice,labels[name].id,text),`${name} name text never reached the client`);
      assert.ok(spawns(alice).some(packet=>packet.id===labels[name].id&&packet.type===nativeIds.textDisplay),`${name} label is not a real TextDisplay spawn`);
      labels[name]=await state(name);
      await command(`dm unload ${name}`);
    }
    return labels;
  });
  await command('dm attach trex');
  await command('tp ModelRider 0 4 3 0 0');
  await command('gamemode survival ModelRider');
  await check('Real name-tag interactions rename twice and consume one tag without duplicating labels',async()=>{
    const native=await inspect('trex');
    for(const text of ['Tag_First','Tag_Second']) {
      const from=alice.packets.length;
      await command(`item replace entity ModelRider weapon.mainhand with minecraft:name_tag[minecraft:custom_name={text:"${text}"}] 2`);
      alice.interact(Number(native.id));
      await eventually(async()=>{labels.trex=await state('trex');return labels.trex.raw.includes(text)&&hasText(alice,labels.trex.id,text,from);},'Native name-tag change did not update the visible label');
      assert.equal(labels.trex.count,1);
      assert.match(await command('data get entity ModelRider Inventory'),/count: 1/);
    }
    for(let i=0;i<4;i++)await command('dm attach trex');
    const after=await state('trex');assert.equal(after.id,labels.trex.id);assert.equal(after.count,1);
    return after;
  });
  await command('gamemode creative ModelRider');
  await check('A changed name reaches both existing viewers',async()=>{
    const other=new Client('NameObserver');clients.push(other);
    await eventually(()=>other.joined,'Second naming client did not join',30000);
    await command('tp NameObserver 1 4 3 0 0');
    const before=await state('trex');
    await eventually(()=>hasText(other,before.id,'Tag_Second'),'Second viewer did not receive the initial name');
    const a=alice.packets.length,b=other.packets.length;
    await command('dm name-state trex Shared_Viewers');
    await eventually(()=>hasText(alice,before.id,'Shared_Viewers',a)&&hasText(other,before.id,'Shared_Viewers',b),'Name change did not reach both existing viewers');
    other.close();return {id:before.id,ownerUpdates:metadata(alice,before.id,a),otherUpdates:metadata(other,before.id,b)};
  });
  await check('Clearing a native name clears text already sent to the viewer',async()=>{
    const from=alice.packets.length,old=await state('trex');
    await command('dm name-state trex null');
    await eventually(()=>destroyed(alice,old.id,from),'Clearing a name left the previous visible text on the client');
    const after=await state('trex');assert.match(after.raw,/name=null;/);assert.ok(!after.raw.includes('Tag_Second'));
    await command('dm name-state trex Tag_After_Clear');
    let restored;
    await eventually(async()=>{restored=await state('trex');return restored.count===1&&hasText(alice,restored.id,'Tag_After_Clear',from);},'Renaming after clear did not restore the visible text');
    assert.ok(spawns(alice,from).some(packet=>packet.id===restored.id),'Renaming after clear did not respawn the label');
    return {after,restored,updates:metadata(alice,restored.id,from)};
  });
  await check('Model unload destroys its text label and reattachment preserves the native name',async()=>{
    const before=await state('trex'),from=alice.packets.length;
    await command('dm unload trex');
    await eventually(()=>destroyed(alice,before.id,from),'Model unload did not destroy its text label');
    await command('dm attach trex');
    await eventually(async()=>{labels.trex=await state('trex');return labels.trex.count===1&&labels.trex.id!==before.id&&hasText(alice,labels.trex.id,'Tag_After_Clear',from);},'Reattached model lost its native name label');
    return {before,after:labels.trex};
  });
  await check('BetterModel reload preserves exactly one native label with its saved name',async()=>{
    const before=await state('trex'),from=alice.packets.length;
    await command('dm reloadmodel trex');
    await eventually(async()=>{try{labels.trex=await state('trex');return labels.trex.count===1&&labels.trex.id===before.id&&labels.trex.raw.includes('Tag_After_Clear');}catch{return false;}},'Reload lost the saved native name label',30000);
    return {before,after:labels.trex};
  });
  await check('A returning viewer receives a fresh label spawn with the existing native name',async()=>{
    const before=await state('trex');
    alice.close();await delay(300);alice=new Client('ModelRider');clients.push(alice);
    await eventually(()=>alice.joined,'Returning naming client did not join',30000);
    await eventually(()=>spawns(alice).some(packet=>packet.id===before.id&&packet.type===nativeIds.textDisplay)&&hasText(alice,before.id,'Tag_After_Clear'),'Returning viewer did not receive its existing name label');
    return {before,spawns:spawns(alice).filter(packet=>packet.id===before.id)};
  });
  await check('The Pteranodon label follows its growing model and moves with flight height',async()=>{
    await command('dm attach pteranodon');
    await command('dm position pteranodon 0 8 0');
    await command('tp ModelRider 0 8 3 180 0');
    await command('dm phase pteranodon flap');await delay(400);
    const position=value=>value.raw.match(/;position=([^;]+)/)[1].split(',').map(Number);
    const adult=await state('pteranodon'),a=position(adult);
    assert.ok(a[1]>8.5,'Adult flight label is not above its carrier');
    await command('dm juvenile pteranodon');await delay(400);
    const baby=await state('pteranodon'),b=position(baby);
    assert.equal(baby.id,adult.id);assert.ok(b[1]>8.2&&b[1]<a[1]-.3,'Baby label did not scale with its model');
    await command('dm position pteranodon 0 10 0');
    await command('tp ModelRider 0 10 3 180 0');await delay(400);
    const moved=await state('pteranodon'),c=position(moved);
    assert.ok(Math.abs(c[1]-b[1]-2)<.15,'Label did not follow actual flight height');
    return {adult,baby,moved};
  });
  report.nameLabels=labels;
}
