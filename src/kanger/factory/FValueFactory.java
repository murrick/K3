package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;
import kanger.storage.Escalera;
import kanger.units.FValue;
import kanger.units.Function;

import java.util.ArrayList;
import java.util.List;

public class FValueFactory {

    public static final String SCHEMA = "fvalues";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
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
            cache = new Escalera(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache = new Escalera(null);
        }
    }

    public void commit(FValueFactory base) throws Exception {
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

    public FValue add(Function f) throws Exception {
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


    public FValue find(Function f) throws Exception {
        FValue temp = new FValue(f, user);
        for (long id : cache.find(temp.getHash())) {
            Identifiable one = get(id);
            if (one.equalsTo(f)) {
                return (FValue) one;
            }
        }
        return null;
    }

    public FValue get(long id) throws Exception {
        FValue t = (FValue) cache.get(id);
//        t.linkExternal(user);
        return t;
    }


    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFValues());
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

    public void unlink() {
        cache.unlink();
    }

    public int size() throws Exception {
        return cache.size();
    }

    public long getLastId() {
        return lastId;
    }

}
