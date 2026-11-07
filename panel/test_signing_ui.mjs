import assert from 'node:assert/strict';
import {readFileSync,readdirSync,existsSync,mkdirSync} from 'node:fs';
import {createServer} from 'node:http';
import {pathToFileURL} from 'node:url';
import {join} from 'node:path';
import {homedir} from 'node:os';
const cache=join(homedir(),'.npm','_npx');const modulePath=process.env.PLAYWRIGHT_MODULE||(existsSync(cache)?readdirSync(cache):[]).map(n=>join(cache,n,'node_modules/playwright/index.mjs')).find(existsSync);
assert.ok(modulePath,'Set PLAYWRIGHT_MODULE to an installed Playwright index.mjs');const {chromium}=await import(pathToFileURL(modulePath));
let settings={provider:'mineskin',client_id:'',key_configured:true,account:{connected:false},status:'configured'},connectPoll=false,denySave=false;
const writes=[],errors=[],network=[];
const server=createServer(async(req,res)=>{
  let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):undefined;
  const send=(value,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
  if(req.url.startsWith('/static/')){const file=req.url.slice(8);res.writeHead(200,{'Content-Type':file.endsWith('.js')?'text/javascript':file.endsWith('.json')?'application/json':'text/css'});res.end(readFileSync(new URL('./static/'+file,import.meta.url)));return;}
  if(!req.url.startsWith('/api/')){res.writeHead(200,{'Content-Type':'text/html'});res.end(readFileSync(new URL('./templates/index.html',import.meta.url),'utf8').replace(/{{ url_for\('static', filename='([^']+)'\) }}/g,'/static/$1'));return;}
  if(req.url==='/api/modules')return send({accounts:true,wardrobe:true,support:true});
  if(req.url==='/api/session')return send({authenticated:true,csrf:'admin-csrf',username:'admin'});
  if(req.url==='/api/status')return send({services:[],players:'No players'});
  if(req.method!=='GET'){writes.push({path:req.url,body});assert.equal(req.headers['x-csrf-token'],'admin-csrf');}
  if(req.url==='/api/settings/wardrobe'){
    if(req.method==='PUT'){if(denySave)return send({error:'Signing service unavailable.'},503);settings={...settings,provider:body.provider,client_id:body.client_id,key_configured:body.clear_mineskin_api_key?false:body.mineskin_api_key?true:settings.key_configured};}
    if(connectPoll){settings={...settings,account:{connected:true,name:'Operator test'},status:'connected'};connectPoll=false;}
    return send(settings);
  }
  if(req.url==='/api/wardrobe/connect'){connectPoll=true;return send({user_code:'TEST-CODE',verification_uri:'https://www.microsoft.com/link',expires_at:Math.floor(Date.now()/1000)+300,interval:5});}
  if(req.url==='/api/wardrobe/disconnect'){settings={...settings,account:{connected:false},status:'disconnected'};return send({ok:true});}
  return send({error:'Unknown fixture route'},404);
});
await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
const browser=await chromium.launch({headless:true,...(process.env.PLAYWRIGHT_CHROMIUM?{executablePath:process.env.PLAYWRIGHT_CHROMIUM}:{})});const page=await browser.newPage({locale:'en-US'});
page.on('pageerror',e=>errors.push(e.message));page.on('dialog',d=>d.accept());page.on('request',r=>network.push(r.url()));
try{
  await page.goto(`http://127.0.0.1:${server.address().port}/`);await page.locator('#app-view').waitFor({state:'visible'});await page.locator('#signing-open').click();
  await page.waitForFunction(()=>document.getElementById('signing-key-status').textContent==='API key configured.');
  assert.equal(await page.locator('#signing-api-key').inputValue(),'');assert.equal(await page.locator('#signing-api-key').getAttribute('type'),'password');
  await page.locator('#signing-provider').selectOption('selfhosted');await page.locator('#signing-client-id').fill('operator-client-id');await page.locator('#signing-api-key').fill('test-secret-no-display');
  await page.locator('#admin-language').selectOption('de');assert.equal(await page.locator('#signing-client-id').inputValue(),'operator-client-id');assert.equal(await page.locator('#signing-api-key').inputValue(),'test-secret-no-display','Language changes preserve unsaved signing inputs');
  await page.locator('#admin-language').selectOption('en');await page.locator('#signing-save').click();await page.waitForFunction(()=>document.getElementById('signing-api-key').value==='');
  assert.equal(writes.at(-1).body.mineskin_api_key,'test-secret-no-display');assert.equal(settings.provider,'selfhosted');
  await page.locator('#signing-connect').click();await page.locator('#signing-device').waitFor({state:'visible'});assert.equal(await page.locator('#signing-user-code').textContent(),'TEST-CODE');assert.equal(await page.locator('#signing-verification').getAttribute('href'),'https://www.microsoft.com/link');
  await page.locator('#signing-refresh').click();await page.waitForFunction(()=>document.getElementById('signing-account-status').textContent.includes('Operator test'));assert.equal(await page.locator('#signing-device').isVisible(),false);
  await page.locator('#signing-disconnect').click();await page.waitForFunction(()=>document.getElementById('signing-account-status').textContent.includes('No generator'));assert.equal(settings.provider,'selfhosted','Disconnect never silently changes provider');
  denySave=true;await page.locator('#signing-client-id').fill('preserve-on-failure');await page.locator('#signing-save').click();await page.waitForFunction(()=>document.getElementById('signing-message').classList.contains('error'));assert.equal(await page.locator('#signing-client-id').inputValue(),'preserve-on-failure');
  denySave=false;await page.locator('#signing-provider').selectOption('mineskin');await page.locator('#signing-clear-key').check();await page.locator('#signing-save').click();await page.waitForFunction(()=>document.getElementById('signing-key-status').textContent.includes('No API key'));assert.equal(settings.key_configured,false);
  await page.setViewportSize({width:390,height:844});assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true,'Admin signer settings fit mobile');
  mkdirSync(new URL('../output/playwright/',import.meta.url),{recursive:true});await page.screenshot({path:new URL('../output/playwright/skin-signing-mobile.png',import.meta.url).pathname,fullPage:true});
  assert.ok(network.every(url=>new URL(url).hostname==='127.0.0.1'),'Frontend loads no external resources');assert.deepEqual(errors,[]);
  console.log('PASS admin signer provider/key save and clear, preserved language/error drafts, safe device flow, connected/disconnected status, provider retention and mobile layout');
}finally{await page.close();await browser.close();await new Promise(resolve=>server.close(resolve));}
