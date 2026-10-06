"""Score every source's Boolean decision, lifecycle checks, and timing scope."""
from pathlib import Path
import base64,gzip,json,re,statistics
root=Path(__file__).parent
son=json.loads((root/'son-expected.json').read_text())
keys=('compiled','collision','answer','accepted')
def value(v):return {'true':True,'false':False,'null':None}[v]
def expected(label):
 if label.startswith('son'):return son
 answers=[True,False,None] if label!='external-other' else [None,None,None]
 return {s:dict(compiled=True,collision=False,answer=a,accepted=a is not None) for s,a in zip(['!seed(item);','!~target(item);','!unrelated(item);'],answers)}
files={}
for p in sorted(root.glob('*.log.gz')):
 lines=gzip.decompress(p.read_bytes()).decode().splitlines()
 cases={};timings={};markers={};accepted={}
 for line in lines:
  if line.startswith('ROW '):
   fields=line.split();label=fields[1];index=int(fields[2]);kv=dict(x.split('=',1) for x in fields[3:]);source=base64.b64decode(kv.pop('source')).decode()
   cases.setdefault(label,[]).append({'index':index,'source':source,**{k:value(kv[k]) for k in keys},'negative':kv['negative'],'positive':kv['positive']})
  elif line.startswith('CASE_OK '):
   fields=line.split();markers[fields[1]]={k:int(v) for k,v in (x.split('=') for x in fields[2:])}
  elif line.startswith('ACCEPTED '):
   _,label,text=line.split(' ',2);accepted[label]=text
  elif line.startswith('TIMING '):
   fields=line.split();timings[fields[1]]={k:int(v) for k,v in (x.split('=') for x in fields[2:])}
 final=[x for x in lines if x.startswith('PREPARED_QUERY_STUDY_OK ')]
 assert len(final)==1 and cases.keys()==markers.keys()==accepted.keys(),p
 last=dict(x.split('=') for x in final[0].split()[1:]);mode=last['mode'];comparison={}
 for label,rows in cases.items():
  oracle=expected(label);sources=[r['source'] for r in rows]
  assert len(sources)==len(set(sources)) and [r['index'] for r in rows]==list(range(len(rows))),p
  assert set(sources)==(set(['!$y son(John,y);']) if '-focus.' in p.name else set(oracle)),p
  admit=sorted(r['source'] for r in rows if r['accepted'])
  assert accepted[label]=='['+', '.join(admit)+']',p
  assert markers[label]==dict(rows=len(rows),accepted=len(admit),raw=18 if label.startswith('son') else 1,reservations=0),p
  differences=[{'source':r['source'],'expected':oracle[r['source']],'actual':{k:r[k] for k in keys}} for r in rows if {k:r[k] for k in keys}!=oracle[r['source']]]
  if mode in ('exact','full'):assert not differences,(p,label,differences)
  elif mode=='prepared':assert {d['source'] for d in differences}==({'!$y son(John,y);'} if label.startswith('son') else set()),(p,label,differences)
  elif mode=='compiled':assert {d['source'] for d in differences}=={'!$y son(John,y);','?$y son(John,y);'},(p,label,differences)
  comparison[label]={'rows':len(rows),'accepted':admit,'differences':differences,'answers':{str(a):sum(r['answer']==a for r in rows if r['compiled'] and not r['collision']) for a in (True,False,None)},'collision':sum(r['collision'] for r in rows),'order':sources}
 files[p.name]={'mode':mode,'checks':int(last['checks']),'cases':comparison,'timings':timings}
for mode in ('exact','full'):
 normal=files['son-'+mode+'.log.gz']['cases']['son']['order']
 reverse=files['son-'+mode+'-reverse.log.gz']['cases']['son']['order']
 assert reverse==normal[::-1]
required={'small-exact.log.gz','small-prepared.log.gz','small-full.log.gz',*(f'son-{m}.log.gz' for m in ('exact','prepared','compiled','full')),*(f'son-{m}-focus.log.gz' for m in ('exact','prepared','full')),*(f'son-{m}-reverse.log.gz' for m in ('exact','full')),*(f'son-{m}-{t}.log.gz' for m,t in (('exact','e1'),('full','f1'),('full','f2'),('exact','e2')))}
assert files.keys()==required, (files.keys(),required)
summary={}
for tag,mode in [('e1','exact'),('f1','full'),('f2','full'),('e2','exact')]:
 name='son-'+mode+'-'+tag+'.log.gz'
 if name not in files:continue
 f=files[name];assert list(f['timings'])==['son-'+str(i) for i in range(6)]
 metrics={}
 for key in ('cpu_ns','wall_ns','allocation_bytes'):
  samples=[f['timings']['son-'+str(i)][key] for i in range(6)]
  assert all(s>0 for s in samples)
  metrics[key]={'all_samples':samples,'warm_mean':statistics.mean(samples[2:]),'warm_min':min(samples[2:]),'warm_max':max(samples[2:])}
 summary[tag]=metrics
pairs={}
if len(summary)==4:
 for e,f in [('e1','f1'),('e2','f2')]:
  pairs[e+'-'+f]={k:summary[f][k]['warm_mean']/summary[e][k]['warm_mean'] for k in ('cpu_ns','wall_ns','allocation_bytes')}
class_comparison=json.loads((root/'class-comparison.json').read_text());assert class_comparison['shared_classes']==651 and class_comparison['changed']==[]
result={'base':class_comparison['base'],'shared_baseline_classes_identical':651,'logs':files,'workflow_timing':summary,'full_over_exact_warm_mean_ratios':pairs,'scope':'template preparation + candidate validation + relevance forks + template release; excludes initial query, candidate pool expansion and final verification; main-thread CPU/allocation; first two of six samples excluded'}
(root/'analysis.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({'logs':len(files),'cases':sum(len(f['cases']) for f in files.values()),'timing':summary,'ratios':pairs},indent=2))
