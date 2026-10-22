from pathlib import Path
from statistics import median
import ast,csv,gzip,json,tarfile,hashlib
from gate import select_pair,check_gate,medians
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
class TraceBytes:
 def __init__(self,body):self.body=body
 def read_bytes(self):return gzip.compress(self.body,mtime=0)
files=sorted(p.glob('*.result.json'));assert len(files)==28,len(files)
per=[];traces=0;pairs=0;data={}
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 if not r['logging']:assert all(not any(s in x for s in ('LogCompilation','LogFile','Xlog')) for x in r['command'])
 native=(p/(label+'.native.txt')).read_bytes();assert native==(p/f"{r['profile']}-off-{r['rep']}-clean.native.txt").read_bytes();assert len(native.splitlines())==1792 and native.count(b'values=[{x=Tom}]')==512
 rows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.csv')).open())];qr=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.queries.csv')).open())];assert len(rows)==256 and len(qr)==1792
 assert [x['iteration'] for x in rows]==list(range(256))
 for i,row in enumerate(rows):
  queries=qr[i*7:(i+1)*7];assert [(q['iteration'],q['query']) for q in queries]==[(i,n) for n in range(7)]
  assert row['queries']==7 and row['query_wall_ns']==sum(q['wall_ns'] for q in queries) and row['query_allocated_bytes']==sum(q['allocated_bytes'] for q in queries)
  assert row['end_ns']-row['start_ns']==row['wall_ns']>=row['query_wall_ns']>0 and row['allocated_bytes']>=row['query_allocated_bytes']>=0
  for q in queries:assert q['wall_ns']>0 and q['allocated_bytes']>=0;row['q'+str(q['query'])]=q['wall_ns']
 data[label]=rows
 entry={k:v for k,v in r.items() if k!='command'};entry['label']=label;entry['last_48_descriptive']={key:median(row[key] for row in rows[208:]) for key in ('wall_ns','query_wall_ns','allocated_bytes','query_allocated_bytes',*['q'+str(q) for q in range(7)])};entry['variable_query_share_last_48']=median((row['q5']+row['q6'])/row['query_wall_ns'] for row in rows[208:]);entry['first_passing_gate']=next((c for c in range(64,209,16) if check_gate(rows,c)['passed']),None);per.append(entry)
 archive=p/(label+'.evidence.tar.gz')
 if r['mode'] in ('baseline','candidate'):
  with tarfile.open(archive,'r:gz') as tar:
   members={m.name for m in tar.getmembers()};assert len(members)==512
   reference=tarfile.open(p/(label.replace('-candidate','-baseline')+'.evidence.tar.gz'),'r:gz') if r['mode']=='candidate' else None
   try:
    for i in range(256):
     tn=f'{label}.{i}.trace';en=f'{label}.{i}.events.txt';body=tar.extractfile(tn).read();events=tar.extractfile(en).read();replay(TraceBytes(body));traces+=1;assert b'metadata=' in events and b'add=' in events
     if reference:
      assert body==reference.extractfile(tn.replace('-candidate','-baseline')).read();assert events==reference.extractfile(en.replace('-candidate','-baseline')).read();pairs+=1
   finally:
    if reference:reference.close()
 else:assert not archive.exists()
results=[]
for profile in ('default','batch'):
 decisions=[]
 for rep in range(3):
  labels=[f'{profile}-off-{rep}-{mode}' for mode in ('baseline','candidate')];b,c=[data[x] for x in labels];decision=select_pair(b,c);decision.update(rep=rep,profile=profile)
  if decision['status']=='stable':
   a,end=decision['cutoff'],decision['end'];decision['candidate_baseline_query_ratio']=median(r['query_wall_ns'] for r in c[a:end])/median(r['query_wall_ns'] for r in b[a:end]);decision['paired_cycle_bytes_saved']=median(r['allocated_bytes'] for r in b[a:end])-median(r['allocated_bytes'] for r in c[a:end])
  decisions.append(decision)
 result={'profile':profile,'pairs':decisions,'all_three_stable':all(x['status']=='stable' for x in decisions)}
 if result['all_three_stable']:result['qualified_paired_query_median_ratio']=median(x['candidate_baseline_query_ratio'] for x in decisions)
 results.append(result)
assert traces==4096 and pairs==2048,(traces,pairs)
build=json.loads((p/'build-validation.json').read_text());sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();assert sha(p/'plan.json')==build['plan_sha256'];assert sha(p/'ResidentStabilityRunner.java')==build['source_sha256'];assert all(sha(f)==h for f,h in {**build['class_sha256'],**build['jar_sha256']}.items())
summary={'JVMs':28,'timing_JVMs':24,'logging_JVMs':4,'cycles_per_JVM':256,'queries':28*256*7,'trace_replays':traces,'identical_trace_pairs':pairs,'variable_rows_checked':28*256*2,'flags':'off only','plan_sha256':build['plan_sha256'],'decisions':results,'production_changed':False,'steady_state_claim':False,'native_base_extended':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM.json').write_text(json.dumps(per,indent=2)+'\n')
for result in results:print(result['profile'],[(x['rep'],x['status'],x['cutoff']) for x in result['pairs']],result.get('qualified_paired_query_median_ratio'))
print('STABILITY_ANALYSIS_OK',traces,pairs)
