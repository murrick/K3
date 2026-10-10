from pathlib import Path
import subprocess,hashlib,json,zipfile,shutil
p=Path(__file__).parent;m=Path('kanger-qualification/diagnostics');out=Path('../build/tvalue-journal-context-snapshots-module')
if out.exists():shutil.rmtree(out)
out.mkdir(parents=True)
paths=(m/'sources.txt').read_text().splitlines();cp='../build/tvalue-observer-fast-disabled-clean:lib/jline-3.13.0.jar'
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(m/'sources.txt')];subprocess.run(cmd,check=True)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest();classes={str(f.relative_to(out)):sha(f) for f in out.rglob('*.class')}
prior=json.loads(Path('docs/tvalue-journal-three-routes/build-validation.json').read_text())['class_sha256'];assert set(classes)==set(prior)
changed=sorted(k for k in classes if classes[k]!=prior[k]);assert all(k.startswith(('org/kanger/BeforeAuthorityJournal','org/kanger/StreamAuthorityJournal')) for k in changed)
jar=Path('../build/tvalue-journal-context-snapshots.jar')
with zipfile.ZipFile(jar,'w') as z:
 for name in sorted(classes):z.writestr(zipfile.ZipInfo(name,(1980,1,1,0,0,0)),(out/name).read_bytes())
fixture=Path('../build/tvalue-journal-context-snapshots-tests');fixture.mkdir(exist_ok=True)
source=p/'ContextSnapshotRunner.java';cmd2=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp+':'+str(jar),'-d',str(fixture),str(source)];subprocess.run(cmd2,check=True)
(p/'build-validation.json').write_text(json.dumps({'commands':[cmd,cmd2],'compiler_sha256':sha(Path('../tooling/ecj.jar')),'source_sha256':{f:sha(Path(f)) for f in paths},'class_sha256':classes,'changed_classes':changed,'jar_sha256':sha(jar),'fixture_source_sha256':sha(source),'fixture_class_sha256':{str(f.relative_to(fixture)):sha(f) for f in fixture.rglob('*.class')}},indent=2)+'\n');print('CONTEXT_SNAPSHOT_BUILD_OK',changed)
