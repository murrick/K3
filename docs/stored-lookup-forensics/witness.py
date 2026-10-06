from pathlib import Path
import subprocess

root = Path(__file__).parent
classes = '../build/stored-lookup-reference'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar',
                '-d',classes,'@docs/resident-base-comparison-evidence/sources.txt'],check=True)
for mode, folder in [('clean',classes),('diagnostic','../build/stored-lookup-classes')]:
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',folder,'-d',folder,str(root/'StoredLookupWitness.java')],check=True)
    prefix=root/('witness-'+mode)
    with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
        subprocess.run(['java','-cp',folder+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
                        'org.kanger.StoredLookupWitness'],stdout=out,stderr=err,check=True,timeout=60)
    assert 'STORED_LOOKUP_WITNESS_OK scenarios=3 checks=33' in prefix.with_suffix('.log').read_text()
    assert not prefix.with_suffix('.err').read_text()
assert (root/'witness-clean.log').read_bytes()==(root/'witness-diagnostic.log').read_bytes()
print((root/'witness-clean.log').read_text())
