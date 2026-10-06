import subprocess
from pathlib import Path
root=Path(__file__).parent
runners={'SingleVariableEmptyRunner':'SINGLE_VARIABLE_EMPTY_OK scenarios=24 checks=163','CompactFindSnapshotsRunner':'COMPACT_FIND_SNAPSHOTS_OK checks=23','ResidentBaseComparisonRunner':'RESIDENT_BASE_COMPARISON_OK checks=48 custom_reads=3','SingleTValueLookupRunner':'SINGLE_LOOKUP_BOUNDARIES_OK checks=15','TValueIndexReopenRunner':'TVALUE_INDEX_REOPEN_PASS','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20','KangerRuleCandidateConcurrencyRunner':'RULE_CANDIDATE_CONCURRENCY_OK iterations=3'}
for mode in ['false','true']:
 for runner,marker in runners.items():
  prefix=root/(runner+'-'+mode)
  cmd=['java','-Xmx512m','-Dkanger.experiment.singleVariableEmpty='+mode,'-Dkanger.rule.candidate.concurrency.iterations=3','-cp','../build/single-empty-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner':cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:subprocess.run(cmd,stdout=out,stderr=err,check=True,timeout=300)
  assert marker in prefix.with_suffix('.log').read_text(),prefix
  assert not prefix.with_suffix('.err').read_text(),prefix
  print('PASS',runner,mode,flush=True)
assert (root/'LatentSolveSyncTransactionRunner-false.state').read_bytes()==(root/'LatentSolveSyncTransactionRunner-true.state').read_bytes()
print('TRANSACTION_STATES_EQUAL',flush=True)
