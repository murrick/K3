/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger;

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
 * Detached semantic value of one ordinary KANGER Term.
 *
 * <p>Operational ID, Mind ownership, deletion state and C-variable descriptors
 * are intentionally excluded. The snapshot preserves the exact logical type,
 * semantic hash and recursively detached payload required by cross-Context
 * query transport and canonicalization.</p>
 */
public final class SemanticTermSnapshot {

    private final DataType type;
    private final int hash;
    private final Object scalar;
    private final List<SemanticTermSnapshot> members;

    private SemanticTermSnapshot(DataType type,
                                 int hash,
                                 Object scalar,
                                 List<SemanticTermSnapshot> members) {
        this.type = type;
        this.hash = hash;
        this.scalar = scalar;
        this.members = members == null
                ? Collections.<SemanticTermSnapshot>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<SemanticTermSnapshot>(members));
    }

    public static SemanticTermSnapshot capture(ITerm value)
            throws Exception {
        if (!(value instanceof Term)) {
            throw new IllegalArgumentException(
                    "Semantic snapshot requires a Term");
        }
        Term term = (Term) value;
        if (term.isCVariable()) {
            throw new IllegalArgumentException(
                    "C-variable descriptor is not an ordinary semantic value");
        }

        Object raw = term.getValue();
        switch (term.getType()) {
            case BLOB:
                byte[] blob = (byte[]) raw;
                return new SemanticTermSnapshot(
                        term.getType(),
                        term.getHash(),
                        blob == null
                                ? null
                                : Arrays.copyOf(blob, blob.length),
                        null);
            case DATE:
                return new SemanticTermSnapshot(
                        term.getType(),
                        term.getHash(),
                        raw == null
                                ? null
                                : Long.valueOf(((Date) raw).getTime()),
                        null);
            case SET:
                return structured(
                        term,
                        term.semanticMembers());
            case INTERVAL:
                return structured(
                        term,
                        castMembers(raw));
            case TERM:
                if (!(raw instanceof ITerm)) {
                    throw new IllegalStateException(
                            "Nested TERM value is not materialized");
                }
                return new SemanticTermSnapshot(
                        term.getType(),
                        term.getHash(),
                        null,
                        Collections.singletonList(
                                capture((ITerm) raw)));
            default:
                return new SemanticTermSnapshot(
                        term.getType(),
                        term.getHash(),
                        raw,
                        null);
        }
    }

    private static SemanticTermSnapshot structured(
            Term term, Collection<ITerm> values) throws Exception {
        List<SemanticTermSnapshot> members =
                new ArrayList<SemanticTermSnapshot>();
        for (ITerm value : values) {
            members.add(capture(value));
        }
        return new SemanticTermSnapshot(
                term.getType(),
                term.getHash(),
                null,
                members);
    }

    @SuppressWarnings("unchecked")
    private static Collection<ITerm> castMembers(Object raw) {
        if (!(raw instanceof Collection)) {
            throw new IllegalStateException(
                    "Structured Term payload is not materialized");
        }
        return (Collection<ITerm>) raw;
    }

    public DataType getType() {
        return type;
    }

    public int getHash() {
        return hash;
    }

    /**
     * Recreates a detached Term with no operational ID or Mind ownership.
     */
    public Term materialize() {
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
                for (SemanticTermSnapshot member : members) {
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
