"use strict";
// Original images and their metadata remain in this browser; only a SkinEditor leaves this dialog.
(() => {
  function mount(root,{available,busy,onCreate}) {
    const $=id=>root.querySelector('#'+id), t=text=>ArkI18n.t(text);
    const views=['front','back'], parts=['head','body','right_arm','left_arm','right_leg','left_leg'];
    const sources=Object.fromEntries(views.map(view=>[view,{version:0,pending:null,loading:false,image:null,regions:null,point:{x:50,y:50}}]));
    let candidate=null,frame=null,dragging=null;
    const active=()=>sources[$('photo-view').value];
    const loading=()=>views.some(view=>sources[view].loading);
    const editable=()=>available()&&!busy()&&Boolean(active().image);
    function status(text,error=false) { $('photo-status').textContent=t(text);$('photo-status').classList.toggle('error',error); }
    function updateAvailability() {
      if(!available()&&!$('photo-panel').hidden)clear();
      const disabled=!available()||busy();
      $('skin-photo-open').disabled=disabled;
      for(const view of views) {
        $('photo-'+view+'-file').disabled=disabled;
        $('photo-'+view+'-state').textContent=t(sources[view].loading?'Bild wird lokal geladen …':sources[view].image?'Bild geladen.':'Kein Bild geladen.');
      }
      $('photo-view').disabled=disabled;
      $('photo-fields').disabled=disabled||!active().image;
      $('photo-apply').disabled=disabled||loading()||!candidate;
      $('photo-rear-hint').hidden=Boolean(sources.back.image);
    }
    function releasePending(source) {
      if(!source.pending)return;
      source.pending.image.removeAttribute('src');URL.revokeObjectURL(source.pending.url);source.pending=null;
    }
    function clearCanvas(id,release=false) {
      const canvas=$(id);canvas.getContext('2d').clearRect(0,0,canvas.width,canvas.height);
      if(release)canvas.width=canvas.height=1;
    }
    function clear() {
      for(const view of views) {
        const source=sources[view];source.version++;releasePending(source);
        source.loading=false;source.image=null;source.regions=null;source.point.x=source.point.y=50;
        $('photo-'+view+'-file').value='';$('photo-'+view+'-state').textContent=t('Kein Bild geladen.');
      }
      if(frame!==null)cancelAnimationFrame(frame);frame=null;candidate=null;dragging=null;
      $('photo-panel').hidden=true;status('');
      for(const input of root.querySelectorAll('input[type=range],input[type=color]'))input.value=input.defaultValue;
      $('photo-model').value='classic';$('photo-mode').value='crop';$('photo-view').value='front';$('photo-part').value='head';
      for(const id of ['photo-canvas','photo-front','photo-back'])clearCanvas(id,id==='photo-canvas');
      $('photo-rear-hint').hidden=false;$('photo-fields').disabled=true;$('photo-apply').disabled=true;
    }
    function syncRegion() {
      const source=active();if(!source.image)return;
      const rect=source.regions[$('photo-part').value],{width,height}=source.image;
      const maxima={x:width-rect.width,y:height-rect.height,width:width-rect.x,height:height-rect.y};
      for(const key of ['x','y','width','height']) {
        const input=$('photo-'+key);input.min=key==='x'||key==='y'?0:1;input.max=maxima[key];input.step=1;input.value=rect[key];
      }
    }
    function colors() { return Object.fromEntries(['skin','hair','shirt','trousers'].map(key=>[key,$('photo-'+key).value])); }
    function createSkin() {
      return ArkPhotoSkin.createSkin({front:{image:sources.front.image,regions:sources.front.regions},back:sources.back.image?{image:sources.back.image,regions:sources.back.regions}:null},colors(),$('photo-model').value);
    }
    function render() {
      if(!available())return;
      const source=active(),canvas=$('photo-canvas');
      if(source.image) {
        canvas.width=source.image.width;canvas.height=source.image.height;
        const ctx=canvas.getContext('2d');ctx.putImageData(source.image,0,0);
        ctx.lineWidth=Math.max(1,canvas.width/200);
        for(const part of [...parts.filter(part=>part!==$('photo-part').value),$('photo-part').value]) {
          const rect=source.regions[part];ctx.strokeStyle=part===$('photo-part').value?'#ffffff':'#00c8ff';
          ctx.strokeRect(rect.x,rect.y,rect.width,rect.height);
        }
        const x=source.point.x*(canvas.width-1)/100,y=source.point.y*(canvas.height-1)/100,r=Math.max(3,canvas.width/80);
        ctx.strokeStyle='#ffffff';ctx.beginPath();ctx.moveTo(x-r,y);ctx.lineTo(x+r,y);ctx.moveTo(x,y-r);ctx.lineTo(x,y+r);ctx.stroke();
      } else clearCanvas('photo-canvas',true);
      candidate=sources.front.image?createSkin():null;
      if(candidate) { candidate.drawPreview($('photo-front'),'front');candidate.drawPreview($('photo-back'),'back'); }
      else for(const id of ['photo-front','photo-back'])clearCanvas(id);
      updateAvailability();
    }
    function queueRender() { if(frame===null)frame=requestAnimationFrame(()=>{frame=null;render();}); }
    async function load(view,file) {
      if(!file)return;
      const source=sources[view],version=++source.version;releasePending(source);
      source.loading=true;source.image=null;source.regions=null;dragging=null;render();
      status('Bild wird lokal geladen …');
      try {
        if(!file.size||file.size>ArkPhotoSkin.limits.bytes)throw new Error('Bitte ein JPEG-, PNG- oder WebP-Bild mit höchstens 8 MiB wählen.');
        const bytes=new Uint8Array(await file.slice(0,12).arrayBuffer());
        if(version!==source.version||!available())return;
        const mime=bytes.length>=8&&[137,80,78,71,13,10,26,10].every((n,i)=>bytes[i]===n)?'image/png':bytes[0]===255&&bytes[1]===216&&bytes[2]===255?'image/jpeg':String.fromCharCode(...bytes.slice(0,4))==='RIFF'&&String.fromCharCode(...bytes.slice(8,12))==='WEBP'?'image/webp':null;
        if(!mime)throw new Error('Bitte ein gültiges JPEG-, PNG- oder WebP-Bild wählen.');
        const image=new Image(),url=URL.createObjectURL(file.slice(0,file.size,mime));file=null;
        source.pending={image,url};image.src=url;await image.decode();
        if(version!==source.version||!available())return;
        const {naturalWidth:w,naturalHeight:h}=image;
        if(!w||!h||w>ArkPhotoSkin.limits.side||h>ArkPhotoSkin.limits.side||w*h>ArkPhotoSkin.limits.pixels)throw new Error('Das Bild darf höchstens 16 Megapixel und 8192 Pixel pro Seite haben.');
        const work=document.createElement('canvas'),scale=Math.min(1,1024/Math.max(w,h));
        work.width=Math.max(1,Math.round(w*scale));work.height=Math.max(1,Math.round(h*scale));
        try {
          const ctx=work.getContext('2d',{willReadFrequently:true});ctx.drawImage(image,0,0,work.width,work.height);
          source.image=ctx.getImageData(0,0,work.width,work.height);
          source.regions=ArkPhotoSkin.defaultRegions(work.width,work.height,view);
        } finally { work.width=work.height=0; }
        status('Bild lokal geladen. Körperbereiche ausrichten.');syncRegion();render();
      } catch(error) {
        if(version!==source.version||!available())return;
        source.image=null;source.regions=null;
        status(['Bitte ein JPEG-, PNG- oder WebP-Bild mit höchstens 8 MiB wählen.','Bitte ein gültiges JPEG-, PNG- oder WebP-Bild wählen.','Das Bild darf höchstens 16 Megapixel und 8192 Pixel pro Seite haben.'].includes(error.message)?error.message:'Das Bild konnte nicht gelesen werden. Bitte ein anderes Bild wählen.',true);
        render();
      } finally {
        if(version===source.version) { source.loading=false;releasePending(source);updateAvailability(); }
      }
    }
    function usePoint(offset=null) {
      if(!editable())return;
      const source=active(),{image,point}=source,mode=$('photo-mode').value;
      if(mode==='crop') {
        const rect=source.regions[$('photo-part').value];
        rect.x=Math.max(0,Math.min(image.width-rect.width,Math.round(point.x*(image.width-1)/100-(offset?.x??rect.width/2))));
        rect.y=Math.max(0,Math.min(image.height-rect.height,Math.round(point.y*(image.height-1)/100-(offset?.y??rect.height/2))));
        syncRegion();
      } else $('photo-'+mode).value=ArkPhotoSkin.sampleColor(image,point.x*(image.width-1)/100,point.y*(image.height-1)/100);
      queueRender();
    }
    function pointer(event) {
      const b=$('photo-canvas').getBoundingClientRect(),point=active().point;
      point.x=Math.max(0,Math.min(100,(event.clientX-b.left)*100/b.width));point.y=Math.max(0,Math.min(100,(event.clientY-b.top)*100/b.height));
    }
    $('skin-photo-open').addEventListener('click',()=>{if(!available()||busy())return;clear();$('photo-panel').hidden=false;updateAvailability();$('photo-front-file').focus();});
    for(const view of views)$('photo-'+view+'-file').addEventListener('change',()=>{const input=$('photo-'+view+'-file'),file=input.files[0];input.value='';if(available()&&!busy())load(view,file);});
    $('photo-cancel').addEventListener('click',()=>{clear();$('skin-photo-open').focus();});
    for(const id of ['photo-view','photo-part'])$(id).addEventListener('input',()=>{dragging=null;syncRegion();updateAvailability();queueRender();});
    for(const key of ['x','y','width','height'])$('photo-'+key).addEventListener('input',()=>{
      if(!editable())return;
      const input=$('photo-'+key),rect=active().regions[$('photo-part').value];
      rect[key]=Math.max(Number(input.min),Math.min(Number(input.max),Math.round(Number(input.value))));syncRegion();queueRender();
    });
    for(const input of root.querySelectorAll('input[type=color],#photo-model,#photo-mode'))input.addEventListener('input',queueRender);
    $('photo-canvas').addEventListener('pointerdown',event=>{
      if(!editable()||event.button!==0)return;
      event.preventDefault();$('photo-canvas').focus();pointer(event);
      const {point,image,regions}=active(),rect=regions[$('photo-part').value],x=point.x*(image.width-1)/100,y=point.y*(image.height-1)/100;
      dragging=x>=rect.x&&x<=rect.x+rect.width&&y>=rect.y&&y<=rect.y+rect.height?{x:x-rect.x,y:y-rect.y}:{x:rect.width/2,y:rect.height/2};
      $('photo-canvas').setPointerCapture(event.pointerId);usePoint(dragging);
    });
    $('photo-canvas').addEventListener('pointermove',event=>{if(dragging&&editable()){pointer(event);usePoint(dragging);}});
    for(const type of ['pointerup','pointercancel','lostpointercapture'])$('photo-canvas').addEventListener(type,()=>{dragging=null;});
    $('photo-canvas').addEventListener('keydown',event=>{
      if(!editable())return;
      const point=active().point,delta={ArrowLeft:['x',-1],ArrowRight:['x',1],ArrowUp:['y',-1],ArrowDown:['y',1]}[event.key];
      if(delta){event.preventDefault();point[delta[0]]=Math.max(0,Math.min(100,point[delta[0]]+delta[1]));queueRender();}
      else if(['Enter',' '].includes(event.key)){event.preventDefault();usePoint();}
    });
    $('photo-apply').addEventListener('click',()=>{if(!sources.front.image||loading()||!available()||busy())return;const editor=createSkin();clear();onCreate(editor);});
    window.addEventListener('pagehide',clear);
    clear();updateAvailability();
    return {clear,updateAvailability,hasDirty:()=>!$('photo-panel').hidden&&views.some(view=>Boolean(sources[view].image||sources[view].loading))};
  }
  window.ArkPhotoImport={mount};
})();
