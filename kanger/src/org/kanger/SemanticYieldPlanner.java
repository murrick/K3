/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.Collection;
import java.util.List;

/**
 * Pure pre-execution estimator for the first P5b calibration checkpoint.
 *
 * The initial model is intentionally narrow: one complete stored arity-three
 * domain, selected through the normal Analyzer predicate/polarity candidate
 * path. Its coefficients come directly from the verified P5a value/3
 * baseline. Other shapes return an explicit uncalibrated estimate.
 */
public final class SemanticYieldPlanner {

    public static final String P5A_SINGLE_DOMAIN_ARITY3 =
            "P5A_SINGLE_DOMAIN_ARITY3";

    private SemanticYieldPlanner() {
    }

    public static SemanticPlanEstimate estimate(Rule query,
                                                 Collection<IRule> candidates,
                                                 Mind mind,
                                                 boolean selectById) throws Exception {
        long candidatePool = candidates == null ? 0L : candidates.size();
        if (query == null || !query.isQuery()) {
            return SemanticPlanEstimate.uncalibrated(
                    "NOT_QUERY", candidatePool, 0L, 0, 0);
        }
        if (selectById) {
            return SemanticPlanEstimate.uncalibrated(
                    "SELECT_BY_ID", candidatePool, 0L, 0, 0);
        }

        List<List<Domain>> queryTree = query.getTree();
        if (queryTree.size() != 1 || queryTree.get(0).size() != 1) {
            return SemanticPlanEstimate.uncalibrated(
                    "MULTI_DOMAIN_QUERY", candidatePool, 0L, 0, 0);
        }

        Domain source = query.getDomain();
        int boundPositions = 0;
        int queryVariables = 0;
        for (int i = 0; i < source.getRange(); ++i) {
            if (isWildcard(source.get(i), mind)) {
                ++queryVariables;
            } else {
                ++boundPositions;
            }
        }

        if (source.getRange() != 3) {
            return SemanticPlanEstimate.uncalibrated(
                    "UNSUPPORTED_ARITY", candidatePool, 0L,
                    boundPositions, queryVariables);
        }

        long matchedCandidates = 0L;
        if (candidates != null) {
            for (IRule candidate : candidates) {
                if (!isSimpleStoredDomain(candidate, source, mind)) {
                    return SemanticPlanEstimate.uncalibrated(
                            "MIXED_CANDIDATE_SHAPE", candidatePool,
                            matchedCandidates, boundPositions, queryVariables);
                }
                if (matchesBoundPositions(source,
                        ((Rule) candidate).getDomain(), mind)) {
                    ++matchedCandidates;
                }
            }
        }

        if (queryVariables == 0) {
            long expectedDirectDelta = matchedCandidates > 0L ? 1L : 0L;
            return SemanticPlanEstimate.calibrated(
                    P5A_SINGLE_DOMAIN_ARITY3,
                    candidatePool,
                    matchedCandidates,
                    boundPositions,
                    queryVariables,
                    0L,
                    0L,
                    0L,
                    expectedDirectDelta);
        }

        long expectedOperations = 8L * matchedCandidates;
        long expectedEffectDelta;
        if (queryVariables == 1) {
            expectedEffectDelta = 4L * matchedCandidates;
        } else if (queryVariables == 2) {
            expectedEffectDelta = 5L * matchedCandidates;
        } else if (queryVariables == 3 && boundPositions == 0) {
            expectedEffectDelta = 5L * matchedCandidates
                    + (matchedCandidates > 0L ? 1L : 0L);
        } else {
            return SemanticPlanEstimate.uncalibrated(
                    "UNSUPPORTED_QUERY_SHAPE", candidatePool,
                    matchedCandidates, boundPositions, queryVariables);
        }

        return SemanticPlanEstimate.calibrated(
                P5A_SINGLE_DOMAIN_ARITY3,
                candidatePool,
                matchedCandidates,
                boundPositions,
                queryVariables,
                matchedCandidates,
                expectedOperations,
                expectedEffectDelta,
                0L);
    }

    private static boolean isSimpleStoredDomain(IRule candidate,
                                                Domain source,
                                                Mind mind) throws Exception {
        if (candidate == null
                || candidate.isDeleted(mind)
                || !candidate.isStored()) {
            return false;
        }
        List<List<Domain>> tree = ((Rule) candidate).getTree();
        if (tree.size() != 1 || tree.get(0).size() != 1) {
            return false;
        }
        Domain domain = ((Rule) candidate).getDomain();
        return domain.getPredicateId() == source.getPredicateId()
                && domain.isAntc() != source.isAntc()
                && domain.getRange() == source.getRange()
                && domain.isComplete();
    }

    private static boolean matchesBoundPositions(Domain source,
                                                 Domain candidate,
                                                 Mind mind) throws Exception {
        for (int i = 0; i < source.getRange(); ++i) {
            IArgument expected = source.get(i);
            if (isWildcard(expected, mind)) {
                continue;
            }
            IArgument actual = candidate.get(i);
            if (actual.isEmpty(mind)
                    || actual.getValue(mind).getId()
                    != expected.getValue(mind).getId()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWildcard(IArgument argument,
                                      Mind mind) throws Exception {
        if (argument.getType() == ArgumentType.TVARIABLE
                || argument.isEmpty(mind)) {
            return true;
        }
        return argument.getValue(mind).isCVariable();
    }
}
