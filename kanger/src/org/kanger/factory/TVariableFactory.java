package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Rule;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;
import org.kanger.units.Term;

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
    private IStep top = null;
    private Mind mind = null;
    private IBase connection = null;

    public TVariableFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TVariableFactory base) throws Exception {
        if (mind.getNext() == null && !mind.getUser().isClosed()) {
//            if(mind.getNext() == null) {
            connection = mind.getUser().getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            cache = new Escalera(mind, SCHEMA, base.cache);
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

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
    }

    public void commit(TVariableFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
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
//        pack();
//        update();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized TVariable createTVar(Rule r, Term name) throws Exception {
        TVariable p = new TVariable(mind);
        p.setId(mind.getUser().nextId(SCHEMA));
        r.setMindId(mind.getId());
        p.setIndex(mind.getTerms().nextVarIndex());
        p.setRule(r);
        p.setName(name);
        cache.add(p);
        if (top == null) {
            top = cache.getRoot();
        }
        return p;
    }

    public TVariable load(long id) throws Exception {
        TVariable t = get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TVariable) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private TVariable get(long id) throws Exception {
        TVariable t = (TVariable) cache.get(id);
        return t;
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
            mind.getTValues().getCurrent().remove(o);
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

    public void delete(TVariable t) throws Exception {
        t.setDeleted();
        for (TValue v : mind.getTValues()) {
            if (v.getTVar().getId() == t.getId()) {
                mind.getTValues().delete(v);
            }
        }
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        TVariable t = get(id);
//        if (t != null) {
//            List<TValue> list = new ArrayList<>();
//            for (TValue v : mind.getTValues()) {
//                if (v.getTVarId() == t.getId()) {
//                    list.add(v);
//                }
//            }
//            for (TValue v : list) {
//                mind.getTValues().delete(v.getId());
//            }
//            cache.delete(id);
//        }
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getTVars());
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

    public int size() throws Exception {
        return cache.size();
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    @Override
    public Iterator iterator() {
        return cache.iterator(-1);
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

}
