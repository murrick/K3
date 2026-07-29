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
import java.util.Map;
import java.util.Set;
import java.util.Stack;

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
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }
        if (base != null) {
            bottom = base.top;
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
        primaryPromotions.clear();
        promotionViews.clear();
        promotionStack.clear();
        appliedPromotions.clear();
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

        if (!base.primaryPromotions.isEmpty()) {
            primaryPromotions.addAll(base.primaryPromotions);
            promotionViews.clear();
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
        action = action || base.isAction();
        return list;
    }

    public void update() throws Exception {
        cache.update();
        if (connection != null && !appliedPromotions.isEmpty()) {
            for (long id : new HashSet<>(appliedPromotions)) {
                Rule rule = getRaw(id);
                IStep step = connection.get(id);
                if (rule != null && step != null) {
                    step.setData(rule);
                    step.update();
                }
            }
        }
        appliedPromotions.clear();
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
                if (existing.isDeleted(mind)) {
                    ((Rule) existing).setDeleted(false, mind);
                    action = true;
                }
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

    private Rule effectiveView(Rule rule) throws Exception {
        if (rule == null || !isPromoted(rule.getId())) {
            return rule;
        }
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

    private boolean isPromoted(long id) {
        for (IMind current = mind; current != null; current = current.getNext()) {
            RuleFactory factory = (RuleFactory) current.getRules();
            if (factory.primaryPromotions.contains(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPromotedHere(IRule rule) {
        return rule != null && primaryPromotions.contains(rule.getId());
    }

    public boolean isGenerated(IRule rule) {
        return rule != null && !isPromoted(rule.getId()) && ((Rule) rule).isGenerated();
    }

    private IRule promotePrimary(IRule rule) throws Exception {
        if (rule != null && isGenerated(rule)) {
            primaryPromotions.add(rule.getId());
            promotionViews.remove(rule.getId());
            action = true;
        }
        return get(rule.getId());
    }

    public void clear() throws Exception {
        primaryPromotions.clear();
        promotionViews.clear();
        promotionStack.clear();
        appliedPromotions.clear();
        if (mind.getNext() != null) {
            transaction((RuleFactory) mind.getNext().getRules());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void mark() throws Exception {
        cache.mark();
        promotionStack.push(new HashSet<>(primaryPromotions));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!promotionStack.isEmpty()) {
            promotionStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!promotionStack.isEmpty()) {
            primaryPromotions.clear();
            primaryPromotions.addAll(promotionStack.pop());
            promotionViews.clear();
        }
    }

    public int size() {
        return cache.size();
    }

    public synchronized IRule add(Domain domain) throws Exception {
        IRule rule = find(domain);
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
        for (long id : cache.find(domain.getHash(mind))) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(domain)) {
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
        if (mind.getNext() != null || primaryPromotions.isEmpty()) {
            return;
        }
        for (long id : new HashSet<>(primaryPromotions)) {
            Rule rule = getRaw(id);
            if (rule != null) {
                rule.setGenerated(false);
                rule.setQuery(false);
                rule.setSecond(false);
                rule.getCauses().clear();
                appliedPromotions.add(id);
            }
        }
        primaryPromotions.clear();
        promotionViews.clear();
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
