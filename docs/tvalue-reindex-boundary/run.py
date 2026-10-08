from pathlib import Path
import subprocess,json,gzip,tempfile,sys
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for stop in (() if '--db-only' in sys.argv else (0,1,2,3)):
 for mode in ('clean','shadow'):
  for flag in ('on','off','verify'):
   label=str(stop)+'-'+mode+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
   if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cp=':'.join(['../build/'+stage+'-'+mode for stage in ('tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary')]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'kanger/resources','kanger-udf/src','lib/jline-3.13.0.jar'])
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='reindex-'),'-Dpersistence.shadow='+str(mode=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp',cp,'org.kanger.ReindexRunner',str(stop)];print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=60)
   Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'stop':stop,'build':mode,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'REINDEX_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode());print('DONE',label,flush=True)

for stop in (0,1,2,3):
 for mode in ('clean','shadow'):
  for flag in ('on','off','verify'):
   label='db-'+str(stop)+'-'+mode+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
   if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cp=':'.join(['../build/'+stage+'-'+mode for stage in ('tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary')]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'kanger/resources','kanger-udf/src','lib/jline-3.13.0.jar'])
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='reindex-'),'-Dpersistence.shadow='+str(mode=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp',cp,'org.kanger.DBReindexRunner',str(stop)];print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=60)
   Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'stop':stop,'build':mode,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'DB_REINDEX_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode());print('DONE',label,flush=True)
