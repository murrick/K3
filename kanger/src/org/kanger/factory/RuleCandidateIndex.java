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
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Compact in-memory candidate metadata. Only Rule IDs are retained; Rules and
 * Domains remain owned by Escalera/IBase and are hydrated after candidate
 * selection.
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
            if (this == value) return true;
            if (!(value instanceof SignatureKey)) return false;
            SignatureKey other = (SignatureKey) value;
            return predicateId == other.predicateId && antc == other.antc && arity == other.arity;
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
            if (this == value) return true;
            if (!(value instanceof PositionKey)) return false;
            PositionKey other = (PositionKey) value;
            return position == other.position && termId == other.termId && signature.equals(other.signature);
        }

        @Override
        public int hashCode() {
            int result = signature.hashCode();
            result = 31 * result + position;
            return 31 * result + Long.valueOf(termId).hashCode();
        }
    }

    private static final class BatchKey {
        private final SignatureKey signature;
        private final boolean sourceGenerated;

        private BatchKey(SignatureKey signature, boolean sourceGenerated) {
            this.signature = signature;
            this.sourceGenerated = sourceGenerated;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof BatchKey)) return false;
            BatchKey other = (BatchKey) value;
            return sourceGenerated == other.sourceGenerated
                    && signature.equals(other.signature);
        }

        @Override
        public int hashCode() {
            return 31 * signature.hashCode() + (sourceGenerated ? 1 : 0);
        }
    }

    private static final class BatchSummary {
        private final LinkedHashSet<Long> batchedIds;

        private BatchSummary(LinkedHashSet<Long> batchedIds) {
            this.batchedIds = batchedIds;
        }
    }

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
            if (ids == null || !ids.remove(id)) return false;
            if (ids.isEmpty()) values.remove(key);
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
            if (journals.isEmpty()) return;
            List<Change<K>> committed = journals.pop();
            if (!journals.isEmpty()) journals.peek().addAll(committed);
        }

        void release() {
            if (journals.isEmpty()) return;
            List<Change<K>> changes = journals.pop();
            replaying = true;
            try {
                for (int i = changes.size() - 1; i >= 0; --i) {
                    Change<K> change = changes.get(i);
                    if (change.addition) removeInternal(change.key, change.id);
                    else addInternal(change.key, change.id);
                }
            } finally {
                replaying = false;
            }
        }

        Map<K, LinkedHashSet<Long>> snapshot() {
            Map<K, LinkedHashSet<Long>> copy = new HashMap<>();
            for (Map.Entry<K, LinkedHashSet<Long>> entry : values.entrySet()) {
                copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return copy;
        }

        void mergeFrom(Map<K, LinkedHashSet<Long>> childValues) {
            for (Map.Entry<K, LinkedHashSet<Long>> entry : childValues.entrySet()) {
                for (long id : entry.getValue()) add(entry.getKey(), id);
            }
        }
    }

    private static final class Snapshot {
        private final Map<SignatureKey, LinkedHashSet<Long>> signatures;
        private final Map<SignatureKey, LinkedHashSet<Long>> fallbackSignatures;
        private final Map<PositionKey, LinkedHashSet<Long>> positions;

        private Snapshot(Map<SignatureKey, LinkedHashSet<Long>> signatures,
                         Map<SignatureKey, LinkedHashSet<Long>> fallbackSignatures,
                         Map<PositionKey, LinkedHashSet<Long>> positions) {
            this.signatures = signatures;
            this.fallbackSignatures = fallbackSignatures;
            this.positions = positions;
        }
    }

    /**
     * One guard protects the three correlated indexes and their transaction
     * journals. Candidate reads must observe one coherent version rather than
     * copying a LinkedHashSet while a concurrent child commit mutates it.
     */
    private final ReentrantReadWriteLock guard = new ReentrantReadWriteLock();
    private final Lock readLock = guard.readLock();
    private final Lock writeLock = guard.writeLock();

    private final IdIndex<SignatureKey> signatures = new IdIndex<>();
    private final IdIndex<SignatureKey> fallbackSignatures = new IdIndex<>();
    private final IdIndex<PositionKey> positions = new IdIndex<>();
    private final Map<Mind, Map<BatchKey, BatchSummary>> batchSummaries = new WeakHashMap<>();

    void clear() {
        writeLock.lock();
        try {
            signatures.clear();
            fallbackSignatures.clear();
            positions.clear();
            batchSummaries.clear();
        } finally {
            writeLock.unlock();
        }
    }

    void mark() {
        writeLock.lock();
        try {
            signatures.mark();
            fallbackSignatures.mark();
            positions.mark();
        } finally {
            writeLock.unlock();
        }
    }

    void commit() {
        writeLock.lock();
        try {
            signatures.commit();
            fallbackSignatures.commit();
            positions.commit();
        } finally {
            writeLock.unlock();
        }
    }

    void release() {
        writeLock.lock();
        try {
            signatures.release();
            fallbackSignatures.release();
            positions.release();
        } finally {
            writeLock.unlock();
        }
    }

    private Snapshot snapshot() {
        readLock.lock();
        try {
            return new Snapshot(signatures.snapshot(),
                    fallbackSignatures.snapshot(), positions.snapshot());
        } finally {
            readLock.unlock();
        }
    }

    void mergeFrom(RuleCandidateIndex child) {
        if (child == null || child == this) return;
        Snapshot childSnapshot = child.snapshot();
        writeLock.lock();
        try {
            signatures.mergeFrom(childSnapshot.signatures);
            fallbackSignatures.mergeFrom(childSnapshot.fallbackSignatures);
            positions.mergeFrom(childSnapshot.positions);
            batchSummaries.clear();
        } finally {
            writeLock.unlock();
        }
    }

    private boolean positionalEligible(Rule rule) throws Exception {
        return !rule.isGenerated()
                && !rule.isQuery()
                && rule.getTree().size() == 1
                && rule.getTree().get(0).size() == 1;
    }

    void indexRule(Rule rule) throws Exception {
        if (rule == null) return;
        writeLock.lock();
        try {
            batchSummaries.clear();
            boolean positional = positionalEligible(rule);
            for (List<Domain> branch : rule.getTree()) {
                for (Domain domain : branch) {
                    SignatureKey signature = signature(domain, domain.isAntc());
                    signatures.add(signature, rule.getId());
                    if (!positional) {
                        fallbackSignatures.add(signature, rule.getId());
                        continue;
                    }
                    for (int position = 0; position < domain.getRange(); ++position) {
                        IArgument argument = domain.get(position);
                        long termId = argument.getType() == ArgumentType.TERM
                                ? argument.getId() : WILDCARD_TERM_ID;
                        positions.add(new PositionKey(signature, position, termId), rule.getId());
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    void unindexRule(Rule rule) throws Exception {
        if (rule == null) return;
        writeLock.lock();
        try {
            batchSummaries.clear();
            for (List<Domain> branch : rule.getTree()) {
                for (Domain domain : branch) {
                    SignatureKey signature = signature(domain, domain.isAntc());
                    signatures.remove(signature, rule.getId());
                    fallbackSignatures.remove(signature, rule.getId());
                    for (int position = 0; position < domain.getRange(); ++position) {
                        IArgument argument = domain.get(position);
                        long exactOrWildcard = argument.getType() == ArgumentType.TERM
                                ? argument.getId() : WILDCARD_TERM_ID;
                        positions.remove(new PositionKey(signature, position, exactOrWildcard), rule.getId());
                        if (exactOrWildcard != WILDCARD_TERM_ID) {
                            positions.remove(new PositionKey(signature, position, WILDCARD_TERM_ID), rule.getId());
                        }
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    void collectLocal(Domain source, boolean candidateAntc,
                      LinkedHashSet<Long> result) throws Exception {
        readLock.lock();
        try {
            SignatureKey signature = signature(source, candidateAntc);
            LinkedHashSet<Long> selected = signatures.get(signature);
            if (selected.isEmpty()) return;
            LinkedHashSet<Long> fallback = fallbackSignatures.get(signature);
            for (int position = 0; position < source.getRange(); ++position) {
                IArgument argument = source.get(position);
                if (argument.getType() != ArgumentType.TERM) continue;
                LinkedHashSet<Long> compatible = positions.get(
                        new PositionKey(signature, position, argument.getId()));
                compatible.addAll(positions.get(
                        new PositionKey(signature, position, WILDCARD_TERM_ID)));
                compatible.addAll(fallback);
                selected.retainAll(compatible);
                if (selected.isEmpty()) return;
            }
            result.addAll(selected);
        } finally {
            readLock.unlock();
        }
    }

    private boolean batchGeneratedNonSubstitutablePair(Domain source,
                                                         Rule candidate,
                                                         boolean candidateAntc,
                                                         Mind mind) throws Exception {
        if (source.isSubstitutable()) {
            return false;
        }
        Rule sourceRule = (Rule) source.getRule();
        if (!sourceRule.isGenerated() && !candidate.isGenerated()) {
            return false;
        }

        List<Domain> matched = new ArrayList<>();
        for (List<Domain> branch : candidate.getTree()) {
            for (Domain domain : branch) {
                if (domain.getPredicateId() == source.getPredicateId()
                        && domain.isAntc() == candidateAntc
                        && domain.getRange() == source.getRange()) {
                    if (domain.isSubstitutable()) {
                        return false;
                    }
                    matched.add(domain);
                }
            }
        }
        if (matched.isEmpty()) {
            return false;
        }

        source.setUsed(mind);
        sourceRule.setUsed(mind);
        for (Domain domain : matched) {
            domain.setUsed(mind);
        }
        candidate.setUsed(mind);
        return true;
    }

    private BatchSummary batchSummary(Domain source,
                                      boolean candidateAntc,
                                      Mind mind,
                                      LinkedHashSet<Long> selected) throws Exception {
        Rule sourceRule = (Rule) source.getRule();
        Mind activeMind = sourceRule.getMind() == null ? mind : sourceRule.getMind();
        SignatureKey signature = signature(source, candidateAntc);
        BatchKey key = new BatchKey(signature, sourceRule.isGenerated());

        Map<BatchKey, BatchSummary> byKey = batchSummaries.get(activeMind);
        if (byKey == null) {
            byKey = new HashMap<>();
            batchSummaries.put(activeMind, byKey);
        }
        BatchSummary summary = byKey.get(key);
        if (summary == null) {
            LinkedHashSet<Long> batchedIds = new LinkedHashSet<>();
            for (long id : selected) {
                Rule candidate = (Rule) activeMind.getRules().get(id);
                if (candidate != null
                        && batchGeneratedNonSubstitutablePair(
                        source, candidate, candidateAntc, activeMind)) {
                    batchedIds.add(id);
                }
            }
            summary = new BatchSummary(batchedIds);
            byKey.put(key, summary);
        } else if (!summary.batchedIds.isEmpty()) {
            source.setUsed(activeMind);
            sourceRule.setUsed(activeMind);
        }
        return summary;
    }

    void collectResolvedLocal(Domain source, boolean candidateAntc, Mind mind,
                              LinkedHashSet<Long> result) throws Exception {
        // batchSummary is a mutable per-Mind memo, therefore the resolved path
        // uses the write side of the same guard.
        writeLock.lock();
        try {
            SignatureKey signature = signature(source, candidateAntc);
            LinkedHashSet<Long> selected = signatures.get(signature);
            if (selected.isEmpty()) return;
            LinkedHashSet<Long> fallback = fallbackSignatures.get(signature);
            for (int position = 0; position < source.getRange(); ++position) {
                IArgument argument = source.get(position);
                Long termId = resolvedTermId(argument, mind);
                if (termId == null) continue;
                LinkedHashSet<Long> compatible = positions.get(
                        new PositionKey(signature, position, termId));
                compatible.addAll(positions.get(
                        new PositionKey(signature, position, WILDCARD_TERM_ID)));
                compatible.addAll(fallback);
                selected.retainAll(compatible);
                if (selected.isEmpty()) return;
            }
            if (!source.isSubstitutable()) {
                BatchSummary summary = batchSummary(source, candidateAntc, mind, selected);
                selected.removeAll(summary.batchedIds);
            }
            result.addAll(selected);
        } finally {
            writeLock.unlock();
        }
    }

    private Long resolvedTermId(IArgument argument, Mind mind) throws Exception {
        if (argument.getType() == ArgumentType.TERM) return argument.getId();
        if (argument.getType() == ArgumentType.TVARIABLE) {
            TVariable variable = (TVariable) argument.getObject(mind);
            TValue current = variable.getCurrent();
            return current == null ? null : current.getValueId();
        }
        return null;
    }

    private SignatureKey signature(Domain domain, boolean antc) throws Exception {
        return new SignatureKey(domain.getPredicateId(), antc, domain.getRange());
    }
}
