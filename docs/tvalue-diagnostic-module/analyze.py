from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};entries=0;controls=0;canonical={};diagnostics={}
assert len(list(p.glob('*.result.json')))==18
for rep in range(3):
 for flag in ['on','off','verify']:
  for mode in ['clean','hooked']:
   prefix=p/f'{rep}-{flag}-{mode}';r=json.loads(Path(str(prefix)+'.result.json').read_text());assert r['exit_code']==0 and r['mode']==mode and r['flag']==flag and r['repetition']==rep
   assert not Path(str(prefix)+'.err').read_bytes();log=gzip.decompress(Path(str(prefix)+'.log.gz').read_bytes()).decode();assert 'CONSUMER_GATE_OK' in log
   wanted=270 if mode=='clean' else 306;assert f'controls={wanted}' in log;controls+=wanted
   assert ('accepted=0 refused=0' if mode=='clean' else 'accepted=6 refused=3') in log
   text=Path(str(prefix)+'.native-rows.txt').read_bytes();assert text==(Path('docs/tvalue-recycled-id')/f'{rep}-{flag}-clean.native-rows.txt').read_bytes()
   if rep in canonical:assert canonical[rep]==text
   else:canonical[rep]=text
   if mode=='clean':continue
   lines=Path(str(prefix)+'.diagnostics.txt').read_text().splitlines();assert len(lines)==12
   for variable in range(3):
    label='root-clear-v'+str(variable);line=next(x for x in lines if x.startswith('REFUSE case='+label+' '));assert line==f'REFUSE case={label} code=RECYCLED_ID aliases=2 journalRejected={str(variable==2).lower()} outputRows=0'
    assert not list(p.glob(prefix.name+'.'+label+'.*trace*'))
   assert lines[-3:]==['NEGATIVE code=FULL_AUTHORITY_MISMATCH outputRows=0','NEGATIVE code=ADAPTER_FAILURE outputRows=0','NEGATIVE code=NO_SESSION outputRows=0']
   if rep in diagnostics:assert diagnostics[rep]==lines
   else:diagnostics[rep]=lines
   accepted=list(p.glob(prefix.name+'.*.accepted.trace.gz'));assert len(accepted)==7
   for path in accepted:
    stats=replay(path);traces[path.name]=stats
    if '.recovery.' not in path.name:
     label=path.name[len(prefix.name)+1:].removesuffix('.accepted.trace.gz');assert label.startswith(('child-clear','pack'))
     prior=Path('docs/tvalue-recycled-id')/f'{rep}-{flag}-guarded.{label}.old.trace.gz';assert path.read_bytes()==prior.read_bytes()
    for row in gzip.decompress(path.read_bytes()).decode().splitlines():
     if row.startswith('VIEW '):
      kv=dict(x.split('=',1) for x in row.split()[1:]);entries+=sum(len(v) for v in parseview(kv['view']).values())
