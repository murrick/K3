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
import org.kanger.interfaces.IArgument;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IRule;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.primitives.ArgumentsList;
import org.kanger.storage.Escalera;
import org.kanger.units.Domain;
import org.kanger.units.Predicate;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class DomainFactory implements IFactory<Domain> {

    //    private long lastId = 0;
//    private long firstId = 0;
    public static final String SCHEMA = "domains";

    private Set<Domain> waiters = new HashSet<>();


    private ICache cache;
    private IStep top = null;
    //    private Cache load = new Cache();
    private transient Mind mind = null;
    private IBase connection = null;

    public DomainFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DomainFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
//            if(mind.getNext() == null) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        waiters.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            waiters.addAll(base.waiters);
            cache = new Escalera(mind, SCHEMA, base.cache);

//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

        } else {
            cache = new Escalera(mind, SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }

    public void commit(DomainFactory base) throws Exception {
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
//        pack();
//        update();
        waiters.addAll(base.waiters);
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

//    public Domain add(Domain d) throws IOException, ClassNotFoundException {
//        cache.add(d);
//        return d;
//    }

//    public Domain add(Right r) throws Exception {
//        Domain p = new Domain(user);
//        p.setRight(r);
//        p.setId(lastId++);
//        cache.add(p);
//        return p;
//    }


    public synchronized Domain add(Predicate pred, boolean antc, ArgumentsList arg, IRule r) throws Exception {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            p.setDeleted(false, mind);
            return p;
        } else {
            p = new Domain(mind);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRule(r);
            p.setId(((User) mind.getUser()).nextId(SCHEMA));
            p.setMindId(mind.getId());

            if (!arg.getTVariables(mind).isEmpty()) {
                p.setSubstitutable();
            }
            if (!arg.getCVariables(mind).isEmpty()) {
                p.setAbstractive();
            }
            if (arg != null) {
                for (IArgument t : arg) {
                    p.add(t);
                }
            }
            return add(p);
        }
    }

    public synchronized Domain add(Domain p) throws Exception {
        cache.add(p);
        if (top == null) {
            top = cache.getRoot();
        }
        return p;
    }

    public Domain find(Predicate pred, boolean antc, ArgumentsList arg, IRule r) throws Exception {
        Domain temp = new Domain(pred, antc, arg, r);
        return find(temp);
//        temp.setUser(user);
    }

    public Domain find(Domain d) throws Exception {
        for (long id : cache.find(d.getHash())) {
            IUnit one = get(id);
            if (one.equalsTo(d)) {
                return (Domain) one;
            }
        }
        return null;
    }

    @Override
    public Domain get(long id) throws Exception {
        Domain t = (Domain) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Domain) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

//    private Domain get(long id) throws Exception {
//        Domain t = (Domain) cache.get(id);
//        return t;
//    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            waiters.remove(o);
            cache.delete(((IUnit) o).getId());
        }
//        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
//            firstId = lastId;
//        } else {
//            lastId = 0;
//            firstId = 0;
//        }

    }

//    public void delete(Domain d) throws Exception {
//        d.setDeleted(true, mind);
//        for (TVariable t : d.getArguments().getTVariables(mind)) {
//            mind.getTVars().delete(t);
//        }
//        for (Function f : d.getArguments().getFunctions(mind)) {
//            mind.getFunctions().delete(f);
//        }
//        for (TValue v : d.getArguments().getTValues(mind, true)) {
//            if (v.getMindId() == mind.getId()) {
//                mind.getTValues().delete(v);
//            }
//        }
//
////            for (TValue v : mind.getTValues()) {
////                Set<Cause> toDelete = new HashSet<>();
////                for (Cause c : v.getCauses()) {
////                    if (c.getSrcId() == d.getId() || c.getDstId() == d.getId()) {
////                        toDelete.add(c);
////                    }
////                }
////                if (!toDelete.isEmpty()) {
////                    v.getCauses().removeAll(toDelete);
////                }
////            }
////            mind.getTValues().update();
////            waiters.remove(d);
////            cache.delete(id);
//    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Domain d = get(id);
//        if (d != null) {
//            for (TVariable t : d.getArguments().getTVariables(true)) {
//                mind.getTVars().delete(t.getId());
//            }
//            for (TValue v : mind.getTValues()) {
//                Set<Cause> toDelete = new HashSet<>();
//                for (Cause c : v.getCauses()) {
//                    if (c.getSrcId() == d.getId() || c.getDstId() == d.getId()) {
//                        toDelete.add(c);
//                    }
//                }
//                if (!toDelete.isEmpty()) {
//                    v.getCauses().removeAll(toDelete);
//                }
//            }
//            mind.getTValues().update();
//            waiters.remove(d);
//            cache.delete(id);
//        }
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getDomains());
        } else {
            cache.clear();
            transaction(null);
        }
    }

//    public void unlink() throws Exception {
//        cache.unlink();
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

    @Override
    public int size() throws Exception {
        return cache.size();
    }

    public Set<Domain> getWaiters() {
        return waiters;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Override
    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

}
