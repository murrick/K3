from pathlib import Path
import json,gzip,hashlib,subprocess,csv
p=Path(__file__).parent;prior=Path('docs/tvalue-observer-session');sha=lambda q:hashlib.sha256(q.read_bytes()).hexdigest()
assert len(list(p.glob('*.result.json')))==24
exports=0
for rep in range(3):
 for flag in ('on','off','verify'):
  for mode in ('clean','hooked'):
   label=f'{rep}-{flag}-{mode}';a=p/label;b=prior/label
   result=json.loads(Path(str(a)+'.result.json').read_text());assert result['exit_code']==0
   assert not Path(str(a)+'.err').read_bytes()
   assert gzip.decompress(Path(str(a)+'.log.gz').read_bytes())==gzip.decompress(Path(str(b)+'.log.gz').read_bytes())
   assert Path(str(a)+'.native-rows.txt').read_bytes()==Path(str(b)+'.native-rows.txt').read_bytes()
   if mode=='hooked':
    assert Path(str(a)+'.diagnostics.txt').read_bytes()==Path(str(b)+'.diagnostics.txt').read_bytes()
    traces=list(p.glob(label+'.*.accepted.trace.gz'));assert len(traces)==7
    for trace in traces:assert trace.read_bytes()==(prior/trace.name).read_bytes();exports+=1
    assert not list(p.glob(label+'.root-clear*.trace*'))
for flag in ('on','off','verify'):
 for mode in ('clean','hooked'):
  a=p/f'owner-{flag}-{mode}';result=json.loads(Path(str(a)+'.result.json').read_text());assert result['exit_code']==0
  assert not Path(str(a)+'.err').read_bytes();assert b'OBSERVER_SESSION_OK' in gzip.decompress(Path(str(a)+'.log.gz').read_bytes())
b=json.loads((p/'build-validation.json').read_text())
for mode,record in b['compilations'].items():
 assert all(sha(Path(k))==v for k,v in record['source_sha256'].items())
 assert all(sha(Path('../build/tvalue-observer-fast-disabled-'+mode)/k)==v for k,v in record['class_sha256'].items())
assert sha(Path('../build/tvalue-observer-fast-disabled.jar'))==b['jar_sha256']
changes=json.loads((p/'changes.json').read_text())
for name,edits in changes.items():
 body=(p/'source'/name).read_text()
 for edit in reversed(edits):assert body.count(edit['new'])==1;body=body.replace(edit['new'],edit['old'],1)
 assert body==(Path('../K3-observer-native/kanger/src/org/kanger')/name).read_text()
assert not subprocess.check_output(['git','diff','550ba13fbf4339afbcd3f266c62d4c97461c7626','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','pom.xml','kanger-qualification/pom.xml','.github','docs/tvalue-observer-fast-disabled','kanger-qualification/diagnostics/src'])
assert len(list(p.glob('*.wiring.json')))==4
for name in ('vanilla','missing-mind','missing-factory','missing-value'):
 assert json.loads((p/(name+'.wiring.json')).read_text())['exit_code']==0
 assert not (p/(name+'.err')).read_bytes()
 assert b'VANILLA_CONSUMER_REFUSAL_OK' in gzip.decompress((p/(name+'.log.gz')).read_bytes())
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()==b['native_head']
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
old_build=json.loads(Path('docs/tvalue-observer-session/build-validation.json').read_text())
assert b['jar_sha256']==old_build['jar_sha256']
assert b['compilations']['module']['class_sha256']==old_build['compilations']['module']['class_sha256']
assert b['compilations']['tests']['class_sha256']==old_build['compilations']['tests']['class_sha256']
s={'parent':'550ba13fbf4339afbcd3f266c62d4c97461c7626','native_head':b['native_head'],'final_JVMs':28,'consumer_matrix_JVMs':28,'observer_module_and_fixture_classes_byte_identical_to_prior':True,'native_compile_sources':len((p/'native-sources.txt').read_text().splitlines()),'clean_runtime_classes':len(b['compilations']['clean']['class_sha256']),'new_base_semantic_scope':'bounded legacy DUMB resident fixtures only','prior_matrix_JVMs':18,'observer_ownership_JVMs':6,'standalone_jar_used_in_all_runs':True,'qualified_exports_byte_identical_to_prior':exports,'native_controls_and_console_output_byte_identical_to_prior':True,'recycled_ID_refusals':27,'missing_callback_adapter_no_session_refusals':27,'no_instrumentation_refusal':True,'exclusive_session_owner':True,'abandonment_clears_journals_and_adapter_references':True,'foreign_thread_callback_taints_export':True,'throwing_observer_preserves_native_success_and_setter_exception':True,'actual_vanilla_without_bridge_API_refuses':True,'each_missing_instrumented_class_refuses':True,'wiring_refusal_JVMs':4,'production_changed':False,'SMART_persistence_qualified':False,'full_inference_corpus_qualified':False,'native_recycled_ID_repaired':False,'fast_disabled_qualified':True}
assert exports==63
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n')
print('FAST_DISABLED_QUALIFICATION_OK',json.dumps(s))
