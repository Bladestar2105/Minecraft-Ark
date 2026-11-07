import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { join } from 'node:path';
import { homedir } from 'node:os';
import { template } from './test_templates.mjs';
const cache=join(homedir(),'.npm','_npx');
const modulePath=process.env.PLAYWRIGHT_MODULE||(existsSync(cache)?readdirSync(cache):[]).map(n=>join(cache,n,'node_modules/playwright/index.mjs')).find(existsSync);
assert.ok(modulePath,'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');
const {chromium}=await import(pathToFileURL(modulePath));
let session={authenticated:false},requests=[],writes=[];
let pendingStatus=null,delayStatus=false,pendingSession=null,delaySession=false;
const server=createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):undefined;
  const send=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
  if(req.url.startsWith('/static/')){res.writeHead(200,{'Content-Type':req.url.endsWith('.js')?'text/javascript':req.url.endsWith('.json')?'application/json':'text/css'});return res.end(readFileSync(new URL('.'+req.url.slice(7),new URL('./static/',import.meta.url))));}
  if(!req.url.startsWith('/api/')){res.writeHead(200,{'Content-Type':'text/html'});return res.end(template());}
  requests.push(req.url);if(req.method!=='GET')writes.push({path:req.url,body,csrf:req.headers['x-csrf-token']});
  if(req.url==='/api/session'){if(delaySession){delaySession=false;const snapshot={...session};pendingSession=(status=200)=>send(status===200?snapshot:{error:'Delayed initial session failure'},status);return;}return send(session);}
  if(req.url==='/api/login'){session={authenticated:true,username:body.username,csrf:'shared-csrf',auth_type:body.username==='owner'?'local':'player',is_player:body.username!=='owner',is_admin:['OpAlice','owner'].includes(body.username)};return send(session);}
  if(req.url==='/api/logout'){session={authenticated:false};return send({ok:true});}
  if(!session.authenticated)return send({error:'Sign in'},401);
  if(req.url==='/api/player/modules'||req.url==='/api/modules')return send({accounts:true,wardrobe:true,support:false});
  if(req.url==='/api/player/me')return send({uuid:session.username,name:session.username,stats:{deaths:3}});
  if(req.url==='/api/player/account')return send({password_configured:true,recovery_enabled:false});
  if(req.url==='/api/player/skins')return send({skins:[],limit:20});
  if(!session.is_admin)return send({error:'Admin denied'},403);
  if(req.url==='/api/settings/wardrobe')return send({provider:'mineskin',client_id:'',key_configured:true,account:{connected:false},status:'configured'});
  if(req.url==='/api/status'){if(delayStatus){delayStatus=false;pendingStatus=()=>send({services:[],players:'SECRET LATE ADMIN DATA'});return;}return send({services:[],players:'SECRET ADMIN DATA'});}
  return send({error:'Unknown '+req.url},404);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM?{executablePath:process.env.PLAYWRIGHT_CHROMIUM}:{})});
