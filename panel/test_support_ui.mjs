import assert from 'node:assert/strict';
import {readFileSync,readdirSync,existsSync,mkdirSync} from 'node:fs';
import {createServer} from 'node:http';
import {pathToFileURL} from 'node:url';
import {join} from 'node:path';
import {homedir} from 'node:os';
const cache=join(homedir(),'.npm','_npx');const modulePath=process.env.PLAYWRIGHT_MODULE||(existsSync(cache)?readdirSync(cache):[]).map(n=>join(cache,n,'node_modules/playwright/index.mjs')).find(existsSync);
assert.ok(modulePath,'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');const {chromium}=await import(pathToFileURL(modulePath));
let playerAuth=true,adminAuth=false;
let modules={accounts:true,wardrobe:true,support:true},modulesFailure=false;
const stamp='2026-11-07T14:00:00Z',ownId='11111111-1111-4111-8111-111111111111',otherId='22222222-2222-4222-8222-222222222222';
let tickets=[{id:ownId,player_uuid:'alice-id',player_name:'Alice',priority:'high',status:'open',revision:1,created_at:stamp,updated_at:stamp,messages:[{author:'player',message:'<img src=x onerror="window.supportXSS=true"> Help with my skin',created_at:stamp}]},{id:otherId,player_uuid:'bob-id',player_name:'Bob',priority:'low',status:'open',revision:1,created_at:stamp,updated_at:stamp,messages:[{author:'player',message:'Private Bob message',created_at:stamp}]}];
const writes=[],requests=[],errors=[];
const server=createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):undefined;
  const send=(value,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
  const isPlayer=req.url.startsWith('/play/');const path=isPlayer?req.url.slice(5):req.url;
  if(path.startsWith('/static/')){const file=path.slice(8);res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.json')?'application/json':'text/css'});res.end(readFileSync(new URL('./static/'+file,import.meta.url)));return;}
  if(!path.startsWith('/api/')){res.writeHead(200,{'Content-Type':'text/html'});res.end(readFileSync(new URL('./templates/'+(isPlayer?'player':'index')+'.html',import.meta.url),'utf8').replace(/{{ url_for\('static', filename='([^']+)'\) }}/g,'/static/$1'));return;}
  requests.push({isPlayer,path});
  if(path==='/api/session')return send({authenticated:isPlayer?playerAuth:adminAuth,csrf:isPlayer?'player-csrf':'admin-csrf',username:isPlayer?'Alice':'Operator',auth_type:isPlayer?undefined:'operator'});
  if(path==='/api/operator/login'){assert.equal(isPlayer,false);if(body.password==='wrong')return send({error:'Operator sign-in denied.'},401);adminAuth=true;writes.push({isPlayer,path,body});return send({ok:true,csrf:'admin-csrf',username:'Operator',auth_type:'operator'});}
  if(!(isPlayer?playerAuth:adminAuth))return send({error:'Please sign in first.'},401);
  if(path==='/api/modules')return modulesFailure?send({error:'Module service unavailable.'},503):send(modules);
  if(req.method!=='GET'){assert.equal(req.headers['x-csrf-token'],isPlayer?'player-csrf':'admin-csrf');writes.push({isPlayer,path,body});}
  if(path==='/api/logout'){if(isPlayer)playerAuth=false;else adminAuth=false;return send({ok:true});}
  if(path==='/api/status')return send({services:[],players:'No players'});
  if(path==='/api/me')return send({uuid:'alice-id',name:'Alice',online:true,stats:{},observed_at:stamp});
  if(path==='/api/skins')return send({skins:[],limit:20});
  if(path==='/api/account')return send({password_configured:true,recovery_enabled:false,recovery_codes_remaining:0});
  if(path==='/api/players')return send({players:[],whitelist:[],banned:[]});
  if(path==='/api/player-accounts')return send({accounts:[{uuid:'alice-id',name:'Alice'},{uuid:'bob-id',name:'Bob'}]});
  if(path==='/api/worlds')return send({worlds:[]});
  if(path==='/api/player-accounts/password'){assert.equal(isPlayer,false);assert.ok(['Alice','Bob'].includes(body.username));playerAuth=false;return send({ok:true});}
  if(path==='/api/support'){
    if(req.method==='GET')return send({tickets:tickets.filter(t=>!isPlayer||t.player_uuid==='alice-id').map(({messages,...ticket})=>({...ticket,message_count:messages.length}))});
    const ticket={id:'33333333-3333-4333-8333-333333333333',player_uuid:'alice-id',player_name:'Alice',priority:body.priority,status:'open',revision:1,created_at:stamp,updated_at:stamp,messages:[{author:'player',message:body.message,created_at:stamp}]};tickets.push(ticket);return send({ticket},201);
  }
  if(path.startsWith('/api/support/')){
    const [, , ,id,action]=path.split('/');const ticket=tickets.find(t=>t.id===id);if(!ticket||isPlayer&&ticket.player_uuid!=='alice-id')return send({error:'Not found.'},404);
    if(req.method==='GET')return send({ticket});
    if(body.revision!==ticket.revision)return send({error:'The support request has changed. Refresh before sending again.',code:'stale_revision'},409);
    if(action==='status'){assert.equal(isPlayer,false);ticket.status=body.status;ticket.revision++;return send({ticket});}
    if(ticket.status==='closed')return send({error:'This support request is closed.'},409);
    ticket.messages.push({author:isPlayer?'player':'admin',message:body.message,created_at:stamp});ticket.revision++;return send({ticket});
  }
  return send({error:'Unknown fixture route '+path},404);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM?{executablePath:process.env.PLAYWRIGHT_CHROMIUM}:{})});
const player=await browser.newPage({locale:'en-US'}),admin=await browser.newPage({locale:'en-US'});for(const page of [player,admin]){page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());}
await player.clock.install();await admin.clock.install();
const origin=`http://127.0.0.1:${server.address().port}`;
try{
  await admin.goto(origin+'/#support');await admin.locator('#login-view').waitFor({state:'visible'});
  assert.equal(await admin.locator('#app-view').isVisible(),false,'An existing player session never grants admin access');
  await admin.getByText('Sign in as a server operator',{exact:true}).click();await admin.locator('#operator-username').fill('Operator');await admin.locator('#operator-password').fill('wrong');await admin.locator('#operator-login-submit').click();await admin.waitForFunction(()=>document.getElementById('operator-login-message').textContent.includes('denied'));
  await admin.locator('#operator-password').fill('operator-server-password');await admin.locator('#operator-login-submit').click();await admin.locator('#app-view').waitFor({state:'visible'});
  assert.equal(await admin.locator('#account-button').isVisible(),false);assert.equal(await admin.locator('#operator-account-link').getAttribute('href'),'/play/#account');
  await player.goto(origin+'/play/');await player.locator('#player-app').waitFor({state:'visible'});await player.getByRole('link',{name:'Support',exact:true}).click();
  await player.locator('[data-support="list"] button').first().waitFor();
  assert.equal(await player.locator('[data-support="list"] button').count(),1,'Player sees only own tickets');
  assert.doesNotMatch(await player.locator('[data-support="list"]').textContent(),/Bob/);
  await player.locator('[data-support="list"] button').first().click();await player.getByText('<img src=x onerror="window.supportXSS=true"> Help with my skin',{exact:true}).waitFor();
  assert.equal(await player.locator('[data-support="messages"] img').count(),0);assert.equal(await player.evaluate(()=>window.supportXSS),undefined);
  await player.locator('[data-support="reply-message"]').fill('My unsent draft');
  tickets[0].revision++;
  await player.locator('[data-support="reply-submit"]').click();await player.waitForFunction(()=>document.querySelector('[data-support="message"]').textContent.includes('changed'));
  assert.equal(await player.locator('[data-support="reply-message"]').inputValue(),'My unsent draft','409 preserves the reply');
  await player.locator('[data-support="refresh"]').click();await player.locator('[data-support="reply-submit"]').click();await player.waitForFunction(()=>document.querySelector('[data-support="reply-message"]').value==='');
  assert.deepEqual(writes.findLast(w=>w.isPlayer&&w.path.endsWith('/reply')).body,{revision:2,message:'My unsent draft'});
  await player.locator('[data-support="create-message"]').fill('New multiline\nrequest');await player.locator('[data-support="priority"]').selectOption('low');await player.locator('[data-support="create-submit"]').click();await player.waitForFunction(()=>document.querySelectorAll('[data-support="list"] button').length===2);
  assert.deepEqual(writes.findLast(w=>w.isPlayer&&w.path==='/api/support').body,{priority:'low',message:'New multiline\nrequest'});
  assert.ok(requests.filter(r=>r.isPlayer).every(r=>!r.path.includes('/admin/')&&r.path!=='/api/player-accounts/password'));
  await admin.locator('[data-support="refresh"]').click();await admin.waitForFunction(()=>document.querySelectorAll('[data-support="list"] button').length===3);assert.equal(await admin.locator('[data-support="list"] button').count(),3,'Admin sees all tickets');
  await admin.locator('[data-support="list"] button').filter({hasText:'Alice'}).first().click();await admin.locator('[data-support="reply-message"]').fill('Administrator reply');await admin.locator('[data-support="reply-submit"]').click();await admin.waitForFunction(()=>document.querySelector('[data-support="messages"]').textContent.includes('Administrator reply'));
  await admin.locator('[data-support="status"]').click();await admin.waitForFunction(()=>document.querySelector('[data-support="reply-message"]').matches(':disabled'));
  await player.locator('[data-support="list"] button').first().click();await player.waitForFunction(()=>document.querySelector('[data-support="reply-message"]').matches(':disabled'));
  assert.equal(await player.locator('[data-support="status"]').count(),0,'Player cannot change ticket status');
  await admin.locator('[data-support="status"]').click();await admin.waitForFunction(()=>!document.querySelector('[data-support="reply-message"]').matches(':disabled'));
  await player.locator('[data-support="refresh"]').click();await player.locator('[data-support="reply-message"]').fill('Language draft');await player.locator('#language').selectOption('de');assert.equal(await player.locator('[data-support="reply-message"]').inputValue(),'Language draft');assert.ok((await player.locator('#player-support [data-wiki]').getAttribute('href')).endsWith('/Support-DE'));
  await player.setViewportSize({width:390,height:844});await player.locator('#player-support').scrollIntoViewIfNeeded();assert.equal(await player.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  mkdirSync(new URL('../output/playwright/',import.meta.url),{recursive:true});await player.screenshot({path:new URL('../output/playwright/support-player-mobile.png',import.meta.url).pathname});
  modules={accounts:true,wardrobe:false,support:false};await player.locator('#profile-refresh').click();
  await player.waitForFunction(()=>document.getElementById('player-support').hidden&&document.getElementById('wardrobe').hidden,{},{timeout:3000});
  assert.equal(await player.locator('#account').isVisible(),true,'Core account remains available without optional plugins');
  const beforeDisabledPoll=requests.length;await player.clock.runFor(15050);assert.equal(requests.slice(beforeDisabledPoll).filter(r=>r.isPlayer&&(/^\/api\/(support|skins)/.test(r.path))).length,0,'Disabled optional modules are not polled');
  modulesFailure=true;await player.locator('#profile-refresh').click();await player.waitForFunction(()=>document.getElementById('player-message').textContent.includes('Module service unavailable'));assert.equal(await player.locator('#account').isVisible(),true);
  modulesFailure=false;modules={accounts:true,wardrobe:true,support:true};await player.locator('#profile-refresh').click();await player.locator('#player-support').waitFor({state:'visible'});
  await admin.locator('[data-view="players"]').click();await admin.locator('#reset-player-names option').first().waitFor({state:'attached'});assert.equal(await admin.locator('#reset-player-names option').count(),2,'Offline known accounts are offered too');
  await admin.locator('#reset-player-name').fill('Alice');await admin.locator('#reset-player-password').fill('support-reset-password');await admin.locator('#reset-player-confirm').fill('support-reset-password');await admin.locator('#reset-player-submit').click();await admin.waitForFunction(()=>document.getElementById('reset-player-message').textContent.includes('sessions'));
  assert.deepEqual(writes.findLast(w=>w.path==='/api/player-accounts/password').body,{username:'Alice',new_password:'support-reset-password',confirmation:'support-reset-password'});
  assert.equal(await admin.locator('#reset-player-password').inputValue(),'');assert.equal(adminAuth,true);
  await player.locator('[data-support="refresh"]').click();await player.locator('#pair-view').waitFor({state:'visible'});assert.equal(await player.locator('[data-support="list"]').textContent(),'');assert.equal(await player.locator('[data-support="reply-message"]').inputValue(),'','Session expiry clears private tickets and reply drafts');
  await admin.locator('#logout-button').click();await admin.locator('#login-view').waitFor({state:'visible'});assert.equal(await admin.locator('[data-support="list"]').textContent(),'');
  if(!await admin.locator('.operator-login').evaluate(el=>el.open))await admin.getByText('Sign in as a server operator',{exact:true}).click();await admin.getByText('Use an in-game operator code',{exact:true}).click();await admin.locator('#operator-code').fill('operator-one-time-code');await admin.locator('#operator-code-submit').click();await admin.locator('#app-view').waitFor({state:'visible'});
  assert.deepEqual(writes.findLast(w=>w.path==='/api/operator/login').body,{code:'operator-one-time-code'});
  await admin.getByRole('link',{name:'Overview',exact:true}).click();modules={accounts:true,wardrobe:false,support:false};await admin.locator('#refresh-status').click();
  await admin.waitForFunction(()=>document.querySelector('[data-view="support"]').hidden&&document.getElementById('signing-open').hidden,{},{timeout:3000});
  assert.equal(await admin.locator('[data-view="players"]').isVisible(),true,'Core administration remains available without optional plugins');
  await admin.locator('[data-view="players"]').click();await admin.locator('#reset-player-form').waitFor({state:'visible'});
  assert.equal(await admin.locator('#reset-player-submit').isVisible(),true,'Admin password reset belongs to Accounts and remains available without Support or Wardrobe');
  assert.deepEqual(errors,[]);
  console.log('PASS support own/all lists, plain literal text, revisions/draft retention, create/reply/close/reopen, DE/EN, mobile, scoped routes, known-account admin reset, separate OP password/code login, module availability/retry and session cleanup');
}finally{await player.close();await admin.close();await browser.close();await new Promise(resolve=>server.close(resolve));}
