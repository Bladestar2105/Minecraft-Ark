"use strict";
(async()=>{
  await ArkI18n.ready;
  const $=id=>document.getElementById(id),t=(s,v)=>ArkI18n.t(s,v);
  let loaded=false,busy=false,settings=null,device=null,baseline=null;
  const values=()=>({provider:$('signing-provider').value,client_id:$('signing-client-id').value.trim()});
  const dirty=()=>baseline!==null&&(JSON.stringify(values())!==baseline||$('signing-api-key').value!==''||$('signing-clear-key').checked);
  async function api(path,method='GET',body){
    const headers={Accept:'application/json','Accept-Language':ArkI18n.language};
    if(method!=='GET'){headers['Content-Type']='application/json';const session=await fetch('/api/session',{headers:{Accept:'application/json','Accept-Language':ArkI18n.language},cache:'no-store'}).then(r=>r.json());headers['X-CSRF-Token']=session.csrf;}
    let response;try{response=await fetch(path,{method,headers,credentials:'same-origin',cache:'no-store',...(body===undefined?{}:{body:JSON.stringify(body)})});}catch{throw new Error(t('Signierungsdienst nicht erreichbar.'));}
    const data=await response.json().catch(()=>({}));if(!response.ok)throw new Error(t(data.error||'Die Anfrage ist fehlgeschlagen.'));return data;
  }
  function render(){
    if(!settings)return;$('signing-key-status').textContent=t(settings.key_configured?'API-Schlüssel eingerichtet.':'Kein API-Schlüssel eingerichtet.');
    $('signing-api-key').placeholder=t(settings.key_configured?'Gespeichert · zum Ändern neuen Schlüssel eingeben':'Noch kein Schlüssel gespeichert');
    $('signing-account-status').textContent=settings.account?.connected?t('Verbunden: {name}',{name:settings.account.name||settings.account.uuid||'—'}):t('Kein Generator-Konto verbunden.');
    $('signing-connect').disabled=busy||settings.provider!=='selfhosted'||dirty();$('signing-disconnect').disabled=busy||!settings.account?.connected;
    $('signing-fields').disabled=busy;$('signing-refresh').disabled=busy;
    $('signing-message').textContent=settings.message?t(settings.message):t('Status: {status}',{status:t(({ready:'Bereit',unconfigured:'Nicht eingerichtet',disconnected:'Nicht verbunden',connected:'Verbunden',pending:'Anmeldung ausstehend',configured:'Eingerichtet',connecting:'Anmeldung ausstehend',authorization_expired:'Autorisierung abgelaufen',authorization_failed:'Autorisierung fehlgeschlagen',reauthentication_required:'Erneute Anmeldung erforderlich',error:'Fehler',failed:'Fehler'})[settings.status]||settings.status)});
    $('signing-device').hidden=!device;
    if(device){$('signing-user-code').textContent=device.user_code;$('signing-verification').href=device.verification_uri;$('signing-expiry').textContent=t('Code gültig bis {time}',{time:new Date(device.expires_at*1000).toLocaleTimeString(ArkI18n.language)});}
  }
  async function load(preserve=false){settings=await api('/api/settings/wardrobe');if(!preserve||!dirty()){$('signing-provider').value=settings.provider;$('signing-client-id').value=settings.client_id||'';$('signing-api-key').value='';$('signing-clear-key').checked=false;baseline=JSON.stringify(values());}if(settings.account?.connected)device=null;render();}
  async function run(task){if(busy)return;busy=true;render();try{await task();$('signing-message').classList.remove('error');}catch(e){$('signing-message').textContent=e.message;$('signing-message').classList.add('error');}finally{busy=false;renderControls();}}
  function renderControls(){$('signing-fields').disabled=busy;$('signing-connect').disabled=busy||!settings||settings.provider!=='selfhosted'||dirty();$('signing-disconnect').disabled=busy||!settings?.account?.connected;$('signing-refresh').disabled=busy;}
  $('signing-form').addEventListener('input',renderControls);
  $('signing-form').addEventListener('submit',event=>{event.preventDefault();run(async()=>{await api('/api/settings/wardrobe','PUT',{...values(),mineskin_api_key:$('signing-api-key').value,clear_mineskin_api_key:$('signing-clear-key').checked});$('signing-api-key').value='';await load();});});
  $('signing-connect').addEventListener('click',()=>{if(!confirm(t('Der Generator ändert den globalen Skin des verbundenen Kontos. Jetzt die Microsoft-Anmeldung starten?')))return;run(async()=>{const data=await api('/api/wardrobe/connect','POST',{});const url=new URL(data.verification_uri);if(url.protocol!=='https:'||!['microsoft.com','www.microsoft.com','login.microsoftonline.com','microsoft.com'].includes(url.hostname))throw new Error(t('Ungültige Microsoft-Anmeldeseite.'));device=data;render();});});
  $('signing-disconnect').addEventListener('click',()=>{if(!confirm(t('Generator-Konto trennen? Neue Signierungen benötigen danach eine erneute Anmeldung.')))return;run(async()=>{await api('/api/wardrobe/disconnect','POST',{});device=null;await load(true);});});
  $('signing-refresh').addEventListener('click',()=>run(()=>load(true)));
  $('signing-open').addEventListener('click',()=>{if(document.documentElement.dataset.arkWardrobe!=='true')return;$('wardrobe-admin').hidden=false;if(!loaded){loaded=true;run(()=>load());}setTimeout(()=>$('wardrobe-admin').scrollIntoView({behavior:'smooth',block:'start'}),0);});
  window.addEventListener('ark:language',render);
  window.addEventListener('ark:modules',()=>{if(document.documentElement.dataset.arkWardrobe!=='true'){$('wardrobe-admin').hidden=true;device=null;}});
  window.addEventListener('hashchange',()=>{if(location.hash!=='#overview')$('wardrobe-admin').hidden=true;});
  window.addEventListener('beforeunload',event=>{if(dirty()){event.preventDefault();event.returnValue='';}});
  const observer=new MutationObserver(()=>{if($('app-view').hidden){loaded=false;settings=null;device=null;baseline=null;$('signing-api-key').value='';$('wardrobe-admin').hidden=true;} });observer.observe($('app-view'),{attributes:true,attributeFilter:['hidden']});
  setInterval(()=>{if(!device||busy||document.hidden||$('app-view').hidden)return;if(Date.now()>device.expires_at*1000){device=null;render();return;}run(()=>load(true));},5000);
})();
