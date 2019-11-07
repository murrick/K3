package org.kanger.factory;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
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
    private IUser user = null;

    public PredicateFactory(IUser user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
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

    public void commit(PredicateFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
//            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
//                firstId = cache.getTop().getId();
            }
        }

//        List<Predicate> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Predicate) p);
//        }
//        for (Predicate p : list) {
////            p.setId(lastId++);
//            cache.add(p);
//        }
//        lastId = cache.getRoot().getId() + 1;
    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public Predicate add(Term line, int range) throws Exception {
        Predicate p = find(line, range);
        if (p != null) {
            return p;
        } else {
            p = new Predicate(user);
            p.setId(user.nextId(SCHEMA));
            p.setRange(range);
            p.setName(line);
            cache.add(p);
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

    public Predicate load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Predicate t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Predicate) s.getData(user);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Predicate get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Predicate t = (Predicate) cache.get(id);
        return t;
    }

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getPredicates());
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
        return cache.iterator();
    }

    public void pack() throws IOException {
        update();
    }
}
