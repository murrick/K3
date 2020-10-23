package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.FValue;
import org.kanger.units.Function;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FValueFactory implements Iterable<FValue> {

    public static final String SCHEMA = "fvalues";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;

    private transient boolean action = false;

    public FValueFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
//        cache.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            cache = new Escalera(mind, SCHEMA, base.cache);
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
//        lastId = user.lastId(SCHEMA);
    }

    public void commit(FValueFactory base) throws Exception {
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

    public synchronized FValue add(Function f) throws Exception {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, mind);
                t.setId(mind.getUser().nextId(SCHEMA));
                f.setMindId(mind.getId());
                cache.add(t);
                if (top == null) {
                    top = cache.getRoot();
                }
                action = true;
            } else {
                return null;
            }
        }
        return t;
    }


    public FValue find(Function f) throws Exception {
        for (long id : cache.find(f.getHashBase())) {
            FValue one = load(id);
            if (one.equalsTo(f)) {
                return one;
            }
        }
        return null;
    }

    public FValue load(long id) throws Exception {
        FValue t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (FValue) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private FValue get(long id) throws Exception {
        FValue t = (FValue) cache.get(id);
        return t;
    }


    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getFValues());
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

//        update();

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

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }
}
