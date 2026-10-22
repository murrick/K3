from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
changed=subprocess.check_output(['git','diff','f21b2f3f','--name-only'],text=True).splitlines();assert all(x.startswith('docs/tvalue-resident-warmup') for x in changed),changed
build=json.loads((p/'build-validation.json').read_text())
for mode in ('clean','hooked','baseline','candidate','fixture'):
 out=Path('../build')/({'clean':'tvalue-observer-fast-disabled-clean','hooked':'tvalue-observer-fast-disabled-hooked','baseline':'warmup-baseline','candidate':'warmup-candidate','fixture':'tvalue-resident-warmup-tests'}[mode])
 actual={str(x.relative_to(out)):sha(x) for x in out.rglob('*.class')};assert actual==build['classes'][mode],mode
files={str(f):sha(f) for f in sorted(p.rglob('*')) if f.is_file() and f.name!='manifest.json' and '__pycache__' not in f.parts}
files[str(p.with_suffix('.md'))]=sha(p.with_suffix('.md'))
record={'parent':'f21b2f3f1cdf98fd1769d8dd37685635bc2c8fae','native_pin':'2422f7f6d9e1af7608203b1566df64b5f29fe344','changed_tracked_files':changed,'only_new_documentation_and_fixture':True,'compiler_sha256':sha('../tooling/ecj.jar'),'files':files,'jars':{str(x):sha(x) for x in [Path('../build/tvalue-journal-context-snapshots.jar'),Path('../build/tvalue-journal-touch-strings.jar')]},'native_source_sha256':{f:sha(f) for f in Path('docs/tvalue-observer-fast-disabled/native-sources.txt').read_text().splitlines()}}
(p/'manifest.json').write_text(json.dumps(record,indent=2)+'\n');print('WARMUP_MANIFEST_OK',len(files))
