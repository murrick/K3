/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** Diagnostic-only capture of residual hypothesis formation. */
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

    public static void recordResidual(Domain candidate,
                                      Mind mind,
                                      Hypothesis hypothesis) throws Exception {
        List<Event> active = ACTIVE.get();
        if (active == null) {
            return;
        }

        IRule owner = candidate.getRule();
        Rule rule = owner instanceof Rule ? (Rule) owner : null;
        int branchIndex = -1;
        List<Domain> branch = Collections.emptyList();
        if (rule != null) {
            int index = 0;
            for (List<Domain> current : rule.getTree()) {
                for (Domain domain : current) {
                    if (domain == candidate) {
                        branchIndex = index;
                        branch = current;
                        break;
                    }
                }
                if (branchIndex >= 0) {
                    break;
                }
                ++index;
            }
        }

        List<String> branchState = new ArrayList<String>();
        boolean branchQueryDomain = false;
        boolean branchQueryVariable = false;
        for (Domain domain : branch) {
            branchState.add(domainState(domain, mind));
            branchQueryDomain |= domain.isQuery(mind);
            branchQueryVariable |= hasQueryVariable(domain, mind);
        }

        List<String> solve = new ArrayList<String>();
        if (rule != null) {
            SortedSet<TVariable> variables = new TreeSet<TVariable>();
            for (List<Domain> current : rule.getTree()) {
                for (Domain domain : current) {
                    variables.addAll(domain.getArguments().getTVariables(mind));
                }
            }
            for (TVariable variable : variables) {
                if (!variable.isEmpty()) {
                    TValue value = variable.getCurrent();
                    solve.add(variable.getId() + "="
                            + (value == null ? "null" : value.toString(mind)));
                }
            }
        }

        CVarLineage lineage = cvarLineage(candidate, mind);

        active.add(new Event(
                mind.getQueryPass().name(),
                mind.getId(),
                owner == null ? -1L : owner.getId(),
                owner != null && owner.isQuery(),
                owner != null && owner.isGenerated(),
                branchIndex,
                branch.size(),
                branchQueryDomain,
                branchQueryVariable,
                candidate.isQuery(mind),
                hasQueryVariable(candidate, mind),
                lineage.cvars,
                lineage.queryOwnedRoots,
                lineage.queryLinkedRoots,
                lineage.cvars > 0 && lineage.cvars == lineage.queryOwnedRoots,
                lineage.cvars > 0 && lineage.cvars == lineage.queryLinkedRoots,
                candidate.toString(mind),
                hypothesis.toString(mind),
                branchState,
                solve));
    }

    private static CVarLineage cvarLineage(Domain domain, Mind mind)
            throws Exception {
        int cvars = 0;
        int queryOwnedRoots = 0;
        int queryLinkedRoots = 0;

        for (IArgument argument : domain.getArguments()) {
            if (argument.isEmpty(mind)) {
                continue;
            }
            ITerm value = argument.getValue(mind);
            if (value == null || !value.isCVariable()) {
                continue;
            }

            ++cvars;
            Term root = (Term) value;
            ITerm parent;
            while ((parent = root.getParent(mind)) != null
                    && parent.isCVariable()) {
                root = (Term) parent;
            }

            IRule rootOwner = root.getRule(mind);
            boolean queryOwned = rootOwner != null && rootOwner.isQuery();
            if (queryOwned) {
                ++queryOwnedRoots;
                ++queryLinkedRoots;
            } else if (hasVisibleQueryProjection(root, mind)) {
                ++queryLinkedRoots;
            }
        }
        return new CVarLineage(cvars, queryOwnedRoots, queryLinkedRoots);
    }

    /**
     * A native/root C-variable may still participate in the current query when
     * Linker projected it into the binding scope of a visible query Rule. The
     * canonical parent -> child-by-target-Rule map preserves that relation even
     * though the root itself remains owned by the native Rule.
     */
    private static boolean hasVisibleQueryProjection(Term root, Mind mind)
            throws Exception {
        for (IRule candidate : mind.getRules()) {
            if (candidate == null
                    || !candidate.isQuery()
                    || candidate.isDeleted(mind)) {
                continue;
            }
            ITerm child = mind.getCVarChild(root, candidate.getId());
            if (child != null && child.isCVariable()) {
                return true;
            }
        }
        return false;
    }

    private static final class CVarLineage {
        private final int cvars;
        private final int queryOwnedRoots;
        private final int queryLinkedRoots;

        private CVarLineage(int cvars,
                            int queryOwnedRoots,
                            int queryLinkedRoots) {
            this.cvars = cvars;
            this.queryOwnedRoots = queryOwnedRoots;
            this.queryLinkedRoots = queryLinkedRoots;
        }
    }

    private static String domainState(Domain domain, Mind mind)
            throws Exception {
        return domain.toString(mind)
                + "{complete=" + domain.isComplete()
                + ",stored=" + domain.isStored(mind)
                + ",calculated=" + domain.isCalculated(mind)
                + ",excluded=" + domain.isExcluded(mind)
                + ",used=" + domain.isUsed(mind)
                + ",query=" + domain.isQuery(mind)
                + ",queryVar=" + hasQueryVariable(domain, mind)
                + ",system=" + domain.isSystem(mind)
                + "}";
    }

    private static boolean hasQueryVariable(Domain domain, Mind mind)
            throws Exception {
        for (TVariable variable : domain.getArguments().getTVariables(mind)) {
            if (variable.isQuery(mind)) {
                return true;
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
        private final int branchIndex;
        private final int branchSize;
        private final boolean branchQueryDomain;
        private final boolean branchQueryVariable;
        private final boolean candidateQueryDomain;
        private final boolean candidateQueryVariable;
        private final int candidateCVars;
        private final int candidateQueryRoots;
        private final int candidateQueryLinkedRoots;
        private final boolean candidateAllQueryRoots;
        private final boolean candidateAllQueryLinkedRoots;
        private final String candidate;
        private final String hypothesis;
        private final List<String> branchState;
        private final List<String> solve;

        private Event(String pass,
                      long mindId,
                      long ruleId,
                      boolean ruleQuery,
                      boolean ruleGenerated,
                      int branchIndex,
                      int branchSize,
                      boolean branchQueryDomain,
                      boolean branchQueryVariable,
                      boolean candidateQueryDomain,
                      boolean candidateQueryVariable,
                      int candidateCVars,
                      int candidateQueryRoots,
                      int candidateQueryLinkedRoots,
                      boolean candidateAllQueryRoots,
                      boolean candidateAllQueryLinkedRoots,
                      String candidate,
                      String hypothesis,
                      List<String> branchState,
                      List<String> solve) {
            this.pass = pass;
            this.mindId = mindId;
            this.ruleId = ruleId;
            this.ruleQuery = ruleQuery;
            this.ruleGenerated = ruleGenerated;
            this.branchIndex = branchIndex;
            this.branchSize = branchSize;
            this.branchQueryDomain = branchQueryDomain;
            this.branchQueryVariable = branchQueryVariable;
            this.candidateQueryDomain = candidateQueryDomain;
            this.candidateQueryVariable = candidateQueryVariable;
            this.candidateCVars = candidateCVars;
            this.candidateQueryRoots = candidateQueryRoots;
            this.candidateQueryLinkedRoots = candidateQueryLinkedRoots;
            this.candidateAllQueryRoots = candidateAllQueryRoots;
            this.candidateAllQueryLinkedRoots = candidateAllQueryLinkedRoots;
            this.candidate = candidate;
            this.hypothesis = hypothesis;
            this.branchState = Collections.unmodifiableList(
                    new ArrayList<String>(branchState));
            this.solve = Collections.unmodifiableList(new ArrayList<String>(solve));
        }

        public String getPass() { return pass; }
        public long getMindId() { return mindId; }
        public long getRuleId() { return ruleId; }
        public boolean isRuleQuery() { return ruleQuery; }
        public boolean isRuleGenerated() { return ruleGenerated; }
        public int getBranchIndex() { return branchIndex; }
        public int getBranchSize() { return branchSize; }
        public boolean hasBranchQueryDomain() { return branchQueryDomain; }
        public boolean hasBranchQueryVariable() { return branchQueryVariable; }
        public boolean hasCandidateQueryDomain() { return candidateQueryDomain; }
        public boolean hasCandidateQueryVariable() { return candidateQueryVariable; }
        public int getCandidateCVars() { return candidateCVars; }
        public int getCandidateQueryRoots() { return candidateQueryRoots; }
        public int getCandidateQueryLinkedRoots() {
            return candidateQueryLinkedRoots;
        }
        public boolean hasCandidateAllQueryRoots() {
            return candidateAllQueryRoots;
        }
        public boolean hasCandidateAllQueryLinkedRoots() {
            return candidateAllQueryLinkedRoots;
        }
        public String getCandidate() { return candidate; }
        public String getHypothesis() { return hypothesis; }
        public List<String> getBranchState() { return branchState; }
        public List<String> getSolve() { return solve; }
    }
}
