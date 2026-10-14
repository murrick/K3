#!/usr/bin/env python3
"""Run the accepted production-rounds contract (expected RED before implementation)."""
from pathlib import Path
import json
import os
import subprocess
import tempfile


def main():
    root = Path(__file__).resolve().parents[1]
    output = root / 'target/context-production-rounds'
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes = run / 'classes'
    classes.mkdir()
    modules = ('kanger', 'kanger-bootstrap', 'kanger-command', 'kanger-udf',
               'kanger-data-dumb2', 'kanger-console')
    production = sorted(p for module in modules for p in (root / module / 'src').rglob('*.java'))
    name = 'org.kanger.storage.dumb2.ContextProductionRoundsContractRunner'
    driver = root / 'kanger-qualification/src' / (name.replace('.', '/') + '.java')
    listing = run / 'sources.list'
    listing.write_text(''.join('"' + str(p).replace('\\', '/') + '"\n'
                               for p in production + [driver]), encoding='utf-8')
    libs = [root / 'lib/jline-3.13.0.jar', root / 'lib/jansi-1.17.jar']
    compile_cp = os.pathsep.join(str(p) for p in libs)
    compile_result = subprocess.run(
        ['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
         '-cp', compile_cp, '-d', str(classes), '@' + str(listing)],
        cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'compile.log').write_text(compile_result.stdout, encoding='utf-8')
    if compile_result.returncode:
        print(compile_result.stdout)
        return compile_result.returncode
    runtime_cp = os.pathsep.join([str(classes), str(root / 'kanger/resources')] + [str(p) for p in libs])
    try:
        result = subprocess.run(['java', '-XX:-UsePerfData', '-cp', runtime_cp, name], cwd=root,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
        code, log = result.returncode, result.stdout
    except subprocess.TimeoutExpired as failure:
        code = 124
        log = failure.stdout or b''
        if isinstance(log, bytes):
            log = log.decode('utf-8', errors='replace')
        log += '\nCONTEXT_PRODUCTION_ROUNDS_TIMEOUT\n'
    (run / 'contract.log').write_text(log, encoding='utf-8')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    passed = code == 0 and 'CONTEXT_PRODUCTION_ROUNDS_CONTRACT_PASS' in log
    report = dict(head=head, production_sources=len(production), exit_code=code, passed=passed,
                  expected_baseline='RED: masked writes must be refused in both orders',
                  source_dirty=bool(subprocess.check_output(
                      ['git', 'status', '--porcelain'], cwd=root, text=True).strip()))
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(log, end='')
    print('Report:', run / 'report.json')
    return 0 if passed else (code or 1)


if __name__ == '__main__':
    raise SystemExit(main())
