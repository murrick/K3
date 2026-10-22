from pathlib import Path
from collections import Counter
import json,gzip,re,hashlib
p=Path(__file__).parent
expected={'LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','KangerOptionsRunner':'KANGER options PASS','LatentSolveSyncSafetyRunner':'LATENT_SOLVE_SYNC_SAFETY_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS','CauseWeightFallbackRunner':'CAUSE_FALLBACK_PASS','CauseWeightShadowSafetyRunner':'CAUSE_WEIGHT_SHADOW_BOUNDARIES_PASS','SingleTValueLookupRunner':'SINGLE_LOOKUP_BOUNDARIES_OK','ResidentBaseComparisonRunner':'RESIDENT_BASE_COMPARISON_OK','CompactFindSnapshotsRunner':'COMPACT_FIND_SNAPSHOTS_OK','TValueIndexPreservationRunner':'TVALUE_INDEX_PRESERVATION_PASS','TValueIndexBoundaryRunner':'TVALUE_INDEX_BOUNDARY_PASS','TValueIndexReopenRunner':'TVALUE_INDEX_REOPEN_PASS'}
files=sorted(p.glob('*.result.json'));assert len(files)==56,len(files)
records={};rows=[];method_runs=0
for f in files:
 label=f.name.removesuffix('.result.json');r=json.loads(f.read_text());out=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();err=gzip.decompress((p/(label+'.err.gz')).read_bytes()).decode()
 inventory=[x.removeprefix('Testing: ') for x in out.splitlines() if x.startswith('Testing: ')]
 counts=[(m[1] or 'completed',int(m[2]),int(m[3])) for m in re.finditer(r'(?m)^(?:(\w+) )?Success: (\d+)\s*\n\s*(?:\w+ )?Fails: (\d+)',out)]
 # Stable externally printed assertion outcomes, not a claim that every stdout byte is semantic.
 projection=[x for x in out.splitlines() if x.startswith(('OPTIONS_ENTRY ','GATE_ENTRY ','Testing: ','SEMANTIC_GATE_RETURNED ','LATENT_','CAUSE_','SINGLE_LOOKUP_','RESIDENT_BASE_','COMPACT_FIND_','TVALUE_INDEX_','BOUNDARY ','Options logical parity PASS:','KANGER options PASS')) or re.fullmatch(r'\s*(?:\w+ )?(?:Success|Fails): \d+',x)]
 failures=[]
 entry=re.search(r'^OPTIONS_ENTRY \{(.*)\}$',out,re.M)
 flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
 desired={n:str(r['state']!='off' and (r['state']!='mixed' or i%2==0)).lower() for i,n in enumerate(flags)}
 if not entry or dict(x.split('=') for x in entry[1].split(', '))!=desired:failures.append('entry_options')
 if r['timeout']:failures.append('timeout')
 if r['exit_code']!=0:failures.append('exit_code')
 watchdog_pattern=r'\s*========== KANGER DIAGNOSTICS: WATCHDOG timeout after 10000 ms: (set_\w+) ==========\n.*?\n={52}\n\s*========== JVM THREAD DUMP ==========\n.*?\n={37}\n\s*'
 watchdogs=re.findall(watchdog_pattern,err,re.S)
 unclassified=re.sub(watchdog_pattern,'',err,flags=re.S).strip()
 if unclassified:failures.append('unclassified_stderr')
 if watchdogs and (r['kind']!='options-smart' or any(name not in inventory for name in watchdogs)):failures.append('unexpected_watchdog_scope')
 if re.search(r'(?m)^(?:Exception in thread|(?:[\w$]+\.)*(?:\w*Exception|\w*Error)(?::|$))',err):failures.append('stderr_exception')
 if expected[r['gate']] not in out or 'SEMANTIC_GATE_RETURNED '+r['gate'] not in out:failures.append('missing_pass_marker')
 if re.search(r'(?m)^\s*(?:\w+ )?Fails: [1-9]',out):failures.append('suite_failure')
 if r['kind']=='corpus':
  if counts!=[('completed',103,0),('Stabilization',9,0),('C1',5,0),('C4',3,0),('C3',6,0)] or len(inventory)!=126:failures.append('corpus_inventory')
 if r['kind'].startswith('options-'):
  if out.count('Options logical parity PASS:')!=2 or len(inventory)!=212:failures.append('options_inventory')
 if not r['disposable_home_removed']:failures.append('cleanup')
 method_runs+=len(inventory)
 row={k:r[k] for k in ('impl','state','kind','gate','exit_code','timeout')};row.update({'label':label,'failures':failures,'test_method_runs':len(inventory),'watchdog_warnings':watchdogs,'stderr_bytes':len(err.encode()),'suite_counts':counts,'projection':projection})
 rows.append(row);records[label]=row
pairs=[];mismatches=[]
for label,r in records.items():
 if r['impl']!='keys':continue
 peer=label.replace('-keys-','-current-',1);c=records[peer];same=r['projection']==c['projection'] and r['failures']==c['failures']
 pair={'candidate':label,'baseline':peer,'same_assertion_projection':same,'both_pass':not r['failures'] and not c['failures']}
 if r['gate']=='LatentSolveSyncTransactionRunner':
  for suffix in ('states','work'):
   a=p/(label+'.'+suffix+'.txt');b=p/(peer+'.'+suffix+'.txt');pair[suffix+'_identical']=a.exists() and b.exists() and a.read_bytes()==b.read_bytes()
  same= same and pair['states_identical'] and pair['work_identical']
 pairs.append(pair)
 if not same:mismatches.append(pair)
(p/'assertion-projections.json').write_text(json.dumps(rows,indent=2)+'\n')
failed=[{'label':r['label'],'failures':r['failures']} for r in rows if r['failures']]
summary={'JVMs':len(files),'pairs':len(pairs),'test_method_runs':method_runs,'corpus_method_runs':sum(r['test_method_runs'] for r in rows if r['kind']=='corpus'),'options_method_runs':sum(r['test_method_runs'] for r in rows if r['kind'].startswith('options-')),'failures':failed,'diagnostic_warnings':[{'label':r['label'],'watchdogs':r['watchdog_warnings']} for r in rows if r['watchdog_warnings']],'mismatches':mismatches,'pair_checks':pairs,'kinds':dict(Counter(r['kind'] for r in rows)),'production_changed':False,'speedup_claim':False,'all_qualification_inventory_covered':False,'SMART_scope':'Not qualified: argument smart in the pinned runner opens local org.kanger.storage.dumb2.DB; SMART ownership/ACL are outside this gate.'}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('SEMANTIC_ANALYSIS',len(files),'failures',len(failed),'mismatches',len(mismatches),'method_runs',method_runs)
assert not failed and not mismatches,(failed,mismatches)
