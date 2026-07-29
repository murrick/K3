/*
 * MIT License
 *
 * Copyright (c) 2026 Dmitry G. Quznetsov
 */
package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.enums.ArgumentType;
import org.kanger.interfaces.IArgument;
import org.kanger.units.Domain;
import org.kanger.units.Rule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Compact in-memory candidate metadata. Only Rule IDs are retained; Rules and
 * Domains remain owned by Escalera/IBase and are hydrated after candidate
 * selection.
 *
 * The positional lookup is conservative. A non-TERM argument is indexed as a
 * wildcard, so dynamic variables/functions are never excluded by a constant
 * query argument.
 */
final class RuleCandidateIndex {

    private static final long WILDCARD_TERM_ID = -1L;

    private static final class SignatureKey {
        private final long predicateId;
        private final boolean antc;
        private final int arity;

        private SignatureKey(long predicateId, boolean antc, int arity) {
            this.predicateId = predicateId;
            this.antc = antc;
            this.arity = arity;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof SignatureKey)) {
                return false;
            }
            SignatureKey other = (SignatureKey) value;
            return predicateId == other.predicateId
                    && antc == other.antc
                    && arity == other.arity;
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(predicateId).hashCode();
            result = 31 * result + (antc ? 1 : 0);
            return 31 * result + arity;
        }
    }

    private static final class PositionKey {
        private final SignatureKey signature;
        private final int position;
        private final long termId;

        private PositionKey(SignatureKey signature, int position, long termId) {
            this.signature = signature;
            this.position = position;
            this.termId = termId;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof PositionKey)) {
                return false;
            }
            PositionKey other = (PositionKey) value;
            return position == other.position
                    && termId == other.termId
                    && signature.equals(other.signature);
        }

        @Override
        public int hashCode() {
            int result = signature.hashCode();
            result = 31 * result + position;
            return 31 * result + Long.valueOf(termId).hashCode();
        }
    }

    /** Journaled ID index: mark/release cost is proportional to mutations. */
    private static final class IdIndex<K> {
        private static final class Change<K> {
            private final K key;
            private final long id;
            private final boolean addition;

            private Change(K key, long id, boolean addition) {
                this.key = key;
                this.id = id;
                this.addition = addition;
            }
        }

        private final Map<K, LinkedHashSet<Long>> values = new HashMap<>();
        private final Stack<List<Change<K>>> journals = new Stack<>();
        private boolean replaying = false;

        private boolean addInternal(K key, long id) {
            LinkedHashSet<Long> ids = values.get(key);
            if (ids == null) {
                ids = new LinkedHashSet<>();
                values.put(key, ids);
            }
            return ids.add(id);
        }

        private boolean removeInternal(K key, long id) {
            LinkedHashSet<Long> ids = values.get(key);
            if (ids == null || !ids.remove(id)) {
                return false;
            }
            if (ids.isEmpty()) {
                values.remove(key);
            }
            return true;
        }

        void add(K key, long id) {
            if (addInternal(key, id) && !replaying && !journals.isEmpty()) {
                journals.peek().add(new Change<>(key, id, true));
            }
        }

        void remove(K key, long id) {
            if (removeInternal(key, id) && !replaying && !journals.isEmpty()) {
                journals.peek().add(new Change<>(key, id, false));
            }
        }

        LinkedHashSet<Long> get(K key) {
            LinkedHashSet<Long> ids = values.get(key);
            return ids == null ? new LinkedHashSet<Long>() : new LinkedHashSet<>(ids);
        }

        void clear() {
            values.clear();
            journals.clear();
        }

        void mark() {
            journals.push(new ArrayList<Change<K>>());
        }

        void commit() {
            if (journals.isEmpty()) {
                return;
            }
            List<Change<K>> committed = journals.pop();
            if (!journals.isEmpty()) {
                journals.peek().addAll(committed);
            }
        }

        void release() {
            if (journals.isEmpty()) {
                return;
            }
            List<Change<K>> changes = journals.pop();
            replaying = true;
            try {
                for (int i = changes.size() - 1; i >= 0; --i) {
                    Change<K> change = changes.get(i);
                    if (change.addition) {
                        removeInternal(change.key, change.id);
                    } else {
                        addInternal(change.key, change.id);
                    }
                }
            } finally {
                replaying = false;
            }
        }

        void mergeFrom(IdIndex<K> child) {
            for (Map.Entry<K, LinkedHashSet<Long>> entry : child.values.entrySet()) {
                for (long id : entry.getValue()) {
                    add(entry.getKey(), id);
                }
            }
        }
    }

    private final IdIndex<SignatureKey> signatures = new IdIndex<>();
    private final IdIndex<PositionKey> positions = new IdIndex<>();

    void clear() {
        signatures.clear();
        positions.clear();
    }

    void mark() {
        signatures.mark();
        positions.mark();
    }

    void commit() {
        signatures.commit();
        positions.commit();
    }

    void release() {
        signatures.release();
        positions.release();
    }

    void mergeFrom(RuleCandidateIndex child) {
        signatures.mergeFrom(child.signatures);
        positions.mergeFrom(child.positions);
    }

    void indexRule(Rule rule) {
        if (rule == null) {
            return;
        }
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                SignatureKey signature = signature(domain, domain.isAntc());
                signatures.add(signature, rule.getId());
                for (int position = 0; position < domain.getRange(); ++position) {
                    IArgument argument = domain.get(position);
                    long termId = argument.getType() == ArgumentType.TERM
                            ? argument.getId()
                            : WILDCARD_TERM_ID;
                    positions.add(new PositionKey(signature, position, termId), rule.getId());
                }
            }
        }
    }

    void unindexRule(Rule rule) {
        if (rule == null) {
            return;
        }
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                SignatureKey signature = signature(domain, domain.isAntc());
                signatures.remove(signature, rule.getId());
                for (int position = 0; position < domain.getRange(); ++position) {
                    IArgument argument = domain.get(position);
                    long termId = argument.getType() == ArgumentType.TERM
                            ? argument.getId()
                            : WILDCARD_TERM_ID;
                    positions.remove(new PositionKey(signature, position, termId), rule.getId());
                }
            }
        }
    }

    void collectLocal(Domain source, boolean candidateAntc, LinkedHashSet<Long> result) {
        SignatureKey signature = signature(source, candidateAntc);
        LinkedHashSet<Long> selected = signatures.get(signature);
        if (selected.isEmpty()) {
            return;
        }

        for (int position = 0; position < source.getRange(); ++position) {
            IArgument argument = source.get(position);
            if (argument.getType() != ArgumentType.TERM) {
                continue;
            }

            LinkedHashSet<Long> compatible = positions.get(
                    new PositionKey(signature, position, argument.getId()));
            compatible.addAll(positions.get(
                    new PositionKey(signature, position, WILDCARD_TERM_ID)));
            selected.retainAll(compatible);
            if (selected.isEmpty()) {
                return;
            }
        }
        result.addAll(selected);
    }

    private SignatureKey signature(Domain domain, boolean antc) {
        return new SignatureKey(domain.getPredicateId(), antc, domain.getRange());
    }
}
