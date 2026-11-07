import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
const require = createRequire(import.meta.url);
const { SkinEditor } = require('./static/skin_editor.js');
const { defaultRegions, sampleColor, createSkin, limits } = require('./static/photo_skin.js');
const colors = {skin:'#c08040', hair:'#201008', shirt:'#307090', trousers:'#405060'};
const image = (width,height,pixel) => ({width,height,data:Uint8ClampedArray.from(Array.from({length:width*height},(_,i)=>pixel(i%width,Math.floor(i/width))).flat())});
const pixel = (skin,x,y) => [...skin.pixels.slice((y*64+x)*4,(y*64+x)*4+4)];
const facePixel = (skin,part,face,x,y) => {
  const [u,v] = SkinEditor.faceRect(part,face,'base',skin.model);
  return pixel(skin,u+x,v+y);
};
const wholeView = source => ({image:source,regions:Object.fromEntries(SkinEditor.parts.map(part=>[part,{x:0,y:0,width:source.width,height:source.height}]))});
const solid = rgba => wholeView(image(1,1,()=>rgba));

// Defaults cover six nonempty in-bounds regions, including tiny and wide images.
for (const [width,height] of [[100,100],[31,101],[1,1],[1,9],[9,1],[3,3]]) {
  const front = defaultRegions(width,height), back = defaultRegions(width,height,'back');
  assert.deepEqual(Object.keys(front).sort(),[...SkinEditor.parts].sort());
  for (const part of SkinEditor.parts) for (const regions of [front,back]) {
    const r = regions[part];
    assert.ok([r.x,r.y,r.width,r.height].every(Number.isInteger));
    assert.ok(r.x>=0 && r.y>=0 && r.width>=1 && r.height>=1 && r.x+r.width<=width && r.y+r.height<=height);
  }
  assert.deepEqual(back.right_arm,front.left_arm);
  assert.deepEqual(back.left_arm,front.right_arm);
  assert.deepEqual(back.right_leg,front.left_leg);
  assert.deepEqual(back.left_leg,front.right_leg);
}
const defaults = defaultRegions(100,100);
assert.ok(defaults.head.y<defaults.body.y && defaults.body.y<defaults.right_leg.y);
assert.ok(defaults.right_arm.x<defaults.body.x && defaults.left_arm.x>defaults.body.x);
for (const args of [[0,2],[-1,2],[1.5,2],[NaN,2],[2,Infinity],[8193,2],[4096,4096],[2,2,'side']]) assert.throws(()=>defaultRegions(...args));
assert.deepEqual(limits,{bytes:8388608,pixels:16000000,side:8192});

