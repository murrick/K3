#!/usr/bin/env python3
"""Build and open a disposable exact-source DMZ Console fixture."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MODULES = ('kanger', 'kanger-bootstrap', 'kanger-command', 'kanger-udf',
           'kanger-data-dumb2', 'kanger-console')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument('--prepare-only', action='store_true', help='Prepare files without opening Console')
    mode.add_argument('--smoke', action='store_true', help='Run and verify the generated walkthrough')
    mode.add_argument('--family', action='store_true', help='Create a disposable SDK family proof report')
    parser.add_argument('--family-invocations', type=int, default=None, help='Fresh provider invocation cap (1..10000), family only')
    parser.add_argument('--family-choices', type=int, default=None, help='Restriction choice depth (1..128), family only')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/dmz-manual',
                        help='Parent directory; each run creates a new child')
    args = parser.parse_args()
    if not args.family and (args.family_invocations is not None or args.family_choices is not None):
        parser.error('family limits require --family')
    family_invocations = 64 if args.family_invocations is None else args.family_invocations
    family_choices = 128 if args.family_choices is None else args.family_choices
    if not 1 <= family_invocations <= 10000 or not 1 <= family_choices <= 128:
        parser.error('family invocations must be 1..10000 and choices 1..128')
    args.output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(args.output.resolve())))
    classes = run / 'classes'
    classes.mkdir()
    sources = sorted(p for module in MODULES for p in (ROOT / module / 'src').rglob('*.java'))
    fixture = 'DmzFamilyManualFixtureRunner' if args.family else 'DmzManualFixtureRunner'
    sources.append(ROOT / ('kanger-qualification/src/org/kanger/storage/dumb2/' + fixture + '.java'))
    source_list = run / 'sources.list'
    source_list.write_text(''.join('"' + str(p).replace('\\', '/') + '"\n' for p in sources), encoding='utf-8')
    libs = [ROOT / 'lib/jline-3.13.0.jar', ROOT / 'lib/jansi-1.17.jar']
    compile_cp = os.pathsep.join(map(str, libs))
    compiled = subprocess.run(['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
                               '-cp', compile_cp, '-sourcepath', str(ROOT / 'kanger-qualification/src'),
                               '-d', str(classes), '@' + str(source_list)],
                              cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'compile.log').write_text(compiled.stdout, encoding='utf-8')
    if compiled.returncode:
        print(compiled.stdout)
        return compiled.returncode
    runtime_cp = os.pathsep.join(map(str, [classes, ROOT / 'kanger/resources'] + libs))
    command = ['java', '-XX:-UsePerfData', '-Duser.home=' + str(run), '-cp', runtime_cp,
               'org.kanger.storage.dumb2.' + fixture, str(run)]
    command += [str(family_invocations), str(family_choices)] if args.family else [
        'prepare' if args.prepare_only else 'smoke' if args.smoke else 'console']
    print('Disposable DMZ fixture: ' + str(run), flush=True)
    return subprocess.call(command, cwd=run)


if __name__ == '__main__':
    raise SystemExit(main())
