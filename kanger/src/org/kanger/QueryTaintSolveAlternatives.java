/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.IList;
import org.kanger.primitives.ArgumentsList;
import org.kanger.units.Domain;
import org.kanger.units.Rule;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-shot terminal-alternative closure over a deferred SOLVE relevance
 * shortlist and the original query demand.
 *
 * <p>This class deliberately does not participate in inference and does not
 * feed relevance back into QueryTaintSolve. Raw SOLVE candidates and compiled
 * query Domains are seeds. A seed may project its concrete argument bindings
 * onto one stored Rule Domain; only same-polarity terminal siblings in that
 * exact branch are then admitted. Newly admitted hypotheses are never used as
 * seeds, so the closure cannot become a graph fixed point.</p>
 *
 * <p>The query seed is required for cases where raw SOLVE is empty but a
 * sibling terminal alternative can itself determine the query, e.g.
 * {@code spouse(John,x)} versus {@code divorced(John,Mary)}. Concrete query
 * bindings are propagated through shared TVariable ids, so a fixed argument
 * such as {@code John} is retained across the sibling projection.</p>
 *
 * <p>EXACT remains semantic authority.</p>
 */
public final class QueryTaintSolveAlternatives {

    private QueryTaintSolveAlternatives() {
    }

    public static List<IHypothesis> expand(Mind mind,
                                           Collection<IHypothesis> allHypotheses,
                                           Collection<IHypothesis> solveCandidates)
            throws Exception {
        return expand(mind, null, allHypotheses, solveCandidates);
    }

