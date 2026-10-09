from pathlib import Path
from statistics import median
import csv,json,gzip,hashlib,ast,subprocess
p=Path(__file__).parent;sha=lambda q:hashlib.sha256(Path(q).read_bytes()).hexdigest()
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
assert len(list(p.glob('*.result.json')))==48
per=[];canonical={};traces={};counts=0
for result in sorted(p.glob('*.result.json')):
 r=json.loads(result.read_text());label=result.name.removesuffix('.result.json');assert r['exit_code']==0
 assert not (p/(label+'.err')).read_bytes();assert b'OBSERVER_COST_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 native=(p/(label+'.native.txt')).read_bytes()
 if r['size'] in canonical:assert native==canonical[r['size']]
 else:canonical[r['size']]=native
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==64
 for workload,operations in [('setter',8192),('export',16)]:
  samples=[x for x in rows if x['workload']==workload];assert [int(x['iteration']) for x in samples]==list(range(32))
  assert all(int(x['operations'])==operations and int(x['wall_ns'])>0 and int(x['allocated_bytes'])>=0 for x in samples)
  e={k:r[k] for k in ['size','repetition','flag','mode']};e['workload']=workload
  for key in ('wall_ns','allocated_bytes'):e[key+'_per_operation']=median(int(x[key])/operations for x in samples)
  e['first_half_ns_per_operation']=median(int(x['wall_ns'])/operations for x in samples[:16]);e['second_half_ns_per_operation']=median(int(x['wall_ns'])/operations for x in samples[16:])
  e['sample_min_ns_per_operation']=min(int(x['wall_ns'])/operations for x in samples);e['sample_max_ns_per_operation']=max(int(x['wall_ns'])/operations for x in samples)
  per.append(e);counts+=len(samples)
 if r['mode']=='attached':
  for workload in ('setter','export'):
   trace=p/(label+'.'+workload+'.trace.gz');stats=replay(trace);traces[trace.name]=stats
   assert stats['observations']==(3 if workload=='setter' else 4)
   assert stats['touches']==(8192 if workload=='setter' else 16)
   assert stats['changes']==(0 if workload=='setter' else 16)
   assert stats['transitions']['term_changes']==(0 if workload=='setter' else 16)
   touches=[dict(x.split('=',1) for x in row.split()[1:]) for row in gzip.decompress(trace.read_bytes()).decode().splitlines() if row.startswith('TOUCH ')]
   assert all(int(t['value'])==i%8 and int(t['variable'])==i%8 and int(t['term'])==(1 if (i//8)%2==0 else 0) for i,t in enumerate(touches))
   reference=p/f"{r['size']}-0-on-attached.{workload}.trace.gz";assert trace.read_bytes()==reference.read_bytes()
 else:assert not list(p.glob(label+'.*.trace.gz'))
aggregated=[]
for size in (32,128):
 for workload in ('setter','export'):
  row={'size':size,'workload':workload}
  for mode in ('clean','disabled','attached'):
   group=[x for x in per if (x['size'],x['workload'],x['mode'],x['flag'])==(size,workload,mode,'on')];assert len(group)==6
   row[mode+'_ns_per_operation']=median(x['wall_ns_per_operation'] for x in group)
   row[mode+'_bytes_per_operation']=median(x['allocated_bytes_per_operation'] for x in group)
   row[mode+'_JVM_median_min_ns']=min(x['wall_ns_per_operation'] for x in group);row[mode+'_JVM_median_max_ns']=max(x['wall_ns_per_operation'] for x in group)
  disabled=[];attached=[];extra=[]
  for rep in range(6):
   cells={x['mode']:x for x in per if (x['size'],x['workload'],x['repetition'],x['flag'])==(size,workload,rep,'on')};assert len(cells)==3
   disabled.append(cells['disabled']['wall_ns_per_operation']/cells['clean']['wall_ns_per_operation']);attached.append(cells['attached']['wall_ns_per_operation']/cells['clean']['wall_ns_per_operation']);extra.append(cells['disabled']['wall_ns_per_operation']-cells['clean']['wall_ns_per_operation'])
  row['paired_disabled_ratio_median']=median(disabled);row['paired_disabled_ratio_min']=min(disabled);row['paired_disabled_ratio_max']=max(disabled);row['paired_attached_ratio_median']=median(attached);row['paired_disabled_extra_ns_median']=median(extra)
  aggregated.append(row)
for name,records in [('per-JVM-medians.csv',per),('aggregated-medians.csv',aggregated)]:
 with (p/name).open('w') as f:w=csv.DictWriter(f,fieldnames=list(records[0]),lineterminator="\n");w.writeheader();w.writerows(records)
b=json.loads((p/'build-validation.json').read_text());old=json.loads(Path('docs/tvalue-observer-session/build-validation.json').read_text())
assert sha('docs/tvalue-observer-session/build-validation.json')==b['observer_build_manifest_sha256']
for mode,record in old['compilations'].items():
 assert all(sha(k)==v for k,v in record['source_sha256'].items())
 assert all(sha(Path('../build/tvalue-observer-session-'+mode)/k)==v for k,v in record['class_sha256'].items())
assert sha('../build/tvalue-observer-session.jar')==old['jar_sha256'];assert all(sha(k)==v for k,v in b['source_sha256'].items());assert all(sha(Path('../build/tvalue-observer-cost-tests')/k)==v for k,v in b['class_sha256'].items())
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/diagnostics/src','kanger-qualification/diagnostics/runtime','kanger-qualification/src','pom.xml','kanger-qualification/pom.xml','.github','docs/tvalue-observer-session'])
s={'parent':b['parent'],'native_head':b['native_head'],'final_JVMs':48,'timing_JVMs_flag_on':36,'off_verify_correctness_JVMs':12,'balanced_six_mode_orders':True,'measured_batches':counts,'native_controls_identical_across_modes_repetitions_and_flags':True,'trace_replays':traces,'observer_runtime_and_module_unchanged':True,'both_full_journals_enabled':True,'internal_phase_timers_disabled':True,'bounded_sessions_per_setter_batch':True,'setter_seed_observe_finish_outside_timer':True,'export_seed_mutation_observation_finish_inside_timer':True,'native_result_consumed_with_volatile_sink':True,'production_changed':False,'SMART_persistence_qualified':False,'complete_inference_corpus_qualified':False,'end_to_end_inference_speedup_qualified':False,'exclusive_host_or_statistical_significance_claimed':False,'metrics':aggregated}
assert counts==3072 and len(traces)==32
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n')
print('OBSERVER_COST_ANALYSIS_OK JVMs=48 batches=3072 traces=32')
for x in aggregated:print(x['size'],x['workload'],'ns',*(round(x[m+'_ns_per_operation'],2) for m in ('clean','disabled','attached')),'bytes',*(round(x[m+'_bytes_per_operation'],2) for m in ('clean','disabled','attached')),'disabledRatio',round(x['paired_disabled_ratio_median'],2))
