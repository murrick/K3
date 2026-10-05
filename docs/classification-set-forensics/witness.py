from pathlib import Path
import subprocess
root=Path(__file__).parent
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d','../build/classification-reference','@docs/resident-base-comparison-evidence/sources.txt'],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/classification-reference','-d','../build/classification-witness',str(root/'ClassificationSetWitness.java')],check=True)
with (root/'witness.log').open('w') as out,(root/'witness.err').open('w') as err:subprocess.run(['java','-cp','../build/classification-witness:../build/classification-reference','ClassificationSetWitness'],stdout=out,stderr=err,check=True,timeout=30)
assert not (root/'witness.err').read_text()
assert 'CLASSIFICATION_SET_WITNESS_OK scenarios=2 checks=7' in (root/'witness.log').read_text()
print((root/'witness.log').read_text())
