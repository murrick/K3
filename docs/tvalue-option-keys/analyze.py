from pathlib import Path
import ast,csv,gzip,json,tarfile
from statistics import median
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
class TraceBytes:
 def __init__(self,body):self.body=body
 def read_bytes(self):return gzip.compress(self.body,mtime=0)
files=sorted(p.glob('*.result.json'));assert len(files)==43,len(files)
queries=variables=replays=pairs=0;metrics={}
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');n=r['repeats'];assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 native=(p/(label+'.native.txt')).read_bytes();assert len(native.splitlines())==n*7 and native.count(b'values=[{x=Tom}]')==n*2;queries+=n*7;variables+=n*2
 reference=('smoke-current-off-0-candidate' if label.startswith('smoke-') else label.replace('keys-','current-',1))
 assert native==(p/(reference+'.native.txt')).read_bytes(),label
 if not label.startswith('smoke-'):
  clean=label.replace('-baseline','-clean').replace('-candidate','-clean');assert native==(p/(clean+'.native.txt')).read_bytes(),label
 rows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.csv')).open())];qrows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.queries.csv')).open())];assert len(rows)==n and len(qrows)==n*7
 for i,row in enumerate(rows):
  q=qrows[i*7:i*7+7];assert [(v['iteration'],v['query']) for v in q]==[(i,z) for z in range(7)];assert row['query_wall_ns']==sum(x['wall_ns'] for x in q) and row['query_allocated_bytes']==sum(x['allocated_bytes'] for x in q);assert row['wall_ns']==row['end_ns']-row['start_ns']>=row['query_wall_ns']>0;assert row['allocated_bytes']>=row['query_allocated_bytes']>=0
 if r['mode']!='clean':
  reference_label=label.replace('-candidate','-baseline') if not label.startswith('smoke-') and r['mode']=='candidate' and not r['profile'] else reference
  with tarfile.open(p/(label+'.evidence.tar.gz')) as tar,tarfile.open(p/(reference_label+'.evidence.tar.gz')) as control:
   assert len(tar.getmembers())==n*2
   for i in range(n):
    trace=tar.extractfile(f'{label}.{i}.trace').read();events=tar.extractfile(f'{label}.{i}.events.txt').read();replay(TraceBytes(trace));replays+=1
    assert trace==control.extractfile(f'{reference_label}.{i}.trace').read() and events==control.extractfile(f'{reference_label}.{i}.events.txt').read();pairs+=reference_label!=label
 if r['profile']:
  for phase,a,z in [('early',0,128),('late',128,512)]:
   metrics[label+'/'+phase]={'query_cycle_allocated_bytes':median(x['query_allocated_bytes'] for x in rows[a:z]),'query_cycle_wall_ns':median(x['query_wall_ns'] for x in rows[a:z])}
ratios={}
for label,v in metrics.items():
 if label.startswith('keys-'):
  c=metrics[label.replace('keys-','current-',1)];ratios[label]={k:v[k]/c[k] for k in v}
summary={'JVMs':len(files),'queries':queries,'exact_variable_rows':variables,'trace_replays':replays,'distinct_trace_event_comparisons':pairs,'native_rows_identical':True,'metrics':metrics,'candidate_over_baseline_ratios':ratios,'time_ratios_diagnostic_only':True,'inference_speedup_qualified':False,'SMART_qualified':False,'full_corpus_qualified':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('OPTION_KEYS_ANALYSIS_OK',queries,replays,pairs)
