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
 * Внутренний ID-only индекс кандидатов, принадлежащий одному
 * {@link RuleFactory}. Он сокращает множество Rule перед unification, но не
 * владеет {@link Rule}, {@link Domain}, persistent storage либо semantic
 * identity.
 *
 * <p><strong>Представление.</strong> Индекс поддерживает три согласованные
 * структуры:</p>
 * <ul>
 *   <li>{@code signatures}: predicate ID, polarity и arity -> Rule IDs;</li>
 *   <li>{@code positions}: signature, argument position и exact/wildcard Term
 *       ID -> Rule IDs;</li>
 *   <li>{@code fallbackSignatures}: Rule IDs, для которых positional pruning
 *       небезопасен.</li>
 * </ul>
 * <p>Все коллекции сохраняют только IDs. Canonical Rule и Domain остаются в
 * Escalera/IBase контуре {@link RuleFactory} и гидратируются после selection.</p>
 *
 * <p><strong>Experimental occurrence topology.</strong> Startup modes
 * {@code factory} and {@code factory-verify} additionally retain immutable,
 * ordered Domain ID rows per Rule and predicate/polarity. Publication and
 * hydration build these rows; lookups borrow them without rebuilding. Nested
 * journals retain prior row values, child commit shares immutable rows, and
 * generation reset discards them. No knowledge objects are retained by rows.
 * The mode must be chosen before constructing any Mind in the run.</p>
 *
 * <p><strong>Positional eligibility.</strong> Exact/wildcard positional index
 * строится только для primary, non-query Rule с одним branch и одним Domain.
 * Generated, query и multi-domain Rules сохраняются в fallback signature, чтобы
 * ускорение не стало semantic filter. Non-Term candidate argument индексируется
 * как wildcard {@code -1}; обычная unification остаётся окончательной проверкой.</p>
 *
 * <p><strong>Checkpoint journals.</strong> Каждый внутренний {@code IdIndex}
 * хранит nested delta journals. {@link #mark()} открывает journal во всех трёх
 * картах, {@link #commit()} сливает inner delta во внешний frame, а
 * {@link #release()} воспроизводит changes в обратном порядке. Это derived
 * metadata checkpoint; canonical Rule rollback выполняет owning factory.
 * {@link #mergeFrom(RuleCandidateIndex)} копирует child snapshot по IDs и не
 * передаёт object ownership.</p>
 *
 * <p><strong>Generation и memo.</strong> Каждая mutation, release, merge либо
 * clear увеличивает {@code version} и очищает per-Mind batch summaries.
 * {@link WeakHashMap} не является persistence layer: он ограничивает lifetime
 * memo активными Mind generations. Summary публикуется только если observed
 * version не изменилась; устаревший результат может использоваться для текущего
 * вызова, но не кэшируется как новая authoritative view.</p>
 *
 * <p><strong>Resolved selection phases.</strong>
 * {@link #collectResolvedLocal(Domain, boolean, Mind, LinkedHashSet)} строго
 * разделяет четыре стадии:</p>
 * <ol>
 *   <li>разрешение direct Term/TVariable -> TValue bindings вне index lock;</li>
 *   <li>копирование и фильтрация Rule IDs, чтение memo/version под lock;</li>
 *   <li>гидратация Rule и Mind-dependent batching/used effects вне lock;</li>
 *   <li>условная публикация memo под lock при неизменной version.</li>
 * </ol>
 *
 * <p><strong>Lock-order invariant.</strong> Один
 * {@link ReentrantReadWriteLock} защищает coherent snapshots трёх карт,
 * journals, version и memo publication. Он не должен охватывать
 * {@code RuleFactory.get(id)}, logical deletion checks, TValue resolution либо
 * операции, способные войти в {@code Mind.locker}. Эта граница устраняет цикл
 * {@code Mind.locker -> candidate lock -> Mind.locker}. Owning factory может
 * дополнительно сериализовать metadata publication своим lock, но внутренний
 * guard не становится глобальным Mind lock.</p>
 *
 * <p><strong>Semantic batching.</strong> Для non-substitutable generated pair
 * resolved path может отметить source/candidate Domains и Rules как used и
 * исключить batched IDs из текущего candidate result. Это существующий
 * Mind-dependent semantic side effect, поэтому он вычисляется только после
 * выхода из metadata lock и memo хранится отдельно для каждого active Mind.</p>
 *
 * <p><strong>Lifetime и ограничения.</strong> Индекс создаётся вместе с
 * {@link RuleFactory}, очищается при смене transaction/cache generation и не
 * сериализуется. Возвращаемые ID sets являются snapshots/copies; вызывающий код
 * обязан гидратировать их через owning factory и повторно проверить logical
 * visibility. Класс package-private и не является public API.</p>
 *
 * @see RuleFactory
 * @see Rule
 * @see Domain
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
        private final Map<Long, OccurrenceRows> occurrences;

        private Snapshot(Map<SignatureKey, LinkedHashSet<Long>> signatures,
                         Map<SignatureKey, LinkedHashSet<Long>> fallbackSignatures,
                         Map<PositionKey, LinkedHashSet<Long>> positions,
                         Map<Long, OccurrenceRows> occurrences) {
            this.signatures = signatures;
            this.fallbackSignatures = fallbackSignatures;
            this.positions = positions;
            this.occurrences = occurrences;
        }
    }

    /** Immutable ID-only rows; repeated domain occurrences retain their order. */
    private static final class OccurrenceRows {
        private final Map<SignatureKey, long[]> rows = new HashMap<>();
        private final Map<Long, String> argumentPlans;

        private OccurrenceRows(Rule rule) throws Exception {
            argumentPlans = Boolean.getBoolean("kanger.experiment.argumentPlan") ? new HashMap<Long, String>() : null;
            Map<SignatureKey, List<Long>> building = new HashMap<>();
            for (List<Domain> branch : rule.getTree()) {
                for (Domain domain : branch) {
                    if (argumentPlans != null) {
                        StringBuilder plan = new StringBuilder();
                        for (int i = 0; i < domain.getRange(); i++)
                            plan.append(domain.get(i).getType() == ArgumentType.TVARIABLE ? 'v' : '.');
                        argumentPlans.put(domain.getId(), plan.toString());
                    }
                    // Arity zero intentionally denotes predicate/polarity only:
                    // this reproduces Linker's gate without adding restrictions.
                    SignatureKey key = new SignatureKey(domain.getPredicateId(), domain.isAntc(), 0);
                    List<Long> ids = building.get(key);
                    if (ids == null) { ids = new ArrayList<>(); building.put(key, ids); }
                    ids.add(domain.getId());
                }
            }
            for (Map.Entry<SignatureKey, List<Long>> entry : building.entrySet()) {
                long[] ids = new long[entry.getValue().size()];
                for (int i = 0; i < ids.length; i++) ids[i] = entry.getValue().get(i);
                rows.put(entry.getKey(), ids);
            }
        }
    }

    /**
     * One guard protects the correlated indexes and their transaction
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
    private long version = 0L;
    private final boolean occurrencesEnabled =
            "factory".equals(System.getProperty("kanger.experiment.latent"))
            || "factory-verify".equals(System.getProperty("kanger.experiment.latent"));
    private final Map<Long, OccurrenceRows> occurrences = new HashMap<>();
    private final Stack<Map<Long, OccurrenceRows>> occurrenceJournals = new Stack<>();
    private long occurrenceBuilds;
    private static final long[] NO_OCCURRENCES = new long[0];

    boolean hasOccurrenceIndex() { return occurrencesEnabled; }

    String findArgumentPlan(long ruleId, long domainId) {
        readLock.lock();
        try {
            OccurrenceRows value = occurrences.get(ruleId);
            if (value == null) return null;
            if (value.argumentPlans == null || !value.argumentPlans.containsKey(domainId))
                throw new AssertionError("Missing structural argument plan: " + ruleId + "/" + domainId);
            return value.argumentPlans.get(domainId);
        } finally { readLock.unlock(); }
    }

    private void replaceOccurrences(long id, OccurrenceRows rows) {
        if (!occurrenceJournals.isEmpty() && !occurrenceJournals.peek().containsKey(id)) {
            occurrenceJournals.peek().put(id, occurrences.get(id));
        }
        if (rows == null) occurrences.remove(id);
        else occurrences.put(id, rows);
    }

    /** Borrowed immutable array; null means this layer does not own the Rule. */
    long[] findOccurrences(long ruleId, long predicateId, boolean antc) {
        readLock.lock();
        try {
            OccurrenceRows value = occurrences.get(ruleId);
            if (value == null) return null;
            long[] ids = value.rows.get(new SignatureKey(predicateId, antc, 0));
            return ids == null ? NO_OCCURRENCES : ids;
        } finally { readLock.unlock(); }
    }

    void clear() {
        writeLock.lock();
        try {
            signatures.clear();
            fallbackSignatures.clear();
            positions.clear();
            occurrences.clear();
            occurrenceJournals.clear();
            batchSummaries.clear();
            ++version;
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
            if (occurrencesEnabled) occurrenceJournals.push(new HashMap<Long, OccurrenceRows>());
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
            if (!occurrenceJournals.isEmpty()) {
                Map<Long, OccurrenceRows> changes = occurrenceJournals.pop();
                if (!occurrenceJournals.isEmpty()) {
                    for (Map.Entry<Long, OccurrenceRows> entry : changes.entrySet()) {
                        if (!occurrenceJournals.peek().containsKey(entry.getKey())) {
                            occurrenceJournals.peek().put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
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
            if (!occurrenceJournals.isEmpty()) {
                for (Map.Entry<Long, OccurrenceRows> entry : occurrenceJournals.pop().entrySet()) {
                    if (entry.getValue() == null) occurrences.remove(entry.getKey());
                    else occurrences.put(entry.getKey(), entry.getValue());
                }
            }
            batchSummaries.clear();
            ++version;
        } finally {
            writeLock.unlock();
        }
    }

    private Snapshot snapshot() {
        readLock.lock();
        try {
            return new Snapshot(signatures.snapshot(),
                    fallbackSignatures.snapshot(), positions.snapshot(), new HashMap<>(occurrences));
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
            for (Map.Entry<Long, OccurrenceRows> entry : childSnapshot.occurrences.entrySet()) {
                replaceOccurrences(entry.getKey(), entry.getValue());
            }
            batchSummaries.clear();
            ++version;
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
        // Resolve the tree before acquiring the index lock; rows retain IDs only.
        OccurrenceRows rows = occurrencesEnabled ? new OccurrenceRows(rule) : null;
        writeLock.lock();
        try {
            if (rows != null) {
                replaceOccurrences(rule.getId(), rows);
                occurrenceBuilds++;
            }
            batchSummaries.clear();
            ++version;
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
            if (occurrencesEnabled) replaceOccurrences(rule.getId(), null);
            batchSummaries.clear();
            ++version;
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

    private BatchSummary computeBatchSummary(Domain source,
                                             boolean candidateAntc,
                                             Mind activeMind,
                                             LinkedHashSet<Long> selected) throws Exception {
        LinkedHashSet<Long> batchedIds = new LinkedHashSet<>();
        for (long id : selected) {
            Rule candidate = (Rule) activeMind.getRules().get(id);
            if (candidate != null
                    && batchGeneratedNonSubstitutablePair(
                    source, candidate, candidateAntc, activeMind)) {
                batchedIds.add(id);
            }
        }
        return new BatchSummary(batchedIds);
    }

    private void markCachedBatchUsed(Domain source,
                                     Rule sourceRule,
                                     Mind activeMind,
                                     BatchSummary summary) throws Exception {
        if (!summary.batchedIds.isEmpty()) {
            source.setUsed(activeMind);
            sourceRule.setUsed(activeMind);
        }
    }

    void collectResolvedLocal(Domain source, boolean candidateAntc, Mind mind,
                              LinkedHashSet<Long> result) throws Exception {
        collectResolvedLocal(source, candidateAntc, mind, result, null);
    }

    void collectResolvedLocal(Domain source, boolean candidateAntc, Mind mind,
                              LinkedHashSet<Long> result, long[] profile) throws Exception {
        long start = profile == null ? 0 : System.nanoTime();
        SignatureKey signature = signature(source, candidateAntc);
        Long[] resolvedTermIds = new Long[source.getRange()];
        for (int position = 0; position < source.getRange(); ++position) {
            resolvedTermIds[position] = resolvedTermId(source.get(position), mind);
        }

        boolean batchEligible = !source.isSubstitutable();
        Rule sourceRule = batchEligible ? (Rule) source.getRule() : null;
        Mind activeMind = batchEligible
                ? (sourceRule.getMind() == null ? mind : sourceRule.getMind())
                : null;
        BatchKey batchKey = batchEligible
                ? new BatchKey(signature, sourceRule.isGenerated())
                : null;

        LinkedHashSet<Long> selected;
        BatchSummary summary = null;
        long observedVersion;

        if (profile != null) { profile[3] += System.nanoTime() - start; start = System.nanoTime(); }
        writeLock.lock();
        try {
            selected = signatures.get(signature);
            if (selected.isEmpty()) {
                if (profile != null) ++profile[2];
                return;
            }
            LinkedHashSet<Long> fallback = fallbackSignatures.get(signature);
            for (int position = 0; position < resolvedTermIds.length; ++position) {
                Long termId = resolvedTermIds[position];
                if (termId == null) continue;
                LinkedHashSet<Long> compatible = positions.get(
                        new PositionKey(signature, position, termId));
                compatible.addAll(positions.get(
                        new PositionKey(signature, position, WILDCARD_TERM_ID)));
                compatible.addAll(fallback);
                selected.retainAll(compatible);
                if (selected.isEmpty()) return;
            }
            observedVersion = version;
            if (batchEligible) {
                Map<BatchKey, BatchSummary> byKey = batchSummaries.get(activeMind);
                summary = byKey == null ? null : byKey.get(batchKey);
            }
        } finally {
            writeLock.unlock();
            if (profile != null) profile[4] += System.nanoTime() - start;
        }

        if (profile != null) { profile[7] += selected.size(); start = System.nanoTime(); }
        if (batchEligible) {
            boolean cached = summary != null;
            if (profile != null) ++profile[cached ? 8 : 9];
            if (summary == null) {
                summary = computeBatchSummary(
                        source, candidateAntc, activeMind, selected);

                writeLock.lock();
                try {
                    if (version == observedVersion) {
                        Map<BatchKey, BatchSummary> byKey = batchSummaries.get(activeMind);
                        if (byKey == null) {
                            byKey = new HashMap<>();
                            batchSummaries.put(activeMind, byKey);
                        }
                        BatchSummary existing = byKey.get(batchKey);
                        if (existing == null) {
                            byKey.put(batchKey, summary);
                        } else {
                            summary = existing;
                            cached = true;
                        }
                    }
                } finally {
                    writeLock.unlock();
                }
            }
            if (cached) {
                markCachedBatchUsed(source, sourceRule, activeMind, summary);
            }
            selected.removeAll(summary.batchedIds);
        }
        result.addAll(selected);
        if (profile != null) profile[5] += System.nanoTime() - start;
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
