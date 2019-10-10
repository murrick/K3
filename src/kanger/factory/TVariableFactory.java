package kanger.factory;

import kanger.User;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.storage.Escalera;
import kanger.units.Right;
import kanger.units.TValue;
import kanger.units.TVariable;
import kanger.units.Term;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class TVariableFactory implements Iterable<TVariable> {

    public static final String SCHEMA = "tvariables";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private User user = null;

    public TVariableFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
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

    public void commit(TVariableFactory base, Collection vars) throws Exception {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }

            for (Object p : cache) {
                if (((TVariable) p).getId() >= base.firstId) {
                    vars.add(p);
                } else {
                    break;
                }
            }

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

    public void update() throws Exception {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public TVariable createTVar(Right r, Term name) throws Exception {
        TVariable p = new TVariable(user);
        p.setId(lastId++);
        p.setIndex(user.getMind().getTerms().nextVarIndex());
        p.setRight(r);
        p.setName(name);
        cache.add(p);
        return p;
    }

    public TVariable load(long id) throws IOException, ClassNotFoundException {
        TVariable t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (TVariable) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public TVariable get(long id) throws IOException, ClassNotFoundException {
        TVariable t = (TVariable) cache.get(id);
        return t;
    }

    public void delete(long id) throws IOException, ClassNotFoundException {
        TVariable t = get(id);
        if (t != null) {
            Iterator<TValue> iterator = user.getMind().getTValues().iterator(t);
            TValue x = null;
            TValue y = null;
            while (iterator.hasNext()) {
                if (y != null) {
                    user.getMind().getTValues().delete(y.getId());
                }
                y = x;
                x = iterator.next();
            }
            if (y != null) {
                user.getMind().getTValues().delete(y.getId());
            }
            cache.delete(id);
        }
    }

    public void clear() throws IOException, ClassNotFoundException {
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
