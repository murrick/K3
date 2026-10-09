from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};per_selected={};entries=0
results=list(p.glob('*.result.json'));assert len(results)==9
for q in results:
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 log=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert f"PARENT_ADD_OK selected={r['selected']} contexts=6 waves=2 negative=3 controls=204" in log
 pair=[]
 for mode in ('old','memo'):
  path=p/(label+'.'+mode+'.trace.gz');stats=replay(path);assert stats['contexts']==6 and stats['observations']==49 and stats['seeds']==6 and stats['resets']==0 and stats['deferred']==0 and stats['retired']==0
  assert stats['transitions']['added']==8 and stats['transitions']['term_changes']==9 and all(stats['transitions'][k]==0 for k in ('removed','order_changes','deleted','resurrected'))
  lines=gzip.decompress(path.read_bytes()).decode().splitlines();counts={i:0 for i in range(1,7)};previous={};views={};touches={}
  for line in lines:
   if line.startswith('TOUCH '):
    kv=dict(x.split('=',1) for x in line.split()[1:]);touches.setdefault(kv['reason'],[]).append(int(kv['ctx']))
   if line.startswith('VIEW '):
    kv=dict(x.split('=',1) for x in line.split()[1:]);ctx=int(kv['ctx']);reason=kv['reason'];view=parseview(kv['view']);counts[ctx]+=1;entries+=sum(len(v) for v in view.values());views[(ctx,reason)]=view
    assert len(view)==4
    if reason in ('parent-add-1','parent-add-2'):assert (view!=previous[ctx])==(ctx==1)
    if reason=='late-value-term-rewrite':assert (view!=previous[ctx])==(ctx in (1,4,6))
    if reason=='duplicate-parent-add':assert view==previous[ctx]
    if reason=='existing-inherited-term-rewrite':assert view!=previous[ctx]
    previous[ctx]=view
  assert counts=={1:9,2:9,3:9,4:8,5:8,6:6}
  assert views[(4,'new-sibling-1')]==views[(1,'parent-add-1')]
  assert views[(5,'new-grand-from-old-child')]==views[(2,'parent-add-1')]
  assert views[(6,'new-sibling-2')]==views[(1,'parent-add-2')]
  assert touches=={'parent-add-1':[1,2,3]*4,'parent-add-2':[1,2,3,4,5]*4,'native-term-rewrite':[1,2,3,4,6]+[1,2,3,4,5,6],'duplicate-parent-add':[1,2,3,4,5,6]}
  traces[path.name]=stats;pair.append(path.read_bytes())
 assert pair[0]==pair[1];per_selected.setdefault(r['selected'],set()).add(pair[1])
 import re
 errors=(p/(label+'.negative-errors.txt')).read_text().splitlines();assert len(errors)==3
 for index,line in enumerate(errors):assert line.startswith('mode='+str(index)+' ') and list(map(int,re.findall(r'dirty projection mismatch ctx=(\d+)',line)))==([1,1] if index==0 else [1,4,1,4] if index==1 else [1,2,3,1,2,3])
assert all(len(v)==1 for v in per_selected.values());assert len(traces)==18 and entries==33192
build=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in build['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-parent-add')/k).read_bytes()).hexdigest()==v for k,v in build['class_sha256'].items())
for directory,proof in [('../build/tvalue-layer-cost','docs/tvalue-layer-cost/build-validation.json'),('../build/tvalue-authority-stream','docs/tvalue-authority-stream/build-validation.json'),('../build/tvalue-authority-contexts','docs/tvalue-authority-contexts/build-validation.json')]:
 b=json.loads(Path(proof).read_text());assert all(hashlib.sha256((Path(directory)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'].items());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'].items())
assert not subprocess.check_output(['git','diff',build['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-authority-contexts'])
s={'parent':build['parent'],'final_JVMs':9,'successful_traces':18,'replayed_authority_views':882,'serialized_value_entries_checked':entries,'expected_negative_cases':27,'native_enumeration_controls':1836,'contexts_per_positive_session':6,'parent_addition_waves':2,'identical_pair_traces':True,'identical_traces_across_flags_for_each_selected_variable':True,'native_observer_purity_checked':True,'snapshot_membership_and_shared_payload_checked':True,'new_parent_values_after_child_setup_qualified':True,'full_oracle_never_disabled':True,'implementation_changed':False,'production_changed':False,'native_hook_integration_rerun':False,'native_transaction_boundaries_qualified':False,'persistent_storage_qualified':False,'end_to_end_inference_speedup_qualified':False,'frozen_complete_corpus_semantics_equal':False,'diagnostic_baseline_remains':'docs/tvalue-authority-stream/StreamAuthorityJournal.java','trace_replays':traces};(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('PARENT_ADD_ANALYSIS_OK JVMs=9 traces=18 views=882 value_entries=33192 negatives=27')
