/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.factory;

import org.kanger.GeneratedCVarMaterializer;
import org.kanger.Mind;
import org.kanger.SemanticEffectTelemetry;
import org.kanger.User;
import org.kanger.enums.QueryPass;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IMind;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.primitives.Hypothesis;
import org.kanger.primitives.Solve;
import org.kanger.storage.Escalera;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * Канонический реестр Rule и транзакционный overlay одного {@link Mind}.
 * Фабрика связывает persistent/canonical Rule lifecycle, child transaction
 * visibility, generated-to-primary promotion и производные ID-only индексы,
 * но не заменяет semantic unification в Linker.
 *
 * <p><strong>Каноническое владение и lifetime.</strong> Объекты {@link Rule}
 * принадлежат {@link Escalera} cache и, для root Mind со storage, schema-specific
 * {@link IBase}. Root factory создаёт самостоятельную cache chain; child
 * {@link #transaction(RuleFactory)} строит overlay над parent cache, фиксирует
 * parent {@code top} как sequencing boundary и не наследует storage connection.
 * {@link #get(long)} всегда начинает с canonical raw lookup и только затем может
 * вернуть transaction-local effective promotion view.</p>
 *
 * <p><strong>Identity и lookup.</strong> {@link #register(IRule)} выделяет Rule
 * ID, привязывает Rule к текущему Mind generation и сохраняет текущий variable
 * index. Structural canonical lookup выполняется через hash bucket и
 * {@link Rule#equalsTo(IRule)} либо {@link Rule#equalsTo(Solve)}. Origin является
 * provenance и частью persistent Rule record, но не отдельным ключом canonical
 * identity. Логически удалённый matching Rule может быть восстановлен до
 * физического {@link #pack()} с сохранением ID.</p>
 *
 * <p><strong>Производные metadata.</strong> {@code localDomainIndex},
 * {@code localTermIndex} и {@link RuleCandidateIndex} хранят только Rule IDs.
 * Они ускоряют поиск по predicate/polarity, участвующим Term и resolved direct
 * positions, но не владеют Rule/Domain и не являются persistence authority.
 * Child lookup собирает parent IDs раньше local IDs, после чего каждый кандидат
 * гидратируется через {@link #get(long)} и повторно проверяется на logical
 * deletion. Initial root hydration публикует готовность только после согласованной
 * перестройки всех трёх индексов.</p>
 *
 * <p><strong>Candidate boundary.</strong> {@link #findByDomain(Domain, boolean)}
 * применяет signature и direct-Term positional filtering до Rule hydration.
 * {@link #findByResolvedDomain(Domain, boolean)} дополнительно разрешает текущие
 * TValue bindings и делегирует versioned batch analysis внутреннему index.
 * Candidate result остаётся лишь superset для обычной unification. Ни один
 * acceleration path не меняет canonical Rule identity и не разрешает обход
 * semantic проверки.</p>
 *
 * <p><strong>Lock-order invariant.</strong> {@code metadataLock} защищает
 * согласованность layered indexes и promotion state. Внутренний candidate lock
 * защищает только coherent ID snapshots, journals и memo publication. Rule
 * hydration, deletion checks, TValue resolution и Mind-dependent semantic
 * effects выполняются вне candidate lock; иначе возник бы цикл
 * {@code Mind.locker -> candidate lock -> Mind.locker}. Public lookup не обещает
 * общую thread-safety mutable Rule graph: lifecycle reservation и composite
 * transaction ordering остаются обязанностью {@link Mind}.</p>
 *
 * <p><strong>Generated-to-primary promotion.</strong> При insert semantics уже
 * существующий generated Rule может стать independent primary Rule без
 * немедленной мутации parent-visible raw object. {@code primaryPromotions}
 * хранит ID намерения, а effective view создаёт transaction-local projection с
 * тем же ID, tree и semantic references, но без generated/query/second/cause
 * состояния. Child typed commit передаёт promotion intent parent factory. Только
 * root {@link #pack()} материализует promotion в raw Rule, после чего
 * {@link #update()} обновляет уже persisted record. Projection не является новой
 * canonical Rule и не получает отдельный ID.</p>
 *
 * <p><strong>Два commit-протокола.</strong> {@link #commit(RuleFactory)} — typed
 * child-to-parent publication: он устраняет structural duplicates, переносит
 * child-owned Rules в parent Mind, объединяет ID indexes и promotion intent и
 * публикует surviving action. No-argument {@link #commit()} завершает только
 * верхний nested checkpoint текущей фабрики. Эти операции не взаимозаменяемы.</p>
 *
 * <p><strong>Nested checkpoint.</strong> {@link #mark()} открывает согласованный
 * frame для Escalera cache, domain/term index snapshots, candidate journals,
 * promotion set и Linker continuation {@code action}. {@link #commit()}
 * отбрасывает saved frame, сохраняя текущую effective state. {@link #release()}
 * восстанавливает все компоненты как одну pre-mark view. Поэтому Rule, его
 * acceleration IDs, promotion visibility и continuation signal не могут
 * переживать rollback по отдельности.</p>
 *
 * <p><strong>Action semantics.</strong> {@code action=true} означает surviving
 * Rule creation, resurrection либо primary promotion и участвует в решении
 * Linker о следующем проходе. Это memory-only continuation state, а не
 * persistent Rule attribute. {@link #dropAction()} относится к Linker pass
 * protocol; speculative action после released checkpoint восстанавливается из
 * отдельного journal.</p>
 *
 * <p><strong>Persistence и cleanup.</strong> Root cache miss может materialize
 * Rule из {@link IBase}. {@link #pack()} сначала материализует root promotions,
 * затем физически удаляет Rules, остающиеся logically deleted, одновременно
 * очищая их derived metadata. {@link #update()} передаёт cache changes storage и
 * отдельно обновляет records, изменённые promotion materialization. Child
 * factory не владеет storage connection. {@link #clear()} уничтожает cache
 * generation anchors, все metadata/promotion/action journals и заново строит
 * правильный root либо child transaction.</p>
 *
 * <p><strong>Ordering boundary.</strong> Порядок обхода Rule IDs не является
 * canonical identity этой фабрики. Linker обязан задавать требуемый ascending
 * либо descending порядок явно и сравнивать полный {@code long} domain через
 * {@link Long#compare(long, long)}; cache insertion order и candidate index не
 * являются заменой этого execution invariant.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Rule следует получать
 * через фабрику актуального Mind, не удерживать promotion projection как durable
 * object, не считать ID index semantic authority, не гидратировать Rule под
 * candidate metadata lock, не смешивать typed commit с checkpoint completion и
 * не вызывать root persistence operations из child transaction.</p>
 *
 * @see Rule
 * @see RuleCandidateIndex
 * @see DomainFactory
 * @see org.kanger.Linker
 */
public class RuleFactory implements IFactory<IRule> {

    public static final String SCHEMA = "rules";

    private ICache cache;
    private IStep top = null;
    private IStep bottom = null;
    private IBase connection = null;

    private final Mind mind;
    private volatile boolean action = false;
    private final Stack<Boolean> actionStack = new Stack<>();
    private final Object metadataLock = new Object();

    private static final class DomainKey {
        private final long predicateId;
        private final boolean antc;

        private DomainKey(long predicateId, boolean antc) {
            this.predicateId = predicateId;
            this.antc = antc;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof DomainKey)) {
                return false;
            }
            DomainKey other = (DomainKey) value;
            return predicateId == other.predicateId && antc == other.antc;
        }

        @Override
        public int hashCode() {
            int result = Long.valueOf(predicateId).hashCode();
            return 31 * result + (antc ? 1 : 0);
        }
    }

    /**
     * A transaction-layered metadata index. It stores Rule IDs only; semantic
     * objects remain owned by Escalera/IBase and are hydrated through get(id).
     */
    private RuleFactory parentIndex = null;
    private final Map<DomainKey, LinkedHashSet<Long>> localDomainIndex = new HashMap<>();
    private final Map<Long, LinkedHashSet<Long>> localTermIndex = new HashMap<>();
    private final Stack<Map<DomainKey, LinkedHashSet<Long>>> domainIndexStack = new Stack<>();
    private final Stack<Map<Long, LinkedHashSet<Long>>> termIndexStack = new Stack<>();
    private boolean domainIndexInitialized = false;
    private final RuleCandidateIndex candidateIndex = new RuleCandidateIndex();

    /**
     * Transaction-local overrides that make an already canonical generated
     * rule an independent primary rule without mutating the visible parent.
     */
    private final Set<Long> primaryPromotions = new HashSet<>();
    private final Map<Long, Rule> promotionViews = new HashMap<>();
    private final Stack<Set<Long>> promotionStack = new Stack<>();
    private final Set<Long> appliedPromotions = new HashSet<>();

    public RuleFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(RuleFactory base) throws Exception {
        top = null;
        bottom = null;
        connection = null;
        action = false;
        actionStack.clear();
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            bottom = base.top;
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
        parentIndex = base;
        synchronized (metadataLock) {
            localDomainIndex.clear();
            localTermIndex.clear();
            domainIndexStack.clear();
            termIndexStack.clear();
            domainIndexInitialized = base != null;

            primaryPromotions.clear();
            promotionViews.clear();
            promotionStack.clear();
            appliedPromotions.clear();
            candidateIndex.clear();
        }
    }

    private Map<DomainKey, LinkedHashSet<Long>> copyDomainIndexLocked() {
        Map<DomainKey, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : localDomainIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private Map<Long, LinkedHashSet<Long>> copyTermIndexLocked() {
        Map<Long, LinkedHashSet<Long>> copy = new HashMap<>();
        for (Map.Entry<Long, LinkedHashSet<Long>> entry : localTermIndex.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return copy;
    }

    private void indexRuleLocked(Rule rule) throws Exception {
        if (rule == null) {
            return;
        }
        Set<DomainKey> indexed = new HashSet<>();
        rule.getTerms().add(rule.getOriginId());
        for (List<Domain> branch : rule.getTree()) {
            for (Domain domain : branch) {
                rule.getTerms().addAll(domain.getTerms(mind, true));
                DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                if (indexed.add(key)) {
                    LinkedHashSet<Long> ids = localDomainIndex.get(key);
                    if (ids == null) {
                        ids = new LinkedHashSet<>();
                        localDomainIndex.put(key, ids);
                    }
                    ids.add(rule.getId());
                }
            }
        }
        for (long termId : rule.getTerms()) {
            LinkedHashSet<Long> ids = localTermIndex.get(termId);
            if (ids == null) {
                ids = new LinkedHashSet<>();
                localTermIndex.put(termId, ids);
            }
            ids.add(rule.getId());
        }
    }

    private void ensureDomainIndex() throws Exception {
        synchronized (metadataLock) {
            if (domainIndexInitialized) {
                return;
            }

            List<Rule> rules = new ArrayList<>();
            for (Object value : cache) {
                rules.add((Rule) value);
            }

            localDomainIndex.clear();
            localTermIndex.clear();
            candidateIndex.clear();
            for (Rule rule : rules) {
                indexRuleLocked(rule);
                candidateIndex.indexRule(rule);
            }
            // Publish readiness only after all three correlated indexes are complete.
            domainIndexInitialized = true;
        }
    }

    private void indexRule(Rule rule) throws Exception {
        synchronized (metadataLock) {
            indexRuleLocked(rule);
            candidateIndex.indexRule(rule);
            domainIndexInitialized = true;
        }
    }

    private void unindexRule(Rule rule) throws Exception {
        synchronized (metadataLock) {
            if (rule == null || !domainIndexInitialized) {
                return;
            }
            Set<DomainKey> indexed = new HashSet<>();
            for (List<Domain> branch : rule.getTree()) {
                for (Domain domain : branch) {
                    DomainKey key = new DomainKey(domain.getPredicateId(), domain.isAntc());
                    if (indexed.add(key)) {
                        LinkedHashSet<Long> ids = localDomainIndex.get(key);
                        if (ids != null) {
                            ids.remove(rule.getId());
                            if (ids.isEmpty()) {
                                localDomainIndex.remove(key);
                            }
                        }
                    }
                }
            }
            for (long termId : rule.getTerms()) {
                LinkedHashSet<Long> ids = localTermIndex.get(termId);
                if (ids != null) {
                    ids.remove(rule.getId());
                    if (ids.isEmpty()) {
                        localTermIndex.remove(termId);
                    }
                }
            }
            candidateIndex.unindexRule(rule);
        }
    }

    private Map<DomainKey, LinkedHashSet<Long>> snapshotDomainIndex() throws Exception {
        ensureDomainIndex();
        synchronized (metadataLock) {
            return copyDomainIndexLocked();
        }
    }

    private Map<Long, LinkedHashSet<Long>> snapshotTermIndex() throws Exception {
        ensureDomainIndex();
        synchronized (metadataLock) {
            return copyTermIndexLocked();
        }
    }

    private void collectDomainIds(DomainKey key, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectDomainIds(key, result);
        }
        ensureDomainIndex();
        synchronized (metadataLock) {
            LinkedHashSet<Long> local = localDomainIndex.get(key);
            if (local != null) {
                result.addAll(new LinkedHashSet<>(local));
            }
        }
    }

    private void collectTermIds(long termId, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectTermIds(termId, result);
        }
        ensureDomainIndex();
        synchronized (metadataLock) {
            LinkedHashSet<Long> local = localTermIndex.get(termId);
            if (local != null) {
                result.addAll(new LinkedHashSet<>(local));
            }
        }
    }

    private void mergeDomainIndex(RuleFactory child) throws Exception {
        Map<DomainKey, LinkedHashSet<Long>> childDomains = child.snapshotDomainIndex();
        Map<Long, LinkedHashSet<Long>> childTerms = child.snapshotTermIndex();
        ensureDomainIndex();
        synchronized (metadataLock) {
            for (Map.Entry<DomainKey, LinkedHashSet<Long>> entry : childDomains.entrySet()) {
                LinkedHashSet<Long> ids = localDomainIndex.get(entry.getKey());
                if (ids == null) {
                    ids = new LinkedHashSet<>();
                    localDomainIndex.put(entry.getKey(), ids);
                }
                ids.addAll(entry.getValue());
            }
            for (Map.Entry<Long, LinkedHashSet<Long>> entry : childTerms.entrySet()) {
                LinkedHashSet<Long> ids = localTermIndex.get(entry.getKey());
                if (ids == null) {
                    ids = new LinkedHashSet<>();
                    localTermIndex.put(entry.getKey(), ids);
                }
                ids.addAll(entry.getValue());
            }
            candidateIndex.mergeFrom(child.candidateIndex);
        }
    }

    public List<IRule> findByDomain(long predicateId, boolean antc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectDomainIds(new DomainKey(predicateId, antc), ids);
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            IRule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                result.add(rule);
            }
        }
        return result;
    }

    private void collectCandidateIds(Domain source,
                                     boolean candidateAntc,
                                     LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectCandidateIds(source, candidateAntc, result);
        }
        ensureDomainIndex();
        synchronized (metadataLock) {
            candidateIndex.collectLocal(source, candidateAntc, result);
        }
    }

    /**
     * Select by predicate/polarity/arity and direct TERM positions before any
     * Rule is hydrated. Dynamic candidate arguments are indexed as wildcards.
     */
    public List<IRule> findByDomain(Domain source, boolean candidateAntc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectCandidateIds(source, candidateAntc, ids);
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            IRule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                result.add(rule);
            }
        }
        return result;
    }

    private void collectResolvedCandidateIds(Domain source,
                                             boolean candidateAntc,
                                             LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectResolvedCandidateIds(source, candidateAntc, result);
        }
        ensureDomainIndex();
        candidateIndex.collectResolvedLocal(source, candidateAntc, mind, result);
    }

    /**
     * Resolve current query TValue assignments to term IDs before selecting
     * candidates. The returned Rules are still checked by normal unification.
     */
    public List<IRule> findByResolvedDomain(Domain source,
                                            boolean candidateAntc) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectResolvedCandidateIds(source, candidateAntc, ids);
        List<IRule> result = new ArrayList<>();
        for (long id : ids) {
            IRule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                result.add(rule);
            }
        }
        return result;
    }

    public boolean hasActiveRuleWithTerm(long termId) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectTermIds(termId, ids);
        for (long id : ids) {
            Rule rule = get(id);
            if (rule != null && !rule.isDeleted(mind)) {
                return true;
            }
        }
        return false;
    }

    private Set<Long> promotionSnapshot() {
        synchronized (metadataLock) {
            return new HashSet<>(primaryPromotions);
        }
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
        Set<Long> list = new HashSet<>();
        if (base.cache.getRoot() != null) {
            for (IStep s = base.cache.getRoot(); s != null; s = s.getNext()) {
                IUnit candidate = (IUnit) s.getData();
                if (candidate.getMindId() == base.mind.getId()) {
                    IRule existing = find((IRule) candidate);
                    if (existing != null && existing.getId() != s.getId()) {
                        if (isGenerated(existing) && !base.isGenerated((IRule) candidate)) {
                            promotePrimary(existing);
                        }
                        candidate.setDeleted(true, base.mind);
                    } else {
                        ((Rule) candidate).packCauses(base.mind);
                    }
                } else {
                    break;
                }
            }
        }

        Set<Long> childPromotions = base.promotionSnapshot();
        if (!childPromotions.isEmpty()) {
            synchronized (metadataLock) {
                primaryPromotions.addAll(childPromotions);
                promotionViews.clear();
            }
            action = true;
        }

        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
                list.add(((IUnit) s).getId());
            }
        }
        mergeDomainIndex(base);
        action = action || base.isAction();
        return list;
    }

    public void update() throws Exception {
        cache.update();
        Set<Long> applied;
        synchronized (metadataLock) {
            applied = new HashSet<>(appliedPromotions);
        }
        if (connection != null && !applied.isEmpty()) {
            for (long id : applied) {
                Rule rule = getRaw(id);
                IStep step = connection.get(id);
                if (rule != null && step != null) {
                    step.setData(rule);
                    step.update();
                }
            }
        }
        synchronized (metadataLock) {
            appliedPromotions.clear();
        }
    }

    public synchronized IRule register(IRule r) {
        ((Rule) r).setId(((User) mind.getUser()).nextId(SCHEMA));
        ((Rule) r).setMindId(mind.getId());
        ((Rule) r).setVarIndex(mind.getTerms().getVarIndex());
        return r;
    }

    public synchronized IRule add(IRule r) throws Exception {
        return add(r, false);
    }

    public synchronized IRule add(IRule r, boolean primary) throws Exception {
        IRule existing = find(r);
        if (existing != null) {
            if (existing.getId() != r.getId()) {
                ((Rule) r).setDeleted(true, mind);
                if (primary && isGenerated(existing)) {
                    existing = promotePrimary(existing);
                }
            }
            if (existing.isDeleted(mind)) {
                ((Rule) existing).setDeleted(false, mind);
                action = true;
            }
            return existing;
        } else {
            cache.add((IUnit) r);
            if (top == null) {
                top = cache.getRoot();
            }
            ((Rule) r).getTerms().add(((Rule) r).getOriginId());

            for (List<Domain> list : ((Rule) r).getTree()) {
                for (Domain d : list) {
                    ((Rule) r).getTerms().addAll(d.getTerms(mind, true));
                    ((Rule) r).getPredicates().add(d.getPredicateId());
                    d.setRule(r);
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        t.setRule(r);
                    }
                }
            }
            indexRule((Rule) r);
            action = true;
            return r;
        }
    }

    public void expand(Rule r) throws Exception {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(mind).isEmpty()) {
                    mind.getDomains().getWaiters().add(tree.get(0));
                } else if (r.getTree().size() == 1) {
                    tree.get(0).setStored(mind);
                } else {
                    tree.get(0).createStored(mind);
                }
            }
            for (Domain d : tree) {
                d.setMind(mind);
            }
        }
    }

    private Rule getRaw(long id) throws Exception {
        Rule rule = (Rule) cache.get(id);
        if (rule == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                rule = (Rule) s.getData(mind);
            }
        }
        return rule;
    }

    public Rule get(long id) throws Exception {
        return effectiveView(getRaw(id));
    }

    private boolean containsPromotion(long id) {
        synchronized (metadataLock) {
            return primaryPromotions.contains(id);
        }
    }

    private Rule effectiveView(Rule rule) throws Exception {
        if (rule == null || !isPromoted(rule.getId())) {
            return rule;
        }
        synchronized (metadataLock) {
            Rule view = promotionViews.get(rule.getId());
            if (view == null) {
                view = new Rule(mind);
                view.setId(rule.getId());
                view.setMindId(mind.getId());
                if (rule.getOriginId() != -1) {
                    view.setOrigin(mind.getTerms().get(rule.getOriginId()));
                }
                view.setVarIndex(rule.getVarIndex());
                view.setQuery(false);
                view.setGenerated(false);
                if (rule.isStored()) {
                    view.setStored(mind);
                }
                view.setSubstitutable(rule.isSubstitutable());
                view.setAbstractive(rule.isAbstractive());
                view.setSecond(false);
                view.getTree().clear();
                for (List<Domain> branch : rule.getTree()) {
                    view.getTree().add(new ArrayList<>(branch));
                }
                view.getSolves().addAll(rule.getSolves());
                view.getPredicates().addAll(rule.getPredicates());
                view.getTerms().addAll(rule.getTerms());
                promotionViews.put(rule.getId(), view);
            } else {
                view.setMind(mind);
            }
            return view;
        }
    }

    private boolean isPromoted(long id) {
        for (IMind current = mind; current != null; current = current.getNext()) {
            RuleFactory factory = (RuleFactory) current.getRules();
            if (factory.containsPromotion(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPromotedHere(IRule rule) {
        return rule != null && containsPromotion(rule.getId());
    }

    public boolean isGenerated(IRule rule) {
        return rule != null && !isPromoted(rule.getId()) && ((Rule) rule).isGenerated();
    }

    private IRule promotePrimary(IRule rule) throws Exception {
        if (rule != null && isGenerated(rule)) {
            synchronized (metadataLock) {
                primaryPromotions.add(rule.getId());
                promotionViews.remove(rule.getId());
            }
            action = true;
        }
        return get(rule.getId());
    }

    public void clear() throws Exception {
        synchronized (metadataLock) {
            primaryPromotions.clear();
            promotionViews.clear();
            promotionStack.clear();
            appliedPromotions.clear();
            actionStack.clear();
        }
        if (mind.getNext() != null) {
            transaction((RuleFactory) mind.getNext().getRules());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void mark() throws Exception {
        cache.mark();
        ensureDomainIndex();
        synchronized (metadataLock) {
            domainIndexStack.push(copyDomainIndexLocked());
            termIndexStack.push(copyTermIndexLocked());
            promotionStack.push(new HashSet<>(primaryPromotions));
            candidateIndex.mark();
            actionStack.push(action);
        }
    }

    public void commit() throws Exception {
        cache.commit();
        synchronized (metadataLock) {
            if (!domainIndexStack.isEmpty()) {
                domainIndexStack.pop();
            }
            if (!termIndexStack.isEmpty()) {
                termIndexStack.pop();
            }
            if (!promotionStack.isEmpty()) {
                promotionStack.pop();
            }
            candidateIndex.commit();
            if (!actionStack.isEmpty()) {
                actionStack.pop();
            }
        }
    }

    public void release() throws Exception {
        cache.release();
        synchronized (metadataLock) {
            if (!domainIndexStack.isEmpty()) {
                localDomainIndex.clear();
                localDomainIndex.putAll(domainIndexStack.pop());
                domainIndexInitialized = true;
            }
            if (!termIndexStack.isEmpty()) {
                localTermIndex.clear();
                localTermIndex.putAll(termIndexStack.pop());
                domainIndexInitialized = true;
            }
            if (!promotionStack.isEmpty()) {
                primaryPromotions.clear();
                primaryPromotions.addAll(promotionStack.pop());
                promotionViews.clear();
            }
            candidateIndex.release();
            if (!actionStack.isEmpty()) {
                action = actionStack.pop();
            }
        }
    }

    public int size() {
        return cache.size();
    }

    public synchronized IRule add(Domain domain) throws Exception {
        IRule rule = find(domain);
        if (rule == null) {
            rule = GeneratedCVarMaterializer.findAlphaEquivalent(this, domain, mind);
        }
        if (rule != null) {
            if (mind.getQueryPass() == QueryPass.INSERT && isGenerated(rule)) {
                rule = promotePrimary(rule);
            }
            if (rule.isDeleted(mind)) {
                ((Rule) rule).setDeleted(false, mind);
                action = true;
            }
            return rule;
        } else {
            ArgumentsList list;

            if (domain.isQuery(mind)) {
                list = domain.getArguments().convert(mind);
                for (TValue t : list.getTValues(mind, true)) {
                    t.setQuery(mind);
                }
            } else {
                list = domain.getArguments().convertBase(mind);
            }
            Rule r = new Rule(mind);
            register(r);
            if (!domain.isQuery(mind)) {
                list = GeneratedCVarMaterializer.rebindForGeneratedRule(list, domain, mind, r);
            }

            Domain d = mind.getDomains().add(domain.getPredicate(), domain.isAntc(), list, r);
            r.getTree().get(0).add(d);
            r.setGenerated(true);
            r.setStored(mind);

            if (domain.isQuery(mind)) {
                r.setQuery(true);
            }

            int save = mind.getDebugLevel();
            mind.setDebugLevel(0);
            ITerm origin = mind.getTerms().add(d.toString());
            mind.setDebugLevel(save);
            r.setOrigin(origin);

            r.getTerms().add(origin.getId());
            r.getTerms().addAll(d.getTerms(mind, true));
            r.getPredicates().add(d.getPredicateId());

            IRule inserted = add(r);
            if (inserted == r) {
                SemanticEffectTelemetry.recordGeneratedRule(r);
            }
            return inserted;
        }
    }

    public IRule store(Domain d) throws Exception {
        ((Rule) d.getRule()).setStored(mind);
        IRule r = d.getRule();
        if (r.isDeleted(mind)) {
            ((Rule) r).setDeleted(false, mind);
            action = true;
        }
        return r;
    }

    private boolean shouldPromoteInsertResult(Solve candidate, IRule existing) throws Exception {
        if (mind.getQueryPass() != QueryPass.INSERT
                || !(candidate instanceof Domain)
                || existing == null
                || !isGenerated(existing)) {
            return false;
        }
        Domain domain = (Domain) candidate;
        IRule owner = domain.getRule();
        return owner != null
                && owner.isQuery()
                && domain.isComplete()
                && !domain.isExcluded(mind);
    }

    public IRule find(Solve domain) throws Exception {
        for (long id : cache.find(domain.getHash(mind))) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(domain)) {
                if (shouldPromoteInsertResult(domain, one)) {
                    one = promotePrimary(one);
                }
                return one;
            }
        }
        return null;
    }

    public IRule find(IRule rule) throws Exception {
        IRule self = null;
        for (long id : cache.find(((Rule) rule).getHash())) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(rule)) {
                if (one.getId() != rule.getId()) {
                    return one;
                }
                self = one;
            }
        }
        return self;
    }

    public IRule find(Hypothesis h) throws Exception {
        Solve p = new Solve((Predicate) h.getPredicate(), h.isAntc(), h.getArguments());
        return find(p);
    }

    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

    @Override
    public Iterator iterator() {
        final Iterator raw = cache.iterator();
        return new Iterator() {
            @Override
            public boolean hasNext() {
                return raw.hasNext();
            }

            @Override
            public Object next() {
                Object value = raw.next();
                try {
                    return effectiveView((Rule) value);
                } catch (Exception error) {
                    System.err.println(new Date());
                    throw new IllegalStateException(error);
                }
            }

            @Override
            public void remove() {
                raw.remove();
            }
        };
    }

    private void applyPromotions() throws Exception {
        if (mind.getNext() != null) {
            return;
        }
        Set<Long> promotions;
        synchronized (metadataLock) {
            if (primaryPromotions.isEmpty()) {
                return;
            }
            promotions = new HashSet<>(primaryPromotions);
        }
        for (long id : promotions) {
            Rule rule = getRaw(id);
            if (rule != null) {
                rule.setGenerated(false);
                rule.setQuery(false);
                rule.setSecond(false);
                rule.getCauses().clear();
                synchronized (metadataLock) {
                    appliedPromotions.add(id);
                }
            }
        }
        synchronized (metadataLock) {
            primaryPromotions.clear();
            promotionViews.clear();
        }
    }

    public void pack() throws Exception {
        applyPromotions();
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            unindexRule((Rule) o);
            cache.delete(((IUnit) o).getId());
        }
    }

    public boolean isSequencedBy(RuleFactory r) {
        return top == null || (r.bottom != null && top.getId() == r.bottom.getId());
    }

    public List<Rule> getResults() throws Exception {
        List<Rule> list = new ArrayList<>();
        for (Object o : this) {
            Rule rule = get(((IUnit) o).getId());
            if (rule.getMind().getId() == mind.getId()) {
                list.add(rule);
            }
        }
        return list;
    }

    public Rule getTop() throws Exception {
        IStep s = cache.getRoot();
        if (s != null) {
            return effectiveView((Rule) s.getData(mind));
        } else {
            return null;
        }
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }
}
