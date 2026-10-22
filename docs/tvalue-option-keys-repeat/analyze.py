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
parent=Path('docs/tvalue-option-keys');files=sorted(p.glob('*.result.json'));assert len(files)==26,len(files)
queries=variables=replays=trace_pairs=0;metrics={};records={}
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');n=r['repeats'];records[label]=r
 assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 native=(p/(label+'.native.txt')).read_bytes();assert len(native.splitlines())==n*7 and native.count(b'values=[{x=Tom}]')==n*2;queries+=n*7;variables+=n*2
 smoke=label.startswith('smoke-');control=label if smoke else 'current-off-0-clean-profile'
 assert native==(parent/(control+'.native.txt')).read_bytes(),label
 peer=label.replace('keys-','current-',1);assert native==(p/(peer+'.native.txt')).read_bytes(),label
 rows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.csv')).open())];qrows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.queries.csv')).open())]
 assert len(rows)==n and len(qrows)==n*7
 for i,row in enumerate(rows):
  q=qrows[i*7:i*7+7];assert [(v['iteration'],v['query']) for v in q]==[(i,z) for z in range(7)];assert row['query_wall_ns']==sum(x['wall_ns'] for x in q) and row['query_allocated_bytes']==sum(x['allocated_bytes'] for x in q);assert row['wall_ns']==row['end_ns']-row['start_ns']>=row['query_wall_ns']>0;assert row['allocated_bytes']>=row['query_allocated_bytes']>=0
 if r['mode']!='clean':
  reference=label if smoke else 'current-off-0-candidate-profile'
  with tarfile.open(p/(label+'.evidence.tar.gz')) as tar,tarfile.open(parent/(reference+'.evidence.tar.gz')) as frozen:
   assert len(tar.getmembers())==n*2
   for i in range(n):
    trace=tar.extractfile(f'{label}.{i}.trace').read();events=tar.extractfile(f'{label}.{i}.events.txt').read();replay(TraceBytes(trace));replays+=1
    assert trace==frozen.extractfile(f'{reference}.{i}.trace').read() and events==frozen.extractfile(f'{reference}.{i}.events.txt').read();trace_pairs+=1
 if not smoke:
  metrics[label]={}
  for phase,a,z in [('early_0_127',0,128),('late_128_511',128,512),('window_128_255',128,256),('window_256_383',256,384),('window_384_511',384,512)]:
   metrics[label][phase]={'query_cycle_allocated_bytes':median(x['query_allocated_bytes'] for x in rows[a:z]),'query_cycle_wall_ns':median(x['query_wall_ns'] for x in rows[a:z]),'per_query_allocated_bytes':{str(k):median(q['allocated_bytes'] for q in qrows if a<=q['iteration']<z and q['query']==k) for k in range(7)},'per_query_wall_ns':{str(k):median(q['wall_ns'] for q in qrows if a<=q['iteration']<z and q['query']==k) for k in range(7)}}
ratios=[];groups={}
for label,phases in metrics.items():
 if not label.startswith('keys-'):continue
 r=records[label];control=label.replace('keys-','current-',1)
 for phase,values in phases.items():
  c=metrics[control][phase];ratio={k:values[k]/c[k] for k in ('query_cycle_allocated_bytes','query_cycle_wall_ns')}
  row={'candidate':label,'baseline':control,'rep':r['rep'],'profile':r['profile'],'mode':r['mode'],'phase':phase,'ratios':ratio};ratios.append(row)
  key=('profile' if r['profile'] else 'unprofiled')+'/'+r['mode']+'/'+phase
  groups.setdefault(key,[]).append(row)
group_summary={}
for key,items in groups.items():
 group_summary[key]={'JVM_pairs':len(items)}
 for k in ('query_cycle_allocated_bytes','query_cycle_wall_ns'):
  values=[i['ratios'][k] for i in items];group_summary[key][k]={'median_ratio':median(values),'min_ratio':min(values),'max_ratio':max(values),'individual_pair_ratios':values}
assert (queries,variables,replays,trace_pairs)==(86044,24584,6148,6148)
(p/'metrics.json').write_text(json.dumps({'runs':metrics,'pair_ratios':ratios},indent=2)+'\n')
summary={'inference_JVMs':len(files),'unprofiled_measurement_JVMs':16,'profile_measurement_JVMs':8,'smoke_JVMs':2,'queries':queries,'exact_variable_rows':variables,'trace_replays':replays,'frozen_trace_event_comparisons':trace_pairs,'native_rows_identical':True,'group_summary':group_summary,'JVM_is_repetition_unit':True,'time_ratios_diagnostic_only':True,'inference_speedup_qualified':False,'SMART_qualified':False,'full_corpus_qualified':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('OPTION_KEYS_REPEAT_ANALYSIS_OK',queries,replays)
