/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.interfaces.IRule;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reduces an Analyzer frontier through local X inference only.
 *
 * <p>The operation Mind already contains generated demand Rules created by
 * Linker. A demand is expanded by running Linker on that exact Rule in an
 * isolated child and inspecting Linker's query-local trace directly:
 * newly-created generated demands plus unresolved premises adjacent to exact
 * used Domain occurrences. Analyzer is deliberately not used here because its
 * contract is a full visible-database interpretation, not seed-local
 * dependency discovery.</p>
 *
 * <p>No ConnectionVector or foreign Context is consulted. If local linking
 * produces no deeper dependency and does not consume the demand, the demand is
 * an external frontier leaf. If it consumes the demand without leaving a
 * deeper dependency, X has closed it locally.</p>
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

        List<FrontierDomain> deeper =
                collectLocalDependencies(
                        operationMind,
                        probe,
                        frontier);
        if (deeper.isEmpty()) {
            if (!wasDemandUsed(probe, frontier)) {
                addUnique(result, frontier);
            }
            return;
        }

        List<FrontierDomain> nextTrail =
                new ArrayList<FrontierDomain>(trail);
        nextTrail.add(frontier);
        for (FrontierDomain candidate : deeper) {
            expandInProbe(
                    operationMind,
                    probe,
                    candidate,
                    result,
                    nextTrail,
                    depth + 1);
        }
    }

    private static List<FrontierDomain> collectLocalDependencies(
            Mind operationMind,
            Mind probe,
            FrontierDomain current) throws Exception {
        List<FrontierDomain> result =
                new ArrayList<FrontierDomain>();

        /*
         * Single unresolved local premise is often materialized by Linker as a
         * new generated demand owned by this probe.
         */
        for (IRule candidate : probe.getRules()) {
            if (candidate == null
                    || candidate.isDeleted(probe)
                    || !candidate.isStored()
                    || !candidate.isGenerated()
                    || candidate.getMindId() != probe.getId()) {
                continue;
            }
            Rule generated = (Rule) candidate;
            Domain domain = generated.getDomain();
            if (domain.isAntc()
                    || !domain.isComplete()
                    || domain.isCalculated(probe)) {
                continue;
            }
            FrontierDomain descriptor =
                    capture(domain, probe);
            if (descriptor != null
                    && !descriptor.semanticallyEquivalent(current)) {
                addUnique(result, descriptor);
            }
        }

        /*
         * Multi-premise local dependency does not have to materialize generated
         * Rules. Linker's usedDomains map contains the exact occurrences it
         * consumed. Unused ordinary premises in the same branch are therefore
         * the deeper local demand frontier.
         */
        for (Map.Entry<Domain, Set<org.kanger.primitives.ArgumentsList>> entry
                : probe.getUsedDomains().entrySet()) {
            Domain used = entry.getKey();
            IRule owner = used.getRule();
            if (!(owner instanceof Rule)
                    || owner.isDeleted(probe)) {
                continue;
            }

            for (List<Domain> branch : ((Rule) owner).getTree()) {
                if (!containsOccurrence(branch, used)) {
                    continue;
                }
                for (Domain domain : branch) {
                    if (sameOccurrence(domain, used)
                            || domain.isAntc()
                            || !domain.isComplete()
                            || domain.isUsed(probe)
                            || domain.isCalculated(probe)
                            || domain.isSystem(probe)) {
                        continue;
                    }
                    FrontierDomain descriptor =
                            capture(domain, probe);
                    if (descriptor != null
                            && !descriptor.semanticallyEquivalent(current)) {
                        addUnique(result, descriptor);
                    }
                }
            }
        }

        return result;
    }

    private static boolean wasDemandUsed(
            Mind probe,
            FrontierDomain frontier) throws Exception {
        for (Domain used : probe.getUsedDomains().keySet()) {
            if (frontier.semanticallyMatches(
                    used, probe)) {
                return true;
            }
        }
        return false;
    }

    private static FrontierDomain capture(
            Domain domain, Mind probe) throws Exception {
        /*
         * semanticallyMatches already performs the same detached capture but
         * returns only equality. Use a tiny temporary Analyzer-visible wrapper
         * path by asking the public descriptor matcher against candidates
         * generated from current frontier state is not possible here, so
         * capture through the ordinary Analyzer surface helper exposed below.
         */
        return FrontierDomain.fromDomain(domain, probe);
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

    private static boolean containsOccurrence(
            List<Domain> branch, Domain used) {
        for (Domain candidate : branch) {
            if (sameOccurrence(candidate, used)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameOccurrence(
            Domain left, Domain right) {
        return left == right
                || left.getId() == right.getId();
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
