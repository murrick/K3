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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One-shot terminal-alternative closure over a deferred SOLVE relevance
 * shortlist.
 *
 * <p>This class deliberately does not participate in inference and does not
 * feed relevance back into QueryTaintSolve. Only hypotheses already selected
 * by the exact deferred-substitution carrier are seeds. If a seed can match a
 * Domain in one terminal Rule branch, hypotheses compatible with sibling
 * Domains of that same branch are admitted. Newly admitted siblings are never
 * used as new seeds, so the closure cannot become a graph fixed point.</p>
 *
 * <p>The purpose is narrowly semantic: singleton alternatives such as
 * {@code spouse(x,y) || divorced(x,y)} can both determine a query about one
 * member even though only one alternative lies on the concrete solve
 * provenance path. EXACT remains authority.</p>
 */
public final class QueryTaintSolveAlternatives {

    private QueryTaintSolveAlternatives() {
    }

    public static List<IHypothesis> expand(Mind mind,
                                           Collection<IHypothesis> allHypotheses,
                                           Collection<IHypothesis> solveCandidates)
            throws Exception {
        List<IHypothesis> all = new ArrayList<>(allHypotheses);
        List<IHypothesis> seeds = new ArrayList<>(solveCandidates);
        Set<IHypothesis> selected = new LinkedHashSet<>(seeds);

        if (seeds.isEmpty()) {
            return new ArrayList<>(selected);
        }

        for (IHypothesis seed : seeds) {
            for (IRule candidate : mind.getRules()) {
                if (candidate.isDeleted(mind) || !(candidate instanceof Rule)) {
                    continue;
                }
                Rule rule = (Rule) candidate;
                for (List<Domain> branch : rule.getTree()) {
                    if (branch.size() < 2 || !matchesAny(seed, branch, mind)) {
                        continue;
                    }
                    for (Domain sibling : branch) {
                        for (IHypothesis hypothesis : all) {
                            if (matches(hypothesis, sibling, mind)) {
                                selected.add(hypothesis);
                            }
                        }
                    }
                }
            }
        }
        return new ArrayList<>(selected);
    }

    private static boolean matchesAny(IHypothesis hypothesis,
                                      List<Domain> branch,
                                      Mind mind) throws Exception {
        for (Domain domain : branch) {
            if (matches(hypothesis, domain, mind)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(IHypothesis hypothesis,
                                   Domain pattern,
                                   Mind mind) throws Exception {
        if (hypothesis.getPredicate().getId() != pattern.getPredicateId()) {
            return false;
        }
        return argumentsCompatible(hypothesis.getArguments(),
                pattern.getArguments(), mind);
    }

    /**
     * Same conservative argument compatibility used by QueryDemandTrace:
     * variables and C-variables are wildcards; only explicit concrete mismatch
     * rejects a candidate. Polarity is intentionally not filtered because
     * Hypothesis construction historically inverts the source polarity.
     */
    private static boolean argumentsCompatible(IList hypothesis,
                                               ArgumentsList pattern,
                                               Mind mind) throws Exception {
        if (hypothesis.size() != pattern.size()) {
            return false;
        }
        for (int i = 0; i < pattern.size(); ++i) {
            IArgument expected = pattern.get(i);
            IArgument actual = (IArgument) hypothesis.get(i);
            if (expected.getType() == ArgumentType.TVARIABLE
                    || expected.isEmpty(mind)) {
                continue;
            }
            if (actual.isEmpty(mind)) {
                continue;
            }
            ITerm expectedValue = expected.getValue(mind);
            ITerm actualValue = actual.getValue(mind);
            if (expectedValue == null || actualValue == null
                    || expectedValue.isCVariable() || actualValue.isCVariable()) {
                continue;
            }
            if (expectedValue.getId() != actualValue.getId()) {
                return false;
            }
        }
        return true;
    }
}
