from pathlib import Path
import subprocess
root=Path(__file__).parent;tmp=Path('../build/binding-size-source');tmp.mkdir(exist_ok=True)
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
paths=['kanger/src/org/kanger/primitives/ArgumentsList.java','kanger/src/org/kanger/units/Domain.java']
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
for path in paths:
 s=subprocess.check_output(['git','show',base+':'+path],text=True)
 if path.endswith('/Domain.java'):
  needle='this.mind = mind;\n        for (TVariable t : arguments.getTVariables(mind))';assert s.count(needle)==1
  s=s.replace(needle,needle.replace('arguments.getTVariables(mind)','org.kanger.BindingSizeCounters.enumerate(arguments, mind)'))
 dest=tmp/Path(path).name;dest.write_text(s);sources=[str(dest) if x==path else x for x in sources]
sources+=['docs/small-binding-capacity/BindingSizeCounters.java'];manifest=root/'count-sources.txt';manifest.write_text('\n'.join(sources)+'\n')
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d','../build/binding-size-classes','@'+str(manifest)],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/binding-size-classes','-d','../build/binding-size-classes',str(root/'BindingSizeProfileRunner.java')],check=True)
