from pathlib import Path
import subprocess,tempfile,shutil,gzip,json
p=Path(__file__).parent
for flag in ('on','off','verify'):
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in ['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 home=tempfile.mkdtemp(prefix='journal-gate-');cp='../build/tvalue-journal-three-routes-tests:../build/tvalue-journal-touch-strings.jar:../build/tvalue-observer-fast-disabled-hooked:../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 cmd=['java','-Duser.home='+home,*options,'-cp',cp,'org.kanger.GateDisagreementRunner']
 try:r=subprocess.run(cmd,capture_output=True,timeout=30)
 finally:shutil.rmtree(home)
 (p/(flag+'.gate.json')).write_text(json.dumps({'exit_code':r.returncode,'command':cmd},indent=2)+'\n');(p/(flag+'.gate.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(flag+'.gate.err')).write_bytes(r.stderr)
 assert r.returncode==0 and not r.stderr and b'JOURNAL_DISAGREEMENT_GATE_OK' in r.stdout,(flag,r.stdout,r.stderr)
 print('GATE_OK',flag,flush=True)
