package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.FValue;
import kanger.units.Function;

import java.util.ArrayList;
import java.util.List;

public class FValueFactory {

    public static final String SCHEMA = "fvalues";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache;
    private User user = null;

    public FValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
//        cache.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Cache(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache = user.getStorage(SCHEMA);
        }
    }

    public void commit(FValueFactory base) {
        List<FValue> list = new ArrayList();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
                break;
            }
            list.add(0, (FValue) p);
        }
        for (FValue p : list) {
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
        for (Object one : cache.find(temp.getHash())) {
            if (((Identifiable) one).equalsTo(f)) {
                return (FValue) one;
            }
        }
        return null;
    }

    public FValue get(long id) {
        FValue t = (FValue) cache.get(id);
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
        return cache.size();
    }

    public long getLastId() {
        return lastId;
    }

}
