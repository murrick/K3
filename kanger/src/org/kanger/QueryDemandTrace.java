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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Opt-in shadow tracer for experiments with query-relevant hypotheses.
 *
 * <p>The tracer never participates in inference. {@link org.kanger.primitives.Cause}
 * reports actual unifications as directed Domain edges. A snapshot can then
 * build a conservative backward slice from query Domains, closing each reached
 * terminal branch as a hyperedge. Hypotheses whose predicate/arguments can
 * participate in that demanded slice form a shortlist for an exact relevance
 * oracle. Missing trace evidence deliberately falls back to the full list.</p>
 *
 * <p>The shortlist is not semantic authority. The intended invariant is
 * {@code exactRelevant subsetOf traceCandidates}; false positives are allowed,
 * false negatives are not.</p>
 */
public final class QueryDemandTrace {

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<>();

    private QueryDemandTrace() {
    }

    public static void begin() {
        if (CURRENT.get() != null) {
            throw new IllegalStateException("Query demand trace is already active");
        }
        CURRENT.set(new Session());
    }

    public static Snapshot end() {
        Session session = CURRENT.get();
        CURRENT.remove();
        if (session == null) {
            return new Snapshot(Collections.<Long>emptySet(),
                    Collections.<Long, Set<Long>>emptyMap(), 0);
        }
        return new Snapshot(session.queryDomains, session.reverseDomainEdges,
                session.edgeCount);
    }

    /** Internal no-op-unless-enabled hook from the semantic Cause boundary. */
    public static void recordCause(Domain acceptor, Domain donor, Mind mind) {
        Session session = CURRENT.get();
        if (session == null || acceptor == null || donor == null) {
            return;
        }
        try {
            long acceptorId = acceptor.getId();
            long donorId = donor.getId();
            add(session.reverseDomainEdges, donorId, acceptorId);
            ++session.edgeCount;

            IRule acceptorRule = acceptor.getRule();
            IRule donorRule = donor.getRule();
            if ((acceptorRule != null && acceptorRule.isQuery())
                    || acceptor.isQuery(mind)) {
                session.queryDomains.add(acceptorId);
            }
            if ((donorRule != null && donorRule.isQuery())
                    || donor.isQuery(mind)) {
                session.queryDomains.add(donorId);
            }
        } catch (Exception ignored) {
            // Shadow diagnostics must never affect logical execution.
        }
    }

    private static void add(Map<Long, Set<Long>> edges, long from, long to) {
        Set<Long> next = edges.get(from);
        if (next == null) {
            next = new LinkedHashSet<>();
            edges.put(from, next);
        }
        next.add(to);
    }

    public static final class Snapshot {
        private final Set<Long> queryDomains;
        private final Map<Long, Set<Long>> reverseDomainEdges;
        private final int recordedEdges;

        private Snapshot(Set<Long> queryDomains,
                         Map<Long, Set<Long>> reverseDomainEdges,
                         int recordedEdges) {
            this.queryDomains = Collections.unmodifiableSet(
                    new LinkedHashSet<>(queryDomains));
            Map<Long, Set<Long>> copied = new HashMap<>();
            for (Map.Entry<Long, Set<Long>> entry : reverseDomainEdges.entrySet()) {
                copied.put(entry.getKey(), Collections.unmodifiableSet(
                        new LinkedHashSet<>(entry.getValue())));
            }
            this.reverseDomainEdges = Collections.unmodifiableMap(copied);
            this.recordedEdges = recordedEdges;
        }

        public int getQueryRootCount() {
            return queryDomains.size();
        }

        public int getRecordedEdgeCount() {
            return recordedEdges;
        }

        /**
         * Build the conservative demanded Domain set. Actual directed Cause
         * edges are followed donor -> acceptor; any reached Domain expands its
         * whole terminal branch, after which newly exposed Cause edges are
         * followed again until fixed point.
         */
        public Set<Long> demandedDomains(Mind mind) throws Exception {
            if (queryDomains.isEmpty()) {
                return Collections.emptySet();
            }

            Set<Long> demanded = new LinkedHashSet<>(queryDomains);
            boolean changed;
            do {
                changed = false;

                List<Long> frontier = new ArrayList<>(demanded);
                for (Long domainId : frontier) {
                    Set<Long> next = reverseDomainEdges.get(domainId);
                    if (next != null) {
                        for (Long target : next) {
                            if (demanded.add(target)) {
                                changed = true;
                            }
                        }
                    }
                }

                for (IRule candidate : mind.getRules()) {
                    if (candidate.isDeleted(mind)) {
                        continue;
                    }
                    Rule rule = (Rule) candidate;
                    for (List<Domain> branch : rule.getTree()) {
                        boolean reached = false;
                        for (Domain domain : branch) {
                            if (demanded.contains(domain.getId())) {
                                reached = true;
                                break;
                            }
                        }
                        if (reached) {
                            for (Domain domain : branch) {
                                if (demanded.add(domain.getId())) {
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            } while (changed);
            return demanded;
        }

        /**
         * Conservative prefilter. If no query roots were observed, return all
         * hypotheses rather than risk a false negative.
         */
        public List<IHypothesis> selectCandidates(Mind mind,
                                                   Collection<IHypothesis> hypotheses)
                throws Exception {
            List<IHypothesis> all = new ArrayList<>(hypotheses);
            Set<Long> demanded = demandedDomains(mind);
            if (queryDomains.isEmpty() || demanded.isEmpty()) {
                return all;
            }

            List<Domain> patterns = new ArrayList<>();
            for (IRule candidate : mind.getRules()) {
                if (candidate.isDeleted(mind)) {
                    continue;
                }
                Rule rule = (Rule) candidate;
                for (List<Domain> branch : rule.getTree()) {
                    for (Domain domain : branch) {
                        if (demanded.contains(domain.getId())) {
                            patterns.add(domain);
                        }
                    }
                }
            }

            List<IHypothesis> selected = new ArrayList<>();
            for (IHypothesis hypothesis : all) {
                if (matchesAny(hypothesis, patterns, mind)) {
                    selected.add(hypothesis);
                }
            }
            return selected;
        }

        private boolean matchesAny(IHypothesis hypothesis,
                                   List<Domain> patterns,
                                   Mind mind) throws Exception {
            for (Domain pattern : patterns) {
                if (hypothesis.getPredicate().getId() != pattern.getPredicateId()) {
                    continue;
                }
                if (argumentsCompatible(hypothesis.getArguments(),
                        pattern.getArguments(), mind)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Variables/C-variables are wildcards for the shadow prefilter. Reject
         * only an explicit concrete mismatch; uncertainty keeps the candidate.
         * Polarity is intentionally not filtered: the two query passes and the
         * historical Hypothesis polarity inversion can make either alternative
         * of a demanded predicate a valid singleton resolution.
         */
        private boolean argumentsCompatible(IList hypothesis,
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
                if (expectedValue.isCVariable() || actualValue.isCVariable()) {
                    continue;
                }
                if (expectedValue.getId() != actualValue.getId()) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class Session {
        private final Set<Long> queryDomains = new HashSet<>();
        private final Map<Long, Set<Long>> reverseDomainEdges = new HashMap<>();
        private int edgeCount;
    }
}
