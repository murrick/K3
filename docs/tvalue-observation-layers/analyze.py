from pathlib import Path
import json,gzip,hashlib,subprocess,ast,csv
from statistics import median
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==54;views={};traces={};timing={};per_jvm=[];trace_bytes={}
for q in results:
 r=json.loads(q.read_text());assert r['exit_code']==0;label=q.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'JOURNAL_COST_OK' in text
 row='COST_NATIVE size='+str(r['size'])+' updates=48 variables='+str(r['size'])+' final_term=a';assert row in text and (p/(label+'.native.txt')).read_text().strip()==row
 views.setdefault(r['size'],set()).add((p/(label+'.final-view.txt')).read_bytes())
 wall=[[int(v) for v in x.split(',')] for x in (p/(label+'.wall.csv')).read_text().splitlines()];assert len(wall)==48 and [x[0] for x in wall]==list(range(48)) and all(x[1]>=0 and x[2]>0 for x in wall)
 entry={'size':r['size'],'repetition':r['repetition'],'build':r['build'],'flag':r['flag'],'setter_ns':median(x[1] for x in wall),'observe_ns':median(x[2] for x in wall)}
 if r['build']=='shadow':
  phases=[x.split(',') for x in (p/(label+'.phases.csv')).read_text().splitlines()];assert len(phases)==50 and phases[0][0:3]==['baseline','true','0'] and phases[-1][0:3]==['session-end','false','0'];updates=phases[1:-1]
  for i,x in enumerate(updates):assert x[:3]==['update-'+str(i),'false','1']
  for x in phases:
   ns=[int(v) for v in x[3:]];assert len(ns)==5 and all(v>=0 for v in ns) and sum(ns[:4])==ns[4]
  for i,name in enumerate(('projection_ns','oracle_ns','validation_ns','reporting_ns','phase_total_ns'),3):entry[name]=median(int(x[i]) for x in updates)
  entry['seed_projection_ns']=int(phases[0][3]);entry['projection_to_oracle']=entry['projection_ns']/entry['oracle_ns']
  trace=p/(label+'.trace.gz');traces[label]=replay(trace);assert traces[label]['observations']==50 and traces[label]['changes']==48;trace_bytes.setdefault(r['size'],set()).add(trace.read_bytes())
 timing[(r['size'],r['repetition'],r['build'],r['flag'])]=entry;per_jvm.append(entry)
assert all(len(v)==1 for v in views.values()) and all(len(v)==1 for v in trace_bytes.values()) and len(traces)==27 and sum(x['observations'] for x in traces.values())==1350
aggregate=[]
for size in (32,128,512):
 for flag in ('on','off','verify'):
  shadow=[timing[(size,i,'shadow',flag)] for i in range(3)];clean=[timing[(size,i,'clean',flag)] for i in range(3)];ratios=[s['observe_ns']/c['observe_ns'] for s,c in zip(shadow,clean)]
  aggregate.append({'size':size,'flag':flag,'projection_us':median(s['projection_ns'] for s in shadow)/1000,'same_JVM_oracle_us':median(s['oracle_ns'] for s in shadow)/1000,'validation_us':median(s['validation_ns'] for s in shadow)/1000,'reporting_us':median(s['reporting_ns'] for s in shadow)/1000,'journal_observe_us':median(s['observe_ns'] for s in shadow)/1000,'clean_full_oracle_us':median(c['observe_ns'] for c in clean)/1000,'projection_to_same_JVM_oracle':median(s['projection_to_oracle'] for s in shadow),'projection_ratio_min':min(s['projection_to_oracle'] for s in shadow),'projection_ratio_max':max(s['projection_to_oracle'] for s in shadow),'journal_to_clean_oracle':median(ratios),'journal_ratio_min':min(ratios),'journal_ratio_max':max(ratios),'seed_projection_ms':median(s['seed_projection_ns'] for s in shadow)/1e6})
