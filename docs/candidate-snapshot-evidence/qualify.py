import subprocess
from pathlib import Path
runners=['CompactFindSnapshotsRunner','LatentSubstitutionCorpusRunner','LatentSolveSyncTransactionRunner']
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/snapshot-classes','-sourcepath','kanger-qualification/src','-d','../build/snapshot-classes']+['kanger-qualification/src/org/kanger/'+r+'.java' for r in runners],check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for mode in ['false','true']:
 for runner in runners:
  prefix=Path('docs/candidate-snapshot-evidence')/(''+runner+'-'+mode)
  cmd=base+['-Dkanger.experiment.compactFindSnapshots='+mode,'-cp','../build/snapshot-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner': cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
  marker={'CompactFindSnapshotsRunner':'COMPACT_FIND_SNAPSHOTS_OK checks=23','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}[runner]
  assert marker in Path(str(prefix)+'.log').read_text(),str(prefix)
  print('PASS',runner,mode,flush=True)
a=Path('docs/candidate-snapshot-evidence/LatentSolveSyncTransactionRunner-false.state').read_bytes()
b=Path('docs/candidate-snapshot-evidence/LatentSolveSyncTransactionRunner-true.state').read_bytes()
assert a==b
print('TRANSACTION_STATES_EQUAL',flush=True)
