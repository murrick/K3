from pathlib import Path
import subprocess,tempfile
root=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for mode in ('reference','profile'):
 p=root/('witness-'+mode)
 command=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='frontier-witness-')]+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-cp','../build/frontier-'+mode+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.FrontierDependencyWitness']
 with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:subprocess.run(command,stdout=out,stderr=err,check=True,timeout=60)
 assert not p.with_suffix('.err').read_bytes()
 assert 'FRONTIER_DEPENDENCY_WITNESS_OK checks=31' in p.with_suffix('.log').read_text()
 print('WITNESS_OK',mode,'checks=31')
assert (root/'witness-reference.log').read_bytes()==(root/'witness-profile.log').read_bytes()
