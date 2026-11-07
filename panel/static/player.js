"use strict";
// One root session controls player features and current administration rights.
(async () => {
  await ArkI18n.ready;
  const $ = id => document.getElementById(id), t = (text,vars) => ArkI18n.t(text,vars);
  const state = { csrf:null, authenticated:false, is_player:false, is_admin:false, modules:null, skins:[], drafts:new Map(), current:null, limit:20, busy:false, loadSerial:0, sessionSerial:0, pollBusy:false, sessionPollBusy:false };
  const supportUI = ArkSupport.mount($('player-support'), { api, authenticated:()=>state.authenticated&&state.modules?.support===true, onActivity:notice });
  const photoUI = ArkPhotoImport.mount($('skin-photo'), { available:()=>state.authenticated&&state.modules?.wardrobe===true&&state.skins.length<state.limit, busy:()=>state.busy, onCreate:editor=>{newSkin(editor);$('skin-name').focus();} });
  const statuses = { draft:'Entwurf',preparing:'Wird vorbereitet …',ready:'Bereit',saved:'Gespeichert',applied:'Im Spiel aktiv',failed:'Aktivierung fehlgeschlagen' };
  const draft = () => state.drafts.get(state.current);
  const dirty = item => item && (item.editor.dirty || item.name !== item.savedName || !item.id);
  const hasDirty = () => photoUI.hasDirty() || supportUI.hasDirty() || [...state.drafts.values()].some(dirty);
  function notice(text,error=false) { $('player-message').hidden=!text; $('player-message').textContent=t(text); $('player-message').classList.toggle('error',error); }
  function status(text,error=false) { $('skin-status').textContent=t(text); $('skin-status').classList.toggle('error',error); }
  async function api(path,method='GET',body) {
    const sessionPath=['session','login','logout','recover'].includes(path);
    const serial=state.sessionSerial;
    if(!sessionPath&&(!state.authenticated||!state.is_player))throw new Error(t('Bitte zuerst anmelden.'));
    const headers={Accept:'application/json','Accept-Language':ArkI18n.language};
    if (method!=='GET') { headers['Content-Type']='application/json'; if(state.csrf) headers['X-CSRF-Token']=state.csrf; }
    let response; try { response=await fetch('/api/'+(sessionPath?'':'player/')+path,{method,headers,credentials:'same-origin',cache:'no-store',...(body===undefined?{}:{body:JSON.stringify(body)})}); } catch { throw new Error(t('Keine Verbindung zum Spielerportal. Bitte erneut versuchen.')); }
    const data=await response.json().catch(()=>({}));
    if(!sessionPath&&(serial!==state.sessionSerial||!state.authenticated))throw new Error(t('Bitte zuerst anmelden.'));
    if (!response.ok) { if(response.status===401 && !['login','recover'].includes(path)) showLogin(); const error=new Error(t(data.error||'Die Anfrage ist fehlgeschlagen.')); error.code=data.code; throw error; }
    return data;
  }
  async function run(task) { if(state.busy) return; state.busy=true; updateControls(); try { await task(); } catch(error) { notice(error.message,true); } finally { state.busy=false; updateControls(); } }
  function showLogin(text='') {
    state.authenticated=false;state.is_player=false;state.is_admin=false;state.modules=null;state.sessionSerial++;
    supportUI.clear();
    photoUI.clear();
    state.authenticated=false; state.modules=null; state.csrf=null; state.loadSerial++;
    $('pair-view').hidden=false; $('login-view').hidden=false; $('app-view').hidden=true; $('player-app').hidden=true; $('player-logout').hidden=true;
    window.dispatchEvent(new CustomEvent('ark:session',{detail:{authenticated:false,is_admin:false,is_player:false}}));
    $('pair-message').textContent=t(text); $('pair-submit').disabled=false;
    clearRecovery();
    for(const form of ['player-login-form','player-recover-form','account-password-form','recovery-form'])$(form).reset();
    // Keep local drafts on session expiry; a different account must never inherit them.
  }
  async function signedIn(data) {
    photoUI.clear();
    window.dispatchEvent(new CustomEvent('ark:session',{detail:{authenticated:false,is_admin:false,is_player:false}}));
    state.is_admin=false;
    if(state.username && state.username!==data.username) { state.drafts.clear(); state.current=null; }
    state.username=data.username;state.auth_type=data.auth_type;state.sessionSerial++;state.csrf=data.csrf;state.authenticated=true;
    $('player-greeting').textContent=data.username; $('signed-in-user').textContent=data.username; $('pair-view').hidden=true; $('login-view').hidden=true; $('app-view').hidden=false; $('player-logout').hidden=false; $('pair-code').value='';
    applyRole(data);
    $('skin-form').hidden=true; $('editor-empty').hidden=false;
    if(!state.is_player)return;
    await loadModules();
    const me=await loadProfile();
    if(me){if(state.owner&&state.owner!==me.uuid){state.drafts.clear();state.current=null;}state.owner=me.uuid;if(state.modules?.wardrobe){await loadLibrary();if(state.current)await selectSkin(state.current);}}
    await loadAccount();
    await supportUI.refresh({silent:true});
  }
  function selectView() {
    const hash=location.hash.slice(1),admin=state.is_admin&&Boolean(document.querySelector('[data-view="'+CSS.escape(hash)+'"]'));
    $('player-app').hidden=!state.is_player||admin;
    if(!admin){
      document.querySelectorAll('.view').forEach(section=>section.hidden=true);
      $('wardrobe-admin').hidden=true;
      const titles={profile:'Profil & Statistik',wardrobe:'Garderobe',account:'Konto','player-support':'Support'};
      $('page-title').textContent=t(titles[hash]||'Profil & Statistik');document.title=$('page-title').textContent+' · ARK Expedition';
    }
    document.querySelectorAll('#player-navigation a').forEach(link=>{if(!admin&&link.hash===(location.hash||'#profile'))link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
  }
  function applyRole(data) {
    const adminChanged=state.is_admin!==(data.is_admin===true);
    state.csrf=data.csrf;state.is_player=data.is_player===true;state.is_admin=data.is_admin===true;
    document.querySelectorAll('[data-player-nav]').forEach(node=>node.hidden=!state.is_player);
    document.querySelectorAll('[data-admin-nav]').forEach(node=>{if(!state.is_admin)node.hidden=true;else if(adminChanged)node.hidden=false;});
    $('account-button').hidden=data.auth_type!=='local';
    if(!state.is_admin&&document.querySelector('[data-view="'+CSS.escape(location.hash.slice(1))+'"]'))history.replaceState(null,'','#profile');
    if(!state.is_player&&state.is_admin&&!document.querySelector('[data-view="'+CSS.escape(location.hash.slice(1))+'"]'))history.replaceState(null,'','#overview');
    window.dispatchEvent(new CustomEvent('ark:session',{detail:data}));selectView();
  }
  async function refreshSession() {
    if(state.sessionPollBusy)return;state.sessionPollBusy=true;
    const serial=state.sessionSerial;
    try{
      const data=await api('session');
      if(serial!==state.sessionSerial)return;
      if(!data.authenticated){if(state.authenticated)showLogin('Deine Sitzung ist abgelaufen. Bitte erneut anmelden.');return;}
      if(!state.authenticated||state.username!==data.username||state.auth_type!==data.auth_type)await signedIn(data);
      else applyRole(data);
    }catch(error){notice(error.message,true);}finally{state.sessionPollBusy=false;}
  }
  async function loadModules() {
    try {
      const modules=await api('modules');
      if(modules.accounts!==true||typeof modules.wardrobe!=='boolean'||typeof modules.support!=='boolean')throw new Error(t('Modulstatus nicht verfügbar. Bitte das Profil erneut aktualisieren.'));
      state.modules=modules;
    }catch(error){state.modules=null;if(state.authenticated)notice(error.message,true);}
    $('wardrobe').hidden=state.modules?.wardrobe===false;
    $('player-support').hidden=state.modules?.support===false;
    document.querySelector('[href="#wardrobe"]').hidden=state.modules?.wardrobe===false;
    document.querySelector('[href="#player-support"]').hidden=state.modules?.support===false;
    supportUI.updateAvailability();updateControls();
  }
  async function loadProfile() {
    try {
      const me=await api('me'); $('profile-status').textContent=t(me.online?'Im Spiel online':'Im Spiel offline');
      $('profile-stats').replaceChildren();
      for(const [label,value] of [['Spielzeit',me.stats?.play_time_ticks],['Tode',me.stats?.deaths],['Besiegte Kreaturen',me.stats?.mob_kills],['Besiegte Spieler',me.stats?.player_kills]]) {
        const row=document.createElement('div'),dt=document.createElement('dt'),dd=document.createElement('dd'); dt.textContent=t(label);
        dd.textContent=typeof value==='number'?label==='Spielzeit'?t('{hours} h',{hours:(value/72000).toLocaleString(ArkI18n.language,{maximumFractionDigits:1})}):value.toLocaleString(ArkI18n.language):t('Nicht verfügbar'); row.append(dt,dd); $('profile-stats').append(row);
      }
      const at=new Date(me.observed_at); $('profile-observed').textContent=me.observed_at&&!Number.isNaN(at.getTime())?t('Abgefragt: {time}',{time:at.toLocaleString(ArkI18n.language)}):t('Abfragezeitpunkt nicht verfügbar.');
      return me;
    } catch(error) { $('profile-status').textContent=error.message; $('profile-observed').textContent=t('Die Statistik konnte nicht gelesen werden.'); }
  }
  async function loadLibrary() {
    const data=await api('skins'); state.skins=data.skins; state.limit=data.limit; renderLibrary();
    if(!state.current && state.skins.length) await selectSkin(state.skins[0].id);
    updateControls();
  }
  function renderLibrary() {
    $('skin-list').replaceChildren();
    const entries=[...state.skins.map(s=>({key:s.id,skin:s,item:state.drafts.get(s.id)})),...[...state.drafts].filter(([key,d])=>!d.id).map(([key,item])=>({key,item}))];
    for(const {key,skin,item} of entries) {
      const li=document.createElement('li'),button=document.createElement('button'),name=document.createElement('strong'),detail=document.createElement('small'); button.type='button'; button.setAttribute('aria-current',String(key===state.current));
      name.dataset.i18nSkip=''; name.textContent=item?.name||skin?.name||t('Neuer Skin');
      detail.textContent=[dirty(item)?t('Ungespeichert'):t(statuses[skin?.status]||'Entwurf'),skin?.selected?t('Ausgewählt'):'',skin?.error?t(skin.error):''].filter(Boolean).join(' · ');
      button.append(name,detail); button.addEventListener('click',()=>selectSkin(key).catch(e=>notice(e.message,true))); li.append(button); $('skin-list').append(li);
    }
    $('library-count').textContent=t('{count} von {limit} gespeichert',{count:state.skins.length,limit:state.limit});
    if(!entries.length) { const li=document.createElement('li'); li.textContent=t('Noch keine Skins gespeichert.'); $('skin-list').append(li); }
  }
  async function selectSkin(key,reload=false,allowBusy=false) {
    if(state.busy&&!allowBusy) return;
    const serial=++state.loadSerial;
    if(!state.drafts.has(key)||reload) {
      const skin=state.skins.find(s=>s.id===key); if(!skin) return;
      const editor=await SkinEditor.fromPNG(skin.png,skin.model);
      if(serial!==state.loadSerial) return;
      state.drafts.set(key,{id:skin.id,name:skin.name,savedName:skin.name,revision:skin.revision,editor});
    }
    state.current=key; const item=draft(); $('skin-name').value=item.name; $('skin-model').value=item.editor.model; $('editor-empty').hidden=true; $('skin-form').hidden=false; status(''); renderLibrary(); render();
  }
  function newSkin(editor=new SkinEditor()) {
    const key='draft-'+crypto.randomUUID(); const item={id:null,name:t('Neuer Skin'),savedName:'',revision:null,editor}; state.drafts.set(key,item); selectSkin(key);
  }
  function updateControls() {
    photoUI.updateAvailability();
    $('skin-fields').disabled=state.busy||state.modules?.wardrobe!==true; $('skin-new').disabled=state.busy||state.modules?.wardrobe!==true||state.skins.length>=state.limit;
    const item=draft(); if(!item) return;
    $('skin-undo').disabled=state.busy||!item.editor.history.length; $('skin-redo').disabled=state.busy||!item.editor.future.length;
    for(const id of ['skin-copy','skin-reload']) $(id).disabled=state.busy||!item.id;
    $('skin-delete').disabled=state.busy;
    $('skin-activate').disabled=state.busy||!item.id||dirty(item);
    $('skin-dirty').textContent=t(dirty(item)?'Ungespeichert':'Gespeichert');
    if($('skin-layer').value==='base'&&$('skin-tool').value==='erase') $('skin-tool').value='paint';
    $('skin-tool').querySelector('[value=erase]').disabled=$('skin-layer').value==='base';
  }
  let cursor={x:0,y:0};
  const rect=()=>SkinEditor.faceRect($('skin-part').value,$('skin-face').value,$('skin-layer').value,draft().editor.model);
  function render() {
    const item=draft(); if(!item) return; $('skin-model').value=item.editor.model; updateControls();
    const texture=item.editor.toCanvas(),[u,v,w,h]=rect(),zoom=Number($('skin-zoom').value),canvas=$('skin-paint');
    cursor.x=Math.min(cursor.x,w-1); cursor.y=Math.min(cursor.y,h-1); canvas.width=w*zoom; canvas.height=h*zoom;
    const ctx=canvas.getContext('2d'); ctx.imageSmoothingEnabled=false; ctx.clearRect(0,0,canvas.width,canvas.height); ctx.drawImage(texture,u,v,w,h,0,0,canvas.width,canvas.height);
    ctx.strokeStyle='#ffffff33'; ctx.lineWidth=1;
    for(let x=0;x<=w;x++){ctx.beginPath();ctx.moveTo(x*zoom+.5,0);ctx.lineTo(x*zoom+.5,h*zoom);ctx.stroke();}
    for(let y=0;y<=h;y++){ctx.beginPath();ctx.moveTo(0,y*zoom+.5);ctx.lineTo(w*zoom,y*zoom+.5);ctx.stroke();}
    ctx.strokeStyle='#ffffff'; ctx.lineWidth=2; ctx.strokeRect(cursor.x*zoom+1,cursor.y*zoom+1,zoom-2,zoom-2);
    $('paint-position').textContent=t('Pixel {x}, {y} · {width} × {height}',{x:cursor.x+1,y:cursor.y+1,width:w,height:h});
    item.editor.drawPreview($('skin-front'),'front');item.editor.drawPreview($('skin-back'),'back');
  }
  function paint(x,y) {
    const [, , w,h]=rect(); if(x<0||x>=w||y<0||y>=h)return;
    cursor={x,y}; const color=$('skin-color').value; const rgba=[1,3,5].map(n=>parseInt(color.slice(n,n+2),16));rgba.push($('skin-tool').value==='erase'?0:255);
    draft().editor.paint($('skin-part').value,$('skin-face').value,$('skin-layer').value,x,y,rgba); render();
  }
  function point(event) { const b=$('skin-paint').getBoundingClientRect(),[, ,w,h]=rect();return {x:Math.floor((event.clientX-b.left)*w/b.width),y:Math.floor((event.clientY-b.top)*h/b.height)}; }
  let dragging=false,last=null;
  $('skin-paint').addEventListener('pointerdown',event=>{if(state.busy||event.button!==0)return;event.preventDefault();$('skin-paint').focus();dragging=true;draft().editor.beginStroke();$('skin-paint').setPointerCapture(event.pointerId);last=point(event);paint(last.x,last.y);});
  $('skin-paint').addEventListener('pointermove',event=>{
    if(!dragging)return;const next=point(event);let {x,y}=last;const dx=Math.abs(next.x-x),dy=-Math.abs(next.y-y),sx=x<next.x?1:-1,sy=y<next.y?1:-1;let error=dx+dy;
    while(true){paint(x,y);if(x===next.x&&y===next.y)break;const e2=2*error;if(e2>=dy){error+=dy;x+=sx;}if(e2<=dx){error+=dx;y+=sy;}}last=next;
  });
  function endStroke(){if(!dragging)return;dragging=false;draft().editor.endStroke();render();renderLibrary();}
  for(const type of ['pointerup','pointercancel','lostpointercapture']) $('skin-paint').addEventListener(type,endStroke);
  $('skin-paint').addEventListener('keydown',event=>{
    if(state.busy)return;const [, ,w,h]=rect();let handled=true;
    if(event.key==='ArrowLeft')cursor.x=Math.max(0,cursor.x-1);else if(event.key==='ArrowRight')cursor.x=Math.min(w-1,cursor.x+1);else if(event.key==='ArrowUp')cursor.y=Math.max(0,cursor.y-1);else if(event.key==='ArrowDown')cursor.y=Math.min(h-1,cursor.y+1);else if([' ','Enter'].includes(event.key)){paint(cursor.x,cursor.y);renderLibrary();}else if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='z'){event.shiftKey?draft().editor.redo():draft().editor.undo();}else handled=false;
    if(handled){event.preventDefault();render();}
  });
  for(const id of ['skin-part','skin-face','skin-layer','skin-tool','skin-zoom'])$(id).addEventListener('input',render);
  $('skin-name').addEventListener('input',()=>{draft().name=$('skin-name').value;updateControls();renderLibrary();});
  $('skin-model').addEventListener('change',()=>{draft().editor.setModel($('skin-model').value);render();renderLibrary();});
  $('skin-undo').addEventListener('click',()=>{draft().editor.undo();render();renderLibrary();});$('skin-redo').addEventListener('click',()=>{draft().editor.redo();render();renderLibrary();});
  $('skin-new').addEventListener('click',()=>newSkin());
  $('skin-form').addEventListener('submit',event=>{event.preventDefault();run(async()=>{
    const item=draft(),oldKey=state.current,payload={name:item.name.trim(),model:item.editor.model,png:item.editor.toPNG(),...(item.id?{revision:item.revision}:{})};
    const data=await api('skins'+(item.id?'/'+encodeURIComponent(item.id):''),item.id?'PUT':'POST',payload);
    item.name=data.skin.name;item.savedName=item.name;item.id=data.skin.id;item.revision=data.skin.revision;item.editor.markSaved();
    state.drafts.delete(oldKey);state.drafts.set(item.id,item);state.current=item.id;$('skin-name').value=item.name;
    await loadLibrary();status('Gespeichert. Dein Skin im Spiel wurde noch nicht geändert.');render();
  });});
  $('skin-copy').addEventListener('click',()=>{if(dirty(draft())){status('Bitte den Entwurf zuerst speichern.',true);return;} const name=prompt(t('Name der Kopie'),draft().name.slice(0,25)+' '+t('Kopie'));if(name===null)return;run(async()=>{const item=draft();const data=await api('skins/'+encodeURIComponent(item.id)+'/copy','POST',{name,revision:item.revision});await loadLibrary();await selectSkin(data.skin.id,false,true);});});
  $('skin-delete').addEventListener('click',()=>{const item=draft();if(!confirm(t('Skin „{name}“ löschen? Ungespeicherte Änderungen werden ebenfalls entfernt.',{name:item.name})))return;run(async()=>{if(item.id)await api('skins/'+encodeURIComponent(item.id),'DELETE',{revision:item.revision});state.drafts.delete(state.current);state.current=null;$('skin-form').hidden=true;$('editor-empty').hidden=false;await loadLibrary();});});
  $('skin-reload').addEventListener('click',async()=>{if(dirty(draft())&&!confirm(t('Ungespeicherte Änderungen verwerfen und gespeicherten Stand laden?')))return;try{await loadLibrary();await selectSkin(state.current,true);}catch(e){notice(e.message,true);}});
  $('skin-activate').addEventListener('click',()=>run(async()=>{const item=draft();await api('skins/'+encodeURIComponent(item.id)+'/activate','POST',{revision:item.revision});await loadLibrary();status('Aktivierung angefragt. Der tatsächliche Status erscheint in der Garderobe.');}));
  $('skin-import').addEventListener('change',()=>{const file=$('skin-import').files[0];$('skin-import').value='';if(!file||state.busy)return;run(async()=>{try{
    if(file.size>1024*1024)throw new Error(t('Die PNG-Datei darf höchstens 1 MiB groß sein.'));
    const bytes=new Uint8Array(await file.arrayBuffer());let binary='';for(let i=0;i<bytes.length;i++)binary+=String.fromCharCode(bytes[i]);
    const imported=await SkinEditor.fromPNG(btoa(binary),draft().editor.model);const editor=draft().editor;editor.beginStroke();editor.pixels=imported.pixels;editor.endStroke();render();renderLibrary();status('PNG importiert. Änderungen sind noch nicht gespeichert.');
  }catch(e){status(e.message,true);}});});
  $('skin-export').addEventListener('click',()=>{const a=document.createElement('a');a.href='data:image/png;base64,'+draft().editor.toPNG();a.download=(draft().name.replace(/[^\p{L}\p{N}._ -]/gu,'_')||'skin')+'.png';a.click();});
  $('pair-form').addEventListener('submit',async event=>{event.preventDefault();$('pair-submit').disabled=true;try{await signedIn(await api('login','POST',{code:$('pair-code').value.trim()}));}catch(e){$('pair-message').textContent=e.message;}finally{$('pair-submit').disabled=false;}});
  $('player-logout').addEventListener('click',()=>{if((hasDirty()||window.ArkAdmin?.hasDirty()||window.ArkSigning?.hasDirty())&&!confirm(t('Es gibt ungespeicherte Änderungen. Trotzdem abmelden?')))return;run(async()=>{await api('logout','POST',{});state.drafts.clear();state.current=null;clearRecovery();showLogin('Du bist abgemeldet.');});});
  $('profile-refresh').addEventListener('click',async()=>{const before=state.modules;await loadModules();await loadProfile();if(state.modules?.wardrobe&&!before?.wardrobe)await loadLibrary().catch(error=>notice(error.message,true));if(state.modules?.support)await supportUI.refresh({silent:true});});
  window.addEventListener('hashchange',()=>{selectView();if(location.hash==='#player-support'&&state.authenticated&&state.is_player)supportUI.refresh();});
  window.addEventListener('beforeunload',event=>{if(hasDirty()){event.preventDefault();event.returnValue='';}});
  window.addEventListener('ark:language',()=>{selectView();if(state.authenticated&&state.is_player){renderLibrary();render();loadProfile();loadAccount();}});
  window.addEventListener('ark:session-refresh',refreshSession);
  window.addEventListener('ark:csrf',event=>{state.csrf=event.detail;});
  setInterval(()=>{if(state.authenticated&&!document.hidden)refreshSession();},15000);
  setInterval(async()=>{if(!state.authenticated||state.modules?.wardrobe!==true||state.busy||state.pollBusy||document.hidden)return;if(!state.skins.some(s=>s.status==='preparing'))return;state.pollBusy=true;try{await loadLibrary();}catch(e){notice(e.message,true);}finally{state.pollBusy=false;}},3000);
  // Account forms are kept separate from skin state; passwords/codes are never stored.
  async function loadAccount() { if(!$('account-status'))return;try{const data=await api('account');const configured=data.password_configured!==false,resetAllowed=data.password_reset_allowed===true;$('account-current').required=configured&&!resetAllowed;$('account-current-hint').textContent=t(!configured?(resetAllowed?'Lege dein erstes Serverpasswort fest. Lass das aktuelle Passwort leer; die frische Bestätigung im Spiel erlaubt die Einrichtung.':'Für dein erstes Serverpasswort bestätige die Einrichtung zuerst frisch mit /web im Spiel.'):resetAllowed?'Deine frische Bestätigung im Spiel erlaubt ein neues Serverpasswort ohne das bisherige Passwort. Lass das aktuelle Passwort dafür leer.':'Gib dein aktuelles Serverpasswort ein, um es zu ändern.');$('account-status').textContent=t(data.recovery_enabled?'Kontowiederherstellung ist aktiviert. {count} Codes verbleiben.':'Kontowiederherstellung ist deaktiviert.',{count:data.recovery_codes_remaining});$('recovery-existing-field').hidden=!data.recovery_enabled;$('recovery-disable').disabled=!data.recovery_enabled;}catch(e){$('account-status').textContent=e.message;} }
  function clearRecovery() { if($('recovery-codes')){$('recovery-codes').textContent='';$('recovery-results').hidden=true;} }
  $('player-login-form').addEventListener('submit',async event=>{event.preventDefault();const button=event.submitter;button.disabled=true;try{const data=await api('login','POST',{username:$('player-username').value.trim(),password:$('player-password').value});$('player-password').value='';await signedIn(data);}catch(e){$('pair-message').textContent=e.message;}finally{button.disabled=false;}});
  $('player-recover-form').addEventListener('submit',async event=>{event.preventDefault();const button=event.submitter;button.disabled=true;try{await api('recover','POST',{username:$('recover-username').value.trim(),recovery_code:$('recover-code').value.trim(),new_password:$('recover-password').value,confirmation:$('recover-confirm').value});$('player-recover-form').reset();showLogin('Serverpasswort zurückgesetzt. Bitte mit dem neuen Passwort anmelden.');}catch(e){$('pair-message').textContent=e.message;}finally{button.disabled=false;}});
  $('account-password-form').addEventListener('submit',event=>{event.preventDefault();run(async()=>{await api('password','POST',{current_password:$('account-current').value,new_password:$('account-password').value,confirmation:$('account-confirm').value});$('account-password-form').reset();clearRecovery();showLogin('Serverpasswort geändert. Bitte erneut anmelden.');});});
  $('recovery-form').addEventListener('submit',event=>{event.preventDefault();run(async()=>{clearRecovery();const data=await api('recovery','POST',{current_password:$('recovery-password').value,...($('recovery-existing').value?{recovery_code:$('recovery-existing').value.trim()}:{})});$('recovery-form').reset();$('recovery-codes').textContent=data.codes.join('\n');$('recovery-results').hidden=false;await loadAccount();});});
  $('recovery-disable').addEventListener('click',()=>{if(!$('recovery-form').reportValidity())return;if(!confirm(t('Kontowiederherstellung deaktivieren? Alle bisherigen Codes verlieren ihre Gültigkeit.')))return;run(async()=>{await api('recovery','DELETE',{current_password:$('recovery-password').value,recovery_code:$('recovery-existing').value.trim()});$('recovery-form').reset();clearRecovery();await loadAccount();notice('Kontowiederherstellung deaktiviert.');});});
  $('recovery-download').addEventListener('click',()=>{const content=t('ARK Expedition · Wiederherstellungscodes')+'\n'+state.username+'\n\n'+$('recovery-codes').textContent+'\n';const url=URL.createObjectURL(new Blob([content],{type:'text/plain;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='ark-recovery-codes.txt';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);});
  $('recovery-copy').addEventListener('click',async()=>{try{await navigator.clipboard.writeText($('recovery-codes').textContent);$('account-message').textContent=t('Codes kopiert.');}catch{$('account-message').textContent=t('Kopieren nicht verfügbar. Bitte die Codes herunterladen.');}});
  $('recovery-close').addEventListener('click',clearRecovery);
  window.addEventListener('pagehide',clearRecovery);
  const initialSerial=state.sessionSerial;
  try{const session=await api('session');if(initialSerial!==state.sessionSerial)return;session.authenticated?await signedIn(session):showLogin();}catch(e){if(initialSerial===state.sessionSerial)showLogin(e.message);}
})();
