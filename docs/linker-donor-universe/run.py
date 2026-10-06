from pathlib import Path
import subprocess,tempfile,gzip,sys
root=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
markers={'KangerLinkerRuleOrderingSafetyRunner':'LINKER_RULE_ORDERING_OK','KangerLinkerCheckpointBalanceSafetyRunner':'LINKER_CHECKPOINT_BALANCE_OK','KangerCompletedHypothesisContractRunner':'COMPLETED_HYPOTHESIS_CONTRACT_OK','FrontierDependencyWitness':'FRONTIER_DEPENDENCY_WITNESS_OK checks=31','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20','KangerRuleCandidateConcurrencyRunner':'RULE_CANDIDATE_CONCURRENCY_OK iterations=3','ExactCandidateReplayRunner':'EXACT_CANDIDATE_REPLAY_OK','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS','KangerLinkerDonorScopeSafetyRunner':'LINKER_DONOR_SCOPE_OK','SonProfileRunner':'SAMPLE 2 ','ScopeTraceSonRunner':'TRACE_END sample=2'}
matrix=[]
for mode in ('reference','split'):
 for flag in ('on','off'):
  for runner in ('KangerLinkerDonorScopeSafetyRunner','KangerLinkerRuleOrderingSafetyRunner','KangerLinkerCheckpointBalanceSafetyRunner','KangerCompletedHypothesisContractRunner','FrontierDependencyWitness','LatentSolveSyncTransactionRunner','ExactCandidateReplayRunner'):
   matrix.append((mode,flag,runner))
 for runner in ('KangerRuleCandidateConcurrencyRunner','LatentSubstitutionCorpusRunner','SonProfileRunner'):matrix.append((mode,'on',runner))
for mode in ('reference-trace','split-trace'):matrix.append((mode,'on','ScopeTraceSonRunner'))
start=int(sys.argv[1]) if len(sys.argv)>1 else 0
for index,(mode,flag,runner) in enumerate(matrix):
 if index<start:continue
 label=mode+'-'+flag+'-'+runner;p=root/label
 args=[]
 if runner=='KangerLinkerDonorScopeSafetyRunner' and mode=='reference':args=['--reference']
 if runner=='LatentSolveSyncTransactionRunner':args=[str(p)+'.state',str(p)+'.work']
 if runner=='ExactCandidateReplayRunner':args=['exact','all']
 command=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='donor-jvm-')]+['-Dkanger.experiment.'+f+'='+('true' if flag=='on' else 'false') for f in flags]+['-Dbench.samples=3','-Dkanger.rule.candidate.concurrency.iterations=3','-cp','../build/donor-'+mode+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner,*args]
 print('START',index,label,flush=True)
 with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:subprocess.run(command,stdout=out,stderr=err,check=True,timeout=600)
 data=p.with_suffix('.log').read_bytes();text=data.decode();assert markers[runner] in text,(label,markers[runner])
 # Some legacy corpora/concurrency probes have expected stderr; compare those explicitly in analyze.py.
 if runner not in ('LatentSubstitutionCorpusRunner','KangerRuleCandidateConcurrencyRunner'):assert not p.with_suffix('.err').read_bytes(),label
 p.with_suffix('.log.gz').write_bytes(gzip.compress(data,mtime=0));p.with_suffix('.log').unlink()
 print('DONE',index,label,flush=True)
