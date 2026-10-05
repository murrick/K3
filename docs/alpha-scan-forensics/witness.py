from pathlib import Path
import subprocess
root=Path(__file__).parent
compiler=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn']
classes='../build/alpha-scan-reference'
subprocess.run(compiler+['-cp','lib/jline-3.13.0.jar','-d',classes,'@docs/resident-base-comparison-evidence/sources.txt'],check=True,timeout=120)
results=[]
for variant,classes in [('reference','../build/alpha-scan-reference'),('instrumented','../build/alpha-scan-classes')]:
 subprocess.run(compiler+['-cp',classes,'-d',classes,str(root/'AlphaScanWitness.java')],check=True,timeout=30)
 p=root/('witness-'+variant)
 with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:
  subprocess.run(['java','-Xmx512m','-cp',classes+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.AlphaScanWitness'],stdout=out,stderr=err,check=True,timeout=30)
 s=p.with_suffix('.log').read_text();assert not p.with_suffix('.err').read_text()
 assert 'ALPHA_SCAN_WITNESS_OK scenarios=3 checks=45' in s;results.append(s)
assert results[0]==results[1]
print('ALPHA_SCAN_WITNESSES_OK builds=2 scenarios_per_build=3 checks_per_build=45')
