from pathlib import Path
import json,gzip,hashlib,subprocess,ast,re
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};entries=0;native_views=0;full_views=0;controls=0;fingerprints=0;alias_cases=0
assert len(list(p.glob('*.result.json')))==27
canonical_native={};canonical_diag={};canonical_trace={}
for rep in range(3):
 for flag in ['on','off','verify']:
  prefixes=[p/f'{rep}-{flag}-{mode}' for mode in ['clean','unguarded','guarded']]
  native=[]
  for prefix,mode in zip(prefixes,['clean','unguarded','guarded']):
   r=json.loads(Path(str(prefix)+'.result.json').read_text());assert r['exit_code']==0 and r['mode']==mode and r['flag']==flag and r['repetition']==rep
   assert not Path(str(prefix)+'.err').read_bytes();log=gzip.decompress(Path(str(prefix)+'.log.gz').read_bytes()).decode();assert 'RECYCLED_ID_OK' in log and 'cases=9 controls=270' in log
   assert ('journalRejected=0' if mode=='clean' else 'journalRejected=1') in log
   assert ('aliasRefusals=6' if mode=='guarded' else 'aliasRefusals=0') in log
   assert 'fullOracleBlindCases=0' in log;controls+=270
   text=Path(str(prefix)+'.native-rows.txt').read_bytes();native.append(text)
   if rep in canonical_native:assert canonical_native[rep]==text
   else:canonical_native[rep]=text
   rows=text.decode().splitlines();assert len(rows)==99 # nine cases × ten native views plus one ID control
   full_mismatches=[]
   for row in rows:
    if row.startswith('VIEW '):
     native_views+=1;full_views+=2
     if 'fullMatchesNative=false' in row:full_mismatches.append(row)
   assert len(full_mismatches)==2 and all('root-clear-v2-replacement' in x for x in full_mismatches)
   for boundary in ['root-clear','child-clear','pack']:
    for variable in range(3):
     label=boundary+'-v'+str(variable);identity=next(x for x in rows if x.startswith('ID case='+label+' '))
     if boundary=='root-clear':assert f'owner=new:0:{variable}:' in identity and 'child=old:0:0:' in identity and 'grand=old:0:0:' in identity
     else:assert 'owner=new:6:' in identity and 'grand=null' in identity
     fresh=next(x for x in rows if x.startswith('VIEW stage='+label+'-fresh '));assert 'fullMatchesNative=true' in fresh and '=new:' in fresh
     replacements=[x for x in rows if x.startswith('VIEW stage='+label+'-replacement ')]
     assert len(replacements)==3
     if boundary=='root-clear':
      assert f'{variable}=new:0:{variable}:' in replacements[0]
      assert all(f'{variable}=old:0:0:' in x and '=new:' not in x for x in replacements[1:])
     elif boundary=='child-clear':assert '=new:6:' in replacements[1] and '=new:' not in replacements[0] and '=new:' not in replacements[2]
     else:assert '=new:6:' in replacements[0] and all('=new:' not in x for x in replacements[1:])
     if mode=='clean':continue
     diag=Path(str(prefix)+'.diagnostics.txt').read_text();line=next(x for x in diag.splitlines() if x.startswith('CASE '+label+' '))
     if mode=='guarded' and boundary=='root-clear':
      assert line.count('unsupported recycled TValue identity')==2 and 'descendantLevel=1' in line and 'descendantLevel=2' in line and 'gate=refused-recycled-identity' in line;alias_cases+=1
     else:assert 'aliases=[]' in line and 'gate=ordinary' in line
     if boundary=='root-clear' and variable==2:
      assert 'journal=journal errors' in line and 'dirty projection mismatch' in line
      assert not list(p.glob(prefix.name+'.'+label+'.*.trace.gz'));continue
     assert 'journal=accepted' in line
     pair=[]
     for journal in ['old','memo']:
      path=Path(str(prefix)+'.'+label+'.'+journal+'.trace.gz');stats=replay(path);traces[path.name]=stats;pair.append(path.read_bytes())
      if label in canonical_trace:assert canonical_trace[label]==path.read_bytes()
      else:canonical_trace[label]=path.read_bytes()
      for row in gzip.decompress(path.read_bytes()).decode().splitlines():
       if row.startswith('VIEW '):
        kv=dict(x.split('=',1) for x in row.split()[1:]);entries+=sum(len(v) for v in parseview(kv['view']).values())
   if mode!='clean':
    diag=Path(str(prefix)+'.diagnostics.txt').read_bytes();key=(rep,mode)
    if key in canonical_diag:assert canonical_diag[key]==diag
    else:canonical_diag[key]=diag
  assert native[0]==native[1]==native[2]
assert len(traces)==288 and alias_cases==27 and controls==7290 and native_views==2430
b=json.loads((p/'build-validation.json').read_text())
for mode in ['clean','hooked']:
 assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'][mode].items())
 assert all(hashlib.sha256((Path('../build/tvalue-recycled-id-'+mode)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'][mode].items())
changes=json.loads((p/'changes.json').read_text());proof=json.loads((p/'structural-check.json').read_text())
for name,edits in changes.items():
 body=(p/'source'/name).read_text();assert hashlib.sha256(body.encode()).hexdigest()==proof[name]['instrumented_sha256']
 for edit in reversed(edits):assert body.count(edit['new'])==1;body=body.replace(edit['new'],edit['old'],1)
 assert body==(Path('../K3-smart-native/kanger/src/org/kanger')/name).read_text()
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-native-mutations'])
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':27,'native_boundary_cases':243,'native_context_variable_controls':controls,'native_views':native_views,'pure_full_authority_comparisons':full_views,'successful_replayed_traces':len(traces),'replayed_authority_views':sum(x['observations'] for x in traces.values()),'serialized_value_entries_checked':entries,'root_clear_reuses_captured_ID':True,'same_and_cross_existing_variable_journals_can_accept_reused_identity':True,'unseen_variable_native_full_authority_disagreements':54,'unseen_variable_journal_rejections':18,'guarded_root_clear_cases_with_two_aliases':alias_cases,'guarded_nonrecycling_control_cases_without_aliases':54,'native_rows_identical_across_builds_guards_and_flags':True,'old_stream_and_successful_case_traces_identical':True,'guard_is_diagnostic_refusal_policy_prototype':True,'native_reused_identity_semantics_repaired':False,'original_native_source_recovered_byte_for_byte':True,'unchanged_prior_clean_classes_verified':730,'changed_native_classes':b['changed_native_classes'],'diagnostic_journals_or_readers_changed':False,'production_changed':False,'SMART_backend_persistence_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_speedup_qualified':False,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('RECYCLED_ID_ANALYSIS_OK',json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
