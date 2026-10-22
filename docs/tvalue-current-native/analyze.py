from pathlib import Path
import ast,csv,gzip,json,tarfile,hashlib,subprocess,collections
p=Path(__file__).parent;raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
class TraceBytes:
 def __init__(self,body):self.body=body
 def read_bytes(self):return gzip.compress(self.body,mtime=0)
files=sorted(p.glob('*.result.json'));assert len(files)==57,len(files)
traces=pairs=cross_pairs=query_count=variable_rows=0
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');n=r['repeats'];assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes());native=(p/(label+'.native.txt')).read_bytes();assert len(native.splitlines())==n*7 and native.count(b'values=[{x=Tom}]')==n*2;query_count+=n*7;variable_rows+=n*2
 control=f"{r['base']}-{r['flag']}-{r['rep']}-clean"+('-profile' if r['profile'] else '');assert native==(p/(control+'.native.txt')).read_bytes()
 old_control='old-off-0-clean-profile' if r['profile'] else label.replace('current-','old-',1)
 if r['base']=='current':assert native==(p/(old_control+'.native.txt')).read_bytes(),label
 rows=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.csv')).open())];queries=[{k:int(v) for k,v in x.items()} for x in csv.DictReader((p/(label+'.queries.csv')).open())];assert len(rows)==n and len(queries)==n*7 and [x['iteration'] for x in rows]==list(range(n))
 for i,row in enumerate(rows):
  q=queries[i*7:i*7+7];assert [(v['iteration'],v['query']) for v in q]==[(i,z) for z in range(7)];assert row['query_wall_ns']==sum(x['wall_ns'] for x in q) and row['query_allocated_bytes']==sum(x['allocated_bytes'] for x in q);assert row['wall_ns']==row['end_ns']-row['start_ns']>=row['query_wall_ns']>0;assert row['allocated_bytes']>=row['query_allocated_bytes']>=0
 archive=p/(label+'.evidence.tar.gz')
 if r['mode'] in ('baseline','candidate'):
  with tarfile.open(archive,'r:gz') as tar:
   assert len(tar.getmembers())==n*2
   reference_label=('current-off-0-baseline-profile' if r['profile'] else label.replace('-candidate','-baseline')) if r['mode']=='candidate' else None
   reference=tarfile.open(p/(reference_label+'.evidence.tar.gz'),'r:gz') if reference_label else None
   cross_label=label.replace('current-','old-',1);cross=tarfile.open(p/(cross_label+'.evidence.tar.gz'),'r:gz') if r['base']=='current' and not r['profile'] else None
   try:
    for i in range(n):
     body=tar.extractfile(f'{label}.{i}.trace').read();events=tar.extractfile(f'{label}.{i}.events.txt').read();replay(TraceBytes(body));traces+=1;assert b'metadata=' in events and b'add=' in events
     if reference:assert body==reference.extractfile(f'{reference_label}.{i}.trace').read();assert events==reference.extractfile(f'{reference_label}.{i}.events.txt').read();pairs+=1
     if cross:assert body==cross.extractfile(f'{cross_label}.{i}.trace').read();assert events==cross.extractfile(f'{cross_label}.{i}.events.txt').read();cross_pairs+=1
   finally:
    if reference:reference.close()
    if cross:cross.close()
 else:assert not archive.exists()
 if r['profile']:assert (p/(label+'.events.json.gz')).stat().st_size>0 and '-Dresident.override='+str(r['override']).lower() in r['command']
assert (traces,pairs,cross_pairs,query_count,variable_rows)==(2624,1824,288,40320,11520),(traces,pairs,cross_pairs,query_count,variable_rows)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();b=json.loads((p/'build-validation.json').read_text());assert all(sha(f)==h for f,h in b['compilations']['fixture']['sources'].items());assert all(sha(Path('../build/current-native/fixture')/f)==h for f,h in b['compilations']['fixture']['classes'].items())
old=b['old'];new=b['current'];subprocess.run(['git','merge-base','--is-ancestor',old,new],check=True);diff=subprocess.check_output(['git','diff','--name-status',old,new,'--','kanger/src'],text=True);(p/'native-core-diff.patch.gz').write_bytes(gzip.compress(subprocess.check_output(['git','diff',old,new,'--','kanger/src']),mtime=0));a=b['compilations']['old-clean']['classes'];c=b['compilations']['current-clean']['classes'];drift={'old':old,'current':new,'old_is_ancestor':True,'core_changed_paths':diff.splitlines(),'core_status_counts':dict(collections.Counter(x.split('\t')[0] for x in diff.splitlines())),'compiled_classes':{'old':len(a),'current':len(c),'added':sorted(set(c)-set(a)),'removed':sorted(set(a)-set(c)),'changed_common':sorted(k for k in a.keys()&c.keys() if a[k]!=c[k])},'OptimizationOptions_unchanged':subprocess.check_output(['git','diff',old,new,'--','kanger/src/org/kanger/OptimizationOptions.java'])==b''};(p/'develop-drift.json').write_text(json.dumps(drift,indent=2)+'\n')
summary={'JVMs':57,'correctness_JVMs':48,'profile_JVMs':9,'queries':query_count,'variable_rows':variable_rows,'trace_replays':traces,'identical_baseline_candidate_trace_pairs':pairs,'identical_cross_native_traces':cross_pairs,'all_current_native_rows_match_old':True,'native_old':old,'native_current':new,'performance_comparison_qualified':False,'production_changed':False,'SMART_qualified':False,'prior_temporary_dataset_lost':True};(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('CURRENT_ANALYSIS_OK',summary)
