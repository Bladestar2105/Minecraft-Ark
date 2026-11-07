"use strict";
((root) => {
  const SkinEditor = typeof module !== 'undefined' ? require('./skin_editor.js').SkinEditor : root.SkinEditor;
  const limits = Object.freeze({bytes:8388608, pixels:16000000, side:8192});

  function dimensions(width, height) {
    if (!Number.isInteger(width) || !Number.isInteger(height) || width < 1 || height < 1 || width > limits.side || height > limits.side || width*height > limits.pixels) throw new Error('Invalid photo dimensions');
  }
  function validateImage(image) {
    if (!image) throw new Error('Invalid photo pixels');
    dimensions(image.width,image.height);
    if (!(image.data instanceof Uint8ClampedArray) || image.data.length !== image.width*image.height*4) throw new Error('Invalid photo pixels');
  }
  function defaultRegions(width, height, view = 'front') {
    dimensions(width,height);
    if (!['front','back'].includes(view)) throw new Error('Invalid photo view');
    const layout = {
      head:[.375,0,.625,.20], body:[.25,.20,.75,.58],
      right_arm:[.075,.20,.25,.58], left_arm:[.75,.20,.925,.58],
      right_leg:[.25,.58,.50,1], left_leg:[.50,.58,.75,1],
    };
    const regions = {};
    for (const part of SkinEditor.parts) {
      const [left,top,right,bottom] = layout[part];
      const x = Math.min(width-1,Math.round(left*width)), y = Math.min(height-1,Math.round(top*height));
      regions[part] = {x,y,width:Math.max(1,Math.round(right*width)-x),height:Math.max(1,Math.round(bottom*height)-y)};
    }
    if (view === 'back') for (const limb of ['arm','leg']) [regions['right_'+limb],regions['left_'+limb]] = [regions['left_'+limb],regions['right_'+limb]];
    return regions;
  }
  function sampleColor(image, x, y) {
    validateImage(image);
    if (!Number.isFinite(x) || !Number.isFinite(y)) throw new Error('Invalid sample point');
    x = Math.max(0,Math.min(image.width-1,Math.round(x)));
    y = Math.max(0,Math.min(image.height-1,Math.round(y)));
    const channels = [[],[],[]];
    let weight = 0;
    for (let row=Math.max(0,y-1);row<=Math.min(image.height-1,y+1);row++) for (let col=Math.max(0,x-1);col<=Math.min(image.width-1,x+1);col++) {
      const offset = (row*image.width+col)*4, alpha = image.data[offset+3];
      if (!alpha) continue;
      weight += alpha;
      for (let c=0;c<3;c++) channels[c].push([image.data[offset+c],alpha]);
    }
    if (!weight) return '#ffffff';
    return '#'+channels.map(values => {
      values.sort((a,b)=>a[0]-b[0]);
      let sum = 0;
      for (const [value,alpha] of values) {
        sum += alpha;
        if (sum >= weight/2) return value.toString(16).padStart(2,'0');
      }
    }).join('');
  }
  function createSkin(views, colors, model) {
    if (!views || !views.front) throw new Error('A front photo is required');
    for (const view of [views.front,views.back]) {
      if (view === null || view === undefined) continue;
      validateImage(view.image);
      for (const part of SkinEditor.parts) {
        const region = view.regions?.[part];
        if (!region || ![region.x,region.y,region.width,region.height].every(Number.isInteger) || region.x < 0 || region.y < 0 || region.width < 1 || region.height < 1 || region.x+region.width > view.image.width || region.y+region.height > view.image.height) throw new Error('Invalid photo region');
      }
    }
    const palette = {};
    for (const key of ['skin','hair','shirt','trousers']) {
      if (!colors || typeof colors[key] !== 'string' || !/^#[0-9a-f]{6}$/i.test(colors[key])) throw new Error('Invalid photo color');
      palette[key] = colors[key].slice(1).match(/../g).map(value=>parseInt(value,16));
    }
    if (!['classic','slim'].includes(model)) throw new Error('Invalid skin model');
    const pixels = new Uint8ClampedArray(64*64*4), shoes = [37,40,50];
    for (const part of SkinEditor.parts) for (const face of ['front','back']) {
      const [u,v,w,h] = SkinEditor.faceRect(part,face,'base',model);
      const view = views[face], region = view?.regions[part];
      for (let y=0;y<h;y++) for (let x=0;x<w;x++) {
        let color;
        if (part === 'head') color = face === 'back' ? palette.hair : palette.skin;
        else if (part === 'body') color = face === 'front' && y === 0 && x >= 3 && x <= 4 ? palette.skin : palette.shirt;
        else if (part.endsWith('arm')) color = y >= 8 ? palette.skin : palette.shirt;
        else color = y >= 9 ? shoes : palette.trousers;
        if (view) {
          const left = region.x+x*region.width/w, right = region.x+(x+1)*region.width/w;
          const top = region.y+y*region.height/h, bottom = region.y+(y+1)*region.height/h;
          const sum = [0,0,0];
          for (let row=Math.floor(top);row<Math.ceil(bottom);row++) for (let col=Math.floor(left);col<Math.ceil(right);col++) {
            const area = (Math.min(right,col+1)-Math.max(left,col))*(Math.min(bottom,row+1)-Math.max(top,row));
            const offset = (row*view.image.width+col)*4, alpha = view.image.data[offset+3]/255;
            for (let c=0;c<3;c++) sum[c] += area*(view.image.data[offset+c]*alpha+color[c]*(1-alpha));
          }
          color = sum.map(value=>Math.round(value/((right-left)*(bottom-top))));
        }
        pixels.set([...color,255],((v+y)*64+u+x)*4);
      }
    }
    for (const part of SkinEditor.parts) {
      const [fu,fv,fw,fh] = SkinEditor.faceRect(part,'front','base',model);
      const [bu,bv] = SkinEditor.faceRect(part,'back','base',model);
      for (const face of ['left','right','top','bottom']) {
        const [u,v,w,h] = SkinEditor.faceRect(part,face,'base',model);
        for (let y=0;y<h;y++) for (let x=0;x<w;x++) {
          const side = face === 'left' || face === 'right';
          const frontX = side ? face === 'right' ? 0 : fw-1 : x;
          const frontY = side ? y : face === 'top' ? 0 : fh-1;
          const frontOffset = ((fv+frontY)*64+fu+frontX)*4;
          const backOffset = ((bv+frontY)*64+bu+fw-1-frontX)*4;
          const towardBack = side ? face === 'left' ? x/(w-1) : 1-x/(w-1) : 1-y/(h-1);
          const offset = ((v+y)*64+u+x)*4;
          for (let c=0;c<3;c++) pixels[offset+c] = Math.round(pixels[frontOffset+c]*(1-towardBack)+pixels[backOffset+c]*towardBack);
          pixels[offset+3] = 255;
        }
      }
    }
    return new SkinEditor({pixels,model});
  }
  const api = {defaultRegions,sampleColor,createSkin,limits};
  root.ArkPhotoSkin = api;
  if (typeof module !== 'undefined') module.exports = api;
})(typeof globalThis !== 'undefined' ? globalThis : window);
