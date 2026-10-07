from pathlib import Path
import subprocess,tempfile,json,hashlib,gzip

root=Path(__file__).parent
parent='c5551eae10ded5a975043e829360c32daf623881'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
output=Path('../build/tvalue-resident-persistent-classes');output.mkdir(parents=True,exist_ok=True)
sources=[root/'ResidentPersistentRead.java',root/'ResidentPersistentRunner.java']
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-owner-pure-observation-clean:lib/jline-3.13.0.jar','-d',str(output),*map(str,sources)]
subprocess.run(cmd,check=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
results=[]
for build in ('clean','shadow'):
    prior=Path('../build/tvalue-owner-pure-observation-'+build)
    expected=json.loads(Path('docs/tvalue-owner-pure-observation',build+'-class-sha256.json').read_text())
    assert all(hashlib.sha256((prior/k).read_bytes()).hexdigest()==v for k,v in expected.items())
    for flag in ('on','off','verify'):
        options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
        if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
        label=build+'-'+flag
        command=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='resident-persistent-jvm-'),*options,'-cp',str(output)+':'+str(prior)+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ResidentPersistentRunner']
        print('START',label,flush=True)
        result=subprocess.run(command,capture_output=True,timeout=60)
        (root/(label+'.log')).write_bytes(result.stdout);(root/(label+'.err')).write_bytes(result.stderr)
        metadata={'build':build,'flags':flag,'command':command,'exit_code':result.returncode,'unchanged_prior_class_count':len(expected)}
        (root/(label+'.result.json')).write_text(json.dumps(metadata,indent=2)+'\n')
        assert result.returncode==0 and not result.stderr and b'RESIDENT_PERSISTENT_OK' in result.stdout,(label,result.stdout.decode(),result.stderr.decode())
        results.append(metadata);print('DONE',label,flush=True)
assert len({(root/(b+'-'+f+'.log')).read_bytes() for b in ('clean','shadow') for f in ('on','off','verify')})==1
(root/'summary.json').write_text(json.dumps({'parent':parent,'fresh_JVMs':len(results),'all_native_stdout_identical':True,'zero_extension_callbacks':True,'persistent_factory_journal_still_unsupported':True,'production_changed':False,'prior_rejected_concurrent_corpus_still_unqualified':True,'reader_class_sha256':{str(p.relative_to(output)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(output.rglob('*.class'))}},indent=2)+'\n')
print('RESIDENT_PERSISTENT_MATRIX_OK JVMs='+str(len(results)))
