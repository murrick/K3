/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.DataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Detached local-only answer produced by one exact foreign Context revision.
 */
final class FrontierAnswer {

    enum Truth {
        TRUE,
        FALSE,
        NULL
    }

    private final RevisionRef source;
    private final Truth truth;
    private final List<String> variableOrder;
    private final List<List<ValueRef>> values;
    private final List<String> hypotheses;

    FrontierAnswer(RevisionRef source,
                   Truth truth,
                   List<String> variableOrder,
                   List<List<ValueRef>> values,
                   List<String> hypotheses) {
        this.source = source;
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

    /**
     * Detached pointer to one semantic Term in the exact source revision.
     *
     * <p>M3.5 may reopen the source revision and perform the full
     * hash-plus-equalsTo semantic lift. The rendered value is diagnostic only.</p>
     */
    static final class ValueRef {

        private final long termId;
        private final int semanticHash;
        private final DataType type;
        private final String rendered;

        ValueRef(long termId,
                 int semanticHash,
                 DataType type,
                 String rendered) {
            this.termId = termId;
            this.semanticHash = semanticHash;
            this.type = type;
            this.rendered = rendered;
        }

        long getTermId() {
            return termId;
        }

        int getSemanticHash() {
            return semanticHash;
        }

        DataType getType() {
            return type;
        }

        String getRendered() {
            return rendered;
        }
    }
}
