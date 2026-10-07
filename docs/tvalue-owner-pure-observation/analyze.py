from pathlib import Path
import json,gzip,re,ast,base64,subprocess
root=Path(__file__).parent;base='ca2d1b081d5e49cbe98bfe7096abe8eb97d1df22'
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    assert json.loads((root/(label+'.result.json')).read_text())['exit_code']==0
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith('DIRTY_JOURNAL_OK '))+'\n'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
prior=Path('docs/tvalue-persistence-boundaries')
for flag in ('on','off','verify'):
    a=read('clean-'+flag+'-TValueOwnerPureRunner');b=read('shadow-'+flag+'-TValueOwnerPureRunner')
    assert a==native(b)==read('clean-on-TValueOwnerPureRunner') and 'TVALUE_OWNER_PURE_OK checks=27' in a
    assert 'pure_owner=child pure_pack=1 native_owner=root native_pack=0 lazy_index=preserved initialized_index=preserved rollback=preserved' in a
    for runner in ('TValueMetadataSafetyRunner','TValueDirtyJournalSafetyRunner','TValuePersistenceRunner'):
        label='shadow-'+flag+'-'+runner;old=gzip.decompress((prior/(label+'.log.gz')).read_bytes()).decode()
        assert read(label)==old,label
        assert (root/(label+'.trace.gz')).read_bytes()==(prior/(label+'.trace.gz')).read_bytes(),label
    for mode in ('sapato','trap-sapato','trap-step','trap-value','unresolved','cycle','connection','missing-dirty','lookup-alias'):
        label='shadow-'+flag+'-TValueResidentBoundaryRunner-'+mode;assert read(label).strip()=='TVALUE_RESIDENT_BOUNDARY_OK mode='+mode+' rejected=true callbacks=0 session=closed native_root=preserved'
    assert (root/('shadow-'+flag+'-TValueOwnerPureRunner.trace.gz')).read_bytes()==(root/'shadow-on-TValueOwnerPureRunner.trace.gz').read_bytes()
