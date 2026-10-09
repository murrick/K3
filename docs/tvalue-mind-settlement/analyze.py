from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};per_rep={};entries=0
modes=['accept','reject','user-reject','analyzer-exception','completion-exception','post-commit','post-reject']
results=list(p.glob('*.result.json'));assert len(results)==9
expected_outcomes=[('accept','true','true','none'),('reject','false','false','none'),('user-reject','false','false','none'),('analyzer-exception','false','false','analyzer'),('completion-exception','false','false','commit'),('post-commit','false','true','settled-COMMITTED'),('post-reject','false','false','settled-REJECTED')]
for q in results:
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();log=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert f"MIND_SETTLEMENT_OK repetition={r['repetition']} modes=7 negative=3 controls=37" in log
 outcomes=(p/(label+'.outcomes.txt')).read_text().splitlines();assert len(outcomes)==9
 for i,(mode,accepted,applied,failure) in enumerate(expected_outcomes):assert outcomes[i]==f'SETTLEMENT_CASE mode={mode} gap=false accepted={accepted} applied={applied} reservation=0 failure={failure}'
 assert outcomes[7:] == ['SETTLEMENT_CASE mode=accept gap=true accepted=true applied=true reservation=0 failure=none','SETTLEMENT_CASE mode=post-commit gap=true accepted=false applied=true reservation=0 failure=settled-COMMITTED']
 pair=[]
 for mode in ('old','memo'):
  path=p/(label+'.'+mode+'.trace.gz');stats=replay(path);assert stats['contexts']==14 and stats['observations']==51 and stats['seeds']==14 and stats['resets']==0 and stats['retired']==7 and stats['deferred']==7
  assert stats['changes']==2 and stats['touches']==28 and stats['bucketReads']==8 and stats['mapChanges']==0
  assert stats['transitions']=={'added':2,'removed':0,'deleted':0,'resurrected':0,'order_changes':0,'term_changes':0}
  rows=gzip.decompress(path.read_bytes()).decode().splitlines();views={};counts={};retired=set();changed=[];touches=[]
  for row in rows:
   kind=row.split()[0]
   if kind=='DIRTY_JOURNAL_OK':continue
   kv=dict(x.split('=',1) for x in row.split()[1:]);ctx=int(kv['ctx']);assert ctx not in retired
   if kind=='RETIRE':retired.add(ctx)
   if kind=='TOUCH':assert kv['reason']=='settlement-return';touches.append(ctx)
   if kind=='CHANGE':assert kv['reason']=='after-settlement';changed.append(ctx)
   if kind=='VIEW':
    assert not kv['reason'].endswith('-deferred');view=parseview(kv['view']);assert len(view)==1;views[(ctx,kv['reason'])]=view;counts[ctx]=counts.get(ctx,0)+1;entries+=sum(len(v) for v in view.values())
  assert retired==set(range(2,15,2)) and changed==[1,11] and touches==sum(([parent,parent+1]*2 for parent in range(1,14,2)),[])
  assert counts=={**{i:6 if i%2 else 1 for i in range(1,15)},5:7,6:2}
  for index,case in enumerate(modes):
   parent=1+2*index;child=parent+1;baseline=views[(parent,case+'-parent-baseline')];childView=views[(child,case+'-child-baseline')];assert all(len(v)==1 for v in baseline.values()) and all(len(v)==2 for v in childView.values())
   assert views[(parent,case+'-before')]==baseline
   final=childView if case in ('accept','post-commit') else baseline
   assert views[(parent,'after-settlement')]==views[(parent,case+'-final')]==views[(parent,'session-end')]==final
   if case=='user-reject':assert views[(child,'live-user-rejected-child')]==childView and views[(parent,'user-rejected-rollback')]==baseline
  traces[path.name]=stats;pair.append(path.read_bytes())
 assert pair[0]==pair[1];per_rep.setdefault(r['repetition'],set()).add(pair[0]);errors=(p/(label+'.negative-errors.txt')).read_text().splitlines();assert len(errors)==3
 for mode in range(2):assert errors[mode].startswith('mode='+str(mode)+' ') and errors[mode].count('dirty projection mismatch ctx=1')==2 and 'reason=after-settlement' in errors[mode] and 'reason=session-end' in errors[mode]
 assert errors[2]=='mode=2 journal errors [java.lang.AssertionError:retired unfinished context]'
assert all(len(v)==1 for v in per_rep.values());assert len(traces)==18 and entries==1170
b=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-mind-settlement')/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'].items());prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-smart-base')/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items())
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-authority-contexts','docs/tvalue-parent-add','docs/tvalue-smart-base','docs/tvalue-promotion-retire'])
a=p/'preliminary-sequenced-fixture';r=json.loads((a/'0-on.result.json').read_text());assert r['exit_code']==1 and 'native non-sequenced branch reached' in (a/'0-on.err').read_text()
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':9,'excluded_preliminary_fixture_failures':1,'successful_traces':18,'replayed_authority_views':918,'serialized_value_entries_checked':entries,'positive_composite_settlement_cases':63,'modes_per_positive_session':7,'expected_negative_cases':27,'native_context_variable_controls':333,'native_reservations_zero_after_final_cleanup':True,'all_eight_composite_checkpoint_stacks_zero_after_target_call':True,'user_rejection_retains_reservation_until_explicit_release':True,'post_settlement_COMMITTED_and_REJECTED_classification_checked':True,'pair_and_flag_traces_identical':True,'full_oracle_never_disabled':True,'diagnostic_implementation_changed':False,'production_changed':False,'whole_Mind_commit_TValue_boundary_qualified':True,'all_factory_payload_atomicity_qualified':False,'native_hook_integration_rerun':False,'SMART_backend_persistence_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_inference_speedup_qualified':False,'trace_replays':traces};(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('MIND_SETTLEMENT_ANALYSIS_OK JVMs=9 traces=18 views=918 entries=1170 positive_cases=63 negatives=27')
