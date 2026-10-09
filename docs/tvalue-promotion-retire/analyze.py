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
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes();log=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert f"PROMOTION_RETIRE_OK selected={r['selected']} rounds=6 promotions=3 discards=3 negative=3 controls=176" in log
 pair=[]
 for mode in ('old','memo'):
  path=p/(label+'.'+mode+'.trace.gz');stats=replay(path);assert stats['contexts']==14 and stats['observations']==66 and stats['seeds']==14 and stats['resets']==0 and stats['retired']==13 and stats['deferred']==0
  assert stats['changes']==53 and stats['touches']==202 and stats['bucketReads']==78 and stats['mapChanges']==0
  assert stats['transitions']=={'added':36,'removed':32,'deleted':0,'resurrected':0,'order_changes':0,'term_changes':1}
  rows=gzip.decompress(path.read_bytes()).decode().splitlines();views={};counts={};retired=set();changes={};touches={}
  for row in rows:
   kind=row.split()[0]
   if kind=='DIRTY_JOURNAL_OK':continue
   kv=dict(x.split('=',1) for x in row.split()[1:]);ctx=int(kv['ctx']);assert ctx not in retired
   if kind=='RETIRE':retired.add(ctx)
   if kind=='TOUCH':touches.setdefault(kv['reason'],[]).append(ctx)
   if kind=='CHANGE':changes.setdefault(kv['reason'],[]).append(ctx)
   if kind=='VIEW':
    view=parseview(kv['view']);views[(ctx,kv['reason'])]=view;counts[ctx]=counts.get(ctx,0)+1;entries+=sum(len(v) for v in view.values());assert kv['reason']!='after-retirement'
  assert retired==set(range(2,15));assert counts=={1:22,2:20,**{i:(3 if i%2 else 1) for i in range(3,15)}}
  baseline=views[(1,'baseline')];assert baseline==views[(2,'baseline')];previous=baseline
  for round in range(6):
   child=3+2*round;fresh=child+1
   assert views[(child,'child-baseline-'+str(round))]==previous
   added=views[(child,'child-added-'+str(round))];assert all(len(added[k])==len(previous[k])+1 and added[k][:-1]==previous[k] for k in previous)
   assert views[(1,'parent-before-'+str(round))]==previous and views[(2,'sibling-before-'+str(round))]==baseline
   if round%2==0:
    assert views[(1,'promoted-'+str(round))]==added and views[(child,'promoted-'+str(round))]==added;assert changes['promoted-'+str(round)]==[1]*4;previous=added
   else:
    assert views[(child,'cleared-'+str(round))]==previous and changes['cleared-'+str(round)]==[child]*4;assert views[(1,'discarded-'+str(round))]==previous
   assert views[(1,'settled-'+str(round))]==previous and views[(2,'settled-'+str(round))]==baseline and views[(fresh,'fresh-'+str(round))]==previous
  assert views[(2,'promoted-payload-rewrite')]==baseline and changes['promoted-payload-rewrite']==[1]
  assert changes['only-parent-live']==[1]*4 and views[(1,'only-parent-live')]==views[(1,'session-end')]=={}
  assert 'retired-touch' not in touches and touches['native-term-rewrite']==[1,2] and touches['root-pack']==[1]*20
  assert touches['promote']==sum(([1,2,3+2*round]*width for round,width in [(0,12),(2,16),(4,20)]),[])
  traces[path.name]=stats;pair.append(path.read_bytes())
 assert pair[0]==pair[1];per_selected.setdefault(r['selected'],set()).add(pair[0]);errors=(p/(label+'.negative-errors.txt')).read_text().splitlines();assert len(errors)==3 and errors[0].count('dirty projection mismatch ctx=1')==2
 assert errors[1:] == ['mode=1 journal errors [java.lang.AssertionError:retired unfinished context]','mode=2 journal errors [java.lang.AssertionError:retired unfinished context]']
assert all(len(v)==1 for v in per_selected.values());assert entries==15264 and len(traces)==18
b=json.loads((p/'build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-promotion-retire')/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'].items());prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-smart-base')/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items())
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-authority-contexts','docs/tvalue-parent-add','docs/tvalue-smart-base'])
a=p/'preliminary-missing-pack';r=json.loads((a/'0-on.result.json').read_text());assert r['exit_code']==1 and (a/'0-on.err').read_text().count('dirty projection mismatch ctx=1')==2 and 'authority={}' in (a/'0-on.err').read_text()
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':9,'excluded_preliminary_fixture_failures':1,'successful_traces':18,'replayed_authority_views':1188,'serialized_value_entries_checked':entries,'expected_negative_cases':27,'native_context_variable_controls':1584,'typed_factory_promotions':27,'child_clears_and_discards':27,'contexts_per_trace':14,'retired_contexts_per_trace':13,'native_last_reservation_pack_checked':True,'native_reservations_zero_at_finish':True,'retired_context_reads_and_events_absent':True,'pair_and_flag_traces_identical':True,'full_oracle_never_disabled':True,'diagnostic_implementation_changed':False,'production_changed':False,'whole_Mind_commit_qualified':False,'native_hook_integration_rerun':False,'SMART_backend_persistence_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_inference_speedup_qualified':False,'trace_replays':traces};(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('PROMOTION_RETIRE_ANALYSIS_OK JVMs=9 traces=18 views=1188 entries=15264 negatives=27')
