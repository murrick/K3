from pathlib import Path
from statistics import median
import csv,json,gzip,hashlib,ast,subprocess
root=Path(__file__).parent;p=root/'cost';sha=lambda q:hashlib.sha256(Path(q).read_bytes()).hexdigest()
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
assert len(list(p.glob('*.result.json')))==120
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
 if r['mode'].endswith('-attached'):
  for workload in ('setter','export'):
   trace=p/(label+'.'+workload+'.trace.gz');stats=replay(trace);traces[trace.name]=stats
   assert stats['observations']==(3 if workload=='setter' else 4)
   assert stats['touches']==(8192 if workload=='setter' else 16)
   assert stats['changes']==(0 if workload=='setter' else 16)
   assert stats['transitions']['term_changes']==(0 if workload=='setter' else 16)
   touches=[dict(x.split('=',1) for x in row.split()[1:]) for row in gzip.decompress(trace.read_bytes()).decode().splitlines() if row.startswith('TOUCH ')]
   assert all(int(t['value'])==i%8 and int(t['variable'])==i%8 and int(t['term'])==(1 if (i//8)%2==0 else 0) for i,t in enumerate(touches))
   reference=p/f"{r['size']}-0-on-slow-attached.{workload}.trace.gz";assert trace.read_bytes()==reference.read_bytes()
 else:assert not list(p.glob(label+'.*.trace.gz'))
aggregated=[]
modes=('clean','slow-disabled','fast-disabled','slow-attached','fast-attached')
for size in (32,128):
 for workload in ('setter','export'):
  row={'size':size,'workload':workload}
  for mode in modes:
   group=[x for x in per if (x['size'],x['workload'],x['mode'],x['flag'])==(size,workload,mode,'on')];assert len(group)==10
   for metric in ('wall_ns','allocated_bytes'):
    row[mode+'_'+metric+'_per_operation']=median(x[metric+'_per_operation'] for x in group)
   row[mode+'_JVM_min_ns']=min(x['wall_ns_per_operation'] for x in group);row[mode+'_JVM_max_ns']=max(x['wall_ns_per_operation'] for x in group)
  ratios=[];savings=[];fast_overhead=[];attached_ratios=[]
  for rep in range(10):
   cells={x['mode']:x['wall_ns_per_operation'] for x in per if (x['size'],x['workload'],x['repetition'],x['flag'])==(size,workload,rep,'on')};assert len(cells)==5
   ratios.append(cells['fast-disabled']/cells['slow-disabled']);savings.append(cells['slow-disabled']-cells['fast-disabled']);fast_overhead.append(cells['fast-disabled']-cells['clean']);attached_ratios.append(cells['fast-attached']/cells['slow-attached'])
  row.update(paired_disabled_fast_slow_ratio=median(ratios),paired_disabled_saved_ns=median(savings),paired_fast_extra_over_clean_ns=median(fast_overhead),paired_attached_fast_slow_ratio=median(attached_ratios),paired_attached_ratio_min=min(attached_ratios),paired_attached_ratio_max=max(attached_ratios),paired_disabled_ratio_min=min(ratios),paired_disabled_ratio_max=max(ratios))
  aggregated.append(row)
for name,records in [('per-JVM-medians.csv',per),('aggregated-medians.csv',aggregated)]:
 with (p/name).open('w') as f:w=csv.DictWriter(f,fieldnames=list(records[0]),lineterminator="\n");w.writeheader();w.writerows(records)
assert counts==7680 and len(traces)==96
b=json.loads((root/'build-validation.json').read_text());control=json.loads((root/'control-validation.json').read_text())
assert control['class_sha256']==json.loads(Path('docs/tvalue-hygiene-base/build-validation.json').read_text())['compilations']['hooked']['class_sha256']
for mode,record in b['compilations'].items():
 assert all(sha(k)==v for k,v in record['source_sha256'].items())
 assert all(sha(Path('../build/tvalue-observer-fast-disabled-'+mode)/k)==v for k,v in record['class_sha256'].items())
assert all(sha(Path('../build/tvalue-observer-fast-disabled-slow')/k)==v for k,v in control['class_sha256'].items())
driver=json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())
assert all(sha(k)==v for k,v in driver['source_sha256'].items())
assert all(sha(Path('../build/tvalue-observer-fast-disabled-cost-tests')/k)==v for k,v in driver['class_sha256'].items())
assert json.loads((root/'cost-build-validation.json').read_text())['class_sha256']==driver['class_sha256']
s={'native_head':b['native_head'],'final_JVMs':120,'timing_flag_on_JVMs':100,'off_verify_JVMs':20,'measured_batches':counts,'trace_replays':traces,'native_controls_identical':True,'same_cost_driver_bytecode':True,'slow_control_byte_identical_to_prior_fresh_base':True,'both_full_journals_enabled':True,'metrics':aggregated,'exclusive_host_or_statistical_significance_claimed':False,'end_to_end_inference_speedup_qualified':False}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n')
print('FAST_DISABLED_COST_ANALYSIS_OK JVMs=120 batches=7680 traces=96')
for row in aggregated:print(json.dumps(row))
