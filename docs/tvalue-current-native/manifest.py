from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();build=json.loads((p/'build-validation.json').read_text())
for mode,record in build['compilations'].items():
 root=Path('../build/current-native')/mode;assert {str(f.relative_to(root)):sha(f) for f in root.rglob('*.class')}==record['classes'],mode
changed=subprocess.check_output(['git','diff','--cached','95f1c4ef','--name-only'],text=True).splitlines();assert all(x.startswith('docs/tvalue-current-native') for x in changed),changed
assert not list(p.rglob('*.jfr'))
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'};files[str(p.with_suffix('.md'))]=sha(p.with_suffix('.md'));(p/'manifest.json').write_text(json.dumps({'parent':'95f1c4ef93bd228ce04c35e03fc5fc8380f5919a','native_old':build['old'],'native_current':build['current'],'only_new_fixture_and_evidence':True,'changed_commit_paths':changed,'files':files},indent=2)+'\n');print('MANIFEST_OK',len(files))
