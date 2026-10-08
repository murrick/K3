from pathlib import Path
import subprocess,json,gzip,tempfile
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for size in (32,128,512):
 for repetition in range(3):
  for mode in ('clean','shadow'):
   for flag in ('on','off','verify'):
    label=str(size)+'-'+str(repetition)+'-'+mode+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
    if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
    cp=':'.join(['../build/'+stage+'-'+mode for stage in ('tvalue-observation-layers','tvalue-journal-cost','tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary')]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'kanger/resources','kanger-udf/src','lib/jline-3.13.0.jar'])
    cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='journal-cost-'),'-Dpersistence.shadow='+str(mode=='shadow').lower(),'-Djournal.cost.enabled=true','-Djournal.path='+str(out),*opts,'-cp',cp,'org.kanger.JournalCostRunner',str(size)];print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=120)
    Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'size':size,'repetition':repetition,'build':mode,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'JOURNAL_COST_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
    for f in p.glob(label+'.trace'):Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
    print('DONE',label,flush=True)
