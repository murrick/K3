package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.TValue;
import kanger.units.TVariable;
import kanger.units.Term;

import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TValueFactory {

    public static final String SCHEMA = "tvalues";

    private long lastId = 0;
    private long firstId = 0;

    private Map<TVariable, TValue> current = new HashMap<>();

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public TValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TValueFactory base) {
        cache.clear();
        load.clear();
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
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (TValue) p);
        }
        for (TValue p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
    }

    public void update() {
        if (!user.isClosed()) {
            try {
                for (Identifiable p : cache) {
                    if (p.getId() < firstId) {
                        break;
                    }
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
                firstId = lastId;
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public TValue add(TVariable tv, Term o) {
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

    public TValue find(TVariable tv, Term v) {
        TValue temp = new TValue(tv, v);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                one.linkExternal(user);
                if (one.equalsTo(temp)) {
                    return (TValue) one;
                }
            }
        }
        return null;
    }

    public TValue get(long id) {
        TValue t = (TValue) cache.get(id);
        if (t == null) {
            t = (TValue) load.get(id);
            if (t == null) {
                try {
                    t = (TValue) user.getStorage(SCHEMA).get(id);
                    if (t != null) {
                        t.linkExternal(user);
                        load.add(t);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
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
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new TValueIterator(tVariable, cache, storage);
    }

    public class TValueIterator extends DataIterator {

        private TVariable tVariable;
        private TValue next = null;

        public TValueIterator(TVariable tVariable, Cache cache, Storage storage) {
            super(false, cache, storage);
            this.tVariable = tVariable;
        }

        @Override
        public boolean hasNext() {
            while(super.hasNext()) {
                next = (TValue) super.next();
                if(next.getTVar().getId() == tVariable.getId()) {
                    next.linkExternal(user);
                    return true;
                }
            }
            next = null;
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
}
