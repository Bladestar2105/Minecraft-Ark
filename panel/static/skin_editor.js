"use strict";
// Browser: SkinEditor / SkinEditor.faceRect. Node: { SkinEditor, faceRect }.
((root) => {
  const parts = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg'];
  const faces = ['front', 'back', 'left', 'right', 'top', 'bottom'];
  function faceRect(part, face, layer = 'base', model = 'classic') {
    if (!parts.includes(part) || !faces.includes(face) || !['base', 'overlay'].includes(layer) || !['classic', 'slim'].includes(model)) throw new Error('Invalid skin face');
    const arm = part.endsWith('arm'), width = part === 'head' || part === 'body' ? 8 : arm && model === 'slim' ? 3 : 4;
    const depth = part === 'head' ? 8 : 4, height = part === 'head' ? 8 : 12;
    const offsets = { head: [[0,0], [32,0]], body: [[16,16], [16,32]], right_arm: [[40,16], [40,32]], left_arm: [[32,48], [48,48]], right_leg: [[0,16], [0,32]], left_leg: [[16,48], [0,48]] };
    const [u, v] = offsets[part][layer === 'overlay' ? 1 : 0];
    const rects = { top: [u+depth,v,width,depth], bottom: [u+depth+width,v,width,depth], right: [u,v+depth,depth,height], front: [u+depth,v+depth,width,height], left: [u+depth+width,v+depth,depth,height], back: [u+2*depth+width,v+depth,width,height] };
    return rects[face];
  }
  class SkinEditor {
    static parts = parts;
    static faces = faces;
    static faceRect = faceRect;
    constructor({ pixels, model = 'classic' } = {}) {
      if (!['classic', 'slim'].includes(model)) throw new Error('Invalid skin model');
      if (pixels && pixels.length !== 64*64*4) throw new Error('A skin must be 64 × 64 pixels');
      this.model = model;
      this.pixels = pixels ? new Uint8ClampedArray(pixels) : new Uint8ClampedArray(64*64*4);
      this.history = []; this.future = []; this.stroke = null;
      if (!pixels) for (const part of parts) for (const face of faces) {
        const [x,y,w,h] = faceRect(part, face, 'base', model);
        const color = part === 'head' || part.endsWith('arm') ? [189,143,111,255] : part === 'body' ? [64,111,112,255] : [52,65,81,255];
        for (let j=y;j<y+h;j++) for (let i=x;i<x+w;i++) this.pixels.set(color,(j*64+i)*4);
      }
      this.markSaved();
    }
    snapshot() { return { model: this.model, pixels: this.pixels.slice() }; }
    same(a, b) { return a.model === b.model && a.pixels.every((n,i) => n === b.pixels[i]); }
    get dirty() { return !this.same(this.snapshot(), this.saved); }
    markSaved() { this.saved = this.snapshot(); }
    beginStroke() { if (!this.stroke) this.stroke = this.snapshot(); }
    endStroke() {
      if (this.stroke && !this.same(this.stroke, this.snapshot())) { this.history.push(this.stroke); if (this.history.length > 50) this.history.shift(); this.future = []; }
      this.stroke = null;
    }
    paint(part, face, layer, x, y, rgba) {
      const [u,v,w,h] = faceRect(part,face,layer,this.model);
      if (!Number.isInteger(x) || !Number.isInteger(y) || x<0 || x>=w || y<0 || y>=h || rgba.length !== 4) throw new Error('Invalid pixel');
      const single = !this.stroke; if (single) this.beginStroke();
      this.pixels.set([rgba[0],rgba[1],rgba[2],layer === 'base' ? 255 : rgba[3]],((v+y)*64+u+x)*4);
      if (single) this.endStroke();
    }
    setModel(model) {
      if (!['classic', 'slim'].includes(model)) throw new Error('Invalid skin model');
      this.beginStroke(); this.model=model; this.endStroke();
    }
    restore(snapshot) { this.model=snapshot.model; this.pixels=snapshot.pixels.slice(); }
    undo() { this.endStroke(); if (!this.history.length) return false; this.future.push(this.snapshot()); this.restore(this.history.pop()); return true; }
    redo() { if (!this.future.length) return false; this.history.push(this.snapshot()); this.restore(this.future.pop()); return true; }
    toCanvas() {
      const canvas=document.createElement('canvas'); canvas.width=canvas.height=64;
      canvas.getContext('2d').putImageData(new ImageData(this.pixels.slice(),64,64),0,0); return canvas;
    }
    toPNG() { return this.toCanvas().toDataURL('image/png').split(',')[1]; }
    static async fromPNG(png, model = 'classic') {
      if (typeof png !== 'string' || png.length > 1400000 || !/^[A-Za-z0-9+/]+={0,2}$/.test(png)) throw new Error('Bitte eine gültige PNG-Datei mit 64 × 64 Pixeln wählen.');
      const bytes=atob(png); if (bytes.slice(0,8) !== '\x89PNG\r\n\x1a\n') throw new Error('Bitte eine gültige PNG-Datei mit 64 × 64 Pixeln wählen.');
      const image=new Image(); image.src='data:image/png;base64,'+png;
      try { await image.decode(); } catch { throw new Error('Bitte eine gültige PNG-Datei mit 64 × 64 Pixeln wählen.'); }
      if (image.width!==64 || image.height!==64) throw new Error('Bitte eine gültige PNG-Datei mit 64 × 64 Pixeln wählen.');
      const canvas=document.createElement('canvas'); canvas.width=canvas.height=64; const context=canvas.getContext('2d'); context.drawImage(image,0,0);
      return new SkinEditor({pixels:context.getImageData(0,0,64,64).data, model});
    }
  }
  root.SkinEditor = SkinEditor;
  if (typeof module !== 'undefined') module.exports = { SkinEditor, faceRect };
})(typeof globalThis !== 'undefined' ? globalThis : window);
