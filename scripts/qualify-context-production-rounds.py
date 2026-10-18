#!/usr/bin/env python3
"""Run authoring/witness contracts or bounded native production-round reference controls."""
from pathlib import Path
import argparse
import json
import os
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=('contract', 'reference', 'witness-contract'), default='contract')
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
    if args.mode == 'witness-contract':
        names = [('org.kanger.ContextWitnessForkContractRunner', 'CONTEXT_WITNESS_FORK_CONTRACT_PASS')]
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
                 ('org.kanger.ContextCompositionCandidateRunner', 'CONTEXT_COMPOSITION_CANDIDATE_PASS'),
                 ('org.kanger.ContextGroundCompositionProofRunner', 'CONTEXT_GROUND_COMPOSITION_PROOF_PASS'),
                 ('org.kanger.ContextGroundCompositionRoundsRunner', 'CONTEXT_GROUND_COMPOSITION_ROUNDS_PASS'),
                 ('org.kanger.ContextGroundLocalClosureRunner', 'CONTEXT_GROUND_LOCAL_CLOSURE_PASS'),
                 ('org.kanger.ContextGroundClauseFamilyRunner', 'CONTEXT_GROUND_CLAUSE_FAMILY_PASS'),
                 ('org.kanger.ContextUniversalCompositionProofRunner', 'CONTEXT_UNIVERSAL_COMPOSITION_PROOF_PASS'),
                 ('org.kanger.ContextUniversalCompositionRoundsRunner', 'CONTEXT_UNIVERSAL_COMPOSITION_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalUnitProofRunner', 'CONTEXT_UNIVERSAL_UNIT_PROOF_PASS'),
                 ('org.kanger.ContextUniversalUnitLedgerRunner', 'CONTEXT_UNIVERSAL_UNIT_LEDGER_PASS'),
                 ('org.kanger.ContextUniversalUnitRoundsRunner', 'CONTEXT_UNIVERSAL_UNIT_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalCombinedRoundsRunner', 'CONTEXT_UNIVERSAL_COMBINED_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalFamilyCoverageRunner', 'CONTEXT_UNIVERSAL_FAMILY_COVERAGE_PASS'),
                 ('org.kanger.ContextUniversalFactoringProofRunner', 'CONTEXT_UNIVERSAL_FACTORING_PROOF_PASS'),
                 ('org.kanger.ContextUniversalFactoredRoundsRunner', 'CONTEXT_UNIVERSAL_FACTORED_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalQuotientModelRunner', 'CONTEXT_UNIVERSAL_QUOTIENT_MODEL_PASS'),
                 ('org.kanger.ContextUniversalFamilyModelRunner', 'CONTEXT_UNIVERSAL_FAMILY_MODEL_PASS'),
                 ('org.kanger.ContextUniversalFamilyInspectionRunner', 'CONTEXT_UNIVERSAL_FAMILY_INSPECTION_PASS'),
                 ('org.kanger.ContextUniversalCandidateRunner', 'CONTEXT_UNIVERSAL_CANDIDATE_PASS'),
                 ('org.kanger.ContextUniversalCandidateAuditRunner', 'CONTEXT_UNIVERSAL_CANDIDATE_AUDIT_PASS'),
                 ('org.kanger.ContextUniversalFamilyAuditRunner', 'CONTEXT_UNIVERSAL_FAMILY_AUDIT_PASS'),
                 ('org.kanger.storage.dumb2.ContextUniversalCandidateSnapshotRunner', 'CONTEXT_UNIVERSAL_CANDIDATE_SNAPSHOT_PASS'),
                 ('org.kanger.storage.dumb2.ContextUniversalAuthoringBridgeRunner', 'CONTEXT_UNIVERSAL_AUTHORING_BRIDGE_PASS'),
                 ('org.kanger.storage.dumb2.ContextUniversalCandidateConsoleRunner', 'CONTEXT_UNIVERSAL_CANDIDATE_CONSOLE_PASS'),
                 ('org.kanger.storage.dumb2.ContextUniversalPublicationConsoleRunner', 'CONTEXT_UNIVERSAL_PUBLICATION_CONSOLE_PASS'),
                 ('org.kanger.storage.dumb2.ContextUniversalCommitSeamRunner', 'CONTEXT_UNIVERSAL_COMMIT_SEAM_PASS'),
                 ('org.kanger.storage.dumb2.ContextPendingProductionSnapshotRunner', 'CONTEXT_PENDING_PRODUCTION_SNAPSHOT_PASS'),
                 ('org.kanger.storage.dumb2.ContextPendingCommitPolicyRunner', 'CONTEXT_PENDING_COMMIT_POLICY_PASS'),
                 ('org.kanger.storage.dumb2.ContextPendingWitnessWriteRunner', 'CONTEXT_PENDING_WITNESS_WRITE_PASS'),
                 ('org.kanger.ContextGroundClauseCandidateRunner', 'CONTEXT_GROUND_CLAUSE_CANDIDATE_PASS'),
                 ('org.kanger.storage.dumb2.ContextGroundCandidateSnapshotRunner', 'CONTEXT_GROUND_CANDIDATE_SNAPSHOT_PASS'),
                 ('org.kanger.storage.dumb2.ContextGroundAuthoringBridgeRunner', 'CONTEXT_GROUND_AUTHORING_BRIDGE_PASS'),
                 ('org.kanger.ContextWitnessTransportBoundaryRunner', 'CONTEXT_WITNESS_TRANSPORT_BOUNDARY_PASS'),
                 ('org.kanger.ContextWitnessBundleRunner', 'CONTEXT_WITNESS_BUNDLE_PASS'),
                 ('org.kanger.ContextWitnessProjectionRunner', 'CONTEXT_WITNESS_PROJECTION_PASS'),
                 ('org.kanger.ContextWitnessPrimaryStepRunner', 'CONTEXT_WITNESS_PRIMARY_STEP_PASS'),
                 ('org.kanger.ContextWitnessMaterializationRunner', 'CONTEXT_WITNESS_MATERIALIZATION_PASS'),
                 ('org.kanger.ContextWitnessUnaryChainRunner', 'CONTEXT_WITNESS_UNARY_CHAIN_PASS'),
                 ('org.kanger.ContextWitnessSharedOriginJoinRunner', 'CONTEXT_WITNESS_SHARED_ORIGIN_JOIN_PASS'),
                 ('org.kanger.ContextWitnessPrimaryIdentityRunner', 'CONTEXT_WITNESS_PRIMARY_IDENTITY_PASS'),
                 ('org.kanger.ContextWitnessResultTransportRunner', 'CONTEXT_WITNESS_RESULT_TRANSPORT_PASS'),
                 ('org.kanger.ContextWitnessSeedReplayRunner', 'CONTEXT_WITNESS_SEED_REPLAY_PASS'),
                 ('org.kanger.ContextWitnessFamilyBarrierRunner', 'CONTEXT_WITNESS_FAMILY_BARRIER_PASS'),
                 ('org.kanger.ContextWitnessClauseCollisionRunner', 'CONTEXT_WITNESS_CLAUSE_COLLISION_PASS'),
                 ('org.kanger.ContextWitnessDisjointCoverageRunner', 'CONTEXT_WITNESS_DISJOINT_COVERAGE_PASS'),
                 ('org.kanger.ContextWitnessDormantCoverageRunner', 'CONTEXT_WITNESS_DORMANT_COVERAGE_PASS'),
                 ('org.kanger.ContextWitnessHornClosureRunner', 'CONTEXT_WITNESS_HORN_CLOSURE_PASS'),
                 ('org.kanger.ContextWitnessFamilyFixedPointRunner', 'CONTEXT_WITNESS_FAMILY_FIXED_POINT_PASS'),
                 ('org.kanger.ContextWitnessFamilyQualificationRunner', 'CONTEXT_WITNESS_FAMILY_QUALIFICATION_PASS'),
                 ('org.kanger.storage.dumb2.ContextProductionSnapshotRunner', 'CONTEXT_PRODUCTION_SNAPSHOT_PASS'),
                 ('org.kanger.ContextProductionNativeBindingRunner', 'CONTEXT_PRODUCTION_NATIVE_BINDING_PASS'),
                 ('org.kanger.ContextProductionFamilyAssemblyRunner', 'CONTEXT_PRODUCTION_FAMILY_ASSEMBLY_PASS'),
                 ('org.kanger.ContextGroundApplicationVerifierRunner', 'CONTEXT_GROUND_APPLICATION_VERIFIER_PASS'),
                 ('org.kanger.ContextWitnessRoundCoverageRunner', 'CONTEXT_WITNESS_ROUND_COVERAGE_PASS'),
                 ('org.kanger.ContextWitnessEmptyTargetRunner', 'CONTEXT_WITNESS_EMPTY_TARGET_PASS'),
                 ('org.kanger.ContextProductionCandidateRunner', 'CONTEXT_PRODUCTION_CANDIDATE_PASS'),
                 ('org.kanger.storage.dumb2.ContextProductionAuthoringBridgeRunner', 'CONTEXT_PRODUCTION_AUTHORING_BRIDGE_PASS'),
                 ('org.kanger.ContextProductionCoreJarRunner', 'CONTEXT_PRODUCTION_CORE_JAR_PASS'),
                 ('org.kanger.storage.dumb2.ContextProductionProviderJarRunner', 'CONTEXT_PRODUCTION_PROVIDER_JAR_PASS'),
                 ('org.kanger.ContextGroundCutEvidenceRunner', 'CONTEXT_GROUND_CUT_EVIDENCE_PASS'),
                 ('org.kanger.ContextGroundProductionRoundsRunner', 'CONTEXT_GROUND_PRODUCTION_ROUNDS_PASS'),
                 ('org.kanger.ContextGroundRoundCoverageRunner', 'CONTEXT_GROUND_ROUND_COVERAGE_PASS'),
                 ('org.kanger.ContextGroundHornFamilyRunner', 'CONTEXT_GROUND_HORN_FAMILY_PASS'),
                 ('org.kanger.ContextOrdinaryResidualRoundsRunner', 'CONTEXT_ORDINARY_RESIDUAL_ROUNDS_PASS'),
                 ('org.kanger.ContextOrdinaryResidualCoverageRunner', 'CONTEXT_ORDINARY_RESIDUAL_COVERAGE_PASS'),
                 ('org.kanger.ContextOrdinaryResidualParametersRunner', 'CONTEXT_ORDINARY_RESIDUAL_PARAMETERS_PASS'),
                 ('org.kanger.ContextOrdinaryResidualProbesRunner', 'CONTEXT_ORDINARY_RESIDUAL_PROBES_PASS'),
                 ('org.kanger.ContextOrdinaryHornFamilyRunner', 'CONTEXT_ORDINARY_HORN_FAMILY_PASS'),
                 ('org.kanger.storage.dumb2.ContextWitnessPrimaryIdentityStorageRunner', 'CONTEXT_WITNESS_PRIMARY_IDENTITY_STORAGE_PASS'),
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
                  scope={'contract': 'Pending runtime authoring contract',
                         'witness-contract': 'Native primary existential witness consistency contract',
                         'reference': 'Capture, witness identity and scoped Horn reference; round gate unchanged'}[args.mode],
                  source_dirty=bool(subprocess.check_output(
                      ['git', 'status', '--porcelain'], cwd=root, text=True).strip()))
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Report:', run / 'report.json')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
