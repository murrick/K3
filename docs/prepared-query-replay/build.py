from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).parent
base='1c943d02d39bd33ebd91fabb7c4190112ab49459'
assert not subprocess.check_output(['git','diff',base,'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
classes='../build/prepared-query-classes'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',classes,'@docs/resident-base-comparison-evidence/sources.txt'],check=True,timeout=120)
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,str(root/'PreparedQueryReplayStudy.java')],check=True,timeout=30)
hashes={str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))}
(root/'class-sha256.json').write_text(json.dumps(hashes,indent=2)+'\n')
reference=json.loads((root/'base-class-sha256.json').read_text())
assert all(hashes[k]==v for k,v in reference.items())
(root/'class-comparison.json').write_text(json.dumps({'shared_classes':len(reference),'changed':[],'base':base},indent=2)+'\n')
print('PREPARED_QUERY_BUILD_READY shared_classes_identical='+str(len(reference)))
v1classes='../build/prepared-query-v1'
subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',v1classes,str(root/'runner-v1/PreparedQueryReplayStudy.java')],check=True,timeout=30)
v1=json.loads((root/'class-sha256-v1.json').read_text())
comparison={str(p.relative_to(v1classes)):hashlib.sha256(p.read_bytes()).hexdigest()==v1[str(p.relative_to(v1classes))] for p in sorted(Path(v1classes).rglob('*.class'))}
assert len(comparison)==4 and all(comparison.values())
(root/'runner-v1-comparison.json').write_text(json.dumps(comparison,indent=2)+'\n')
print('ARCHIVED_V1_RUNNER_READY identical_classes=4')
