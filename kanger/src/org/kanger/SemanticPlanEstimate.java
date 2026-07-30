/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Immutable pre-execution estimate produced by the observational P5b planner.
 *
 * The estimate is deliberately separate from execution. It does not select an
 * index, reorder candidates, or alter inference. Unsupported shapes are
 * represented explicitly through {@link #isCalibrated()}.
 */
public final class SemanticPlanEstimate {

    private final String calibration;
    private final boolean calibrated;
    private final long candidatePool;
    private final long matchedCandidates;
    private final int boundPositions;
    private final int queryVariables;
    private final long expectedResultRows;
    private final long expectedExecutedOperations;
    private final long expectedEffectDelta;
    private final long expectedDirectDelta;
    private final double expectedEffectYield;

    private SemanticPlanEstimate(String calibration,
                                 boolean calibrated,
                                 long candidatePool,
                                 long matchedCandidates,
                                 int boundPositions,
                                 int queryVariables,
                                 long expectedResultRows,
                                 long expectedExecutedOperations,
                                 long expectedEffectDelta,
                                 long expectedDirectDelta,
                                 double expectedEffectYield) {
        this.calibration = calibration;
        this.calibrated = calibrated;
        this.candidatePool = candidatePool;
        this.matchedCandidates = matchedCandidates;
        this.boundPositions = boundPositions;
        this.queryVariables = queryVariables;
        this.expectedResultRows = expectedResultRows;
        this.expectedExecutedOperations = expectedExecutedOperations;
        this.expectedEffectDelta = expectedEffectDelta;
        this.expectedDirectDelta = expectedDirectDelta;
        this.expectedEffectYield = expectedEffectYield;
    }

    public static SemanticPlanEstimate calibrated(String calibration,
                                                   long candidatePool,
                                                   long matchedCandidates,
                                                   int boundPositions,
                                                   int queryVariables,
                                                   long expectedResultRows,
                                                   long expectedExecutedOperations,
                                                   long expectedEffectDelta,
                                                   long expectedDirectDelta) {
        double yield = expectedExecutedOperations == 0L
                ? 0.0
                : ((double) expectedEffectDelta) / expectedExecutedOperations;
        return new SemanticPlanEstimate(
                calibration,
                true,
                candidatePool,
                matchedCandidates,
                boundPositions,
                queryVariables,
                expectedResultRows,
                expectedExecutedOperations,
                expectedEffectDelta,
                expectedDirectDelta,
                yield);
    }

    public static SemanticPlanEstimate uncalibrated(String reason,
                                                     long candidatePool,
                                                     long matchedCandidates,
                                                     int boundPositions,
                                                     int queryVariables) {
        return new SemanticPlanEstimate(
                reason,
                false,
                candidatePool,
                matchedCandidates,
                boundPositions,
                queryVariables,
                0L,
                0L,
                0L,
                0L,
                0.0);
    }

    public String getCalibration() { return calibration; }
    public boolean isCalibrated() { return calibrated; }
    public long getCandidatePool() { return candidatePool; }
    public long getMatchedCandidates() { return matchedCandidates; }
    public int getBoundPositions() { return boundPositions; }
    public int getQueryVariables() { return queryVariables; }
    public long getExpectedResultRows() { return expectedResultRows; }
    public long getExpectedExecutedOperations() { return expectedExecutedOperations; }
    public long getExpectedEffectDelta() { return expectedEffectDelta; }
    public long getExpectedDirectDelta() { return expectedDirectDelta; }
    public double getExpectedEffectYield() { return expectedEffectYield; }
}
