/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.Mind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expands an Analyzer frontier through local X inference only.
 *
 * <p>A frontier such as {@code gate(Tom)} may itself be derivable from an
 * unresolved local premise such as {@code seed(Tom)}. Before any foreign
 * fan-out, this helper probes the current operation Mind in an ephemeral child
 * and replaces locally reducible frontiers with their unresolved leaf
 * dependencies. No connected Context is consulted here.</p>
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
            expandOne(
                    operationMind,
                    frontier,
                    result,
                    new ArrayList<FrontierDomain>(),
                    0);
        }
        return result;
    }

    private static void expandOne(
            Mind operationMind,
            FrontierDomain frontier,
            List<FrontierDomain> result,
            List<FrontierDomain> trail,
            int depth) throws Exception {
        if (depth >= MAX_DEPTH
                || containsEquivalent(trail, frontier)) {
            addUnique(result, frontier);
            return;
        }

        Mind probe =
                Mind.ephemeralChild(operationMind);
        List<FrontierDomain> deeper;
        Boolean localResult;
        try {
            localResult = probe.queryCanonical(
                    frontier.getQuerySource(),
                    frontier.projectFixedArguments(probe),
                    false);
            deeper = new ArrayList<FrontierDomain>(
                    probe.getFrontierDomains());
        } finally {
            probe.getSolutions().clear();
            probe.getValues().clear();
            operationMind.release(probe);
        }

        /*
         * A locally decisive answer is not an external dependency. M3.6 needs
         * only positive continuation; the final TRUE/FALSE/NULL/CONFLICT
         * aggregation of decisive local/foreign evidence is completed in M3.7.
         */
        if (localResult != null) {
            return;
        }

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
            expandOne(
                    operationMind,
                    candidate,
                    result,
                    nextTrail,
                    depth + 1);
        }
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
