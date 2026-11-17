#!/usr/bin/env python3
"""Observe real DUMB2 syscall ordering on a fresh Linux fixture; no power-loss PASS."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DRIVER = 'org.kanger.storage.dumb2.ContextPublicationDurabilityTraceRunner'
CALLS = ('openat,close,write,pwrite64,fsync,fdatasync,rename,renameat,renameat2,'
         'mkdir,mkdirat,unlink,unlinkat,copy_file_range,sendfile,msync,sync,syncfs')


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True).strip()


def parse_trace(path, data):
    pending, events = {}, []
    for ordinal, line in enumerate(path.read_text(errors='replace').splitlines(), 1):
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
        events.append((ordinal, body))
    directories = {str(p) for p in data.rglob('*') if p.is_dir()} | {str(data)}
    for _, body in events:
        if re.match(r'mkdir(?:at)?\(', body) and body.endswith('= 0'):
            for name in re.findall(r'"([^"]+)"', body):
                if name.startswith(str(data)):
                    directories.add(name)
        if 'O_DIRECTORY' in body:
            directories.update(re.findall(r'\d+<([^>]+)>', body))
    syncs, installs, markers, topology_writes = [], [], [], []
    global_flushes, acknowledgements = [], []
    for ordinal, body in events:
        ack = re.search(r'DURABILITY_TRACE_ACK_SOURCE previous=(\d+) revision=(\d+)', body) if body.startswith('write(') else None
        if ack:
            acknowledgements.append(dict(line=ordinal, previous=int(ack[1]), revision=int(ack[2])))
        if re.match(r'(sync|syncfs|msync)\(', body):
            global_flushes.append(dict(line=ordinal, call=body))
        sync = re.match(r'(fsync|fdatasync)\(\d+<([^>]+)>\)\s*=\s*(.*)', body)
        if sync and (sync[2] == str(data) or sync[2].startswith(str(data) + '/')):
            syncs.append(dict(line=ordinal, call=sync[1], path=sync[2], result=sync[3],
                              directory=sync[2] in directories))
        if re.match(r'rename(?:at2|at)?\(', body) and body.endswith('= 0'):
            names = re.findall(r'"([^"]+)"', body)
            if len(names) >= 2 and names[0].startswith(str(data / 'Source.dumb2') + '/') and re.search(r'/revision-\d+$', names[-1]):
                installs.append(dict(line=ordinal, source=names[0], target=names[-1]))
            if names and names[-1] == str(data / 'Source.revision'):
                markers.append(dict(line=ordinal, source=names[0], target=names[-1]))
        if ('openat(' in body and 'O_CREAT' in body
                and str(data / 'Source.dumb2' / '.topology-') in body and '.base"' in body):
            topology_writes.append(ordinal)
    return dict(
        file_syncs=syncs,
        directory_sync_count=sum(s['directory'] for s in syncs),
        revision_manifest_sync_count=sum(s['path'].endswith('/revision.manifest') for s in syncs),
        source_generation_installs=installs,
        source_marker_replacements=markers,
        source_topology_base_creations=len(topology_writes),
        source_topology_base_sync_count=sum('/Source.dumb2/.topology-' in s['path'] and s['path'].endswith('.base') for s in syncs),
        initial_marker_path_sync_count=sum(s['path'] in {str(data / (n + '.revision')) for n in ('Source', 'Rules', 'Fork')} for s in syncs),
        global_flush_calls=global_flushes,
        source_acknowledgements=acknowledgements,
        unfinished_sync_calls=[body for _, body in pending.values() if re.match(r'(fsync|fdatasync|sync|syncfs|msync)\(', body)],
    )


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, default=ROOT / 'kanger-console/target/kanger-console-3.3-SNAPSHOT.jar')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/context-durability-audit')
    args = parser.parse_args()
    for command in ('java', 'javac', 'strace', 'git'):
        if not shutil.which(command):
            parser.error('Linux audit requires ' + command)
    jar = args.jar.resolve()
    if not jar.is_file():
        parser.error('Build the Console package first: ' + str(jar))
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes = run / 'classes'
    classes.mkdir()
    source = ROOT / 'kanger-qualification/src' / (DRIVER.replace('.', '/') + '.java')
    environment = os.environ.copy()
    for option in ('KANGER_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(option, None)
    environment['KANGER_HOME'] = 'KANGER'
    compiled = subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8',
                               '-cp', str(jar), '-d', str(classes), str(source)], cwd=ROOT,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120, env=environment)
    (run / 'compile.log').write_text(compiled.stdout)
    if compiled.returncode:
        print(compiled.stdout)
        return compiled.returncode
    fixture = run / 'fixture'  # Must not exist: the runner refuses overwriting data.
    trace = run / 'syscalls.log'
    result = subprocess.run(['strace', '-f', '-qq', '-yy', '-s', '4096', '-e', 'trace=' + CALLS, '-o', str(trace),
                             'java', '-XX:-UsePerfData', '-cp', os.pathsep.join((str(classes), str(jar))), DRIVER, str(fixture)],
                            cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=240, env=environment)
    (run / 'fixture.log').write_text(result.stdout)
    completed = re.search(r'^DURABILITY_TRACE_FIXTURE_COMPLETE checks=(\d+)$', result.stdout, re.M)
    report = dict(scope='Observation only; no power-loss or directory-durability acceptance',
                  head=git('rev-parse', 'HEAD'), working_tree_dirty=bool(git('status', '--porcelain')),
                  production_tree_dirty=bool(git('status', '--porcelain', '--untracked-files=all', '--',
                      *[module + '/src' for module in ('kanger', 'kanger-bootstrap', 'kanger-command', 'kanger-udf', 'kanger-data-dumb2', 'kanger-console', 'kanger-server')])),
                  jar=str(jar), jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                  fixture_exit_code=result.returncode, fixture_completed=bool(completed) and result.returncode == 0,
                  runtime_options='KANGER_HOME=KANGER; JVM/KANGER option environment cleared; -XX:-UsePerfData',
                  fixture_checks=int(completed[1]) if completed else 0,
                  java_version=subprocess.run(['java', '-version'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=environment).stdout.strip())
    with zipfile.ZipFile(jar) as packaged:
        names = ('ContextStore', 'ContextSnapshot', 'ContextBase', 'ContextManifestStore', 'ConnectionStore', 'RevisionStore', 'RevisionManifestStore')
        report['matches_built_provider_classes'] = all(
            (ROOT / 'kanger-data-dumb2/target/classes/org/kanger/storage/dumb2' / (name + '.class')).is_file()
            and packaged.read('org/kanger/storage/dumb2/' + name + '.class') ==
            (ROOT / 'kanger-data-dumb2/target/classes/org/kanger/storage/dumb2' / (name + '.class')).read_bytes() for name in names)
    if report['fixture_completed']:
        report['filesystem_type'] = re.search(r'^DURABILITY_TRACE_FILESTORE (.+)$', result.stdout, re.M)[1]
        report.update(parse_trace(trace, fixture / 'db'))
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Report:', run / 'report.json')
    if not report['fixture_completed']:
        print(result.stdout)
        return result.returncode or 1
    # Positive acquisition controls only. Missing barriers must NEVER become a passing durability oracle.
    if (not report['matches_built_provider_classes'] or report['unfinished_sync_calls']
            or len(report['source_generation_installs']) != 5 or len(report['source_marker_replacements']) != 5
            or not report['source_topology_base_creations'] or not report['file_syncs']):
        print('DURABILITY_AUDIT_INCOMPLETE: acquisition/trace controls failed')
        return 1
    print('DURABILITY_AUDIT_OBSERVED fixture_checks={} directory_syncs={} manifest_syncs={} topology_base_syncs={}'.format(
        report['fixture_checks'], report['directory_sync_count'], report['revision_manifest_sync_count'], report['source_topology_base_sync_count']))
    print('No power-loss qualification or production guarantee is granted by this report.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
