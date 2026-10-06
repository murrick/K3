from pathlib import Path
import subprocess,hashlib,json,tempfile,gzip
root=Path(__file__).parent
base='e84d1748fd7d5b963cc8f5acc89ecfc4a23c0f2d'
assert subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-qualification/src','.github'])==b''
classes=Path('../build/dependency-lifecycle');classes.mkdir(parents=True,exist_ok=True)
listing=Path('docs/resident-base-comparison-evidence/sources.txt')
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(classes),'@'+str(listing)],check=True,timeout=120)
runners=['KangerLinkerDonorScopeSafetyRunner','KangerDomainWaiterRollbackSafetyRunner','LatentSolveSyncTransactionRunner']
extra=[root/'DependencyLifecycleWitness.java',Path('docs/linker-donor-universe/FrontierDependencyWitness.java')]+[Path('kanger-qualification/src/org/kanger/'+r+'.java') for r in runners]
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(classes)+':lib/jline-3.13.0.jar','-sourcepath','kanger-qualification/src','-d',str(classes),*map(str,extra)],check=True,timeout=45)
hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(classes.rglob('*.class'))}
prior=json.loads(Path('docs/linker-donor-universe/split-class-sha256.json').read_text())
shared=sorted(set(prior)&set(hashes));changed=[p for p in shared if prior[p]!=hashes[p]];assert not changed,changed
(root/'class-sha256.json').write_text(json.dumps(hashes,indent=2)+'\n')
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
markers={'DependencyLifecycleWitness':'DEPENDENCY_LIFECYCLE_OK checks=49','FrontierDependencyWitness':'FRONTIER_DEPENDENCY_WITNESS_OK checks=31','KangerLinkerDonorScopeSafetyRunner':'LINKER_DONOR_SCOPE_OK mode=split checks=88','KangerDomainWaiterRollbackSafetyRunner':'DOMAIN_WAITER_ROLLBACK_OK','LatentSolveSyncTransactionRunner':'LATENT_SOLVE_TRANSACTIONS_PASS operations=20'}
logs={};states={};work={}
for mode in ('on','off','verify'):
    options=['-Dkanger.experiment.'+f+'='+('false' if mode=='off' else 'true') for f in flags]
    if mode=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    for runner,marker in markers.items():
        label=mode+'-'+runner;args=[]
        if runner=='LatentSolveSyncTransactionRunner':args=[str(root/(label+'.state')),str(root/(label+'.work'))]
        print('START',label,flush=True)
        result=subprocess.run(['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='dependency-jvm-'),*options,'-cp',str(classes)+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner,*args],capture_output=True,timeout=300)
        (root/(label+'.log.gz')).write_bytes(gzip.compress(result.stdout,mtime=0));(root/(label+'.err')).write_bytes(result.stderr)
        assert result.returncode==0 and not result.stderr and marker in result.stdout.decode(),label
        logs[(mode,runner)]=result.stdout
        if args:states[mode]=Path(args[0]).read_bytes();work[mode]=Path(args[1]).read_bytes()
        print('DONE',label,flush=True)
    # Fresh-process repeat of the new witness checks its complete deterministic rows.
    label=mode+'-repeat-DependencyLifecycleWitness'
    result=subprocess.run(['java','-Xmx512m',*options,'-cp',str(classes)+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.DependencyLifecycleWitness'],capture_output=True,timeout=60)
    (root/(label+'.log.gz')).write_bytes(gzip.compress(result.stdout,mtime=0));(root/(label+'.err')).write_bytes(result.stderr)
    assert result.returncode==0 and not result.stderr and result.stdout==logs[(mode,'DependencyLifecycleWitness')],label
for runner in markers:assert logs[('on',runner)]==logs[('off',runner)]==logs[('verify',runner)],runner
assert states['on']==states['off']==states['verify']
assert work['on']==work['verify']
summary={'base':base,'production_source_unchanged':True,'shared_prior_classes_identical':len(shared),'changed_shared_classes':changed,'accepted_jvms':18,'new_native_checks_per_jvm':49,'new_witness_complete_rows_equal_modes_and_repeats':True,'existing_dependency_checks_per_mode':31,'donor_scope_checks_per_mode':88,'transaction_operations_per_mode':20,'transaction_full_state_equal_across_modes':True,'all_stderr_empty':True,'scope':'native lifecycle witnesses and source audit; no scheduler or event journal installed, no speedup claim'}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print('DEPENDENCY_QUALIFICATION_OK',json.dumps(summary))
