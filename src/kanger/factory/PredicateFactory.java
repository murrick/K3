package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Predicate;
import kanger.units.Term;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by murray on 25.05.15.
 */
public class PredicateFactory implements Iterable<Predicate> {

    public static final String SCHEMA = "predicates";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public PredicateFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
        cache.clear();
        load.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
        }
    }

    public void commit(PredicateFactory base) {
        List<Predicate> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Predicate) p);
        }
        for (Predicate p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
    }

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            try {
                for (Identifiable p : cache) {
                    if (p.getId() < firstId) {
                        break;
                    }
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
                firstId = lastId;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
            }
        }
    }

    public Predicate add(Term line, int range) throws RuntimeErrorException {
        Predicate p = find(line, range);
        if (p != null) {
            return p;
        } else {
            p = new Predicate(user);
            p.setId(lastId++);
            p.setRange(range);
            p.setName(line);
            cache.add(p);
            return p;
        }
    }

    public Predicate find(Term line, int range) throws RuntimeErrorException {
        Predicate temp = new Predicate(line, range);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Predicate) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                if (one.equalsTo(temp)) {
                    one.linkExternal(user);
                    return (Predicate) one;
                }
            }
        }
        return null;
    }

    public Predicate get(long id) {
        Predicate t = (Predicate) cache.get(id);
        if (t == null) {
            t = (Predicate) load.get(id);
        }
        return t;
    }

    public Predicate load(long id) throws RuntimeErrorException {
        Predicate t = null;
        if (!user.isClosed()) {
            try {
                t = (Predicate) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    load.add(t);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace(System.err);
                throw new RuntimeErrorException(e.toString());
            }
        }
        return t;
    }

    //    public Predicate getRoot() {
//        return root;
//    }
//
//    public void setRoot(Predicate root) {
//        this.root = root;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getPredicates());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }


    @Override
    public Iterator<Predicate> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage, user);
    }

}
