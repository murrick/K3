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

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.enums.LogMode;
import org.kanger.enums.UnitType;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IHypothesis;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Hypothesis;
import org.kanger.storage.Escalera;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Канонический словарь {@link Term}, общий для цепочки одного {@link Mind}.
 *
 * <p><strong>Представление и роль.</strong> Фабрика сопоставляет внешнему
 * значению либо внутреннему variable descriptor единственный канонический
 * {@code Term}. Она координирует identity lookup, восстановление удалённых
 * единиц, выделение идентификаторов, storage hydration, C-variable descriptors
 * и reachability cleanup; это registry/lifecycle boundary, а не простой
 * конструктор объектов.</p>
 *
 * <p><strong>Владение и публикация.</strong> Экземпляр создаётся корневым
 * {@code Mind} и сохраняет этот контекст. Штатный дочерний {@code Mind} не
 * получает отдельный dictionary overlay, а повторно использует ту же ссылку
 * через {@code getTerms()}. Поэтому canonical Term identity, cache и счётчик
 * C-переменных общие для активной Mind-цепочки и не откатываются как обычное
 * transaction-private состояние дочерней фабрики.</p>
 *
 * <p><strong>C-variable lifecycle.</strong> {@link #createCVar(IRule, ITerm,
 * ITerm)} выделяет chain-shared index, канонизирует внутренний descriptor,
 * связывает его с Rule и именем и, при наличии parent, публикует transient
 * parent/child adjacency в retained {@code Mind}. Канонический Term может
 * участвовать в persistence, но карты C-variable links являются runtime-state
 * {@code Mind} и удаляются симметрично при cleanup.</p>
 *
 * <p><strong>Persistence.</strong> {@link Escalera} удерживает каноническую
 * последовательность и cache. При открытом storage поле {@code connection}
 * указывает на schema-specific {@link IBase}, заимствованный у {@link User}:
 * cache miss может materialize Term через эту базу, а {@link #update()} —
 * передать изменения storage boundary. Владельцами generation и close остаются
 * {@code User}/{@code IData}; фабрика не приобретает close authority.</p>
 *
 * <p><strong>Generation lifecycle.</strong> Методы {@code transaction},
 * {@code commit(DictionaryFactory)} и cache checkpoints сохраняются как
 * implementation/compatibility surfaces, но штатный child {@code Mind}
 * публикует тот же canonical instance, а не отдельную фабрику для последующего
 * promotion. Поле {@code top} — локальный sequence anchor creation/splice
 * mechanics, не semantic identity и не durable database root.</p>
 *
 * <p><strong>Reachability cleanup.</strong> {@link #pack()} после первого
 * полного прохода поддерживает incremental candidate frontier. Активные Rules
 * образуют устойчивые корни; diff предыдущего и текущего Rule-term snapshot
 * возвращает потерявшие Rule-ссылку Terms в frontier. Solutions, hypotheses и
 * values являются временными корнями: удерживаемый только ими Term остаётся
 * кандидатом, чтобы быть пересмотренным после очистки projection. Удаление
 * Term одновременно разрывает его transient C-variable adjacency.</p>
 *
 * <p><strong>Инварианты и concurrency.</strong> Одно эквивалентное значение
 * должно иметь одну активную каноническую единицу. C-variable indexes должны
 * возрастать без коллизий во всей общей цепочке. Синхронизация canonical add,
 * index allocation и pack защищает эти конкретные операции, но не объявляет
 * factory iterators или возвращённые Terms независимо thread-safe.</p>
 *
 * <p><strong>Обязательства вызывающего кода.</strong> Нормальный доступ идёт
 * через актуальный {@code Mind}. Вызывающая сторона не должна считать фабрику
 * child transaction, сохранять C-variable adjacency как durable knowledge,
 * закрывать через неё общее storage либо удерживать query projections как
 * постоянное доказательство достижимости Term.</p>
 *
 * @see PredicateFactory
 * @see IFactory
 * @see Term
 */
public class DictionaryFactory implements IFactory<ITerm> {

    public static final String SCHEMA = "dictionary";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;
    private final Mind mind;
    private int varIndex = 0;           // Счетчик C-переменных

    /*
     * Terms that can become unreachable without requiring a complete
     * dictionary sweep. New Terms enter this frontier immediately. Terms
     * referenced only by transient result stores remain in it until those
     * references disappear. Terms owned by active Rules leave the frontier;
     * a later Rule-reference loss is detected from the previous/current Rule
     * term snapshots.
     */
    private final Set<Long> packCandidates = new LinkedHashSet<>();
    private Set<Long> previousRuleTerms = new HashSet<>();
    private boolean fullPackRequired = true;

    public DictionaryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) throws Exception {
        top = null;
        connection = null;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            synchronized (base) {
                varIndex = base.varIndex;
            }
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
            if (!cache.isEmpty()) {
                for (ITerm t : this) {
                    if (t.isCVariable()) {
                        varIndex = ((Term) t).getIndex();
                        break;
                    }
                }
            } else {
                varIndex = 0;           // Счетчик C-переменных
            }
        }
    }

    public void commit(DictionaryFactory base) throws Exception {
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
            }
        }
        synchronized (base) {
            varIndex = Math.max(base.varIndex, varIndex);
            /*
             * A child may introduce Terms that are not retained by any Rule.
             * Carry its cleanup frontier into the parent; otherwise an already
             * incremental parent would never reconsider those committed orphans.
             */
            packCandidates.addAll(base.packCandidates);
        }
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized ITerm add(Object o) throws Exception {
        ITerm p = find(o);
        if (p != null) {
            boolean restored = ((Term) p).isDeleted(mind);
            ((Term) p).setDeleted(false, mind);
            if (restored) {
                packCandidates.add(p.getId());
            }
            return p;
        } else {
            if (p instanceof Term) {
                ((Term) p).setMind(mind);
            } else {
                p = new Term(o, mind);
                ((Term) p).setId(((User) mind.getUser()).nextId(SCHEMA));
                ((Term) p).setMindId(mind.getId());
            }
            cache.add((IUnit) p);
            packCandidates.add(p.getId());
            if (top == null) {
                top = cache.getRoot();
            }
            return p;
        }
    }

    public Term find(Object o) throws Exception {
        Term t;
        if (o instanceof Term) {
            t = (Term) o;
        } else {
            t = new Term(o, mind);
        }
        for (long id : cache.find(t.getHash())) {
            IUnit one = get(id);
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        return null;
    }

    /**
     * Create a C-variable descriptor owned by {@code r}.
     *
     * <p>When {@code parent} is non-null, the new {@code *N} term is not a
     * concrete substitution. It is the canonical projection of the parent
     * C-variable into the independent binding scope of the target Rule. A
     * parent may therefore have several children, but at most one child for
     * each target Rule id. Reusing that rule-scoped identity prevents repeated
     * linker passes from producing an unbounded chain of equivalent variables,
     * while keeping different Rule-local sets of T-variables isolated.</p>
     *
     * <p>The child receives its target Rule before {@link Mind#linkCVar(ITerm,
     * ITerm)} publishes the transient adjacency. The adjacency belongs to the
     * active Mind lifecycle and is not persistent knowledge.</p>
     *
     * @param r target Rule whose T-variable set defines the binding scope
     * @param name source-level variable name retained for display
     * @param parent source C-variable, or {@code null} for a root descriptor
     * @return the newly allocated root or rule-scoped child descriptor
     */
    public ITerm createCVar(IRule r, ITerm name, ITerm parent) throws Exception {
        int i = nextVarIndex();
        String temp = String.format("%c%d", parent == null ? '%' : '*', i);
        ITerm t = add(temp);
        ((Term) t).setRule(r);
        ((Term) t).setIndex(i);
        ((Term) t).setName(name);
        if (parent != null) {
            mind.linkCVar(parent, t);
        }
        return t;
    }

    public Term get(long id) throws Exception {
        Term t = (Term) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Term) s.getData(mind);
            }
        }
        return t;
    }

    public int size() throws Exception {
        return cache.size();
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((DictionaryFactory) mind.getNext().getTerms());
        } else {
            cache.clear();
            packCandidates.clear();
            previousRuleTerms.clear();
            fullPackRequired = true;
            transaction(null);
        }
    }

    public synchronized int nextVarIndex() {
        return ++varIndex;
    }

    public synchronized int getVarIndex() {
        return varIndex;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator(-1);
    }

    private Set<Long> collectDynamicRuleTerms() throws Exception {
        Set<Long> dynamicTerms = new HashSet<>();
        for (IRule candidate : mind.getRules()) {
            if (candidate == null || candidate.isDeleted(mind)) {
                continue;
            }
            Rule rule = (Rule) candidate;
            // Preserve the historical dynamic traversal and its Rule.terms
            // side effect, but execute it once per Rule instead of once per
            // candidate Term.
            rule.containsTerm(Long.MIN_VALUE, mind);
            dynamicTerms.addAll(rule.getTerms());
        }
        return dynamicTerms;
    }

    private Set<Long> collectSolutionTermIds() throws Exception {
        Set<Long> solutionTermIds = new HashSet<>();
        for (IRule candidate : mind.getSolutions()) {
            if (candidate == null || candidate.isDeleted(mind)) {
                continue;
            }
            Rule rule = (Rule) candidate;
            rule.containsTerm(Long.MIN_VALUE, mind);
            solutionTermIds.addAll(rule.getTerms());
        }
        return solutionTermIds;
    }

    private Set<Long> collectHypothesisTermIds() throws Exception {
        Set<Long> hypothesisTermIds = new HashSet<>();
        for (IHypothesis candidate : mind.getHypothesis()) {
            if (candidate == null) {
                continue;
            }
            Hypothesis hypothesis = (Hypothesis) candidate;
            hypothesisTermIds.add(((org.kanger.units.Predicate) hypothesis.getPredicate()).getNameId());
            hypothesisTermIds.addAll(hypothesis.getArguments().getTerms(mind, true));
        }
        return hypothesisTermIds;
    }

    private Set<Long> collectValueTermIds() {
        Set<Long> valueTermIds = new HashSet<>();
        for (Map<String, ITerm> row : mind.getValues()) {
            for (ITerm term : row.values()) {
                valueTermIds.add(term.getId());
            }
        }
        return valueTermIds;
    }

    public synchronized void pack() throws Exception {
        Set<Long> currentRuleTerms = collectDynamicRuleTerms();

        if (!fullPackRequired) {
            for (long termId : previousRuleTerms) {
                if (!currentRuleTerms.contains(termId)) {
                    packCandidates.add(termId);
                }
            }
            Set<Long> explicitlyDeleted = mind.getDeleted().get(UnitType.TERM);
            if (explicitlyDeleted != null) {
                packCandidates.addAll(explicitlyDeleted);
            }
            if (packCandidates.isEmpty()) {
                previousRuleTerms = currentRuleTerms;
                return;
            }
        }

        List<Object> candidates = new ArrayList<>();
        if (fullPackRequired) {
            for (Object value : cache) {
                candidates.add(value);
            }
        } else {
            for (long termId : new ArrayList<>(packCandidates)) {
                Object value = get(termId);
                if (value != null) {
                    candidates.add(value);
                }
            }
        }

        Set<Long> solutionTermIds = null;
        Set<Long> hypothesisTermIds = null;
        Set<Long> valueTermIds = null;
        Set<Long> retainedCandidates = new LinkedHashSet<>();
        List<Object> toDelete = new ArrayList<>();

        for (Object o : candidates) {
            IUnit term = (IUnit) o;
            if (term.isDeleted(mind)) {
                toDelete.add(o);
                continue;
            }

            long termId = term.getId();
            if (currentRuleTerms.contains(termId)) {
                // Active Rule references are durable. If they disappear later,
                // the previous/current Rule-term diff re-enters this Term into
                // the candidate frontier.
                continue;
            }

            boolean found;
            if (solutionTermIds == null) {
                solutionTermIds = collectSolutionTermIds();
            }
            found = solutionTermIds.contains(termId);
            if (!found) {
                if (hypothesisTermIds == null) {
                    hypothesisTermIds = collectHypothesisTermIds();
                }
                found = hypothesisTermIds.contains(termId);
            }
            if (!found) {
                if (valueTermIds == null) {
                    valueTermIds = collectValueTermIds();
                }
                found = valueTermIds.contains(termId);
            }

            if (found) {
                // Secondary stores are transient, so keep watching this Term.
                retainedCandidates.add(termId);
            } else {
                toDelete.add(o);
            }
        }

        List<Long> deleteIds = new ArrayList<>(toDelete.size());
        for (Object o : toDelete) {
            mind.getLog().add(LogMode.STORAGE, "Unused term wiped: " + o.toString());
            mind.unlinkCVar((ITerm) o);
            deleteIds.add(((IUnit) o).getId());
        }
        cache.deleteAll(deleteIds);

        packCandidates.clear();
        packCandidates.addAll(retainedCandidates);
        previousRuleTerms = new HashSet<>(currentRuleTerms);
        fullPackRequired = false;
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public Term getRoot() throws Exception {
        IStep one = cache.getRoot();
        if (one != null) {
            return (Term) cache.getRoot().getData(mind);
        } else {
            return null;
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

    public void mark() throws Exception {
        cache.mark();
    }

    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }
}
