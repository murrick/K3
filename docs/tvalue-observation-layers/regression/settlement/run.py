from pathlib import Path
import subprocess,json,gzip,tempfile
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for mode in ('accept','reject','user-reject','exception'):
 for build in ('clean','shadow'):
  for flag in ('on','off','verify'):
   label=mode+'-'+build+'-'+flag;out=p/label;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
   if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='persistent-settlement-'),'-Dpersistence.shadow='+str(build=='shadow').lower(),'-Djournal.path='+str(out),*opts,'-cp','../build/tvalue-observation-layers-'+build+':../build/tvalue-journal-cost-'+build+':../build/tvalue-reindex-boundary-'+build+':../build/tvalue-clear-close-guard-'+build+':../build/tvalue-delete-flush-guard-'+build+':../build/tvalue-write-guard-'+build+':../build/tvalue-saved-links-'+build+':../build/tvalue-update-boundary-'+build+':../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.PersistentSettlementRunner',mode]
   print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=120);Path(str(out)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(out)+'.err').write_bytes(r.stderr);Path(str(out)+'.result.json').write_text(json.dumps({'mode':mode,'build':build,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'PERSISTENT_SETTLEMENT_OK' in r.stdout,(label,r.stderr.decode(),r.stdout.decode())
   for f in p.glob(label+'.*.trace'):
    Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
   print('DONE',label,flush=True)

for build in ('clean','shadow'):
 for flag in ('on','off','verify'):
  label='scope-'+build+'-'+flag;opts=['-Dkanger.experiment.'+f+'='+str(flag!='off').lower() for f in flags]
  if flag=='verify':opts+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
  cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='saved-links-scope-'),*opts,'-cp','../build/tvalue-observation-layers-'+build+':../build/tvalue-journal-cost-'+build+':../build/tvalue-reindex-boundary-'+build+':../build/tvalue-clear-close-guard-'+build+':../build/tvalue-delete-flush-guard-'+build+':../build/tvalue-write-guard-'+build+':../build/tvalue-saved-links-'+build+':../build/tvalue-update-boundary-'+build+':../build/tvalue-materialization-routing-classes:../build/tvalue-persistent-lookup-classes:../build/tvalue-resident-persistent-classes:../build/tvalue-owner-pure-observation-'+build+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SavedLinksRunner']
  print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=60);(p/(label+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(label+'.err')).write_bytes(r.stderr);(p/(label+'.result.json')).write_text(json.dumps({'mode':'scope','build':build,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'SAVED_LINKS_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode());print('DONE',label,flush=True)
