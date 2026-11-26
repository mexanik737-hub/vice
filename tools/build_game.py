#!/usr/bin/env python3
"""Build the offline HTML by injecting progressive Russian DOM text substitutions."""
from __future__ import annotations
import json, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / 'upstream/original/viceburg_mod_clean.html'
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / 'android/app/src/main/assets/game/index.html'
catalog = ROOT / 'localization/catalog/ru.json'
html = source.read_text(encoding='utf-8')
data = json.loads(catalog.read_text(encoding='utf-8'))
mapping = json.dumps(data.get('phrases', {}), ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
templates = json.dumps(data.get('templates', []), ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
scoped = json.dumps(data.get('scoped', []), ensure_ascii=False, separators=(',', ':')).replace('</', '<\\/')
script = r'''<script id="viceburg-ru-layer">
(()=>{'use strict';const map=__PHRASES__,templates=__TEMPLATES__,scoped=__SCOPED__;
const skip='SCRIPT,STYLE,TEXTAREA,CODE,PRE,TW-STORYDATA,TW-PASSAGEDATA,#store-area';
const compiled=templates.map(t=>{const keys=[];const parts=t.source.split(/(\{\{[A-Za-z0-9_]+\}\})/);const pattern=parts.map(p=>{if(/^\{\{[A-Za-z0-9_]+\}\}$/.test(p)){keys.push(p.slice(2,-2));return '([\\s\\S]+?)';}return p.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');}).join('');return {t,re:new RegExp('^'+pattern+'$'),keys};});
const fragments=Object.entries(map).filter(([source])=>source.length>=24).sort((a,b)=>b[0].length-a[0].length).map(([source,target])=>{const escaped=source.replace(/[.*+?^${}()|[\]\\]/g,'\\$&').replace(/\s+/g,'\\s+');return {target,re:new RegExp('(^|[^A-Za-z])('+escaped+')(?=$|[^A-Za-z])','g')};});
function translated(value,element=null){if(element)for(const item of scoped)if(item.source===value&&element.closest(item.selector))return item.target;if(Object.prototype.hasOwnProperty.call(map,value))return map[value];for(const {t,re,keys} of compiled){const m=value.match(re);if(!m)continue;let out=t.target;keys.forEach((k,i)=>{const raw=m[i+1],value=t.values&&t.values[k]&&Object.prototype.hasOwnProperty.call(t.values[k],raw)?t.values[k][raw]:raw;out=out.replaceAll('{{'+k+'}}',value);});return out;}let out=value,changed=false;for(const f of fragments){const next=out.replace(f.re,(_,before)=>{changed=true;return before+f.target;});out=next;}return changed?out:null;}
function translateText(node){const p=node.parentElement;if(!p||p.closest(skip))return;const raw=node.nodeValue,trimmed=raw.trim();if(!trimmed)return;const result=translated(trimmed,p);if(result===null||result===trimmed)return;const start=raw.indexOf(trimmed),end=start+trimmed.length;node.nodeValue=raw.slice(0,start)+result+raw.slice(end);}
const importMarker='__viceburg_import_pending';if(localStorage.getItem(importMarker)==='1'){localStorage.removeItem(importMarker);document.addEventListener(':storyready',()=>{setTimeout(()=>{try{if(typeof Save==='undefined')return;const auto=Save.browser&&Save.browser.auto?Save.browser.auto:Save.autosave;if(!auto||!auto.has())return;const result=auto.load(Save.browser&&Save.browser.auto===auto?0:undefined);if(result&&typeof result.catch==='function')result.catch(()=>{});}catch(_){}},0);},{once:true});}
function translate(root){if(!root||root.nodeType!==Node.ELEMENT_NODE||root.closest(skip))return;const w=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);let n;while((n=w.nextNode()))translateText(n);if(root.matches('[placeholder],[title],[alt],[aria-label],[data-faq-label]'))translateAttributes(root);for(const element of root.querySelectorAll('[placeholder],[title],[alt],[aria-label],[data-faq-label]'))translateAttributes(element);}
function translateAttributes(element){for(const a of ['placeholder','title','alt','aria-label','data-faq-label']){const v=element.getAttribute(a);if(v){const result=translated(v,element);if(result!==null)element.setAttribute(a,result);}}}
// Never traverse the embedded Twine source at startup: it contains the entire game and is hidden.
for(const root of document.querySelectorAll('#passages,#ui-bar,#ui-dialog'))translate(root);
const pending=new Set();let frame=0;function flush(){frame=0;const batch=Array.from(pending);pending.clear();for(const item of batch){if(item.nodeType===Node.TEXT_NODE)translateText(item);else translate(item);}}
new MutationObserver(records=>{for(const r of records){if(r.type==='characterData')pending.add(r.target);else for(const n of r.addedNodes)if(n.nodeType===Node.ELEMENT_NODE||n.nodeType===Node.TEXT_NODE)pending.add(n);}if(pending.size&&!frame)frame=requestAnimationFrame(flush);}).observe(document.documentElement,{subtree:true,childList:true,characterData:true});
})();
</script>'''.replace('__PHRASES__', mapping).replace('__TEMPLATES__', templates).replace('__SCOPED__', scoped)
if 'id="viceburg-ru-layer"' not in html:
 marker='</body>'
 if marker not in html: raise SystemExit('Expected closing </body> not found')
 html=html.replace(marker,script+'\n'+marker,1)
tools_script = r'''<script id="viceburg-android-tools">
(()=>{'use strict';
const cheat=/cheat|чит/i;
const style=document.createElement('style');
style.textContent='.vb-app-tools{box-sizing:border-box;width:min(100% - 24px,560px);margin:18px auto 24px;padding:12px;border:1px solid rgba(247,174,59,.58);border-radius:16px;background:linear-gradient(145deg,rgba(35,32,52,.97),rgba(20,19,29,.97));box-shadow:0 8px 26px rgba(0,0,0,.3);color:#f8f4ed;font:14px/1.35 system-ui,sans-serif}'+
'.vb-app-tools *{box-sizing:border-box}'+
'.vb-app-tools__toggle{display:flex;align-items:center;gap:10px;width:100%;min-height:44px;padding:8px 10px;border:0;border-radius:11px;background:transparent;color:inherit;text-align:left;font:inherit;font-weight:700;cursor:pointer}'+
'.vb-app-tools__brand{color:#f7ae3b;font-size:11px;letter-spacing:.08em}'+
'.vb-app-tools__title{flex:1}'+
'.vb-app-tools__status{color:#c5bdc8;font-size:12px;font-weight:500}'+
'.vb-app-tools__chevron{color:#f7ae3b;font-size:18px}'+
'.vb-app-tools__actions{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;padding-top:10px}'+
'.vb-app-tools__actions[hidden]{display:none}'+
'.vb-app-tools__action{min-height:42px;padding:8px 10px;border:1px solid rgba(247,174,59,.42);border-radius:10px;background:rgba(247,174,59,.11);color:#fff;font:inherit;font-weight:600;cursor:pointer}'+
'.vb-app-tools__action:active{background:rgba(247,174,59,.26)}'+
'@media(max-width:420px){.vb-app-tools__actions{grid-template-columns:1fr}}';
document.head.appendChild(style);
const panel=document.createElement('section');
panel.className='vb-app-tools';
panel.setAttribute('aria-label','Инструменты приложения Viceburg');
panel.innerHTML='<button type="button" class="vb-app-tools__toggle" aria-expanded="false"><span class="vb-app-tools__brand">VICEBURG</span><span class="vb-app-tools__title">Файлы и сохранения</span><span class="vb-app-tools__status" aria-live="polite"></span><span class="vb-app-tools__chevron" aria-hidden="true">⌄</span></button><div class="vb-app-tools__actions" hidden><button type="button" class="vb-app-tools__action" data-vb-action="media">Папка Media</button><button type="button" class="vb-app-tools__action" data-vb-action="export">Экспорт</button><button type="button" class="vb-app-tools__action" data-vb-action="import">Импорт</button></div>';
const toggle=panel.querySelector('.vb-app-tools__toggle');
const actions=panel.querySelector('.vb-app-tools__actions');
const status=panel.querySelector('.vb-app-tools__status');
function updateStatus(){const value=window.ViceburgAndroid?window.ViceburgAndroid.mediaStatus():'Доступно в приложении';if(status.textContent!==value)status.textContent=value;}
function visible(el){return !!el&&el.getClientRects().length>0;}
function passageInfo(){
 const node=document.querySelector('#passages .passage:last-child');
 const state=window.SugarCube&&window.SugarCube.State?window.SugarCube.State:window.State;
 const name=state&&state.passage?String(state.passage):String(node&&(node.getAttribute('data-passage')||node.id)||'').replace(/^passage-/i,'');
 return {node,name};
}
function findTarget(){
 const {node,name}=passageInfo();
 if(/^start$/i.test(name))return node;
 if(cheat.test(name))return node;
 const dialog=document.querySelector('#ui-dialog-body');
 if(visible(dialog)){
  const heading=dialog.querySelector('h1,h2,h3,[role="heading"],.title');
  const headingText=heading?heading.textContent:'';
  if(cheat.test((headingText||'')+' '+(dialog.getAttribute('data-passage')||''))||cheat.test(dialog.textContent||''))return dialog;
 }
 return null;
}
function refresh(){
 const target=findTarget();
 if(!target){panel.remove();return;}
 if(panel.parentElement!==target)target.appendChild(panel);
 updateStatus();
}
toggle.addEventListener('click',()=>{const expanded=toggle.getAttribute('aria-expanded')==='true';toggle.setAttribute('aria-expanded',String(!expanded));actions.hidden=expanded;});
panel.addEventListener('click',event=>{
 const button=event.target.closest('[data-vb-action]');
 if(!button)return;
 const bridge=window.ViceburgAndroid;
 if(!bridge){status.textContent='Откройте экран в APK';return;}
 const action=button.getAttribute('data-vb-action');
 if(action==='media')bridge.selectMedia();
 if(action==='export')bridge.requestExportSave();
 if(action==='import')bridge.requestImportSave();
});
window.addEventListener('viceburg-media-change',updateStatus);
document.addEventListener(':passageend',refresh);
document.addEventListener(':dialogopen',refresh);
document.addEventListener(':dialogclose',refresh);
new MutationObserver(()=>{if(!window.__vbToolsRefreshQueued){window.__vbToolsRefreshQueued=true;requestAnimationFrame(()=>{window.__vbToolsRefreshQueued=false;refresh();});}}).observe(document.body,{subtree:true,childList:true,characterData:true});
refresh();
})();</script>'''
if 'id="viceburg-android-tools"' not in html:
 marker='</body>'
 if marker not in html: raise SystemExit('Expected closing </body> not found')
 html=html.replace(marker,tools_script+'\n'+marker,1)
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(html,encoding='utf-8')
print(f'Built {out} ({out.stat().st_size} bytes), {len(data.get("phrases",{}))} exact phrase substitutions')
