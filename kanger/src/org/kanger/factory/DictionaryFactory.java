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
import org.kanger.units.Predicate;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class DictionaryFactory implements IFactory<ITerm> {

    public static final String SCHEMA = "dictionary";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;
    private final Mind mind;
    private int varIndex = 0;           // Счетчик C-переменных

    public DictionaryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            varIndex = base.varIndex;
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
        varIndex = Math.max(base.varIndex, varIndex);

    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized ITerm add(Object o) throws Exception {
        ITerm p = find(o);
        if (p != null) {
            ((Term) p).setDeleted(false, mind);
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
            transaction(null);
        }
    }

    public int nextVarIndex() {
        return ++varIndex;
    }

    public int getVarIndex() {
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

    private Set<Long> collectReferencedTermIds() throws Exception {
        Set<Long> referencedTermIds = collectDynamicRuleTerms();

        for (IRule candidate : mind.getSolutions()) {
            if (candidate == null || candidate.isDeleted(mind)) {
                continue;
            }
            Rule rule = (Rule) candidate;
            rule.containsTerm(Long.MIN_VALUE, mind);
            referencedTermIds.addAll(rule.getTerms());
        }

        for (IHypothesis candidate : mind.getHypothesis()) {
            if (candidate == null) {
                continue;
            }
            Hypothesis hypothesis = (Hypothesis) candidate;
            referencedTermIds.add(((Predicate) hypothesis.getPredicate()).getNameId());
            referencedTermIds.addAll(hypothesis.getArguments().getTerms(mind, true));
        }

        for (Map<String, ITerm> row : mind.getValues()) {
            for (ITerm term : row.values()) {
                referencedTermIds.add(term.getId());
            }
        }

        return referencedTermIds;
    }

    public void pack() throws Exception {
        Set<Long> referencedTermIds = collectReferencedTermIds();
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            IUnit unit = (IUnit) o;
            if (unit.isDeleted(mind) || !referencedTermIds.contains(unit.getId())) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            mind.getLog().add(LogMode.STORAGE, "Unused term wiped: " + o.toString());
            mind.unlinkCVar((ITerm) o);
            cache.delete(((IUnit) o).getId());
        }
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
