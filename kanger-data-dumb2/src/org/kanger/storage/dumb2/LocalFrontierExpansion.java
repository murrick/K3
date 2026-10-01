/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.interfaces.IRule;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reduces an Analyzer frontier through local X inference only.
 *
 * <p>The operation Mind already contains the generated demand Rules created by
 * Linker (for example {@code ?gate(Tom)}). Recompiling the same query in a
 * child is semantically wrong because it becomes a duplicate/second Rule and
 * enters the normal FALSE/TRUE query protocol. Instead this helper finds the
 * existing generated demand in an isolated probe and lets Linker expand that
 * exact demand through X-local knowledge.</p>
 *
 * <p>No ConnectionVector or foreign Context is consulted here. A demand with
 * no deeper local dependency remains an external frontier leaf.</p>
 */
final class LocalFrontierExpansion {

    private static final int MAX_DEPTH = 64;

    private LocalFrontierExpansion() {
    }

    static List<FrontierDomain> expand(
            Mind operationMind,
            List<FrontierDomain> source) throws Exception {
        if (operationMind == null) {
            throw new NullPointerException("operationMind");
        }
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }

        List<FrontierDomain> result =
                new ArrayList<FrontierDomain>();
        for (FrontierDomain frontier : source) {
            Mind probe =
                    Mind.ephemeralChild(operationMind);
            try {
                expandInProbe(
                        operationMind,
                        probe,
                        frontier,
                        result,
                        new ArrayList<FrontierDomain>(),
                        0);
            } finally {
                probe.getSolutions().clear();
                probe.getValues().clear();
                operationMind.release(probe);
            }
        }
        return result;
    }

    private static void expandInProbe(
            Mind operationMind,
            Mind probe,
            FrontierDomain frontier,
            List<FrontierDomain> result,
            List<FrontierDomain> trail,
            int depth) throws Exception {
        if (depth >= MAX_DEPTH
                || containsEquivalent(trail, frontier)) {
            addUnique(result, frontier);
            return;
        }

        Rule demand =
                findGeneratedDemand(
                        operationMind,
                        probe,
                        frontier);
        if (demand == null) {
            addUnique(result, frontier);
            return;
        }

        probe.link(demand, false);
        boolean locallyResolved =
                probe.analyze(demand, false);
        if (locallyResolved) {
            return;
        }

        List<FrontierDomain> deeper =
                new ArrayList<FrontierDomain>(
                        probe.getFrontierDomains());
        List<FrontierDomain> meaningful =
                new ArrayList<FrontierDomain>();
        for (FrontierDomain candidate : deeper) {
            if (!candidate.semanticallyEquivalent(frontier)) {
                addUnique(meaningful, candidate);
            }
        }

        if (meaningful.isEmpty()) {
            addUnique(result, frontier);
            return;
        }

        List<FrontierDomain> nextTrail =
                new ArrayList<FrontierDomain>(trail);
        nextTrail.add(frontier);
        for (FrontierDomain candidate : meaningful) {
            expandInProbe(
                    operationMind,
                    probe,
                    candidate,
                    result,
                    nextTrail,
                    depth + 1);
        }
    }

    private static Rule findGeneratedDemand(
            Mind operationMind,
            Mind probe,
            FrontierDomain frontier) throws Exception {
        for (IRule candidate : probe.getRules()) {
            if (candidate == null
                    || candidate.isDeleted(probe)
                    || !candidate.isStored()
                    || !candidate.isGenerated()) {
                continue;
            }

            Rule rule = (Rule) candidate;
            long owner = rule.getMindId();
            if (owner != operationMind.getId()
                    && owner != probe.getId()) {
                continue;
            }

            if (frontier.semanticallyMatches(
                    rule.getDomain(), probe)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean containsEquivalent(
            List<FrontierDomain> values,
            FrontierDomain candidate) {
        for (FrontierDomain value : values) {
            if (value.semanticallyEquivalent(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static void addUnique(
            List<FrontierDomain> values,
            FrontierDomain candidate) {
        if (!containsEquivalent(values, candidate)) {
            values.add(candidate);
        }
    }
}
