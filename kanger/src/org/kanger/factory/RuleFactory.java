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
import org.kanger.interfaces.*;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.Argument;
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
//    public static final String SCHEMA_STORED = "stored";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    //    private ICache stored;
    private IStep top = null;
    private IStep bottom = null;
    //    private IStep topStored = null;
    private Mind mind = null;
    private IBase connection = null;

    private transient boolean action = false;

    public RuleFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(RuleFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
//            if(mind.getNext() == null) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

//        cache.clear();
//        stored.clear();
//        user.nextId(SCHEMA);
        if (base != null) {
//            System.err.println(" --------------------------------------------------- ");
//            lastId = base.lastId;

//            lastId = user.nextId(SCHEMA);
//            firstId = base.lastId;
            bottom = base.top;
            cache = new Escalera(mind, SCHEMA, base.cache);

//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

//            stored = new Escalera(mind, SCHEMA_STORED, base.stored);
        } else {
//            System.err.println(" =================================================== ");
            cache = new Escalera(mind, SCHEMA, null);
//            stored = new Escalera(mind, SCHEMA_STORED, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }


//    public void commit(RightFactory base) throws Exception {
//        List<Right> list = new ArrayList<>();
//        for (IStep s = base.cache.getRoot(); s != null; s = s.getNext()) {
//            if (bottom != null && s.getId() == bottom.getId()) {
//                break;
//            }
////            if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
//            Right r = (Right) s.getData();
//            r = (Right) mind.compileLine(r.getOrig().toString(), false, null);
//            list.add(r);
////                r.commit(mind);
////            } else {
////                break;
////            }
//        }
//        Boolean res = mind.analise(null, false);
//        if (res != null && res) {
//            for (Right r : list) {
//                r.setDeleted();
//            }
//            throw new RuntimeErrorException("Conflict in committed transaction");
//        }
//    }

    //TODO: Проверять дублирующиеся правила!
    public Set<Long> commit(RuleFactory base) throws Exception {

        Set<Long> list = new HashSet<>();
        if (base.cache.getRoot() != null) {
            for (IStep s = base.cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {

//                    System.err.println(((IUnit) s.getData()).getMindId() + ": " + s.getData());
                    IRule x = find((IRule) s.getData());
                    if (x != null && x.getId() != s.getId()) {
                        ((IUnit) s.getData()).setDeleted(true, base.mind);
//                        if(base.mind.getComments().get(s.getId()) != null) {
//                            base.mind.getComments().get(s.getId()).setDeleted(true, mind);
//                        }
//                        base.mind.getComments().get(s.getId()).setDeleted(true, base.mind);
//                        base.delete((Rule) s.getData());
//                        base.mind.getComments().delete(s.getId());
//                    } else {
//                        ((IUnit) s.getData()).setMind(mind);
//                        ((IUnit) s.getData()).setMindId(mind.getId());
//                        list.add(s.getId());
//                    Right r = (Right) s.getData();
//                    r.setMind(mind);
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


//        if (base.top != null) {
//            if (cache.getRoot() == null) {
//                top = base.top;
//            } else {
//                base.top.setNext(cache.getRoot());
//            }
//        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
                list.add(((IUnit) s).getId());
            }
        }

//        if (base.topStored != null) {
//            if (stored.getRoot() == null) {
//                topStored = base.topStored;
//            } else {
//                base.topStored.setNext(stored.getRoot());
//            }
//        }
//        stored.setRoot(base.stored.getRoot());
//        if (stored.getRoot() != null && stored.getTop() == null) {
//            stored.setTop(base.stored.getTop());
//        }

//        List<Right> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Right) p);
//        }
//        for (Right p : list) {
//            add(p);
//        }

//        pack();
//        update();
        action = base.isAction();
        return list;
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
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
        if (x != null && x.getId() != r.getId()) {
            ((Rule) r).setDeleted(true, mind);
            if (x.isDeleted(mind)) {
                ((Rule) x).setDeleted(false, mind);
                action = true;
            }
            return x;
        } else {
//            if (r.getId() == -1) {
//                r.setId(lastId++);
//            }
            cache.add((IUnit) r);
            if (top == null) {
                top = cache.getRoot();
            }
//            if (r.isStored()) {
//                stored.add(r.getId(), r.getId());
//                if (topStored == null) {
//                    topStored = cache.getRoot();
//                }
//            }
            ((Rule) r).getTerms().add(r.getOrigin().getId());

            for (List<Domain> list : ((Rule) r).getTree()) {
                for (Domain d : list) {
                    ((Rule) r).getTerms().addAll(d.getTerms(mind, true));
                    ((Rule) r).getPredicates().add(d.getPredicateId());
                    d.setRule(r);
//                    d.setMind(mind);
                    for (TVariable t : d.getArguments().getTVariables(mind)) {
                        t.setRule(r);
                    }
//                    mind.getDomains().add(d);
                }
            }
            action = true;
            return r;
        }
    }


//    public void reindex() throws RuntimeErrorException {
//        if (!user.isClosed()) {
//            //TODO: Переиндексация после открытия БД
//        }
//    }
//


    public void expand(Rule r) throws Exception {
        //TODO: Удалять выделенные в правила домены ??
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(mind).isEmpty()) {
                    mind.getDomains().getWaiters().add(tree.get(0));
                } else if (r.getTree().size() == 1) {
                    IRule rx = tree.get(0).setStored(mind);
//                    rx.setGenerated(false);
//                    rx.setGenerated(false);
                } else { //if(tree.get(0).getArguments().getCVariables(mind).isEmpty()){
                    //TODO: Нужен список линков для обхода. Нафиг создавать целое правило
                    IRule rx = tree.get(0).createStored(mind);
//                    rx.setGenerated(false);
//                    rx.setGenerated(false);
//                    tree.remove(0);
                }
            }

//            boolean found;
//            do {
//                found = false;
//                for(int i=0; i<r.getTree().size(); ++i) {
//                    if(r.getTree().get(i).isEmpty()) {
//                        r.getTree().remove(i);
//                        found = true;
//                        break;
//                    }
//                }
//            } while (found);

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
//                t.getTree();

//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

//    private Rule get(long id) throws Exception {
//        Rule t = (Rule) cache.get(id);
//        return t;
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((RuleFactory) mind.getNext().getRules());
        } else {
            cache.clear();
//            stored.clear();
            transaction(null);
        }
    }

//    public void delete(Rule r) throws Exception {
//        r.setDeleted(true, mind);
//        for (List<Domain> list : r.getTree()) {
//            for (Domain d : list) {
//                mind.getDomains().delete(d);
//            }
//        }
////            cache.delete(id);
////            stored.delete(id);
//    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Right r = get(id);
//        if (r != null) {
//            for (List<Domain> list : r.getTree()) {
//                for (Domain d : list) {
//                    mind.getDomains().delete(d.getId());
//                }
//            }
//            cache.delete(id);
//            stored.delete(id);
//        }
//    }

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

//    public int storedSize() {
//        return stored.size();
//    }

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
//                    a.getValue(mind).toCVariable();
//                list = new ArgList();
//                for (Argument a : domain.getArguments().convertBase(mind)) {
//                    if (a.isCVar()) {
//                        list.add(new Argument(mind.getTerms().createCVar(domain.getRight(), a.getValue(mind).getName())));
//                    } else {
//                        list.add(a);
//                    }
//                }
            }
            Rule r = new Rule(mind);
            register(r);

            for (IArgument a : list) {
                if (!a.isEmpty(mind) && ((Term) a.getValue(mind)).isXVariable()) {
                    mind.getTerms().createCVar(r, ((Term) a.getValue(mind)).getName());
                    ((Argument) a).setValue(mind, ((Term) a.getValue(mind)).getParent());
                }
            }

            Domain d = mind.getDomains().add(domain.getPredicate(), domain.isAntc(), list, r);
            r.getTree().get(0).add(d);
            r.setGenerated(true);
            r.setStored(mind);

            //TODO: 1
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

    public IRule find(ISolve domain) throws Exception {
        for (long id : cache.find(((Solve) domain).getHash(mind))) {
            IRule one = get(id);
            if (((Rule) one).equalsTo(((Solve) domain))) {
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
        Solve p = new Solve(h.getPredicate(), h.isAntc(), h.getArguments());
        IRule r = find(p);
//        if(r == null) {
//            p.setAntc(!p.isAntc());
//            r = find(p);
//        }
//        if (r != null && !r.isDeleted(m)) {
//            return r;
//        } else {
//            return null;
//        }
        return r;
    }

//    public Right find(Hypothesis h) throws Exception {
////        boolean antc = h.isAntc();
//        for (long id : cache.find(h.getHash(mind))) {
//            Right one = load(id);
//            if (one.equalsTo(h)) {
//                return one;
//            }
//        }
//
//        h.setAntc(!h.isAntc());
//        for (long id : cache.find(h.getHash(mind))) {
//            Right one = load(id);
//            if (one.equalsTo(h)) {
////                h.setAntc(antc);
//                return one;
//            }
//        }
////        h.setAntc(antc);
//        return null;
//    }

    //    public void unlink() throws Exception {
//        cache.unlink();
//        stored.unlink();
//    }
//
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

//    public long getLastId() {
//        return lastId;
//    }

//    public long getFirstId() {
//        return firstId;
//    }

    // ****************** DATABASE

//    public Iterable<Long> getDatabase(long fromId) {
//        return new Iterable<Long>() {
//            @Override
//            public Iterator iterator() {
//                return stored.iterator(fromId);
//            }
//        };
//    }


    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
//            stored.delete(((IUnit) o).getId());
        }
//        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
////            firstId = lastId;
//        } else {
//            lastId = 0;
////            firstId = 0;
//        }

    }

    public boolean isSequencedBy(RuleFactory r) {
        return top == null || (r.bottom != null && top.getId() == r.bottom.getId());
    }

    public List<Rule> getResults() throws Exception {
        List<Rule> list = new ArrayList<>();
        for (Object o : cache) {
            //TODO: ----
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
