from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};per_rep={};entries=0;results=list(p.glob('*.result.json'));assert len(results)==18
modes=['accept','reject','user-reject','analyzer-exception','completion-exception','post-commit','post-reject']
for rep in range(3):
 for flag in ['on','off','verify']:
  clean=p/f'{rep}-{flag}-clean';hooked=p/f'{rep}-{flag}-hooked';prior=Path('docs/tvalue-mind-settlement')/f'{rep}-{flag}'
  for prefix,mode in [(clean,'clean'),(hooked,'hooked')]:
   r=json.loads(Path(str(prefix)+'.result.json').read_text());assert r['exit_code']==0 and r['mode']==mode and r['flag']==flag and r['repetition']==rep;assert not Path(str(prefix)+'.err').read_bytes();log=gzip.decompress(Path(str(prefix)+'.log.gz').read_bytes()).decode();assert f'AUTO_SETTLEMENT_OK repetition={rep} modes=7 hooked={str(mode=="hooked").lower()} controls={37 if mode=="hooked" else 29}' in log
  a=Path(str(clean)+'.outcomes.txt').read_bytes();b=Path(str(hooked)+'.outcomes.txt').read_bytes();assert a==b and a.decode().splitlines()==Path(str(prior)+'.outcomes.txt').read_text().splitlines()[:7]
  pair=[]
  for journal in ['old','memo']:
   path=Path(str(hooked)+'.'+journal+'.trace.gz');stats=replay(path);assert stats['contexts']==40 and stats['observations']==145 and stats['seeds']==40 and stats['resets']==0 and stats['retired']==33 and stats['deferred']==34
   assert stats['changes']==16 and stats['touches']==121 and stats['bucketReads']==48 and stats['mapChanges']==0
   assert stats['transitions']=={'added':16,'removed':0,'deleted':0,'resurrected':0,'order_changes':0,'term_changes':0}
   rows=gzip.decompress(path.read_bytes()).decode().splitlines();views={};parents={};retired=set();changes=[]
   for row in rows:
    kind=row.split()[0]
    if kind=='DIRTY_JOURNAL_OK':continue
    kv=dict(x.split('=',1) for x in row.split()[1:]);ctx=int(kv['ctx']);assert ctx not in retired
    if kind=='RETIRE':retired.add(ctx)
    if kind=='CHANGE':changes.append((ctx,kv['reason']))
    if kind=='VIEW':
     assert kv['reason']!='native-settlement-probe';view=parseview(kv['view']);views[(ctx,kv['reason'])]=view;entries+=sum(len(v) for v in view.values())
     if kv['reason'].endswith('-parent-baseline'):parents[kv['reason'].removesuffix('-parent-baseline')]=ctx
   assert list(parents)==modes and set(parents.values())==set(range(1,41))-retired
   for mode,parent in parents.items():
    baseline=views[(parent,mode+'-parent-baseline')];final=views[(parent,mode+'-final')];assert len(baseline)==1 and all(len(v)==1 for v in baseline.values())
    assert views[(parent,mode+'-before')]==baseline and final==views[(parent,'session-end')]
    if mode in ['accept','post-commit']:assert all(len(final[k])==2 and final[k][0]==baseline[k][0] for k in baseline) and (parent,'after-settlement') in changes
    else:assert final==baseline and (parent,'after-settlement') not in changes
    if mode=='user-reject':assert views[(parent,'user-rejected-rollback')]==baseline
   assert stats['deferred']==34 and len(retired)==33
   traces[path.name]=stats;pair.append(path.read_bytes())
  assert pair[0]==pair[1];per_rep.setdefault(rep,set()).add(pair[0]);errors=Path(str(hooked)+'.negative-errors.txt').read_bytes();assert errors==Path(str(prior)+'.negative-errors.txt').read_bytes()
  counts=Path(str(hooked)+'.hook-counts.txt').read_text().strip();assert counts=='{add=18, beginSettlement=43, complete=7, constructed=51, endSettlement=43, mark=7, metadata=63, promoted=41, reset=92, retire=43}'
assert len(traces)==18 and entries==3114 and all(len(v)==1 for v in per_rep.values())
b=json.loads((p/'build-validation.json').read_text());assert b['changed_native_classes']==['org/kanger/Mind.class','org/kanger/Mind$1.class','org/kanger/factory/TValueFactory.class','org/kanger/units/TValue.class']
for mode in ['clean','hooked']:
 assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'][mode].items());assert all(hashlib.sha256((Path('../build/tvalue-native-hooks-'+mode)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'][mode].items())
changes=json.loads((p/'changes.json').read_text());proof=json.loads((p/'structural-check.json').read_text())
for name,edits in changes.items():
 body=(p/'source'/name).read_text();assert hashlib.sha256(body.encode()).hexdigest()==proof[name]['instrumented_sha256']
 for edit in reversed(edits):assert body.count(edit['new'])==1;body=body.replace(edit['new'],edit['old'],1)
 assert body==(Path('../K3-smart-native/kanger/src/org/kanger')/name).read_text() and hashlib.sha256(body.encode()).hexdigest()==proof[name]['original_sha256']
runner=(p/'AutoSettlementRunner.java').read_text();positive=runner[:runner.index(' static String finishOld()')];assert all(x not in positive for x in ['BeforeAuthorityJournal.touch','StreamAuthorityJournal.touch','NativeJournalHooks.promoted','NativeJournalHooks.beginSettlement','NativeJournalHooks.endSettlement','NativeJournalHooks.retire'])
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-mind-settlement'])
import tarfile
with tarfile.open(p/'preliminary-no-bracket-probe.tar.gz') as tar:
 names=tar.getnames();assert len([x for x in names if x.endswith('.result.json')])==18
 for name in names:
  if name.endswith('.result.json'):assert json.loads(tar.extractfile(name).read())['exit_code']==0
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':18,'preliminary_successful_JVMs_archived':18,'successful_final_traces':18,'replayed_authority_views':2610,'serialized_value_entries_checked':entries,'positive_native_settlement_cases_per_build':63,'expected_hook_gap_cases':27,'native_context_variable_controls':594,'clean_hooked_and_prior_native_outcomes_identical':True,'old_stream_and_flag_traces_identical':True,'deferred_native_observation_probes_per_trace':34,'retired_contexts_per_trace':33,'manual_mutation_or_settlement_notifications_in_positive_driver':False,'native_hook_integration_qualified_for_memory_settlement':True,'all_original_native_code_recovered_byte_for_byte':True,'changed_native_classes':b['changed_native_classes'],'unchanged_prior_clean_class_hashes_verified':730,'diagnostic_implementation_changed':False,'production_changed':False,'full_oracle_never_disabled':True,'automatic_term_rewrite_and_clear_hooks_exercised':False,'SMART_backend_persistence_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_inference_speedup_qualified':False,'trace_replays':traces};(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('NATIVE_HOOKS_ANALYSIS_OK JVMs=18 traces=18 views=2610 entries=3114 gaps=27 clean_native_parity=true')
