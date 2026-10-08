from pathlib import Path
import subprocess,json,gzip,tempfile
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
flush=['flush-after-'+phase+'-'+str(n) for n in (1,2) for phase in ('index','data','integrity','checkpoint')]
for operation,points in [('clear',['none','empty',*flush,*['clear-after-'+x for x in ('data','index','integrity','cache','last-id','endpoints')]]),('close',['none',*flush[:4],*['close-after-'+x for x in ('compact','cache','index','data','endpoints')]])]:
 for point in points:
  for build in ('clean','shadow'):
   for flag in ('on','off','verify'):
    label=operation+'-'+point+'-'+build+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
    if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    cp=':'.join(['../build/tvalue-clear-close-guard-'+build,'../build/tvalue-delete-flush-guard-'+build,'../build/tvalue-write-guard-'+build,'../build/tvalue-saved-links-'+build,'../build/tvalue-update-boundary-'+build,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+build,'kanger/resources','kanger-udf/src','lib/jline-3.13.0.jar'])
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='clear-close-'),'-Dpersistence.shadow='+str(build=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp',cp,'org.kanger.ClearCloseRunner',operation,point];print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=60)
    Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'operation':operation,'point':point,'build':build,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'CLEAR_CLOSE_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode());print('DONE',label,flush=True)
subprocess.run(['python',str(p/'regression'/'run.py')],check=True)
for name in ('settlement','serialization','faults'):subprocess.run(['python',str(p/'regression'/name/'run.py')],check=True)
