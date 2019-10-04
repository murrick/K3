package kanger.factory;

import kanger.User;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.interfaces.Identifiable;
import kanger.storage.Escalera;
import kanger.units.TValue;
import kanger.units.TVariable;
import kanger.units.Term;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TValueFactory {

    public static final String SCHEMA = "tvalues";

    private long lastId = 0;
    private long firstId = 0;
    private long tag = 0;

    private Map<TVariable, TValue> current = new HashMap<>();

    private ICache cache;
    private User user = null;

    public TValueFactory(User user) {
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

    public void commit(TValueFactory base) throws Exception {
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

    public void update() throws Exception {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public TValue add(TVariable tv, Term o) throws Exception {
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

    public TValue find(TVariable tv, Term v) throws Exception {
        TValue temp = new TValue(tv, v);
        for (long id : cache.find(temp.getHash())) {
            Identifiable one = load(id);
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue load(long id) throws IOException, ClassNotFoundException {
        TValue t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (TValue) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public TValue get(long id) throws IOException, ClassNotFoundException {
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

    public void mark() throws Exception {
        cache.mark();
    }


    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
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

    public int size() throws Exception {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    public void unlink() throws Exception {
        cache.unlink();
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
