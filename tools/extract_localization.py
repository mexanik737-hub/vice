#!/usr/bin/env python3
"""Extract candidate visible English strings with passage context; conservative inventory only."""
from __future__ import annotations
import hashlib, html, json, re
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'upstream/original/viceburg_mod_clean.html'
OUT=ROOT/'localization/inventory/candidates.jsonl'
class StoryParser(HTMLParser):
 def __init__(self): super().__init__(convert_charrefs=True); self.in_passage=False; self.name=''; self.buf=[]; self.items=[]
 def handle_starttag(self,tag,attrs):
  if tag.lower()=='tw-passagedata':
   self.in_passage=True; self.buf=[]; self.name=dict(attrs).get('name','')
 def handle_data(self,data):
  if self.in_passage:self.buf.append(data)
 def handle_endtag(self,tag):
  if tag.lower()=='tw-passagedata' and self.in_passage:self.items.append((self.name,''.join(self.buf)));self.in_passage=False

def quoted_macro_arguments(body):
 """Collect quoted output labels and dialogue from visible SugarCube macros."""
 names=r'(?:button|link|ChatMessage|Chat|say|tooltip|prompt)'
 for macro in re.finditer(r'<<'+names+r'\b([\s\S]*?)>>',body,re.I):
  value=macro.group(1);i=0
  while i<len(value):
   if value[i] not in "\"'":
    i+=1;continue
   quote=value[i];i+=1;start=i;out=[]
   while i<len(value):
    ch=value[i]
    if ch=='\\' and i+1<len(value):
     nxt=value[i+1]
     if nxt==quote or nxt=='\\':out.append(nxt);i+=2;continue
     if nxt=='\n':i+=2;continue
     if nxt=='\r' and i+2<len(value) and value[i+2]=='\n':i+=3;continue
     out.extend((ch,nxt));i+=2;continue
    if ch==quote:break
    out.append(ch);i+=1
   yield ''.join(out)
   i+=1
p=StoryParser();p.feed(SRC.read_text(encoding='utf-8',errors='replace'))
rows=[]
for name,body in p.items:
 body=re.sub(r'/\*[\s\S]*?\*/|<!--[\s\S]*?-->',' ',body)
 # Capture ordinary rendered text and common labels passed into output-producing SugarCube macros.
 candidates=[]
 stripped=re.sub(r'<<[\s\S]*?>>',' ',body)
 stripped=re.sub(r'<[^>]+>',' ',stripped)
 stripped=html.unescape(stripped)
 candidates.extend(re.split(r'(?<=[.!?])\s+|\n+',stripped))
 candidates.extend(quoted_macro_arguments(body))
 seen=set()
 for text in candidates:
  text=' '.join(html.unescape(text).split()).strip(' \t\r\n|')
  if len(text)<3 or text in seen or not re.search(r'[A-Za-z]{2}',text):continue
  seen.add(text)
  if re.search(r'\b(?:the|you|your|is|are|was|were|with|and|for|from|this|that|have|has|what|when|where|how|not|can|will|would|could|should|player|npc|click|save|load)\b',text,re.I) or len(text)>36:
   rows.append({'passage':name,'source':text,'source_sha256':hashlib.sha256(text.encode()).hexdigest()})
OUT.parent.mkdir(parents=True,exist_ok=True)
with OUT.open('w',encoding='utf-8') as f:
 for row in rows:f.write(json.dumps(row,ensure_ascii=False)+'\n')
from collections import Counter
by=Counter(r['passage'] for r in rows)
summary={'source':str(SRC.relative_to(ROOT)),'sha256':hashlib.sha256(SRC.read_bytes()).hexdigest(),'passages':len(p.items),'candidate_rows':len(rows),'passages_with_candidates':len(by),'top_passages':by.most_common(20),'warning':'Conservative candidate extraction; includes false positives and misses dynamically composed/code-generated strings. Review before translation.'}
(ROOT/'localization/inventory/summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
progress=ROOT/'localization/progress.json'; data=json.loads(progress.read_text()); data['source_sha256']=summary['sha256']; data['inventory']=summary; progress.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(summary,ensure_ascii=False,indent=2))
