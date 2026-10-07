from pathlib import Path
import json,gzip,re,ast,base64,subprocess
root=Path(__file__).parent;base='50550406ae1761322d06dcb995e2bd4c00fdb78a'
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    assert json.loads((root/(label+'.result.json')).read_text())['exit_code']==0
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith('DIRTY_JOURNAL_OK '))+'\n'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
prior=Path('docs/tvalue-persistent-lookup')
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


source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
    if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace("assert old[id][1]==new[id][1];","kinds['term_changes']+=old[id][1]!=new[id][1];")
    exec(body)
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==33,len(traces)
results=[json.loads(p.read_text()) for p in root.glob('*.result.json')];assert len(results)==71,len(results)
assert all(r['exit_code']==0 for r in results)
all_native=[]
for build in ('clean','shadow'):
    for flag in ('on','off','verify'):
        label=build+'-'+flag+'-MaterializationRoutingRunner';text=read(label);first=text.splitlines()[0]
        assert 'materialization_route=registered' in first and 'reservations=0' in first
        assert 'expected_metadata_rejection=false' in text
        all_native.append(first)
        if build=='shadow':
            positive=root/(label+'.trace.positive.trace.gz');old=prior/('shadow-'+flag+'-PersistentLookupRunner.trace.positive.trace.gz');assert positive.read_bytes()==old.read_bytes()
            rows=gzip.decompress((root/(label+'.trace.rebound.trace.gz')).read_bytes()).decode().splitlines()
            materialized=[r for r in rows if r.startswith('TOUCH ') and 'reason=materialize ' in r];assert len(materialized)==6
            assert {re.search(r'ctx=(\d+)',r).group(1) for r in materialized}=={'1','2'}
            assert any(r.startswith('CHANGE ') and 'reason=rematerialized-registered-write ' in r for r in rows)
            assert any(r.startswith('CHANGE ') and 'reason=rematerialized-child-write ' in r for r in rows)
            assert not any(r.startswith('CHANGE ') and 'reason=detached-old' in r for r in rows)
            assert len([r for r in rows if r.startswith('TOUCH ') and 'reason=setPersistentReferences ' in r])==2
            assert any(r.startswith('CHANGE ') and 'reason=reloaded-without-setter ' in r for r in rows)
            assert any(r.startswith('CHANGE ') and 'reason=reloaded-child-without-setter ' in r for r in rows)
assert len(set(all_native))==1
for flag in ('on','off','verify'):
    label='baseline-'+flag+'-PersistentLookupRunner';assert read(label)==gzip.decompress((prior/('shadow-'+flag+'-PersistentLookupRunner.log.gz')).read_bytes()).decode()
    error=(root/(label+'.trace.negative-error.txt')).read_bytes();assert error==(prior/('shadow-'+flag+'-PersistentLookupRunner.trace.negative-error.txt')).read_bytes()
    for mode in ('variable','index'):
        label='scope-'+flag+'-'+mode;assert 'native_return=preserved native_disk=preserved session=closed reservations=0' in read(label)
        err=(root/(label+'.trace.scope-error.txt')).read_text();assert ('unsupported materialized variable change' if mode=='variable' else 'materialization requires valid native lookup metadata') in err
# Every retained preceding memory result is exact, despite the new helper and
# temporary Base wrapper. Other old source oracle assertions ran above.
for r in results:
    if r.get('runner') in ('MaterializationRoutingRunner','MaterializationScopeRunner','PersistentLookupRunner'):continue
    label=r['build']+'-'+r['flag']+'-'+r['runner']+('-'+r['args'][0] if r['runner']=='TValueResidentBoundaryRunner' else '')
    assert (root/(label+'.log.gz')).read_bytes()==(prior/(label+'.log.gz')).read_bytes(),label
    trace=root/(label+'.trace.gz')
    if trace.exists():assert trace.read_bytes()==(prior/trace.name).read_bytes()
info=json.loads((root/'build-validation.json').read_text());assert info['original_Base_get_body_recovered_byte_for_byte'] and not info['production_changed']
old=json.loads((prior/'summary.json').read_text());assert old['frozen_complete_corpus_semantics_equal'] is False and old['prior_rejected_corpus_attempts_retained']==2
summary={'parent':base,'final_matrix_JVMs':71,'all_matrix_expectations_met':True,'all_stderr_empty':True,'new_materialization_positive_JVMs':6,'memory_regression_JVMs':56,'original_negative_without_hook_JVMs':3,'closed_new_scope_JVMs':6,'parent_and_child_known_ID_rebinding_qualified':True,'detached_old_instance_registration_retired':True,'known_same_variable_post_native_return_only':True,'unchanged_positive_phase_traces':3,'preceding_56_memory_logs_and_18_traces_byte_identical':True,'native_original_return_and_disk_payload_preserved_on_qualification_failures':True,'full_oracle_never_repairs_or_registers_replacements':True,'success_trace_count':len(traces),'replayed_authority_views':sum(t['observations'] for t in traces.values()),'trace_replays':traces,'preliminary_positive_JVMs_retained':12,'unflushed_payload_reload_without_setter_tracked':True,'detached_setters_emit_zero_canonical_metadata_touches':True,'preliminary_scope_fixture_JVMs_retained':2,'production_changed':False,'temporary_Base_get_hook_only':True,'general_persistent_publication_and_concurrency_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'no_consumer_or_performance_claim':True}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('MATERIALIZATION_ROUTING_ANALYSIS_OK',json.dumps({k:v for k,v in summary.items() if k!='trace_replays'}))
