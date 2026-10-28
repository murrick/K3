#!/usr/bin/env python3
"""Measure the owning grouped controller budget in isolated temporary source copies.

No production source/JAR is edited. Baseline runs against the actual JAR; only the
explicit diagnostic variant overlays instrumented Core classes. Counts are work
callback attempts, not CPU instructions. No counter grants proof/write authority.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import subprocess
import tempfile
import time


def closing_brace(source, opening):
    depth, i = 0, opening
    while i < len(source):
        c = source[i]
        if source.startswith('//', i):
            end = source.find('\n', i)
            i = len(source) if end < 0 else end
            continue
        if source.startswith('/*', i):
            end = source.find('*/', i + 2)
            if end < 0:
                raise ValueError('Unclosed Java comment')
            i = end + 2
            continue
        if c in ('"', "'"):
            quote = c
            i += 1
            while i < len(source):
                if source[i] == '\\':
                    i += 2
                elif source[i] == quote:
                    i += 1
                    break
                else:
                    i += 1
            continue
        if c == '{':
            depth += 1
        elif c == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    raise ValueError('Unclosed Java method')


def wrap(source, name, label, target, depth=None):
    pattern = r'(?m)^    (?:private )?static [^\n{]+?\b' + re.escape(name) + r'\([^\n{}]*Runnable work\)\s*\{'
    matches = list(re.finditer(pattern, source))
    if len(matches) != 1:
        raise ValueError(f'Expected one work-bearing {name}, got {len(matches)}')
    opening = matches[0].end() - 1
    end = closing_brace(source, opening)
    expression = label if label.startswith('(') else json.dumps(label)
    if depth:
        expression += ' + "/" + ' + depth
    start = '\n        try (ContextUniversalWorkProfile.Scope profileScope = ContextUniversalWorkProfile.enter(' + expression + ', ' + target + ', work)) {\n            try {\n'
    return source[:opening+1] + start + source[opening+1:end] + '\n            } catch (RuntimeException | Error profileFailure) { if (profileScope != null) profileScope.fail(); throw profileFailure; }\n        }\n    ' + source[end:]


HOOKS = {
    'ContextUniversalGroupedRounds': [('checkCertificates', 'Global.checkCertificates', 'checks', None)],
    'ContextUniversalGroupedLocalRounds': [('check', 'Local.check', 'certificate', None)],
    'ContextUniversalFrozenConstraints': [
        ('check', 'Frozen.check', 'result', 'maximumDepth'),
        ('checkSaturation', 'Frozen.saturation', 'result', 'maximumDepth'),
        ('verify', '(checked.contains(p) ? "Frozen.verify.alreadyInLocalSet" : "Frozen.verify.full")', 'p', 'depth'),
        ('frame', 'Frozen.frame', 'input', 'depth'),
        ('steps', 'Frozen.steps', 'input', None),
        ('groupedFrame', 'Frozen.groupedFrame', 'base', None)],
    'ContextUniversalSignedUnitExchange': [('check', 'Unit.check', 'exchange', 'maximumDepth'), ('frame', 'Unit.frame', 'checks', None)],
    'ContextUniversalCompoundExchange': [('check', 'Compound.check', 'exchange', 'maximumDepth')],
    'ContextUniversalGroupedCandidates': [('checked', 'Candidates.checked', 'base', 'maximumDepth'), ('check', 'Candidates.check', 'receipt', 'maximumDepth')],
    'ContextUniversalCompoundOccurrences': [('frame', 'Occurrences.frame.receiver', 'receiver', None), ('check', 'Occurrences.check', 'receipt', None)],
    'ContextUniversalGroupedCompletion': [('check', 'Completion.check', 'receipt', 'maximumDepth')],
    'ContextProductionFamilyAssembly': [('identity', '(localX ? "Native.identity.X" : "Native.identity.foreign")', 'snapshot', None)],
}


def controller_hooks(source):
    needle = 'Runnable work=ContextGroundProductionRounds.budget(maximumWork);'
    if source.count(needle) != 1:
        raise ValueError('Controller budget anchor changed')
    source = source.replace(needle, needle + 'work=ContextUniversalWorkProfile.root(work);')
    needle = 'for(int number=1;number<=maximumRounds;++number) {'
    if source.count(needle) != 1:
        raise ValueError('Controller round anchor changed')
    source = source.replace(needle, needle + '\n                ContextUniversalWorkProfile.round(number,input,pools,symbolic,compounds);')
    source, phases = re.subn(r'phase="([^"\n]+)";', lambda m: m.group(0) + 'ContextUniversalWorkProfile.phase(' + json.dumps(m.group(1)) + ');', source)
    if phases != 12:
        raise ValueError(f'Controller phase anchors changed: {phases}')
    needle = 'phase="certificate-binding";ContextUniversalWorkProfile.phase("certificate-binding");'
    if source.count(needle) != 1:
        raise ValueError('Certificate boundary anchor changed')
    return source.replace(needle, needle + 'ContextUniversalWorkProfile.certificates(checks,certificates);')


def run_command(command, root, log, timeout=180):
    started = time.monotonic()
    try:
        p = subprocess.run(command, cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout)
    except subprocess.TimeoutExpired as e:
        output = e.stdout or b''
        if isinstance(output, bytes):
            output = output.decode('utf-8', errors='replace')
        log.write_text(output + '\nPROFILE_TIMEOUT\n')
        raise
    log.write_text(p.stdout)
    if p.returncode:
        raise RuntimeError(f'{command[0]} failed ({p.returncode}): {log}')
    return p.stdout, round(time.monotonic()-started, 3)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, help='New output directory; defaults to a unique target run')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    jar = root / 'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar'
    parent = root / 'target/context-work-profile'
    parent.mkdir(parents=True, exist_ok=True)
    run = args.output.resolve() if args.output else Path(tempfile.mkdtemp(prefix='run-', dir=parent))
    if args.output:
        try:
            relative = run.relative_to(root)
        except ValueError:
            relative = None
        if relative is not None and (not relative.parts or relative.parts[0] != 'target'):
            raise ValueError('Diagnostic output inside the repository must be under target/')
        run.mkdir(parents=True, exist_ok=False)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    sources = {name: root/'kanger/src/org/kanger'/(name+'.java') for name in HOOKS}
    hashes = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources.values()}
    jar_sha = hashlib.sha256(jar.read_bytes()).hexdigest()
    qout, variant = run/'qualification', run/'instrumented'
    qout.mkdir(); variant.mkdir()
    runner = root/'kanger-qualification/src/org/kanger/ContextUniversalWorkProfileRunner.java'
    run_command(['javac','-source','8','-target','8','-encoding','UTF-8','-cp',str(jar),'-sourcepath',str(root/'kanger-qualification/src'),'-d',str(qout),str(runner)],root,run/'qualification-compile.log')
    baseline_cp = os.pathsep.join((str(qout),str(jar)))
    self_test, _ = run_command(['java','-XX:-UsePerfData','-cp',baseline_cp,'org.kanger.ContextUniversalWorkProfileRunner','self-test'],root,run/'counter-self-test.log')
    assert 'CONTEXT_UNIVERSAL_WORK_PROFILE_SELF_PASS checks=2' in self_test
    # Guard lexical wrapping across literal/comment braces, without changing the method body.
    toy = '    static void test(Runnable work) { String s="}"; /* } */ char c=\'{\'; work.run(); }\n'
    assert closing_brace(toy, toy.index('{')) == toy.rindex('}')
    assert 'work.run();' in wrap(toy,'test','toy','work')
    generated = []
    manifest = []
    for name, path in sources.items():
        text = path.read_text()
        for method,label,target,depth in HOOKS[name]:
            text = wrap(text,method,label,target,depth)
        if name == 'ContextUniversalGroupedRounds':
            text = controller_hooks(text)
        out = run/'sources/org/kanger'/(name+'.java')
        out.parent.mkdir(parents=True,exist_ok=True)
        out.write_text(text)
        generated.append(str(out))
        manifest.append(dict(source=str(path.relative_to(root)),hooks=[h[0] for h in HOOKS[name]],generated_sha256=hashlib.sha256(out.read_bytes()).hexdigest()))
    run_command(['javac','-source','8','-target','8','-encoding','UTF-8','-cp',baseline_cp,'-d',str(variant)]+generated,root,run/'instrumented-compile.log')
    failure_test, _ = run_command(['java','-XX:-UsePerfData','-cp',os.pathsep.join((str(variant),baseline_cp)),'org.kanger.ContextUniversalWorkProfileRunner','validation-counter'],root,run/'validation-counter-self-test.log')
    assert 'CONTEXT_UNIVERSAL_WORK_PROFILE_VALIDATION_PASS checks=2' in failure_test
    rows=[]
    for fixture in ('stable','budget'):
        outcomes={}
        for mode,cp in [('actual-jar',baseline_cp),('diagnostic-overlay',os.pathsep.join((str(variant),baseline_cp)))]:
            print(f'PROFILE_START fixture={fixture} mode={mode}',flush=True)
            output, seconds=run_command(['java','-XX:-UsePerfData','-cp',cp,'org.kanger.ContextUniversalWorkProfileRunner',fixture],root,run/(fixture+'-'+mode+'.log'))
            payload=json.loads(next(line[len('WORK_PROFILE_RESULT '):] for line in output.splitlines() if line.startswith('WORK_PROFILE_RESULT ')))
            payload['seconds']=seconds
            outcomes[mode]=payload
            print(f'PROFILE_DONE fixture={fixture} mode={mode} status={payload["semantic"]["status"]} rounds={payload["semantic"]["completed"]} ticks={payload["profile"]["attemptedTicks"]} seconds={seconds}',flush=True)
        a,b=outcomes['actual-jar'],outcomes['diagnostic-overlay']
        assert a['originals']==b['originals'] and a['workLimit']==b['workLimit']==64000000 and a['semantic']==b['semantic']
        assert not a['profile']['instrumented'] and a['profile']['attemptedTicks']==0 and b['profile']['instrumented']
        profile=b['profile'];assert sum(profile['phaseTicks'].values())==profile['attemptedTicks']==sum(x['leafTicks'] for x in profile['scopes'])
        if fixture=='budget':
            assert profile['attemptedTicks']==64000001 and profile['acceptedTicks']==64000000 and profile['failed']
            assert profile['rounds'][-1]['round']==3 and 'Global.checkCertificates' in profile['failureStack']
        else:
            assert not profile['failed'] and profile['attemptedTicks']==profile['acceptedTicks']
        rows.append(dict(fixture=fixture,semantic_equal=True,outcomes=outcomes))
    assert hashes=={str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources.values()}
    assert jar_sha==hashlib.sha256(jar.read_bytes()).hexdigest()
    assert head==subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip()
    report=dict(passed=True,head=head,jar_sha256=jar_sha,core_sources_unchanged=hashes,hook_manifest=manifest,variants=rows,scope='Exact owning controller callback counts only; diagnostic overlay, not production qualification/authority. Inclusive scopes overlap. Identity visits do not license caching.')
    (run/'report.json').write_text(json.dumps(report,indent=2)+'\n')
    print('PROFILE_PASS comparisons=2 counter_self_checks=4',flush=True)
    print('Report:',run/'report.json',flush=True)


if __name__=='__main__':
    main()
