import subprocess
from pathlib import Path
runners=['ResidentBaseComparisonRunner','SingleTValueLookupRunner','TValueIndexReopenRunner','LatentSubstitutionCorpusRunner','LatentSolveSyncTransactionRunner']
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/base-classes','-sourcepath','kanger-qualification/src','-d','../build/base-classes']+['kanger-qualification/src/org/kanger/'+r+'.java' for r in runners],check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for mode in ['false','true']:
 for runner in runners:
  prefix=Path('docs/resident-base-comparison-evidence')/(''+runner+'-'+mode)
  cmd=base+['-Dkanger.experiment.residentBaseComparison='+mode,'-cp','../build/base-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner': cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
  marker={'SingleTValueLookupRunner':'SINGLE_LOOKUP_BOUNDARIES_OK checks=15','TValueIndexReopenRunner':'TVALUE_INDEX_REOPEN_PASS','ResidentBaseComparisonRunner':'RESIDENT_BASE_COMPARISON_OK checks=48 custom_reads=3','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}[runner]
  assert marker in Path(str(prefix)+'.log').read_text(),str(prefix)
  print('PASS',runner,mode,flush=True)
a=Path('docs/resident-base-comparison-evidence/LatentSolveSyncTransactionRunner-false.state').read_bytes()
b=Path('docs/resident-base-comparison-evidence/LatentSolveSyncTransactionRunner-true.state').read_bytes()
assert a==b
print('TRANSACTION_STATES_EQUAL',flush=True)

for mode in ['false','true']:
 prefix=Path('docs/resident-base-comparison-evidence')/('concurrency-'+mode)
 cmd=base+['-Dkanger.experiment.residentBaseComparison='+mode,'-Dkanger.rule.candidate.concurrency.iterations=3','-cp','../build/base-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.KangerRuleCandidateConcurrencyRunner']
 with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
 assert 'RULE_CANDIDATE_CONCURRENCY_OK iterations=3' in Path(str(prefix)+'.log').read_text()
 print('PASS concurrency',mode,flush=True)
