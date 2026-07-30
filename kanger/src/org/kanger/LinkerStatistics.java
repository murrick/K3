/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

/**
 * Query-local operational counters for Linker planning. This is observational
 * execution state only; it is neither serialized nor part of logical results.
 */
public final class LinkerStatistics {

    public static final int EFFECT_NEW_TVALUE = 1;
    public static final int EFFECT_NEW_CAUSE = 1 << 1;
    public static final int EFFECT_DEFERRED_SOLVE_CANDIDATE = 1 << 2;
    public static final int EFFECT_USED_ONLY = 1 << 3;
    private static final int OPERATION_EFFECT_MASK_COUNT = 1 << 4;

    private long passes;
    private long ruleVisits;
    private long branchVisits;
    private long terminalRotations;
    private long candidateRuleVisits;
    private long domainPairs;
    private long unificationAttempts;
    private long newTValues;
    private long databaseEvaluations;
    private long functionEvaluations;

    private long firstPassDurableUnifications;
    private long firstPassQueryUnifications;
    private long firstPassGeneratedUnifications;
    private long laterPassDurableUnifications;
    private long laterPassQueryUnifications;
    private long laterPassGeneratedUnifications;

    private final long[] operationEffectMasks = new long[OPERATION_EFFECT_MASK_COUNT];

    public LinkerStatistics() {
    }

    private LinkerStatistics(LinkerStatistics source) {
        passes = source.passes;
        ruleVisits = source.ruleVisits;
        branchVisits = source.branchVisits;
        terminalRotations = source.terminalRotations;
        candidateRuleVisits = source.candidateRuleVisits;
        domainPairs = source.domainPairs;
        unificationAttempts = source.unificationAttempts;
        newTValues = source.newTValues;
        databaseEvaluations = source.databaseEvaluations;
        functionEvaluations = source.functionEvaluations;
        firstPassDurableUnifications = source.firstPassDurableUnifications;
        firstPassQueryUnifications = source.firstPassQueryUnifications;
        firstPassGeneratedUnifications = source.firstPassGeneratedUnifications;
        laterPassDurableUnifications = source.laterPassDurableUnifications;
        laterPassQueryUnifications = source.laterPassQueryUnifications;
        laterPassGeneratedUnifications = source.laterPassGeneratedUnifications;
        System.arraycopy(source.operationEffectMasks, 0,
                operationEffectMasks, 0, operationEffectMasks.length);
    }

    void reset() {
        passes = 0L;
        ruleVisits = 0L;
        branchVisits = 0L;
        terminalRotations = 0L;
        candidateRuleVisits = 0L;
        domainPairs = 0L;
        unificationAttempts = 0L;
        newTValues = 0L;
        databaseEvaluations = 0L;
        functionEvaluations = 0L;
        firstPassDurableUnifications = 0L;
        firstPassQueryUnifications = 0L;
        firstPassGeneratedUnifications = 0L;
        laterPassDurableUnifications = 0L;
        laterPassQueryUnifications = 0L;
        laterPassGeneratedUnifications = 0L;
        for (int i = 0; i < operationEffectMasks.length; ++i) {
            operationEffectMasks[i] = 0L;
        }
    }

    LinkerStatistics snapshot() {
        return new LinkerStatistics(this);
    }

    void incrementPasses() { ++passes; }
    void incrementRuleVisits() { ++ruleVisits; }
    void incrementBranchVisits() { ++branchVisits; }
    void incrementTerminalRotations() { ++terminalRotations; }
    void incrementCandidateRuleVisits() { ++candidateRuleVisits; }
    void incrementDomainPairs() { ++domainPairs; }

    void incrementUnificationAttempts(boolean firstPass,
                                       boolean query,
                                       boolean generated) {
        ++unificationAttempts;
        if (firstPass) {
            if (generated) {
                ++firstPassGeneratedUnifications;
            } else if (query) {
                ++firstPassQueryUnifications;
            } else {
                ++firstPassDurableUnifications;
            }
        } else {
            if (generated) {
                ++laterPassGeneratedUnifications;
            } else if (query) {
                ++laterPassQueryUnifications;
            } else {
                ++laterPassDurableUnifications;
            }
        }
    }

    void recordOperationEffectMask(int mask) {
        if (mask < 0 || mask >= operationEffectMasks.length) {
            throw new IllegalArgumentException("Unknown Linker operation effect mask: " + mask);
        }
        ++operationEffectMasks[mask];
    }

    void incrementNewTValues() { ++newTValues; }
    void incrementDatabaseEvaluations() { ++databaseEvaluations; }
    void incrementFunctionEvaluations() { ++functionEvaluations; }

    public long getPasses() { return passes; }
    public long getRuleVisits() { return ruleVisits; }
    public long getBranchVisits() { return branchVisits; }
    public long getTerminalRotations() { return terminalRotations; }
    public long getCandidateRuleVisits() { return candidateRuleVisits; }
    public long getDomainPairs() { return domainPairs; }
    public long getUnificationAttempts() { return unificationAttempts; }
    public long getNewTValues() { return newTValues; }
    public long getDatabaseEvaluations() { return databaseEvaluations; }
    public long getFunctionEvaluations() { return functionEvaluations; }
    public long getFirstPassDurableUnifications() { return firstPassDurableUnifications; }
    public long getFirstPassQueryUnifications() { return firstPassQueryUnifications; }
    public long getFirstPassGeneratedUnifications() { return firstPassGeneratedUnifications; }
    public long getLaterPassDurableUnifications() { return laterPassDurableUnifications; }
    public long getLaterPassQueryUnifications() { return laterPassQueryUnifications; }
    public long getLaterPassGeneratedUnifications() { return laterPassGeneratedUnifications; }

    public long getOperationEffectMaskCount(int mask) {
        if (mask < 0 || mask >= operationEffectMasks.length) {
            return 0L;
        }
        return operationEffectMasks[mask];
    }

    public long getClassifiedOperations() {
        long result = 0L;
        for (long count : operationEffectMasks) {
            result += count;
        }
        return result;
    }

    public long getOperationsWithEffect(int effect) {
        long result = 0L;
        for (int mask = 0; mask < operationEffectMasks.length; ++mask) {
            if ((mask & effect) != 0) {
                result += operationEffectMasks[mask];
            }
        }
        return result;
    }

    public long getNoImmediateEffectOperations() {
        return operationEffectMasks[0];
    }

    public void add(LinkerStatistics other) {
        if (other == null) {
            return;
        }
        passes += other.passes;
        ruleVisits += other.ruleVisits;
        branchVisits += other.branchVisits;
        terminalRotations += other.terminalRotations;
        candidateRuleVisits += other.candidateRuleVisits;
        domainPairs += other.domainPairs;
        unificationAttempts += other.unificationAttempts;
        newTValues += other.newTValues;
        databaseEvaluations += other.databaseEvaluations;
        functionEvaluations += other.functionEvaluations;
        firstPassDurableUnifications += other.firstPassDurableUnifications;
        firstPassQueryUnifications += other.firstPassQueryUnifications;
        firstPassGeneratedUnifications += other.firstPassGeneratedUnifications;
        laterPassDurableUnifications += other.laterPassDurableUnifications;
        laterPassQueryUnifications += other.laterPassQueryUnifications;
        laterPassGeneratedUnifications += other.laterPassGeneratedUnifications;
        for (int i = 0; i < operationEffectMasks.length; ++i) {
            operationEffectMasks[i] += other.operationEffectMasks[i];
        }
    }
}
