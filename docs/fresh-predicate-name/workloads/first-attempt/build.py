from pathlib import Path
import hashlib
import json
import subprocess

root = Path(__file__).parent
base = '1c943d02d39bd33ebd91fabb7c4190112ab49459'
changed = subprocess.check_output(['git', 'diff', '--name-only', base, '--', 'kanger/src', 'kanger-udf/src', 'kanger-server/src'], text=True).splitlines()
assert changed == ['kanger/src/org/kanger/units/Predicate.java']
subprocess.run(['bash', 'docs/fresh-predicate-name/build-local.sh'], check=True)
subprocess.run(['python', 'docs/fresh-predicate-name/build-reference.py'], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', '../build/fresh-name-reference',
                '-sourcepath', 'kanger-qualification/src', '-d', '../build/fresh-name-workload-classes',
                str(root / 'FreshNameWorkloadRunner.java')], check=True)
recorded = json.loads(Path('docs/fresh-predicate-name/class-sha256.json').read_text())
result = {}
for mode, folder in [('experiment', '../build/fresh-name-classes'), ('clean', '../build/fresh-name-reference'),
                     ('common', '../build/fresh-name-workload-classes')]:
    directory = Path(folder)
    hashes = {str(p.relative_to(directory)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(directory.rglob('*.class'))}
    if mode != 'common': assert hashes == recorded[mode], mode
    else: assert all(p.startswith('org/kanger/test/') for p in hashes), hashes.keys()
    result[mode] = hashes
(root / 'class-sha256.json').write_text(json.dumps(result, indent=2) + '\n')
print('Original production classes unchanged; common driver contains qualification classes only')