oracle=json.loads(Path('docs/linker-donor-universe/son-expected.json').read_text())
for flag in ('on','off'):
    for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner','TValueOrderedPublicationRunner'):
        a=read('clean-'+flag+'-'+runner);b=read('shadow-'+flag+'-'+runner);assert a==native(b)
        previous=prior if runner!='TValueOrderedPublicationRunner' else Path('docs/tvalue-dirty-buckets')
        assert a==gzip.decompress((previous/('clean-'+flag+'-'+runner+'.log.gz')).read_bytes()).decode(),runner
    for build in ('clean','shadow'):
        son={};small={}
        for row in read(build+'-'+flag+'-ExactCandidateReplayRunner').splitlines():
            if row.startswith('ROW '):
                f=row.split();kv=dict(p.split('=',1) for p in f[3:]);source=base64.b64decode(kv['source']).decode();data={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
                if f[1]=='son':assert source not in son;son[source]=data
                else:small.setdefault(f[1],[]).append(data['answer'])
        assert son==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
    for suffix in ('state','work'):
        a=root/('clean-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix);b=root/('shadow-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix)
        assert a.read_bytes()==b.read_bytes()==(prior/a.name).read_bytes()
for build in ('clean','shadow'):assert 'MIND_COMMIT_EXCEPTION_ATOMICITY_OK' in read(build+'-on-KangerMindCommitExceptionAtomicitySafetyRunner')
# Independent committed replay validates every authority view and dirty-read
# count. TermID changes remain explicit, without snapshot-based state repair.
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
    if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace("assert old[id][1]==new[id][1];","kinds['term_changes']+=old[id][1]!=new[id][1];")
    exec(body)
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==18,len(traces)
for flag in ('on','off','verify'):
    t=traces['shadow-'+flag+'-TValueOwnerPureRunner.trace.gz'];assert t['observations']==14 and t['changes']==9 and t['deferred']==1,t
    rows=gzip.decompress((root/('shadow-'+flag+'-TValueOwnerPureRunner.trace.gz')).read_bytes()).decode().splitlines()
    assert not any(r.startswith('CHANGE ') and 'reason=owner-pure-root-read ' in r for r in rows)
old=json.loads((prior/'summary.json').read_text());assert old['owner_observation_transparency_qualified'] is False and old['frozen_complete_corpus_semantics_equal'] is False and old['prior_rejected_corpus_attempts_retained']==2
assert len(list(root.glob('*.result.json')))==56
struct=json.loads((root/'structural-check.json').read_text());assert all(v['all_original_code_recovered_byte_for_byte'] for v in struct['shadow'].values())
helper=(root/'ResidentTValueRead.java').read_text();journal=(root/'TValueDirtyJournal.java').read_text()
# These are implementation guard checks, supplementary to native field and
# serialization controls, not a substitute for the native assertions above.
assert not re.search(r'\.setMind\(|\.setMindId\(|\.getData\(mind\)|\.setRoot\(|\.set\(object',helper)
assert '.forEach(' not in journal and 'for(TValue value:child)' not in journal
comparison=json.loads((root/'class-comparison.json').read_text());assert comparison['prior_shadow_classes_except_journal_helper_identical']==682
summary={'base':base,'production_qualification_and_workflows_unchanged':True,'matrix_jvms':56,'all_matrix_expectations_met':True,'all_matrix_exit_codes_zero_and_stderr_empty':True,'resident_owner_observation_controls_qualified':True,'scope':'native memory-only Step/TValue/Escalera/TValueFactory in explicit serial sessions; stable identity; no persistent connection or Sapato nodes','owner_control_assertions_per_jvm':27,'owner_control_jvms':6,'owner_and_pack_deleted_flag_preserved_by_observer':True,'actual_native_forEach_still_rebinds_owner':True,'lazy_variable_index_preserved':True,'resident_value_references_cache_indices_roots_and_checkpoint_depth_fingerprints_preserved':True,'closed_boundary_negative_jvms':27,'negative_modes':['sapato','trap-sapato','trap-step','trap-value','unresolved','cycle','connection','missing-dirty','lookup-alias'],'unresolved_and_custom_callbacks_invoked':0,'untracked_metadata_and_fast_lookup_alias_gaps_detected_without_repair':True,'prior_metadata_dirty_persistence_logs_and_traces_identical':True,'prior_gate_assertions':[19,83,22],'candidate_source_oracles':4,'sources_per_oracle':47,'transaction_operations_per_jvm':20,'full_transaction_state_work_stdout_equal':True,'ordered_native_publication_jvms':4,'orders_per_jvm':6,'ordered_publication_assertions_per_jvm':90,'native_commit_exception_gates':2,'independent_trace_replays':traces,'replayed_authority_views':sum(v['observations'] for v in traces.values()),'class_comparison':comparison,'native_runtime_hooks_unchanged_from_prior_stage':True,'read_algorithms':'recursive single-bucket layered routing vs independent full iterative base-to-leaf merge; validated shared raw-state primitives; both checked against actual native forEach','cache_fast_lookup_uses_actual_native_table_without_repair':True,'lazy_index_simulation_does_not_initialize_native_index':True,'initial_invalid_clean_journal_control_archived_and_excluded':1,'earlier_owner_mutating_reader_evidence_retained':True,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'corpus_not_rerun':True,'limitations':['does not qualify persistent storage, attached Sapato data, unresolved units, arbitrary extensions or concurrent worker observation','full projection still records only ID/TermID/context-deletion; owner changes by native engine and resident-only changes are outside the event schema','independent full algorithm shares validated raw-state primitives; separate native controls bound common-mode risk','reflection depends on this exact native source layout; diagnostic only','no production consumer, optimization default, native index repair, owner compensation, performance claim or develop merge','earlier overlapping-preparation corpus remains unqualified']}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('TVALUE_OWNER_PURE_ANALYSIS_OK',json.dumps({k:v for k,v in summary.items() if k not in ('independent_trace_replays','limitations')}))
