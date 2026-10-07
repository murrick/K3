from pathlib import Path
import subprocess,tempfile,gzip,json,sys
root=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
matrix=[(build,flag,"MaterializationRoutingRunner",[]) for build in ("clean","shadow") for flag in ("on","off","verify")]
for flag in ('on','off','verify'):
    for build in ('clean','shadow'):matrix.append((build,flag,'TValueOwnerPureRunner',[]))
    matrix.extend([('shadow',flag,'TValueMetadataSafetyRunner',[]),('shadow',flag,'TValueDirtyJournalSafetyRunner',[]),('shadow',flag,'TValuePersistenceRunner',[])])
for flag in ('on','off','verify'):
    for mode in ('sapato','trap-sapato','trap-step','trap-value','unresolved','cycle','connection','missing-dirty','lookup-alias'):matrix.append(('shadow',flag,'TValueResidentBoundaryRunner',[mode]))
for build in ('clean','shadow'):
    for flag in ('on','off'):
        matrix.extend([(build,flag,'ExactCandidateReplayRunner',['exact','all']),(build,flag,'LatentSolveSyncTransactionRunner',[]),(build,flag,'TValueOrderedPublicationRunner',[])])
    matrix.append((build,'on','KangerMindCommitExceptionAtomicitySafetyRunner',[]))
markers={'MaterializationRoutingRunner':'MATERIALIZATION_ROUTING_OK','TValueOwnerPureRunner':'TVALUE_OWNER_PURE_OK','TValueMetadataSafetyRunner':'TVALUE_METADATA_SAFETY_OK','TValueDirtyJournalSafetyRunner':'TVALUE_DIRTY_JOURNAL_SAFETY_OK checks=83','TValuePersistenceRunner':'TVALUE_PERSISTENCE_OK checks=22','TValueResidentBoundaryRunner':'TVALUE_RESIDENT_BOUNDARY_OK','ExactCandidateReplayRunner':'EXACT_CANDIDATE_REPLAY_OK','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20','TValueOrderedPublicationRunner':'TVALUE_ORDERED_PUBLICATION_OK checks=90','KangerMindCommitExceptionAtomicitySafetyRunner':'MIND_COMMIT_EXCEPTION_ATOMICITY_OK'}
start=int(sys.argv[1]) if len(sys.argv)>1 else 0;stop=int(sys.argv[2]) if len(sys.argv)>2 else len(matrix)
for i,(build,flag,runner,args) in enumerate(matrix):
    if not start<=i<stop:continue
    label=build+'-'+flag+'-'+runner+('-'+args[0] if runner=='TValueResidentBoundaryRunner' else '');p=root/label
    if runner=='LatentSolveSyncTransactionRunner':args=[str(p)+'.state',str(p)+'.work']
    options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
    if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    wrapped=build=='shadow' and runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner','TValueOrderedPublicationRunner')
    target=['org.kanger.DirtyJournalRunner',runner,*args] if wrapped else ['org.kanger.'+runner,*args]
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='tvalue-persistent-lookup-jvm-'),'-Dpersistence.shadow='+str(build=='shadow').lower(),'-Dmetadata.shadow='+str(build=='shadow').lower(),'-Djournal.path='+str(p)+'.trace',*options,'-cp','../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',*target]
    print('START',i,label,flush=True)
    with Path(str(p)+'.log').open('w') as out,Path(str(p)+'.err').open('w') as err:result=subprocess.run(cmd,stdout=out,stderr=err,timeout=600)
    data=Path(str(p)+'.log').read_bytes();err=Path(str(p)+'.err').read_bytes()
    (root/(label+'.result.json')).write_text(json.dumps({'index':i,'build':build,'flag':flag,'runner':runner,'args':args,'exit_code':result.returncode,'expected_closed_boundary_rejection':runner=='TValueResidentBoundaryRunner','post_native_materialization_hook':True,'expected_rematerialization_metadata_rejection':False,'command':cmd},indent=2)+'\n')
    assert result.returncode==0 and not err and markers[runner] in data.decode(),(label,result.returncode,err.decode()[-2200:],data.decode()[-600:])
    has_trace=build=='shadow' and (wrapped or runner in ('TValueOwnerPureRunner','TValueMetadataSafetyRunner','TValueDirtyJournalSafetyRunner','TValuePersistenceRunner'))
    if runner=='MaterializationRoutingRunner' and build=='shadow':
        for suffix in ('.positive.trace','.rebound.trace','.fresh.trace'):
            trace=Path(str(p)+'.trace'+suffix);body=trace.read_bytes();assert body.decode().splitlines()[-1].startswith('DIRTY_JOURNAL_OK ')
            Path(str(trace)+'.gz').write_bytes(gzip.compress(body,mtime=0));trace.unlink()
    if has_trace:
        trace=Path(str(p)+'.trace');text=trace.read_bytes();assert text.decode().splitlines()[-1].startswith('DIRTY_JOURNAL_OK ')
        Path(str(p)+'.trace.gz').write_bytes(gzip.compress(text,mtime=0));trace.unlink()
    Path(str(p)+'.log.gz').write_bytes(gzip.compress(data,mtime=0));Path(str(p)+'.log').unlink()
    print('DONE',i,label,flush=True)

# Frozen negative oracle uses the exact preceding helpers/native Base (no hook).
if stop==len(matrix):
    for flag in ('on','off','verify'):
        if len(sys.argv)>3 and sys.argv[3]=='scope':continue
        label='baseline-'+flag+'-PersistentLookupRunner';p=root/label
        options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
        if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
        cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='materialization-baseline-'),'-Dpersistence.shadow=true','-Djournal.path='+str(p)+'.trace',*options,'-cp','../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-shadow:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.PersistentLookupRunner']
        print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=90)
        Path(str(p)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(p)+'.err').write_bytes(r.stderr)
        Path(str(p)+'.result.json').write_text(json.dumps({'runner':'PersistentLookupRunner','flags':flag,'native_hook_enabled':False,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
        assert r.returncode==0 and not r.stderr and b'expected_metadata_rejection=true' in r.stdout
        for suffix in ('.positive.trace','.fresh.trace'):
            trace=Path(str(p)+'.trace'+suffix);Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
        print('DONE',label,flush=True)
    for flag in ('on','off','verify'):
        for mode in ('variable','index'):
            label='scope-'+flag+'-'+mode;p=root/label
            options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
            if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
            cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='materialization-scope-'),'-Djournal.path='+str(p)+'.trace',*options,'-cp','../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-shadow:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.MaterializationScopeRunner',mode]
            print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=90)
            Path(str(p)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(p)+'.err').write_bytes(r.stderr)
            Path(str(p)+'.result.json').write_text(json.dumps({'runner':'MaterializationScopeRunner','flags':flag,'mode':mode,'native_hook_enabled':True,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
            assert r.returncode==0 and not r.stderr and b'MATERIALIZATION_SCOPE_OK' in r.stdout
            assert ('unsupported materialized variable change' if mode=='variable' else 'materialization requires valid native lookup metadata') in Path(str(p)+'.trace.scope-error.txt').read_text()
            print('DONE',label,flush=True)
