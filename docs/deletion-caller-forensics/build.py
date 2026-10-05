from pathlib import Path
import subprocess

root = Path(__file__).parent
tmp = Path('../build/deletion-caller-source')
tmp.mkdir(parents=True, exist_ok=True)
path = 'kanger/src/org/kanger/Mind.java'
original = subprocess.check_output(['git', 'show', '1c943d02d39bd33ebd91fabb7c4190112ab49459:' + path], text=True)
start = original.index('    public boolean isUnitDeleted(IUnit unit) {')
end = original.index('    public void setUnitDeleted(', start)
method = original[start:end]
method = method.replace('        long unitId = unit.getId();', '        long unitId = unit.getId();\n        int diagnosticDepth = 0;\n        boolean diagnosticExposed = false;')
method = method.replace('            Mind current = (Mind) level;', '            Mind current = (Mind) level;\n            ++diagnosticDepth;')
method = method.replace('                Set<Long> restoredIds = current.restored.get(unitType);', '                Set<Long> restoredIds = current.restored.get(unitType);\n                diagnosticExposed |= DeletionCallerCounters.restoredProbe(unitType, restoredIds != null, current.diagnosticMapsExposed);')
method = method.replace('                    return false;', '                    DeletionCallerCounters.complete(unitType, diagnosticDepth, 0, !diagnosticExposed);\n                    return false;')
method = method.replace('                Set<Long> deletedIds = current.deleted.get(unitType);', '                Set<Long> deletedIds = current.deleted.get(unitType);\n                DeletionCallerCounters.deletedProbe(unitType, deletedIds != null);')
method = method.replace('                    return true;', '                    DeletionCallerCounters.complete(unitType, diagnosticDepth, 1, !diagnosticExposed);\n                    return true;')
# Replace only the final miss return, not the restoration return above.
needle = '        }\n        return false;'
assert method.count(needle) == 1
method = method.replace(needle, '        }\n        DeletionCallerCounters.complete(unitType, diagnosticDepth, 2, !diagnosticExposed);\n        return false;')
modified = original[:start] + method + original[end:]
modified = modified.replace('private final Object locker = new Object();', 'private final Object locker = new Object();\n    private boolean diagnosticMapsExposed;')
for getter in ['getDeleted', 'getRestored']:
 needle = 'public Map<UnitType, Set<Long>> ' + getter + '() {'
 assert modified.count(needle) == 1
 modified = modified.replace(needle, needle + '\n        diagnosticMapsExposed = DeletionCallerCounters.expose(diagnosticMapsExposed);')
(tmp / 'Mind.java').write_text(modified)
modified_sources = {path: tmp / 'Mind.java'}
patches = []
for p, replacements in {
 'kanger/src/org/kanger/primitives/ArgumentsList.java': [('&& !a.isDeleted(mind)\n                    && !a.isDeleted(mind)', '&& !org.kanger.DeletionCallerCounters.argumentDeleted(a, mind, 1)\n                    && !org.kanger.DeletionCallerCounters.argumentDeleted(a, mind, 2)'), ('for (TVariable t : getTVariables(mind))', 'for (TVariable t : org.kanger.DeletionCallerCounters.collect(this, mind, 2))')],
 'kanger/src/org/kanger/units/Domain.java': [('\n        for (TVariable t : arguments.getTVariables(mind))', '\n        for (TVariable t : org.kanger.DeletionCallerCounters.collect(arguments, mind, 1))')],
 'kanger/src/org/kanger/primitives/Argument.java': [('public boolean isDeleted(IMind mind) {', 'public boolean isDeleted(IMind mind) {\n        org.kanger.DeletionCallerCounters.argumentBoundary(this, o, mind);')]
}.items():
 source = subprocess.check_output(['git', 'show', '1c943d02d39bd33ebd91fabb7c4190112ab49459:' + p], text=True)
 for needle, replacement in replacements:
  assert source.count(needle) == 1, (p, needle)
  source = source.replace(needle, replacement)
 destination = tmp / Path(p).name
 destination.write_text(source)
 modified_sources[p] = destination
for p, destination in modified_sources.items():
 diff = subprocess.run(['diff', '-u', p, str(destination)], capture_output=True, text=True)
 assert diff.returncode == 1
 patches.append(diff.stdout)
(root / 'instrumentation.patch').write_text(''.join(patches))
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
sources = [str(modified_sources[p]) if p in modified_sources else p for p in sources]
sources.append(str(root / 'DeletionCallerCounters.java'))
(root / 'sources.txt').write_text('\n'.join(sources) + '\n')
runner = Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text()
runner = runner.replace('public static void main(String[] args) throws Exception {', 'public static void main(String[] args) throws Exception {\n        DeletionCallerCounters.startExposureTracking();')
runner = runner.replace('public final class SonProfileRunner', 'public final class DeletionCallerProfileRunner')
runner = runner.replace('try { mind.optimizeHypothesis(); }', 'DeletionCallerCounters.begin();\n            try { mind.optimizeHypothesis(); }')
runner = runner.replace('finally { if (sampler != null) sampler.running = false; }\n            long optimizeNs', 'finally { DeletionCallerCounters.finish(i); if (sampler != null) sampler.running = false; }\n            long optimizeNs')
(root / 'DeletionCallerProfileRunner.java').write_text(runner)
classes = '../build/deletion-caller-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar', '-d', classes, '@' + str(root / 'sources.txt')], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes, '-d', classes, str(root / 'DeletionCallerProfileRunner.java'), str(root / 'DeletionVisibilityWitness.java'), str(root / 'RepeatedDeletionWitness.java'), str(root / 'ExposedSetDeletionWitness.java')], check=True)
