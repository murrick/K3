from pathlib import Path
import subprocess

root = Path(__file__).parent
tmp = Path('../build/deletion-chain-source')
tmp.mkdir(parents=True, exist_ok=True)
path = 'kanger/src/org/kanger/Mind.java'
original = subprocess.check_output(['git', 'show', '1c943d02d39bd33ebd91fabb7c4190112ab49459:' + path], text=True)
start = original.index('    public boolean isUnitDeleted(IUnit unit) {')
end = original.index('    public void setUnitDeleted(', start)
method = original[start:end]
method = method.replace('        long unitId = unit.getId();', '        long unitId = unit.getId();\n        int diagnosticDepth = 0;')
method = method.replace('            Mind current = (Mind) level;', '            Mind current = (Mind) level;\n            ++diagnosticDepth;')
method = method.replace('                Set<Long> restoredIds = current.restored.get(unitType);', '                Set<Long> restoredIds = current.restored.get(unitType);\n                DeletionChainCounters.restoredProbe(unitType, restoredIds != null);')
method = method.replace('                    return false;', '                    DeletionChainCounters.complete(unitType, diagnosticDepth, 0);\n                    return false;')
method = method.replace('                Set<Long> deletedIds = current.deleted.get(unitType);', '                Set<Long> deletedIds = current.deleted.get(unitType);\n                DeletionChainCounters.deletedProbe(unitType, deletedIds != null);')
method = method.replace('                    return true;', '                    DeletionChainCounters.complete(unitType, diagnosticDepth, 1);\n                    return true;')
# Replace only the final miss return, not the restoration return above.
needle = '        }\n        return false;'
assert method.count(needle) == 1
method = method.replace(needle, '        }\n        DeletionChainCounters.complete(unitType, diagnosticDepth, 2);\n        return false;')
modified = original[:start] + method + original[end:]
(tmp / 'Mind.java').write_text(modified)
diff = subprocess.run(['diff', '-u', path, str(tmp / 'Mind.java')], capture_output=True, text=True)
assert diff.returncode == 1
(root / 'instrumentation.patch').write_text(diff.stdout)
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
sources = [str(tmp / 'Mind.java') if p == path else p for p in sources]
sources.append(str(root / 'DeletionChainCounters.java'))
(root / 'sources.txt').write_text('\n'.join(sources) + '\n')
runner = Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text()
runner = runner.replace('public final class SonProfileRunner', 'public final class DeletionChainProfileRunner')
runner = runner.replace('try { mind.optimizeHypothesis(); }', 'DeletionChainCounters.begin();\n            try { mind.optimizeHypothesis(); }')
runner = runner.replace('finally { if (sampler != null) sampler.running = false; }\n            long optimizeNs', 'finally { DeletionChainCounters.finish(i); if (sampler != null) sampler.running = false; }\n            long optimizeNs')
(root / 'DeletionChainProfileRunner.java').write_text(runner)
classes = '../build/deletion-chain-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar', '-d', classes, '@' + str(root / 'sources.txt')], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes, '-d', classes, str(root / 'DeletionChainProfileRunner.java'), str(root / 'DeletionVisibilityWitness.java'), str(root / 'RepeatedDeletionWitness.java')], check=True)
