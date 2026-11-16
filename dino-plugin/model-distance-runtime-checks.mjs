import assert from 'node:assert/strict';

export default async function ({check,command,inspect,delay,alice,readVi,report,eventually}) {
  const fields=text=>Object.fromEntries(text.trim().split(';').map(row=>{const at=row.indexOf('=');return[row.slice(0,at),row.slice(at+1)];}));
  if(!(await command('dm inspect triceratops')).startsWith('id='))await command('dm spawn triceratops 0 -13');
  await eventually(async()=>(await inspect('triceratops')).trackers==='1','Distance carrier model must finish startup attachment');
  await check('Persisted dinosaur trackers migrate their animation sight setting while preserving data, passengers and unrelated trackers',async()=>{
    await command('dm stopmotion triceratops');await command('dm mount triceratops ModelRider');
    await command('dm legacy-tracker triceratops');await delay(150);
    const before=fields(await command('dm migration-state triceratops ModelRider')),saved=JSON.parse(before.data);
    assert.equal(saved.modifier['sight-trace'],true);assert.equal(before.passenger,'true');
    await command('dm attach triceratops');await delay(250);
    const after=fields(await command('dm migration-state triceratops ModelRider')),migrated=JSON.parse(after.data);
    assert.equal(migrated.modifier['sight-trace'],false,'Restored tracker kept the old animation sight cutoff');
    saved.modifier['sight-trace']=false;assert.deepEqual(migrated,saved,'Migration changed unrelated persistent tracker settings');
    const persisted=JSON.parse(after.persisted),shared=data=>JSON.parse(data).find(row=>row.id==='shared_fixture');
    assert.equal(persisted.find(row=>row.id==='ark_triceratops').modifier['sight-trace'],false);
    assert.deepEqual(shared(after.persisted),shared(before.persisted),'Migration changed saved unrelated model data');
    assert.notEqual(after.identity,before.identity);assert.equal(after.shared,before.shared);assert.equal(after.passenger,'true');
    await eventually(async()=>/active=1;stale=0/.test(await command('dm bridge-state triceratops')),'Migration must leave one current Bridge actor and no stale tracker');
    await command('dm attach triceratops');const repeated=fields(await command('dm migration-state triceratops ModelRider'));
    assert.equal(repeated.identity,after.identity,'Already migrated tracker was recreated');
    await command('dm dismount triceratops');return{before,after,repeated};
  });
  await check('Tracked distant Java dinosaurs keep walking legs while untracked viewers receive no transforms',async()=>{
    await command('fill -8 3 -18 20 3 -8 stone');
    const samples=[];report.distanceGait=samples;
    for(const distance of [10,20,40,64]) {
      await command('dm stopmotion triceratops');await command('dm position triceratops 0 4 -13');
      await command(`tp ModelRider 0.0 4.0 ${-13-distance}.0 0 0`);await delay(500);
      const tracked=fields(await command('dm distance-state triceratops ModelRider'));
      const legs=tracked.legs.split(',').map(Number);assert.equal(legs.length,4);
      const before=await inspect('triceratops'),from=alice.packets.length;
      await command('dm motion triceratops .12 -.08 0');await delay(1600);
      const moving=await inspect('triceratops');
      assert.ok(Number(moving.x)-Number(before.x)>2,'Carrier must actually translate');
      const wire=alice.packets.slice(from),legPackets=wire.filter(packet=>packet.id===0x65&&legs.includes(readVi(Buffer.from(packet.hex,'hex')).value));
      const poses=new Set(legPackets.map(packet=>packet.hex));
      const movement=fields(await command('dm movement-id triceratops')),carrier=Number(moving.id),positions=[],movementPackets=[];
      let position={x:Number(before.x),y:Number(before.y),z:Number(before.z)};
      for(const packet of wire) {
        if(![Number(movement.sync),Number(movement.relative),Number(movement.relativeRotation)].filter(id=>id>0).includes(packet.id))continue;
        const bytes=Buffer.from(packet.hex,'hex'),entity=readVi(bytes);if(entity.value!==carrier)continue;
        movementPackets.push(packet);
        if(packet.id===Number(movement.sync)) {
          const path=readVi(bytes,entity.end);let at=path.end,count=1;
          if(path.value===1){const steps=readVi(bytes,at);count=steps.value;at=steps.end;}else assert.equal(path.value,0);
          for(let step=0;step<count;step++){position={x:bytes.readDoubleBE(at),y:bytes.readDoubleBE(at+8),z:bytes.readDoubleBE(at+16)};at=readVi(bytes,at+24).end;}
        } else {
          // Protocol777 packs on-ground/step-count before linear or timed VecDelta steps.
          const properties=readVi(bytes,entity.end),steps=properties.value>>>1;let at=properties.end;
          for(let step=0;step<Math.max(1,steps);step++) {
            if(steps>0)at=readVi(bytes,at).end;
            position={x:position.x+bytes.readInt16BE(at)/4096,y:position.y+bytes.readInt16BE(at+2)/4096,z:position.z+bytes.readInt16BE(at+4)/4096};at+=6;
          }
        }
        positions.push({...position});
      }
      const passengerPackets=alice.packets.filter(packet=>packet.id===Number(moving.packetId)).map(packet=>{
        const bytes=Buffer.from(packet.hex,'hex'),entity=readVi(bytes),count=readVi(bytes,entity.end),passengers=[];let at=count.end;
        for(let i=0;i<count.value;i++){const id=readVi(bytes,at);passengers.push(id.value);at=id.end;}return{carrier:entity.value,passengers};
      }).filter(packet=>packet.carrier===carrier);
      const sample={distance,tracked,before,moving,legPackets:legPackets.length,distinctPoses:poses.size,positions,movementPackets,passengers:passengerPackets.at(-1)};samples.push(sample);
      if(distance<=40) {
        assert.match(moving.running,/name=walk/);
        assert.equal(tracked.nativeTracked,'true',`${distance} blocks must retain native tracking`);
        assert.equal(tracked.modelTracked,'true',`${distance} blocks must retain model tracking`);
        assert.ok(poses.size>4,`${distance} blocks: moving tracked dinosaur received no changing leg poses`);
        assert.ok(positions.length>1&&positions.at(-1).x-Number(before.x)>2,`${distance} blocks: native movement did not reach the viewer`);
        assert.ok(Math.hypot(positions.at(-1).x-Number(moving.x),positions.at(-1).y-Number(moving.y),positions.at(-1).z-Number(moving.z))<.6,`${distance} blocks: viewer carrier position diverged from the real carrier`);
        assert.ok(legs.every(id=>sample.passengers?.passengers.includes(id)),'Leg displays must ride the same moving native carrier on the wire');
      } else {
        assert.equal(tracked.nativeTracked,'false','64 blocks must be beyond this fixture native tracking');
        assert.equal(tracked.modelTracked,'false','64 blocks must be beyond this fixture model tracking');
        assert.equal(legPackets.length,0,'Untracked viewers must not receive animation transforms');
        assert.equal(positions.length,0,'Untracked viewers must not receive carrier movement');
      }
    }
    await command('dm stopmotion triceratops');await command('dm position triceratops 7 4 8');await command('dm migration-cleanup triceratops');return samples;
  });
}
