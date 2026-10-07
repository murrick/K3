from pathlib import Path
import json,gzip,re,ast,base64,subprocess
root=Path(__file__).parent;base='b2c13ae8a4cb99f177a200a22b6a23748491bf0a'
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    assert json.loads((root/(label+'.result.json')).read_text())['exit_code']==0
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith(('DIRTY_JOURNAL_OK ','EXPECTED_ID_REJECTION ')))+'\n'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
for flag in ('on','off','verify'):
    a=read('clean-'+flag+'-TValuePersistenceRunner');b=read('shadow-'+flag+'-TValuePersistenceRunner');assert a==native(b)
    assert a==read('clean-on-TValuePersistenceRunner')
    checks=re.findall(r'^TVALUE_PERSISTENCE_OK checks=(\d+)$',a,re.M);assert checks==['22'],checks
    for runner in ('TValueMetadataSafetyRunner','TValueDirtyJournalSafetyRunner'):
        label='shadow-'+flag+'-'+runner;prior=Path('docs/tvalue-metadata-observation')/(label+'.log.gz')
        assert read(label)==gzip.decompress(prior.read_bytes()).decode(),label
        assert (root/(label+'.trace.gz')).read_bytes()==(prior.parent/(label+'.trace.gz')).read_bytes(),label
