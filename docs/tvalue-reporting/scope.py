from pathlib import Path
import subprocess,tempfile,gzip,json
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for flag in ('on','off','verify'):
 label='scope-'+flag;target=p/label
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='report-scope-'),'-Dresult.path='+str(target),*options,'-cp','../build/tvalue-reporting:../build/tvalue-layer-cost:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ReportScopeRunner']
 print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
 (p/(label+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(label+'.err')).write_bytes(r.stderr);(p/(label+'.result.json')).write_text(json.dumps({'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'REPORT_SCOPE_OK' in r.stdout,(label,r.stderr.decode())
 for mode in ('old','memo'):
  trace=p/(label+'.'+mode+'.trace');(p/(trace.name+'.gz')).write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
