import subprocess
from pathlib import Path
runners=['DirectPredicateNameRunner','CompactFindSnapshotsRunner','LatentSubstitutionCorpusRunner','LatentSolveSyncTransactionRunner','ResidentBaseComparisonRunner','SingleTValueLookupRunner','TValueIndexReopenRunner']
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/resident-allocation-classes','-sourcepath','kanger-qualification/src','-d','../build/resident-allocation-classes']+['kanger-qualification/src/org/kanger/'+r+'.java' for r in runners],check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for mode in ['00','10','01','11']:
 for runner in runners:
  prefix=Path('docs/resident-allocation-evidence')/(''+runner+'-'+mode)
  cmd=base+['-Dkanger.experiment.compactFindSnapshots='+str(mode[1]=='1').lower(),'-Dkanger.experiment.directPredicateName='+str(mode[0]=='1').lower(),'-cp','../build/resident-allocation-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
  if runner=='LatentSolveSyncTransactionRunner': cmd += [str(prefix)+'.state',str(prefix)+'.work']
  with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
  marker={'ResidentBaseComparisonRunner':'RESIDENT_BASE_COMPARISON_OK checks=48 custom_reads=3','SingleTValueLookupRunner':'SINGLE_LOOKUP_BOUNDARIES_OK checks=15','TValueIndexReopenRunner':'TVALUE_INDEX_REOPEN_PASS','DirectPredicateNameRunner':'DIRECT_PREDICATE_NAME_BOUNDARIES_OK checks=24','CompactFindSnapshotsRunner':'COMPACT_FIND_SNAPSHOTS_OK checks=23','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}[runner]
  assert marker in Path(str(prefix)+'.log').read_text(),str(prefix)
  print('PASS',runner,mode,flush=True)
states=[(Path('docs/resident-allocation-evidence')/('LatentSolveSyncTransactionRunner-'+m+'.state')).read_bytes() for m in ['00','10','01','11']]
assert len(set(states))==1
print('ALL_FOUR_TRANSACTION_STATES_EQUAL',flush=True)
