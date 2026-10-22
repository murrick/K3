from pathlib import Path
import hashlib,json,subprocess
p=Path(__file__).parent;sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();build=json.loads((p/'build-validation.json').read_text())
for mode,record in build['compilations'].items():
 root=Path('../build/option-keys')/mode
 assert {str(f.relative_to(root)):sha(f) for f in root.rglob('*.class')}==record['classes'],mode
 for f,h in record['sources'].items():assert sha(f)==h,f
assert not list(p.rglob('*.jfr'))
assert not subprocess.check_output(['git','diff','dcc8c853','--','docs/tvalue-current-native','docs/tvalue-current-native.md'])
for mode in ('clean','hooked'):
 a=build['compilations']['current-'+mode]['classes'];b=build['compilations']['keys-'+mode]['classes'];assert a.keys()==b.keys();assert [k for k in a if a[k]!=b[k]]==['org/kanger/OptimizationOptions.class']
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'};report=p.with_suffix('.md');files[str(report)]=sha(report)
(p/'manifest.json').write_text(json.dumps({'parent':'dcc8c853a5ea6e7ce7f3963a7558890f25ce0420','native_old':build['old'],'native_current':build['current'],'compiler_sha256':build['compiler_sha256'],'production_changed':False,'old_evidence_unchanged':True,'files':files},indent=2)+'\n');print('OPTION_KEYS_MANIFEST_OK',len(files))
