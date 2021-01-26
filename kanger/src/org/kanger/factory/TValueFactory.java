package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.*;
import org.kanger.storage.Escalera;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TValueFactory implements Iterable<TValue> {

    public static final String SCHEMA = "tvalues";

    //    private long lastId = 0;
//    private long firstId = 0;
    private long tag = 0;

    private Map<TVariable, TValue> current = new HashMap<>();

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;
    private IBase connection = null;

    private transient boolean action = false;


    public TValueFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TValueFactory base) throws Exception {
        if (!mind.getUser().isClosed()) {
//            if(mind.getNext() == null) {
            connection = mind.getUser().getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        current.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
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

    public void commit(TValueFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
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
//        pack();
//        update();
        action = base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized TValue add(TVariable tv, Term o) throws Exception {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(mind.getUser().nextId(SCHEMA));
            t.setMindId(mind.getId());
            cache.add(t);
            if (top == null) {
                top = cache.getRoot();
            }
            action = true; //!o.isCVariable() || !o.getSlaves().isEmpty();

            tv.incFloodControl(o);
        }

        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        TValue v = current.get(tv);
        return v;
    }

    public boolean isEmpty(TVariable tv) {
        return /*(cache.isEmpty() && load.isEmpty() &&  ||*/ !current.containsKey(tv);
    }

    public TValue getXValue(TVariable tv, final Term parent) throws Exception {
        final TValue[] result = new TValue[]{null};
        mind.getTValues().forEach(tv, new IReactor<TValue>() {
            @Override
            public Object run(TValue o) throws Exception {
                if (o.getValue().isXVariable()
                        && (o.getValue().getParentId() == parent.getId()
                        || o.getValue().getParentId() == parent.getParentId())) {
                    result[0] = o;
                }
                return true;
            }
        });
        return result[0];
    }

    public TValue find(TVariable tv, Term v) throws Exception {
        if (v.isXVariable()) {
            return getXValue(tv, v.getParent());
        } else {
            TValue temp = new TValue(tv, v);
            for (long id : cache.find(temp.getHash())) {
                IUnit one = load(id);
                if (one.equalsTo(temp)) {
                    return (TValue) one;
                }
            }
        }
        return null;
    }

    public TValue load(long id) throws Exception {
        TValue t = get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TValue) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private TValue get(long id) throws Exception {
        TValue t = (TValue) cache.get(id);
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
            if (current.containsKey(((TValue) o).getTVar()) && current.get(((TValue) o).getTVar()).getId() == ((TValue) o).getId()) {
                current.remove(((TValue) o).getTVar());
            }
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

    public void delete(TValue v) throws IOException, ClassNotFoundException {
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
            transaction(mind.getNext().getTValues());
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


    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v);
        }
        return v;
    }

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

    public TValue getRoot(TVariable t) throws Exception {
        for (TValue v : this) {
            if (!v.isDeleted() && v.getTVar().getId() == t.getId()) {
                return v;
            }
        }
        return null;
    }

    public long incTag() {
        return ++tag;
    }

    public Map<TVariable, TValue> getCurrent() {
        return current;
    }

    private void forward(IStep root, TVariable t, IReactor reactor) throws Exception {
        if (root.getNext() != null) {
            forward(root.getNext(), t, reactor);
            if (((TValue) root.getData()).getTVarId() == t.getId()) {
                reactor.run(root.getData(mind));
            }
        } else {
            if (((TValue) root.getData()).getTVarId() == t.getId()) {
                reactor.run(root.getData(mind));
            }
        }
    }

    public void forEach(TVariable t, IReactor reactor) throws Exception {
        if (cache.size() > 0) {
            forward(cache.getRoot(), t, reactor);
        }
    }

    public void scan(TVariable t, IReactor reactor) throws Exception {
        if (!cache.isEmpty()) {
            IStep root = null;
            IStep bottom = null;
            do {
                root = cache.getRoot();
                IStep saveRoot = root;
                for (; root != bottom; root = root.getNext()) {
                    if (((TValue) root.getData(mind)).getTVarId() == t.getId()) {
                        reactor.run(root.getData(mind));
                    }
                }
                bottom = saveRoot;
            } while (root != cache.getRoot());
        }
    }

//    public boolean isCVariabled(TVariable t, Term tm) throws Exception {
//        boolean found = false;
//        for (IStep root = cache.getRoot(); root != null; root = root.getNext()) {
//            TValue v = (TValue) root.getData(mind);
//            if (v.isCVariable()) { // && (v.getTVarId() == t.getId() || v.getParentId() == t.getId()) {
//                found = true;
//                break;
//            }
//        }
//        return found;
//    }

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


    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
