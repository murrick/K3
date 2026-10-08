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
reg=list((p/'regression').glob('*.result.json'));assert len(reg)==36;regtraces={}
for q in reg:
 r=json.loads(q.read_text());assert r['exit_code']==0;label=q.name.replace('.result.json','');root=q.parent;old=Path('docs/tvalue-reindex-boundary/regression');assert not (root/(label+'.err')).read_bytes();text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode();prior=gzip.decompress((old/(label+'.log.gz')).read_bytes()).decode();assert '_OK' in text
 prefix={'delete':'DELETE_FLUSH_NATIVE','flush':'DELETE_FLUSH_NATIVE','clear':'CLEAR_CLOSE_NATIVE','close':'CLEAR_CLOSE_NATIVE','upsert':'STORAGE_FAULT_NATIVE','scope':'SAVED_LINKS_OK'}[r['fixture']]
 assert next(x for x in text.splitlines() if x.startswith(prefix))==next(x for x in prior.splitlines() if x.startswith(prefix))
 for proof in root.glob(label+'.physical.txt'):assert proof.read_bytes()==(old/proof.name).read_bytes()
 for trace in root.glob(label+'.success.trace.gz'):assert trace.read_bytes()==(old/trace.name).read_bytes();regtraces[trace.name]=replay(trace)
assert len(regtraces)==3 and sum(x['observations'] for x in regtraces.values())==9
before=Path('docs/tvalue-update-boundary/TValueDirtyJournal.java').read_text();recovered=(p/'TValueDirtyJournal.java').read_text()
for old,new in reversed(json.loads((p/'instrumentation.json').read_text())['replacements']):recovered=recovered.replace(new,old)
assert recovered==before
build=json.loads((p/'build-validation.json').read_text());assert build['oracle_never_disabled'] and build['original_journal_source_recovered']
for mode,info in build['builds'].items():assert all(hashlib.sha256((Path('../build/tvalue-journal-cost-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
for stage in ('tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','c8d4fa5adc17b206e97c90e25487bb869a591956','--','kanger/src','kanger-data-dumb/src','.github'])
s={'parent':'c8d4fa5adc17b206e97c90e25487bb869a591956','fresh_JVMs':90,'timing_JVMs':54,'regression_JVMs':36,'preliminary_fixture_failures':0,'all_expectations_met':True,'measured_updates_per_JVM':48,'warmup_updates_per_JVM':16,'new_successful_journal_traces':27,'new_authority_views':1350,'new_traces_byte_identical_per_size':True,'unchanged_regression_successful_traces':3,'regression_authority_views':9,'total_successful_traces':30,'total_authority_views':1359,'oracle_never_disabled':True,'original_journal_source_recovered':True,'native_final_views_equal_clean_shadow_flags_repetitions':True,'production_changed':False,'end_to_end_inference_speedup_qualified':False,'general_persistent_or_settlement_performance_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'aggregated_metrics':aggregate,'new_trace_replays':traces,'regression_trace_replays':regtraces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('JOURNAL_COST_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k not in ('aggregated_metrics','new_trace_replays','regression_trace_replays')}))
