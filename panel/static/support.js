"use strict";
// One UI, injected role-specific API functions; player code never builds admin URLs.
window.ArkSupport = {
  mount(root,{api,admin=false,authenticated,onActivity=()=>{}}) {
    const t=(text,vars)=>ArkI18n.t(text,vars),uid=root.id;
    root.innerHTML=`<div class="card-heading wrap"><h2>Support</h2><div class="button-row"><a data-wiki="Support" target="_blank" rel="noopener">Support-Hilfe ↗</a><button class="button quiet" type="button" data-support="refresh">Aktualisieren</button></div></div><p class="hint">Anfragen und Antworten bleiben gespeichert. Teile keine Passwörter oder Wiederherstellungscodes.</p><p class="form-message" data-support="message" role="status" aria-live="polite"></p>
      <form data-support="create-form"><fieldset data-support="create-fields"><h3>Neue Support-Anfrage</h3><div class="field"><label for="${uid}-priority">Priorität</label><select id="${uid}-priority" data-support="priority"><option value="low">Niedrig</option><option value="normal" selected>Normal</option><option value="high">Hoch</option></select></div><div class="field"><label for="${uid}-create-message">Nachricht</label><textarea id="${uid}-create-message" data-support="create-message" rows="3" required maxlength="2000"></textarea></div><p class="hint">1–2000 Zeichen. Im Spiel: /support [low|normal|high] Nachricht</p><button class="button primary" type="submit" data-support="create-submit">Anfrage senden</button></fieldset></form>
      <div class="support-layout"><div><h3>Anfragen</h3><p class="hint">Die neuesten 100 Anfragen werden angezeigt.</p><ul data-support="list" class="support-list"></ul></div><div><p data-support="empty" class="hint">Wähle eine Anfrage, um die Unterhaltung zu lesen.</p><section data-support="detail" hidden><div class="card-heading wrap"><h3 data-support="title" data-i18n-skip></h3><button data-support="status" type="button" class="button"></button></div><p data-support="meta" class="hint" data-i18n-skip></p><div data-support="messages" class="support-messages" aria-live="polite"></div><form data-support="reply-form"><fieldset data-support="reply-fields"><label for="${uid}-reply-message">Antwort</label><textarea id="${uid}-reply-message" data-support="reply-message" rows="3" required maxlength="2000"></textarea><p data-support="closed" class="hint" hidden>Diese Anfrage ist geschlossen. Die Verwaltung kann sie wieder öffnen.</p><button class="button primary" type="submit" data-support="reply-submit">Antwort senden</button></fieldset></form></section></div></div>`;
    const $=key=>root.querySelector(`[data-support="${key}"]`);
    if(admin)$('create-form').remove();else $('status').remove();
    const state={tickets:[],selected:null,ticket:null,drafts:new Map(),epoch:0,serial:0,refreshSerial:0,busy:false,polling:false,seen:null};
    const date=value=>{const parsed=new Date(value);return Number.isNaN(parsed.getTime())?'—':parsed.toLocaleString(ArkI18n.language);};
    const priority=value=>t(({low:'Niedrig',normal:'Normal',high:'Hoch'})[value]||value);
    const status=value=>t(value==='closed'?'Geschlossen':'Offen');
    const visible=()=>!document.hidden&&root.getClientRects().length>0&&root.getBoundingClientRect().top<innerHeight&&root.getBoundingClientRect().bottom>0;
    function message(text,error=false){$('message').textContent=t(text);$('message').classList.toggle('error',error);}
    function controls(){
      const disabled=state.busy||!authenticated();
      if(!admin)$('create-fields').disabled=disabled;
      $('reply-fields').disabled=disabled||!state.ticket||state.ticket.status==='closed';
      $('refresh').disabled=disabled;
      if(admin)$('status').disabled=disabled||!state.ticket;
      for(const button of $('list').querySelectorAll('button'))button.disabled=disabled;
    }
    function renderList(){
      $('list').replaceChildren();
      for(const ticket of state.tickets){
        const li=document.createElement('li'),button=document.createElement('button'),title=document.createElement('strong'),detail=document.createElement('small');button.type='button';button.setAttribute('aria-current',String(ticket.id===state.selected));
        title.dataset.i18nSkip='';title.textContent=`#${ticket.id.slice(0,8)}${admin?' · '+ticket.player_name:''}`;
        detail.dataset.i18nSkip='';detail.textContent=[priority(ticket.priority),status(ticket.status),date(ticket.updated_at)].join(' · ');button.append(title,detail);button.addEventListener('click',()=>{if(!state.busy)select(ticket.id).catch(error=>message(error.message,true));});li.append(button);$('list').append(li);
      }
      if(!state.tickets.length){const li=document.createElement('li');li.textContent=t('Noch keine Support-Anfragen.');$('list').append(li);}
      controls();
    }
    function renderDetail(){
      const ticket=state.ticket;$('empty').hidden=!!ticket;$('detail').hidden=!ticket;if(!ticket){controls();return;}
      $('title').textContent='#'+ticket.id.slice(0,8);$('meta').textContent=[admin?ticket.player_name:'',priority(ticket.priority),status(ticket.status),date(ticket.created_at)].filter(Boolean).join(' · ');
      if(admin)$('status').textContent=t(ticket.status==='closed'?'Wieder öffnen':'Anfrage schließen');
      $('messages').replaceChildren();
      for(const entry of ticket.messages||[]){
        const article=document.createElement('article'),author=document.createElement('strong'),time=document.createElement('time'),text=document.createElement('p');article.className='support-message';
        author.dataset.i18nSkip='';author.textContent=entry.author==='admin'?t('Verwaltung'):admin?ticket.player_name:t('Du');time.dateTime=entry.created_at;time.textContent=date(entry.created_at);
        text.dataset.i18nSkip='';text.textContent=entry.message;article.append(author,time,text);$('messages').append(article);
      }
      const value=state.drafts.get(ticket.id)||'';if($('reply-message').value!==value)$('reply-message').value=value;
      $('closed').hidden=ticket.status!=='closed';controls();
    }
    async function select(id){
      if(!authenticated())return;
      const serial=++state.serial,epoch=state.epoch;state.selected=id;renderList();
      const data=await api('support/'+encodeURIComponent(id));
      if(epoch!==state.epoch||serial!==state.serial||!authenticated())return;
      state.ticket=data.ticket;renderDetail();
    }
    async function refresh({silent=false}={}){
      if(!authenticated()||state.busy||state.polling)return;state.polling=true;const epoch=state.epoch,serial=++state.refreshSerial;
      try{
        const data=await api('support');if(epoch!==state.epoch||serial!==state.refreshSerial||!authenticated())return;
        if(state.seen){const changed=data.tickets.filter(ticket=>state.seen.get(ticket.id)!==ticket.revision);if(changed.length)onActivity(t('Neue Support-Aktivität: {count}',{count:changed.length}));}
        state.tickets=data.tickets;state.seen=new Map(data.tickets.map(ticket=>[ticket.id,ticket.revision]));renderList();
        if(state.selected&&visible())await select(state.selected);
        if(!silent)message('Support-Anfragen aktualisiert.');
      }catch(error){if(epoch===state.epoch)message(error.message,true);}finally{state.polling=false;}
    }
    async function mutate(task,{reply=false,created=false}={}){
      if(state.busy||!authenticated())return;state.busy=true;state.serial++;state.refreshSerial++;controls();const epoch=state.epoch;
      try{const data=await task();if(epoch!==state.epoch||!authenticated())return;state.ticket=data.ticket;state.selected=data.ticket.id;const index=state.tickets.findIndex(ticket=>ticket.id===data.ticket.id);if(index<0)state.tickets.push(data.ticket);else state.tickets[index]=data.ticket;state.seen?.set(data.ticket.id,data.ticket.revision);if(reply)state.drafts.set(data.ticket.id,'');if(created)$('create-message').value='';renderList();renderDetail();message('Support-Anfrage gespeichert.');}
      catch(error){if(epoch===state.epoch)message(error.message,true);}
      finally{state.busy=false;controls();}
      if(epoch===state.epoch)await refresh({silent:true});
    }
    $('refresh').addEventListener('click',()=>refresh());
    $('reply-message').addEventListener('input',()=>{if(state.selected)state.drafts.set(state.selected,$('reply-message').value);});
    $('reply-form').addEventListener('submit',event=>{event.preventDefault();if(!state.ticket||state.ticket.status==='closed')return;const ticket=state.ticket,text=$('reply-message').value.trim();mutate(()=>api('support/'+encodeURIComponent(ticket.id)+'/reply','POST',{revision:ticket.revision,message:text}),{reply:true});});
    if(admin)$('status').addEventListener('click',()=>{const ticket=state.ticket;if(ticket)mutate(()=>api('support/'+encodeURIComponent(ticket.id)+'/status','PUT',{revision:ticket.revision,status:ticket.status==='closed'?'open':'closed'}));});
    else $('create-form').addEventListener('submit',event=>{event.preventDefault();mutate(()=>api('support','POST',{priority:$('priority').value,message:$('create-message').value.trim()}),{created:true});});
    function clear(){state.epoch++;state.serial++;state.tickets=[];state.selected=null;state.ticket=null;state.drafts.clear();state.seen=null;state.busy=false;$('list').replaceChildren();$('messages').replaceChildren();$('reply-message').value='';if(!admin)$('create-message').value='';message('');renderDetail();}
    window.addEventListener('ark:language',()=>{renderList();renderDetail();});
    controls();
    setInterval(()=>{if(authenticated()&&!document.hidden)refresh({silent:true});},15000);
    return {refresh,clear,updateAvailability:controls,hasDirty:()=>[...state.drafts.values()].some(text=>text.trim())||!admin&&!!$('create-message').value.trim()};
  }
};
