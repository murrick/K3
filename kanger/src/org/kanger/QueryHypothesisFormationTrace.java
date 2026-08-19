/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Domain;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Diagnostic-only capture of residual hypothesis formation at the exact
 * terminal-branch instance where Linker creates a temporary hypothesis.
 *
 * <p>The recorder is inert unless {@link #begin()} is active on the current
 * thread. It does not participate in inference, candidate selection, store
 * admission or query result construction.</p>
 */
public final class QueryHypothesisFormationTrace {

    private static final ThreadLocal<List<Event>> ACTIVE =
            new ThreadLocal<List<Event>>();

    private QueryHypothesisFormationTrace() {
    }

    public static void begin() {
        ACTIVE.set(new ArrayList<Event>());
    }

    public static Snapshot end() {
        List<Event> events = ACTIVE.get();
        ACTIVE.remove();
        return new Snapshot(events == null
                ? Collections.<Event>emptyList()
                : new ArrayList<Event>(events));
    }

    static void recordResidual(Mind mind,
                               List<Domain> branch,
                               Set<Domain> candidates,
                               List<TValue> solve,
                               Domain candidate,
                               Hypothesis hypothesis) throws Exception {
        List<Event> active = ACTIVE.get();
        if (active == null) {
            return;
        }

        IRule source = candidate.getRule();
        List<String> branchText = new ArrayList<String>();
        boolean branchQueryDomain = false;
        boolean branchQueryVariable = false;
        for (Domain domain : branch) {
            branchText.add(domain.toString(mind));
            branchQueryDomain |= domain.isQuery(mind);
            branchQueryVariable |= hasQueryVariable(domain, mind);
        }

        List<String> candidateText = new ArrayList<String>();
        for (Domain domain : candidates) {
            candidateText.add(domain.toString(mind));
        }
        Collections.sort(candidateText);

        List<String> solveText = new ArrayList<String>();
        for (TValue value : solve) {
            solveText.add(value.toString(mind));
        }
        Collections.sort(solveText);

        active.add(new Event(
                mind.getQueryPass().name(),
                mind.getId(),
                source == null ? -1L : source.getId(),
                source != null && source.isQuery(),
                source != null && source.isGenerated(),
                branch.size(),
                candidates.size(),
                branchQueryDomain,
                branchQueryVariable,
                candidate.isQuery(mind),
                hasQueryVariable(candidate, mind),
                candidate.toString(mind),
                hypothesis.toString(mind),
                branchText,
                candidateText,
                solveText));
    }

    private static boolean hasQueryVariable(Domain domain, Mind mind)
            throws Exception {
        for (TVariable variable : domain.getArguments().getTVariables(mind)) {
            if (variable.isQuery(mind)) {
                return true;
            }
        }
        for (IArgument argument : domain.getArguments()) {
            if (!argument.isEmpty(mind)
                    && argument.getValue(mind) != null
                    && argument.getValue(mind).isCVariable()) {
                // Query C-variable ancestry is not equivalent to TVariable
                // ownership; keep this recorder conservative and let the
                // concrete branch/solve snapshot expose the value itself.
            }
        }
        return false;
    }

    public static final class Snapshot {
        private final List<Event> events;

        private Snapshot(List<Event> events) {
            this.events = Collections.unmodifiableList(events);
        }

        public List<Event> getEvents() {
            return events;
        }
    }

    public static final class Event {
        private final String pass;
        private final long mindId;
        private final long ruleId;
        private final boolean ruleQuery;
        private final boolean ruleGenerated;
        private final int branchSize;
        private final int candidateCount;
        private final boolean branchQueryDomain;
        private final boolean branchQueryVariable;
        private final boolean candidateQueryDomain;
        private final boolean candidateQueryVariable;
        private final String candidate;
        private final String hypothesis;
        private final List<String> branch;
        private final List<String> candidates;
        private final List<String> solve;

        private Event(String pass,
                      long mindId,
                      long ruleId,
                      boolean ruleQuery,
                      boolean ruleGenerated,
                      int branchSize,
                      int candidateCount,
                      boolean branchQueryDomain,
                      boolean branchQueryVariable,
                      boolean candidateQueryDomain,
                      boolean candidateQueryVariable,
                      String candidate,
                      String hypothesis,
                      List<String> branch,
                      List<String> candidates,
                      List<String> solve) {
            this.pass = pass;
            this.mindId = mindId;
            this.ruleId = ruleId;
            this.ruleQuery = ruleQuery;
            this.ruleGenerated = ruleGenerated;
            this.branchSize = branchSize;
            this.candidateCount = candidateCount;
            this.branchQueryDomain = branchQueryDomain;
            this.branchQueryVariable = branchQueryVariable;
            this.candidateQueryDomain = candidateQueryDomain;
            this.candidateQueryVariable = candidateQueryVariable;
            this.candidate = candidate;
            this.hypothesis = hypothesis;
            this.branch = Collections.unmodifiableList(new ArrayList<String>(branch));
            this.candidates = Collections.unmodifiableList(new ArrayList<String>(candidates));
            this.solve = Collections.unmodifiableList(new ArrayList<String>(solve));
        }

        public String getPass() { return pass; }
        public long getMindId() { return mindId; }
        public long getRuleId() { return ruleId; }
        public boolean isRuleQuery() { return ruleQuery; }
        public boolean isRuleGenerated() { return ruleGenerated; }
        public int getBranchSize() { return branchSize; }
        public int getCandidateCount() { return candidateCount; }
        public boolean hasBranchQueryDomain() { return branchQueryDomain; }
        public boolean hasBranchQueryVariable() { return branchQueryVariable; }
        public boolean hasCandidateQueryDomain() { return candidateQueryDomain; }
        public boolean hasCandidateQueryVariable() { return candidateQueryVariable; }
        public String getCandidate() { return candidate; }
        public String getHypothesis() { return hypothesis; }
        public List<String> getBranch() { return branch; }
        public List<String> getCandidates() { return candidates; }
        public List<String> getSolve() { return solve; }
    }
}
