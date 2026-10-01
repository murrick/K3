/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDomain;
import org.kanger.Mind;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Rule;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * M3.6 positive-query continuation over ephemeral foreign evidence.
 *
 * <p>The query Rule is compiled exactly once in one operation-local child Mind.
 * Each federation wave may add ordinary assertion Rules to that same child,
 * after which the same compiled query Rule is linked/analyzed again. No
 * evidence is committed to the source Context and no query is recursively
 * federated by target Contexts.</p>
 *
 * <p>This engine proves only the queried proposition or reaches a fixed point.
 * FALSE/UNKNOWN/CONFLICT aggregation belongs to M3.7.</p>
 */
final class FrontierContinuationEngine {

    private static final int MAX_WAVES = 128;

    private FrontierContinuationEngine() {
    }

    static Result execute(
            Path sourceLocation,
            String querySource) throws Exception {
        if (sourceLocation == null) {
            throw new NullPointerException("sourceLocation");
        }
        if (querySource == null
                || querySource.isEmpty()
                || querySource.charAt(0) != '?') {
            throw new IllegalArgumentException(
                    "Federated continuation requires a query source");
        }

        OperationSnapshot operation =
                OperationSnapshot.open(sourceLocation);
        SnapshotMindRuntime runtime = null;
        Mind work = null;
        Mind root = null;
        try {
            runtime = SnapshotMindRuntime.open(
                    operation.getSourceLocation(),
                    operation.getSourceRef(),
                    "frontier-operation-"
                            + operation.getSourceRef()
                                    .getContextId().toString());
            root = runtime.getMind();
            work = new Mind(root);
            work.setQueryPass(QueryPass.CHECKTRUE);

            Rule query = (Rule) work.compileLine(
                    querySource,
                    true,
                    new LinkedList<ITerm>());
            if (query == null || query.isSecond()) {
                throw new IllegalStateException(
                        "Unable to establish operation-local query Rule: "
                                + querySource);
            }

            long queryRuleId = query.getId();
            if (prove(work, query)) {
                return new Result(
                        true,
                        0,
                        0,
                        queryRuleId,
                        Collections.<List<String>>emptyList());
            }

            Set<EvidenceKey> evidence =
                    new LinkedHashSet<EvidenceKey>();
            List<List<String>> frontierTrace =
                    new ArrayList<List<String>>();
            int waves = 0;
            int evidenceCount = 0;

            while (waves < MAX_WAVES) {
                List<FrontierDomain> frontiers =
                        work.getFrontierDomains();
                if (frontiers.isEmpty()) {
                    return new Result(
                            false,
                            waves,
                            evidenceCount,
                            queryRuleId,
                            frontierTrace);
                }

                List<String> predicates =
                        new ArrayList<String>();
                for (FrontierDomain frontier : frontiers) {
                    predicates.add(
                            frontier.getPredicateName());
                }
                frontierTrace.add(
                        Collections.unmodifiableList(predicates));
                ++waves;

                boolean changed = false;
                for (FrontierDomain frontier : frontiers) {
                    List<FrontierAnswer> answers =
                            FrontierFanOut.execute(
                                    operation, frontier);

                    if (frontier.isGround()) {
                        Set<RevisionRef> supports =
                                new LinkedHashSet<RevisionRef>();
                        for (FrontierAnswer answer : answers) {
                            if (answer.getTruth()
                                    == FrontierAnswer.Truth.TRUE) {
                                supports.add(answer.getSource());
                            }
                        }
                        if (!supports.isEmpty()
                                && inject(
                                        work,
                                        frontier,
                                        Collections.<String>emptyList(),
                                        Collections.<ITerm>emptyList(),
                                        evidence)) {
                            ++evidenceCount;
                            changed = true;
                        }
                    }

                    FrontierLiftSession.LiftResult lifted =
                            FrontierLiftSession.liftInto(
                                    work,
                                    operation,
                                    answers);
                    for (FrontierLiftSession.LiftedTuple tuple
                            : lifted.getTuples()) {
                        if (inject(
                                work,
                                frontier,
                                lifted.getVariableOrder(),
                                tuple.getValues(),
                                evidence)) {
                            ++evidenceCount;
                            changed = true;
                        }
                    }
                }

                if (!changed) {
                    return new Result(
                            false,
                            waves,
                            evidenceCount,
                            queryRuleId,
                            frontierTrace);
                }

                work.setQueryPass(QueryPass.CHECKTRUE);
                work.link(query, false);
                if (work.analyze(query, false)) {
                    return new Result(
                            true,
                            waves,
                            evidenceCount,
                            queryRuleId,
                            frontierTrace);
                }
                if (query.getId() != queryRuleId) {
                    throw new AssertionError(
                            "Federated continuation replaced the compiled query Rule");
                }
            }

            throw new IllegalStateException(
                    "Federated continuation exceeded "
                            + MAX_WAVES + " waves");
        } finally {
            Throwable failure = null;
            if (root != null && work != null) {
                try {
                    root.release(work);
                } catch (Throwable releaseFailure) {
                    failure = releaseFailure;
                }
            }
            if (runtime != null) {
                try {
                    runtime.close();
                } catch (Throwable closeFailure) {
                    if (failure == null) {
                        failure = closeFailure;
                    } else if (closeFailure != failure) {
                        failure.addSuppressed(closeFailure);
                    }
                }
            }
            try {
                operation.close();
            } catch (Throwable closeFailure) {
                if (failure == null) {
                    failure = closeFailure;
                } else if (closeFailure != failure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            if (failure != null) {
                if (failure instanceof Exception) {
                    throw (Exception) failure;
                }
                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                throw new RuntimeException(failure);
            }
        }
    }

    private static boolean prove(
            Mind work, Rule query) throws Exception {
        boolean result = work.analyze(query, false);
        if (!result) {
            work.link(query, false);
            result = work.analyze(query, false);
        }
        return result;
    }

    private static boolean inject(
            Mind work,
            FrontierDomain frontier,
            List<String> variableOrder,
            List<ITerm> values,
            Set<EvidenceKey> evidence) throws Exception {
        Queue<ITerm> arguments =
                frontier.evidenceArguments(
                        work,
                        variableOrder,
                        values);
        EvidenceKey key =
                EvidenceKey.of(
                        frontier.getEvidenceSource(true),
                        arguments);
        if (!evidence.add(key)) {
            return false;
        }

        work.setQueryPass(QueryPass.ACCEPT);
        Rule assertion = (Rule) work.compileLine(
                frontier.getEvidenceSource(true),
                false,
                new LinkedList<ITerm>(arguments));
        work.setQueryPass(QueryPass.CHECKTRUE);

        if (assertion == null || assertion.isSecond()) {
            return false;
        }

        work.link(assertion, false);
        if (work.analyze(assertion, false)) {
            throw new IllegalStateException(
                    "Ephemeral federation evidence conflicts in X: "
                            + frontier.getPredicateName());
        }
        return true;
    }

    static final class Result {

        private final boolean resolved;
        private final int waves;
        private final int evidenceCount;
        private final long queryRuleId;
        private final List<List<String>> frontierTrace;

        private Result(boolean resolved,
                       int waves,
                       int evidenceCount,
                       long queryRuleId,
                       List<List<String>> frontierTrace) {
            this.resolved = resolved;
            this.waves = waves;
            this.evidenceCount = evidenceCount;
            this.queryRuleId = queryRuleId;
            List<List<String>> copied =
                    new ArrayList<List<String>>();
            for (List<String> wave : frontierTrace) {
                copied.add(Collections.unmodifiableList(
                        new ArrayList<String>(wave)));
            }
            this.frontierTrace =
                    Collections.unmodifiableList(copied);
        }

        boolean isResolved() {
            return resolved;
        }

        int getWaves() {
            return waves;
        }

        int getEvidenceCount() {
            return evidenceCount;
        }

        long getQueryRuleId() {
            return queryRuleId;
        }

        List<List<String>> getFrontierTrace() {
            return frontierTrace;
        }
    }

    private static final class EvidenceKey {

        private final String source;
        private final List<Long> argumentIds;

        private EvidenceKey(String source,
                            List<Long> argumentIds) {
            this.source = source;
            this.argumentIds = argumentIds;
        }

        static EvidenceKey of(
                String source,
                Queue<ITerm> arguments) {
            List<Long> ids =
                    new ArrayList<Long>();
            for (ITerm argument : arguments) {
                ids.add(Long.valueOf(argument.getId()));
            }
            return new EvidenceKey(
                    source,
                    Collections.unmodifiableList(ids));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof EvidenceKey)) {
                return false;
            }
            EvidenceKey key = (EvidenceKey) other;
            return source.equals(key.source)
                    && argumentIds.equals(key.argumentIds);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode()
                    + argumentIds.hashCode();
        }
    }
}
