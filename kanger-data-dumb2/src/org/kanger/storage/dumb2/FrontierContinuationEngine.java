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
 * M3.6/M3.7 continuation over ephemeral foreign evidence.
 *
 * <p>The query Rule is compiled exactly once in one operation-local child Mind.
 * Each federation wave may add ordinary assertion Rules to that same child,
 * after which the same compiled query Rule is linked/analyzed again. No
 * evidence is committed to the source Context and no query is recursively
 * federated by target Contexts.</p>
 *
 * <p>M3.7 aggregates every foreign frontier independently. Ground TRUE and
 * FALSE aggregates become operation-local factual donors of the corresponding
 * polarity. UNKNOWN and CONFLICT remain observations only. Provisional foreign
 * hypotheses are retained with exact source/revision provenance and never
 * become factual donors.</p>
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
            work = Mind.ephemeralChild(root);
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
            List<FrontierObservation> observations =
                    new ArrayList<FrontierObservation>();
            Set<FrontierAggregate.ProvisionalHypothesis>
                    provisionalHypotheses =
                    new LinkedHashSet<
                            FrontierAggregate.ProvisionalHypothesis>();

            if (prove(work, query)) {
                return new Result(
                        true,
                        0,
                        0,
                        queryRuleId,
                        Collections.<List<String>>emptyList(),
                        observations,
                        provisionalHypotheses);
            }

            Set<EvidenceKey> evidence =
                    new LinkedHashSet<EvidenceKey>();
            List<List<String>> frontierTrace =
                    new ArrayList<List<String>>();
            int waves = 0;
            int evidenceCount = 0;

            while (waves < MAX_WAVES) {
                List<FrontierDomain> frontiers =
                        new ArrayList<FrontierDomain>(
                                work.getFrontierDomains());
                if (frontiers.isEmpty()) {
                    return new Result(
                            false,
                            waves,
                            evidenceCount,
                            queryRuleId,
                            frontierTrace,
                            observations,
                            provisionalHypotheses);
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
                    FrontierAggregate aggregate =
                            FrontierAggregate.of(answers);
                    observations.add(
                            new FrontierObservation(
                                    waves,
                                    frontier,
                                    aggregate));
                    provisionalHypotheses.addAll(
                            aggregate.getHypotheses());

                    /*
                     * Ground truth aggregation belongs exactly here: it governs
                     * whether one fully-grounded frontier may become factual
                     * operation-local evidence in X. A conflict never injects
                     * either side; UNKNOWN injects nothing.
                     */
                    if (frontier.isGround()) {
                        Boolean factualTruth = null;
                        switch (aggregate.getTruth()) {
                            case TRUE:
                                factualTruth = Boolean.TRUE;
                                break;
                            case FALSE:
                                factualTruth = Boolean.FALSE;
                                break;
                            case UNKNOWN:
                            case CONFLICT:
                                break;
                            default:
                                throw new IllegalStateException(
                                        "Unsupported aggregate truth: "
                                                + aggregate.getTruth());
                        }

                        if (factualTruth != null
                                && inject(
                                        work,
                                        frontier,
                                        Collections.<String>emptyList(),
                                        Collections.<ITerm>emptyList(),
                                        evidence,
                                        factualTruth.booleanValue())) {
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
                                evidence,
                                true)) {
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
                            frontierTrace,
                            observations,
                            provisionalHypotheses);
                }

                work.setQueryPass(QueryPass.CHECKTRUE);
                /*
                 * New foreign evidence can satisfy a premise one or more local
                 * rules away from the original query. Rule-scoped linking
                 * intentionally builds a narrow candidate closure; after a
                 * federation wave we therefore run full local saturation in
                 * the same operation Mind, then analyze the same compiled query
                 * Rule. No query recompilation or foreign recursion occurs.
                 */
                work.link(null, false);
                if (work.analyze(query, false)) {
                    return new Result(
                            true,
                            waves,
                            evidenceCount,
                            queryRuleId,
                            frontierTrace,
                            observations,
                            provisionalHypotheses);
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
                    /*
                     * Mind.release historically publishes presentation result
                     * stores even for rollback. Federation evidence must not
                     * become a reachability root in the snapshot dictionary:
                     * drop operation-local Solutions/Values before settlement
                     * so ordinary DictionaryFactory.pack can discard every
                     * transient projected Term before read-only update.
                     */
                    work.getSolutions().clear();
                    work.getValues().clear();
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
            Set<EvidenceKey> evidence,
            boolean truth) throws Exception {
        Queue<ITerm> arguments =
                frontier.evidenceArguments(
                        work,
                        variableOrder,
                        values);
        String evidenceSource =
                frontier.getEvidenceSource(truth);
        EvidenceKey key =
                EvidenceKey.of(
                        evidenceSource,
                        arguments);
        if (!evidence.add(key)) {
            return false;
        }

        work.setQueryPass(QueryPass.ACCEPT);
        Rule assertion = (Rule) work.compileLine(
                evidenceSource,
                false,
                new LinkedList<ITerm>(arguments));
        work.setQueryPass(QueryPass.CHECKTRUE);

        if (assertion == null || assertion.isSecond()) {
            return false;
        }

        /*
         * Do not analyze the donor assertion as an independent ACCEPT. The
         * operation Mind already contains the opposite-polarity generated
         * demand, so ordinary ACCEPT collision semantics would correctly see
         * that pair as a collision. In federation that pair means "the answer
         * arrived". Retain the donor Rule and let the original compiled query
         * Rule consume it on the next link/analyze continuation.
         */
        return true;
    }

    static final class Result {

        private final boolean resolved;
        private final int waves;
        private final int evidenceCount;
        private final long queryRuleId;
        private final List<List<String>> frontierTrace;
        private final List<FrontierObservation> observations;
        private final List<FrontierAggregate.ProvisionalHypothesis>
                provisionalHypotheses;

        private Result(
                boolean resolved,
                int waves,
                int evidenceCount,
                long queryRuleId,
                List<List<String>> frontierTrace,
                List<FrontierObservation> observations,
                Set<FrontierAggregate.ProvisionalHypothesis>
                        provisionalHypotheses) {
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
            this.observations =
                    Collections.unmodifiableList(
                            new ArrayList<FrontierObservation>(
                                    observations));
            this.provisionalHypotheses =
                    Collections.unmodifiableList(
                            new ArrayList<
                                    FrontierAggregate.ProvisionalHypothesis>(
                                    provisionalHypotheses));
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

        List<FrontierObservation> getObservations() {
            return observations;
        }

        List<FrontierAggregate.ProvisionalHypothesis>
                getProvisionalHypotheses() {
            return provisionalHypotheses;
        }

        boolean hasConflict() {
            for (FrontierObservation observation : observations) {
                if (observation.getAggregate().getTruth()
                        == FrontierAggregate.Truth.CONFLICT) {
                    return true;
                }
            }
            return false;
        }
    }

    static final class FrontierObservation {

        private final int wave;
        private final String predicateName;
        private final String querySource;
        private final FrontierAggregate aggregate;

        private FrontierObservation(
                int wave,
                FrontierDomain frontier,
                FrontierAggregate aggregate) {
            this.wave = wave;
            this.predicateName = frontier.getPredicateName();
            this.querySource = frontier.getQuerySource();
            this.aggregate = aggregate;
        }

        int getWave() {
            return wave;
        }

        String getPredicateName() {
            return predicateName;
        }

        String getQuerySource() {
            return querySource;
        }

        FrontierAggregate getAggregate() {
            return aggregate;
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
