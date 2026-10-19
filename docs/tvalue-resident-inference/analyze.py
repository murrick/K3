from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
files=list(p.glob('*.result.json'));assert len(files)==36
per=[];traces=0;stats=[]
for f in sorted(files):
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 control=p/f"{r['flag']}-{r['rep']}-clean.native.txt";assert (p/(label+'.native.txt')).read_bytes()==control.read_bytes()
 native=(p/(label+'.native.txt')).read_text().splitlines();assert len(native)==63
 assert sum('values=[{x=Tom}]' in line for line in native)==18
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==9 and [int(x['iteration']) for x in rows]==list(range(9))
 assert all(int(x['queries'])==7 and int(x['wall_ns'])>0 and int(x['query_wall_ns'])>0 and int(x['allocated_bytes'])>=int(x['query_allocated_bytes'])>=0 for x in rows)
 samples=rows[3:]
 per.append({**r,'qualified_cycle_ns':median(int(x['wall_ns']) for x in samples),'qualified_cycle_bytes':median(int(x['allocated_bytes']) for x in samples),'queries_ns':median(int(x['query_wall_ns']) for x in samples),'queries_bytes':median(int(x['query_allocated_bytes']) for x in samples)})
 if r['mode'] in ('baseline','candidate'):
  for i in range(9):
   trace=p/f'{label}.{i}.trace.gz';result=replay(trace);traces+=1
   assert gzip.decompress(trace.read_bytes()).count(b'TOUCH ')>0
   events=(p/f'{label}.{i}.events.txt').read_text();assert 'metadata=' in events and 'add=' in events
   if r['mode']=='candidate':
    ref=p/f"{r['flag']}-{r['rep']}-baseline.{i}"
    assert trace.read_bytes()==Path(str(ref)+'.trace.gz').read_bytes();assert events==Path(str(ref)+'.events.txt').read_text()
   if i==0:stats.append({'label':label,'replay':result,'events':events.strip()})
 else:assert not list(p.glob(label+'.*.trace.gz'))
metrics=[]
for flag in ('on','off','verify'):
 row={'flag':flag}
 for mode in ('clean','disabled','baseline','candidate'):
  cells=[x for x in per if x['flag']==flag and x['mode']==mode];assert len(cells)==3
  row[mode]={k:median(x[k] for x in cells) for k in ('qualified_cycle_ns','qualified_cycle_bytes','queries_ns','queries_bytes')}
 for key in ('qualified_cycle_ns','qualified_cycle_bytes','queries_ns','queries_bytes'):
  ratios=[];savings=[]
  for rep in range(3):
   pair={x['mode']:x for x in per if x['flag']==flag and x['rep']==rep};ratios.append(pair['candidate'][key]/pair['baseline'][key]);savings.append(pair['baseline'][key]-pair['candidate'][key])
  row[key+'_paired_ratios']=ratios;row[key+'_paired_ratio']=median(ratios);row[key+'_paired_savings']=median(savings)
 metrics.append(row)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();b=json.loads((p/'build-validation.json').read_text());assert sha(p/'ResidentInferenceRunner.java')==b['source_sha256'];assert all(sha(f)==h for f,h in {**b['classes'],**b['jars']}.items())
assert traces==162
summary={'JVMs':36,'queries_all':36*9*7,'queries_measured':36*6*7,'trace_replays':traces,'candidate_baseline_traces_identical':81,'native_control_JVMs':9,'disabled_JVMs':9,'attached_JVMs':18,'ground_queries_per_cycle':5,'variable_queries_per_cycle':2,'expected_variable_value':'[{x=Tom}]','full_inference_corpus_qualified':False,'SMART_qualified':False,'production_changed':False,'metrics':metrics,'trace_examples':stats}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
for x in metrics:print(x['flag'],'cycle ratio',x['qualified_cycle_ns_paired_ratio'],'query ratio',x['queries_ns_paired_ratio'],'cycle bytes saved',x['qualified_cycle_bytes_paired_savings'],'query bytes saved',x['queries_bytes_paired_savings'])
print('RESIDENT_ANALYSIS_OK',traces)
