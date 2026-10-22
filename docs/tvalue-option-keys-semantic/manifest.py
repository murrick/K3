from pathlib import Path
import hashlib,json,subprocess
p=Path(__file__).parent
def sha(f):
 q=Path(f)
 if q.is_absolute():
  try:q=Path.cwd()/q.relative_to('/workspace/K3')
  except ValueError:pass
 return hashlib.sha256(q.read_bytes()).hexdigest()
for prefix in ('tvalue-option-keys','tvalue-option-keys-repeat'):
 m=json.loads(Path('docs',prefix,'manifest.json').read_text())
 for f,h in m['files'].items():assert sha(f)==h,f
assert not subprocess.check_output(['git','diff','8d7a41ef','--','docs/tvalue-option-keys','docs/tvalue-option-keys.md','docs/tvalue-option-keys-repeat','docs/tvalue-option-keys-repeat.md','docs/tvalue-current-native','docs/tvalue-current-native.md'])
b=json.loads((p/'build-validation.json').read_text())
for rec,root in ((b,Path('../build/option-keys-semantic/classes')),(b['launcher'],Path('../build/option-keys-semantic/launcher'))):
 assert {str(f.relative_to(root)):sha(f) for f in root.rglob('*.class')}==rec['classes']
 for f,h in rec['sources'].items():assert sha(f)==h,f
assert not any(p.rglob('*.jfr'))
files={str(f.relative_to(Path.cwd())):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'};files[str(p.with_suffix('.md').relative_to(Path.cwd()))]=sha(p.with_suffix('.md'))
(p/'manifest.json').write_text(json.dumps({'parent':'8d7a41ef0e8511ce964969cdcfe0bbab15cb9b37','native':'60fdc34736c51fa4e5659982630347fb1d1b59fc','previous_manifest_sha256':sha('docs/tvalue-option-keys-repeat/manifest.json'),'old_evidence_unchanged':True,'production_changed':False,'files':files},indent=2)+'\n');print('SEMANTIC_MANIFEST_OK',len(files))
