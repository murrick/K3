#!/usr/bin/env python3
"""Run pending authoring contract or bounded native production-round reference controls."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('contract', 'reference'), default='contract')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    output = root / 'target/context-production-rounds'
    output.mkdir(parents=True, exist_ok=True)
    run = Path(tempfile.mkdtemp(prefix='run-', dir=str(output)))
    classes = run / 'classes'
    classes.mkdir()
    modules = ('kanger', 'kanger-bootstrap', 'kanger-command', 'kanger-udf',
               'kanger-data-dumb2', 'kanger-console')
    production = sorted(p for module in modules for p in (root / module / 'src').rglob('*.java'))
    names = [('org.kanger.storage.dumb2.ContextProductionRoundsContractRunner', 'CONTEXT_PRODUCTION_ROUNDS_CONTRACT_PASS')]
    if args.mode == 'reference':
        names = [('org.kanger.ContextProductionCaptureRunner', 'CONTEXT_PRODUCTION_CAPTURE_PASS'),
                 ('org.kanger.ContextProductionRoundsReferenceRunner', 'CONTEXT_PRODUCTION_ROUNDS_REFERENCE_PASS'),
                 ('org.kanger.ContextProductionTupleCoverageRunner', 'CONTEXT_PRODUCTION_TUPLE_COVERAGE_PASS'),
                 ('org.kanger.RuleVariableIdentityRunner', 'RULE_VARIABLE_IDENTITY_PASS'),
                 ('org.kanger.ContextProductionRotationAuditRunner', 'CONTEXT_PRODUCTION_ROTATION_AUDIT_PASS'),
                 ('org.kanger.ContextVerifiedLinearCoverageRunner', 'CONTEXT_VERIFIED_LINEAR_COVERAGE_PASS'),
                 ('org.kanger.ContextHornResidualVerifierRunner', 'CONTEXT_HORN_RESIDUAL_VERIFIER_PASS'),
                 ('org.kanger.ContextUniversalInstanceEvidenceRunner', 'CONTEXT_UNIVERSAL_INSTANCE_EVIDENCE_PASS'),
                 ('org.kanger.ContextVerifiedHornCoverageRunner', 'CONTEXT_VERIFIED_HORN_COVERAGE_PASS'),
                 ('org.kanger.ContextResidualSubsetCaptureRunner', 'CONTEXT_RESIDUAL_SUBSET_CAPTURE_PASS'),
                 ('org.kanger.ContextWitnessTransportBoundaryRunner', 'CONTEXT_WITNESS_TRANSPORT_BOUNDARY_PASS'),
                 ('org.kanger.ContextWitnessBundleRunner', 'CONTEXT_WITNESS_BUNDLE_PASS'),
                 ('org.kanger.storage.dumb2.GroundDisjunctionQualificationRunner', 'GROUND_DISJUNCTION_QUALIFICATION_PASS'),
                 ('org.kanger.DmzTerminalSupportCaptureRunner', 'DMZ_TERMINAL_SUPPORT_CAPTURE_PASS'),
                 ('org.kanger.DmzCollisionProofCaptureRunner', 'DMZ_COLLISION_PROOF_CAPTURE_PASS'),
                 ('org.kanger.DmzReplaySettlementRunner', 'DMZ_REPLAY_SETTLEMENT_PASS'),
                 ('org.kanger.DmzNativeAlternativeProofRunner', 'DMZ_NATIVE_ALTERNATIVE_PROOF_PASS')]
    drivers = [root / 'kanger-qualification/src' / (name.replace('.', '/') + '.java') for name, _ in names]
    listing = run / 'sources.list'
    listing.write_text(''.join('"' + str(p).replace('\\', '/') + '"\n'
                               for p in production + drivers), encoding='utf-8')
    libs = [root / 'lib/jline-3.13.0.jar', root / 'lib/jansi-1.17.jar']
    compile_cp = os.pathsep.join(str(p) for p in libs)
    compile_result = subprocess.run(
        ['javac', '-source', '8', '-target', '8', '-encoding', 'UTF-8',
         '-cp', compile_cp, '-sourcepath', str(root / 'kanger-qualification/src'),
         '-d', str(classes), '@' + str(listing)],
        cwd=root, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    (run / 'compile.log').write_text(compile_result.stdout, encoding='utf-8')
    if compile_result.returncode:
        print(compile_result.stdout)
        return compile_result.returncode
    runtime_cp = os.pathsep.join([str(classes), str(root / 'kanger/resources')] + [str(p) for p in libs])
    results = []
    for name, marker in names:
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
        (run / (name + '.log')).write_text(log, encoding='utf-8')
        if args.mode == 'contract': (run / 'contract.log').write_text(log, encoding='utf-8')
        passed = code == 0 and marker in log
        results.append(dict(driver=name, exit_code=code, passed=passed))
        print(log, end='', flush=True)
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip()
    passed = all(result['passed'] for result in results)
    report = dict(head=head, production_sources=len(production), mode=args.mode, passed=passed, results=results,
                  scope='Pending runtime authoring contract' if args.mode == 'contract' else 'Capture and scoped scalar Horn reference; runtime gate unchanged',
                  source_dirty=bool(subprocess.check_output(
                      ['git', 'status', '--porcelain'], cwd=root, text=True).strip()))
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Report:', run / 'report.json')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
