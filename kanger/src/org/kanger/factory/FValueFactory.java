package org.kanger.factory;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.storage.Escalera;
import org.kanger.units.FValue;
import org.kanger.units.Function;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FValueFactory {

    public static final String SCHEMA = "fvalues";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IUser user = null;

    private transient boolean action = false;

    public FValueFactory(IUser user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
//        cache.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            cache = new Escalera(user.getMind(), SCHEMA, base.cache);
        } else {
            cache = new Escalera(user.getMind(), SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
//        lastId = user.lastId(SCHEMA);
    }

    public void commit(FValueFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
//            lastId = cache.getRoot().getId() + 1;
//            if (cache.getTop() == null) {
//                cache.setTop(base.cache.getTop());
////                firstId = cache.getTop().getId();
//            }
        }

//        List<FValue> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (FValue) p);
//        }
//        for (FValue p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//        }
        action = base.isAction();
    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public FValue add(Function f) throws Exception {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, user);
                t.setId(user.nextId(SCHEMA));
                f.setMindId(user.getMind().getId());
                cache.add(t);
                action = true;
            } else {
                return null;
            }
        }
        return t;
    }


    public FValue find(Function f) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (long id : cache.find(f.getHashBase())) {
            FValue one = load(id);
            if (one.equalsTo(f)) {
                return one;
            }
        }
        return null;
    }

    public FValue load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        FValue t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (FValue) s.getData(user.getMind());
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public FValue get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        FValue t = (FValue) cache.get(id);
        return t;
    }


    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFValues());
        } else {
            cache.clear();
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

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public int size() {
        return cache.size();
    }

    public long getLastId() {
        return cache.isEmpty() ? -1 : cache.getRoot().getId();
    }

    public void pack() throws IOException, ClassNotFoundException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }

        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
//            firstId = lastId;
//        } else {
//            lastId = 0;
//            firstId = 0;
//        }

    }

    public void delete(FValue v) {
        v.setDeleted();
    }

    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

}
