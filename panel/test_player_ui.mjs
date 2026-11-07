import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync, mkdirSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';
import { homedir } from 'node:os';

const cache=join(homedir(),'.npm','_npx');
const modulePath=process.env.PLAYWRIGHT_MODULE||(existsSync(cache)?readdirSync(cache):[]).map(n=>join(cache,n,'node_modules/playwright/index.mjs')).find(existsSync);
assert.ok(modulePath,'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');
const { chromium }=await import(pathToFileURL(modulePath));
let authenticated=false,skins=[],nextId=1,recovery=false,activatePolls=0,passwordConfigured=true,passwordResetAllowed=false;
const writes=[],headers=[],errors=[];
let delaySkinWrite=false,releaseSkinWrite=null;
const server=createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk;
  const body=raw?JSON.parse(raw):undefined;
  const send=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
  if(req.url.startsWith('/play/static/')){const file=req.url.slice('/play/static/'.length);if(file.includes('..'))return send({},404);res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.json')?'application/json':'text/css'});res.end(readFileSync(new URL('./static/'+file,import.meta.url)));return;}
  if(!req.url.startsWith('/play/api/')){res.writeHead(200,{'Content-Type':'text/html'});res.end(readFileSync(new URL('./templates/player.html',import.meta.url)));return;}
  const path=req.url.slice('/play/api/'.length);headers.push(req.headers);
  if(req.method!=='GET'){writes.push({path,method:req.method,body});if(!['login','recover'].includes(path)&&req.headers['x-csrf-token']!=='player-csrf')return send({error:'CSRF missing'},403);}
  if(path==='modules')return send({accounts:true,wardrobe:true,support:true});
  if(path==='session')return send({authenticated,csrf:'player-csrf',username:'Alice',language:'en'});
  if(path==='login'){if(body.password==='wrong')return send({error:'Incorrect player name or password.',code:'invalid_credentials'},401);authenticated=true;return send({ok:true,csrf:'player-csrf',username:'Alice'});}
  if(path==='recover'){authenticated=false;return send({ok:true});}
  if(!authenticated)return send({error:'Please sign in first.'},401);
  if(path==='logout'||path==='password'){authenticated=false;return send({ok:true});}
  if(path==='account')return send({password_configured:passwordConfigured,password_reset_allowed:passwordResetAllowed,recovery_enabled:recovery,recovery_codes_remaining:recovery?8:0});
  if(path==='recovery'){recovery=req.method!=='DELETE';return send(recovery?{codes:['AAAA-BBBB-CCCC','DDDD-EEEE-FFFF']}:{ok:true});}
  if(path==='me')return send({uuid:'test-owner',name:'Alice',online:true,stats:{play_time_ticks:144000,deaths:3,mob_kills:42,player_kills:1},observed_at:'2026-11-07T12:00:00Z',active_skin_id:skins.find(s=>s.selected)?.id});
  if(path==='skins'){
    if(req.method==='GET'){if(activatePolls&&++activatePolls>2){for(const s of skins)if(s.status==='preparing'){s.status='applied';s.ready=true;}}return send({skins,limit:20});}
    const skin={...body,id:String(nextId++),revision:1,ready:false,selected:false,status:'draft'};skins.push(skin);return send({skin},201);
  }
  const [,id,action]=path.split('/');const skin=skins.find(s=>s.id===id);if(!skin)return send({error:'Not found.'},404);
  if(body.revision!==skin.revision)return send({error:'The skin has changed in the meantime. Please reload.',code:'stale_revision'},409);
  if(action==='activate'){skins.forEach(s=>s.selected=false);skin.selected=true;skin.status='preparing';activatePolls=1;return send({skin},202);}
  if(action==='copy'){const copy={...skin,id:String(nextId++),name:body.name,revision:1,selected:false,status:'draft'};skins.push(copy);return send({skin:copy},201);}
  if(req.method==='DELETE'){if(skin.selected)return send({error:'Activate another skin before deleting this one.'},400);skins=skins.filter(s=>s!==skin);return send({ok:true});}
  if(delaySkinWrite&&req.method==='PUT')await new Promise(resolve=>releaseSkinWrite=resolve);
  Object.assign(skin,body,{revision:skin.revision+1});return send({skin});
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM?{executablePath:process.env.PLAYWRIGHT_CHROMIUM}:{})});
const page=await browser.newPage({locale:'en-US',viewport:{width:1380,height:1000}});
page.on('pageerror',e=>errors.push(e.message));page.on('dialog',async dialog=>dialog.type()==='prompt'?dialog.accept('Copied skin'):dialog.accept());
try{
  await page.goto(`http://127.0.0.1:${server.address().port}/play/`);
  await page.getByRole('heading',{name:'Player portal',exact:true}).waitFor();
  assert.equal(await page.locator('#player-login-form').isVisible(),true,'Existing accounts can sign in directly');
  await page.locator('#player-username').fill('Alice');await page.locator('#player-password').fill('wrong');await page.locator('#player-login-form button').click();
  await page.waitForFunction(()=>document.getElementById('pair-message').textContent.includes('Incorrect'));
  await page.locator('#player-password').fill('existing-password');await page.locator('#player-login-form button').click();
  await page.locator('#player-app').waitFor({state:'visible'});await page.waitForFunction(()=>document.getElementById('profile-stats').textContent.includes('42'));
  assert.equal(await page.locator('#account-current').evaluate(el=>el.required),true,'Configured accounts require their existing server password');
  assert.equal(await page.locator('#profile-stats dd').first().textContent(),'2 h');
  await page.locator('#skin-new').click();await page.locator('#skin-name').fill('Alice original');
  await page.locator('#skin-paint').focus();await page.keyboard.press('Space');
  await page.locator('#skin-save').click();await page.waitForFunction(()=>document.getElementById('skin-dirty').textContent==='Saved');
  assert.equal(skins.length,1);assert.equal(skins[0].model,'classic');assert.ok(skins[0].png.startsWith('iVBOR'));
  await page.locator('#skin-new').click();await page.locator('#skin-name').fill('Second draft');await page.locator('#skin-model').selectOption('slim');
  for(const part of ['head','body','right_arm','left_arm','right_leg','left_leg'])for(const layer of ['base','overlay'])for(const face of ['front','back','left','right','top','bottom']){await page.locator('#skin-part').selectOption(part);await page.locator('#skin-layer').selectOption(layer);await page.locator('#skin-face').selectOption(face);await page.locator('#skin-paint').focus();await page.keyboard.press('Enter');}
  await page.locator('#skin-save').click();await page.waitForFunction(()=>document.getElementById('skin-dirty').textContent==='Saved');assert.equal(skins[1].model,'slim');
  await page.locator('#skin-name').fill('Unsaved renamed skin');
  await page.locator('#skin-list button').first().click();await page.locator('#skin-list button').nth(1).click();assert.equal(await page.locator('#skin-name').inputValue(),'Unsaved renamed skin','Library changes retain each draft');
  await page.locator('#language').selectOption('de');assert.equal(await page.locator('#skin-name').inputValue(),'Unsaved renamed skin');assert.equal(await page.locator('#skin-model').inputValue(),'slim');
  await page.getByRole('heading',{name:'Profil & Statistik'}).waitFor();
  assert.ok((await page.locator('[data-wiki="Skin-Editor"]').first().getAttribute('href')).endsWith('/Skin-Editor-DE'));
  await page.locator('#language').selectOption('en');
  skins[1].revision=2;await page.locator('#skin-save').click();await page.waitForFunction(()=>document.getElementById('player-message').textContent.includes('changed'));
  assert.equal(await page.locator('#skin-name').inputValue(),'Unsaved renamed skin','A stale write preserves the local draft');
  await page.locator('#skin-reload').click();await page.waitForFunction(()=>document.getElementById('skin-name').value==='Second draft');
  const validImport=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=32;c.height=64;return c.toDataURL('image/png').split(',')[1];});
  await page.locator('#skin-import').setInputFiles({name:'invalid.png',mimeType:'image/png',buffer:Buffer.from(validImport,'base64')});await page.waitForFunction(()=>document.getElementById('skin-status').textContent.includes('64 × 64'));
  await page.locator('#skin-copy').click();await page.waitForFunction(()=>document.getElementById('skin-name').value==='Copied skin');assert.equal(skins.length,3);
  await page.locator('#skin-activate').click();await page.waitForFunction(()=>document.getElementById('skin-list').textContent.includes('Active in the game'));
  await page.locator('#skin-delete').click();await page.waitForFunction(()=>document.getElementById('player-message').textContent.includes('Activate another'));assert.equal(skins.length,3,'Active deletion rejected without removing its draft');
  await page.locator('#recovery-password').fill('existing-password');await page.locator('#recovery-enable').click();await page.locator('#recovery-results').waitFor({state:'visible'});
  assert.equal(await page.locator('#recovery-codes').textContent(),'AAAA-BBBB-CCCC\nDDDD-EEEE-FFFF');assert.equal(await page.evaluate(()=>Object.values(localStorage).join('').includes('AAAA')),false,'Recovery codes are not persisted');
  await page.locator('#recovery-close').click();assert.equal(await page.locator('#recovery-codes').textContent(),'');
  await page.locator('#recovery-password').fill('existing-password');await page.locator('#recovery-existing').fill('AAAA-BBBB-CCCC');await page.locator('#recovery-disable').click();await page.waitForFunction(()=>document.getElementById('account-status').textContent.includes('disabled'));
  await page.setViewportSize({width:390,height:844});await page.locator('#wardrobe').scrollIntoViewIfNeeded();
  const overflow=await page.evaluate(()=>[...document.querySelectorAll('body *')].filter(el=>el.getBoundingClientRect().right>innerWidth+1).map(el=>el.tagName+'#'+el.id+'.'+el.className).slice(0,15));
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Editor fits a mobile viewport: '+overflow.join(', '));
  mkdirSync(new URL('../output/playwright/',import.meta.url),{recursive:true});await page.screenshot({path:new URL('../output/playwright/player-portal-mobile.png',import.meta.url).pathname,fullPage:true});
  await page.locator('#account-current').fill('existing-password');await page.locator('#account-password').fill('new-server-password');await page.locator('#account-confirm').fill('new-server-password');await page.locator('#account-password-form button').click();await page.locator('#pair-view').waitFor({state:'visible'});
  assert.deepEqual(writes.findLast(w=>w.path==='password').body,{current_password:'existing-password',new_password:'new-server-password',confirmation:'new-server-password'});
  await page.getByText('Forgot your password?',{exact:true}).click();await page.locator('#recover-username').fill('Alice');await page.locator('#recover-code').fill('recovery-test');await page.locator('#recover-password').fill('recovered-password');await page.locator('#recover-confirm').fill('recovered-password');await page.locator('#player-recover-form button').click();await page.waitForFunction(()=>document.getElementById('pair-message').textContent.includes('reset'));assert.equal(authenticated,false);
  passwordConfigured=false;passwordResetAllowed=true;
  await page.getByText('Sign in with a sign-in code',{exact:true}).click();await page.locator('#pair-code').fill('fresh-game-proof');await page.locator('#pair-submit').click();
  await page.waitForFunction(()=>!document.getElementById('account-current').required,{},{timeout:3000});
  assert.match(await page.locator('#account-current-hint').textContent(),/first server password/);
  assert.equal(await page.locator('#account [data-wiki]').getAttribute('href'),'https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security');
  await page.locator('#language').selectOption('de');
  await page.waitForFunction(()=>document.getElementById('account-current-hint').textContent.includes('erstes Serverpasswort'));
  assert.equal(await page.locator('#account [data-wiki]').getAttribute('href'),'https://github.com/Bladestar2105/Minecraft-Ark/wiki/Account-Security-DE');
  await page.locator('#account-password').fill('first-server-password');await page.locator('#account-confirm').fill('first-server-password');await page.locator('#account-password-form button').click();await page.locator('#pair-view').waitFor({state:'visible'});
  assert.deepEqual(writes.findLast(w=>w.path==='password').body,{current_password:'',new_password:'first-server-password',confirmation:'first-server-password'},'Fresh in-game proof permits initial password setup without a previous password');
  assert.equal(authenticated,false);
  passwordConfigured=true;
  await page.locator('#pair-code').fill('fresh-game-proof');await page.locator('#pair-submit').click();
  await page.waitForFunction(()=>!document.getElementById('account-current').required&&document.getElementById('account-current-hint').textContent.includes('Bestätigung im Spiel'));
  assert.equal(await page.locator('#recovery-password').evaluate(el=>el.required),true,'Fresh proof does not bypass recovery-code setup password validation');
  await page.locator('#account-password').fill('reset-server-password');await page.locator('#account-confirm').fill('reset-server-password');await page.locator('#account-password-form button').click();await page.locator('#pair-view').waitFor({state:'visible'});
  assert.equal(writes.findLast(w=>w.path==='password').body.current_password,'','Fresh in-game proof also permits reset of an existing unknown server password');
  passwordResetAllowed=false;
  await page.locator('#player-username').fill('Alice');await page.locator('#player-password').fill('reset-server-password');await page.locator('#player-login-form button').click();
  await page.waitForFunction(()=>document.getElementById('account-current').required&&document.getElementById('account-current-hint').textContent.includes('aktuelles Serverpasswort'));
  // Independent body/clothing textures from front and rear photos stay local.
  await page.locator('#language').selectOption('en');
  await page.locator('#skin-new').click();await page.locator('#skin-name').fill('Keep this unsaved draft');
  const originalDraftCount=await page.locator('#skin-list button').count();
  assert.equal(await page.locator('#photo-front-file').count(),1,'Separate front and rear photo sources are required');
  const photo=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=256;c.height=128;const ctx=c.getContext('2d');ctx.fillStyle='#ff0000';ctx.fillRect(0,0,128,128);ctx.fillStyle='#00ff00';ctx.fillRect(128,0,128,128);return c.toDataURL('image/png').split(',')[1];});
  const rearPhoto=await page.evaluate(()=>{const c=document.createElement('canvas');c.width=256;c.height=128;const ctx=c.getContext('2d');ctx.fillStyle='#0000ff';ctx.fillRect(0,0,128,128);ctx.fillStyle='#ffff00';ctx.fillRect(128,0,128,128);return c.toDataURL('image/png').split(',')[1];});
  const writesBeforePhoto=writes.length;
  await page.locator('#skin-photo-open').click();
  await page.locator('#photo-front-file').setInputFiles({name:'PRIVATE-front-photo.png',mimeType:'image/png',buffer:Buffer.from(photo,'base64')});
  await page.waitForFunction(()=>!document.getElementById('photo-apply').disabled);
  await page.locator('#photo-back-file').setInputFiles({name:'PRIVATE-back-photo.png',mimeType:'image/png',buffer:Buffer.from(rearPhoto,'base64')});
  await page.waitForFunction(()=>!document.getElementById('photo-fields').disabled&&!document.getElementById('photo-apply').disabled);
  async function region(view,part,x,y,width,height) {
    await page.locator('#photo-view').selectOption(view);await page.locator('#photo-part').selectOption(part);
    await page.evaluate(async rect=>{const set=(key,value)=>{const input=document.getElementById('photo-'+key);input.value=value;input.dispatchEvent(new Event('input',{bubbles:true}));};set('x',0);set('y',0);await new Promise(requestAnimationFrame);set('width',rect.width);set('height',rect.height);await new Promise(requestAnimationFrame);set('x',rect.x);set('y',rect.y);await new Promise(requestAnimationFrame);},{x,y,width,height});
  }
  assert.equal(await page.locator('#photo-front-state').textContent(),'Image loaded.');assert.equal(await page.locator('#photo-back-state').textContent(),'Image loaded.');
  await region('front','head',128,0,128,128);await region('front','body',0,0,128,128);
  await region('back','head',0,0,128,128);await region('back','body',128,0,128,128);
  await page.locator('#photo-model').selectOption('slim');
  await page.locator('#photo-view').selectOption('front');await page.locator('#photo-mode').selectOption('hair');
  const photoBox=await page.locator('#photo-canvas').boundingBox();
  await page.locator('#photo-canvas').click({position:{x:photoBox.width*.2,y:photoBox.height*.5}});
  assert.equal(await page.locator('#photo-hair').inputValue(),'#ff0000','Pipette samples the selected source photo');
  await page.waitForFunction(()=>{const pixel=(id,x,y)=>[...document.getElementById(id).getContext('2d').getImageData(x,y,1,1).data];return pixel('photo-front',64,32).join()==='0,255,0,255'&&pixel('photo-back',64,32).join()==='0,0,255,255'&&pixel('photo-front',64,100).join()==='255,0,0,255'&&pixel('photo-back',64,100).join()==='255,255,0,255';});
  await page.locator('#language').selectOption('de');
  assert.equal(await page.locator('#photo-model').inputValue(),'slim');assert.equal(await page.locator('#photo-part').inputValue(),'body');
  assert.ok((await page.locator('#skin-photo [data-wiki]').getAttribute('href')).endsWith('/Skin-Editor-DE'));
  assert.equal(writes.length,writesBeforePhoto,'Both photo uploads, body crop alignment and preview stay local');
  assert.equal(await page.evaluate(p=>[...Object.values(localStorage),...Object.values(sessionStorage)].join('').includes(p)),false,photo);
  await page.setViewportSize({width:1380,height:1000});await page.locator('#skin-photo').screenshot({path:new URL('../output/playwright/photo-skin-desktop.png',import.meta.url).pathname});
  await page.setViewportSize({width:390,height:844});
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Full-body photo controls fit a mobile viewport');
  await page.locator('#skin-photo').screenshot({path:new URL('../output/playwright/photo-skin-mobile.png',import.meta.url).pathname});
  await page.locator('#photo-apply').click();
  assert.equal(await page.locator('#skin-list button').count(),originalDraftCount+1);
  assert.equal(await page.locator('#skin-model').inputValue(),'slim');
  for(const id of ['photo-front-file','photo-back-file'])assert.equal(await page.locator('#'+id).inputValue(),'','Original source file reference cleared');
  const newDraftName=await page.locator('#skin-name').inputValue();
  await page.getByRole('button',{name:'Keep this unsaved draft',exact:false}).click();
  assert.equal(await page.locator('#skin-name').inputValue(),'Keep this unsaved draft','Full body conversion preserves previous unsaved work');
  await page.getByRole('button',{name:newDraftName,exact:false}).click();
  await page.locator('#skin-name').fill('Photo kit');await page.locator('#skin-save').click();
  await page.waitForFunction(()=>document.getElementById('skin-dirty').textContent==='Gespeichert');
  const savedPhoto=skins.find(s=>s.name==='Photo kit');assert.equal(savedPhoto.model,'slim');
  assert.notEqual(savedPhoto.png,photo);assert.notEqual(savedPhoto.png,rearPhoto);assert.deepEqual(Object.keys(writes.findLast(w=>w.path==='skins').body).sort(),['model','name','png']);
  const pixels=await page.evaluate(async png=>{const skin=await SkinEditor.fromPNG(png,'slim');return [[8,8],[24,8],[20,20],[32,20]].map(([x,y])=>[...skin.pixels.slice((y*64+x)*4,(y*64+x)*4+4)]);},savedPhoto.png);
  assert.deepEqual(pixels,[[0,255,0,255],[0,0,255,255],[255,0,0,255],[255,255,0,255]],'Independent face and jersey front/rear textures survive Save');
  await page.locator('#skin-paint').focus();await page.keyboard.press('Space');await page.locator('#skin-undo').click();
  assert.equal(await page.locator('#skin-dirty').textContent(),'Gespeichert','Generated skins use normal undo history');
  await page.locator('#skin-photo-open').click();
  await page.locator('#photo-front-file').setInputFiles({name:'broken.png',mimeType:'image/png',buffer:Buffer.from('invalid image')});
  await page.waitForFunction(()=>document.getElementById('photo-status').textContent.length>0&&!document.getElementById('photo-status').textContent.includes('…'));
  assert.equal(await page.locator('#photo-apply').isDisabled(),true);
  await page.locator('#photo-cancel').click();assert.equal(await page.locator('#photo-panel').isVisible(),false);
  // Front and rear decodes are independent and may finish in reverse order.
  await page.evaluate(()=>{const decode=HTMLImageElement.prototype.decode;window.originalPhotoDecode=decode;window.photoReleases=[];HTMLImageElement.prototype.decode=async function(){await decode.call(this);await new Promise(resolve=>window.photoReleases.push(resolve));};});
  await page.locator('#skin-photo-open').click();
  await page.locator('#photo-front-file').setInputFiles({name:'parallel-front.png',mimeType:'image/png',buffer:Buffer.from(photo,'base64')});
  await page.waitForFunction(()=>window.photoReleases.length===1);
  await page.locator('#photo-back-file').setInputFiles({name:'parallel-back.png',mimeType:'image/png',buffer:Buffer.from(rearPhoto,'base64')});
  await page.waitForFunction(()=>window.photoReleases.length===2);
  await page.evaluate(()=>window.photoReleases[1]());
  await page.waitForFunction(()=>document.getElementById('photo-status').textContent.includes('geladen'));
  assert.equal(await page.locator('#photo-apply').isDisabled(),true,'Wait for all selected photos to finish');
  await page.evaluate(()=>window.photoReleases[0]());await page.waitForFunction(()=>!document.getElementById('photo-apply').disabled);
  const heads=await page.evaluate(()=>['photo-front','photo-back'].map(id=>[...document.getElementById(id).getContext('2d').getImageData(36,32,1,1).data]));
  assert.deepEqual(heads,[[255,0,0,255],[0,0,255,255]],'Out-of-order image completion retains both independent sources');
  await page.locator('#photo-cancel').click();await page.evaluate(()=>{HTMLImageElement.prototype.decode=window.originalPhotoDecode;});
  // A front photo decoded while an unrelated Save is busy must be ready without another edit.
  await page.evaluate(()=>{const decode=HTMLImageElement.prototype.decode;window.originalPhotoDecode=decode;HTMLImageElement.prototype.decode=async function(){await decode.call(this);await new Promise(resolve=>window.releasePhoto=resolve);};});
  await page.locator('#skin-photo-open').click();
  await page.locator('#photo-front-file').setInputFiles({name:'while-saving.png',mimeType:'image/png',buffer:Buffer.from(photo,'base64')});
  await page.waitForFunction(()=>typeof window.releasePhoto==='function');
  delaySkinWrite=true;await page.locator('#skin-save').click();
  await page.waitForFunction(()=>document.getElementById('photo-front-file').disabled);
  await page.evaluate(()=>window.releasePhoto());
  await page.waitForFunction(()=>document.getElementById('photo-status').textContent.includes('geladen'));
  assert.equal(typeof releaseSkinWrite,'function');releaseSkinWrite();delaySkinWrite=false;
  await page.waitForFunction(()=>!document.getElementById('photo-front-file').disabled);
  assert.equal(await page.locator('#photo-apply').isEnabled(),true,'Photo decoded during Save is immediately ready after Save');
  await page.locator('#photo-cancel').click();
  await page.evaluate(()=>{HTMLImageElement.prototype.decode=window.originalPhotoDecode;delete window.releasePhoto;});
  // Finish a valid decode after logout: neither source may reopen the photo dialog.
  await page.evaluate(()=>{const decode=HTMLImageElement.prototype.decode;HTMLImageElement.prototype.decode=async function(){await decode.call(this);await new Promise(resolve=>window.releasePhoto=resolve);};});
  await page.locator('#skin-photo-open').click();
  await page.locator('#photo-front-file').setInputFiles({name:'late.png',mimeType:'image/png',buffer:Buffer.from(photo,'base64')});
  await page.waitForFunction(()=>typeof window.releasePhoto==='function');
  await page.locator('#player-logout').click();await page.locator('#pair-view').waitFor({state:'visible'});
  await page.evaluate(()=>window.releasePhoto());
  assert.equal(await page.locator('#photo-panel').isVisible(),false);assert.equal(await page.locator('#photo-front-file').inputValue(),'');
  assert.equal(writes.some(w=>JSON.stringify(w.body).includes('PRIVATE-')||JSON.stringify(w.body).includes(photo)||JSON.stringify(w.body).includes(rearPhoto)),false,'Original front/rear photos and filenames never reach an API');
  assert.ok(headers.every(h=>['de','en'].includes(h['accept-language'])),'Every API call sends the current locale');
  assert.deepEqual(errors,[]);
  console.log('PASS /play/ direct login, own profile, all72 UV faces, draft retention, DE/EN, revisions, import rejection, copy/activate/active-delete, recovery/password/recover, initial password/game-proof reset, configured-password guards mobile layout and local photo draft privacy/crop/color/front-back previews/cancel/late/busy decoding');
}finally{await page.close();await browser.close();await new Promise(resolve=>server.close(resolve));}
