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
    void incrementUnificationAttempts() { ++unificationAttempts; }
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
    }
}
