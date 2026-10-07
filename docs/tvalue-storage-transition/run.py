from pathlib import Path
import subprocess,json,gzip,tempfile,sys
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
matrix=[(b,f,m) for m in ('update','generation','publication') for b in ('clean','shadow') for f in ('on','off','verify')]
for i,(build,flag,mode) in enumerate(matrix):
 if len(sys.argv)>1 and i>=int(sys.argv[1]):break
 label=mode+'-'+build+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
 if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='storage-transition-'),'-Dpersistence.shadow='+str(build=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp','../build/tvalue-storage-transition-classes:../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.StorageTransitionRunner',mode]
 print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=90)
 Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'build':build,'flag':flag,'mode':mode,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'STORAGE_TRANSITION_OK' in r.stdout,(label,r.stderr.decode(),r.stdout.decode())
 trace=Path(str(out)+'.trace')
 if trace.exists():Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
