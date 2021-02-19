package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IPredicate;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Predicate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class PredicateFactory implements IFactory<IPredicate> {

    public static final String SCHEMA = "predicates";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;
    private IBase connection = null;

    public PredicateFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(PredicateFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
//            if(mind.getNext() == null) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
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

    public void commit(PredicateFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }
//        if (cache.getRoot() != null) {
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
//                    ((IUnit) s.getData()).setMind(mind);
//                    ((IUnit) s.getData()).setMindId(mind.getId());
//                } else {
//                    break;
//                }
//            }
//        }
//        pack();
//        update();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized Predicate add(ITerm line, int range) throws Exception {
        Predicate p = find(line, range);
        if (p != null) {
            p.setDeleted(false, mind);
            return p;
        } else {
            p = new Predicate(mind);
            p.setId(((User) mind.getUser()).nextId(SCHEMA));
            p.setMindId(mind.getId());
            p.setRange(range);
            p.setName(line);
            cache.add(p);
            if (top == null) {
                top = cache.getRoot();
            }
            return p;
        }
    }

    public Predicate find(ITerm line, int range) throws Exception {
        Predicate temp = new Predicate(line, range);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = get(id);
            if (one.equalsTo(temp)) {
                return (Predicate) one;
            }
        }
        return null;
    }

    public Predicate get(long id) throws Exception {
        Predicate t = (Predicate) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Predicate) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

//    private Predicate get(long id) throws Exception {
//        Predicate t = (Predicate) cache.get(id);
//        return t;
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((PredicateFactory) mind.getNext().getPredicates());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public int size() throws Exception {
        return cache.size();
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
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

}
