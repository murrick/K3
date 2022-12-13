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
import org.kanger.interfaces.IFactory;
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
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class RuleFactory implements IFactory<IRule> {

    public static final String SCHEMA = "rules";

    private ICache cache;
    private IStep top = null;
    private IStep bottom = null;
    private IBase connection = null;

    private final Mind mind;
    private boolean action = false;

    public RuleFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(RuleFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            bottom = base.top;
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public Set<Long> commit(RuleFactory base) throws Exception {
        Set<Long> list = new HashSet<>();
        if (base.cache.getRoot() != null) {
            for (IStep s = base.cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    IRule x = find((IRule) s.getData());
                    if (x != null && x.getId() != s.getId()) {
                        ((IUnit) s.getData()).setDeleted(true, base.mind);
                    } else {
                        ((Rule) s.getData()).packCauses(base.mind);
                    }
                } else {
                    break;
                }
            }
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
        action = base.isAction();
        return list;
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized IRule register(IRule r) {
        ((Rule) r).setId(((User) mind.getUser()).nextId(SCHEMA));
        ((Rule) r).setMindId(mind.getId());
        ((Rule) r).setVarIndex(mind.getTerms().getVarIndex());
        return r;
    }

    public synchronized IRule add(IRule r) throws Exception {
        IRule x = find(r);
        if (x != null) {
            if (x.getId() != r.getId()) {
                ((Rule) r).setDeleted(true, mind);
                if (x.isDeleted(mind)) {
                    ((Rule) x).setDeleted(false, mind);
                    action = true;
                }
            }
            return x;
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
            action = true;
            return r;
        }
    }


    /**
     * Анализ ветвей дерева. Если в ветви только один домен<br>
     * - Если этот домен содержит t-переменные - то добавляем его в список "ждунов" - кандидатов для подстановки.<br>
     * - Если домен НЕ содержит t-переменных и ветка является единственной в дереве - помечаем его как утверждение.<br>
     * - Если домен НЕ содержит t-переменных, но в дереве есть другие ветки - создаем утверждение как отдельное правило.
     *
     * @param r Анализируемое правило
     * @throws Exception
     */
    public void expand(Rule r) throws Exception {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (tree.get(0).isSubstitutable()) {
                    mind.getDomains().getWaiters().add(tree.get(0));
                } else if (r.getTree().size() == 1) {
                    IRule rx = tree.get(0).setStored(mind);
                } else {
                    IRule rx = tree.get(0).createStored(mind);
                }
            }
            for (Domain d : tree) {
                d.setMind(mind);
            }
        }
    }

    public IRule get(long id) throws Exception {
        Rule t = (Rule) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Rule) s.getData(mind);
            }
        }
        return t;
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((RuleFactory) mind.getNext().getRules());
        } else {
            cache.clear();
            transaction(null);
        }
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

    public int size() {
        return cache.size();
    }

    public synchronized IRule add(Domain domain) throws Exception {
        IRule p = find(domain);
        if (p != null) {
            if (p.isDeleted(mind)) {
                ((Rule) p).setDeleted(false, mind);
                action = true;
            }
            return p;
        } else {
            ArgumentsList list = null;

            if (domain.isQuery(mind)) {
                list = domain.getArguments().convert(mind);
                for (TValue t : list.getTValues(mind, true)) t.setQuery(mind);
            } else {
                list = domain.getArguments().convertBase(mind);
            }
            Rule r = new Rule(mind);
            register(r);

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

            return add(r);
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

    public IRule find(Solve domain) throws Exception {
        for (long id : cache.find((domain).getHash(mind))) {
            IRule one = get(id);
            if (((Rule) one).equalsTo((domain))) {
                return one;
            }
        }
        return null;
    }

    public IRule find(IRule rule) throws Exception {
        for (long id : cache.find(((Rule) rule).getHash())) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(rule)) {
                return one;
            }
        }
        return null;
    }

    public IRule find(Hypothesis h) throws Exception {
        Solve p = new Solve((Predicate) h.getPredicate(), h.isAntc(), h.getArguments());
        IRule r = find(p);
        return r;
    }

    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public boolean isSequencedBy(RuleFactory r) {
        return top == null || (r.bottom != null && top.getId() == r.bottom.getId());
    }

    public List<Rule> getResults() throws Exception {
        List<Rule> list = new ArrayList<>();
        for (Object o : cache) {
            o = get(((IUnit) o).getId());
            if (((IUnit) o).getMind().getId() == mind.getId()) {
                list.add((Rule) o);
            }
        }
        return list;
    }

    public Rule getTop() throws Exception {
        IStep s = cache.getRoot();
        if (s != null) {
            return (Rule) s.getData(mind);
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