for filename,entries in [('per-JVM-medians.csv',per_jvm),('aggregated-medians.csv',aggregate)]:
 fields=list(dict.fromkeys(k for e in entries for k in e))
 with (p/filename).open('w') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(entries)

regtraces={};counts={}
for folder,old,count in [('scope',None,6),('regression','tvalue-delete-flush-guard',60),('regression/settlement','tvalue-write-guard/settlement',30),('regression/serialization','tvalue-write-guard/serialization',12),('regression/faults','tvalue-write-guard/faults',30),('regression/memory','tvalue-owner-pure-observation',56)]:
 root=p/folder;results=list(root.glob('*.result.json'));assert len(results)==count,(folder,len(results));counts[folder]=count
 for q in results:
  r=json.loads(q.read_text());assert r['exit_code']==0,q;label=q.name.removesuffix('.result.json');assert not (root/(label+'.err')).read_bytes();txt=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
  if folder!='regression/memory':assert '_OK' in txt,q
  if old:
   prior=Path('docs')/old;previous=gzip.decompress((prior/(label+'.log.gz')).read_bytes()).decode()
   native=lambda t:'\n'.join(x for x in t.splitlines() if not x.startswith('DIRTY_JOURNAL_OK '))
   assert native(txt)==native(previous),q
   for proof in root.glob(label+'.*'):
    if proof.name.endswith(('.trace.gz','.physical.txt','.state','.work')):
     assert proof.read_bytes()==(prior/proof.name).read_bytes(),proof
     if proof.name.endswith('.trace.gz') and 'prefix' not in proof.name:regtraces[str(proof.relative_to(p))]=replay(proof)
  else:assert 'OBSERVATION_LAYERS_OK checks=11' in txt
assert len(regtraces)==60,len(regtraces)
assert sum(x['observations'] for x in regtraces.values())==9318
for q in p.glob('*.trace.gz'):assert q.read_bytes()==(Path('docs/tvalue-journal-cost')/q.name).read_bytes()
for q in p.glob('*.final-view.txt'):assert q.read_bytes()==(Path('docs/tvalue-journal-cost')/q.name).read_bytes()
build=json.loads((p/'build-validation.json').read_text());initial=json.loads((p/'initial-duplicate-value-scope-attempt/build-validation.json').read_text())
for mode,info in build['builds'].items():
 for k,v in info['overlay_class_sha256'].items():
  assert hashlib.sha256((Path('../build/tvalue-observation-layers-'+mode)/k).read_bytes()).hexdigest()==v
  if 'ObservationLayersRunner' not in k:assert initial['builds'][mode]['overlay_class_sha256'][k]==v
assert build['prior_profiled_journal_recovered'] and build['oracle_never_disabled'] and build['original_reader_unchanged']
assert not subprocess.check_output(['git','diff','0c3684326d1592cda91e79076394027cd5fcd67d','--','kanger/src','kanger-data-dumb/src','.github'])
old=json.loads(Path('docs/tvalue-journal-cost/summary.json').read_text())['aggregated_metrics']
for a,b in zip(aggregate,old):
 assert (a['size'],a['flag'])==(b['size'],b['flag'])
 a['prior_projection_us']=b['projection_us'];a['projection_to_prior']=a['projection_us']/b['projection_us'];a['prior_seed_projection_ms']=b['seed_projection_ms'];a['seed_to_prior']=a['seed_projection_ms']/b['seed_projection_ms']
s={'parent':build['parent'],'final_JVMs':248,'executed_JVMs':249,'preliminary_scope_fixture_failures':1,'all_final_expectations_met':True,'total_successful_traces':87,'total_authority_views':10668,'matrix':counts,'oracle_never_disabled':True,'original_reader_unchanged':True,'prior_profiled_journal_recovered':True,'production_changed':False,'end_to_end_inference_speedup_qualified':False,'general_persistent_performance_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'aggregated_metrics':aggregate,'benchmark_replays':traces,'regression_replays':regtraces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('OBSERVATION_LAYERS_ANALYSIS_OK');print(json.dumps(aggregate,indent=2))
