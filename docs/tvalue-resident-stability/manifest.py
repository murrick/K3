from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();build=json.loads((p/'build-validation.json').read_text());assert sha(p/'plan.json')==build['plan_sha256'];assert sha(p/'ResidentStabilityRunner.java')==build['source_sha256'];assert all(sha(f)==h for f,h in {**build['class_sha256'],**build['jar_sha256']}.items())
changed=subprocess.check_output(['git','diff','--cached','303be970','--name-only'],text=True).splitlines();assert all(f.startswith('docs/tvalue-resident-stability') for f in changed),changed
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json' and '__pycache__' not in f.parts};files[str(p.with_suffix('.md'))]=sha(p.with_suffix('.md'))
(p/'manifest.json').write_text(json.dumps({'parent':'303be970c719cea70991791a4286cda9460b9802','native_pin':'2422f7f6d9e1af7608203b1566df64b5f29fe344','previous_study_files_unchanged_in_commit':True,'only_new_fixture_and_evidence':True,'changed_parent_files':changed,'files':files},indent=2)+'\n');print('STABILITY_MANIFEST_OK',len(files))
