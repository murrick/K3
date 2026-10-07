/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.FrontierDemand;
import org.kanger.SemanticTermSnapshot;
import org.kanger.enums.DataType;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Detached local-only answer addressed to one invocation and exact target. */
final class FrontierAnswer {

    enum Truth {
        TRUE,
        FALSE,
        NULL
    }

    private final RevisionRef source;
    private final FrontierInvocation invocation;
    private final FrontierRequest request;
    private final FrontierExecutionState executionState;
    private final Truth truth;
    private final List<String> variableOrder;
    private final List<List<ValueRef>> values;
    private final List<String> hypotheses;
    private final List<FrontierDemand> unresolvedFrontiers;

    FrontierAnswer(RevisionRef source,
                   FrontierInvocation invocation,
                   Truth truth,
                   List<String> variableOrder,
                   List<List<ValueRef>> values,
                   List<String> hypotheses,
                   List<FrontierDemand> unresolvedFrontiers) {
        this(source, FrontierRequest.initial(invocation), truth, variableOrder,
                values, hypotheses, unresolvedFrontiers);
    }

    FrontierAnswer(RevisionRef source,
                   FrontierRequest request,
                   Truth truth,
                   List<String> variableOrder,
                   List<List<ValueRef>> values,
                   List<String> hypotheses,
                   List<FrontierDemand> unresolvedFrontiers) {
        if (source == null || request == null || truth == null) {
            throw new NullPointerException("Frontier answer requires source, invocation and truth");
        }
        if (truth != Truth.NULL && !unresolvedFrontiers.isEmpty()) {
            throw new IllegalArgumentException("A resolved frontier has no unresolved demands");
        }
        this.source = source;
        this.request = request;
        this.invocation = request.getInvocation();
        this.executionState = request.executionState(source);
        this.truth = truth;
        this.variableOrder = Collections.unmodifiableList(
                new ArrayList<String>(variableOrder));

        ArrayList<List<ValueRef>> copied =
                new ArrayList<List<ValueRef>>();
        for (List<ValueRef> row : values) {
            copied.add(Collections.unmodifiableList(
                    new ArrayList<ValueRef>(row)));
        }
        this.values = Collections.unmodifiableList(copied);
        this.hypotheses = Collections.unmodifiableList(
                new ArrayList<String>(hypotheses));
        this.unresolvedFrontiers = Collections.unmodifiableList(
                new ArrayList<FrontierDemand>(unresolvedFrontiers));
    }

    FrontierInvocation getInvocation() {
        return invocation;
    }

    FrontierRequest getRequest() {
        return request;
    }

    FrontierExecutionState getExecutionState() {
        return executionState;
    }

    List<FrontierDemand> getUnresolvedFrontiers() {
        return unresolvedFrontiers;
    }

    /** Validate the whole response batch before aggregation or materialization. */
    static FrontierInvocation requireSameInvocation(List<FrontierAnswer> answers) {
        FrontierInvocation invocation = null;
        Map<RevisionRef, FrontierExecutionState> states =
                new LinkedHashMap<RevisionRef, FrontierExecutionState>();
        for (FrontierAnswer answer : answers) {
            if (answer == null) {
                throw new NullPointerException("answer");
            }
            if (invocation == null) {
                invocation = answer.getInvocation();
            } else if (!invocation.sameAddress(answer.getInvocation())) {
                throw new IllegalArgumentException("Frontier answers belong to different invocations");
            }
            FrontierExecutionState previous = states.put(answer.getSource(), answer.getExecutionState());
            if (previous != null && !previous.equals(answer.getExecutionState())) {
                throw new IllegalArgumentException("Frontier batch contains stale and resumed target states");
            }
        }
        return invocation;
    }

    RevisionRef getSource() {
        return source;
    }

    Truth getTruth() {
        return truth;
    }

    List<String> getVariableOrder() {
        return variableOrder;
    }

    List<List<ValueRef>> getValues() {
        return values;
    }

    List<String> getHypotheses() {
        return hypotheses;
    }

    static Truth truth(Boolean answer) {
        if (answer == null) {
            return Truth.NULL;
        }
        return answer.booleanValue()
                ? Truth.TRUE
                : Truth.FALSE;
    }

    static final class ValueRef {

        private final long termId;
        private final SemanticTermSnapshot semantic;
        private final String rendered;

        private ValueRef(long termId,
                         SemanticTermSnapshot semantic,
                         String rendered) {
            this.termId = termId;
            this.semantic = semantic;
            this.rendered = rendered;
        }

        static ValueRef capture(Term term) throws Exception {
            return new ValueRef(
                    term.getId(),
                    SemanticTermSnapshot.capture(term),
                    term.toString());
        }

        long getTermId() {
            return termId;
        }

        int getSemanticHash() {
            return semantic.getHash();
        }

        DataType getType() {
            return semantic.getType();
        }

        String getRendered() {
            return rendered;
        }

        Term materialize() {
            return semantic.materialize();
        }

        SemanticTermSnapshot getSemantic() {
            return semantic;
        }
    }
}
