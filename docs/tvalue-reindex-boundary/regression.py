from pathlib import Path
import subprocess,json,gzip,tempfile
p=Path(__file__).parent/'regression';p.mkdir(exist_ok=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for name,klass,args in [('delete','DeleteFlushRunner',['delete','none']),('flush','DeleteFlushRunner',['flush','none']),('clear','ClearCloseRunner',['clear','none']),('close','ClearCloseRunner',['close','none']),('upsert','StorageFaultRunner',['none']),('scope','SavedLinksRunner',[])]:
 for mode in ('clean','shadow'):
  for flag in ('on','off','verify'):
   label=name+'-'+mode+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
   if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cp=':'.join(['../build/'+stage+'-'+mode for stage in ('tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary')]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'kanger/resources','kanger-udf/src','lib/jline-3.13.0.jar'])
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='reindex-regression-'),'-Dpersistence.shadow='+str(mode=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp',cp,'org.kanger.'+klass,*args];print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=90)
   Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'fixture':name,'build':mode,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
   for f in p.glob(label+'.*.trace'):Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
   print('DONE',label,flush=True)