// Independently patterned jerseys catch front-only mapping and mirrored numbers.
const regions = {
  head:{x:0,y:0,width:8,height:8}, body:{x:8,y:0,width:8,height:12},
  right_arm:{x:16,y:0,width:4,height:12}, left_arm:{x:20,y:0,width:4,height:12},
  right_leg:{x:24,y:0,width:4,height:12}, left_leg:{x:28,y:0,width:4,height:12},
};
const makeView = back => {
  const source = image(32,12,()=>[0,0,0,0]);
  SkinEditor.parts.forEach((part,index)=>{
    const r=regions[part];
    for(let y=0;y<r.height;y++) for(let x=0;x<r.width;x++) {
      const rgba = part==='head' ? [x*20,y*20,back?100:0,255]
        : part==='body' ? back ? [220,(x===5 || y===3 && x>=2)?255:10,0,255] : [10+x*20,30+y*10,40,255]
        : [index*30,back?180:60,10,255];
      source.data.set(rgba,((r.y+y)*source.width+r.x+x)*4);
    }
  });
  return {image:source,regions};
};
const views = {front:makeView(false),back:makeView(true)};
const before = [views.front.image.data.slice(),views.back.image.data.slice()];
for (const model of ['classic','slim']) {
  const skin = createSkin(views,colors,model);
  assert.ok(skin instanceof SkinEditor); assert.equal(skin.model,model);
  for(let y=0;y<8;y++) for(let x=0;x<8;x++) {
    assert.deepEqual(pixel(skin,8+x,8+y),[x*20,y*20,0,255]);
    assert.deepEqual(facePixel(skin,'head','back',x,y),[x*20,y*20,100,255]);
  }
  for(let y=0;y<12;y++) for(let x=0;x<8;x++) {
    assert.deepEqual(facePixel(skin,'body','front',x,y),[10+x*20,30+y*10,40,255]);
    assert.deepEqual(facePixel(skin,'body','back',x,y),[220,(x===5 || y===3 && x>=2)?255:10,0,255]);
  }
  SkinEditor.parts.slice(2).forEach((part,index)=>{
    assert.deepEqual(facePixel(skin,part,'front',0,0),[(index+2)*30,60,10,255]);
    assert.deepEqual(facePixel(skin,part,'back',0,0),[(index+2)*30,180,10,255]);
  });
  // Minecraft's bottom UV last row joins the front; its first joins reversed rear X.
  for (const part of SkinEditor.parts) {
    const [,,w,depth]=SkinEditor.faceRect(part,'bottom','base',model);
    const [,,,height]=SkinEditor.faceRect(part,'front','base',model);
    for(let x=0;x<w;x++) {
      assert.deepEqual(facePixel(skin,part,'bottom',x,depth-1),facePixel(skin,part,'front',x,height-1),`${model} ${part} bottom/front seam`);
      assert.deepEqual(facePixel(skin,part,'bottom',x,0),facePixel(skin,part,'back',w-1-x,height-1),`${model} ${part} bottom/back seam`);
    }
  }
  const occupied = new Set();
  for (const part of SkinEditor.parts) for (const face of SkinEditor.faces) {
    const [u,v,w,h]=SkinEditor.faceRect(part,face,'base',model);
    for(let y=v;y<v+h;y++) for(let x=u;x<u+w;x++) {
      occupied.add(y*64+x); assert.equal(pixel(skin,x,y)[3],255,`${model} ${part}/${face}`);
    }
  }
  for(let i=0;i<4096;i++) if(!occupied.has(i)) assert.deepEqual(pixel(skin,i%64,Math.floor(i/64)),[0,0,0,0]);
}
assert.deepEqual(views.front.image.data,before[0]); assert.deepEqual(views.back.image.data,before[1]);
const slimWidth = wholeView(image(4,12,x=>[x===1?120:0,0,0,255]));
assert.deepEqual(facePixel(createSkin({front:slimWidth},colors,'slim'),'right_arm','front',0,0),[30,0,0,255]);
assert.deepEqual(facePixel(createSkin({front:slimWidth},colors,'classic'),'right_arm','front',0,0),[0,0,0,255]);

// Connected surfaces interpolate both visible edges instead of using flat palette.
const connected = createSkin({front:solid([240,0,0,255]),back:solid([0,0,240,255])},colors,'classic');
for(const [face,x,y,want] of [
  ['right',0,2,[0,0,240,255]],['right',1,2,[80,0,160,255]],['right',3,2,[240,0,0,255]],
  ['left',0,2,[240,0,0,255]],['left',3,2,[0,0,240,255]],
  ['top',0,0,[0,0,240,255]],['top',0,3,[240,0,0,255]],
  ['bottom',0,0,[0,0,240,255]],['bottom',0,3,[240,0,0,255]],
]) assert.deepEqual(facePixel(connected,'body',face,x,y),want);
// Rear pixels have opposite screen-side edges to the matching front pixels.
const sides = createSkin(views,colors,'classic');
assert.deepEqual(facePixel(sides,'head','right',0,2),[140,40,100,255]);
assert.deepEqual(facePixel(sides,'head','right',7,2),[0,40,0,255]);
assert.deepEqual(facePixel(sides,'head','left',0,2),[140,40,0,255]);
assert.deepEqual(facePixel(sides,'head','left',7,2),[0,40,100,255]);
const fallback = createSkin({front:solid([240,0,0,255]),back:null},colors,'slim');
assert.deepEqual(facePixel(fallback,'body','back',0,0),[48,112,144,255]);
assert.deepEqual(facePixel(fallback,'head','back',0,0),[32,16,8,255]);
assert.deepEqual(facePixel(fallback,'body','right',3,0),[240,0,0,255]);

