/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.Mind;
import org.kanger.interfaces.ITerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Operation-local semantic lift of foreign frontier tuples into canonical X Terms.
 */
final class FrontierLiftSession implements AutoCloseable {

    private final SnapshotMindRuntime runtime;
    private final Mind operationMind;
    private final List<String> variableOrder;
    private final List<LiftedTuple> tuples;
    private boolean closed;

    private FrontierLiftSession(SnapshotMindRuntime runtime,
                                Mind operationMind,
                                List<String> variableOrder,
                                List<LiftedTuple> tuples) {
        this.runtime = runtime;
        this.operationMind = operationMind;
        this.variableOrder = Collections.unmodifiableList(
                new ArrayList<String>(variableOrder));
        this.tuples = Collections.unmodifiableList(
                new ArrayList<LiftedTuple>(tuples));
    }

    static FrontierLiftSession open(
            OperationSnapshot operation,
            List<FrontierAnswer> answers) throws Exception {
        if (operation == null) {
            throw new NullPointerException("operation");
        }
        if (answers == null) {
            throw new NullPointerException("answers");
        }

        SnapshotMindRuntime runtime =
                SnapshotMindRuntime.open(
                        operation.getSourceLocation(),
                        operation.getSourceRef(),
                        "frontier-lift-"
                                + operation.getSourceRef()
                                .getContextId().toString());
        Mind operationMind = null;
        boolean success = false;
        try {
            operationMind =
                    Mind.ephemeralChild(runtime.getMind());
            LiftResult result =
                    liftInto(
                            operationMind,
                            operation,
                            answers);
            FrontierLiftSession session =
                    new FrontierLiftSession(
                            runtime,
                            operationMind,
                            result.getVariableOrder(),
                            result.getTuples());
            success = true;
            return session;
        } finally {
            if (!success) {
                if (operationMind != null) {
                    runtime.getMind().release(operationMind);
                }
                runtime.close();
            }
        }
    }

    static LiftResult liftInto(
            Mind target,
            OperationSnapshot operation,
            List<FrontierAnswer> answers) throws Exception {
        if (target == null) {
            throw new NullPointerException("target");
        }
        if (operation == null) {
            throw new NullPointerException("operation");
        }
        if (answers == null) {
            throw new NullPointerException("answers");
        }

        List<String> order =
                answers.isEmpty()
                        ? Collections.<String>emptyList()
                        : answers.get(0).getVariableOrder();
        Map<List<Long>, MutableTuple> unique =
                new LinkedHashMap<List<Long>, MutableTuple>();

        for (FrontierAnswer answer : answers) {
            ContextConnection connection =
                    operation.getConnections().find(
                            answer.getSource().getContextId());
            if (connection == null
                    || !connection.getTarget()
                    .equals(answer.getSource())) {
                throw new IllegalArgumentException(
                        "Frontier answer is not from an exact direct connection: "
                                + answer.getSource());
            }
            if (!order.equals(answer.getVariableOrder())) {
                throw new IllegalArgumentException(
                        "Frontier answers use different variable order");
            }
            if (answer.getTruth() != FrontierAnswer.Truth.TRUE) {
                continue;
            }

            for (List<FrontierAnswer.ValueRef> row
                    : answer.getValues()) {
                if (row.size() != order.size()) {
                    throw new IllegalArgumentException(
                            "Frontier tuple arity does not match variable order");
                }

                List<ITerm> liftedValues =
                        new ArrayList<ITerm>();
                List<Long> key =
                        new ArrayList<Long>();
                for (FrontierAnswer.ValueRef value : row) {
                    ITerm canonical =
                            target.getTerms()
                                    .projectSemantic(
                                            value.materialize());
                    liftedValues.add(canonical);
                    key.add(Long.valueOf(canonical.getId()));
                }

                MutableTuple tuple = unique.get(key);
                if (tuple == null) {
                    tuple = new MutableTuple(liftedValues);
                    unique.put(
                            new ArrayList<Long>(key),
                            tuple);
                }
                tuple.supports.add(answer.getSource());
            }
        }

        List<LiftedTuple> result =
                new ArrayList<LiftedTuple>();
        for (MutableTuple tuple : unique.values()) {
            result.add(new LiftedTuple(
                    tuple.values,
                    tuple.supports));
        }
        return new LiftResult(order, result);
    }

    static final class LiftResult {

        private final List<String> variableOrder;
        private final List<LiftedTuple> tuples;

        private LiftResult(List<String> variableOrder,
                           List<LiftedTuple> tuples) {
            this.variableOrder = Collections.unmodifiableList(
                    new ArrayList<String>(variableOrder));
            this.tuples = Collections.unmodifiableList(
                    new ArrayList<LiftedTuple>(tuples));
        }

        List<String> getVariableOrder() {
            return variableOrder;
        }

        List<LiftedTuple> getTuples() {
            return tuples;
        }
    }

    Mind getMind() {
        requireOpen();
        return operationMind;
    }

    List<String> getVariableOrder() {
        requireOpen();
        return variableOrder;
    }

    List<LiftedTuple> getTuples() {
        requireOpen();
        return tuples;
    }

    @Override
    public void close() throws Exception {
        if (closed) {
            return;
        }
        operationMind.getSolutions().clear();
        operationMind.getValues().clear();
        runtime.getMind().release(operationMind);
        runtime.close();
        closed = true;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(
                    "Frontier lift session is closed");
        }
    }

    static final class LiftedTuple {

        private final List<ITerm> values;
        private final Set<RevisionRef> supports;

        private LiftedTuple(List<ITerm> values,
                            Set<RevisionRef> supports) {
            this.values = Collections.unmodifiableList(
                    new ArrayList<ITerm>(values));
            this.supports = Collections.unmodifiableSet(
                    new LinkedHashSet<RevisionRef>(supports));
        }

        List<ITerm> getValues() {
            return values;
        }

        Set<RevisionRef> getSupports() {
            return supports;
        }
    }

    private static final class MutableTuple {
        private final List<ITerm> values;
        private final Set<RevisionRef> supports =
                new LinkedHashSet<RevisionRef>();

        private MutableTuple(List<ITerm> values) {
            this.values =
                    new ArrayList<ITerm>(values);
        }
    }
}
