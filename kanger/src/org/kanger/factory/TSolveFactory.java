package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.TSolve;
import org.kanger.units.TValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TSolveFactory implements Iterable<TSolve> {

    public static final String SCHEMA = "tsolves";

    private long tag = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;

    private transient boolean action = false;


    public TSolveFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TSolveFactory base) throws Exception {
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(TSolveFactory base) throws Exception {
        if (base.top != null) {
            if (cache.getRoot() == null) {
                top = base.top;
            } else {
                base.top.setNext(cache.getRoot());
            }
        }
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    ((IUnit) s.getData()).setMind(mind);
                    ((IUnit) s.getData()).setMindId(mind.getId());
                } else {
                    break;
                }
            }
        }
        action = base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public synchronized TSolve add(List<TValue> list) throws Exception {
        TSolve t = find(list);
        if (t == null) {
            t = new TSolve(list, mind);
            t.setId(mind.getUser().nextId(SCHEMA));
            t.setMindId(mind.getId());
            cache.add(t);
            if (top == null) {
                top = cache.getRoot();
            }
            action = true;
        }
        return t;
    }

//    public TValue get(TVariable tv) {
//        if (isEmpty(tv)) {
//            return null;
//        }
//        TValue v = current.get(tv);
//        return v;
//    }

//    public boolean isEmpty(TVariable tv) {
//        return /*(cache.isEmpty() && load.isEmpty() &&  ||*/ !current.containsKey(tv);
//    }

    public TSolve find(List<TValue> list) throws Exception {
        TSolve temp = new TSolve(list, mind);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(temp)) {
                return (TSolve) one;
            }
        }
        return null;
    }

    public TSolve load(long id) throws Exception {
        TSolve t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (TSolve) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private TSolve get(long id) throws Exception {
        TSolve t = (TSolve) cache.get(id);
        return t;
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void delete(TSolve v) throws IOException, ClassNotFoundException {
        v.setDeleted();
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        TValue r = get(id);
//        if (r != null) {
//            cache.delete(id);
//        }
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
//            transaction(mind.getNext().getTSolves());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public long mark() throws Exception {
        return cache.mark();
    }


    public long commit() throws Exception {
        return cache.commit();
    }

    public long release() throws Exception {
        return cache.release();
    }


//    public TValue set(TVariable tv, TValue v) {
//        if (v == null) {
//            current.remove(tv);
//        } else {
//            current.put(tv, v);
//        }
//        return v;
//    }

    public int size() throws Exception {
        return cache.size();
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

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

    public TSolve getRoot() throws Exception {
        for (TSolve s : this) {
            if (!s.isDeleted()) {
                return s;
            }
        }
        return null;
    }

    public long incTag() {
        return ++tag;
    }

//    public Map<TVariable, TValue> getCurrent() {
//        return current;
//    }

    private void forward(IStep root, long stopId, IReactor reactor) throws Exception {
        if (root.getId() <= stopId) {
            return;
        } else if (root.getNext() != null) {
            forward(root.getNext(), stopId, reactor);
            reactor.run(root.getData(mind));
        } else {
            reactor.run(root.getData(mind));
        }
    }

    public void forEach(IReactor reactor) throws Exception {
        if (cache.size() > 0) {
            long rootId;
            long newsId = -1;
            do {
                rootId = newsId;
                forward(cache.getRoot(), rootId, reactor);
                newsId = cache.getRoot().getId();
            } while (newsId > rootId);
        }
    }

//    public Iterator<TValue> iterator(TVariable tVariable) {
//        return new TValueIterator(true, tVariable);
//    }
//
//
//    public class TValueIterator implements Iterator {
//
//        private TVariable tVariable;
//        private TValue next = null;
//        private Iterator iterator = null;
//
//        public TValueIterator(boolean backward, TVariable tVariable) {
//            this.tVariable = tVariable;
//            iterator = cache.iterator(backward, -1);
//        }
//
//        @Override
//        public boolean hasNext() {
//            if (tVariable != null) {
//                while (iterator.hasNext()) {
//                    next = (TValue) iterator.next();
//                    if (next.getTVarId() == tVariable.getId()) {
//                        return true;
//                    }
//                }
//                next = null;
//            } else {
//                if (iterator.hasNext()) {
//                    next = (TValue) iterator.next();
//                    return true;
//                }
//            }
//            return false;
//        }
//
//        @Override
//        public IUnit next() {
//            return next;
//        }
//    }
//

}
