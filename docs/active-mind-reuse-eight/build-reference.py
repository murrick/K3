from pathlib import Path
import subprocess
base="1c943d02d39bd33ebd91fabb7c4190112ab49459"
p=Path('../build/reuse-eight-reference');p.mkdir(exist_ok=True)
r=Path('../build/reuse-eight-clean-source');r.mkdir(exist_ok=True)
# The sole production delta is TVariable; all other production sources must be base-identical.
changes=subprocess.check_output(['git','diff','--name-only',base,'--','kanger/src','kanger-udf/src','kanger-server/src'],text=True).splitlines()
assert changes==['kanger/src/org/kanger/units/TVariable.java'],changes
r.joinpath('TVariable.java').write_text(subprocess.check_output(['git','show',base+':kanger/src/org/kanger/units/TVariable.java'],text=True))
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
sources=[str(r/'TVariable.java') if x=='kanger/src/org/kanger/units/TVariable.java' else x for x in sources]
r.joinpath('sources.txt').write_text('\n'.join(sources)+'\n')
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(p),'@'+str(r/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(p),'-sourcepath','kanger-qualification/src','-d',str(p),'kanger-qualification/src/org/kanger/SonProfileRunner.java'],check=True)
assert (p/'org/kanger/SonProfileRunner.class').read_bytes()==Path('../build/reuse-eight-classes/org/kanger/SonProfileRunner.class').read_bytes()
print('CLEAN_REFERENCE_READY_RUNNER_BYTE_IDENTICAL')
