from pathlib import Path
import hashlib
import json
import subprocess

base = '1c943d02d39bd33ebd91fabb7c4190112ab49459'
root = Path(__file__).parent
temporary = Path('../build/single-empty-probe-source')
temporary.mkdir(parents=True, exist_ok=True)
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
assert subprocess.check_output(['git', 'diff', '--name-only', base, '--', 'kanger/src', 'kanger-udf/src'], text=True).splitlines() == ['kanger/src/org/kanger/units/TVariable.java']
modified = {}

def replace(source, needle, replacement):
    assert source.count(needle) == 1, needle
    return source.replace(needle, replacement)

path = 'kanger/src/org/kanger/factory/TValueFactory.java'
source = Path(path).read_text()
source = replace(source, '    public TValue get(TVariable tv) {\n',
    '    public TValue get(TVariable tv) {\n        int previous = org.kanger.CurrentEmptyCounters.enterGet();\n        try {\n')
source = replace(source, '        return current.get(tv);\n    }',
    '        TValue result = current.get(tv);\n        org.kanger.CurrentEmptyCounters.mapGet(result == null);\n        return result;\n        } finally { org.kanger.CurrentEmptyCounters.leaveGet(previous); }\n    }')
source = replace(source, '        return !current.containsKey(tv);',
    '        boolean present = current.containsKey(tv);\n        org.kanger.CurrentEmptyCounters.contains(this, tv, present);\n        return !present;')
modified[path] = source

path = 'kanger/src/org/kanger/units/TVariable.java'
source = Path(path).read_text()
start = source.index('    public boolean isEmpty() {')
end = source.index('    /** Default-OFF experiment', start)
original_method = source[start:end]
assert original_method.count('        Mind active = activeMind();') == 1
body = original_method.replace('    public boolean isEmpty() {\n        Mind active = activeMind();',
                              '    private boolean diagnosticEmptyBody(Mind active) {')
body = replace(body, '                return values.getCurrent().get(this) == null;',
               '                boolean empty = values.getCurrent().get(this) == null;\n                org.kanger.CurrentEmptyCounters.mapGet(empty);\n                return empty;')
wrapper = """    public boolean isEmpty() {
        Mind active = activeMind();
        boolean previousNative = org.kanger.CurrentEmptyCounters.nativeContext();
        int previous = org.kanger.CurrentEmptyCounters.enterVariable(this, active);
        try {
            boolean result = diagnosticEmptyBody(active);
            org.kanger.CurrentEmptyCounters.variableResult(result);
            return result;
        } finally { org.kanger.CurrentEmptyCounters.leaveVariable(previous, previousNative); }
    }

"""
modified[path] = source[:start] + wrapper + body + source[end:]
patches = []
for path, source in modified.items():
    destination = temporary / Path(path).name
    destination.write_text(source)
    sources[sources.index(path)] = str(destination)
    diff = subprocess.run(['diff', '-U0', path, str(destination)], capture_output=True, text=True)
    assert diff.returncode == 1
    patches.append(diff.stdout)
(root / 'instrumentation.patch').write_text(''.join(patches))
sources.append(str(root / 'CurrentEmptyCounters.java'))
(root / 'sources.txt').write_text('\n'.join(sources) + '\n')
runner = Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text()
runner = replace(runner, 'public final class SonProfileRunner', 'public final class CurrentEmptyProfileRunner')
runner = replace(runner, '            try { mind.optimizeHypothesis(); }',
    '            CurrentEmptyCounters.begin();\n            try { mind.optimizeHypothesis(); }')
runner = replace(runner, '            long optimizeNs = System.nanoTime() - start;',
    '            CurrentEmptyCounters.finish(i);\n            long optimizeNs = System.nanoTime() - start;')
(root / 'CurrentEmptyProfileRunner.java').write_text(runner)
classes = '../build/single-empty-probe-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar', '-d', classes, '@' + str(root / 'sources.txt')], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes, '-d', classes, str(root / 'CurrentEmptyProfileRunner.java')], check=True)
hashes = {str(p.relative_to(classes)): hashlib.sha256(p.read_bytes()).hexdigest()
          for p in sorted(Path(classes).rglob('*.class'))}
(root / 'class-sha256.json').write_text(json.dumps(hashes, indent=2) + '\n')
print('Diagnostic probe-census build ready; experiment production unchanged')
