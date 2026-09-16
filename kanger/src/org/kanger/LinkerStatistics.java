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
    // End-of-pass action flags: Rule=1, TValue=2, FValue=4,
    // TempHypothesis=8, Hypothesis=16. Zero records a quiescent pass.
    private final long[] passActionMasks = new long[32];
    private final boolean tracePasses = Boolean.getBoolean("kanger.experiment.tracePasses");
    private final java.util.List<String> passTrace = new java.util.ArrayList<>();
    private final java.util.List<String> bindingTrace = new java.util.ArrayList<>();
    private final java.util.List<String> tupleTrace = new java.util.ArrayList<>();
    private final java.util.List<String> activationTrace = new java.util.ArrayList<>();
    private final java.util.Map<String, Integer> pairLastPass = new java.util.HashMap<>();
    private final java.util.Map<Long, Integer> pairOperationClass = new java.util.HashMap<>();
    private final long[] pairEffects = new long[48];
    private final long[] pairNewTuples = new long[3];
    private final long[] pairBoundaries = new long[27];
    private final java.util.Map<Long, String> pairOperationKeys = new java.util.HashMap<>();
    private final java.util.Map<String, Boolean> pairLastResult = new java.util.HashMap<>();
    private final long[] pairResultChanges = new long[3];
    // calls, multi-domain, empty topology, resolved IDs, bucket slots scanned,
    // retained candidates, indexed slots built, resolved IDs probed.
    private final long[] candidateSelection = new long[8];
    private final boolean timeStages = Boolean.getBoolean("kanger.experiment.timeStages");
    // 0..4: selection, linking, functions, database, update (non-overlapping).
    // 5: resolved lookup, INCLUDED in selection; 6: whole invocation, INCLUSIVE.
    private final long[] stageNanos = new long[7];
    private final java.util.Map<String, String> pairFirstOutput = new java.util.HashMap<>();
    private final long[] pairOutputMatches = new long[3];
    private final long[] pairOutputMismatches = new long[3];
    private final java.util.List<String> pairOutputWitnesses = new java.util.ArrayList<>();
    private int incomingActions = -1;
    private long priorRuleVisits, priorUnifications, priorTValues, priorFunctions, priorDatabase;

    public LinkerStatistics() {
    }

    private LinkerStatistics(LinkerStatistics source) {
        passTrace.addAll(source.passTrace);
        bindingTrace.addAll(source.bindingTrace);
        tupleTrace.addAll(source.tupleTrace);
        activationTrace.addAll(source.activationTrace);
        System.arraycopy(source.pairEffects, 0, pairEffects, 0, pairEffects.length);
        System.arraycopy(source.pairNewTuples, 0, pairNewTuples, 0, pairNewTuples.length);
        System.arraycopy(source.pairBoundaries, 0, pairBoundaries, 0, pairBoundaries.length);
        System.arraycopy(source.pairResultChanges, 0, pairResultChanges, 0, pairResultChanges.length);
        System.arraycopy(source.candidateSelection, 0, candidateSelection, 0, candidateSelection.length);
        System.arraycopy(source.stageNanos, 0, stageNanos, 0, stageNanos.length);
        System.arraycopy(source.pairOutputMatches, 0, pairOutputMatches, 0, pairOutputMatches.length);
        System.arraycopy(source.pairOutputMismatches, 0, pairOutputMismatches, 0, pairOutputMismatches.length);
        pairOutputWitnesses.addAll(source.pairOutputWitnesses);
        System.arraycopy(source.passActionMasks, 0, passActionMasks, 0, passActionMasks.length);
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
        passTrace.clear();
        bindingTrace.clear();
        tupleTrace.clear();
        activationTrace.clear();
        pairLastPass.clear(); pairOperationClass.clear();
        java.util.Arrays.fill(pairEffects, 0L); java.util.Arrays.fill(pairNewTuples, 0L);
        java.util.Arrays.fill(pairBoundaries, 0L);
        pairOperationKeys.clear(); pairLastResult.clear(); java.util.Arrays.fill(pairResultChanges, 0L);
        java.util.Arrays.fill(candidateSelection, 0L);
        java.util.Arrays.fill(stageNanos, 0L);
        pairFirstOutput.clear(); pairOutputWitnesses.clear();
        java.util.Arrays.fill(pairOutputMatches, 0L); java.util.Arrays.fill(pairOutputMismatches, 0L);
        incomingActions = -1;
        priorRuleVisits = priorUnifications = priorTValues = priorFunctions = priorDatabase = 0L;
        java.util.Arrays.fill(passActionMasks, 0L);
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
        QueryTaintSolve.beginLink();
    }

    LinkerStatistics snapshot() {
        return new LinkerStatistics(this);
    }

    void incrementPasses() { ++passes; }
    void recordPassActions(boolean rules, boolean tvalues, boolean fvalues,
                           boolean temporaryHypotheses, boolean hypotheses) {
        int mask = (rules ? 1 : 0) | (tvalues ? 2 : 0) | (fvalues ? 4 : 0)
                | (temporaryHypotheses ? 8 : 0) | (hypotheses ? 16 : 0);
        ++passActionMasks[mask];
        if (tracePasses) {
            passTrace.add("pass=" + passes + ",in=" + incomingActions + ",out=" + mask
                    + ",rules=" + (ruleVisits - priorRuleVisits)
                    + ",unifications=" + (unificationAttempts - priorUnifications)
                    + ",tvalues=" + (newTValues - priorTValues)
                    + ",functions=" + (functionEvaluations - priorFunctions)
                    + ",database=" + (databaseEvaluations - priorDatabase));
            priorRuleVisits = ruleVisits;
            priorUnifications = unificationAttempts;
            priorTValues = newTValues;
            priorFunctions = functionEvaluations;
            priorDatabase = databaseEvaluations;
            incomingActions = mask;
        }
    }

    /** Copy of the action histogram; aborted passes need not have an entry. */
    public long[] getPassActionMasks() { return passActionMasks.clone(); }
    /** Completed-pass trace, enabled at statistics construction; -1 starts a link invocation. */
    public java.util.List<String> getPassTrace() { return new java.util.ArrayList<>(passTrace); }
    void recordBindingTrace(String row) { bindingTrace.add(row); }
    public java.util.List<String> getBindingTrace() { return new java.util.ArrayList<>(bindingTrace); }
    void recordTupleTrace(String row) { tupleTrace.add(row); }
    public java.util.List<String> getTupleTrace() { return new java.util.ArrayList<>(tupleTrace); }
    void recordActivationTrace(String row) { activationTrace.add(row); }
    public java.util.List<String> getActivationTrace() { return new java.util.ArrayList<>(activationTrace); }
    void observePairInput(String key, int pass, long operation) {
        Integer previous = pairLastPass.put(key, pass);
        pairOperationClass.put(operation, previous == null ? 0 : previous == pass ? 1 : 2);
        pairOperationKeys.put(operation, key);
    }
    void observePairEffects(long operation, int effects) {
        ++pairEffects[pairOperationClass.get(operation) * 16 + effects];
    }
    void observePairNewTuple(long operation) { ++pairNewTuples[pairOperationClass.get(operation)]; }
    public long[] getPairEffects() { return pairEffects.clone(); }
    public long[] getPairNewTuples() { return pairNewTuples.clone(); }
    void observePairBoundary(long operation, boolean committed, long added, long removed,
                             long used, long excluded, long rulesUsed, boolean resultActivated, boolean resultAfter) {
        int offset = pairOperationClass.get(operation) * 9;
        ++pairBoundaries[offset];
        ++pairBoundaries[offset + (committed ? 1 : 2)];
        pairBoundaries[offset + 3] += added;
        pairBoundaries[offset + 4] += removed;
        pairBoundaries[offset + 5] += used;
        pairBoundaries[offset + 6] += excluded;
        pairBoundaries[offset + 7] += rulesUsed;
        if (resultActivated) ++pairBoundaries[offset + 8];
        Boolean previousResult = pairLastResult.put(pairOperationKeys.get(operation), resultAfter);
        if (previousResult != null && previousResult != resultAfter)
            ++pairResultChanges[pairOperationClass.get(operation)];
    }
    public long[] getPairBoundaries() { return pairBoundaries.clone(); }
    public long[] getPairResultChanges() { return pairResultChanges.clone(); }
    void recordCandidateSelection(int metric, long count) { candidateSelection[metric] += count; }
    public long[] getCandidateSelection() { return candidateSelection.clone(); }
    long stageClock() { return timeStages ? System.nanoTime() : 0L; }
    void finishStage(int stage, long start) {
        if (timeStages) stageNanos[stage] += System.nanoTime() - start;
    }
    /** Wall time: completed sections 0..5; inclusive invocation 6 also records failure.
     * Resolved lookup (5) is already inside selection (0); do not sum all slots. */
    public long[] getStageNanos() { return stageNanos.clone(); }
    void observePairOutput(long operation, String output) {
        String key = pairOperationKeys.get(operation);
        String first = pairFirstOutput.get(key);
        if (first == null) {
            pairFirstOutput.put(key, output);
        } else if (first.equals(output)) {
            ++pairOutputMatches[pairOperationClass.get(operation)];
        } else {
            ++pairOutputMismatches[pairOperationClass.get(operation)];
            if (pairOutputWitnesses.size() < 8)
                pairOutputWitnesses.add("input=" + key + "; first=" + first + "; actual=" + output);
            if (Boolean.getBoolean("kanger.experiment.verifyPairOutputs"))
                throw new AssertionError("Pair output mismatch: input=" + key + "; first=" + first + "; actual=" + output);
        }
    }
    public long[] getPairOutputMatches() { return pairOutputMatches.clone(); }
    public long[] getPairOutputMismatches() { return pairOutputMismatches.clone(); }
    public java.util.List<String> getPairOutputWitnesses() { return new java.util.ArrayList<>(pairOutputWitnesses); }
    void incrementRuleVisits() { ++ruleVisits; }
    void incrementBranchVisits() { ++branchVisits; }
    void incrementTerminalRotations() { ++terminalRotations; }
    void incrementCandidateRuleVisits() { ++candidateRuleVisits; }
    void incrementDomainPairs() { ++domainPairs; }

    long incrementUnificationAttempts(boolean firstPass,
                                      boolean query,
                                      boolean generated) {
        long operationId = ++unificationAttempts;
        QueryTaintSolve.beginOperation(operationId);
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
        return operationId;
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
        passTrace.addAll(other.passTrace);
        bindingTrace.addAll(other.bindingTrace);
        tupleTrace.addAll(other.tupleTrace);
        activationTrace.addAll(other.activationTrace);
        for (int i = 0; i < pairEffects.length; i++) pairEffects[i] += other.pairEffects[i];
        for (int i = 0; i < pairNewTuples.length; i++) pairNewTuples[i] += other.pairNewTuples[i];
        for (int i = 0; i < pairBoundaries.length; i++) pairBoundaries[i] += other.pairBoundaries[i];
        for (int i = 0; i < pairResultChanges.length; i++) pairResultChanges[i] += other.pairResultChanges[i];
        for (int i = 0; i < candidateSelection.length; i++) candidateSelection[i] += other.candidateSelection[i];
        for (int i = 0; i < stageNanos.length; i++) stageNanos[i] += other.stageNanos[i];
        for (int i = 0; i < pairOutputMatches.length; i++) {
            pairOutputMatches[i] += other.pairOutputMatches[i];
            pairOutputMismatches[i] += other.pairOutputMismatches[i];
        }
        pairOutputWitnesses.addAll(other.pairOutputWitnesses);
        for (int i = 0; i < passActionMasks.length; ++i) {
            passActionMasks[i] += other.passActionMasks[i];
        }
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
