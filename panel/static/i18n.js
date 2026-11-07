"use strict";
// Shared browser API: ArkI18n.ready, t(source, vars), setLanguage(de|en), language.
// Catalogs are immutable. DOM source text is remembered independently per node.
(() => {
  const script=document.currentScript, base=new URL('./',script.src), dictionaries={}, sources=new WeakMap(), attributes=new WeakMap();
  let language='de', patterns=[], reversePatterns=[], reverse=new Map();
  function canonical(text){
    if(reverse.has(text))return reverse.get(text);
    if(text.includes(' · '))return text.split(' · ').map(canonical).join(' · ');
    for(const pattern of reversePatterns){const match=pattern.regex.exec(text);if(match)return pattern.value.replace(/\{(\w+)\}/g,(token,key)=>canonical(match[pattern.names.indexOf(key)+1]??token));}
    return text;
  }
  try { language=localStorage.getItem('ark_language')||document.cookie.match(/(?:^|;\s*)ark_language=(de|en)(?:;|$)/)?.[1]||(/^de\b/i.test(navigator.language)?'de':'en'); } catch { language=/^de\b/i.test(navigator.language)?'de':'en'; }
  if(!['de','en'].includes(language))language='en';
  function t(text,vars={}) {
    const raw=String(text??''),source=canonical(raw);let translated=dictionaries[language]?.[source];
    if(translated===undefined&&source.includes(' · '))translated=source.split(' · ').map(part=>t(part)).join(' · ');
    if(translated===undefined&&language==='en')for(const pattern of patterns){const match=pattern.regex.exec(source);if(match){translated=pattern.value.replace(/\{(\w+)\}/g,(token,key)=>t(match[pattern.names.indexOf(key)+1]??token));break;}}
    return (translated??source).replace(/\{(\w+)\}/g,(token,key)=>Object.hasOwn(vars,key)?String(vars[key]):token);
  }
  function translatedNode(node) {
    if(!node.parentElement||node.parentElement.closest('script,style,textarea,code,[data-i18n-skip]'))return;
    const old=sources.get(node);const current=node.nodeValue;
    const source=old&&current===old.translated?old.source:current.replace(current.trim(),canonical(current.trim()));
    const trimmed=source.trim();if(!trimmed)return;
    const translated=source.replace(trimmed,t(trimmed));sources.set(node,{source,translated});if(current!==translated)node.nodeValue=translated;
  }
  function apply(root=document) {
    const walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let node;while((node=walker.nextNode()))translatedNode(node);
    const elements=root.nodeType===Node.ELEMENT_NODE?[root,...root.querySelectorAll('*')]:[...root.querySelectorAll('*')];
    for(const element of elements){
      if(element.closest('[data-i18n-skip]'))continue;
      let saved=attributes.get(element);if(!saved){saved={};attributes.set(element,saved);}
      for(const key of ['aria-label','title','placeholder','label'])if(element.hasAttribute(key)){const current=element.getAttribute(key),old=saved[key],source=old&&current===old.translated?old.source:canonical(current),translated=t(source);saved[key]={source,translated};if(current!==translated)element.setAttribute(key,translated);}
      if(element.matches('[data-language]'))element.value=language;
      if(element.matches('[data-wiki]'))element.href='https://github.com/Bladestar2105/Minecraft-Ark/wiki/'+encodeURIComponent(element.dataset.wiki+(language==='de'?'-DE':''));
    }
  }
  function persist(){try{localStorage.setItem('ark_language',language);}catch{}document.cookie='ark_language='+language+'; Path=/; Max-Age=31536000; SameSite=Lax'+(location.protocol==='https:'?'; Secure':'');}
  function setLanguage(value){if(!['de','en'].includes(value))return;language=value;persist();document.documentElement.lang=language;apply();window.dispatchEvent(new CustomEvent('ark:language',{detail:{language}}));}
  const ready=Promise.all(['de','en'].map(async lang=>{const response=await fetch(new URL('locales/'+lang+'.json',base));if(!response.ok)throw new Error('Language dictionary unavailable');dictionaries[lang]=Object.freeze(await response.json());})).then(()=>{
    function pattern(source,value){const names=[];const regex=source.split(/(\{\w+\})/).map(part=>{if(/^\{\w+\}$/.test(part)){names.push(part.slice(1,-1));return '([\\s\\S]+?)';}return part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}).join('');return {regex:new RegExp('^'+regex+'$'),names,value};}
    for(const [source,value]of Object.entries(dictionaries.en)){if(source!==value)reverse.set(value,source);if(!/\{\w+\}/.test(source))continue;patterns.push(pattern(source,value));if(source!==value)reversePatterns.push(pattern(value,source));}
    document.documentElement.lang=language;persist();apply();
    const observer=new MutationObserver(records=>{for(const record of records){if(record.type==='characterData')translatedNode(record.target);else if(record.type==='attributes')apply(record.target);else for(const added of record.addedNodes){if(added.nodeType===Node.TEXT_NODE)translatedNode(added);else if(added.nodeType===Node.ELEMENT_NODE)apply(added);}}});
    observer.observe(document.documentElement,{subtree:true,childList:true,characterData:true,attributes:true,attributeFilter:['aria-label','title','placeholder','label']});
    document.addEventListener('change',event=>{if(event.target.matches('[data-language]'))setLanguage(event.target.value);});
  });
  window.ArkI18n={ready,t,setLanguage,get language(){return language;}};
})();
