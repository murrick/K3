#!/usr/bin/env python3
"""Two-process exported-pin namespace model; never a physical power-cut acceptance claim."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.kanger.storage.dumb2.'


def events(trace):
    pending = {}
    for ordinal, line in enumerate(trace.read_text(errors='replace').splitlines(), 1):
        match = re.match(r'\s*(\d+)\s+(.*)', line)
        if not match:
            continue
        pid, body = match.groups()
        if '<unfinished ...>' in body:
            pending[pid] = (ordinal, body.split('<unfinished ...>')[0])
            continue
        resumed = re.match(r'<\.\.\. \w+ resumed>(.*)', body)
        if resumed:
            if pid not in pending:
                continue
            ordinal, prefix = pending.pop(pid)
            body = prefix + resumed[1]
        yield ordinal, body


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT / 'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar')
    args = parser.parse_args()
    jar = args.jar.resolve()
    output = ROOT / 'target/context-exported-pin-durability'
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes, overlay = run / 'classes', run / 'producer-overlay'
    classes.mkdir(); overlay.mkdir()
    env = os.environ.copy()
    for name in ('KANGER_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        env.pop(name, None)
    env['KANGER_HOME'] = 'KANGER'
    originals = [ROOT / 'kanger-qualification/src' / ((PACKAGE + n).replace('.', '/') + '.java')
                 for n in ('ExportedPinPause', 'ExportedPinDurabilityRunner', 'ExportedPinCrashProbe')]
    compile_args = ['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8']
    compiled = subprocess.run(compile_args + ['-cp', str(jar), '-d', str(classes)] + list(map(str, originals)),
                              cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'compile.log').write_text(compiled.stdout)
    if compiled.returncode:
        print(compiled.stdout); return compiled.returncode
    original = ROOT / 'kanger-data-dumb2/src/org/kanger/storage/dumb2/RevisionStore.java'
    source = original.read_text()
    boundary = '            // A visible next marker is not yet a durable acknowledgement.'
    if source.count(boundary) != 1:
        raise ValueError('Exact producer pause boundary changed')
    injected = run / 'RevisionStore.java'
    injected.write_text(source.replace(boundary, '            ExportedPinPause.afterMove(path);\n' + boundary))
    cp = os.pathsep.join((str(classes), str(jar)))
    compiled = subprocess.run(compile_args + ['-cp', cp, '-d', str(overlay), str(injected)], cwd=ROOT,
                              env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'overlay-compile.log').write_text(compiled.stdout)
    if compiled.returncode:
        print(compiled.stdout); return compiled.returncode
    producer_cp = os.pathsep.join((str(overlay), cp))
    sys.dont_write_bytecode = True
    spec = importlib.util.spec_from_file_location('durability_audit', ROOT / 'scripts/audit-context-durability.py')
    audit = importlib.util.module_from_spec(spec); spec.loader.exec_module(audit)
    fixture, trace = run / 'fixture', run / 'syscalls.log'
    result = subprocess.run(['strace', '-f', '-qq', '-yy', '-s', '4096', '-e', 'trace=' + audit.CALLS,
                             '-o', str(trace), 'java', '-XX:-UsePerfData', '-cp', cp,
                             PACKAGE + 'ExportedPinDurabilityRunner', 'coordinate', str(fixture), producer_cp],
                            cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=180)
    (run / 'fixture.log').write_text(result.stdout)
    if result.returncode or 'EXPORTED_PIN_FIXTURE_PASS' not in result.stdout:
        print(result.stdout); return result.returncode or 2
    parsed = audit.parse_trace(trace, fixture)
    if parsed['unfinished_sync_calls']:
        raise ValueError('Unfinished barrier acquisition')
    marker = str(fixture / 'producer-db/Producer.revision')
    parent = str(Path(marker).parent)
    renames, acks = [], []
    for line, body in events(trace):
        if re.match(r'rename(?:at2|at)?\(', body) and body.endswith('= 0'):
            paths = re.findall(r'"([^"]+)"', body)
            if paths and paths[-1] == marker:
                renames.append(line)
        if body.startswith('write(') and 'EXPORTED_PIN_CONSUMER_ACK revision=2 target=2' in body:
            acks.append(line)
    if len(acks) != 1 or len(renames) != 2 or renames[-1] >= acks[0]:
        raise ValueError('Two actual producer publications and one exact consumer ACK required')
    barriers = [e for e in parsed['file_syncs'] if e['directory'] and e['path'] == parent
                and e['result'] == '0' and renames[-1] < e['line'] < acks[0]]
    if any(renames[-1] < e['line'] < acks[0] for e in parsed['global_flush_calls']):
        raise ValueError('Global flush requires a separate model interpretation')
    rollback = not barriers
    probe = subprocess.run(['java', '-XX:-UsePerfData', '-cp', cp, PACKAGE + 'ExportedPinCrashProbe',
                            str(fixture), str(rollback).lower()], cwd=ROOT, env=env,
                           stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=60)
    (run / 'probe.log').write_text(probe.stdout)
    observation = re.search(r'EXPORTED_PIN_MODEL consumer=(\d+) pinned=(\d+) producer=(\d+) readable=(true|false)', probe.stdout)
    if probe.returncode or not observation:
        print(probe.stdout); return probe.returncode or 2
    consumer, pinned, recovered = map(int, observation.group(1, 2, 3))
    readable = observation[4] == 'true'
    with zipfile.ZipFile(jar) as packaged:
        names = [n for n in packaged.namelist() if n.startswith('org/kanger/storage/dumb2/') and n.endswith('.class')]
        matches = all((ROOT / 'kanger-data-dumb2/target/classes' / n).read_bytes() == packaged.read(n) for n in names)
    if not matches:
        raise ValueError('Packaged consumer provider classes do not match built bytes')
    passed = not rollback and recovered >= pinned and readable
    artifacts = [jar, original, injected, trace, run / 'fixture.log', run / 'probe.log', Path(__file__), *originals]
    report = dict(scope='Two JVMs; one producer-name persistence projection; no physical power loss',
                  head=audit.git('rev-parse', 'HEAD'), source_dirty=bool(audit.git('status', '--porcelain')),
                  producer_overlay='Only pause after successful real marker replacement, before final producer barrier',
                  consumer_unmodified_packaged_provider=True, matching_provider_classes=len(names),
                  producer_marker_replacement_line=renames[-1], consumer_acknowledgement_line=acks[0],
                  successful_producer_parent_barriers_before_consumer_ack=len(barriers),
                  rollback_admissible=rollback, consumer_revision=consumer, pinned_revision=pinned,
                  producer_recovered_revision=recovered, exact_pin_readable=readable,
                  exported_pin_namespace_gate_passed=passed, full_power_loss_acceptance=False,
                  projection='Lose only an unforced producer CURRENT replacement; retain all other files and acknowledged consumer',
                  artifacts_sha256={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in artifacts})
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Report:', run / 'report.json')
    print(('EXPORTED_PIN_NAMESPACE_PASS' if passed else 'EXPORTED_PIN_NAMESPACE_RED')
          + ' consumer={} pinned={} producer={} readable={}'.format(consumer, pinned, recovered, readable))
    print('Explicit persistence model; not an observed hardware power cut.')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
