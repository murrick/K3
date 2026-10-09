/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M3.7 aggregate of one frontier across exact connected Context revisions.
 *
 * <p>Truth and hypotheses are deliberately orthogonal. A hypothesis remains a
 * provisional source/revision-qualified statement and never contributes TRUE
 * or FALSE evidence by itself.</p>
 */
final class FrontierAggregate {

    enum Truth {
        TRUE,
        FALSE,
        UNKNOWN,
        CONFLICT
    }

    private final FrontierInvocation invocation;
    private final Truth truth;
    private final Set<RevisionRef> trueSources;
    private final Set<RevisionRef> falseSources;
    private final Set<RevisionRef> unknownSources;
    private final List<ProvisionalHypothesis> hypotheses;

    private FrontierAggregate(
            FrontierInvocation invocation,
            Truth truth,
            Set<RevisionRef> trueSources,
            Set<RevisionRef> falseSources,
            Set<RevisionRef> unknownSources,
            List<ProvisionalHypothesis> hypotheses) {
        this.invocation = invocation;
        this.truth = truth;
        this.trueSources = immutableSet(trueSources);
        this.falseSources = immutableSet(falseSources);
        this.unknownSources = immutableSet(unknownSources);
        this.hypotheses = Collections.unmodifiableList(
                new ArrayList<ProvisionalHypothesis>(hypotheses));
    }

    static FrontierAggregate of(
            List<FrontierAnswer> answers) {
        if (answers == null) {
            throw new NullPointerException("answers");
        }
        FrontierInvocation invocation = FrontierAnswer.requireSameInvocation(answers);

        Set<RevisionRef> trueSources =
                new LinkedHashSet<RevisionRef>();
        Set<RevisionRef> falseSources =
                new LinkedHashSet<RevisionRef>();
        Set<RevisionRef> unknownSources =
                new LinkedHashSet<RevisionRef>();
        List<ProvisionalHypothesis> hypotheses =
                new ArrayList<ProvisionalHypothesis>();
        Set<HypothesisKey> hypothesisKeys =
                new LinkedHashSet<HypothesisKey>();

        for (FrontierAnswer answer : answers) {
            if (answer == null) {
                throw new NullPointerException("answer");
            }

            switch (answer.getTruth()) {
                case TRUE:
                    trueSources.add(answer.getSource());
                    break;
                case FALSE:
                    falseSources.add(answer.getSource());
                    break;
                case NULL:
                    unknownSources.add(answer.getSource());
                    break;
                default:
                    throw new IllegalStateException(
                            "Unsupported frontier truth: "
                                    + answer.getTruth());
            }

            for (String source : answer.getHypotheses()) {
                HypothesisKey key =
                        new HypothesisKey(
                                answer.getSource(),
                                source);
                if (hypothesisKeys.add(key)) {
                    hypotheses.add(
                            new ProvisionalHypothesis(
                                    answer.getSource(),
                                    source));
                }
            }
        }

        Truth truth;
        if (!trueSources.isEmpty()
                && !falseSources.isEmpty()) {
            truth = Truth.CONFLICT;
        } else if (!trueSources.isEmpty()) {
            truth = Truth.TRUE;
        } else if (!falseSources.isEmpty()) {
            truth = Truth.FALSE;
        } else {
            truth = Truth.UNKNOWN;
        }

        return new FrontierAggregate(
                invocation,
                truth,
                trueSources,
                falseSources,
                unknownSources,
                hypotheses);
    }

    FrontierInvocation getInvocation() {
        return invocation;
    }

    Truth getTruth() {
        return truth;
    }

    Set<RevisionRef> getTrueSources() {
        return trueSources;
    }

    Set<RevisionRef> getFalseSources() {
        return falseSources;
    }

    Set<RevisionRef> getUnknownSources() {
        return unknownSources;
    }

    List<ProvisionalHypothesis> getHypotheses() {
        return hypotheses;
    }

    private static Set<RevisionRef> immutableSet(
            Set<RevisionRef> source) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<RevisionRef>(source));
    }

    static final class ProvisionalHypothesis {

        private final RevisionRef source;
        private final String statement;

        private ProvisionalHypothesis(
                RevisionRef source,
                String statement) {
            this.source = source;
            this.statement = statement;
        }

        RevisionRef getSource() {
            return source;
        }

        String getStatement() {
            return statement;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProvisionalHypothesis)) {
                return false;
            }
            ProvisionalHypothesis value =
                    (ProvisionalHypothesis) other;
            return source.equals(value.source)
                    && statement.equals(value.statement);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode()
                    + statement.hashCode();
        }
    }

    private static final class HypothesisKey {

        private final RevisionRef source;
        private final String statement;

        private HypothesisKey(
                RevisionRef source,
                String statement) {
            this.source = source;
            this.statement = statement;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HypothesisKey)) {
                return false;
            }
            HypothesisKey key = (HypothesisKey) other;
            return source.equals(key.source)
                    && statement.equals(key.statement);
        }

        @Override
        public int hashCode() {
            return 31 * source.hashCode()
                    + statement.hashCode();
        }
    }
}
