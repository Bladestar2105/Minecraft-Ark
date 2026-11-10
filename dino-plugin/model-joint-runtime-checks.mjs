import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';

export default async function run({check,command,inspect,delay,directory,report,alice}) {
 await command('fill 0 3 -6 64 3 6 stone');await command('fill 0 4 -6 64 16 6 water');
 await command('dm spawn-joints mosasaurus 12 0');
 const model=JSON.parse(await readFile(`${directory}/plugins/BetterModel/models/ark_mosasaurus.bbmodel`,'utf8'));
 const headOrigin=model.groups.find(b=>b.name==='head').origin,jawOrigin=model.groups.find(b=>b.name==='jaw').origin;
 const offset=jawOrigin.map((v,i)=>(v-headOrigin[i])*(i===2?-1:1)/16);
 const rotate=(q,v)=>{
  const[x,y,z,w]=q,c=[y*v[2]-z*v[1],z*v[0]-x*v[2],x*v[1]-y*v[0]],cc=[y*c[2]-z*c[1],z*c[0]-x*c[2],x*c[1]-y*c[0]];
  return v.map((value,i)=>value+2*(w*c[i]+cc[i]));
 };
 report.jointDiagnostics=[];
 for(const scenario of ['fresh-first-viewer','idle','attack-idle','swim','attack-swim','interrupted-attacks','cancelled-attack'])await check(`Mosa real engine joint state capture: ${scenario}`,async()=>{
  if(scenario==='swim')await command('dm swim-motion mosasaurus .12 0 0');
  if(scenario!=='fresh-first-viewer')await command('dm sample-joints mosasaurus');
  if(scenario.startsWith('attack-'))await command('dm attack mosasaurus');
  if(scenario==='cancelled-attack'){await command('dm attack mosasaurus');await delay(125);await command('dm cancel-attack mosasaurus');}
  if(scenario==='interrupted-attacks') {
   for(let i=0;i<3;i++){await command('dm attack mosasaurus');await delay(125);}
   await command('dm stopmotion mosasaurus');await command('dm phase mosasaurus idle');
  }
  await delay(3300);await command('dm detail-samples mosasaurus');await command('dm joint-packets');
  const rows=JSON.parse(await readFile(`${directory}/plugins/ModelFixture/detail-mosasaurus.json`,'utf8'));
  assert.equal(rows.length,61*3,'All three real joint bones require61 complete engine frames');
  for(const row of rows)for(const key of ['worldPosition','worldRotation','rotation','transform','interpolatedTransform','interpolatedRotation'])assert.ok(row[key].every(Number.isFinite),`${scenario}/${row.bone} has nonfinite actual ${key}`);
  let maxHingeError=0,maxInterpolatedHingeError=0;
  for(let tick=0;tick<=60;tick++) {
   const head=rows.find(row=>row.tick===tick&&row.bone==='head'),jaw=rows.find(row=>row.tick===tick&&row.bone==='jaw');
   assert.equal(head.clip,jaw.clip,'Head/jaw must select the same inherited real animation');
   assert.equal(head.frame,jaw.frame);assert.equal(head.delay,jaw.delay);
   const rotated=rotate(head.rotation,offset),error=Math.hypot(...jaw.transform.map((v,i)=>v-head.transform[i]-rotated[i]));
   assert.ok(error<.0002,`${scenario}/${tick} real jaw pivot detached from head: ${error}`);maxHingeError=Math.max(maxHingeError,error);
   const interpolatedOffset=rotate(head.interpolatedRotation,offset),interpolatedError=Math.hypot(...jaw.interpolatedTransform.map((v,i)=>v-head.interpolatedTransform[i]-interpolatedOffset[i]));maxInterpolatedHingeError=Math.max(maxInterpolatedHingeError,interpolatedError);
  }
  const file=`${directory}/joint-${scenario}.json`;await writeFile(file,JSON.stringify(rows,null,2));
  const packets=JSON.parse(await readFile(`${directory}/plugins/ModelFixture/joint-packets.json`,'utf8'));
  const packetFile=`${directory}/joint-${scenario}-packets.json`;await writeFile(packetFile,JSON.stringify(packets,null,2));
  for(const bone of ['neck','head','jaw']) {
   const id=rows.find(row=>row.bone===bone).display;
   assert.ok(id>=0);
   if(scenario==='fresh-first-viewer') {
    assert.ok(packets.some(packet=>packet.id===id&&packet.packet==='metadata'),`${bone}: first viewer lacks initial display metadata`);
    assert.ok(packets.some(packet=>packet.id===id&&packet.packet==='spawn'),`${bone}: first viewer never received its actual display spawn`);
   }
  }

  let initialWireHingeError;
  if(scenario==='fresh-first-viewer') {
   const first=bone=>{
    const id=rows.find(row=>row.bone===bone).display;
    const packet=packets.find(packet=>packet.id===id&&packet.packet==='metadata'&&packet.values.some(value=>value.index===11));
    const values=Object.fromEntries(packet.values.map(value=>[value.index,value.value]));
    assert.ok(Array.isArray(values[11])&&Array.isArray(values[13]),`${bone}: initial transform must contain numeric translation and rotation`);
    return{position:values[11],rotation:values[13]};
   };
   const head=first('head'),jaw=first('jaw'),rotated=rotate(head.rotation,offset);
   initialWireHingeError=Math.hypot(...jaw.position.map((value,i)=>value-head.position[i]-rotated[i]));
   assert.ok(initialWireHingeError<.0002,`Fresh viewer initial wire jaw hinge detached: ${initialWireHingeError}`);
  }
  const state=await inspect('mosasaurus'),summary={scenario,file,packetFile,state,frames:rows.length,maxHingeError,maxInterpolatedHingeError,initialWireHingeError,clips:Object.fromEntries(['neck','head','jaw'].map(bone=>[bone,[...new Set(rows.filter(row=>row.bone===bone).map(row=>row.clip))]]))};
  report.jointDiagnostics.push(summary);return summary;
 });
}
