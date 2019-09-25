package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Predicate;
import kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class PredicateFactory implements Iterable<Predicate> {

    public static final String SCHEMA = "predicates";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private User user = null;

    public PredicateFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
//        cache.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Cache(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache = new Cache(null);
        }
    }

    public void commit(PredicateFactory base) throws Exception {
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

    public Predicate add(Term line, int range) throws Exception {
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

    public Predicate find(Term line, int range) throws Exception {
        Predicate temp = new Predicate(line, range);
        for (long id : cache.find(temp.getHash())) {
            Identifiable one = get(id);
            if (one.equalsTo(temp)) {
                return (Predicate) one;
            }
        }
        return null;
    }

    public Predicate get(long id) throws Exception {
        Predicate t = (Predicate) cache.get(id);
//        t.linkExternal(user);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getPredicates());
        } else {
            transaction(null);
        }
    }

    public int size() throws Exception {
        return cache.size();
    }


    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

}
