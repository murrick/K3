from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='66413653cf3764821b0bdd14d784226a281a834a'
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',parent],text=True).splitlines();assert not subprocess.check_output(['git','diff',parent,'--',*paths])
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
for mode,record in json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations'].items():
 if mode in ('clean','hooked','tests'):assert all(sha(Path('../build/tvalue-observer-fast-disabled-'+mode)/k)==v for k,v in record['class_sha256'].items())
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':parent,'files':files},indent=2)+'\n');print('NO_LOGGING_MANIFEST_OK',len(files))
