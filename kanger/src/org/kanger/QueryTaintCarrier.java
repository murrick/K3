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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Opt-in shadow experiment that carries query relevance on the concrete
 * substitution observed at an actual Cause boundary.
 *
 * <p>Unlike {@link QueryTaint}, unresolved Domain positions are never wildcard
 * matches. A variable-bearing branch is represented by its Rule/branch identity
 * plus the concrete {@code TVariable id -> TValue id} bindings that exist at the
 * unification boundary. A later binding is relevant only when it extends one of
 * those exact partial substitutions. Fully resolved propagation uses an exact
 * predicate/polarity/term tuple.</p>
 *
 * <p>This is diagnostic state only. Production inference never reads the
 * selected hypothesis set. If Cause-level observation proves insufficient, the
 * next experiment can move the same bit into DeferredSolveCandidate/TSolve
 * without changing the semantic oracle.</p>
 *
 * <p>The qualification invariant remains
 * {@code exactRelevant subsetOf carrierCandidates}; false positives are
 * acceptable and false negatives are forbidden.</p>
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
            return new Snapshot(Collections.<BranchId>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    Collections.<HypothesisKey>emptySet(),
                    0, 0, 0, 0, 0);
        }
        return new Snapshot(session.queryRoots,
                session.observedHypotheses,
                session.taintedHypotheses,
                session.relevantCauses,
                session.groundBridges,
                session.relevantBindings.size(),
                session.relevantGround.size(),
                session.instrumentationErrors);
    }

    /** Internal no-op-unless-enabled hook from the actual Cause boundary. */
    public static void recordCause(Domain left, Domain right, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || left == null || right == null || mind == null) {
            return;
        }
        try {
            bootstrapGroundMatches(session, mind);
            boolean relevant = branchRelevant(session, left, mind)
                    || branchRelevant(session, right, mind);
            if (relevant) {
                ++session.relevantCauses;
                markBranch(session, left, mind);
                markBranch(session, right, mind);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    /** Internal no-op-unless-enabled hook from hypothesis construction. */
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
            if (branchRelevant(session, source, mind)) {
                markBranch(session, source, mind);
                session.taintedHypotheses.add(key);
            }
        } catch (Exception ignored) {
            ++session.instrumentationErrors;
        }
    }

    private static boolean branchRelevant(Session session, Domain domain,
                                          Mind mind) throws Exception {
        BranchRef branch = branchOf(domain);
        if (branch == null) {
            if (isQuery(domain, mind)) {
                return true;
            }
            GroundKey ground = GroundKey.of(domain, domain.getArguments(), mind);
            return ground != null && session.relevantGround.contains(ground);
        }

        for (Domain candidate : branch.domains) {
            if (isQuery(candidate, mind)) {
                session.queryRoots.add(branch.id);
                return true;
            }
            GroundKey ground = GroundKey.of(candidate, candidate.getArguments(), mind);
            if (ground != null && session.relevantGround.contains(ground)) {
                return true;
            }
        }

        BindingKey current = BindingKey.of(branch, mind);
        if (current != null) {
            for (BindingKey relevant : session.relevantBindings) {
                if (current.extendsBinding(relevant)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void markBranch(Session session, Domain domain,
                                   Mind mind) throws Exception {
        BranchRef branch = branchOf(domain);
        if (branch == null) {
            GroundKey ground = GroundKey.of(domain, domain.getArguments(), mind);
            if (ground != null) {
                session.relevantGround.add(ground);
            }
            return;
        }

        BindingKey binding = BindingKey.of(branch, mind);
        if (binding != null) {
            session.relevantBindings.add(binding);
        }
        for (Domain candidate : branch.domains) {
            GroundKey ground = GroundKey.of(candidate, candidate.getArguments(), mind);
            if (ground != null) {
                session.relevantGround.add(ground);
            }
            if (isQuery(candidate, mind)) {
                session.queryRoots.add(branch.id);
            }
        }
    }

    /**
     * Ground matches can be successful without creating a Cause. Recover only
     * exact opposite-polarity matches already recorded by Mind.usedDomains.
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
            BranchRef queryBranch = branchOf(queryDomain);
            if (queryBranch != null) {
                session.queryRoots.add(queryBranch.id);
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
                            GroundKey otherGround = GroundKey.of(other, otherArguments, mind);
                            if (otherGround != null
                                    && session.relevantGround.add(otherGround)) {
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

    private static BranchRef branchOf(Domain domain) throws Exception {
        IRule owner = domain.getRule();
        if (!(owner instanceof Rule)) {
            return null;
        }
        List<List<Domain>> tree = ((Rule) owner).getTree();
        for (int i = 0; i < tree.size(); ++i) {
            List<Domain> branch = tree.get(i);
            for (Domain candidate : branch) {
                if (candidate == domain || candidate.getId() == domain.getId()) {
                    return new BranchRef(new BranchId(owner.getId(), i), branch);
                }
            }
        }
        return null;
    }

    public static final class Snapshot {
        private final Set<BranchId> queryRoots;
        private final Set<HypothesisKey> observedHypotheses;
        private final Set<HypothesisKey> taintedHypotheses;
        private final int relevantCauses;
        private final int groundBridges;
        private final int relevantBindings;
        private final int relevantGround;
        private final int instrumentationErrors;

        private Snapshot(Set<BranchId> queryRoots,
                         Set<HypothesisKey> observedHypotheses,
                         Set<HypothesisKey> taintedHypotheses,
                         int relevantCauses,
                         int groundBridges,
                         int relevantBindings,
                         int relevantGround,
                         int instrumentationErrors) {
            this.queryRoots = Collections.unmodifiableSet(new LinkedHashSet<>(queryRoots));
            this.observedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(observedHypotheses));
            this.taintedHypotheses = Collections.unmodifiableSet(
                    new LinkedHashSet<>(taintedHypotheses));
            this.relevantCauses = relevantCauses;
            this.groundBridges = groundBridges;
            this.relevantBindings = relevantBindings;
            this.relevantGround = relevantGround;
            this.instrumentationErrors = instrumentationErrors;
        }

        public int getQueryRootCount() {
            return queryRoots.size();
        }

        public int getRelevantCauseCount() {
            return relevantCauses;
        }

        public int getGroundBridgeCount() {
            return groundBridges;
        }

        public int getRelevantBindingCount() {
            return relevantBindings;
        }

        public int getRelevantGroundCount() {
            return relevantGround;
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
        private final Set<BranchId> queryRoots = new LinkedHashSet<>();
        private final Set<BindingKey> relevantBindings = new LinkedHashSet<>();
        private final Set<GroundKey> relevantGround = new LinkedHashSet<>();
        private final Set<HypothesisKey> observedHypotheses = new LinkedHashSet<>();
        private final Set<HypothesisKey> taintedHypotheses = new LinkedHashSet<>();
        private int relevantCauses;
        private int groundBridges;
        private int instrumentationErrors;
    }

    private static final class BranchRef {
        private final BranchId id;
        private final List<Domain> domains;

        private BranchRef(BranchId id, List<Domain> domains) {
            this.id = id;
            this.domains = domains;
        }
    }

    private static final class BranchId {
        private final long ruleId;
        private final int branchIndex;

        private BranchId(long ruleId, int branchIndex) {
            this.ruleId = ruleId;
            this.branchIndex = branchIndex;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof BranchId)) {
                return false;
            }
            BranchId other = (BranchId) value;
            return ruleId == other.ruleId && branchIndex == other.branchIndex;
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(ruleId).hashCode();
            return 31 * result + branchIndex;
        }
    }

    /**
     * Exact partial substitution for one logical Rule branch. Compatibility is
     * one-way: the current binding may extend a relevant binding but may never
     * disagree with a value already carried by it.
     */
    private static final class BindingKey {
        private final BranchId branch;
        private final List<Long> variableIds;
        private final List<Long> valueIds;

        private BindingKey(BranchId branch,
                           List<Long> variableIds,
                           List<Long> valueIds) {
            this.branch = branch;
            this.variableIds = Collections.unmodifiableList(new ArrayList<>(variableIds));
            this.valueIds = Collections.unmodifiableList(new ArrayList<>(valueIds));
        }

        private static BindingKey of(BranchRef branch, Mind mind) throws Exception {
            Set<TVariable> variables = new TreeSet<>();
            for (Domain domain : branch.domains) {
                variables.addAll(domain.getArguments().getTVariables(mind));
            }

            List<Long> variableIds = new ArrayList<>();
            List<Long> valueIds = new ArrayList<>();
            for (TVariable variable : variables) {
                TValue current = variable.getCurrent();
                if (current != null) {
                    variableIds.add(variable.getId());
                    valueIds.add(current.getId());
                }
            }
            if (variableIds.isEmpty()) {
                return null;
            }
            return new BindingKey(branch.id, variableIds, valueIds);
        }

        private boolean extendsBinding(BindingKey relevant) {
            if (!branch.equals(relevant.branch)) {
                return false;
            }
            for (int i = 0; i < relevant.variableIds.size(); ++i) {
                Long variableId = relevant.variableIds.get(i);
                int currentIndex = variableIds.indexOf(variableId);
                if (currentIndex < 0
                        || !valueIds.get(currentIndex).equals(relevant.valueIds.get(i))) {
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
            if (!(value instanceof BindingKey)) {
                return false;
            }
            BindingKey other = (BindingKey) value;
            return branch.equals(other.branch)
                    && variableIds.equals(other.variableIds)
                    && valueIds.equals(other.valueIds);
        }

        @Override
        public int hashCode() {
            int result = branch.hashCode();
            result = 31 * result + variableIds.hashCode();
            result = 31 * result + valueIds.hashCode();
            return result;
        }
    }

    /** Exact fully resolved semantic occurrence; no wildcard positions. */
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
