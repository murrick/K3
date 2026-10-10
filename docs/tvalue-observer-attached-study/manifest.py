from pathlib import Path
import hashlib,json,subprocess
p=Path(__file__).parent
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
assert not subprocess.check_output(['git','diff','1867139081ecd2a48a67e656a948f6d774bd924b','--','kanger','kanger-data-dumb','kanger-qualification','pom.xml','docs/tvalue-observer-fast-disabled'])
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
records={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':'1867139081ecd2a48a67e656a948f6d774bd924b','files':records},indent=2)+'\n')
print('ATTACHED_MANIFEST_OK',len(records))
