from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;parent='aac2437d055b146a765fc0efc2849a209c10cef7'
paths=subprocess.check_output(['git','ls-tree','-r','--name-only',parent],text=True).splitlines()
# Every tracked parent file must remain byte-identical.
assert not subprocess.check_output(['git','diff',parent,'--',*paths])
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':parent,'files':files},indent=2)+'\n');print('EXPORT_PHASE_MANIFEST_OK',len(files))
