import subprocess
from pathlib import Path
runners=['EmptyDeletionLayerRunner','LatentSubstitutionCorpusRunner','LatentSolveSyncTransactionRunner']
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/operands-classes','-sourcepath','kanger-qualification/src','-d','../build/operands-classes']+['kanger-qualification/src/org/kanger/'+r+'.java' for r in runners],check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for mode in ['false','true']:
 for runner in runners:
  prefix=Path('docs/base-operands-evidence')/('deletion-'+runner+'-'+mode)
  cmd=base+['-Dkanger.experiment.emptyDeletionLayerShortcut='+mode,'-cp','../build/operands-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner': cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
  marker={'EmptyDeletionLayerRunner':'EMPTY_DELETION_BOUNDARIES_OK checks=27','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}[runner]
  assert marker in Path(str(prefix)+'.log').read_text(),str(prefix)
  print('PASS',runner,mode,flush=True)
a=Path('docs/base-operands-evidence/deletion-LatentSolveSyncTransactionRunner-false.state').read_bytes()
b=Path('docs/base-operands-evidence/deletion-LatentSolveSyncTransactionRunner-true.state').read_bytes()
assert a==b
print('TRANSACTION_STATES_EQUAL',flush=True)
