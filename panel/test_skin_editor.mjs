import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
const require = createRequire(import.meta.url);
const { SkinEditor, faceRect } = require('./static/skin_editor.js');

// Published Minecraft UV examples, rather than a duplicate of the UV algorithm.
assert.deepEqual(faceRect('head', 'front', 'base', 'classic'), [8, 8, 8, 8]);
assert.deepEqual(faceRect('head', 'bottom', 'overlay', 'slim'), [48, 0, 8, 8]);
assert.deepEqual(faceRect('right_arm', 'back', 'base', 'classic'), [52, 20, 4, 12]);
assert.deepEqual(faceRect('right_arm', 'back', 'base', 'slim'), [51, 20, 3, 12]);
assert.deepEqual(faceRect('left_arm', 'left', 'overlay', 'slim'), [55, 52, 4, 12]);
assert.deepEqual(faceRect('left_leg', 'top', 'base', 'classic'), [20, 48, 4, 4]);
for (const model of ['classic', 'slim']) {
  const occupied = new Set();
  for (const part of SkinEditor.parts) for (const layer of ['base', 'overlay']) for (const face of SkinEditor.faces) {
    const [x, y, w, h] = faceRect(part, face, layer, model);
    for (let j = y; j < y+h; j++) for (let i = x; i < x+w; i++) {
      assert.ok(i >= 0 && i < 64 && j >= 0 && j < 64);
      assert.ok(!occupied.has(`${i}:${j}`), `${model} faces must not overlap`);
      occupied.add(`${i}:${j}`);
    }
  }
}
const skin = new SkinEditor(); skin.markSaved();
const original = skin.pixels.slice();
skin.beginStroke(); skin.paint('head', 'front', 'base', 0, 0, [255, 0, 0, 0]); skin.paint('head', 'front', 'base', 1, 0, [0, 255, 0, 255]); skin.endStroke();
assert.deepEqual([...skin.pixels.slice((8*64+8)*4, (8*64+8)*4+4)], [255, 0, 0, 255], 'Base must remain opaque');
assert.equal(skin.dirty, true); assert.equal(skin.undo(), true); assert.deepEqual(skin.pixels, original); assert.equal(skin.dirty, false);
assert.equal(skin.redo(), true); assert.equal(skin.dirty, true);
skin.paint('head', 'front', 'overlay', 0, 0, [100, 120, 140, 0]);
assert.equal(skin.pixels[(8*64+40)*4+3], 0, 'Overlay can be erased');
assert.throws(() => skin.paint('head', 'front', 'base', 8, 0, [0,0,0,255]), /pixel/i);
assert.throws(() => faceRect('unknown', 'front', 'base', 'classic'));
assert.throws(() => new SkinEditor({ pixels: new Uint8ClampedArray(32*64*4) }), /64/);
const before = skin.pixels.slice(); skin.setModel('slim'); assert.deepEqual(skin.pixels, before, 'Model changes preserve imported texture');
skin.markSaved(); skin.setModel('classic'); assert.equal(skin.dirty, true); skin.undo(); assert.equal(skin.model, 'slim'); assert.equal(skin.dirty, false);
const de = JSON.parse(readFileSync(new URL('./static/locales/de.json', import.meta.url)));
const en = JSON.parse(readFileSync(new URL('./static/locales/en.json', import.meta.url)));
assert.deepEqual(Object.keys(de).sort(), Object.keys(en).sort(), 'Entire dictionaries have translation parity');
for (const key of Object.keys(de)) {
  assert.ok(typeof en[key] === 'string' && en[key].length, `Missing English translation: ${key}`);
  assert.deepEqual([...de[key].matchAll(/\{\w+\}/g)].map(x=>x[0]).sort(), [...en[key].matchAll(/\{\w+\}/g)].map(x=>x[0]).sort(), key);
}
console.log('PASS all skin faces, Classic/Slim UV, isolated strokes, opacity, undo/redo, import shape, model preservation and locale parity');
