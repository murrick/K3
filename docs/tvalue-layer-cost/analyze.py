from pathlib import Path
import json,csv,gzip,hashlib,subprocess
from statistics import median
p=Path(__file__).parent;results=list(p.glob('*.result.json'));assert len(results)==81,len(results)
per=[];final={}
for q in results:
 r=json.loads(q.read_text());assert r['exit_code']==0;qbase=q.name.removesuffix('.result.json');assert not (p/(qbase+'.err')).read_bytes();log=gzip.decompress((p/(qbase+'.log.gz')).read_bytes()).decode();assert f"LAYER_COST_OK size={r['size']} buckets={r['buckets']} pairs=64" in log
 rows=list(csv.DictReader((p/(qbase+'.csv')).open()));assert len(rows)==64
 for i,x in enumerate(rows):
  assert int(x['iteration'])==i and x['first']==('old' if i%2==0 else 'memo')
  assert all(int(x[k])>0 for k in ('old_ns','memo_ns','old_allocated_bytes','memo_allocated_bytes'))
 entry={k:r[k] for k in ('size','buckets','repetition','flag')}
 for k in ('old_ns','memo_ns','old_allocated_bytes','memo_allocated_bytes'):entry[k]=median(int(x[k]) for x in rows)
 entry['memo_to_old_time']=median(int(x['memo_ns'])/int(x['old_ns']) for x in rows);entry['memo_to_old_allocation']=median(int(x['memo_allocated_bytes'])/int(x['old_allocated_bytes']) for x in rows)
 for first in ('old','memo'):entry[first+'_first_time_ratio']=median(int(x['memo_ns'])/int(x['old_ns']) for x in rows if x['first']==first)
 entry['first_half_time_ratio']=median(int(x['memo_ns'])/int(x['old_ns']) for x in rows[:32]);entry['second_half_time_ratio']=median(int(x['memo_ns'])/int(x['old_ns']) for x in rows[32:]);per.append(entry);final.setdefault(r['size'],set()).add((p/(qbase+'.final.txt')).read_bytes())
assert all(len(x)==1 for x in final.values())
aggregate=[]
for size in (32,128,512):
 for buckets in (1,8,size):
  for flag in ('on','off','verify'):
   cells=[x for x in per if (x['size'],x['buckets'],x['flag'])==(size,buckets,flag)];assert len(cells)==3
   e={'size':size,'buckets':buckets,'flag':flag}
   for k in ('old_ns','memo_ns','old_allocated_bytes','memo_allocated_bytes','memo_to_old_time','memo_to_old_allocation','old_first_time_ratio','memo_first_time_ratio','first_half_time_ratio','second_half_time_ratio'):e[k]=median(x[k] for x in cells)
   e['time_ratio_min']=min(x['memo_to_old_time'] for x in cells);e['time_ratio_max']=max(x['memo_to_old_time'] for x in cells);aggregate.append(e)
for filename,entries in [('per-JVM-medians.csv',per),('aggregated-medians.csv',aggregate)]:
 with (p/filename).open('w') as f:w=csv.DictWriter(f,fieldnames=list(entries[0]));w.writeheader();w.writerows(entries)
build=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in build['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-layer-cost')/k).read_bytes()).hexdigest()==v for k,v in build['class_sha256'].items());assert not subprocess.check_output(['git','diff',build['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s={'parent':build['parent'],'timing_JVMs':81,'paired_samples':5184,'authority_controls':5184,'warmup_pairs_per_JVM':48,'measured_pairs_per_JVM':64,'all_timing_expectations_met':True,'all_native_final_views_equal':True,'native_classes_matching_prior_clean':build['native_classes_matching_prior_clean'],'production_changed':False,'end_to_end_inference_speedup_qualified':False,'general_persistent_performance_qualified':False,'frozen_complete_corpus_semantics_equal':False,'aggregated_metrics':aggregate}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('LAYER_COST_ANALYSIS_OK')
for x in aggregate:print(x['size'],x['buckets'],x['flag'],'time',round(x['memo_to_old_time'],3),'allocation',round(x['memo_to_old_allocation'],3))
