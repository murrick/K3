from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};per_seed={};entries=0
results=list(p.glob('*.result.json'));assert len(results)==9
for q in results:
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();log=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert f"AUTHORITY_CONTEXTS_OK seed={r['seed']} contexts=3 steps=48 negative=3" in log
 pair=[]
 for mode in ('old','memo'):
  path=p/(label+'.'+mode+'.trace.gz');stats=replay(path);assert stats['contexts']==3 and stats['observations']==150 and stats['resets']==2 and stats['seeds']==5 and stats['deferred']==0 and stats['retired']==0
  assert all(stats['transitions'][k]==0 for k in ('added','removed','order_changes'));assert stats['transitions']['deleted']>0 and stats['transitions']['resurrected']>0 and stats['transitions']['term_changes']>0
  lines=gzip.decompress(path.read_bytes()).decode().splitlines();touches=[line for line in lines if line.startswith('TOUCH ')];assert len(touches)==48 and all('reason=native-term-rewrite' in line for line in touches)
  counts={1:0,2:0,3:0};previous={};pending=[];routing_steps=0
  for line in lines:
   if line.startswith('TOUCH '):pending.append(int(dict(x.split('=',1) for x in line.split()[1:])['ctx']))
   if line.startswith('VIEW '):
    kv=dict(x.split('=',1) for x in line.split()[1:]);ctx=int(kv['ctx']);view=parseview(kv['view']);assert len(view)==4 and all(len(values)==7+ctx for values in view.values());counts[ctx]+=1;entries+=sum(len(values) for values in view.values())
    if kv['reason'].startswith('step-'):
     step=int(kv['reason'][5:]);mode=step%6
     if ctx==1:
      assert pending==([1,2,3] if mode==0 else [2,3] if mode==3 else [3] if mode==4 else []);pending=[];routing_steps+=1
      assert (view==previous[ctx])==(mode in (1,2,3,4))
     if ctx==2 and mode in (2,4):assert view==previous[ctx]
     if ctx==2 and mode in (0,1,3):assert view!=previous[ctx]
     if ctx==3 and mode in (0,2,3,4):assert view!=previous[ctx]
    previous[ctx]=view
  assert counts=={1:50,2:50,3:50} and routing_steps==48 and not pending;traces[path.name]=stats;pair.append(path.read_bytes())
 assert pair[0]==pair[1];per_seed.setdefault(r['seed'],set()).add(pair[1])
 errors=(p/(label+'.negative-errors.txt')).read_text().splitlines();assert len(errors)==3
 for mode,line in enumerate(errors):assert line.startswith('mode='+str(mode)+' ') and line.count('dirty projection mismatch ctx=')==2*(3-mode)
assert all(len(v)==1 for v in per_seed.values());assert len(traces)==18 and sum(x['observations'] for x in traces.values())==2700 and entries==97200
build=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in build['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-authority-contexts')/k).read_bytes()).hexdigest()==v for k,v in build['class_sha256'].items())
for directory,proof in [('../build/tvalue-layer-cost','docs/tvalue-layer-cost/build-validation.json'),('../build/tvalue-authority-stream','docs/tvalue-authority-stream/build-validation.json')]:
 b=json.loads(Path(proof).read_text());assert all(hashlib.sha256((Path(directory)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'].items())
assert not subprocess.check_output(['git','diff',build['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream'])
s={'parent':build['parent'],'final_JVMs':9,'preliminary_fixture_failures':0,'successful_traces':18,'replayed_authority_views':2700,'serialized_value_entries_checked':entries,'positive_mutation_steps':432,'expected_negative_cases':27,'native_enumeration_controls':432,'canonical_reference_checks':3888,'contexts_per_session':3,'trace_context_widths':[8,9,10],'explicit_resets_per_trace':2,'identical_pair_traces':True,'identical_traces_across_flags_for_each_seed':True,'native_observer_purity_checked':True,'setter_fanout_and_parent_isolation_checked':True,'full_oracle_never_disabled':True,'implementation_changed':False,'production_changed':False,'native_hook_integration_rerun':False,'native_transaction_boundaries_qualified':False,'new_parent_values_after_child_setup_qualified':False,'persistent_storage_qualified':False,'end_to_end_inference_speedup_qualified':False,'frozen_complete_corpus_semantics_equal':False,'diagnostic_baseline_remains':'docs/tvalue-authority-stream/StreamAuthorityJournal.java','trace_replays':traces};(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('CONTEXTS_ANALYSIS_OK JVMs=9 traces=18 views=2700 value_entries=97200 negatives=27')
