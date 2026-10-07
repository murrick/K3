from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent;source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==12
native={};traces={};errors=set();physical=set()
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes()
 text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'UPDATE_BOUNDARY_OK' in text
 row=next(x for x in text.splitlines() if x.startswith('UPDATE_BOUNDARY_NATIVE'));native.setdefault(r['mode'],set()).add(row)
 prior=gzip.decompress(Path('docs/tvalue-update-boundary',label+'.log.gz').read_bytes()).decode();assert row==next(x for x in prior.splitlines() if x.startswith('UPDATE_BOUNDARY_NATIVE'))
 if r['mode']=='failure':
  assert 'exception=IllegalStateException' in row and 'native_callbacks=1' in row and 'first_record=written second_write=attempted partial_root=retained' in row
  proof=(p/(label+'.physical.txt')).read_text();assert 'rows=[0:0:4:0]' in proof and 'witness=present native_endpoints=unresolved reader=pure' in proof;physical.add(proof)
 if r['build']=='shadow':
  if r['mode']=='success':
   trace=p/(label+'.success.trace.gz');traces[trace.name]=replay(trace);assert trace.read_bytes()==Path('docs/tvalue-update-boundary',trace.name).read_bytes()
   check=traces[trace.name];assert check['observations']==17 and check['resets']==1 and check['contexts']==2 and check['changes']==6 and check['transitions']['term_changes']==6
   body=gzip.decompress(trace.read_bytes()).decode();assert len([x for x in body.splitlines() if x.startswith('TOUCH ') and 'reason=materialize ' in x])==3
  else:
   error=(p/(label+'.failure-error.txt')).read_text();errors.add(error);assert 'unsupported incomplete native factory update' in error and 'dirty projection mismatch ctx=1 reason=session-end' in error and 'unresolved endpoints' not in error
   assert 'shadow={0=[0:4:0,1:1:0,2:5:0]} authority={0=[0:4:0]}' in error
   prefix=p/(label+'.failure-prefix.trace.gz');body=gzip.decompress(prefix.read_bytes()).decode();assert len(body.splitlines())==2 and 'TOUCH ' not in body and 'CHANGE ' not in body and 'DIRTY_JOURNAL_OK' not in body
   assert prefix.read_bytes()==Path('docs/tvalue-update-boundary',prefix.name).read_bytes()
assert len(traces)==3 and all(len(x)==1 for x in native.values()) and len(errors)==1 and len(physical)==1
for stage,folder in [('tvalue-saved-links','tvalue-saved-links'),('tvalue-update-boundary','tvalue-update-boundary'),('tvalue-saved-link-failure','tvalue-saved-link-failure')]:
 info=json.loads(Path('docs',stage,'build-validation.json').read_text())
 for mode,build in info['builds'].items():
  assert all(hashlib.sha256((Path('../build',folder+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in build['overlay_class_sha256'].items())
for stage,manifest in [('tvalue-saved-links','evidence-sha256.json'),('tvalue-update-boundary','evidence-sha256.json'),('tvalue-resident-endpoints','artifact-manifest.json')]:
 hashes=json.loads(Path('docs',stage,manifest).read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','f6ef43a7348bf270ae196db389c033306f305702','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s={'parent':'f6ef43a7348bf270ae196db389c033306f305702','fresh_JVMs':12,'preliminary_JVM_failures':0,'all_expectations_met':True,'successful_traces':3,'authority_views':sum(x['observations'] for x in traces.values()),'denied_failure_prefixes':3,'original_native_exception_and_partial_writes_preserved':True,'recorded_physical_partial_chain_accepted_without_endpoint_repair':True,'incomplete_factory_update_refusal_precedes_finish_oracle':True,'all_pending_materialization_discarded_on_failure':True,'full_oracle_detects_partial_authority_without_repair':True,'success_traces_and_failure_prefixes_byte_identical_to_original_update_boundary':True,'recorder_reader_Data_journal_factory_unchanged':True,'production_changed':False,'arbitrary_IO_crash_reentrant_or_concurrent_faults_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('SAVED_LINK_FAILURE_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
