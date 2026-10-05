from pathlib import Path
import subprocess

root = Path(__file__).parent
tmp = Path('../build/hash-probe-source')
tmp.mkdir(parents=True, exist_ok=True)
path = 'kanger/src/org/kanger/storage/Escalera.java'
original = subprocess.check_output(['git', 'show', '1c943d02d39bd33ebd91fabb7c4190112ab49459:' + path], text=True)
needle = '        Set<Long> ids = owner.idsByHash.get(hash);'
assert original.count(needle) == 1
modified = original.replace(needle, needle + '\n        HashProbeCounters.observe(owner, hash, ids);')
(tmp / 'Escalera.java').write_text(modified)
diff = subprocess.run(['diff', '-u', path, str(tmp / 'Escalera.java')], capture_output=True, text=True)
assert diff.returncode == 1
(root / 'instrumentation.patch').write_text(diff.stdout)
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
sources = [str(tmp / 'Escalera.java') if p == path else p for p in sources]
sources.append(str(root / 'HashProbeCounters.java'))
(root / 'sources.txt').write_text('\n'.join(sources) + '\n')
runner = Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text()
runner = runner.replace('public final class SonProfileRunner', 'public final class HashProbeProfileRunner')
runner = runner.replace('try { mind.optimizeHypothesis(); }', 'org.kanger.storage.HashProbeCounters.begin();\n            try { mind.optimizeHypothesis(); }')
runner = runner.replace('finally { if (sampler != null) sampler.running = false; }\n            long optimizeNs', 'finally { org.kanger.storage.HashProbeCounters.finish(i); if (sampler != null) sampler.running = false; }\n            long optimizeNs')
(root / 'HashProbeProfileRunner.java').write_text(runner)
classes = '../build/hash-probe-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar', '-d', classes, '@' + str(root / 'sources.txt')], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes, '-d', classes, str(root / 'HashProbeProfileRunner.java')], check=True)
