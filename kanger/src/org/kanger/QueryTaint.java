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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in shadow experiment for forward query relevance propagation.
 *
 * <p>This class is deliberately not semantic authority. While enabled, actual
 * unifications report concrete Domain occurrences and relevance flows forward
 * through the current resolved substitution and through the terminal Rule
 * branch containing that occurrence. Hypothesis construction records whether
 * its source occurrence is query-derived. Normal inference never reads this
 * state, so enabling or disabling the experiment cannot change WHEN output.</p>
 *
 * <p>An occurrence is keyed by Domain identity plus its currently resolved
 * argument values. Unresolved positions are retained as wildcards representing
 * the still-open continuation of that concrete partial substitution; relevance
 * is therefore not stored as one boolean on the reusable Domain object.</p>
 *
 * <p>Historical ground matches may not create Cause/TSolve state. For that
 * path, the shadow session conservatively bootstraps exact opposite-polarity
 * matches from Mind.usedDomains, which is already query-local and stores
 * resolved ArgumentsList snapshots. Missing instrumentation evidence falls
 * back to the complete hypothesis list.</p>
 *
 * <p>The qualification invariant is
 * {@code exactRelevant subsetOf taintCandidates}. False positives are allowed;
 * false negatives are not.</p>
 */
public final class QueryTaint {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();
    private static final Long CVAR = Long.MIN_VALUE;

