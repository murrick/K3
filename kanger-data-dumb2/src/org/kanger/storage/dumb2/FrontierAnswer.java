/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.SemanticTermSnapshot;
import org.kanger.enums.DataType;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Detached local-only answer produced by one exact foreign Context revision. */
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
    }
}
