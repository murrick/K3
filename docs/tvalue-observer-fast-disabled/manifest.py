from pathlib import Path
import json,hashlib,gzip,subprocess
p=Path(__file__).parent
sha=lambda q:hashlib.sha256(q.read_bytes()).hexdigest()
for q in p.rglob('*.trace'):
 gz=Path(str(q)+'.gz');assert gz.exists() and gzip.decompress(gz.read_bytes())==q.read_bytes();q.unlink()
assert not subprocess.check_output(['git','diff','550ba13fbf4339afbcd3f266c62d4c97461c7626','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','pom.xml','kanger-qualification/pom.xml','.github','docs/tvalue-hygiene-base','docs/tvalue-observer-cost','docs/tvalue-observer-session','kanger-qualification/diagnostics/src'])
assert 'TIMING_RESULTS_PENDING' not in Path('docs/tvalue-observer-fast-disabled.md').read_text()
qualification=json.loads((p/'summary.json').read_text());cost=json.loads((p/'cost/summary.json').read_text())
assert qualification['final_JVMs']==28 and cost['final_JVMs']==120
files=[q for q in p.rglob('*') if q.is_file() and q.name!='manifest.json' and q.suffix!='.pyc']
files+=list(Path('kanger-qualification/diagnostics').rglob('*.java'))+[Path('docs/tvalue-observer-fast-disabled.md'),Path('kanger-qualification/diagnostics/README.md')]
(p/'manifest.json').write_text(json.dumps({str(q):sha(q) for q in sorted(files)},indent=2)+'\n')
print('FAST_DISABLED_MANIFEST_OK',len(files),'JVMs=148')
