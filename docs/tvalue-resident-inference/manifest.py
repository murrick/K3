from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='efc8ed2768bb70bc743666f0caeef6b07487153e'
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',parent],text=True).splitlines();assert not subprocess.check_output(['git','diff',parent,'--',*paths])
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
for mode,record in json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations'].items():
 if mode in ('clean','hooked','tests'):assert all(sha(Path('../build/tvalue-observer-fast-disabled-'+mode)/k)==v for k,v in record['class_sha256'].items())
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':parent,'files':files},indent=2)+'\n');print('RESIDENT_MANIFEST_OK',len(files))
