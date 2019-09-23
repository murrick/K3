package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Predicate;
import kanger.units.Term;

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
    private User user = null;

    public PredicateFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
        cache.clear();
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
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
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
            //TODO: Коммит в БД
            firstId = lastId;
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
        return null;
    }

    public Predicate get(long id) {
        Predicate t = (Predicate) cache.get(id);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getPredicates());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size();
    }


    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public Cache use(Cache cache) {
        this.cache = cache;
        return cache;
    }
}
