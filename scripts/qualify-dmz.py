#!/usr/bin/env python3
"""Compile and run the explicit DMZ regression gate with javac or an ECJ jar."""
import argparse
import concurrent.futures
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('kanger', 'kanger-bootstrap', 'kanger-command', 'kanger-udf',
           'kanger-data-dumb2', 'kanger-console')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ecj', type=Path, help='Use this ECJ jar instead of javac')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/dmz-qualification')
    parser.add_argument('--jobs', type=int, default=2)
    parser.add_argument('--timeout', type=int, default=900, help='Seconds per driver')
    args = parser.parse_args()
    if not 1 <= args.jobs <= 8 or args.timeout < 1:
        parser.error('jobs must be 1..8 and timeout positive')
    output = args.output.resolve()
    # Each invocation owns fresh compilation output; old classes cannot satisfy a gate.
    output.mkdir(parents=True, exist_ok=True)
    import tempfile
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes = run / 'classes'
    classes.mkdir()
    manifest = ROOT / 'kanger-qualification/test-data/dmz-suite.txt'
    suite = []
    for line in manifest.read_text(encoding='utf-8').splitlines():
        if not line.strip() or line.lstrip().startswith('#'):
            continue
        name, marker = (part.strip() for part in line.split('|', 1))
        if not re.fullmatch(r'org\.kanger\.[A-Za-z0-9_.]+', name) or not marker:
            raise ValueError('Invalid qualification manifest row: ' + line)
        suite.append((name, marker))
    if not suite or len({name for name, _ in suite}) != len(suite):
        raise ValueError('Nonempty unique qualification manifest required')
    production = sorted(p for module in MODULES for p in (ROOT / module / 'src').rglob('*.java'))
    drivers = [ROOT / 'kanger-qualification/src' / (name.replace('.', '/') + '.java') for name, _ in suite]
    sources = run / 'sources.list'
    sources.write_text(''.join('"' + str(p).replace('\\', '/') + '"\n' for p in production + drivers), encoding='utf-8')
    libs = [ROOT / 'lib/jline-3.13.0.jar', ROOT / 'lib/jansi-1.17.jar']
    for dependency in libs + drivers + ([args.ecj.resolve()] if args.ecj else []):
        if not dependency.is_file():
            raise FileNotFoundError(dependency)
    compile_cp = os.pathsep.join(str(p) for p in libs)
    command = (['java', '-jar', str(args.ecj.resolve()), '-8', '-nowarn'] if args.ecj
               else ['javac', '-source', '8', '-target', '8'])
    command += ['-encoding', 'UTF-8', '-cp', compile_cp, '-sourcepath', str(ROOT / 'kanger-qualification/src'),
                '-d', str(classes), '@' + str(sources)]
    print('Compiling {} production sources and {} gate drivers; output {}'.format(len(production), len(suite), run), flush=True)
    compiled = subprocess.run(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'compile.log').write_text(compiled.stdout, encoding='utf-8')
    if compiled.returncode:
        print(compiled.stdout, file=sys.stderr)
        return compiled.returncode
    runtime_cp = os.pathsep.join([str(classes), str(ROOT / 'kanger/resources')] + [str(p) for p in libs])

    def execute(one):
        name, marker = one
        started = time.monotonic()
        home = run / 'homes' / name
        home.mkdir(parents=True)
        timed_out = False
        try:
            result = subprocess.run(['java', '-XX:-UsePerfData', '-Duser.home=' + str(home), '-cp', runtime_cp, name],
                                    cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                    text=True, timeout=args.timeout)
            code, log = result.returncode, result.stdout
        except subprocess.TimeoutExpired as failure:
            code, timed_out = 124, True
            log = failure.stdout or b''
            if isinstance(log, bytes):
                log = log.decode('utf-8', errors='replace')
            log += '\nQUALIFICATION_TIMEOUT\n'
        (run / (name + '.log')).write_text(log, encoding='utf-8')
        passed = code == 0 and marker in log and not timed_out
        # Preserve counts actually emitted; some gates do not publish a count.
        counts = [int(n) for n in re.findall(r'(?m)^.*PASS[^\n]*checks=(\d+)\s*$', log)]
        counts += [int(n) for n in re.findall(r'(?m)^KANGER command parser conformance PASS:\s*(\d+) assertions\s*$', log)]
        return dict(driver=name, passed=passed, exit_code=code, timed_out=timed_out,
                    seconds=round(time.monotonic() - started, 3), checks=sum(counts) if passed else 0,
                    count_reported=bool(counts), required_marker=marker)

    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as pool:
        tasks = {pool.submit(execute, one): one for one in suite}
        for task in concurrent.futures.as_completed(tasks):
            result = task.result()
            results.append(result)
            print('{} {} checks={} {:.1f}s'.format('PASS' if result['passed'] else 'FAIL',
                                                 result['driver'], result['checks'], result['seconds']), flush=True)
    results.sort(key=lambda row: row['driver'])
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    dirty = subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()
    report = dict(head=head, working_tree_dirty=bool(dirty), production_sources=len(production),
                  drivers=len(suite), passed=sum(r['passed'] for r in results),
                  checks=sum(r['checks'] for r in results), results=results)
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('DMZ_GATE {}: {}/{} drivers, {} counted checks; report {}'.format(
        'PASS' if report['passed'] == len(suite) else 'FAIL', report['passed'], len(suite), report['checks'], run / 'report.json'), flush=True)
    return 0 if report['passed'] == len(suite) else 1


if __name__ == '__main__':
    sys.exit(main())
