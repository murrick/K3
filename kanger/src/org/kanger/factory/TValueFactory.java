package org.kanger.factory;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
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

    private long lastId = 0;
    private long firstId = 0;
    private long tag = 0;

    private Map<TVariable, TValue> current = new HashMap<>();

    private ICache cache;
    private IUser user = null;

    public TValueFactory(IUser user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TValueFactory base) {
        current.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Escalera(user, SCHEMA, base.cache);
        } else {
            cache = new Escalera(user, SCHEMA, null);
            if (!cache.isEmpty()) {
                lastId = cache.getRoot().getId() + 1;
                firstId = lastId;
            } else {
                lastId = 0;
                firstId = 0;
            }
        }
    }

    public void commit(TValueFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }

//        List<TValue> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (TValue) p);
//        }
//        for (TValue p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//        }
    }

    public void update() throws IOException {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public TValue add(TVariable tv, Term o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, user);
            t.setTVar(tv);
            t.setId(lastId++);
            cache.add(t);

//            //TODO: ПРИБИДБ
//            System.out.println("++++++ " + t);
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

    public TValue find(TVariable tv, Term v) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TValue temp = new TValue(tv, v);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TValue t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (TValue) s.getData(user);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public TValue get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TValue t = (TValue) cache.get(id);
        return t;
    }

    public void pack() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
        update();

        if (!cache.isEmpty()) {
            lastId = cache.getRoot().getId() + 1;
            firstId = lastId;
        } else {
            lastId = 0;
            firstId = 0;
        }

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

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTValues());
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
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    public void unlink() throws Exception {
        cache.unlink();
    }

    public Iterator<TValue> iterator(TVariable tVariable) {
        return new TValueIterator(false, tVariable);
    }

    @Override
    public Iterator<TValue> iterator() {
        return new TValueIterator(true, null);
    }

    public long getLastId() {
        return lastId;
    }

    public long incTag() {
        return ++tag;
    }

    public Map<TVariable, TValue> getCurrent() {
        return current;
    }

    public class TValueIterator implements Iterator {

        private TVariable tVariable;
        private TValue next = null;
        private Iterator iterator = null;

        public TValueIterator(boolean backward, TVariable tVariable) {
            this.tVariable = tVariable;
            iterator = cache.iterator(backward, -1);
        }

        @Override
        public boolean hasNext() {
            if (tVariable != null) {
                while (iterator.hasNext()) {
                    next = (TValue) iterator.next();
                    if (next.getTVarId() == tVariable.getId()) {
                        return true;
                    }
                }
                next = null;
            } else {
                if (iterator.hasNext()) {
                    next = (TValue) iterator.next();
                    return true;
                }
            }
            return false;
        }

        @Override
        public IUnit next() {
            return next;
        }
    }
}
