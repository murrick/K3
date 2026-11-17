#!/usr/bin/env python3
"""One bounded D07 namespace model over real publication syscalls, not a power-cut simulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROBE = 'org.kanger.storage.dumb2.ContextMarkerCrashImageProbe'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, help='Reuse a new-format audit report with an ACK witness')
    parser.add_argument('--output', type=Path, default=ROOT / 'target/context-marker-durability')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(args.output.resolve())))
    if args.report:
        audit_report = args.report.resolve()
    else:
        audit = subprocess.run([sys.executable, str(ROOT / 'scripts/audit-context-durability.py'), '--output', str(run / 'audit')],
                               cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=360)
        (run / 'audit.log').write_text(audit.stdout)
        match = re.search(r'^Report: (.+)$', audit.stdout, re.M)
        if audit.returncode or not match:
            print(audit.stdout)
            return audit.returncode or 2
        audit_report = Path(match[1])
    data = json.loads(audit_report.read_text())
    if not data.get('fixture_completed') or not data.get('matches_built_provider_classes') or data.get('unfinished_sync_calls'):
        raise ValueError('Complete actual-I/O acquisition required')
    if hashlib.sha256(Path(data['jar']).read_bytes()).hexdigest() != data['jar_sha256']:
        raise ValueError('The traced JAR has changed; acquire a new trace')
    acks = data.get('source_acknowledgements', [])
    if len(acks) != 1:
        raise ValueError('One exact Source ACK witness required')
    ack = acks[0]
    renames = [entry for entry in data['source_marker_replacements'] if entry['line'] < ack['line']]
    if not renames:
        raise ValueError('Actual marker rename before ACK required')
    marker = renames[-1]
    parent = str(Path(marker['target']).parent)
    barriers = [entry for entry in data['file_syncs'] if entry['directory'] and entry['path'] == parent
                and entry['result'] == '0' and marker['line'] < entry['line'] < ack['line']]
    if any(marker['line'] < entry['line'] < ack['line'] for entry in data['global_flush_calls']):
        raise ValueError('Global flush requires a separate model interpretation')
    # The namespace projection assumes the rest of the acknowledged frame survived.
    # Independently require actual file/directory barriers for that own frame before the marker.
    fixture = audit_report.parent / 'fixture'
    image = fixture / 'ack-image'
    generation_name = 'revision-' + str(ack['revision'])
    captured_generation = image / 'Source.dumb2' / generation_name
    original_generation = fixture / 'db/Source.dumb2' / generation_name
    required_files = [original_generation / f.relative_to(captured_generation)
                      for f in captured_generation.rglob('*') if f.is_file()]
    if not required_files:
        raise ValueError('Captured generation must contain its seal and data')
    required_files += [fixture / 'db' / f.name for f in image.iterdir()
                       if f.is_file() and not f.name.endswith('.revision')]
    required_dirs = [original_generation, original_generation.parent, fixture / 'db']
    installs = [e for e in data['source_generation_installs'] if e['target'] == str(original_generation) and e['line'] < marker['line']]
    if not installs:
        raise ValueError('Actual selected generation install required')
    installed = installs[-1]['line']
    missing_files = [str(p) for p in required_files if not any(
        e['path'] == str(p) and not e['directory'] and e['result'] == '0' and installed < e['line'] < marker['line']
        for e in data['file_syncs'])]
    missing_dirs = [str(p) for p in required_dirs if not any(
        e['path'] == str(p) and e['directory'] and e['result'] == '0' and installed < e['line'] < marker['line']
        for e in data['file_syncs'])]
    own_frame_barriers = not missing_files and not missing_dirs
    rollback_admissible = not barriers
    environment = os.environ.copy()
    for option in ('KANGER_OPTIONS', 'JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS'):
        environment.pop(option, None)
    environment['KANGER_HOME'] = 'KANGER'
    classes = audit_report.parent / 'classes'
    cp = os.pathsep.join((str(classes), data['jar']))
    source = ROOT / 'kanger-qualification/src' / (PROBE.replace('.', '/') + '.java')
    compiled = subprocess.run(['javac', '-source', '8', '-target', '8', '-Xlint:-options', '-encoding', 'UTF-8',
                               '-cp', cp, '-d', str(classes), str(source)], cwd=ROOT, env=environment,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    (run / 'compile.log').write_text(compiled.stdout)
    if compiled.returncode:
        print(compiled.stdout)
        return compiled.returncode
    fixture = audit_report.parent / 'fixture'
    control = subprocess.run(['java', '-XX:-UsePerfData', '-cp', cp, PROBE, str(fixture / 'ack-image'),
                              str(fixture / 'previous-marker.bin'), str(run / 'control'), 'false'],
                             cwd=ROOT, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    (run / 'control.log').write_text(control.stdout)
    expected_control = 'MARKER_MODEL_PROBE previous={} acknowledged={} observed={} acknowledged_readable=true'.format(
        ack['previous'], ack['revision'], ack['revision'])
    if control.returncode or expected_control not in control.stdout:
        raise ValueError('Unmodified acknowledged image must remain readable: ' + control.stdout)
    result = subprocess.run(['java', '-XX:-UsePerfData', '-cp', cp, PROBE, str(fixture / 'ack-image'),
                             str(fixture / 'previous-marker.bin'), str(run / 'projection'), str(rollback_admissible).lower()],
                            cwd=ROOT, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=120)
    (run / 'probe.log').write_text(result.stdout)
    probe = re.search(r'MARKER_MODEL_PROBE previous=(\d+) acknowledged=(\d+) observed=(\d+) acknowledged_readable=(true|false)', result.stdout)
    if result.returncode or not probe:
        print(result.stdout)
        return result.returncode or 2
    previous, acknowledged, observed = map(int, probe.group(1, 2, 3))
    if (previous, acknowledged) != (ack['previous'], ack['revision']):
        raise ValueError('Trace ACK and captured image disagree')
    readable = probe[4] == 'true'
    passed = own_frame_barriers and not rollback_admissible and observed == acknowledged and readable
    report = dict(scope='One marker-name persistence projection; not full power-loss acceptance',
                  runtime_head=data['head'], source_dirty=data['working_tree_dirty'],
                  jar_sha256=data['jar_sha256'], audit_report=str(audit_report),
                  rename_line=marker['line'], acknowledgement_line=ack['line'],
                  successful_parent_barriers_before_ack=len(barriers),
                  projection_assumptions='Unforced directory rename may be lost; all other acknowledged-image files persist; runtime atomicity is retained',
                  own_frame_file_barriers_before_marker=not missing_files,
                  own_frame_directory_barriers_before_marker=not missing_dirs,
                  missing_own_frame_file_barriers=missing_files,
                  missing_own_frame_directory_barriers=missing_dirs,
                  old_marker_rollback_admissible=rollback_admissible,
                  unmodified_ack_image_control=True,
                  previous=previous, acknowledged=acknowledged, recovered=observed,
                  acknowledged_revision_readable=readable,
                  marker_namespace_gate_passed=passed, full_power_loss_acceptance=False)
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n')
    print('Report:', run / 'report.json')
    print(('D07_MARKER_NAMESPACE_MODEL_PASS' if passed else 'D07_MARKER_NAMESPACE_MODEL_RED')
          + ' acknowledged={} recovered={} readable={}'.format(acknowledged, observed, readable))
    print('This is an executable persistence model, not an observed hardware power cut.')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
