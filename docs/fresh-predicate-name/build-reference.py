from pathlib import Path
import subprocess
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
classes=Path('../build/fresh-name-reference');classes.mkdir(exist_ok=True)
source=Path('../build/fresh-name-clean-source');source.mkdir(exist_ok=True)
expected=['kanger/src/org/kanger/units/Predicate.java']
changed=subprocess.check_output(['git','diff','--name-only',base,'--','kanger/src','kanger-udf/src','kanger-server/src'],text=True).splitlines()
assert changed==expected,changed
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
for filename in expected:
 dest=source/Path(filename).name
 dest.write_text(subprocess.check_output(['git','show',base+':'+filename],text=True))
 sources=[str(dest) if x==filename else x for x in sources]
source.joinpath('sources.txt').write_text('\n'.join(sources)+'\n')
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(classes),'@'+str(source/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(classes),'-sourcepath','kanger-qualification/src','-d',str(classes),'kanger-qualification/src/org/kanger/SonProfileRunner.java'],check=True)
assert (classes/'org/kanger/SonProfileRunner.class').read_bytes()==Path('../build/fresh-name-classes/org/kanger/SonProfileRunner.class').read_bytes()
print('CLEAN_REFERENCE_READY_RUNNER_BYTE_IDENTICAL')