oracle=json.loads(Path('docs/linker-donor-universe/son-expected.json').read_text())
for flag in ('on','off'):
    for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner'):
        a=read('clean-'+flag+'-'+runner);b=read('shadow-'+flag+'-'+runner);assert a==native(b)
        prior=Path('docs/tvalue-metadata-observation')/('clean-'+flag+'-'+runner+'.log.gz');assert a==gzip.decompress(prior.read_bytes()).decode()
    for build in ('clean','shadow'):
        son={};small={}
        for row in read(build+'-'+flag+'-ExactCandidateReplayRunner').splitlines():
            if row.startswith('ROW '):
                f=row.split();kv=dict(p.split('=',1) for p in f[3:]);src=base64.b64decode(kv['source']).decode();data={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
                if f[1]=='son':assert src not in son;son[src]=data
                else:small.setdefault(f[1],[]).append(data['answer'])
        assert son==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
    for suffix in ('state','work'):
        a=root/('clean-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix);b=root/('shadow-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix)
        assert a.read_bytes()==b.read_bytes()==(Path('docs/tvalue-metadata-observation')/a.name).read_bytes()
    for mode in ('setId','applyMap','truncated-apply'):
        a=read('clean-'+flag+'-TValuePersistenceGapRunner-'+mode);b=read('shadow-'+flag+'-TValuePersistenceGapRunner-'+mode)
        assert a==native(b) and 'EXPECTED_ID_REJECTION mode='+mode in b
owner={}
for flag in ('on','off'):
    owner[flag]={}
    for mode in ('none','foreach','journal'):
        a=read('clean-'+flag+'-TValueOwnerReadWitness-'+mode);b=read('shadow-'+flag+'-TValueOwnerReadWitness-'+mode);assert a==native(b)
        child=mode=='none';expected='after_child='+str(child).lower()+' pack_deleted=1->'+str(1 if child else 0)
        assert expected in a and 'root_deleted=false child_deleted=true' in a and 'reservations=0' in a
        row=next(r for r in a.splitlines() if r.startswith('OWNER_READ_NATIVE '));fields=dict(p.split('=',1) for p in row.split()[1:]);owner[flag][mode]=fields
    assert len({row['canonical_tuple'] for row in owner[flag].values()})==1 and len({row['native_owner_ID'] for row in owner[flag].values()})==1
assert owner['on']==owner['off']
for build in ('clean','shadow'):assert 'MIND_COMMIT_EXCEPTION_ATOMICITY_OK' in read(build+'-on-KangerMindCommitExceptionAtomicitySafetyRunner')
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
    if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace("assert old[id][1]==new[id][1];","kinds['term_changes']+=old[id][1]!=new[id][1];")
    exec(body)
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==19,len(traces)
for flag in ('on','off','verify'):
    p=root/('shadow-'+flag+'-TValuePersistenceRunner.trace.gz');rows=gzip.decompress(p.read_bytes()).decode().splitlines()
    assert p.read_bytes()==(root/'shadow-on-TValuePersistenceRunner.trace.gz').read_bytes()
    for reason in ('map-term-change','binary-term-change','truncated-apply-survives','partial-map-survives','map-payload-survives-release'):
        assert any(r.startswith('CHANGE ') and 'reason='+reason+' ' in r for r in rows),reason
    for reason in ('map-deleted-false','binary-deleted-false','owner-ID-only','same-ID-and-owner-restored','unregistered-persistence-probe','restore-resident-term'):
        assert not any(r.startswith('CHANGE ') and 'reason='+reason+' ' in r for r in rows),reason
    before=next(i for i,r in enumerate(rows) if r.startswith('VIEW ') and 'reason=same-ID-and-owner-restored ' in r);after=next(i for i,r in enumerate(rows) if r.startswith('VIEW ') and 'reason=unregistered-persistence-probe ' in r)
    assert not any(r.startswith('TOUCH ') for r in rows[before+1:after]),'unregistered persistent probe invalidated canonical bucket'
    assert traces[p.name]['transitions']['term_changes']==8,traces[p.name]
old=json.loads(Path('docs/tvalue-metadata-observation/summary.json').read_text());assert old['frozen_complete_corpus_semantics_equal'] is False and old['prior_rejected_corpus_attempts_retained']==2
assert len(list(root.glob('*.result.json')))==46
structure=json.loads((root/'structural-check.json').read_text());assert all(v['all_original_code_recovered_byte_for_byte'] for v in structure['shadow'].values())
summary={'base':base,'production_qualification_and_workflows_unchanged':True,'completed_matrix_jvms':46,'all_matrix_exit_codes_zero_and_stderr_empty':True,'all_stage_expectations_met':True,'expected_identity_rejections':6,'owner_read_controls':12,'owner_observation_transparency_qualified':False,'owner_pack_deleted_flag_changed_by_journal_read':True,'owner_probe_native_rows':owner,'persistent_native_assertions_per_fixture':22,'persistent_clean_shadow_native_stdout_equal':True,'prior_19_metadata_assertion_gate_and_trace_identical_in_three_flags':True,'prior_83_dirty_assertion_gate_and_trace_identical_in_three_flags':True,'candidate_source_oracles':4,'sources_per_oracle':47,'transaction_state_work_and_stdout_equal':True,'transaction_operations_per_jvm':20,'native_commit_exception_gates':2,'independent_trace_replays':traces,'replayed_authority_views':sum(v['observations'] for v in traces.values()),'class_comparison':json.loads((root/'class-comparison.json').read_text()),'temporary_setter_coverage':['setValue','setTVar','setPersistentReferences','setId','apply','applyMap'],'qualified_projection':'ordered TValueID/TermID/context-deletion bits; stable TValueID and variableID only','same_ID_setter_supported':True,'ID_and_variable_changes_rejected_after_native_write':True,'binary_apply_leaves_stale_resident_reference':True,'applyMap_clears_references_only_after_all_fields_parse':True,'deleted_false_does_not_restore_existing_deletion':True,'partial_native_writes_recorded_even_when_restore_throws':True,'owner_reference_and_owner_ID_not_in_projection':True,'native_indices_not_repaired':True,'diagnostic_owner_side_effect_repaired':False,'initial_compile_error_and_two_initial_executions_archived_not_qualified':True,'initial_completed_clean_fixture_excluded':1,'initial_shadow_class_not_found_excluded':1,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'corpus_not_rerun':True,'limitations':['full native oracle can rebind TValue owner through Step.getData(mind); observation is not semantically transparent for owner-sensitive operations such as pack','current clean/shadow equality is scoped to measured native programs and recorded canonical projection','no production consumer, runtime speedup, index repair or ownership compensation','ThreadLocal native Mind/TValueFactory sessions; no arbitrary worker, extension or storage hydration qualification','resident object references and owner metadata are outside projection','earlier overlapping-preparation corpus remains unqualified']}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('TVALUE_PERSISTENCE_ANALYSIS_OK',json.dumps({k:v for k,v in summary.items() if k not in ('independent_trace_replays','owner_probe_native_rows','limitations')}))
