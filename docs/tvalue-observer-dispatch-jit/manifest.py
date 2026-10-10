from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
assert not subprocess.check_output(['git','diff','ed648650ee0fc8620244ee5a07e9433b2ba175a8','--','kanger','kanger-data-dumb','kanger-qualification','pom.xml','docs/tvalue-observer-fast-disabled','docs/tvalue-observer-attached-study'])
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
for manifest,mode in [('docs/tvalue-observer-fast-disabled/build-validation.json','hooked'),('docs/tvalue-observer-fast-disabled/control-validation.json','slow'),('docs/tvalue-observer-attached-study/build-validation.json','direct')]:
 b=json.loads(Path(manifest).read_text());classes=b['compilations']['hooked']['class_sha256'] if mode=='hooked' else b['class_sha256']
 runtime=Path('../build/tvalue-observer-attached-direct' if mode=='direct' else '../build/tvalue-observer-fast-disabled-'+mode)
 assert all(sha(runtime/k)==v for k,v in classes.items())
records={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':'ed648650ee0fc8620244ee5a07e9433b2ba175a8','files':records},indent=2)+'\n');print('DISPATCH_MANIFEST_OK',len(records))
