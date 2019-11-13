package org.kanger.factory;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
import org.kanger.storage.Escalera;
import org.kanger.units.Right;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TVariableFactory implements Iterable<TVariable> {

    public static final String SCHEMA = "tvariables";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IUser user = null;

    public TVariableFactory(IUser user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            cache = new Escalera(user, SCHEMA, base.cache);
        } else {
            cache = new Escalera(user, SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }

    public void commit(TVariableFactory base /*, Map<Integer, Object> vars*/) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
//            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
//                firstId = cache.getTop().getId();
            }

//            for (Object p : cache) {
//                if (((TVariable) p).getId() >= base.firstId) {
//                    vars.put(((TVariable) p).getIndex(), p);
//                } else {
//                    break;
//                }
//            }

        }


//        List<TVariable> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (TVariable) p);
//        }
//        for (TVariable p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//            vars.add(p);
//        }
    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public TVariable createTVar(Right r, Term name) throws Exception {
        TVariable p = new TVariable(user);
        p.setId(user.nextId(SCHEMA));
        r.setMindId(user.getMind().getId());
        p.setIndex(user.getMind().getTerms().nextVarIndex());
        p.setRight(r);
        p.setName(name);
        cache.add(p);
        return p;
    }

    public TVariable load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TVariable t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (TVariable) s.getData(user);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public TVariable get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        TVariable t = (TVariable) cache.get(id);
        return t;
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
            user.getMind().getTValues().getCurrent().remove(o);
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

    public void delete(TVariable t) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        t.setDeleted();
        for (TValue v : user.getMind().getTValues()) {
            if (v.getTVar().getId() == t.getId()) {
                user.getMind().getTValues().delete(v);
            }
        }
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        TVariable t = get(id);
//        if (t != null) {
//            List<TValue> list = new ArrayList<>();
//            for (TValue v : user.getMind().getTValues()) {
//                if (v.getTVarId() == t.getId()) {
//                    list.add(v);
//                }
//            }
//            for (TValue v : list) {
//                user.getMind().getTValues().delete(v.getId());
//            }
//            cache.delete(id);
//        }
//    }

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTVars());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public int size() throws Exception {
        return cache.size();
    }

    public void unlink() throws Exception {
        cache.unlink();
    }

    @Override
    public Iterator iterator() {
        return cache.iterator(true, -1);
    }


}
