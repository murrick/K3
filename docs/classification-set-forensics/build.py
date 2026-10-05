from pathlib import Path
import subprocess
root=Path(__file__).parent;tmp=Path('../build/classification-source');tmp.mkdir(exist_ok=True)
original=subprocess.check_output(['git','show','1c943d02d39bd33ebd91fabb7c4190112ab49459:kanger/src/org/kanger/Linker.java'],text=True);start=original.index('    private boolean linkDatabase(');end=original.index('    private void logCauses',start);method=original[start:end]
for i,name in enumerate(['excluded','calculated','candidates','assumed','stored']):
 needle='Set<Domain> '+name+' = new HashSet<>();';assert method.count(needle)==1;method=method.replace(needle,'Set<Domain> '+name+' = ClassificationCounters.create('+str(i)+');')
needle='            for (Domain d : tree) {';assert method.count(needle)>=2
marker='            Set<Domain> stored = ClassificationCounters.create(4);';method=method.replace(marker,marker+'\n            try {',1)
marker='            if (candidates.size() == 1) {';assert method.count(marker)==1;method=method.replace(marker,'            ClassificationCounters.checkpoint(excluded, calculated, candidates, assumed, stored);\n'+marker)
marker='        }\n        return result;';assert method.count(marker)==1;method=method.replace(marker,'            } finally { ClassificationCounters.complete(excluded, calculated, candidates, assumed, stored); }\n'+marker)
modified=original[:start]+method+original[end:];tmp.joinpath('Linker.java').write_text(modified)
r=subprocess.run(['diff','-u','kanger/src/org/kanger/Linker.java',str(tmp/'Linker.java')],capture_output=True,text=True);assert r.returncode==1;root.joinpath('instrumentation.patch').write_text(r.stdout)
sources=Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines();sources=[str(tmp/'Linker.java') if x=='kanger/src/org/kanger/Linker.java' else x for x in sources]+[str(root/'ClassificationCounters.java')];root.joinpath('sources.txt').write_text('\n'.join(sources)+'\n')
s=Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text().replace('public final class SonProfileRunner','public final class ClassificationProfileRunner');s=s.replace('try { mind.optimizeHypothesis(); }','ClassificationCounters.begin();\n            try { mind.optimizeHypothesis(); }').replace('finally { if (sampler != null) sampler.running = false; }\n            long optimizeNs','finally { ClassificationCounters.finish(i); if (sampler != null) sampler.running = false; }\n            long optimizeNs');root.joinpath('ClassificationProfileRunner.java').write_text(s)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d','../build/classification-classes','@'+str(root/'sources.txt')],check=True)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/classification-classes','-d','../build/classification-classes',str(root/'ClassificationProfileRunner.java')],check=True)
