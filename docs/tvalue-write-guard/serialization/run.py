from pathlib import Path
import subprocess,json,gzip,tempfile
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for mode in ('success','failure'):
 for build in ('clean','shadow'):
  for flag in ('on','off','verify'):
   label=mode+'-'+build+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
   if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='update-boundary-'),'-Dpersistence.shadow='+str(build=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp','../build/tvalue-write-guard-'+build+':../build/tvalue-saved-links-'+build+':../build/tvalue-update-boundary-'+build+':../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.UpdateBoundaryRunner',mode]
   print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=90);Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'mode':mode,'build':build,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'UPDATE_BOUNDARY_OK' in r.stdout,(label,r.stderr.decode(),r.stdout.decode())
   for suffix in ('.success.trace','.failure-prefix.trace'):
    f=Path(str(out)+suffix)
    if f.exists():Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
   print('DONE',label,flush=True)
