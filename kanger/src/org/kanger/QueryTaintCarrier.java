/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TSolve;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Second opt-in shadow experiment for forward query relevance.
 *
 * <p>Unlike {@link QueryTaint}, this implementation never treats an unresolved
 * Domain argument as a wildcard that can taint later substitutions. Variable
 * relevance is carried by the concrete deferred substitution and, after it is
 * materialized, by the identity of the resulting query-local {@link TSolve}.
 * Fully resolved ground propagation is keyed structurally by predicate,
 * polarity and exact term ids.</p>
 *
 * <p>The carrier is diagnostic only. Production inference never reads the
 * selected hypothesis set and normal TSolve equality/persistence is unchanged.
 * The qualification invariant remains
 * {@code exactRelevant subsetOf carrierCandidates}.</p>
 */
public final class QueryTaintCarrier {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final Long CVAR = Long.MIN_VALUE;

    private QueryTaintCarrier() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Query taint carrier is already active");
        }
        CURRENT.set(new Session());
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(Collections.<Long>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    0, 0, 0, 0, 0);
        }
        return new Snapshot(session.queryRoots,
                session.observedHypotheses,
                session.taintedHypotheses,
                session.relevantUnifications,
                session.solveObservations,
                session.relevantSolveAdds,
                session.relevantTerminalBranches,
                session.instrumentationErrors);
    }

    /**
     * Linker lifecycle hook. TSolve tuples are query-local to one link() run,
     * therefore carrier identities and operation ids must not leak into the
     * next run. Exact ground semantic keys may survive because generated facts
     * can be consumed by a subsequent link() within the same query session.
     */
    public static void beginLink() {
        Session session = CURRENT.get();
        if (session == null) {
            return;
        }
        session.relevantSolves.clear();
        session.relevantOperations.clear();
    }

    /** Records whether one concrete unification operation is query-derived. */
    public static void recordUnification(long operationId,
                                         Domain left,
                                         Domain right,
                                         Mind mind) {
        Session session = CURRENT.get();
        if (session == null || left == null || right == null || mind == null) {
            return;
        }
        try {
            boolean relevant = branchRelevant(session, left, mind)
                    || branchRelevant(session, right, mind);
            if (relevant) {
                if (session.relevantOperations.add(operationId)) {
                    ++session.relevantUnifications;
                }
                markBranchGround(session, left, mind);
                markBranchGround(session, right, mind);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    public static boolean isOperationRelevant(long operationId) {
        Session session = CURRENT.get();
        return session != null && session.relevantOperations.contains(operationId);
    }

    /**
     * Transfers candidate relevance to the exact TSolve object returned by
     * Mind.addTSolve(). Duplicate tuples therefore naturally OR relevance on
     * the canonical query-local tuple identity without changing TSolve itself.
     */
    public static void recordSolve(TSolve solve, boolean relevant) {
        Session session = CURRENT.get();
        if (session == null || solve == null) {
            return;
        }
        ++session.solveObservations;
        if (relevant && session.relevantSolves.add(solve)) {
            ++session.relevantSolveAdds;
        }
    }

    /** Concrete terminal-branch closure for the current rotation only. */
    public static void recordTerminalBranch(List<Domain> tree, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || tree == null || mind == null) {
            return;
        }
        try {
            boolean relevant = false;
            for (Domain domain : tree) {
                if (domainRelevant(session, domain, mind)) {
                    relevant = true;
                    break;
                }
            }
            if (relevant) {
                ++session.relevantTerminalBranches;
                for (Domain domain : tree) {
                    GroundKey key = GroundKey.of(domain, mind);
                    if (key != null) {
                        session.relevantGround.add(key);
                    }
                }
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Hypothesis-construction hook; selection remains shadow-only. */
    public static void recordHypothesis(Domain source, Mind mind,
                                        Hypothesis hypothesis) {
        Session session = CURRENT.get();
        if (session == null || source == null || mind == null || hypothesis == null) {
            return;
        }
        try {
            HypothesisKey key = HypothesisKey.of(hypothesis, mind);
            session.observedHypotheses.add(key);
            if (branchRelevant(session, source, mind)) {
                markBranchGround(session, source, mind);
                session.taintedHypotheses.add(key);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Used by system-predicate TSolve publication at the same rotation. */
    public static boolean isBranchRelevant(List<Domain> tree, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || tree == null || mind == null) {
            return false;
        }
        try {
            for (Domain domain : tree) {
                if (domainRelevant(session, domain, mind)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
        return false;
    }

    private static boolean branchRelevant(Session session, Domain domain,
                                          Mind mind) throws Exception {
        List<Domain> branch = branchOf(domain);
        if (branch.isEmpty()) {
            return domainRelevant(session, domain, mind);
        }
        for (Domain candidate : branch) {
            if (domainRelevant(session, candidate, mind)) {
                return true;
            }
        }
        return false;
    }

    private static boolean domainRelevant(Session session, Domain domain,
                                          Mind mind) throws Exception {
        if (isQuery(domain, mind)) {
            session.queryRoots.add(domain.getId());
            return true;
        }
        GroundKey ground = GroundKey.of(domain, mind);
        if (ground != null && session.relevantGround.contains(ground)) {
            return true;
        }
        return hasCompatibleRelevantSolve(session, domain, mind);
    }

    /**
     * A relevant tuple may taint a Domain only when that tuple belongs to the
     * Domain's Rule and every value carried for that Rule equals the current
     * concrete rotation binding. No unresolved/wildcard compatibility exists.
     */
    private static boolean hasCompatibleRelevantSolve(Session session,
                                                       Domain domain,
                                                       Mind mind) throws Exception {
        long ruleId = domain.getRuleId();
        for (TSolve solve : session.relevantSolves) {
            boolean touchesRule = false;
            boolean compatible = true;
            for (TValue value : solve.getSolve()) {
                TVariable variable = value.getTVar(mind);
                IRule owner = variable.getRule(mind);
                if (owner != null && owner.getId() == ruleId) {
                    touchesRule = true;
                    TValue current = variable.getCurrent();
                    if (current == null || current.getId() != value.getId()) {
                        compatible = false;
                        break;
                    }
                }
            }
            if (touchesRule && compatible) {
                return true;
            }
        }
        return false;
    }

    private static boolean isQuery(Domain domain, Mind mind) throws Exception {
        IRule rule = domain.getRule();
        return (rule != null && rule.isQuery()) || domain.isQuery(mind);
    }

    private static List<Domain> branchOf(Domain domain) throws Exception {
        IRule owner = domain.getRule();
        if (!(owner instanceof Rule)) {
            return Collections.emptyList();
        }
        for (List<Domain> branch : ((Rule) owner).getTree()) {
            for (Domain candidate : branch) {
                if (candidate == domain || candidate.getId() == domain.getId()) {
                    return branch;
                }
            }
        }
        return Collections.emptyList();
    }

    private static void markBranchGround(Session session, Domain domain,
                                         Mind mind) throws Exception {
        List<Domain> branch = branchOf(domain);
        if (branch.isEmpty()) {
            GroundKey key = GroundKey.of(domain, mind);
            if (key != null) {
                session.relevantGround.add(key);
            }
            return;
        }
        for (Domain candidate : branch) {
            GroundKey key = GroundKey.of(candidate, mind);
            if (key != null) {
                session.relevantGround.add(key);
            }
        }
    }

    public static final class Snapshot {
        private final Set<Long> queryRoots;
        private final Set<HypothesisKey> observedHypotheses;
        private final Set<HypothesisKey> taintedHypotheses;
        private final int relevantUnifications;
        private final int solveObservations;
        private final int relevantSolveAdds;
        private final int relevantTerminalBranches;
        private final int instrumentationErrors;

        private Snapshot(Set<Long> queryRoots,
                         Set<HypothesisKey> observedHypotheses,
                         Set<HypothesisKey> taintedHypotheses,
                         int relevantUnifications,
                         int solveObservations,
                         int relevantSolveAdds,
                         int relevantTerminalBranches,
                         int instrumentationErrors) {
            this.queryRoots = Collections.unmodifiableSet(new LinkedHashSet<>(queryRoots));
            this.observedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(observedHypotheses));
            this.taintedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(taintedHypotheses));
            this.relevantUnifications = relevantUnifications;
            this.solveObservations = solveObservations;
            this.relevantSolveAdds = relevantSolveAdds;
            this.relevantTerminalBranches = relevantTerminalBranches;
            this.instrumentationErrors = instrumentationErrors;
        }

        public int getQueryRootCount() {
            return queryRoots.size();
        }

        public int getRelevantUnificationCount() {
            return relevantUnifications;
        }

        public int getSolveObservationCount() {
            return solveObservations;
        }

        public int getRelevantSolveAddCount() {
            return relevantSolveAdds;
        }

        public int getRelevantTerminalBranchCount() {
            return relevantTerminalBranches;
        }

        public int getObservedHypothesisCount() {
            return observedHypotheses.size();
        }

        public int getTaintedHypothesisCount() {
            return taintedHypotheses.size();
        }

        public int getInstrumentationErrorCount() {
            return instrumentationErrors;
        }

        public List<IHypothesis> selectCandidates(Mind mind,
                                                   Collection<IHypothesis> hypotheses)
                throws Exception {
            List<IHypothesis> all = new ArrayList<>(hypotheses);
            if (instrumentationErrors != 0 || queryRoots.isEmpty()) {
                return all;
            }

            List<HypothesisKey> keys = new ArrayList<>();
            for (IHypothesis hypothesis : all) {
                HypothesisKey key = HypothesisKey.of(hypothesis, mind);
                keys.add(key);
                if (!observedHypotheses.contains(key)) {
                    return all;
                }
            }

            List<IHypothesis> selected = new ArrayList<>();
            for (int i = 0; i < all.size(); ++i) {
                if (taintedHypotheses.contains(keys.get(i))) {
                    selected.add(all.get(i));
                }
            }
            return selected;
        }
    }

    private static final class Session {
        private final Set<Long> queryRoots = new LinkedHashSet<>();
        private final Set<TSolve> relevantSolves = Collections.newSetFromMap(
                new IdentityHashMap<TSolve, Boolean>());
        private final Set<Long> relevantOperations = new LinkedHashSet<>();
        private final Set<GroundKey> relevantGround = new LinkedHashSet<>();
        private final Set<HypothesisKey> observedHypotheses = new LinkedHashSet<>();
        private final Set<HypothesisKey> taintedHypotheses = new LinkedHashSet<>();
        private int relevantUnifications;
        private int solveObservations;
        private int relevantSolveAdds;
        private int relevantTerminalBranches;
        private int instrumentationErrors;
    }

    /** Exact fully-resolved semantic occurrence; no wildcard positions. */
    private static final class GroundKey {
        private final long predicateId;
        private final boolean antc;
        private final List<Long> values;

        private GroundKey(long predicateId, boolean antc, List<Long> values) {
            this.predicateId = predicateId;
            this.antc = antc;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static GroundKey of(Domain domain, Mind mind) throws Exception {
            List<Long> values = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                if (argument.isEmpty(mind)) {
                    return null;
                }
                ITerm value = argument.getValue(mind);
                if (value == null || value.isCVariable()) {
                    return null;
                }
                values.add(value.getId());
            }
            return new GroundKey(domain.getPredicateId(), domain.isAntc(), values);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof GroundKey)) {
                return false;
            }
            GroundKey other = (GroundKey) value;
            return predicateId == other.predicateId
                    && antc == other.antc
                    && values.equals(other.values);
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(predicateId).hashCode();
            result = 31 * result + (antc ? 1 : 0);
            result = 31 * result + values.hashCode();
            return result;
        }
    }

    private static final class HypothesisKey {
        private final long predicateId;
        private final boolean antc;
        private final List<Long> values;

        private HypothesisKey(long predicateId, boolean antc, List<Long> values) {
            this.predicateId = predicateId;
            this.antc = antc;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static HypothesisKey of(IHypothesis hypothesis, Mind mind)
                throws Exception {
            List<Long> values = new ArrayList<>();
            for (int i = 0; i < hypothesis.getArguments().size(); ++i) {
                IArgument argument = (IArgument) hypothesis.getArguments().get(i);
                if (argument.isEmpty(mind)) {
                    values.add(null);
                } else {
                    ITerm value = argument.getValue(mind);
                    if (value == null) {
                        values.add(null);
                    } else if (value.isCVariable()) {
                        values.add(CVAR + i);
                    } else {
                        values.add(value.getId());
                    }
                }
            }
            return new HypothesisKey(hypothesis.getPredicate().getId(),
                    hypothesis.isAntc(), values);
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof HypothesisKey)) {
                return false;
            }
            HypothesisKey other = (HypothesisKey) value;
            return predicateId == other.predicateId
                    && antc == other.antc
                    && values.equals(other.values);
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(predicateId).hashCode();
            result = 31 * result + (antc ? 1 : 0);
            result = 31 * result + values.hashCode();
            return result;
        }
    }
}
