package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.TValue;
import kanger.units.TVariable;
import kanger.units.Term;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TValueFactory {

    public static final String SCHEMA = "tvalues";

    private long lastId = 0;
    private long firstId = 0;
    private long tag = 0;

    private Map<TVariable, TValue> current = new HashMap<>();

    private Cache cache = new Cache();
    private User user = null;

    public TValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TValueFactory base) {
        cache.clear();
        current.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
        }
    }

    public void commit(TValueFactory base) {
        List<TValue> list = new ArrayList();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
                break;
            }
            list.add(0, (TValue) p);
        }
        for (TValue p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
    }

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            //TODO: Коммит в БД
            firstId = lastId;
        }
    }

    public TValue add(TVariable tv, Term o) throws RuntimeErrorException {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, user);
            t.setTVar(tv);
            t.setId(lastId++);
            cache.add(t);
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

    public TValue find(TVariable tv, Term v) throws RuntimeErrorException {
        TValue temp = new TValue(tv, v);
        for (Object one : cache.find(temp.getHash())) {
            if (((Identifiable) one).equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue get(long id) {
        TValue t = (TValue) cache.get(id);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTValues());
        } else {
            transaction(null);
        }
    }

    public void mark() {
        cache.mark();
    }


    public void commit() {
        cache.commit();
    }

    public void release() {
        cache.release();
    }


    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v);
        }
        return v;
    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    public Iterator<TValue> iterator(TVariable tVariable) {
        return new TValueIterator(false, tVariable);
    }

    public Iterator<TValue> iterator() {
        return new TValueIterator(true, null);
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
                    if (next.getTVar().getId() == tVariable.getId()) {
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
        public Identifiable next() {
            return next;
        }
    }

    public long getLastId() {
        return lastId;
    }

    public long incTag() {
        return ++tag;
    }
}
