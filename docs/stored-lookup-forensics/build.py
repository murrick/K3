from pathlib import Path
import difflib
import hashlib
import json
import subprocess

base = '1c943d02d39bd33ebd91fabb7c4190112ab49459'
root = Path(__file__).parent
temporary = Path('../build/stored-lookup-source')
temporary.mkdir(parents=True, exist_ok=True)
sources = Path('docs/resident-base-comparison-evidence/sources.txt').read_text().splitlines()
assert not subprocess.check_output(['git', 'diff', base, '--', 'kanger/src', 'kanger-udf/src', 'kanger-qualification/src', '.github'], text=True)
modified = {}

def replace(source, needle, replacement):
    assert source.count(needle) == 1, needle
    return source.replace(needle, replacement)

path = 'kanger/src/org/kanger/Linker.java'
source = Path(path).read_text()
sites = []
lines = []
for n, line in enumerate(source.splitlines(True), 1):
    if 'd.isStored(mind)' in line:
        sites.append({'tag':len(sites)+1,'line':n,'path':path,'original':line.strip()})
        line = line.replace('d.isStored(mind)', 'org.kanger.StoredLookupProfile.atSite(d, mind, %d)' % len(sites))
    lines.append(line)
assert len(sites) == 6
source = ''.join(lines)
start = source.index('    private boolean linkDatabase(')
end = source.index('    private void logCauses(', start)
body = source[start:end]
body = replace(body, '        boolean result = false;', '        org.kanger.StoredLookupProfile.LinkFrame frame = org.kanger.StoredLookupProfile.beginLink();\n        try {\n        boolean result = false;')
body = replace(body, '        return result;\n    }', '        return result;\n        } finally { org.kanger.StoredLookupProfile.endLink(frame); }\n    }')
modified[path] = source[:start] + body + source[end:]
(root / 'callsites.json').write_text(json.dumps(sites, indent=2) + '\n')

path = 'kanger/src/org/kanger/units/Domain.java'
source = Path(path).read_text()
for signature, overload in [('public boolean isStored(Mind mind)', False), ('public boolean isStored(ArgumentsList args, Mind mind)', True)]:
    start = source.index('    ' + signature + ' throws Exception {')
    end = source.index('\n    public ', start + 1)
    body = source[start:end]
    body = replace(body, ' throws Exception {\n', ' throws Exception {\n        org.kanger.StoredLookupProfile.StoredFrame frame = org.kanger.StoredLookupProfile.beginStored(this, mind, %s);\n        try {\n' % str(overload).lower())
    body = replace(body, '        return r != null && !r.isDeleted(mind);\n    }', '        return org.kanger.StoredLookupProfile.storedResult(r != null && !r.isDeleted(mind));\n        } finally { org.kanger.StoredLookupProfile.endStored(frame); }\n    }')
    source = source[:start] + body + source[end:]
modified[path] = source

path = 'kanger/src/org/kanger/factory/RuleFactory.java'
source = Path(path).read_text()
start = source.index('    public IRule find(Solve domain) throws Exception {')
end = source.index('    public IRule find(IRule rule)', start)
body = source[start:end]
body = replace(body, ' throws Exception {\n', ' throws Exception {\n        org.kanger.StoredLookupProfile.FindFrame frame = org.kanger.StoredLookupProfile.beginFind(this);\n        try {\n')
body = replace(body, '            IRule one = get(id);', '            org.kanger.StoredLookupProfile.visit();\n            IRule one = get(id);\n            org.kanger.StoredLookupProfile.candidate(one);')
body = replace(body, 'if (((Rule) one).equalsTo(domain))', 'if (org.kanger.StoredLookupProfile.comparison(((Rule) one).equalsTo(domain)))')
body = replace(body, '                return one;', '                org.kanger.StoredLookupProfile.findResult(true);\n                return one;')
body = replace(body, '        return null;\n    }', '        org.kanger.StoredLookupProfile.findResult(false);\n        return null;\n        } finally { org.kanger.StoredLookupProfile.endFind(frame); }\n    }')
modified[path] = source[:start] + body + source[end:]
patches = []
for path, source in modified.items():
    destination = temporary / Path(path).name
    destination.write_text(source)
    sources[sources.index(path)] = str(destination)
    patches += list(difflib.unified_diff(Path(path).read_text().splitlines(True), source.splitlines(True), fromfile='a/'+path, tofile='b/'+path, n=0))
(root / 'instrumentation.patch').write_text(''.join(patches))
sources.append(str(root / 'StoredLookupProfile.java'))
(root / 'sources.txt').write_text('\n'.join(sources) + '\n')
runner = Path('kanger-qualification/src/org/kanger/SonProfileRunner.java').read_text()
runner = replace(runner, 'public final class SonProfileRunner', 'public final class StoredLookupProfileRunner')
runner = replace(runner, '            try { mind.optimizeHypothesis(); }', '            StoredLookupProfile.begin();\n            try { mind.optimizeHypothesis(); }')
runner = replace(runner, '            long optimizeNs = System.nanoTime() - start;', '            StoredLookupProfile.finish(i);\n            long optimizeNs = System.nanoTime() - start;')
(root / 'StoredLookupProfileRunner.java').write_text(runner)
classes = '../build/stored-lookup-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar', '-d', classes, '@'+str(root/'sources.txt')], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes, '-d', classes, str(root/'StoredLookupProfileRunner.java')], check=True)
hashes = {str(p.relative_to(classes)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(Path(classes).rglob('*.class'))}
(root/'class-sha256.json').write_text(json.dumps(hashes,indent=2)+'\n')
print('STORED_LOOKUP_DIAGNOSTIC_BUILD_READY')
