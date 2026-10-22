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
                 ('org.kanger.ContextWideResidualSubsetRunner', 'CONTEXT_WIDE_RESIDUAL_SUBSET_PASS'),
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
                 ('org.kanger.storage.dumb2.ContextPendingClauseWriteRunner', 'CONTEXT_PENDING_CLAUSE_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAutomaticPendingCommitRunner', 'CONTEXT_AUTOMATIC_PENDING_COMMIT_PASS'),
                 ('org.kanger.storage.dumb2.ContextNullaryPendingCommitRunner', 'CONTEXT_NULLARY_PENDING_COMMIT_PASS'),
                 ('org.kanger.storage.dumb2.ContextWideWitnessFamilyRunner', 'CONTEXT_WIDE_WITNESS_FAMILY_PASS'),
                 ('org.kanger.storage.dumb2.ContextNativeClauseWidthRunner', 'CONTEXT_NATIVE_CLAUSE_WIDTH_PASS'),
                 ('org.kanger.storage.dumb2.ContextWideGroundFamilyRunner', 'CONTEXT_WIDE_GROUND_FAMILY_PASS'),
                 ('org.kanger.storage.dumb2.ContextGroundRequestLimitsRunner', 'CONTEXT_GROUND_REQUEST_LIMITS_PASS'),
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
                 ('org.kanger.ContextWitnessGroundResultRunner', 'CONTEXT_WITNESS_GROUND_RESULT_PASS'),
                 ('org.kanger.ContextWitnessMultiPremiseRunner', 'CONTEXT_WITNESS_MULTI_PREMISE_PASS'),
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
                 ('org.kanger.ContextResidualLedgerLimitsRunner', 'CONTEXT_RESIDUAL_LEDGER_LIMITS_PASS'),
                 ('org.kanger.ContextResidualDuplicateWorkRunner', 'CONTEXT_RESIDUAL_DUPLICATE_WORK_PASS'),
                 ('org.kanger.ContextSevenPremiseClosureRunner', 'CONTEXT_SEVEN_PREMISE_CLOSURE_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenPremiseNativeWriteRunner', 'CONTEXT_SEVEN_PREMISE_NATIVE_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenPremiseNativeRefusalRunner', 'CONTEXT_SEVEN_PREMISE_NATIVE_REFUSAL_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenNegativeUnaryWriteRunner', 'CONTEXT_SEVEN_NEGATIVE_UNARY_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenNegativeUnaryRefusalRunner', 'CONTEXT_SEVEN_NEGATIVE_UNARY_REFUSAL_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenPositiveNullaryWriteRunner', 'CONTEXT_SEVEN_POSITIVE_NULLARY_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenPositiveNullaryRefusalRunner', 'CONTEXT_SEVEN_POSITIVE_NULLARY_REFUSAL_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenNegativeNullaryWriteRunner', 'CONTEXT_SEVEN_NEGATIVE_NULLARY_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextSevenNegativeNullaryRefusalRunner', 'CONTEXT_SEVEN_NEGATIVE_NULLARY_REFUSAL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityProofBoundaryRunner', 'CONTEXT_AUTHORITY_PROOF_BOUNDARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityProofReverseRunner', 'CONTEXT_AUTHORITY_PROOF_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRefutationRunner', 'CONTEXT_AUTHORITY_REFUTATION_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRefutationReverseRunner', 'CONTEXT_AUTHORITY_REFUTATION_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRefutationWorkRunner', 'CONTEXT_AUTHORITY_REFUTATION_WORK_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRefutationProofRunner', 'CONTEXT_AUTHORITY_REFUTATION_PROOF_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeUnaryRefutationRunner', 'CONTEXT_AUTHORITY_NEGATIVE_UNARY_REFUTATION_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeUnaryRefutationReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_UNARY_REFUTATION_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPositiveNullaryRefutationRunner', 'CONTEXT_AUTHORITY_POSITIVE_NULLARY_REFUTATION_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPositiveNullaryRefutationReverseRunner', 'CONTEXT_AUTHORITY_POSITIVE_NULLARY_REFUTATION_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeNullaryRefutationRunner', 'CONTEXT_AUTHORITY_NEGATIVE_NULLARY_REFUTATION_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeNullaryRefutationReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_NULLARY_REFUTATION_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityChainRunner', 'CONTEXT_AUTHORITY_CHAIN_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityChainReverseRunner', 'CONTEXT_AUTHORITY_CHAIN_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeProductRunner', 'CONTEXT_AUTHORITY_BRIDGE_PRODUCT_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeProductReverseRunner', 'CONTEXT_AUTHORITY_BRIDGE_PRODUCT_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeNegativeUnaryRunner', 'CONTEXT_AUTHORITY_BRIDGE_NEGATIVE_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeNegativeUnaryReverseRunner', 'CONTEXT_AUTHORITY_BRIDGE_NEGATIVE_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgePositiveNullaryRunner', 'CONTEXT_AUTHORITY_BRIDGE_POSITIVE_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgePositiveNullaryReverseRunner', 'CONTEXT_AUTHORITY_BRIDGE_POSITIVE_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeNegativeNullaryRunner', 'CONTEXT_AUTHORITY_BRIDGE_NEGATIVE_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityBridgeNegativeNullaryReverseRunner', 'CONTEXT_AUTHORITY_BRIDGE_NEGATIVE_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutPositiveUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_POSITIVE_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutPositiveUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_POSITIVE_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutPositiveNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_POSITIVE_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutPositiveNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_POSITIVE_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutNegativeUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_NEGATIVE_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutNegativeUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_NEGATIVE_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutNegativeNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_NEGATIVE_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeOutNegativeNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_OUT_NEGATIVE_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutPositiveEndUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_POSITIVE_END_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutPositiveEndUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_POSITIVE_END_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutPositiveEndNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_POSITIVE_END_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutPositiveEndNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_POSITIVE_END_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutNegativeEndUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_NEGATIVE_END_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutNegativeEndUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_NEGATIVE_END_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutNegativeEndNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_NEGATIVE_END_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorPositiveOutNegativeEndNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_POSITIVE_OUT_NEGATIVE_END_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutPositiveEndUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_POSITIVE_END_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutPositiveEndUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_POSITIVE_END_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutPositiveEndNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_POSITIVE_END_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutPositiveEndNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_POSITIVE_END_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutNegativeEndUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_NEGATIVE_END_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutNegativeEndUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_NEGATIVE_END_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutNegativeEndNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_NEGATIVE_END_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeDonorNegativeOutNegativeEndNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_DONOR_NEGATIVE_OUT_NEGATIVE_END_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalUnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalUnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalNullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalNullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalNegativeChainUnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_NEGATIVE_CHAIN_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalNegativeChainUnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_NEGATIVE_CHAIN_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalNegativeChainNullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_NEGATIVE_CHAIN_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalNegativeChainNullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_NEGATIVE_CHAIN_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalPositiveChainUnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_POSITIVE_CHAIN_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalPositiveChainUnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_POSITIVE_CHAIN_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalPositiveChainNullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_POSITIVE_CHAIN_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalPositiveChainNullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_POSITIVE_CHAIN_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalPositiveChainUnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_POSITIVE_CHAIN_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalPositiveChainUnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_POSITIVE_CHAIN_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalPositiveChainNullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_POSITIVE_CHAIN_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalPositiveChainNullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_POSITIVE_CHAIN_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsPositiveChainUnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_POSITIVE_CHAIN_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsPositiveChainUnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_POSITIVE_CHAIN_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsPositiveChainNullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_POSITIVE_CHAIN_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsPositiveChainNullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_POSITIVE_CHAIN_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns1UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_1_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns1UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_1_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns1NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_1_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns1NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_1_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns2UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_2_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns2UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_2_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns2NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_2_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns2NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_2_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns3UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_3_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns3UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_3_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns3NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_3_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns3NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_3_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns4UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_4_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns4UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_4_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns4NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_4_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns4NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_4_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns5UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_5_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns5UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_5_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns5NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_5_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns5NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_5_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns6UnaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_6_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns6UnaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_6_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns6NullaryRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_6_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedLocalChainSigns6NullaryReverseRunner', 'CONTEXT_AUTHORITY_MIXED_LOCAL_CHAIN_SIGNS_6_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns1UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_1_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns1UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_1_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns1NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_1_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns1NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_1_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns2UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_2_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns2UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_2_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns2NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_2_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns2NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_2_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns3UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_3_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns3UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_3_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns3NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_3_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns3NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_3_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns4UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_4_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns4UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_4_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns4NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_4_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns4NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_4_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns5UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_5_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns5UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_5_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns5NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_5_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns5NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_5_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns6UnaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_6_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns6UnaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_6_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns6NullaryRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_6_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySecondNegativeLocalChainSigns6NullaryReverseRunner', 'CONTEXT_AUTHORITY_SECOND_NEGATIVE_LOCAL_CHAIN_SIGNS_6_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns1UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_1_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns1UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_1_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns1NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_1_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns1NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_1_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns2UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_2_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns2UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_2_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns2NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_2_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns2NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_2_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns3UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_3_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns3UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_3_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns3NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_3_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns3NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_3_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns4UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_4_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns4UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_4_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns4NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_4_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns4NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_4_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns5UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_5_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns5UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_5_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns5NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_5_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns5NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_5_NULLARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns6UnaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_6_UNARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns6UnaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_6_UNARY_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns6NullaryRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_6_NULLARY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityNegativeLocalsChainSigns6NullaryReverseRunner', 'CONTEXT_AUTHORITY_NEGATIVE_LOCALS_CHAIN_SIGNS_6_NULLARY_REVERSE_PASS'),
                 ['org.kanger.storage.dumb2.ContextAuthorityUniversalBridgePositiveRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_BRIDGE_POSITIVE_PASS'],
                 ['org.kanger.storage.dumb2.ContextAuthorityUniversalBridgePositiveReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_BRIDGE_POSITIVE_REVERSE_PASS'],
                 ['org.kanger.storage.dumb2.ContextAuthorityUniversalBridgeNegativeRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_BRIDGE_NEGATIVE_PASS'],
                 ['org.kanger.storage.dumb2.ContextAuthorityUniversalBridgeNegativeReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_BRIDGE_NEGATIVE_REVERSE_PASS'],
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalCandidatePositiveRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_CANDIDATE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalCandidatePositiveReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_CANDIDATE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalCandidateNegativeRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_CANDIDATE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalCandidateNegativeReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_CANDIDATE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalDonorPositiveRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_DONOR_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalDonorPositiveReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_DONOR_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalDonorNegativeRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_DONOR_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalDonorNegativeReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_DONOR_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalLocalsPositiveRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_LOCALS_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalLocalsPositiveReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_LOCALS_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalLocalsNegativeRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_LOCALS_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityUniversalLocalsNegativeReverseRunner', 'CONTEXT_AUTHORITY_UNIVERSAL_LOCALS_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.ContextResidualRequestLimitsRunner', 'CONTEXT_RESIDUAL_REQUEST_LIMITS_PASS'),
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
