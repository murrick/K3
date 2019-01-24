package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.FValue;
import kanger.units.Function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FValueFactory {

    public static final String SCHEMA = "fvalues";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public FValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
        cache.clear();
        load.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
        }
    }

    public void commit(FValueFactory base) {
        List<FValue> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (FValue) p);
        }
        for (FValue p : list) {
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
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
            }
        }
    }

    public FValue add(Function f) throws RuntimeErrorException {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, user);
                t.setId(lastId++);
                cache.add(t);
            } else {
                return null;
            }
        }
        return t;
    }


    public FValue find(Function f) throws RuntimeErrorException {
        FValue temp = new FValue(f, user);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(f)) {
                return (FValue) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                one.linkExternal(user);
                if (one.equalsTo(f)) {
                    return (FValue) one;
                }
            }
        }
        return null;
    }

    public FValue get(long id) throws RuntimeErrorException {
        FValue t = (FValue) cache.get(id);
        if (t == null) {
            t = (FValue) load.get(id);
            if (t == null && !user.isClosed()) {
                try {
                    t = (FValue) user.getStorage(SCHEMA).get(id);
                    if (t != null) {
                        t.linkExternal(user);
                        load.add(t);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                    throw new RuntimeErrorException(e.toString());
                }
            }
        }
        if(t == null) {
            throw new RuntimeErrorException("TValue id=" + id + " not found");
        }
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFValues());
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


    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    public long getLastId() {
        return lastId;
    }

}
