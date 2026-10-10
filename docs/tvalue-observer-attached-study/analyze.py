from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==46
per=[];replays=0;qualified=0
for path in sorted(results):
 r=json.loads(path.read_text());label=path.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 if r['driver']=='cost':
  rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==64
  reference=Path('docs/tvalue-observer-fast-disabled/cost')/f"{r['size']}-0-on-slow-attached"
  assert (p/(label+'.native.txt')).read_bytes()==Path(str(reference)+'.native.txt').read_bytes()
  for workload,ops in [('setter',8192),('export',16)]:
   samples=[x for x in rows if x['workload']==workload];assert len(samples)==32 and all(int(x['operations'])==ops for x in samples)
   trace=p/(label+'.'+workload+'.trace.gz');stats=replay(trace);replays+=1
   assert trace.read_bytes()==Path(str(reference)+'.'+workload+'.trace.gz').read_bytes()
   per.append({**{k:r[k] for k in ('size','repetition','flag','mode')},'workload':workload,'ns':median(int(x['wall_ns'])/ops for x in samples),'bytes':median(int(x['allocated_bytes'])/ops for x in samples)})
 elif r['driver']=='consumer':
  reference=Path('docs/tvalue-observer-fast-disabled')/f"0-{r['flag']}-hooked"
  for suffix in ('.native-rows.txt','.diagnostics.txt','.log.gz'):
   assert (p/(label+suffix)).read_bytes()==Path(str(reference)+suffix).read_bytes()
  traces=list(p.glob(label+'.*.accepted.trace.gz'));assert len(traces)==7
  for trace in traces:
   assert trace.read_bytes()==Path(str(reference)+trace.name[len(label):]).read_bytes();qualified+=1
  assert not list(p.glob(label+'.root-clear*.trace*'))
 else:assert b'OBSERVER_SESSION_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
metrics=[]
for size in (32,128):
 for workload in ('setter','export'):
  row={'size':size,'workload':workload}
  for mode in ('slow','fast','direct'):
   cells=[x for x in per if (x['size'],x['workload'],x['mode'],x['flag'])==(size,workload,mode,'on')];assert len(cells)==6
   row[mode+'_ns']=median(x['ns'] for x in cells);row[mode+'_bytes']=median(x['bytes'] for x in cells)
  for mode in ('fast','direct'):
   ratios=[]
   for rep in range(6):
    cells={x['mode']:x['ns'] for x in per if (x['size'],x['workload'],x['flag'],x['repetition'])==(size,workload,'on',rep)}
    ratios.append(cells[mode]/cells['slow'])
   row[mode+'_slow_paired_ratio']=median(ratios);row[mode+'_ratio_range']=[min(ratios),max(ratios)]
  metrics.append(row)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
b=json.loads((p/'build-validation.json').read_text())
assert sha(p/'direct/org/kanger/TValueObservation.java')==b['source_sha256']
assert all(sha(Path('../build/tvalue-observer-attached-direct')/k)==v for k,v in b['class_sha256'].items())
assert replays==80 and qualified==21
for name in ('vanilla','missing-mind','missing-factory','missing-value'):
 assert json.loads((p/(name+'.wiring.json')).read_text())['exit_code']==0
 assert not (p/(name+'.err')).read_bytes()
 assert b'VANILLA_CONSUMER_REFUSAL_OK' in gzip.decompress((p/(name+'.log.gz')).read_bytes())
summary={'JVMs':50,'wiring_refusal_JVMs':4,'cost_JVMs':40,'qualification_JVMs':6,'batches':2560,'cost_trace_replays':replays,'prior_accepted_traces_identical':qualified,'native_controls_identical':True,'native_head':'2422f7f6d9e1af7608203b1566df64b5f29fe344','metrics':metrics,'production_changed':False,'direct_variant_adopted':False,'causality_established':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
print(json.dumps(summary,indent=2))
