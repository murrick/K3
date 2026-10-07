from pathlib import Path
import subprocess,tempfile,gzip,sys
root=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
markers={'TValueOrderedPublicationRunner':'TVALUE_ORDERED_PUBLICATION_OK checks=90','TValueDirtyGapSafetyRunner':'TVALUE_DIRTY_GAP_OK', 'TValueDirtyJournalSafetyRunner':'TVALUE_DIRTY_JOURNAL_SAFETY_OK checks=83','ExactCandidateReplayRunner':'EXACT_CANDIDATE_REPLAY_OK','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20','DependencyLifecycleWitness':'DEPENDENCY_LIFECYCLE_OK checks=49','FrontierDependencyWitness':'FRONTIER_DEPENDENCY_WITNESS_OK checks=31','KangerLinkerDonorScopeSafetyRunner':'LINKER_DONOR_SCOPE_OK mode=split checks=88','SonProfileRunner':'SAMPLE 2 ','LatentSubstitutionCorpusRunner':'LATENT_CORPUS_PASS'}
matrix=[('journal',mode,'TValueDirtyJournalSafetyRunner','') for mode in ('on','off','verify')]
matrix += [('journal',mode,'TValueDirtyGapSafetyRunner','') for mode in ('on','off','verify')]
for mode in ('clean','journal'):
    for flag in ('on','off'):
        for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner','DependencyLifecycleWitness','FrontierDependencyWitness','KangerLinkerDonorScopeSafetyRunner'):matrix.append((mode,flag,runner,''))
    for runner in ('SonProfileRunner','LatentSubstitutionCorpusRunner'):matrix.append((mode,'on',runner,''))
matrix.append(('journal','on','SonProfileRunner','repeat-'))
matrix += [(build,mode,'TValueOrderedPublicationRunner','') for build in ('clean','journal') for mode in ('on','off')]
start=int(sys.argv[1]) if len(sys.argv)>1 else 0
stop=int(sys.argv[2]) if len(sys.argv)>2 else len(matrix)
for i,(mode,flag,runner,prefix) in enumerate(matrix):
    if i<start or i>=stop:continue
    label=mode+'-'+flag+'-'+prefix+runner;p=root/label;args=[]
    if runner=='ExactCandidateReplayRunner':args=['exact','all']
    if runner=='LatentSolveSyncTransactionRunner':args=[str(p)+'.state',str(p)+'.work']
    options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
    if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    target=['org.kanger.'+runner,*args] if mode=='clean' or runner in ('TValueDirtyJournalSafetyRunner','TValueDirtyGapSafetyRunner') else ['org.kanger.DirtyJournalRunner',runner,*args]
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='tvalue-dirty-buckets-jvm-'),'-Dbench.samples=3','-Djournal.path='+str(p)+'.trace',*options,'-cp','../build/tvalue-dirty-buckets-'+mode+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',*target]
    print('START',i,label,flush=True)
    with Path(str(p)+'.log').open('w') as out,Path(str(p)+'.err').open('w') as err:result=subprocess.run(cmd,stdout=out,stderr=err,timeout=600)
    data=Path(str(p)+'.log').read_bytes();err=Path(str(p)+'.err').read_bytes()
    assert result.returncode==0 and not err and markers[runner] in data.decode(),(label,result.returncode,err.decode()[-500:])
    if mode=='journal' and runner!='TValueDirtyGapSafetyRunner':
        trace=Path(str(p)+'.trace');text=trace.read_bytes();assert text.decode().splitlines()[-1].startswith('DIRTY_JOURNAL_OK '),label
        Path(str(p)+'.trace.gz').write_bytes(gzip.compress(text,mtime=0));trace.unlink()
    Path(str(p)+'.log.gz').write_bytes(gzip.compress(data,mtime=0));Path(str(p)+'.log').unlink()
    print('DONE',i,label,flush=True)
