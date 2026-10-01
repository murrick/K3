import subprocess
from pathlib import Path
runners=['DirectPredicateNameRunner','CompactFindSnapshotsRunner','LatentSubstitutionCorpusRunner','LatentSolveSyncTransactionRunner']
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/combined-classes','-sourcepath','kanger-qualification/src','-d','../build/combined-classes']+['kanger-qualification/src/org/kanger/'+r+'.java' for r in runners],check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for mode in ['00','10','01','11']:
 for runner in runners:
  prefix=Path('docs/combined-allocation-evidence')/(''+runner+'-'+mode)
  cmd=base+['-Dkanger.experiment.compactFindSnapshots='+str(mode[1]=='1').lower(),'-Dkanger.experiment.directPredicateName='+str(mode[0]=='1').lower(),'-cp','../build/combined-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner': cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
  marker={'DirectPredicateNameRunner':'DIRECT_PREDICATE_NAME_BOUNDARIES_OK checks=24','CompactFindSnapshotsRunner':'COMPACT_FIND_SNAPSHOTS_OK checks=23','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}[runner]
  assert marker in Path(str(prefix)+'.log').read_text(),str(prefix)
  print('PASS',runner,mode,flush=True)
states=[(Path('docs/combined-allocation-evidence')/('LatentSolveSyncTransactionRunner-'+m+'.state')).read_bytes() for m in ['00','10','01','11']]
assert len(set(states))==1
print('ALL_FOUR_TRANSACTION_STATES_EQUAL',flush=True)
