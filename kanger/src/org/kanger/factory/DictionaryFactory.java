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
import org.kanger.enums.Enums;
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
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class DictionaryFactory implements IFactory<ITerm> {

    public static final String SCHEMA = "dictionary";

    //    private Term root = null;
//    private long lastId = 0;
//    private long firstId = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    //    private Stack<Object[]> stack = new Stack<>();
    private ICache cache;
    private IStep top = null;
    //    private Cache load = new Cache();
    private transient Mind mind = null;
    private IBase connection = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) throws Exception {
//        cache.clear();
//        load.clear();
        if (mind.getNext() == null && mind.isStorageUsed()) {
//            if (mind.getNext() == null) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            varIndex = base.varIndex;
            cache = new Escalera(mind, SCHEMA, base.cache);

//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

        } else {
            cache = new Escalera(mind, SCHEMA, null);
            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
                for (ITerm t : this) {
                    if (t.isCVariable()) {
                        varIndex = ((Term) t).getIndex();
                        break;
                    }
                }
            } else {
//                lastId = 0;
//                firstId = 0;
                varIndex = 0;           // Счетчик C-переменных
            }
        }
//        firstId = user.lastId(SCHEMA);
    }

    public void commit(DictionaryFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
//        if (cache.getRoot() != null) {
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
//                    ((IUnit) s.getData()).setMind(mind);
//                    ((IUnit) s.getData()).setMindId(mind.getId());
//                } else {
//                    break;
//                }
//            }
//        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }

//        pack();
//        update();
        varIndex = Math.max(base.varIndex, varIndex);

    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = user.lastId(SCHEMA);
//            mind.getUser().getStorage(SCHEMA).flush();
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

    public ITerm createCVar(IRule r, ITerm name) throws Exception {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        ITerm t = add(temp);
        ((Term) t).setRule(r);
        ((Term) t).setIndex(i);
        ((Term) t).setName(name);
        return t;
    }

//    public ITerm createXVar(ITerm c) throws Exception {
//        ITerm t = null;
////        for(Term x : this) {
////            if(x.getParent().getId() == c.getId()) {
////                t = x;
////                break;
////            }
////        }
////        if(t == null) {
//        int i = nextVarIndex();
//        String temp = String.format("%c%d", Enums.XVC, i);
//        t = add(temp);
//        ((Term) t).setRule(((Term) c).getRule(mind));
//        ((Term) t).setIndex(i);
//        ((Term) t).setName(((Term) c).getName(mind));
////            c.getChilds().add(t.getId());
//        ((Term) t).setParent(c);
////        }
//        return t;
//    }

    public Term get(long id) throws Exception {
        Term t = (Term) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Term) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

//    private Term get(long id) throws Exception {
//        Term t = (Term) cache.get(id);
//        return t;
//    }

//    public Term load(long id) throws RuntimeErrorException {
//        Term t = null;
//        if (!user.isClosed()) {
//            t = (Term) user.getStorage(SCHEMA).get(id);
//            if (t != null) {
//                load.add(t);
//            }
//        }
//        return t;
//    }

//    public Term getRoot() {
//        return root;
//    }

//    public void setRoot(Term o) {
//        root = o;
//    }

//    private void mark() {
//        stack.push(new Object[]{root, lastId, varIndex});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Term saved = (Term) pop[0];
//            lastId = (long) pop[1];
//            varIndex = (int) pop[2];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

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

//    public long getFirstId() {
//        return firstId;
//    }

    @Override
    public Iterator iterator() {
        return cache.iterator(-1);
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            } else {
                boolean found = false;
                for (IRule r : mind.getRules()) {
                    if (!r.isDeleted(mind) && ((Rule) r).containsTerm(((IUnit) o).getId(), mind)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    for (IRule r : mind.getSolutions()) {
                        if (!r.isDeleted(mind) && ((Rule) r).containsTerm(((IUnit) o).getId(), mind)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        for (IHypothesis r : mind.getHypothesis()) {
                            if (((Hypothesis) r).containsTerm(((IUnit) o).getId(), mind)) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            for (Map<String, ITerm> row : mind.getValues()) {
                                for (ITerm t : row.values()) {
                                    if (t.getId() == ((IUnit) o).getId()) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (found) {
                                    break;
                                }
                            }
//                            if(!found) {
//                                for(Function f : mind.getFunctions()) {
//                                    if (!f.isDeleted(mind) && !f.isEmpty() && f.getValue().getId() == ((IUnit) o).getId()) {
//                                        found = true;
//                                        break;
//                                    }
//                                    if(!f.isDeleted(mind) && f.getResult().getValue(mind).getId() == ((IUnit) o).getId()) {
//                                        found = true;
//                                        break;
//                                    }
//                                }
//                            }
                        }
                    }
                }
                if (!found) {
                    toDelete.add(o);
                }
            }
        }
        for (Object o : toDelete) {
            mind.getLog().add(LogMode.STORAGE, "Unused term wiped: " + o.toString());
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
