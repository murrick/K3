from pathlib import Path
import subprocess,json,hashlib,gzip
p=Path(__file__).parent;parent='73c5d6c283697114b962e75eff8dd2ddd0e8107c'
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
assert not subprocess.check_output(['git','diff',parent,'--','kanger','kanger-data-dumb','kanger-qualification/src','pom.xml','kanger-qualification/pom.xml','.github','kanger-qualification/diagnostics/runtime','kanger-qualification/diagnostics/test','docs/tvalue-journal-context-snapshots','docs/tvalue-journal-three-routes','docs/tvalue-observer-fast-disabled'])
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
prior=json.loads(Path('docs/tvalue-journal-context-snapshots/build-validation.json').read_text())
changed={f'kanger-qualification/diagnostics/src/org/kanger/{name}.java' for name in ('BeforeAuthorityJournal','StreamAuthorityJournal')}
for name in ('BeforeAuthorityJournal','StreamAuthorityJournal'):
 f=f'kanger-qualification/diagnostics/src/org/kanger/{name}.java';assert sha(p/(name+'.before.java'))==prior['source_sha256'][f]
assert all(sha(f)==v for f,v in prior['source_sha256'].items() if f not in changed)
assert sha('../build/tvalue-journal-context-snapshots.jar')==prior['jar_sha256']
assert all(sha(Path('../build/tvalue-journal-context-snapshots-tests')/k)==v for k,v in prior['fixture_class_sha256'].items())
for mode,record in json.loads(Path('docs/tvalue-observer-fast-disabled/build-validation.json').read_text())['compilations'].items():
 if mode in ('clean','hooked','tests'):assert all(sha(Path('../build/tvalue-observer-fast-disabled-'+mode)/k)==v for k,v in record['class_sha256'].items())
for f in p.glob('*.trace'):
 z=Path(str(f)+'.gz');assert z.exists() and gzip.decompress(z.read_bytes())==f.read_bytes();f.unlink()
records={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json'}
(p/'manifest.json').write_text(json.dumps({'parent':parent,'files':records},indent=2)+'\n');print('CONTEXT_SNAPSHOT_MANIFEST_OK',len(records))
