/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.storage.dumb2;

import org.kanger.enums.DataType;
import org.kanger.interfaces.ITerm;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
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
     * Detached semantic value carried by one FrontierAnswer tuple position.
     *
     * <p>The source-local termId is diagnostic only. Semantic lift uses the
     * recursively detached type/hash/payload snapshot and therefore does not
     * depend on the foreign Mind remaining open or on the Term being durable in
     * the source Context.</p>
     */
    static final class ValueRef {

        private final long termId;
        private final SemanticTerm semantic;
        private final String rendered;

        private ValueRef(long termId,
                         SemanticTerm semantic,
                         String rendered) {
            this.termId = termId;
            this.semantic = semantic;
            this.rendered = rendered;
        }

        static ValueRef capture(Term term) throws Exception {
            if (term == null) {
                throw new NullPointerException("term");
            }
            return new ValueRef(
                    term.getId(),
                    SemanticTerm.capture(term),
                    term.toString());
        }

        long getTermId() {
            return termId;
        }

        int getSemanticHash() {
            return semantic.hash;
        }

        DataType getType() {
            return semantic.type;
        }

        String getRendered() {
            return rendered;
        }

        Term materialize() {
            return semantic.materialize();
        }
    }

    /**
     * Exact semantic payload detached from operational IDs and Mind ownership.
     */
    private static final class SemanticTerm {

        private final DataType type;
        private final int hash;
        private final Object scalar;
        private final List<SemanticTerm> members;

        private SemanticTerm(DataType type,
                             int hash,
                             Object scalar,
                             List<SemanticTerm> members) {
            this.type = type;
            this.hash = hash;
            this.scalar = scalar;
            this.members = members == null
                    ? Collections.<SemanticTerm>emptyList()
                    : Collections.unmodifiableList(
                            new ArrayList<SemanticTerm>(members));
        }

        @SuppressWarnings("unchecked")
        static SemanticTerm capture(Term term) throws Exception {
            if (term.isCVariable()) {
                throw new IllegalArgumentException(
                        "Frontier value cannot detach a C-variable descriptor");
            }

            Object value = term.getValue();
            switch (term.getType()) {
                case BLOB:
                    byte[] blob = (byte[]) value;
                    return new SemanticTerm(
                            term.getType(),
                            term.getHash(),
                            blob == null
                                    ? null
                                    : Arrays.copyOf(blob, blob.length),
                            null);
                case DATE:
                    return new SemanticTerm(
                            term.getType(),
                            term.getHash(),
                            value == null
                                    ? null
                                    : Long.valueOf(((Date) value).getTime()),
                            null);
                case SET:
                case INTERVAL:
                    if (!(value instanceof Collection)) {
                        throw new IllegalStateException(
                                "Structured frontier Term has no materialized members: "
                                        + term.getType());
                    }
                    List<SemanticTerm> members =
                            new ArrayList<SemanticTerm>();
                    for (ITerm member : (Collection<ITerm>) value) {
                        members.add(capture((Term) member));
                    }
                    return new SemanticTerm(
                            term.getType(),
                            term.getHash(),
                            null,
                            members);
                case TERM:
                    if (!(value instanceof Term)) {
                        throw new IllegalStateException(
                                "Nested TERM frontier value is not materialized");
                    }
                    return new SemanticTerm(
                            term.getType(),
                            term.getHash(),
                            null,
                            Collections.singletonList(
                                    capture((Term) value)));
                default:
                    return new SemanticTerm(
                            term.getType(),
                            term.getHash(),
                            value,
                            null);
            }
        }

        Term materialize() {
            Object value;
            switch (type) {
                case BLOB:
                    byte[] blob = (byte[]) scalar;
                    value = blob == null
                            ? null
                            : Arrays.copyOf(blob, blob.length);
                    break;
                case DATE:
                    value = scalar == null
                            ? null
                            : new Date(((Long) scalar).longValue());
                    break;
                case SET:
                case INTERVAL:
                    List<ITerm> projected =
                            new ArrayList<ITerm>();
                    for (SemanticTerm member : members) {
                        projected.add(member.materialize());
                    }
                    value = projected;
                    break;
                case TERM:
                    value = members.isEmpty()
                            ? null
                            : members.get(0).materialize();
                    break;
                default:
                    value = scalar;
                    break;
            }

            Term term = new Term();
            term.setPersistentState(
                    type,
                    value,
                    hash,
                    0,
                    -1L,
                    -1L,
                    false);
            return term;
        }
    }
}