const page=await browser.newPage({locale:'de-DE'}),errors=[];
page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());
await page.clock.install();
async function login(username){await page.locator('#player-username').fill(username);await page.locator('#player-password').fill('some-password');await page.locator('#player-login-form button').click();await page.locator('#app-view').waitFor({state:'visible'});}
try{
  delaySession=true;
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  assert.equal(await page.locator('#operator-login-form,#operator-code-form,#login-form').count(),0,'There is one shared account login, with no separate OP login');
  assert.equal(await page.locator('[data-language]').count(),1,'There is one language selector');
  assert.equal(await page.locator('a[href^="/play/"]').count(),0,'Navigation stays on the root site');
  await login('Alice');await page.waitForFunction(()=>document.getElementById('profile-stats').textContent.includes('3'));
  assert.ok(pendingSession,'The initial anonymous session check is still in flight');
  pendingSession();pendingSession=null;await page.waitForTimeout(30);
  assert.equal(await page.locator('#app-view').isVisible(),true,'A late initial anonymous response cannot replace a successful login');
  assert.equal(await page.locator('[data-view="overview"]').isVisible(),false,'Regular players cannot see administration');
  assert.equal(requests.some(p=>['/api/status','/api/modules'].includes(p)),false,'Regular player login sends no administration requests');
  await page.locator('#skin-new').click();await page.locator('#skin-name').fill('Keep this player draft');
  session={...session,is_admin:true};await page.clock.runFor(15050);
  await page.locator('[data-view="overview"]').waitFor({state:'visible'});
  await page.locator('[data-view="overview"]').click();await page.waitForFunction(()=>document.getElementById('players-output').textContent.includes('SECRET ADMIN'));
  assert.equal(await page.locator('#account-button').isVisible(),false,'OPs change their server password in the shared player account section');
  await page.locator('#signing-open').click();await page.locator('#signing-api-key').fill('UNSAVED SECRET');
  await page.locator('a[href="#wardrobe"]').click();assert.equal(await page.locator('#skin-name').inputValue(),'Keep this player draft');
  await page.locator('[data-view="overview"]').click();delayStatus=true;await page.locator('#refresh-status').click();
  await page.waitForFunction(()=>document.getElementById('refresh-status').disabled);
  for(let i=0;!pendingStatus&&i<100;i++)await new Promise(resolve=>setTimeout(resolve,5));
  assert.ok(pendingStatus,'An administration request is in flight');
  session={...session,is_admin:false};await page.clock.runFor(15050);
  await page.waitForFunction(()=>document.querySelector('[data-view="overview"]').hidden);
  pendingStatus();pendingStatus=null;await page.waitForTimeout(30);
  assert.equal(await page.locator('#pair-view').isVisible(),false,'Losing OP rights keeps the normal player session');
  assert.equal(await page.locator('#players-output').textContent(),'','Admin data is cleared, including stale in-flight responses');
  assert.equal(await page.locator('#signing-api-key').inputValue(),'','Signing secrets are cleared on OP loss');
  assert.equal(await page.locator('#skin-name').inputValue(),'Keep this player draft','Role refresh preserves player drafts');
  const afterDowngrade=requests.length;await page.clock.runFor(30050);
  assert.equal(requests.slice(afterDowngrade).some(p=>['/api/status','/api/modules','/api/worlds'].includes(p)),false,'Admin polling stops on role loss');
  delaySession=true;await page.clock.runFor(15050);
  for(let i=0;!pendingSession&&i<100;i++)await new Promise(resolve=>setTimeout(resolve,5));
  assert.ok(pendingSession);
  await page.locator('#player-logout').click();await page.locator('#pair-view').waitFor({state:'visible'});
  assert.deepEqual(writes.at(-1),{path:'/api/logout',body:{},csrf:'shared-csrf'},'Central logout uses the shared CSRF token');
  const requestsAfterLogout=requests.length;pendingSession();pendingSession=null;await page.waitForTimeout(30);
  assert.equal(requests.slice(requestsAfterLogout).some(path=>path.startsWith('/api/player/')),false,'A late session poll must not restart player data requests after logout');
  assert.equal(await page.locator('#app-view').isVisible(),false,'A late role poll cannot restore a logged-out session');
  await login('owner');await page.locator('[data-view="overview"]').click();
  assert.equal(await page.locator('a[href="#wardrobe"]').isVisible(),false,'A local bootstrap owner sees no player features');
  assert.equal(await page.locator('#account-button').isVisible(),true,'The bootstrap owner can change its local password');
  await page.locator('#player-logout').click();await page.locator('#pair-view').waitFor({state:'visible'});
  delaySession=true;await page.reload();await login('Alice');
  assert.ok(pendingSession,'The second initial session check is still in flight');
  pendingSession(503);pendingSession=null;await page.waitForTimeout(30);
  assert.equal(await page.locator('#app-view').isVisible(),true,'A late initial session error cannot replace a successful login');
  assert.deepEqual(errors,[]);
  console.log('PASS unified root login, regular/OP/owner menus, shared logout, role polling, stale bootstrap/admin/session responses and retained player drafts');
}finally{pendingStatus?.();pendingSession?.();await page.close();await browser.close();await new Promise(resolve=>server.close(resolve));}
