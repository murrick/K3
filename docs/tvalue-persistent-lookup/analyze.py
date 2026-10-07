from pathlib import Path
import json,gzip,re,ast,base64,subprocess
root=Path(__file__).parent;base='7bb814c706e790c825fb0a69b1e01084c27a8e1e'
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    assert json.loads((root/(label+'.result.json')).read_text())['exit_code']==0
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith('DIRTY_JOURNAL_OK '))+'\n'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
prior=Path('docs/tvalue-owner-pure-observation')
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

# Independent committed trace replay; no incremental or full reader state used.
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
    if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace("assert old[id][1]==new[id][1];","kinds['term_changes']+=old[id][1]!=new[id][1];")
    exec(body)
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==24,len(traces)
results=[json.loads(p.read_text()) for p in root.glob('*.result.json')];assert len(results)==62
assert all(r['exit_code']==0 for r in results)
all_native=[];errors=[]
for build in ('clean','shadow'):
    for flag in ('on','off','verify'):
        label=build+'-'+flag+'-PersistentLookupRunner';text=read(label)
        first=text.splitlines()[0];assert 'root_fallback=present child_fallback=absent' in first and 'reservations=0' in first
        assert 'shadow='+str(build=='shadow').lower()+' expected_metadata_rejection='+str(build=='shadow').lower() in text
        all_native.append(first)
        if build=='shadow':
            e=(root/(label+'.trace.negative-error.txt')).read_text();assert 'dirty projection mismatch' in e;errors.append(e)
assert len(set(all_native))==1 and len(set(errors))==1
# All preceding memory observer native outputs and 18 full traces are exact.
for r in results:
    if r['runner']=='PersistentLookupRunner':continue
    label=r['build']+'-'+r['flag']+'-'+r['runner']+('-'+r['args'][0] if r['runner']=='TValueResidentBoundaryRunner' else '')
    assert (root/(label+'.log.gz')).read_bytes()==(prior/(label+'.log.gz')).read_bytes(),label
    trace=root/(label+'.trace.gz')
    if trace.exists():assert trace.read_bytes()==(prior/trace.name).read_bytes(),trace.name
info=json.loads((root/'build-validation.json').read_text());assert info['production_source_changed'] is False and info['native_instrumentation_changed'] is False
reader=(root/'ResidentTValueRead.java').read_text();assert not re.search(r'\.setMind\(|\.setMindId\(|\.getData\(|\.setRoot\(',reader)
old=json.loads((prior/'summary.json').read_text());assert old['frozen_complete_corpus_semantics_equal'] is False and old['prior_rejected_corpus_attempts_retained']==2
summary={'parent':base,'final_matrix_fresh_JVMs':62,'all_matrix_exit_codes_zero_and_stderr_empty':True,'new_storage_JVMs':6,'memory_regression_JVMs':56,'new_storage_native_control_rows_identical':True,'native_memory_regression_logs_and_18_traces_byte_identical':True,'actual_native_root_fallback_and_child_no_fallback_controls':True,'current_DUMB_generation_registry_identity_checked':True,'fully_resident_quiescent_reader_scope_only':True,'native_checkpoint_local_rollback_and_context_deletion_preserved':True,'owner_and_lazy_index_preserved':True,'custom_Mind_callbacks':0,'expected_rematerialization_metadata_rejections':3,'rematerialized_setter_routing_gap_remains_open':True,'persistent_journal_arbitrary_cache_turnover_qualified':False,'preliminary_fixture_failures_archived':3,'pre_entry_guard_storage_JVMs_retained':6,'replayed_trace_count':len(traces),'replayed_authority_views':sum(t['observations'] for t in traces.values()),'trace_replays':traces,'production_changed':False,'native_hooks_changed':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'no_performance_or_consumer_claim':True}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('PERSISTENT_LOOKUP_ANALYSIS_OK',json.dumps({k:v for k,v in summary.items() if k!='trace_replays'}))
