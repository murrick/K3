package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Predicate;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class PredicateFactory implements Iterable<Predicate> {

    public static final String SCHEMA = "predicates";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;

    public PredicateFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
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
    }

    public void commit(PredicateFactory base) throws Exception {
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
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public synchronized Predicate add(Term line, int range) throws Exception {
        Predicate p = find(line, range);
        if (p != null) {
            return p;
        } else {
            p = new Predicate(mind);
            p.setId(mind.getUser().nextId(SCHEMA));
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

    public Predicate find(Term line, int range) throws Exception {
        Predicate temp = new Predicate(line, range);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(temp)) {
                return (Predicate) one;
            }
        }
        return null;
    }

    public Predicate load(long id) throws Exception {
        Predicate t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Predicate) s.getData(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private Predicate get(long id) throws Exception {
        Predicate t = (Predicate) cache.get(id);
        return t;
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getPredicates());
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

    public void pack() throws IOException {
//        update();
    }
}
