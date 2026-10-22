from pathlib import Path
import hashlib,json,subprocess
p=Path(__file__).parent
def sha(f):
 q=Path(f)
 if q.is_absolute():
  try:q=Path.cwd()/q.relative_to('/workspace/K3')
  except ValueError:pass
 return hashlib.sha256(q.read_bytes()).hexdigest()
parent=Path('docs/tvalue-option-keys');old=json.loads((parent/'manifest.json').read_text())
for f,h in old['files'].items():assert sha(f)==h,f
assert not subprocess.check_output(['git','diff','2a082580','--','docs/tvalue-option-keys','docs/tvalue-option-keys.md','docs/tvalue-current-native','docs/tvalue-current-native.md'])
assert not list(p.rglob('*.jfr'))
files={str(f.relative_to(Path.cwd())):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'};files[str(p.with_suffix('.md').relative_to(Path.cwd()))]=sha(p.with_suffix('.md'))
(p/'manifest.json').write_text(json.dumps({'parent':'2a082580b7f00c211b376047fa617f615897da1b','build_manifest_sha256':sha(parent/'build-validation.json'),'previous_manifest_sha256':sha(parent/'manifest.json'),'old_evidence_unchanged':True,'production_changed':False,'files':files},indent=2)+'\n');print('REPEAT_MANIFEST_OK',len(files))