    public static List<IHypothesis> expand(Mind mind,
                                           String query,
                                           Collection<IHypothesis> allHypotheses,
                                           Collection<IHypothesis> solveCandidates)
            throws Exception {
        List<IHypothesis> all = new ArrayList<>(allHypotheses);
        List<IHypothesis> seeds = new ArrayList<>(solveCandidates);
        Set<IHypothesis> selected = new LinkedHashSet<>(seeds);
        List<QueryPattern> querySeeds = query == null
                ? new ArrayList<QueryPattern>()
                : compileQueryPatterns(mind, query);

        for (IRule candidate : mind.getRules()) {
            if (candidate.isDeleted(mind) || !(candidate instanceof Rule)) {
                continue;
            }
            Rule rule = (Rule) candidate;
            for (List<Domain> branch : rule.getTree()) {
                if (branch.size() < 2) {
                    continue;
                }
                for (Domain target : branch) {
                    for (IHypothesis seed : seeds) {
                        Map<Long, Long> bindings = project(seed, target, mind);
                        if (bindings != null) {
                            expandTerminal(selected, all, branch, target,
                                    bindings, mind);
                        }
                    }
                    for (QueryPattern querySeed : querySeeds) {
                        Map<Long, Long> bindings = project(querySeed, target, mind);
                        if (bindings != null) {
                            expandTerminal(selected, all, branch, target,
                                    bindings, mind);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private static void expandTerminal(Set<IHypothesis> selected,
                                       List<IHypothesis> all,
                                       List<Domain> branch,
                                       Domain target,
                                       Map<Long, Long> bindings,
                                       Mind mind) throws Exception {
        for (Domain sibling : branch) {
            if (sibling.isAntc() != target.isAntc()) {
                continue;
            }
            for (IHypothesis hypothesis : all) {
                if (matchesProjected(hypothesis, sibling, bindings, mind)) {
                    selected.add(hypothesis);
                }
            }
        }
    }

    /**
     * Project concrete hypothesis arguments onto TVariables of the matched
     * stored Domain. Null means predicate/explicit-constant mismatch.
     */
    private static Map<Long, Long> project(IHypothesis hypothesis,
                                           Domain pattern,
                                           Mind mind) throws Exception {
        if (hypothesis.getPredicate().getId() != pattern.getPredicateId()
                || hypothesis.getArguments().size() != pattern.getArguments().size()) {
            return null;
        }
        Map<Long, Long> bindings = new LinkedHashMap<>();
        for (int i = 0; i < pattern.getArguments().size(); ++i) {
            IArgument source = (IArgument) hypothesis.getArguments().get(i);
            Long valueId = concreteValueId(source, mind);
            if (!projectValue(pattern.getArguments().get(i), valueId,
                    bindings, mind)) {
                return null;
            }
        }
        return bindings;
    }

    /** Project the original query pattern onto one stored Rule Domain. */
    private static Map<Long, Long> project(QueryPattern query,
                                           Domain pattern,
                                           Mind mind) throws Exception {
        if (query.predicateId != pattern.getPredicateId()
                || query.values.size() != pattern.getArguments().size()) {
            return null;
        }
        Map<Long, Long> bindings = new LinkedHashMap<>();
        for (int i = 0; i < query.values.size(); ++i) {
            if (!projectValue(pattern.getArguments().get(i), query.values.get(i),
                    bindings, mind)) {
                return null;
            }
        }
        return bindings;
    }

    /**
     * Apply one concrete seed value to a stored pattern position. Unknown seed
     * values are unconstrained. Unknown stored positions are accepted
     * conservatively, while explicit concrete mismatch rejects the projection.
     */
    private static boolean projectValue(IArgument pattern,
                                        Long valueId,
                                        Map<Long, Long> bindings,
                                        Mind mind) throws Exception {
        if (valueId == null) {
            return true;
        }
        if (pattern.getType() == ArgumentType.TVARIABLE) {
            TVariable variable = (TVariable) pattern.getObject(mind);
            Long previous = bindings.put(variable.getId(), valueId);
            return previous == null || previous.equals(valueId);
        }
        if (pattern.isEmpty(mind)) {
            return true;
        }
        ITerm expected = pattern.getValue(mind);
        return expected == null || expected.isCVariable() || expected.getId() == valueId;
    }

    /** Match a hypothesis against a sibling after shared-variable projection. */
    private static boolean matchesProjected(IHypothesis hypothesis,
                                            Domain pattern,
                                            Map<Long, Long> bindings,
                                            Mind mind) throws Exception {
        if (hypothesis.getPredicate().getId() != pattern.getPredicateId()
                || hypothesis.getArguments().size() != pattern.getArguments().size()) {
            return false;
        }
        for (int i = 0; i < pattern.getArguments().size(); ++i) {
            IArgument expected = pattern.getArguments().get(i);
            IArgument actual = (IArgument) hypothesis.getArguments().get(i);

            if (expected.getType() == ArgumentType.TVARIABLE) {
                TVariable variable = (TVariable) expected.getObject(mind);
                Long bound = bindings.get(variable.getId());
                if (bound == null) {
                    continue;
                }
                Long actualId = concreteValueId(actual, mind);
                if (actualId != null && !bound.equals(actualId)) {
                    return false;
                }
                continue;
            }

            if (expected.isEmpty(mind)) {
                continue;
            }
            ITerm expectedValue = expected.getValue(mind);
            if (expectedValue == null || expectedValue.isCVariable()) {
                continue;
            }
            Long actualId = concreteValueId(actual, mind);
            if (actualId != null && expectedValue.getId() != actualId) {
                return false;
            }
        }
        return true;
    }

    /** Null means wildcard/C-variable/unresolved. */
    private static Long concreteValueId(IArgument argument, Mind mind) throws Exception {
        if (argument == null || argument.getType() == ArgumentType.TVARIABLE
                || argument.isEmpty(mind)) {
            return null;
        }
        ITerm value = argument.getValue(mind);
        if (value == null || value.isCVariable()) {
            return null;
        }
        return value.getId();
    }

    /** Compile the query only to obtain immutable predicate/argument patterns. */
    private static List<QueryPattern> compileQueryPatterns(Mind base, String query)
            throws Exception {
        List<QueryPattern> result = new ArrayList<>();
        Mind child = new Mind(base);
        try {
            Rule rule = (Rule) child.compileLine(query, false, null);
            if (rule == null) {
                return result;
            }
            for (List<Domain> branch : rule.getTree()) {
                for (Domain domain : branch) {
                    result.add(QueryPattern.of(domain, child));
                }
            }
            if (result.isEmpty() && rule.getDomain() != null) {
                result.add(QueryPattern.of(rule.getDomain(), child));
            }
        } finally {
            base.release(child);
        }
        return result;
    }

    private static final class QueryPattern {
        private final long predicateId;
        private final List<Long> values;

        private QueryPattern(long predicateId, List<Long> values) {
            this.predicateId = predicateId;
            this.values = values;
        }

        private static QueryPattern of(Domain domain, Mind mind) throws Exception {
            List<Long> values = new ArrayList<>();
            for (IArgument argument : domain.getArguments()) {
                values.add(concreteValueId(argument, mind));
            }
            return new QueryPattern(domain.getPredicateId(), values);
        }
    }
}
