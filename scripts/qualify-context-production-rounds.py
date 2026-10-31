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
    parser.add_argument('--mode', choices=('contract', 'reference', 'witness-contract', 'independent-reopen-contract', 'ordinary-commit-contract', 'private-witness-contract', 'universal-unit-contract'), default='contract')
    parser.add_argument('--ordinary-integration-candidate', action='store_true',
                        help='Compile an isolated experimental default hook; never edit production sources')
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
    if args.mode == 'independent-reopen-contract':
        names = [('org.kanger.storage.dumb2.ContextIndependentReopenContractRunner', 'CONTEXT_INDEPENDENT_REOPEN_CONTRACT_PASS')]
    if args.mode == 'ordinary-commit-contract':
        names = [('org.kanger.storage.dumb2.ContextOrdinaryProductionCommitRunner', 'CONTEXT_ORDINARY_PRODUCTION_COMMIT_PASS'),
                 ('org.kanger.storage.dumb2.ContextOrdinaryCommitCompatibilityRunner', 'CONTEXT_ORDINARY_COMMIT_COMPATIBILITY_PASS')]
    if args.mode == 'private-witness-contract':
        names = [('org.kanger.storage.dumb2.ContextPrivateWitnessAdmissionContractRunner', 'CONTEXT_PRIVATE_WITNESS_ADMISSION_CONTRACT_PASS')]
    if args.mode == 'universal-unit-contract':
        names = [('org.kanger.ContextUniversalUnitFamilyContractRunner', 'CONTEXT_UNIVERSAL_UNIT_FAMILY_CONTRACT_PASS')]
    if args.mode == 'reference':
        names = [('org.kanger.ContextProductionCaptureRunner', 'CONTEXT_PRODUCTION_CAPTURE_PASS'),
                 ('org.kanger.ContextProductionRoundsReferenceRunner', 'CONTEXT_PRODUCTION_ROUNDS_REFERENCE_PASS'),
                 ('org.kanger.ContextProductionTupleCoverageRunner', 'CONTEXT_PRODUCTION_TUPLE_COVERAGE_PASS'),
                 ('org.kanger.RuleVariableIdentityRunner', 'RULE_VARIABLE_IDENTITY_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationConflictPositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationConflictNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentPermutationConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_PERMUTATION_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleConflictPositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleConflictNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentMiddleConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_MIDDLE_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputConflictPositiveRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputConflictNegativeRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedInputControlRunner', 'CONTEXT_AUTHORITY_REPEATED_INPUT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputConflictPositiveRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputConflictNegativeRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedInputControlRunner', 'CONTEXT_AUTHORITY_FIXED_INPUT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputConflictPositiveRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputConflictNegativeRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAllInputControlRunner', 'CONTEXT_AUTHORITY_ALL_INPUT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputConflictPositiveRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputConflictNegativeRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantInputControlRunner', 'CONTEXT_AUTHORITY_CONSTANT_INPUT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportConflictPositiveRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportConflictNegativeRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConstantTransportControlRunner', 'CONTEXT_AUTHORITY_CONSTANT_TRANSPORT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantConflictPositiveRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantConflictNegativeRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctConstantControlRunner', 'CONTEXT_AUTHORITY_DISTINCT_CONSTANT_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDistinctHeadControlRunner', 'CONTEXT_AUTHORITY_DISTINCT_HEAD_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateConflictPositiveRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateConflictNegativeRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedCoordinateControlRunner', 'CONTEXT_AUTHORITY_OMITTED_COORDINATE_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedHeadControlRunner', 'CONTEXT_AUTHORITY_OMITTED_HEAD_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairConflictPositiveRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairConflictNegativeRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairControlRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityOmittedPairHeadControlRunner', 'CONTEXT_AUTHORITY_OMITTED_PAIR_HEAD_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadConflictPositiveRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadConflictNegativeRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadControlRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityGroundHeadLiteralControlRunner', 'CONTEXT_AUTHORITY_GROUND_HEAD_LITERAL_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadConflictPositiveRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadConflictNegativeRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadControlRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveHeadLiteralControlRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_HEAD_LITERAL_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityConjunctiveTailControlRunner', 'CONTEXT_AUTHORITY_CONJUNCTIVE_TAIL_CONTROL_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDisjunctiveHeadPositiveRunner', 'CONTEXT_AUTHORITY_DISJUNCTIVE_HEAD_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDisjunctiveHeadPositiveReverseRunner', 'CONTEXT_AUTHORITY_DISJUNCTIVE_HEAD_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDisjunctiveHeadNegativeRunner', 'CONTEXT_AUTHORITY_DISJUNCTIVE_HEAD_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityDisjunctiveHeadNegativeReverseRunner', 'CONTEXT_AUTHORITY_DISJUNCTIVE_HEAD_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedHeadPositiveRunner', 'CONTEXT_AUTHORITY_MIXED_HEAD_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedHeadPositiveReverseRunner', 'CONTEXT_AUTHORITY_MIXED_HEAD_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedHeadNegativeRunner', 'CONTEXT_AUTHORITY_MIXED_HEAD_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityMixedHeadNegativeReverseRunner', 'CONTEXT_AUTHORITY_MIXED_HEAD_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadPositiveRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadPositiveReverseRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadNegativeRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadNegativeReverseRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadConflictPositiveRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadConflictNegativeRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityAlternativeHeadConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_ALTERNATIVE_HEAD_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadIndependentPositiveRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_INDEPENDENT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadIndependentPositiveReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_INDEPENDENT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadIndependentNegativeRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_INDEPENDENT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadIndependentNegativeReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_INDEPENDENT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadDiagonalPositiveRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_DIAGONAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadDiagonalPositiveReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_DIAGONAL_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadDiagonalNegativeRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_DIAGONAL_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedHeadDiagonalNegativeReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_HEAD_DIAGONAL_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.ContextBranchQuantifierScopeRunner', 'CONTEXT_BRANCH_QUANTIFIER_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionIndependentPositiveRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_INDEPENDENT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionIndependentPositiveReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_INDEPENDENT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionIndependentNegativeRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_INDEPENDENT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionIndependentNegativeReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_INDEPENDENT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionDiagonalPositiveRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_DIAGONAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionDiagonalPositiveReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_DIAGONAL_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionDiagonalNegativeRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_DIAGONAL_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityQuantifiedConjunctionDiagonalNegativeReverseRunner', 'CONTEXT_AUTHORITY_QUANTIFIED_CONJUNCTION_DIAGONAL_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.ContextConjunctiveQuantifierScopeRunner', 'CONTEXT_CONJUNCTIVE_QUANTIFIER_SCOPE_PASS'),
                 ('org.kanger.ContextAntecedentQuantifierScopeRunner', 'CONTEXT_ANTECEDENT_QUANTIFIER_SCOPE_PASS'),
                 ('org.kanger.ContextPrivateWitnessPoolRunner', 'CONTEXT_PRIVATE_WITNESS_POOL_PASS'),
                 ('org.kanger.ContextPrivateWitnessContinuationRunner', 'CONTEXT_PRIVATE_WITNESS_CONTINUATION_PASS'),
                 ('org.kanger.ContextPrivateWitnessFamilyRunner', 'CONTEXT_PRIVATE_WITNESS_FAMILY_PASS'),
                 ('org.kanger.ContextPrivateWitnessAssemblyRunner', 'CONTEXT_PRIVATE_WITNESS_ASSEMBLY_PASS'),
                 ('org.kanger.ContextMixedPrimaryFamilyRunner', 'CONTEXT_MIXED_PRIMARY_FAMILY_PASS'),
                 ('org.kanger.ContextUniversalWitnessInstanceRunner', 'CONTEXT_UNIVERSAL_WITNESS_INSTANCE_PASS'),
                 ('org.kanger.ContextUniversalWitnessRouteRunner', 'CONTEXT_UNIVERSAL_WITNESS_ROUTE_PASS'),
                 ('org.kanger.ContextUniversalSchemaInteractionRunner', 'CONTEXT_UNIVERSAL_SCHEMA_INTERACTION_PASS'),
                 ('org.kanger.ContextUniversalUnitClosureRunner', 'CONTEXT_UNIVERSAL_UNIT_CLOSURE_PASS'),
                 ('org.kanger.ContextUniversalDerivedReplayRunner', 'CONTEXT_UNIVERSAL_DERIVED_REPLAY_PASS'),
                 ('org.kanger.ContextUniversalDerivedRoutesRunner', 'CONTEXT_UNIVERSAL_DERIVED_ROUTES_PASS'),
                 ('org.kanger.ContextUniversalDerivedBarrierRunner', 'CONTEXT_UNIVERSAL_DERIVED_BARRIER_PASS'),
                 ('org.kanger.ContextUniversalInputResidualsRunner', 'CONTEXT_UNIVERSAL_INPUT_RESIDUALS_PASS'),
                 ('org.kanger.ContextUniversalFrozenClosureRunner', 'CONTEXT_UNIVERSAL_FROZEN_CLOSURE_PASS'),
                 ('org.kanger.ContextUniversalFrozenReplayRunner', 'CONTEXT_UNIVERSAL_FROZEN_REPLAY_PASS'),
                 ('org.kanger.ContextUniversalFrozenUnitsRunner', 'CONTEXT_UNIVERSAL_FROZEN_UNITS_PASS'),
                 ('org.kanger.ContextUniversalFrozenEventsRunner', 'CONTEXT_UNIVERSAL_FROZEN_EVENTS_PASS'),
                 ('org.kanger.ContextUniversalTypedBarrierRunner', 'CONTEXT_UNIVERSAL_TYPED_BARRIER_PASS'),
                 ('org.kanger.ContextUniversalFrozenCoverageRunner', 'CONTEXT_UNIVERSAL_FROZEN_COVERAGE_PASS'),
                 ('org.kanger.ContextUniversalFrozenProductsRunner', 'CONTEXT_UNIVERSAL_FROZEN_PRODUCTS_PASS'),
                 ('org.kanger.ContextUniversalFrozenBranchRunner', 'CONTEXT_UNIVERSAL_FROZEN_BRANCH_PASS'),
                 ('org.kanger.ContextUniversalFrozenInstancesRunner', 'CONTEXT_UNIVERSAL_FROZEN_INSTANCES_PASS'),
                 ('org.kanger.ContextUniversalInstanceProductsRunner', 'CONTEXT_UNIVERSAL_INSTANCE_PRODUCTS_PASS'),
                 ('org.kanger.ContextUniversalFrozenRoundsRunner', 'CONTEXT_UNIVERSAL_FROZEN_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalNativeRowsRunner', 'CONTEXT_UNIVERSAL_NATIVE_ROWS_PASS'),
                 ('org.kanger.ContextUniversalConstraintClosureRunner', 'CONTEXT_UNIVERSAL_CONSTRAINT_CLOSURE_PASS'),
                 ('org.kanger.ContextUniversalFrozenConstraintsRunner', 'CONTEXT_UNIVERSAL_FROZEN_CONSTRAINTS_PASS'),
                 ('org.kanger.ContextUniversalSignedProductsRunner', 'CONTEXT_UNIVERSAL_SIGNED_PRODUCTS_PASS'),
                 ('org.kanger.ContextUniversalSignedRoundsRunner', 'CONTEXT_UNIVERSAL_SIGNED_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalSignedProductAuditRunner', 'CONTEXT_UNIVERSAL_SIGNED_PRODUCT_AUDIT_PASS'),
                 ('org.kanger.ContextUniversalSignedUnitExchangeRunner', 'CONTEXT_UNIVERSAL_SIGNED_UNIT_EXCHANGE_PASS'),
                 ('org.kanger.ContextUniversalSignedUnitRoundsRunner', 'CONTEXT_UNIVERSAL_SIGNED_UNIT_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalSignedApplicationsRunner', 'CONTEXT_UNIVERSAL_SIGNED_APPLICATIONS_PASS'),
                 ('org.kanger.ContextUniversalSignedApplicationRoundsRunner', 'CONTEXT_UNIVERSAL_SIGNED_APPLICATION_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalSignedFixedApplicationsRunner', 'CONTEXT_UNIVERSAL_SIGNED_FIXED_APPLICATIONS_PASS'),
                 ('org.kanger.ContextUniversalSignedFixedApplicationRoundsRunner', 'CONTEXT_UNIVERSAL_SIGNED_FIXED_APPLICATION_ROUNDS_PASS'),
                 ('org.kanger.ContextUniversalSymbolicHeadApplicationsRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_HEAD_APPLICATIONS_PASS'),
                 ('org.kanger.storage.dumb2.ContextMixedPrimaryWriteRunner', 'CONTEXT_MIXED_PRIMARY_WRITE_PASS'),
                 ('org.kanger.storage.dumb2.ContextPrivateWitnessAdmissionContractRunner', 'CONTEXT_PRIVATE_WITNESS_ADMISSION_CONTRACT_PASS'),
                 ('org.kanger.storage.dumb2.ContextPrivateWitnessWriteRunner', 'CONTEXT_PRIVATE_WITNESS_WRITE_PASS'),
                 ('org.kanger.ContextTrustedWitnessScopeRunner', 'CONTEXT_TRUSTED_WITNESS_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTrustedWitnessCommitRunner', 'CONTEXT_TRUSTED_WITNESS_COMMIT_PASS'),
                 ('org.kanger.storage.dumb2.ContextTrustedDuplicateSupportRunner', 'CONTEXT_TRUSTED_DUPLICATE_SUPPORT_PASS'),
                 ('org.kanger.ContextTrustedCutScopeRunner', 'CONTEXT_TRUSTED_CUT_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTrustedProductionCommitRunner', 'CONTEXT_TRUSTED_PRODUCTION_COMMIT_PASS'),
                 ('org.kanger.OperationalCommitScopeRunner', 'OPERATIONAL_COMMIT_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextOrdinaryCommitCompatibilityRunner', 'CONTEXT_ORDINARY_COMMIT_COMPATIBILITY_PASS'),
                 ('org.kanger.ContextDisjunctiveOuterExistentialScopeRunner', 'CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveOuterExistentialMiddlePositiveRunner', 'CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_MIDDLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveOuterExistentialMiddleNegativeRunner', 'CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_MIDDLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveOuterExistentialLeadingPositiveRunner', 'CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_LEADING_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveOuterExistentialLeadingNegativeRunner', 'CONTEXT_DISJUNCTIVE_OUTER_EXISTENTIAL_LEADING_NEGATIVE_PASS'),
                 ('org.kanger.ContextConjunctiveOuterExistentialScopeRunner', 'CONTEXT_CONJUNCTIVE_OUTER_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextConjunctiveOuterExistentialMiddlePositiveRunner', 'CONTEXT_CONJUNCTIVE_OUTER_EXISTENTIAL_MIDDLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextConjunctiveOuterExistentialMiddleNegativeRunner', 'CONTEXT_CONJUNCTIVE_OUTER_EXISTENTIAL_MIDDLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextConjunctiveOuterExistentialLeadingPositiveRunner', 'CONTEXT_CONJUNCTIVE_OUTER_EXISTENTIAL_LEADING_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextConjunctiveOuterExistentialLeadingNegativeRunner', 'CONTEXT_CONJUNCTIVE_OUTER_EXISTENTIAL_LEADING_NEGATIVE_PASS'),
                 ('org.kanger.ContextDiagonalOuterExistentialScopeRunner', 'CONTEXT_DIAGONAL_OUTER_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDiagonalOuterExistentialMiddlePositiveRunner', 'CONTEXT_DIAGONAL_OUTER_EXISTENTIAL_MIDDLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDiagonalOuterExistentialMiddleNegativeRunner', 'CONTEXT_DIAGONAL_OUTER_EXISTENTIAL_MIDDLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDiagonalOuterExistentialLeadingPositiveRunner', 'CONTEXT_DIAGONAL_OUTER_EXISTENTIAL_LEADING_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDiagonalOuterExistentialLeadingNegativeRunner', 'CONTEXT_DIAGONAL_OUTER_EXISTENTIAL_LEADING_NEGATIVE_PASS'),
                 ('org.kanger.ContextTwoOuterExistentialScopeRunner', 'CONTEXT_TWO_OUTER_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTwoOuterExistentialMiddlePositiveRunner', 'CONTEXT_TWO_OUTER_EXISTENTIAL_MIDDLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTwoOuterExistentialMiddleNegativeRunner', 'CONTEXT_TWO_OUTER_EXISTENTIAL_MIDDLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTwoOuterExistentialLeadingPositiveRunner', 'CONTEXT_TWO_OUTER_EXISTENTIAL_LEADING_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextTwoOuterExistentialLeadingNegativeRunner', 'CONTEXT_TWO_OUTER_EXISTENTIAL_LEADING_NEGATIVE_PASS'),
                 ('org.kanger.ContextOuterExistentialScopeRunner', 'CONTEXT_OUTER_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextOuterExistentialFirstPositiveRunner', 'CONTEXT_OUTER_EXISTENTIAL_FIRST_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextOuterExistentialFirstNegativeRunner', 'CONTEXT_OUTER_EXISTENTIAL_FIRST_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextOuterExistentialLastPositiveRunner', 'CONTEXT_OUTER_EXISTENTIAL_LAST_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextOuterExistentialLastNegativeRunner', 'CONTEXT_OUTER_EXISTENTIAL_LAST_NEGATIVE_PASS'),
                 ('org.kanger.ContextDualMixedExistentialScopeRunner', 'CONTEXT_DUAL_MIXED_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedStoppingRunner', 'CONTEXT_DUAL_MIXED_STOPPING_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedReopenContractRunner', 'CONTEXT_DUAL_MIXED_REOPEN_CONTRACT_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedExistentialPositiveRunner', 'CONTEXT_DUAL_MIXED_EXISTENTIAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedExistentialPositiveProofRunner', 'CONTEXT_DUAL_MIXED_EXISTENTIAL_POSITIVE_PROOF_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedExistentialNegativeRunner', 'CONTEXT_DUAL_MIXED_EXISTENTIAL_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDualMixedExistentialNegativeProofRunner', 'CONTEXT_DUAL_MIXED_EXISTENTIAL_NEGATIVE_PROOF_PASS'),
                 ('org.kanger.ContextMixedExistentialScopeRunner', 'CONTEXT_MIXED_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextMixedExistentialPositiveRunner', 'CONTEXT_MIXED_EXISTENTIAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextMixedExistentialNegativeRunner', 'CONTEXT_MIXED_EXISTENTIAL_NEGATIVE_PASS'),
                 ('org.kanger.ContextDisjunctiveExistentialScopeRunner', 'CONTEXT_DISJUNCTIVE_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveExistentialPositiveRunner', 'CONTEXT_DISJUNCTIVE_EXISTENTIAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextDisjunctiveExistentialNegativeRunner', 'CONTEXT_DISJUNCTIVE_EXISTENTIAL_NEGATIVE_PASS'),
                 ('org.kanger.ContextCompoundExistentialScopeRunner', 'CONTEXT_COMPOUND_EXISTENTIAL_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextCompoundExistentialPositiveRunner', 'CONTEXT_COMPOUND_EXISTENTIAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextCompoundExistentialNegativeRunner', 'CONTEXT_COMPOUND_EXISTENTIAL_NEGATIVE_PASS'),
                 ('org.kanger.ContextExistentialAntecedentScopeRunner', 'CONTEXT_EXISTENTIAL_ANTECEDENT_SCOPE_PASS'),
                 ('org.kanger.storage.dumb2.ContextExistentialAntecedentPositiveRunner', 'CONTEXT_EXISTENTIAL_ANTECEDENT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextExistentialAntecedentNegativeRunner', 'CONTEXT_EXISTENTIAL_ANTECEDENT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextForeignAntecedentQuantifierPositiveRunner', 'CONTEXT_FOREIGN_ANTECEDENT_QUANTIFIER_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextForeignAntecedentQuantifierNegativeRunner', 'CONTEXT_FOREIGN_ANTECEDENT_QUANTIFIER_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAntecedentQuantifierWriteRunner', 'CONTEXT_ANTECEDENT_QUANTIFIER_WRITE_PASS'),
                 ('org.kanger.ArgumentStampContextRunner', 'ARGUMENT_STAMP_CONTEXT_PASS'),
                 ('org.kanger.storage.dumb2.ContextIndependentReopenContractRunner', 'CONTEXT_INDEPENDENT_REOPEN_CONTRACT_PASS'),
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
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionDistinctPositiveRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_DISTINCT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionDistinctPositiveReverseRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_DISTINCT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionDistinctNegativeRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_DISTINCT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionDistinctNegativeReverseRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_DISTINCT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionMatchedPositiveRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_MATCHED_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionMatchedPositiveReverseRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_MATCHED_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionMatchedNegativeRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_MATCHED_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthoritySubstitutionMatchedNegativeReverseRunner', 'CONTEXT_AUTHORITY_SUBSTITUTION_MATCHED_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesDiagonalPositiveRunner', 'CONTEXT_AUTHORITY_VARIABLES_DIAGONAL_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesDiagonalPositiveReverseRunner', 'CONTEXT_AUTHORITY_VARIABLES_DIAGONAL_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesDiagonalNegativeRunner', 'CONTEXT_AUTHORITY_VARIABLES_DIAGONAL_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesDiagonalNegativeReverseRunner', 'CONTEXT_AUTHORITY_VARIABLES_DIAGONAL_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesIndependentPositiveRunner', 'CONTEXT_AUTHORITY_VARIABLES_INDEPENDENT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesIndependentPositiveReverseRunner', 'CONTEXT_AUTHORITY_VARIABLES_INDEPENDENT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesIndependentNegativeRunner', 'CONTEXT_AUTHORITY_VARIABLES_INDEPENDENT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityVariablesIndependentNegativeReverseRunner', 'CONTEXT_AUTHORITY_VARIABLES_INDEPENDENT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationSinglePositiveRunner', 'CONTEXT_AUTHORITY_PERMUTATION_SINGLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationSinglePositiveReverseRunner', 'CONTEXT_AUTHORITY_PERMUTATION_SINGLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationSingleNegativeRunner', 'CONTEXT_AUTHORITY_PERMUTATION_SINGLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationSingleNegativeReverseRunner', 'CONTEXT_AUTHORITY_PERMUTATION_SINGLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationDoublePositiveRunner', 'CONTEXT_AUTHORITY_PERMUTATION_DOUBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationDoublePositiveReverseRunner', 'CONTEXT_AUTHORITY_PERMUTATION_DOUBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationDoubleNegativeRunner', 'CONTEXT_AUTHORITY_PERMUTATION_DOUBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPermutationDoubleNegativeReverseRunner', 'CONTEXT_AUTHORITY_PERMUTATION_DOUBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_PARTIAL_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_PARTIAL_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_PARTIAL_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_PARTIAL_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialConflictPositiveRunner', 'CONTEXT_AUTHORITY_PARTIAL_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_PARTIAL_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialConflictNegativeRunner', 'CONTEXT_AUTHORITY_PARTIAL_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityPartialConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_PARTIAL_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_REPEATED_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_REPEATED_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedConflictPositiveRunner', 'CONTEXT_AUTHORITY_REPEATED_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedConflictNegativeRunner', 'CONTEXT_AUTHORITY_REPEATED_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityRepeatedConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_REPEATED_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_INPUT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_INPUT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_INPUT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_INPUT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputConflictPositiveRunner', 'CONTEXT_AUTHORITY_INPUT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_INPUT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputConflictNegativeRunner', 'CONTEXT_AUTHORITY_INPUT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityInputConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_INPUT_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_FIXED_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_FIXED_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_FIXED_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_FIXED_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedConflictPositiveRunner', 'CONTEXT_AUTHORITY_FIXED_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_FIXED_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedConflictNegativeRunner', 'CONTEXT_AUTHORITY_FIXED_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFixedConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_FIXED_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_FREE_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_FREE_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_FREE_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_FREE_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeConflictPositiveRunner', 'CONTEXT_AUTHORITY_FREE_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_FREE_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeConflictNegativeRunner', 'CONTEXT_AUTHORITY_FREE_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityFreeConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_FREE_CONFLICT_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentCompatiblePositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_COMPATIBLE_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentCompatiblePositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_COMPATIBLE_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentCompatibleNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_COMPATIBLE_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentCompatibleNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_COMPATIBLE_NEGATIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentConflictPositiveRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_CONFLICT_POSITIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentConflictPositiveReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_CONFLICT_POSITIVE_REVERSE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentConflictNegativeRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_CONFLICT_NEGATIVE_PASS'),
                 ('org.kanger.storage.dumb2.ContextAuthorityIndependentConflictNegativeReverseRunner', 'CONTEXT_AUTHORITY_INDEPENDENT_CONFLICT_NEGATIVE_REVERSE_PASS'),
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
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalSymbolicHeadRoundsRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_HEAD_ROUNDS_PASS'))
        names.append(('org.kanger.ContextUniversalSymbolicDonorEvidenceRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_DONOR_EVIDENCE_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalGroundedMixedApplicationsRunner', 'CONTEXT_UNIVERSAL_GROUNDED_MIXED_APPLICATIONS_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalGroundedMixedRoundsRunner', 'CONTEXT_UNIVERSAL_GROUNDED_MIXED_ROUNDS_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalGroundedSymbolicEvidenceRunner', 'CONTEXT_UNIVERSAL_GROUNDED_SYMBOLIC_EVIDENCE_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalInstantiatedUnitApplicationsRunner', 'CONTEXT_UNIVERSAL_INSTANTIATED_UNIT_APPLICATIONS_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalInstantiatedUnitRoundsRunner', 'CONTEXT_UNIVERSAL_INSTANTIATED_UNIT_ROUNDS_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalCompoundResidualEvidenceRunner', 'CONTEXT_UNIVERSAL_COMPOUND_RESIDUAL_EVIDENCE_PASS'))
    if args.mode == 'reference':
        names.append(('org.kanger.ContextUniversalCompoundExchangeRunner', 'CONTEXT_UNIVERSAL_COMPOUND_EXCHANGE_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundConsumerRunner', 'CONTEXT_UNIVERSAL_COMPOUND_CONSUMER_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundCompletionRunner', 'CONTEXT_UNIVERSAL_COMPOUND_COMPLETION_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundOccurrencesRunner', 'CONTEXT_UNIVERSAL_COMPOUND_OCCURRENCES_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedCompletionRunner', 'CONTEXT_UNIVERSAL_GROUPED_COMPLETION_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedKernelRunner', 'CONTEXT_UNIVERSAL_GROUPED_KERNEL_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedCandidatesRunner', 'CONTEXT_UNIVERSAL_GROUPED_CANDIDATES_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedLocalRoundsRunner', 'CONTEXT_UNIVERSAL_GROUPED_LOCAL_ROUNDS_PASS'))
        names.append(('org.kanger.ContextUniversalOriginBindingsRunner', 'CONTEXT_UNIVERSAL_ORIGIN_BINDINGS_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedRoundsRunner', 'CONTEXT_UNIVERSAL_GROUPED_ROUNDS_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedRoundsBudgetRunner', 'CONTEXT_UNIVERSAL_GROUPED_ROUNDS_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedRoundsGradeRunner', 'CONTEXT_UNIVERSAL_GROUPED_ROUNDS_GRADE_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedRoundsPrivateRunner', 'CONTEXT_UNIVERSAL_GROUPED_ROUNDS_PRIVATE_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedRoundsRefutationRunner', 'CONTEXT_UNIVERSAL_GROUPED_ROUNDS_REFUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedChainAuditRunner', 'CONTEXT_UNIVERSAL_GROUPED_CHAIN_AUDIT_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedChainPrivateRunner', 'CONTEXT_UNIVERSAL_GROUPED_CHAIN_PRIVATE_PASS'))
        names.append(('org.kanger.ContextUniversalValidationSessionRunner', 'CONTEXT_UNIVERSAL_VALIDATION_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedSessionRunner', 'CONTEXT_UNIVERSAL_GROUPED_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedSessionBudgetRunner', 'CONTEXT_UNIVERSAL_GROUPED_SESSION_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalGroupedSessionAuditMatrixRunner', 'CONTEXT_UNIVERSAL_GROUPED_SESSION_AUDIT_MATRIX_PASS'))
        names.append(('org.kanger.ContextUniversalCertifiedTransferSessionRunner', 'CONTEXT_UNIVERSAL_CERTIFIED_TRANSFER_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalCertifiedTransferBudgetRunner', 'CONTEXT_UNIVERSAL_CERTIFIED_TRANSFER_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalProductSessionRunner', 'CONTEXT_UNIVERSAL_PRODUCT_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalSymbolicCollectionSessionRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_COLLECTION_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalSymbolicCollectionBudgetRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_COLLECTION_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundCollectionSessionRunner', 'CONTEXT_UNIVERSAL_COMPOUND_COLLECTION_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundCollectionBudgetRunner', 'CONTEXT_UNIVERSAL_COMPOUND_COLLECTION_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalSymbolicRebaseSessionRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_REBASE_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalSymbolicRebaseBudgetRunner', 'CONTEXT_UNIVERSAL_SYMBOLIC_REBASE_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundRebaseSessionRunner', 'CONTEXT_UNIVERSAL_COMPOUND_REBASE_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalCompoundRebaseBudgetRunner', 'CONTEXT_UNIVERSAL_COMPOUND_REBASE_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalSignedCloseSessionRunner', 'CONTEXT_UNIVERSAL_SIGNED_CLOSE_SESSION_PASS'))
        names.append(('org.kanger.ContextUniversalSignedCloseBudgetRunner', 'CONTEXT_UNIVERSAL_SIGNED_CLOSE_BUDGET_PASS'))
        names.append(('org.kanger.ContextUniversalContinuationPermutationRunner', 'CONTEXT_UNIVERSAL_CONTINUATION_PERMUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalContinuationPrivateRunner', 'CONTEXT_UNIVERSAL_CONTINUATION_PRIVATE_PASS'))
        names.append(('org.kanger.ContextUniversalContinuationRefutationRunner', 'CONTEXT_UNIVERSAL_CONTINUATION_REFUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalContinuationGradeRunner', 'CONTEXT_UNIVERSAL_CONTINUATION_GRADE_PASS'))
        names.append(('org.kanger.ContextUniversalPrivateSymbolicContinuationRunner', 'CONTEXT_UNIVERSAL_PRIVATE_SYMBOLIC_CONTINUATION_PASS'))
        names.append(('org.kanger.ContextUniversalPrivateSymbolicRefutationRunner', 'CONTEXT_UNIVERSAL_PRIVATE_SYMBOLIC_REFUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalSeparatePrivateContinuationRunner', 'CONTEXT_UNIVERSAL_SEPARATE_PRIVATE_CONTINUATION_PASS'))
        names.append(('org.kanger.ContextUniversalTrustedPrivateSymbolicRunner', 'CONTEXT_UNIVERSAL_TRUSTED_PRIVATE_SYMBOLIC_PASS'))
        names.append(('org.kanger.ContextUniversalSeparatePrivateRefutationRunner', 'CONTEXT_UNIVERSAL_SEPARATE_PRIVATE_REFUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalSourceAlternativesFirstARunner', 'CONTEXT_UNIVERSAL_SOURCE_ALTERNATIVES_FIRST_A_PASS'))
        names.append(('org.kanger.ContextUniversalSourceAlternativesFirstBRunner', 'CONTEXT_UNIVERSAL_SOURCE_ALTERNATIVES_FIRST_B_PASS'))
        names.append(('org.kanger.ContextUniversalSourceAlternativesFirstCRunner', 'CONTEXT_UNIVERSAL_SOURCE_ALTERNATIVES_FIRST_C_PASS'))
        names.append(('org.kanger.ContextUniversalSourceAlternativesFirstDRunner', 'CONTEXT_UNIVERSAL_SOURCE_ALTERNATIVES_FIRST_D_PASS'))
        names.append(('org.kanger.ContextUniversalSourceAlternativesGradeRunner', 'CONTEXT_UNIVERSAL_SOURCE_ALTERNATIVES_GRADE_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesStableRunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_STABLE_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesTrustedRunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_TRUSTED_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesTrustedRefutationRunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_TRUSTED_REFUTATION_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesDelayedARunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_DELAYED_A_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesDelayedBRunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_DELAYED_B_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesDelayedDRunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_DELAYED_D_PASS'))
        names.append(('org.kanger.ContextUniversalSignedAlternativesDelayedERunner', 'CONTEXT_UNIVERSAL_SIGNED_ALTERNATIVES_DELAYED_E_PASS'))
        names.append(('org.kanger.ContextUniversalInitialCarrierPositiveRunner', 'CONTEXT_UNIVERSAL_INITIAL_CARRIER_POSITIVE_PASS'))
        names.append(('org.kanger.ContextUniversalInitialCarrierRefusalRunner', 'CONTEXT_UNIVERSAL_INITIAL_CARRIER_REFUSAL_PASS'))
        names.append(('org.kanger.ContextUniversalInitialCarrierOriginRunner', 'CONTEXT_UNIVERSAL_INITIAL_CARRIER_ORIGIN_PASS'))
        names.append(('org.kanger.ContextUniversalInitialEntryRefusalRunner', 'CONTEXT_UNIVERSAL_INITIAL_ENTRY_REFUSAL_PASS'))
        names.append(('org.kanger.ContextUniversalInitialEntryPositiveRunner', 'CONTEXT_UNIVERSAL_INITIAL_ENTRY_POSITIVE_PASS'))
        names.append(('org.kanger.ContextUniversalInitialEntryAuditRefusalRunner', 'CONTEXT_UNIVERSAL_INITIAL_ENTRY_AUDIT_REFUSAL_PASS'))
        names.append(('org.kanger.ContextUniversalInitialEntryAuditPositiveRunner', 'CONTEXT_UNIVERSAL_INITIAL_ENTRY_AUDIT_POSITIVE_PASS'))
        names.append(('org.kanger.ContextUniversalInitialNativeBoundaryRejectionRunner', 'CONTEXT_UNIVERSAL_INITIAL_NATIVE_BOUNDARY_REJECTION_PASS'))
        names.append(('org.kanger.ContextUniversalInitialNativeBoundaryPositiveRunner', 'CONTEXT_UNIVERSAL_INITIAL_NATIVE_BOUNDARY_POSITIVE_PASS'))
        names.append(('org.kanger.ContextUniversalNativeBranchRefusalRunner', 'CONTEXT_UNIVERSAL_NATIVE_BRANCH_REFUSAL_PASS'))
        names.append(('org.kanger.ContextUniversalNativeBranchPositiveRunner', 'CONTEXT_UNIVERSAL_NATIVE_BRANCH_POSITIVE_PASS'))
        names.append(('org.kanger.ContextUniversalNativeCandidateRefusalRunner', 'CONTEXT_UNIVERSAL_NATIVE_CANDIDATE_REFUSAL_PASS'))
        names.append(('org.kanger.ContextUniversalNativeCandidatePositiveRunner', 'CONTEXT_UNIVERSAL_NATIVE_CANDIDATE_POSITIVE_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeCandidateLeaseFreshnessRunner', 'CONTEXT_NATIVE_CANDIDATE_LEASE_FRESHNESS_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeCandidateLeasePositiveRunner', 'CONTEXT_NATIVE_CANDIDATE_LEASE_POSITIVE_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativePendingInspectionOwnershipRunner', 'CONTEXT_NATIVE_PENDING_INSPECTION_OWNERSHIP_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativePendingInspectionPositiveRunner', 'CONTEXT_NATIVE_PENDING_INSPECTION_POSITIVE_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeCommitGuardBoundaryRunner', 'CONTEXT_NATIVE_COMMIT_GUARD_BOUNDARY_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeCommitGuardIngressRunner', 'CONTEXT_NATIVE_COMMIT_GUARD_INGRESS_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeWriterPositiveRunner', 'CONTEXT_NATIVE_WRITER_POSITIVE_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeWriterBoundaryRunner', 'CONTEXT_NATIVE_WRITER_BOUNDARY_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeWriterInitializedRunner', 'CONTEXT_NATIVE_WRITER_INITIALIZED_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeWriterLegacyRunner', 'CONTEXT_NATIVE_WRITER_LEGACY_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeBaselineTrustedRunner', 'CONTEXT_NATIVE_BASELINE_TRUSTED_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeBaselineOwnershipRunner', 'CONTEXT_NATIVE_BASELINE_OWNERSHIP_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextTrustedNativeWriterPositiveRunner', 'CONTEXT_TRUSTED_NATIVE_WRITER_POSITIVE_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextTrustedNativeWriterRefusalRunner', 'CONTEXT_TRUSTED_NATIVE_WRITER_REFUSAL_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextTrustedNativePrivateWitnessRunner', 'CONTEXT_TRUSTED_NATIVE_PRIVATE_WITNESS_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextTrustedNativeOriginRunner', 'CONTEXT_TRUSTED_NATIVE_ORIGIN_PASS'))
        names.append(('org.kanger.ContextSignedWitnessBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_BOUNDARY_PASS'))
        names.append(('org.kanger.storage.dumb2.ContextNativeSignedWitnessRunner', 'CONTEXT_NATIVE_SIGNED_WITNESS_PASS'))
        names.append(('org.kanger.ContextSignedWitnessPlanPositiveRunner', 'CONTEXT_SIGNED_WITNESS_PLAN_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessPlanBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_PLAN_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessFramePositiveRunner', 'CONTEXT_SIGNED_WITNESS_FRAME_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessFrameBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_FRAME_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessReceiverPositiveRunner', 'CONTEXT_SIGNED_WITNESS_RECEIVER_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessReceiverBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_RECEIVER_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessInitialPositiveRunner', 'CONTEXT_SIGNED_WITNESS_INITIAL_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessInitialBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_INITIAL_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessBarrierPositiveRunner', 'CONTEXT_SIGNED_WITNESS_BARRIER_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessBarrierBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_BARRIER_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessNextPositiveRunner', 'CONTEXT_SIGNED_WITNESS_NEXT_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessNextBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_NEXT_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessSecondPositiveRunner', 'CONTEXT_SIGNED_WITNESS_SECOND_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessSecondBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_SECOND_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessRoundsPositiveRunner', 'CONTEXT_SIGNED_WITNESS_ROUNDS_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessRoundsBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_ROUNDS_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessGroupedPositiveRunner', 'CONTEXT_SIGNED_WITNESS_GROUPED_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessGroupedBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_GROUPED_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessStopPositiveRunner', 'CONTEXT_SIGNED_WITNESS_STOP_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessStopTrustedRunner', 'CONTEXT_SIGNED_WITNESS_STOP_TRUSTED_PASS'))
        names.append(('org.kanger.ContextSignedWitnessStopBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_STOP_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessStopIntegrityRunner', 'CONTEXT_SIGNED_WITNESS_STOP_INTEGRITY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessUntilPositiveRunner', 'CONTEXT_SIGNED_WITNESS_UNTIL_POSITIVE_PASS'))
        names.append(('org.kanger.ContextSignedWitnessUntilProgressRunner', 'CONTEXT_SIGNED_WITNESS_UNTIL_PROGRESS_PASS'))
        names.append(('org.kanger.ContextSignedWitnessUntilBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_UNTIL_BOUNDARY_PASS'))
        names.append(('org.kanger.ContextSignedWitnessUntilIntegrityRunner', 'CONTEXT_SIGNED_WITNESS_UNTIL_INTEGRITY_PASS'))
    if args.mode == 'reference': names += [('org.kanger.ContextSignedWitnessNativePositiveRunner', 'CONTEXT_SIGNED_WITNESS_NATIVE_POSITIVE_PASS'), ('org.kanger.ContextSignedWitnessNativeTrustedRunner', 'CONTEXT_SIGNED_WITNESS_NATIVE_TRUSTED_PASS'), ('org.kanger.ContextSignedWitnessNativeBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_NATIVE_BOUNDARY_PASS'), ('org.kanger.ContextSignedWitnessNativeIntegrityRunner', 'CONTEXT_SIGNED_WITNESS_NATIVE_INTEGRITY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.ContextSignedWitnessCandidatePositiveRunner', 'CONTEXT_SIGNED_WITNESS_CANDIDATE_POSITIVE_PASS'), ('org.kanger.ContextSignedWitnessCandidateWitnessRunner', 'CONTEXT_SIGNED_WITNESS_CANDIDATE_WITNESS_PASS'), ('org.kanger.ContextSignedWitnessCandidateRefutationRunner', 'CONTEXT_SIGNED_WITNESS_CANDIDATE_REFUTATION_PASS'), ('org.kanger.ContextSignedWitnessCandidateBoundaryRunner', 'CONTEXT_SIGNED_WITNESS_CANDIDATE_BOUNDARY_PASS'), ('org.kanger.ContextSignedWitnessCandidateIntegrityRunner', 'CONTEXT_SIGNED_WITNESS_CANDIDATE_INTEGRITY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.storage.dumb2.ContextSignedCandidateLeasePositiveRunner', 'CONTEXT_SIGNED_CANDIDATE_LEASE_POSITIVE_PASS'), ('org.kanger.storage.dumb2.ContextSignedCandidateLeaseFreshnessRunner', 'CONTEXT_SIGNED_CANDIDATE_LEASE_FRESHNESS_PASS'), ('org.kanger.storage.dumb2.ContextSignedCandidateLeaseBoundaryRunner', 'CONTEXT_SIGNED_CANDIDATE_LEASE_BOUNDARY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.storage.dumb2.ContextSignedPendingPositiveRunner', 'CONTEXT_SIGNED_PENDING_POSITIVE_PASS'), ('org.kanger.storage.dumb2.ContextSignedPendingTrustedRunner', 'CONTEXT_SIGNED_PENDING_TRUSTED_PASS'), ('org.kanger.storage.dumb2.ContextSignedPendingOwnershipRunner', 'CONTEXT_SIGNED_PENDING_OWNERSHIP_PASS'), ('org.kanger.storage.dumb2.ContextSignedPendingRefusalRunner', 'CONTEXT_SIGNED_PENDING_REFUSAL_PASS')]
    if args.mode == 'reference': names += [('org.kanger.storage.dumb2.ContextSignedCommitProfileRunner', 'CONTEXT_SIGNED_COMMIT_PROFILE_PASS'), ('org.kanger.storage.dumb2.ContextSignedCommitIngressRunner', 'CONTEXT_SIGNED_COMMIT_INGRESS_PASS'), ('org.kanger.storage.dumb2.ContextSignedCommitBoundaryRunner', 'CONTEXT_SIGNED_COMMIT_BOUNDARY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.storage.dumb2.ContextSignedCommitGatesRunner', 'CONTEXT_SIGNED_COMMIT_GATES_PASS')]
    if args.mode == 'reference': names += [('org.kanger.storage.dumb2.ContextSignedWriterForwardRunner', 'CONTEXT_SIGNED_WRITER_FORWARD_PASS'), ('org.kanger.storage.dumb2.ContextSignedWriterReverseRunner', 'CONTEXT_SIGNED_WRITER_REVERSE_PASS'), ('org.kanger.storage.dumb2.ContextSignedWriterProfileRunner', 'CONTEXT_SIGNED_WRITER_PROFILE_PASS'), ('org.kanger.storage.dumb2.ContextSignedWriterTrustedRunner', 'CONTEXT_SIGNED_WRITER_TRUSTED_PASS'), ('org.kanger.storage.dumb2.ContextSignedWriterOwnershipRunner', 'CONTEXT_SIGNED_WRITER_OWNERSHIP_PASS'), ('org.kanger.storage.dumb2.ContextSignedWriterBoundaryRunner', 'CONTEXT_SIGNED_WRITER_BOUNDARY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.ContextSignedEligibilityBarriersRunner', 'CONTEXT_SIGNED_ELIGIBILITY_BARRIERS_PASS'), ('org.kanger.ContextSignedEligibilityCoverageRunner', 'CONTEXT_SIGNED_ELIGIBILITY_COVERAGE_PASS'), ('org.kanger.ContextSignedEligibilityModelsRunner', 'CONTEXT_SIGNED_ELIGIBILITY_MODELS_PASS'), ('org.kanger.storage.dumb2.ContextSignedEligibilityLiveRunner', 'CONTEXT_SIGNED_ELIGIBILITY_LIVE_PASS')]
    if args.mode == 'reference': names += [('org.kanger.ContextSignedUnaryModelPositiveRunner', 'CONTEXT_SIGNED_UNARY_MODEL_POSITIVE_PASS'), ('org.kanger.ContextSignedUnaryModelTopologyRunner', 'CONTEXT_SIGNED_UNARY_MODEL_TOPOLOGY_PASS'), ('org.kanger.ContextSignedUnaryModelBoundaryRunner', 'CONTEXT_SIGNED_UNARY_MODEL_BOUNDARY_PASS'), ('org.kanger.ContextSignedUnaryModelIntegrityRunner', 'CONTEXT_SIGNED_UNARY_MODEL_INTEGRITY_PASS')]
    if args.mode == 'reference': names += [('org.kanger.ContextSignedUnaryBranchPositiveRunner', 'CONTEXT_SIGNED_UNARY_BRANCH_POSITIVE_PASS'), ('org.kanger.ContextSignedUnaryBranchExtensionRunner', 'CONTEXT_SIGNED_UNARY_BRANCH_EXTENSION_PASS'), ('org.kanger.ContextSignedUnaryBranchBoundaryRunner', 'CONTEXT_SIGNED_UNARY_BRANCH_BOUNDARY_PASS'), ('org.kanger.ContextSignedUnaryBranchIntegrityRunner', 'CONTEXT_SIGNED_UNARY_BRANCH_INTEGRITY_PASS')]
    drivers = [root / 'kanger-qualification/src' / (name.replace('.', '/') + '.java') for name, _ in names]
    if args.ordinary_integration_candidate:
        original = root / 'kanger-data-dumb2/src/org/kanger/storage/dumb2/DB.java'
        before = """            validateEffectiveConnections((Mind) proposed, workingConnections);
            if(pendingNativeWrite!=null) pendingNativeWrite.validate((Mind)proposed);"""
        after = """            if(pendingNativeWrite==null && !((Mind)proposed).isOperationalCommit()) validatePendingProductionCommit((Mind)proposed);
            else {
                validateEffectiveConnections((Mind) proposed, workingConnections);
                if(pendingNativeWrite!=null) pendingNativeWrite.validate((Mind)proposed);
            }"""
        source = original.read_text(encoding='utf-8')
        if source.count(before) != 1:
            raise RuntimeError('Experimental default hook baseline changed; reconcile the candidate')
        candidate = run / 'candidate/org/kanger/storage/dumb2/DB.java'
        candidate.parent.mkdir(parents=True, exist_ok=True)
        candidate.write_text(source.replace(before, after), encoding='utf-8')
        production = [candidate if p == original else p for p in production]
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
    report = dict(ordinary_integration_candidate=args.ordinary_integration_candidate, head=head, production_sources=len(production), mode=args.mode, passed=passed, results=results,
                  scope={'contract': 'Pending runtime authoring contract',
                         'witness-contract': 'Native primary existential witness consistency contract',
                         'independent-reopen-contract': 'Certified independent-coordinate source must reopen after publication',
                         'universal-unit-contract': 'Desired universal-unit family coverage; open required language gate, not expected-refusal PASS',
                         'private-witness-contract': 'Bounded actual private witness admission/local refutation; required integration gate',
                         'ordinary-commit-contract': 'Ordinary admission and observational/publication compatibility; activation gate',
                         'reference': 'Capture, witness identity and scoped Horn reference; round gate unchanged'}[args.mode],
                  source_dirty=bool(subprocess.check_output(
                      ['git', 'status', '--porcelain'], cwd=root, text=True).strip()))
    (run / 'report.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print('Report:', run / 'report.json')
    return 0 if passed else 1


if __name__ == '__main__':
    raise SystemExit(main())
