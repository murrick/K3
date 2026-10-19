from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib,subprocess,xml.etree.ElementTree as ET
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
files=sorted(p.glob('*.result.json'));assert len(files)==20
windows={'early':(3,9),'middle':(18,30),'late':(36,48)};per=[];jit=[];traces=0
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 assert ('-XX:+LogCompilation' in r['command'])==(r['profile']=='jit-probe')
 native=(p/(label+'.native.txt')).read_text().splitlines();assert len(native)==336
 # The first nine cycles must retain the independently qualified clean results.
 old=Path('docs/tvalue-resident-inference')/f"{r['flag']}-{r['rep']}-clean.native.txt"
 assert native[:63]==old.read_text().splitlines()
 for i in range(48):
  cycle=native[7*i:7*i+7];assert all(line.startswith(str(i)+' ') for line in cycle)
  assert [line.split(' ',1)[1] for line in cycle]==[line.split(' ',1)[1] for line in native[:7]]
  assert sum('values=[{x=Tom}]' in line for line in cycle)==2
  trace=p/f'{label}.{i}.trace.gz';replay(trace);traces+=1
  events=(p/f'{label}.{i}.events.txt').read_text();assert 'metadata=9' in events and 'add=12' in events
  if r['mode']=='candidate':
   ref=p/f"{r['profile']}-{r['flag']}-{r['rep']}-baseline.{i}"
   assert trace.read_bytes()==Path(str(ref)+'.trace.gz').read_bytes();assert events==Path(str(ref)+'.events.txt').read_text()
  if i<9:
   previous=Path('docs/tvalue-resident-inference')/f"{r['flag']}-{r['rep']}-baseline.{i}.trace.gz";assert trace.read_bytes()==previous.read_bytes()
 if r['mode']=='candidate':assert (p/(label+'.native.txt')).read_bytes()==(p/f"{r['profile']}-{r['flag']}-{r['rep']}-baseline.native.txt").read_bytes()
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==48 and [int(x['iteration']) for x in rows]==list(range(48))
 assert all(int(x['queries'])==7 and int(x['wall_ns'])>0 and int(x['query_wall_ns'])>0 and int(x['allocated_bytes'])>=int(x['query_allocated_bytes'])>=0 for x in rows)
 for window,(start,end) in windows.items():
  samples=rows[start:end];per.append({**{k:r[k] for k in ('flag','rep','mode','profile')},'window':window,**{key:median(int(x[key]) for x in samples) for key in ('wall_ns','allocated_bytes','query_wall_ns','query_allocated_bytes')}})
 if r['profile']=='jit-probe':
  root=ET.fromstring(gzip.decompress((p/(label+'.jit-selected.xml.gz')).read_bytes()));assert root.findall('nmethod')
  jit.append({'mode':r['mode'],'raw_sha256':root.attrib['raw_sha256'],'compiled':[dict(n.attrib) for n in root.findall('nmethod')]})
metrics=[]
for flag in ('on','off','verify'):
 for window in windows:
  row={'flag':flag,'window':window}
  for mode in ('baseline','candidate'):
   cells=[x for x in per if (x['flag'],x['window'],x['mode'],x['profile'])==(flag,window,mode,'timing')];assert len(cells)==3
   row[mode]={key:median(x[key] for x in cells) for key in ('wall_ns','allocated_bytes','query_wall_ns','query_allocated_bytes')}
  for key in ('wall_ns','query_wall_ns'):
   ratios=[]
   for rep in range(3):
    pair={x['mode']:x for x in per if (x['flag'],x['window'],x['rep'],x['profile'])==(flag,window,rep,'timing')};ratios.append(pair['candidate'][key]/pair['baseline'][key])
   row[key+'_paired_ratios']=ratios;row[key+'_paired_ratio']=median(ratios)
  metrics.append(row)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();b=json.loads(Path('docs/tvalue-resident-inference/build-validation.json').read_text())
assert all(sha(f)==h for f,h in {**b['classes'],**b['jars']}.items())
assert sha('docs/tvalue-resident-inference/ResidentInferenceRunner.java')==b['source_sha256']
assert traces==960
summary={'JVMs':20,'timing_JVMs':18,'separate_JIT_probe_JVMs':2,'total_queries':6720,'trace_replays':960,'candidate_baseline_traces_identical':480,'prior_clean_prefix_comparisons':20,'windows':windows,'metrics':metrics,'JIT_separate_probes':jit,'exact_measurement_JIT_phase_alignment_available':False,'full_inference_corpus_qualified':False,'production_changed':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
for r in metrics:print(r['flag'],r['window'],'cycle',round(r['wall_ns_paired_ratio'],3),'queries',round(r['query_wall_ns_paired_ratio'],3),'B',r['baseline']['allocated_bytes']-r['candidate']['allocated_bytes'])
print('RESIDENT_WARMUP_ANALYSIS_OK',traces)
