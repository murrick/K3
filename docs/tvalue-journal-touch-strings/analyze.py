from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib
p=Path(__file__).parent;results=[f for f in p.glob('*.result.json') if not f.name.startswith('recheck-')];assert len(results)==48
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
per=[];accepted=0;replays=0
for path in sorted(results):
 r=json.loads(path.read_text());label=path.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 if r['driver']=='consumer':
  old=Path('docs/tvalue-observer-fast-disabled')/f"{r['repetition']}-{r['flag']}-hooked"
  for suffix in ('.native-rows.txt','.diagnostics.txt','.log.gz'):assert (p/(label+suffix)).read_bytes()==Path(str(old)+suffix).read_bytes()
  traces=list(p.glob(label+'.*.accepted.trace.gz'));assert len(traces)==7
  for f in traces:assert f.read_bytes()==Path(str(old)+f.name[len(label):]).read_bytes();accepted+=1
  assert not list(p.glob(label+'.root-clear*.trace*'))
 elif r['driver']=='owner':assert b'OBSERVER_SESSION_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 elif r['driver']=='contexts':
  assert b'CONTEXT_SNAPSHOT_OK cases=4' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 elif r['driver']=='routes':
  assert b'ROUTE_ORDER_OK cases=50' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 else:
  assert b'OBSERVER_COST_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
  old=Path('docs/tvalue-observer-fast-disabled/cost')/f"{r['size']}-0-on-fast-attached"
  assert (p/(label+'.native.txt')).read_bytes()==Path(str(old)+'.native.txt').read_bytes()
  rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==64
  for workload,ops in [('setter',8192),('export',16)]:
   samples=[x for x in rows if x['workload']==workload];assert [int(x['iteration']) for x in samples]==list(range(32))
   assert all(int(x['operations'])==ops and int(x['wall_ns'])>0 and int(x['allocated_bytes'])>=0 for x in samples)
   f=p/(label+'.'+workload+'.trace.gz');stats=replay(f);replays+=1;assert f.read_bytes()==Path(str(old)+'.'+workload+'.trace.gz').read_bytes()
   per.append({**{k:r[k] for k in ('size','repetition','flag','module')},'workload':workload,'ns':median(int(x['wall_ns'])/ops for x in samples),'bytes':median(int(x['allocated_bytes'])/ops for x in samples)})
assert (p/'routes-baseline.routes.txt').read_bytes()==(p/'routes-candidate.routes.txt').read_bytes()
for flag in ('on','off','verify'):
 assert (p/('contexts-'+flag+'-baseline.contexts.txt')).read_bytes()==(p/('contexts-'+flag+'-candidate.contexts.txt')).read_bytes()
metrics=[]
for size in (32,128):
 for workload in ('setter','export'):
  row={'size':size,'workload':workload}
  for module in ('baseline','candidate'):
   cells=[x for x in per if (x['size'],x['workload'],x['module'],x['flag'])==(size,workload,module,'on')];assert len(cells)==6
   row[module+'_ns']=median(x['ns'] for x in cells);row[module+'_bytes']=median(x['bytes'] for x in cells)
  ratios=[];savings=[]
  for rep in range(6):
   cells={x['module']:x for x in per if (x['size'],x['workload'],x['repetition'],x['flag'])==(size,workload,rep,'on')}
   ratios.append(cells['candidate']['ns']/cells['baseline']['ns']);savings.append(cells['baseline']['bytes']-cells['candidate']['bytes'])
  row['paired_candidate_baseline_ratio']=median(ratios);row['ratio_range']=[min(ratios),max(ratios)];row['paired_saved_bytes']=median(savings);metrics.append(row)
for flag in ('on','off','verify'):
 assert json.loads((p/(flag+'.gate.json')).read_text())['exit_code']==0
 assert not (p/(flag+'.gate.err')).read_bytes()
 assert b'JOURNAL_DISAGREEMENT_GATE_OK' in gzip.decompress((p/(flag+'.gate.log.gz')).read_bytes())
for name in ('vanilla','missing-mind','missing-factory','missing-value'):
 assert json.loads((p/(name+'.wiring.json')).read_text())['exit_code']==0 and not (p/(name+'.err')).read_bytes()
 assert b'VANILLA_CONSUMER_REFUSAL_OK' in gzip.decompress((p/(name+'.log.gz')).read_bytes())
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();b=json.loads((p/'build-validation.json').read_text())
assert all(sha(f)==v for f,v in b['source_sha256'].items())
assert all(sha(Path('../build/tvalue-journal-touch-strings-module')/k)==v for k,v in b['class_sha256'].items())
assert sha('../build/tvalue-journal-touch-strings.jar')==b['jar_sha256']
assert sha(p/'ContextSnapshotRunner.java')==b['fixture_source_sha256']
assert all(sha(Path('../build/tvalue-journal-touch-strings-tests')/k)==v for k,v in b['fixture_class_sha256'].items())
assert accepted==63 and replays==56
summary={'native_head':'2422f7f6d9e1af7608203b1566df64b5f29fe344','JVMs':55,'context_snapshot_JVMs':6,'reentrant_context_cases':24,'context_membership_and_retirement_traces_identical':True,'consumer_JVMs':9,'owner_JVMs':3,'route_order_JVMs':2,'route_probe_cases':100,'cost_JVMs':28,'wiring_JVMs':4,'disagreement_gate_JVMs':3,'cost_measured_batches':1792,'prior_accepted_traces_identical':accepted,'cost_trace_replays_identical':replays,'native_controls_identical':True,'signed_route_order_and_deduplication_identical':True,'JOURNAL_DISAGREEMENT_fault_injected_and_recovers':True,'both_full_authority_journals_enabled':True,'production_changed':False,'metrics':metrics,'SMART_qualified':False,'full_inference_corpus_qualified':False,'native_recycled_ID_repaired':False,'exclusive_host_or_end_to_end_speedup_claimed':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n');print(json.dumps(summary,indent=2))