// Non-square fractional cells: 9/8 horizontal and 13/12 vertical source pixels.
const fractional = image(11,15,(x,y)=>[x===2?180:0,y===2?130:0,0,255]);
const fractionView = wholeView(fractional);
fractionView.regions.body={x:1,y:1,width:9,height:13};
const averaged=createSkin({front:fractionView},colors,'classic');
assert.deepEqual(facePixel(averaged,'body','front',0,0),[20,10,0,255]);
assert.deepEqual(facePixel(averaged,'body','front',1,0),[140,10,0,255]);
const fine = wholeView(image(16,24,(x,y)=>[x%2?200:0,y%2?100:0,50,255]));
assert.deepEqual(facePixel(createSkin({front:fine},colors,'classic'),'body','front',0,0),[100,50,50,255]);
const transparent = image(1,1,()=>[0,0,0,0]);
const alpha=createSkin({front:wholeView(transparent)},colors,'classic');
assert.deepEqual(pixel(alpha,8,8),[192,128,64,255]);
assert.deepEqual(facePixel(alpha,'body','front',0,4),[48,112,144,255]);
assert.deepEqual(pixel(createSkin({front:solid([0,255,0,128])},colors,'classic'),8,8),[96,192,32,255]);
const tiny=createSkin({front:wholeView(image(1,3,(_x,y)=>[10,20,y*30,255]))},colors,'slim');
assert.deepEqual(pixel(tiny,8,8),[10,20,0,255]);
assert.deepEqual(pixel(tiny,15,15),[10,20,60,255]);

// Median sampling resists outliers, clips edges, and ignores hidden RGB.
const samples=image(3,3,(x,y)=>x===1&&y===1?[255,255,255,255]:[20+x,40+y,60,255]);
assert.equal(sampleColor(samples,1,1),'#15293c');
assert.equal(sampleColor(image(1,1,()=>[1,2,3,255]),100,-20),'#010203');
assert.equal(sampleColor(image(3,3,(x,y)=>x===1&&y===1?[70,80,90,255]:[0,0,0,1]),1,1),'#46505a');
assert.equal(sampleColor(transparent,0,0),'#ffffff');
assert.equal(sampleColor(image(3,1,x=>[x*100,0,0,255]),-10,0),'#000000');
for(const point of [[NaN,0],[0,Infinity],['1',0]]) assert.throws(()=>sampleColor(samples,...point));

for(const badImage of [null,{}, {width:0,height:1,data:new Uint8ClampedArray()}, {width:2,height:2,data:new Uint8ClampedArray(3)}, {width:1,height:1,data:[0,0,0,0]}, {width:8193,height:1,data:new Uint8ClampedArray(4)}]) {
  assert.throws(()=>createSkin({front:wholeView(badImage??{})},colors,'classic'));
  assert.throws(()=>sampleColor(badImage,0,0));
}
for(const region of [null,{x:-1,y:0,width:1,height:1},{x:0,y:0,width:0,height:1},{x:.5,y:0,width:1,height:1},{x:0,y:0,width:33,height:1},{x:31,y:0,width:2,height:1},{x:0,y:NaN,width:1,height:1}]) {
  const bad={...views.front,regions:{...regions,body:region}};
  assert.throws(()=>createSkin({front:bad},colors,'classic'));
  assert.throws(()=>createSkin({front:views.front,back:bad},colors,'classic'));
}
for(const badViews of [null,{}, {back:views.back}, {front:{image:views.front.image,regions:{}}}]) assert.throws(()=>createSkin(badViews,colors,'classic'));
for(const part of SkinEditor.parts) for(const face of ['front','back']) {
  const incomplete={...views[face],regions:{...regions,[part]:undefined}};
  assert.throws(()=>createSkin({...views,[face]:incomplete},colors,'classic'));
}
for(const badColors of [null,{}, {...colors,skin:'#123'}, {...colors,hair:'#zzzzzz'}, {...colors,shirt:0}]) assert.throws(()=>createSkin(views,badColors,'classic'));
assert.throws(()=>createSkin(views,colors,'other'));

const browser=vm.createContext({Uint8ClampedArray});
vm.runInContext(readFileSync(new URL('./static/skin_editor.js',import.meta.url),'utf8'),browser);
vm.runInContext(readFileSync(new URL('./static/photo_skin.js',import.meta.url),'utf8'),browser);
assert.equal(typeof browser.ArkPhotoSkin.defaultRegions,'function');
assert.equal(browser.ArkPhotoSkin.createSkin(views,colors,'slim').model,'slim');
console.log('PASS six photo regions, independent front/back clothing, limb orientation, connected faces, non-square averaging, alpha, sampling, opaque Classic/Slim UVs and browser API');
