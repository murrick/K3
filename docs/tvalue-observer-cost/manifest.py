from pathlib import Path
import hashlib,json
p=Path(__file__).parent
files=[q for q in p.rglob('*') if q.is_file() and q.name!='manifest.json' and q.suffix!='.pyc']
files += [Path('docs/tvalue-observer-cost.md'),Path('kanger-qualification/diagnostics/test/org/kanger/ObserverCostRunner.java')]
(p/'manifest.json').write_text(json.dumps({str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in sorted(files)},indent=2)+'\n');print('OBSERVER_COST_MANIFEST_OK',len(files))
