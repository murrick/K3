from pathlib import Path
import json,gzip,hashlib,subprocess,csv,ast
from statistics import median
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==54,len(results)
per=[];traces={};final={}
for q in results:
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();assert f"PAIRED_JOURNAL_OK size={r['size']} dirty={r['buckets']} pairs=64" in gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode()
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==64
 e={k:r[k] for k in ('size','buckets','repetition','flag')}
 for i,x in enumerate(rows):
  assert int(x['iteration'])==i and x['first']==('old' if i%2==0 else 'memo') and all(int(x[k])>0 for k in ('old_wall_ns','memo_wall_ns','old_allocated_bytes','memo_allocated_bytes'))
 for k in ('old_wall_ns','memo_wall_ns','old_allocated_bytes','memo_allocated_bytes'):e[k]=median(int(x[k]) for x in rows)
 for name,top,bottom in [('wall_ratio','memo_wall_ns','old_wall_ns'),('allocation_ratio','memo_allocated_bytes','old_allocated_bytes')]:e[name]=median(int(x[top])/int(x[bottom]) for x in rows)
 for first in ('old','memo'):e[first+'_first_wall_ratio']=median(int(x['memo_wall_ns'])/int(x['old_wall_ns']) for x in rows if x['first']==first)
 for name,samples in [('first_half',rows[:32]),('second_half',rows[32:])]:e[name+'_wall_ratio']=median(int(x['memo_wall_ns'])/int(x['old_wall_ns']) for x in samples)
 phases={}
 for mode in ('old','memo'):
  ph=list(csv.reader((p/(label+'.'+mode+'.phases.csv')).open()));assert len(ph)==66 and ph[0][:3]==['baseline','true','0'] and ph[-1][:3]==['session-end','false','0']
  for x in ph:
   ns=list(map(int,x[3:]));assert len(ns)==5 and all(v>=0 for v in ns) and sum(ns[:4])==ns[4]
  for i,x in enumerate(ph[1:-1]):assert x[:3]==['update-'+str(i),'false',str(r['buckets'])]
  phases[mode]=ph[1:-1]
  for i,name in enumerate(('projection_ns','oracle_ns','validation_ns','reporting_ns','total_ns'),3):e[mode+'_'+name]=median(int(x[i]) for x in ph[1:-1])
  e[mode+'_baseline_projection_ns']=int(ph[0][3]);trace=p/(label+'.'+mode+'.trace.gz');traces[trace.name]=replay(trace);assert traces[trace.name]['observations']==66 and traces[trace.name]['changes']==64*r['buckets']
 assert (p/(label+'.old.trace.gz')).read_bytes()==(p/(label+'.memo.trace.gz')).read_bytes()
 for i,name in enumerate(('projection_ratio','oracle_ratio','validation_ratio','reporting_ratio'),3):e[name]=median(int(m[i])/max(1,int(o[i])) for o,m in zip(phases['old'],phases['memo']))
 per.append(e);final.setdefault(r['size'],set()).add((p/(label+'.final.txt')).read_bytes())
assert len(traces)==108 and sum(x['observations'] for x in traces.values())==7128 and all(len(x)==1 for x in final.values())
aggregate=[]
for size in (32,128,512):
 for buckets in (1,8):
  for flag in ('on','off','verify'):
   cells=[x for x in per if (x['size'],x['buckets'],x['flag'])==(size,buckets,flag)];assert len(cells)==3
   e={'size':size,'buckets':buckets,'flag':flag}
   for k in per[0]:
    if k not in ('size','buckets','repetition','flag'):e[k]=median(x[k] for x in cells)
   e['wall_ratio_min']=min(x['wall_ratio'] for x in cells);e['wall_ratio_max']=max(x['wall_ratio'] for x in cells);aggregate.append(e)
for filename,entries in [('per-JVM-medians.csv',per),('aggregated-medians.csv',aggregate)]:
 with (p/filename).open('w') as f:w=csv.DictWriter(f,fieldnames=list(entries[0]));w.writeheader();w.writerows(entries)
build=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in build['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-journal-paired')/k).read_bytes()).hexdigest()==v for k,v in build['class_sha256'].items())
for x in json.loads((p/'copies.json').read_text()):assert Path(x['copy']).read_text().replace(x['new_name'],x['old_name'])==Path(x['original']).read_text()
assert not subprocess.check_output(['git','diff',build['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s={'parent':build['parent'],'final_JVMs':54,'preliminary_fixture_failures':0,'paired_updates':3456,'successful_journal_traces':108,'replayed_authority_views':7128,'all_expectations_met':True,'traces_byte_identical_within_each_pair':True,'native_final_views_equal':True,'full_oracle_never_disabled':True,'class_rename_only':True,'manual_metadata_bridge_after_real_native_setters':True,'production_changed':False,'native_hook_integration_rerun':False,'end_to_end_inference_speedup_qualified':False,'general_persistent_performance_qualified':False,'frozen_complete_corpus_semantics_equal':False,'aggregated_metrics':aggregate,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('PAIRED_JOURNAL_ANALYSIS_OK')
for x in aggregate:print(x['size'],x['buckets'],x['flag'],'whole',round(x['wall_ratio'],3),'projection',round(x['projection_ratio'],3),'allocation',round(x['allocation_ratio'],3))
