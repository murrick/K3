from pathlib import Path
import json,gzip,re,ast,base64,subprocess
root=Path(__file__).parent
base='95841a8f856f5cbe1c09e6f5cd71c26172d061e6'
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    result=json.loads((root/(label+'.result.json')).read_text());assert result['exit_code']==0
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith(('DIRTY_JOURNAL_OK ','TVALUE_METADATA_SAFETY_OK ','EXPECTED_OBSERVER_REJECTION ')))+'\n'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])
metadata=[]
for flag in ('on','off','verify'):
    a=read('clean-'+flag+'-TValueMetadataNativeRunner');b=read('shadow-'+flag+'-TValueMetadataSafetyRunner')
    assert a==native(b) and 'TVALUE_METADATA_NATIVE_OK checks=19' in a
    assert a==read('clean-on-TValueMetadataNativeRunner')
    assert b==read('shadow-on-TValueMetadataSafetyRunner')
    label='shadow-'+flag+'-TValueDirtyJournalSafetyRunner';prior=Path('docs/tvalue-dirty-buckets')/('journal-'+flag+'-TValueDirtyJournalSafetyRunner.log.gz')
    assert read(label)==gzip.decompress(prior.read_bytes()).decode()
    assert (root/(label+'.trace.gz')).read_bytes()==(prior.parent/(prior.name.replace('.log.gz','.trace.gz'))).read_bytes()
    metadata.append({'flag':flag,'checks':19,'native_and_shadow_equal':True,'prior_83_assertion_gate_and_trace_identical':True})
oracle=json.loads(Path('docs/linker-donor-universe/son-expected.json').read_text())
for flag in ('on','off'):
    for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner'):
        a=read('clean-'+flag+'-'+runner);b=read('shadow-'+flag+'-'+runner);assert a==native(b)
        prior=Path('docs/tvalue-dirty-buckets')/('clean-'+flag+'-'+runner+'.log.gz');assert a==gzip.decompress(prior.read_bytes()).decode()
    for build in ('clean','shadow'):
        text=read(build+'-'+flag+'-ExactCandidateReplayRunner');son={};small={}
        for row in text.splitlines():
            if row.startswith('ROW '):
                f=row.split();kv=dict(p.split('=',1) for p in f[3:]);source=base64.b64decode(kv['source']).decode();data={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
                if f[1]=='son':assert source not in son;son[source]=data
                else:small.setdefault(f[1],[]).append(data['answer'])
        assert son==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
    for suffix in ('state','work'):
        a=root/('clean-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix);b=root/('shadow-'+flag+'-LatentSolveSyncTransactionRunner.'+suffix)
        assert a.read_bytes()==b.read_bytes()==(Path('docs/tvalue-dirty-buckets')/a.name).read_bytes()
    for mode in ('setTVar','persistent-variable','applyMap'):
        a=read('clean-'+flag+'-TValueMetadataGapRunner-'+mode);b=read('shadow-'+flag+'-TValueMetadataGapRunner-'+mode)
        assert a==native(b) and 'TVALUE_METADATA_GAP_OK mode='+mode in a
        expected='EXPECTED_OBSERVER_REJECTION mode='+mode+' identity='+str(mode!='applyMap').lower()
        assert expected in b
for build in ('clean','shadow'):
    assert 'MIND_COMMIT_EXCEPTION_ATOMICITY_OK' in read(build+'-on-KangerMindCommitExceptionAtomicitySafetyRunner')
# Independent replay accepts and counts changes to a surviving TValue's
# TermID. It never repairs state with a native snapshot or missing dirty key.
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
    if name=='replay':
        body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0")
        body=body.replace("assert old[id][1]==new[id][1];", "kinds['term_changes']+=old[id][1]!=new[id][1];")
    exec(body)
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==10
for flag in ('on','off','verify'):
    name='shadow-'+flag+'-TValueMetadataSafetyRunner.trace.gz';t=traces[name]
    assert t['transitions']['term_changes']==8 and t['observations']==27 and t['changes']==9,t
    assert (root/name).read_bytes()==(root/'shadow-on-TValueMetadataSafetyRunner.trace.gz').read_bytes()
old=json.loads(Path('docs/tvalue-dirty-buckets/summary.json').read_text());assert old['frozen_complete_corpus_semantics_equal'] is False and old['rejected_corpus_attempts']==2
results=list(root.glob('*.result.json'));assert len(results)==31
comparison=json.loads((root/'class-comparison.json').read_text());structure=json.loads((root/'structural-check.json').read_text());assert all(v['all_original_code_recovered_byte_for_byte'] for v in structure['shadow'].values())
summary={'base':base,'production_sources_and_qualification_and_workflows_unchanged':True,'fresh_jvms':31,'all_exit_codes_zero_and_stderr_empty':True,'expected_observer_rejections':6,'metadata_checks_per_flag':19,'native_metadata_equivalence_flags':['on','off','verify'],'metadata_gates':metadata,'candidate_source_oracles':4,'sources_per_oracle':47,'transaction_full_state_and_work_equal':True,'transaction_operations_per_jvm':20,'native_commit_exception_gates':2,'journal_traces':traces,'total_replayed_authority_views':sum(v['observations'] for v in traces.values()),'class_comparison':comparison,'qualified_setters':['setValue(Term)','setTVar(same variable ID)','setPersistentReferences(new TermID, same variable ID)'],'registered_variable_changes_rejected_after_native_write':True,'unhooked_applyMap_term_change_detected_by_full_oracle':True,'native_hash_index_repair':False,'native_variable_index_repair':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'corpus_not_rerun_this_stage':True,'limitations':['ThreadLocal explicit native Mind/TValueFactory sessions; no arbitrary worker coverage','object identity registered by successful add hooks and shadow seed/dirty bucket reads; independent full oracle does not seed registration','only ordered ID/TermID/deletion projection; resident object references, owner IDs, action and query/current projections not journaled','setId/setMind/setMindId/apply/applyMap and arbitrary extensions outside setter coverage','variable-changing writes rejected after native execution; no rollback or index repair','diagnostic full scans and strong references; no production consumer or speedup','earlier overlapping-preparation corpus remains unqualified']}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('TVALUE_METADATA_ANALYSIS_OK',json.dumps({k:v for k,v in summary.items() if k not in ('journal_traces','limitations','metadata_gates')}))
