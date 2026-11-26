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
out.parent.mkdir(parents=True,exist_ok=True)
out.write_text(html,encoding='utf-8')
print(f'Built {out} ({out.stat().st_size} bytes), {len(data.get("phrases",{}))} exact phrase substitutions')
