from pathlib import Path
import json,gzip,re,ast,hashlib,subprocess
p=Path(__file__).parent
# Run every preceding strict regression assertion, including full source/work
# oracles and independent replay. Only the evidence root is redirected.
source=Path('docs/tvalue-materialization-routing/analyze.py').read_text();prefix=source.split("info=json.loads((root/'build-validation.json').read_text())")[0]
env={'__file__':str(p/'regression/analyze.py')};exec(prefix,env)
regression=env['traces'];assert len(regression)==33
prior=Path('docs/tvalue-materialization-routing')
for f in (p/'regression').glob('*.log.gz'):assert f.read_bytes()==(prior/f.name).read_bytes(),f.name
for f in (p/'regression').glob('*.trace.gz'):assert f.read_bytes()==(prior/f.name).read_bytes(),f.name
replay=env['replay'];results=list(p.glob('*.result.json'));assert len(results)==12
rows={};new_traces={}
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0
 label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'UPDATE_BOUNDARY_OK' in text;rows.setdefault(r['mode'],set()).add(next(x for x in text.splitlines() if x.startswith('UPDATE_BOUNDARY_NATIVE')))
 if r['build']=='shadow':
  if r['mode']=='success':
   trace=p/(label+'.success.trace.gz');body=gzip.decompress(trace.read_bytes()).decode();new_traces[trace.name]=replay(trace)
   touches=[x for x in body.splitlines() if x.startswith('TOUCH ') and 'reason=materialize ' in x];assert len(touches)==3 and all('ctx=1 ' in x for x in touches)
   setters=[x for x in body.splitlines() if x.startswith('TOUCH ') and 'reason=setPersistentReferences ' in x];assert len(setters)==6
   assert not any(x.startswith('CHANGE ') and ('reason=child-unaffected' in x or 'reason=parent-unaffected' in x or 'reason=detached-old' in x) for x in body.splitlines())
   assert new_traces[trace.name]['resets']==1 and new_traces[trace.name]['contexts']==2 and new_traces[trace.name]['transitions']['term_changes']==6
  else:
   error=(p/(label+'.failure-error.txt')).read_text();assert 'unsupported incomplete native factory update' in error
   body=gzip.decompress((p/(label+'.failure-prefix.trace.gz')).read_bytes()).decode();assert len(body.splitlines())==2 and 'TOUCH ' not in body and 'BASELINE ' in body and 'VIEW ' in body
assert all(len(v)==1 for v in rows.values()) and len(new_traces)==3
assert len({(p/('success-shadow-'+flag+'.success.trace.gz')).read_bytes() for flag in ('on','off','verify')})==1
# Native bodies and old journal methods are recovered in build.py. Verify its
# resulting class manifests again, and both inherited evidence manifests.
info=json.loads((p/'build-validation.json').read_text());assert info['original_update_body_recovered'] and info['all_old_journal_methods_recovered'] and not info['production_changed']
for mode,data in info['builds'].items():
 for k,v in data['overlay_class_sha256'].items():assert hashlib.sha256((Path('../build/tvalue-update-boundary-'+mode)/k).read_bytes()).hexdigest()==v
for name in ('tvalue-storage-transition','tvalue-materialization-routing'):
 manifest=json.loads(Path('docs',name,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in manifest.items())
old=json.loads((prior/'summary.json').read_text());assert not old['frozen_complete_corpus_semantics_equal'] and old['prior_rejected_corpus_attempts_retained']==2
assert not subprocess.check_output(['git','diff','c7319da9c67a0e74169e754e80d00a3e915b6028','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
summary={'parent':'c7319da9c67a0e74169e754e80d00a3e915b6028','fresh_JVMs':83,'new_JVMs':12,'fresh_regression_JVMs':71,'all_expectations_met':True,'new_partial_native_failure_shadow_JVMs':3,'native_return_and_partial_failure_preserved':True,'parent_rebind_and_child_memory_mask_qualified':True,'child_generation_reseed_qualified':True,'pending_objects_not_registered_on_failure':True,'all_71_regression_logs_and_33_traces_byte_identical':True,'success_traces':36,'replayed_authority_views':sum(x['observations'] for x in regression.values())+sum(x['observations'] for x in new_traces.values()),'new_trace_replays':new_traces,'production_changed':False,'known_same_variable_root_factory_update_only':True,'general_persistent_publication_concurrency_or_reentrancy_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'no_consumer_or_performance_claim':True}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('UPDATE_BOUNDARY_ANALYSIS_OK '+json.dumps({k:v for k,v in summary.items() if k!='new_trace_replays'}))