    private QueryTaint() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Query taint is already active");
        }
        CURRENT.set(new Session());
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(Collections.<OccurrencePattern>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    0, 0, 0);
        }
        return new Snapshot(session.queryRoots,
                session.observedHypotheses,
                session.taintedHypotheses,
                session.relevantUnifications,
                session.groundBridges,
                session.instrumentationErrors);
    }

    /** Internal no-op-unless-enabled hook from the actual Cause boundary. */
    public static void recordUnification(Domain left, Domain right, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || left == null || right == null || mind == null) {
            return;
        }
        try {
            bootstrapGroundMatches(session, mind);

            boolean leftQuery = isQuery(left, mind);
            boolean rightQuery = isQuery(right, mind);
            if (leftQuery) {
                markQuery(session, left, left.getArguments(), mind);
            }
            if (rightQuery) {
                markQuery(session, right, right.getArguments(), mind);
            }

            boolean relevant = leftQuery || rightQuery
                    || isTainted(session, left, left.getArguments(), mind)
                    || isTainted(session, right, right.getArguments(), mind)
                    || branchRelevant(session, left, mind)
                    || branchRelevant(session, right, mind);

            if (relevant) {
                ++session.relevantUnifications;
                taint(session, left, left.getArguments(), mind);
                taint(session, right, right.getArguments(), mind);
                taintBranch(session, left, mind);
                taintBranch(session, right, mind);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /**
     * Internal no-op-unless-enabled hook from the point where a terminal
     * Domain is converted into a concrete hypothesis alternative.
     */
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

            boolean query = isQuery(source, mind);
            if (query) {
                markQuery(session, source, source.getArguments(), mind);
            }
            boolean relevant = query
                    || isTainted(session, source, source.getArguments(), mind)
                    || branchRelevant(session, source, mind);
            if (relevant) {
                taintBranch(session, source, mind);
                session.taintedHypotheses.add(key);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    private static void markQuery(Session session, Domain domain,
                                  ArgumentsList arguments, Mind mind) throws Exception {
        OccurrencePattern occurrence = OccurrencePattern.of(domain, arguments, mind);
        session.queryRoots.add(occurrence);
        session.taintedOccurrences.add(occurrence);
    }

    private static void taint(Session session, Domain domain,
                              ArgumentsList arguments, Mind mind) throws Exception {
        session.taintedOccurrences.add(OccurrencePattern.of(domain, arguments, mind));
    }

    private static boolean isTainted(Session session, Domain domain,
                                     ArgumentsList arguments, Mind mind) throws Exception {
        OccurrencePattern current = OccurrencePattern.of(domain, arguments, mind);
        for (OccurrencePattern tainted : session.taintedOccurrences) {
            if (tainted.compatibleWith(current)) {
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

    private static boolean branchRelevant(Session session, Domain domain,
                                          Mind mind) throws Exception {
        for (Domain candidate : branchOf(domain)) {
            if (isQuery(candidate, mind)) {
                markQuery(session, candidate, candidate.getArguments(), mind);
                return true;
            }
            if (isTainted(session, candidate, candidate.getArguments(), mind)) {
                return true;
            }
        }
        return false;
    }

    private static void taintBranch(Session session, Domain domain,
                                    Mind mind) throws Exception {
        List<Domain> branch = branchOf(domain);
        if (branch.isEmpty()) {
            taint(session, domain, domain.getArguments(), mind);
            return;
        }
        for (Domain candidate : branch) {
            taint(session, candidate, candidate.getArguments(), mind);
        }
    }

    /**
     * Conservative rescue for successful fully-ground matches, which the
     * historical Linker marks used without creating a Cause. Only an exact
     * predicate/arguments match of opposite polarity is bridged.
     */
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
                markQuery(session, queryDomain, queryArguments, mind);
                for (Map.Entry<Domain, Set<ArgumentsList>> otherEntry : used.entrySet()) {
                    Domain other = otherEntry.getKey();
                    if (other == queryDomain
                            || other.getPredicateId() != queryDomain.getPredicateId()
                            || other.isAntc() == queryDomain.isAntc()) {
                        continue;
                    }
                    for (ArgumentsList otherArguments : otherEntry.getValue()) {
                        if (queryArguments.equalsBase(mind, otherArguments)) {
                            OccurrencePattern bridge = OccurrencePattern.of(
                                    other, otherArguments, mind);
                            if (session.taintedOccurrences.add(bridge)) {
                                ++session.groundBridges;
                            }
                        }
                    }
                }
            }
        }
    }

    public static final class Snapshot {
        private final Set<OccurrencePattern> queryRoots;
        private final Set<HypothesisKey> observedHypotheses;
        private final Set<HypothesisKey> taintedHypotheses;
        private final int relevantUnifications;
        private final int groundBridges;
        private final int instrumentationErrors;

        private Snapshot(Set<OccurrencePattern> queryRoots,
                         Set<HypothesisKey> observedHypotheses,
                         Set<HypothesisKey> taintedHypotheses,
                         int relevantUnifications,
                         int groundBridges,
                         int instrumentationErrors) {
            this.queryRoots = Collections.unmodifiableSet(
                    new LinkedHashSet<>(queryRoots));
            this.observedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(observedHypotheses));
            this.taintedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(taintedHypotheses));
            this.relevantUnifications = relevantUnifications;
            this.groundBridges = groundBridges;
            this.instrumentationErrors = instrumentationErrors;
        }

        public int getQueryRootCount() {
            return queryRoots.size();
        }

        public int getRelevantUnificationCount() {
            return relevantUnifications;
        }

        public int getGroundBridgeCount() {
            return groundBridges;
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

        /**
         * Select only hypotheses observed from tainted terminal occurrences.
         * If the query root or hypothesis-construction coverage is incomplete,
         * conservatively return the full list.
         */
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
        private final Set<OccurrencePattern> queryRoots = new LinkedHashSet<>();
        private final Set<OccurrencePattern> taintedOccurrences = new LinkedHashSet<>();
        private final Set<HypothesisKey> observedHypotheses = new LinkedHashSet<>();
        private final Set<HypothesisKey> taintedHypotheses = new LinkedHashSet<>();
        private int relevantUnifications;
        private int groundBridges;
        private int instrumentationErrors;
    }

    private static final class OccurrencePattern {
        private final long domainId;
        private final List<Long> values;

        private OccurrencePattern(long domainId, List<Long> values) {
            this.domainId = domainId;
            this.values = Collections.unmodifiableList(new ArrayList<>(values));
        }

        private static OccurrencePattern of(Domain domain, ArgumentsList arguments,
                                            Mind mind) throws Exception {
            List<Long> values = new ArrayList<>();
            for (IArgument argument : arguments) {
                if (argument.isEmpty(mind)) {
                    values.add(null);
                } else {
                    ITerm value = argument.getValue(mind);
                    values.add(value == null ? null : value.getId());
                }
            }
            return new OccurrencePattern(domain.getId(), values);
        }

        private boolean compatibleWith(OccurrencePattern other) {
            if (domainId != other.domainId || values.size() != other.values.size()) {
                return false;
            }
            for (int i = 0; i < values.size(); ++i) {
                Long left = values.get(i);
                Long right = other.values.get(i);
                if (left != null && right != null && !left.equals(right)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof OccurrencePattern)) {
                return false;
            }
            OccurrencePattern other = (OccurrencePattern) value;
            return domainId == other.domainId && values.equals(other.values);
        }

        @Override
        public int hashCode() {
            int result = (int) (domainId ^ (domainId >>> 32));
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
            int result = (int) (predicateId ^ (predicateId >>> 32));
            result = 31 * result + (antc ? 1 : 0);
            result = 31 * result + values.hashCode();
            return result;
        }
    }
}