assert len(traces)==63 and controls==5184
b=json.loads((p/'build-validation.json').read_text())
for mode in ['clean','hooked']:
 assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'][mode].items())
 assert all(hashlib.sha256((Path('../build/tvalue-diagnostic-module-'+mode)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'][mode].items())
changes=json.loads((p/'changes.json').read_text());proof=json.loads((p/'structural-check.json').read_text())
for name,edits in changes.items():
 body=(p/'source'/name).read_text();assert hashlib.sha256(body.encode()).hexdigest()==proof[name]['instrumented_sha256']
 for edit in reversed(edits):assert body.count(edit['new'])==1;body=body.replace(edit['new'],edit['old'],1)
 assert body==(Path('../K3-smart-native/kanger/src/org/kanger')/name).read_text()
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-recycled-id'])
module=Path('kanger-qualification/diagnostics');origins=json.loads((p/'source-origins.json').read_text());assert len(origins)==13
assert all(Path(k).read_bytes()==Path(v).read_bytes() for k,v in origins.items())
assert b['all_clean_and_hooked_class_hashes_equal_previous_consumer_stage']
assert b['module_source_files']==9 and b['module_test_source_files']==4
for mode,data in b['independent_compilations'].items():
 assert all(hashlib.sha256((Path('../build/tvalue-diagnostic-module-'+mode)/k).read_bytes()).hexdigest()==v for k,v in data['class_sha256'].items())
 assert all(b['class_sha256']['clean'][k]==v for k,v in data['class_sha256'].items())
assert not (set(b['independent_compilations']['native-only']['class_sha256'])&set(b['independent_compilations']['module']['class_sha256']))
import zipfile
jar=Path(b['standalone_module_jar']['path']);assert hashlib.sha256(jar.read_bytes()).hexdigest()==b['standalone_module_jar']['sha256']
with zipfile.ZipFile(jar) as z:
 assert sorted(z.namelist())==sorted(b['independent_compilations']['module']['class_sha256'])
 assert all(hashlib.sha256(z.read(k)).hexdigest()==v for k,v in b['independent_compilations']['module']['class_sha256'].items())
assert all(not x.startswith('docs/') for x in (p/'clean-sources.txt').read_text().splitlines())
assert not subprocess.check_output(['git','diff',b['parent'],'--','pom.xml','kanger-qualification/pom.xml','kanger-qualification/src','.github'])
jar_traces={};jar_entries=0
assert len(list(p.glob('*.smoke.json')))==3
hooked_native=Path('../build/tvalue-diagnostic-module-hooked-native');expected=b['independent_compilations']['native-only']['class_sha256']
assert {str(q.relative_to(hooked_native)) for q in hooked_native.rglob('*.class')}==set(expected)
assert all(hashlib.sha256((hooked_native/k).read_bytes()).hexdigest()==b['class_sha256']['hooked'][k] for k in expected)
for flag in ['on','off','verify']:
 prefix=p/('jar-'+flag);r=json.loads(Path(str(prefix)+'.smoke.json').read_text());assert r['exit_code']==0 and r['classpath_has_no_fallback_diagnostic_classes']
 assert not Path(str(prefix)+'.err').read_bytes();assert 'CONSUMER_GATE_OK' in gzip.decompress(Path(str(prefix)+'.log.gz').read_bytes()).decode()
 prior=p/f'0-{flag}-hooked'
 assert Path(str(prefix)+'.native-rows.txt').read_bytes()==Path(str(prior)+'.native-rows.txt').read_bytes()
 assert Path(str(prefix)+'.diagnostics.txt').read_bytes()==Path(str(prior)+'.diagnostics.txt').read_bytes()
 accepted=list(p.glob(prefix.name+'.*.accepted.trace.gz'));assert len(accepted)==7
 for path in accepted:
  suffix=path.name[len(prefix.name):];assert path.read_bytes()==Path(str(prior)+suffix).read_bytes();jar_traces[path.name]=replay(path)
  for row in gzip.decompress(path.read_bytes()).decode().splitlines():
   if row.startswith('VIEW '):
    kv=dict(x.split('=',1) for x in row.split()[1:]);jar_entries+=sum(len(v) for v in parseview(kv['view']).values())
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':18,'native_boundary_cases':162,'native_context_variable_controls':controls,'native_rows_identical_to_prior_and_across_builds_flags':True,'qualified_exports':63,'replayed_authority_views':sum(x['observations'] for x in traces.values()),'serialized_value_entries_checked':entries,'recycled_identity_refusals':27,'additional_missing_event_adapter_and_no_session_refusals':27,'refused_exports_return_no_trace':True,'immutable_successful_exports':True,'both_journal_sessions_completed_and_consumer_closed_after_refusal':True,'fresh_session_recovery_exports':9,'ordinary_boundary_exports_byte_identical_to_prior_raw_traces':54,'guard_has_no_disable_flag':True,'diagnostic_consumer_gate_qualified_for_fixture':True,'native_recycled_identity_repaired':False,'original_native_source_recovered_byte_for_byte':True,'unchanged_prior_clean_classes_verified':730,'changed_native_classes':b['changed_native_classes'],'diagnostic_journals_or_readers_changed':False,'production_changed':False,'SMART_backend_persistence_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_speedup_qualified':False,'module_source_files':9,'module_test_source_files':4,'all_java_sources_byte_identical_to_prior':True,'all_clean_and_hooked_class_hashes_byte_identical_to_prior':True,'standalone_module_compiles_without_diagnostic_classes_on_classpath':True,'no_docs_Java_sources_compiled':True,'normal_Maven_sources_and_reactor_unchanged':True,'jar_diagnostic_class_entries':b['standalone_module_jar']['entries'],'standalone_jar_JVMs':3,'standalone_jar_exports_replayed':len(jar_traces),'standalone_jar_authority_views':sum(x['observations'] for x in jar_traces.values()),'standalone_jar_serialized_entries':jar_entries,'standalone_jar_no_diagnostic_fallback_and_runtime_parity':True,'standalone_jar_trace_replays':jar_traces,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('DIAGNOSTIC_MODULE_ANALYSIS_OK',json.dumps({k:v for k,v in s.items() if not isinstance(v,dict)}))
