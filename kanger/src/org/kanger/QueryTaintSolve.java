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
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in shadow relevance carrier on the real deferred-solve provenance path.
 *
 * <p>Each Linker operation is identified by a query-local link epoch plus the
 * existing LinkerStatistics operation id. Cause observation decides whether
 * that concrete operation is query-derived. Later, the existing deferred
 * contribution telemetry transfers that bit to the exact canonical TValue
 * tuple produced by the operation. Relevance therefore travels with concrete
 * substitutions rather than with a reusable Domain or a branch wildcard.</p>
 *
 * <p>Generated Rules may inherit relevance only when their solve tuple matches
 * an already relevant deferred tuple. Fully-ground query matches that never
 * create a Cause are rescued by exact opposite-polarity Mind.usedDomains keys.
 * Normal inference never reads this state; the class is qualification-only.</p>
 *
 * <p>The hard acceptance invariant is
 * {@code exactRelevant subsetOf solveCandidates}. False positives are allowed;
 * false negatives are not.</p>
 */
public final class QueryTaintSolve {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final Long CVAR = Long.MIN_VALUE;

    private QueryTaintSolve() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Query solve taint is already active");
        }
        CURRENT.set(new Session());
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0,
                    Collections.<HypothesisKey>emptySet(),
                    Collections.<HypothesisKey>emptySet());
        }
        return new Snapshot(session.queryRoots,
                session.relevantOperations.size(),
                session.deferredContributions,
                session.relevantTuples.size(),
                session.relevantRuleIds.size(),
                session.groundBridges,
                session.relevantGround.size(),
                session.instrumentationErrors,
                session.observedHypotheses,
                session.taintedHypotheses);
    }

    /** LinkerStatistics.reset() boundary: operation ids restart at one here. */
    public static void beginLink() {
        Session session = CURRENT.get();
        if (session == null) {
            return;
        }
        ++session.linkEpoch;
        session.currentOperationId = 0L;
        session.relevantOperations.clear();
    }

    /** LinkerStatistics.incrementUnificationAttempts() boundary. */
    public static void beginOperation(long operationId) {
        Session session = CURRENT.get();
        if (session != null) {
            session.currentOperationId = operationId;
        }
    }

    /** Actual successful-substitution boundary. */
    public static void recordCause(Domain left, Domain right, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || left == null || right == null || mind == null) {
            return;
        }
        try {
            bootstrapGroundMatches(session, mind);
            if (session.currentOperationId <= 0L) {
                ++session.instrumentationErrors;
                return;
            }
            if (domainRelevant(session, left, mind)
                    || domainRelevant(session, right, mind)) {
                session.relevantOperations.add(new OperationKey(
                        session.linkEpoch, session.currentOperationId));
                markGround(session, left, mind);
                markGround(session, right, mind);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Existing SemanticEffectTelemetry deferred-contribution boundary. */
    public static void recordDeferredContribution(Collection<TValue> values,
                                                  long operationId) {
        Session session = CURRENT.get();
        if (session == null || values == null) {
            return;
        }
        try {
            ++session.deferredContributions;
            OperationKey operation = new OperationKey(session.linkEpoch, operationId);
            if (!session.relevantOperations.contains(operation)) {
                return;
            }
            SolveBinding binding = SolveBinding.of(values);
            if (!binding.isEmpty()) {
                session.relevantTuples.add(binding);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Existing generated-rule telemetry boundary. */
    public static void recordGeneratedRule(Object value) {
        Session session = CURRENT.get();
        if (session == null || !(value instanceof Rule)) {
            return;
        }
        try {
            Rule rule = (Rule) value;
            SolveBinding generated = SolveBinding.of(rule.getSolves());
            if (!generated.isEmpty() && tupleRelevant(session, generated)) {
                session.relevantRuleIds.add(rule.getId());
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Hypothesis construction remains the terminal shadow observation. */
    public static void recordHypothesis(Domain source, Mind mind,
                                        Hypothesis hypothesis) {
        Session session = CURRENT.get();
        if (session == null || source == null || mind == null || hypothesis == null) {
            return;
        }
        try {
            bootstrapGroundMatches(session, mind);
            HypothesisKey key = HypothesisKey.of(hypothesis, mind);
            session.observedHypotheses.add(key);
            if (domainRelevant(session, source, mind)) {
                session.taintedHypotheses.add(key);
                markGround(session, source, mind);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    private static boolean domainRelevant(Session session, Domain domain,
                                          Mind mind) throws Exception {
        if (isQuery(domain, mind)) {
            ++session.queryRoots;
            return true;
        }

        IRule owner = domain.getRule();
        if (owner != null && session.relevantRuleIds.contains(owner.getId())) {
            return true;
        }

        GroundKey ground = GroundKey.of(domain, domain.getArguments(), mind);
        if (ground != null && session.relevantGround.contains(ground)) {
            return true;
        }

        return currentRuleTupleRelevant(session, domain, mind);
    }

    /**
     * A relevant deferred tuple may enable a rule only if it touches at least
     * one variable in that rule and every pair for that rule equals the current
     * canonical TValue binding. There is no unresolved wildcard matching.
     */
    private static boolean currentRuleTupleRelevant(Session session,
                                                    Domain domain,
                                                    Mind mind) throws Exception {
        IRule owner = domain.getRule();
        if (!(owner instanceof Rule)) {
            return false;
        }

        Set<Long> ruleVariables = new LinkedHashSet<>();
        for (List<Domain> branch : ((Rule) owner).getTree()) {
            for (Domain candidate : branch) {
                for (TVariable variable : candidate.getArguments().getTVariables(mind)) {
                    ruleVariables.add(variable.getId());
                }
            }
        }
        if (ruleVariables.isEmpty()) {
            return false;
        }

        for (SolveBinding tuple : session.relevantTuples) {
            boolean touches = false;
            boolean compatible = true;
            for (Map.Entry<Long, Long> entry : tuple.bindings.entrySet()) {
                if (!ruleVariables.contains(entry.getKey())) {
                    continue;
                }
                touches = true;
                TVariable variable = mind.getTVars().get(entry.getKey());
                TValue current = variable == null ? null : variable.getCurrent();
                if (current == null || current.getId() != entry.getValue()) {
                    compatible = false;
                    break;
                }
            }
            if (touches && compatible) {
                return true;
            }
        }
        return false;
    }

    private static boolean tupleRelevant(Session session, SolveBinding candidate) {
        for (SolveBinding relevant : session.relevantTuples) {
            if (candidate.equals(relevant)) {
                return true;
            }
        }
        return false;
    }

    private static void markGround(Session session, Domain domain,
                                   Mind mind) throws Exception {
        GroundKey key = GroundKey.of(domain, domain.getArguments(), mind);
        if (key != null) {
            session.relevantGround.add(key);
        }
    }

    /** Exact ground rescue for successful matches that create no Cause. */
    private static void bootstrapGroundMatches(Session session, Mind mind) throws Exception {
        Map<Domain, Set<ArgumentsList>> used = mind.getUsedDomains();
        if (used.isEmpty()) {
            return;
        }

        for (Map.Entry<Domain, Set<ArgumentsList>> queryEntry : used.entrySet()) {
            Domain queryDomain = queryEntry.getKey();
            if (!isQuery(queryDomain, mind)) {
                continue;
            }
            for (ArgumentsList queryArguments : queryEntry.getValue()) {
                GroundKey queryGround = GroundKey.of(queryDomain, queryArguments, mind);
                if (queryGround != null) {
                    session.relevantGround.add(queryGround);
                }
                for (Map.Entry<Domain, Set<ArgumentsList>> otherEntry : used.entrySet()) {
                    Domain other = otherEntry.getKey();
                    if (other == queryDomain
                            || other.getPredicateId() != queryDomain.getPredicateId()
                            || other.isAntc() == queryDomain.isAntc()) {
                        continue;
                    }
                    for (ArgumentsList otherArguments : otherEntry.getValue()) {
                        if (queryArguments.equalsBase(mind, otherArguments)) {
                            GroundKey bridge = GroundKey.of(other, otherArguments, mind);
                            if (bridge != null && session.relevantGround.add(bridge)) {
                                ++session.groundBridges;
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isQuery(Domain domain, Mind mind) throws Exception {
        IRule rule = domain.getRule();
        return (rule != null && rule.isQuery()) || domain.isQuery(mind);
    }

    public static final class Snapshot {
        private final int queryRoots;
        private final int relevantOperations;
        private final int deferredContributions;
        private final int relevantTuples;
        private final int relevantRules;
        private final int groundBridges;
        private final int relevantGround;
        private final int instrumentationErrors;
        private final Set<HypothesisKey> observedHypotheses;
        private final Set<HypothesisKey> taintedHypotheses;

        private Snapshot(int queryRoots,
                         int relevantOperations,
                         int deferredContributions,
                         int relevantTuples,
                         int relevantRules,
                         int groundBridges,
                         int relevantGround,
                         int instrumentationErrors,
                         Set<HypothesisKey> observedHypotheses,
                         Set<HypothesisKey> taintedHypotheses) {
            this.queryRoots = queryRoots;
            this.relevantOperations = relevantOperations;
            this.deferredContributions = deferredContributions;
            this.relevantTuples = relevantTuples;
            this.relevantRules = relevantRules;
            this.groundBridges = groundBridges;
            this.relevantGround = relevantGround;
            this.instrumentationErrors = instrumentationErrors;
            this.observedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(observedHypotheses));
            this.taintedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(taintedHypotheses));
        }

        public int getQueryRootCount() { return queryRoots; }
        public int getRelevantOperationCount() { return relevantOperations; }
        public int getDeferredContributionCount() { return deferredContributions; }
        public int getRelevantTupleCount() { return relevantTuples; }
        public int getRelevantRuleCount() { return relevantRules; }
        public int getGroundBridgeCount() { return groundBridges; }
        public int getRelevantGroundCount() { return relevantGround; }
        public int getObservedHypothesisCount() { return observedHypotheses.size(); }
        public int getTaintedHypothesisCount() { return taintedHypotheses.size(); }
        public int getInstrumentationErrorCount() { return instrumentationErrors; }

        public List<IHypothesis> selectCandidates(Mind mind,
                                                   Collection<IHypothesis> hypotheses)
                throws Exception {
            List<IHypothesis> all = new ArrayList<>(hypotheses);
            if (instrumentationErrors != 0 || queryRoots == 0) {
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
        private long linkEpoch;
        private long currentOperationId;
        private int queryRoots;
        private int deferredContributions;
        private int groundBridges;
        private int instrumentationErrors;
        private final Set<OperationKey> relevantOperations = new LinkedHashSet<>();
        private final Set<SolveBinding> relevantTuples = new LinkedHashSet<>();
        private final Set<Long> relevantRuleIds = new LinkedHashSet<>();
        private final Set<GroundKey> relevantGround = new LinkedHashSet<>();
        private final Set<HypothesisKey> observedHypotheses = new LinkedHashSet<>();
        private final Set<HypothesisKey> taintedHypotheses = new LinkedHashSet<>();
    }

    private static final class OperationKey {
        private final long epoch;
        private final long operationId;

        private OperationKey(long epoch, long operationId) {
            this.epoch = epoch;
            this.operationId = operationId;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof OperationKey)) {
                return false;
            }
            OperationKey other = (OperationKey) value;
            return epoch == other.epoch && operationId == other.operationId;
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(epoch).hashCode();
            return 31 * result + Long.valueOf(operationId).hashCode();
        }
    }

    /** Exact canonical deferred substitution, keyed by TVariable -> TValue id. */
    private static final class SolveBinding {
        private final Map<Long, Long> bindings;

        private SolveBinding(Map<Long, Long> bindings) {
            this.bindings = Collections.unmodifiableMap(
                    new LinkedHashMap<>(bindings));
        }

        private static SolveBinding of(Collection<TValue> values) {
            Map<Long, Long> bindings = new HashMap<>();
            if (values != null) {
                for (TValue value : values) {
                    if (value != null) {
                        bindings.put(value.getTVarId(), value.getId());
                    }
                }
            }
            return new SolveBinding(bindings);
        }

        private boolean isEmpty() {
            return bindings.isEmpty();
        }

        @Override
        public boolean equals(Object value) {
            return value instanceof SolveBinding
                    && bindings.equals(((SolveBinding) value).bindings);
        }

        @Override
        public int hashCode() {
            return bindings.hashCode();
        }
    }

    private static final class GroundKey {
        private final long predicateId;
        private final boolean antc;
        private final List<Long> values;

        private GroundKey(long predicateId, boolean antc, List<Long> values) {
            this.predicateId = predicateId;
            this.antc = antc;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static GroundKey of(Domain domain, ArgumentsList arguments,
                                    Mind mind) throws Exception {
            List<Long> values = new ArrayList<>();
            for (IArgument argument : arguments) {
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
