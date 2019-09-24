package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.storage.Cache;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DomainFactory implements Iterable<Domain> {

    public static final String SCHEMA = "domains";

    private long lastId = 0;
    private long firstId = 0;

    private Set<Domain> waiters = new HashSet<>();

    private Cache cache;
    //    private Cache load = new Cache();
    private User user = null;

    public DomainFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
//        cache.clear();
//        load.clear();
        waiters.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            waiters.addAll(base.waiters);
            cache = new Cache(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache = user.getStorage(SCHEMA);
        }
    }

    public void commit(DomainFactory base) {
        List<Domain> list = new ArrayList();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
                break;
            }
            list.add(0, (Domain) p);
        }
        for (Domain p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
        waiters.addAll(base.waiters);
    }

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            //TODO: Коммит в БД
            firstId = lastId;
        }
    }



    public Domain add(Right r) {
        Domain p = new Domain(user);
        p.setRight(r);
        p.setId(lastId++);
        cache.add(p);
        return p;
    }


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) throws RuntimeErrorException {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            return p;
        } else {
            p = new Domain(user);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
            p.setId(lastId++);
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
            cache.add(p);
            return p;
        }
    }

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) throws RuntimeErrorException {
        Domain temp = new Domain(pred, antc, arg, r);
        for (Object one : cache.find(temp.getHash())) {
            if (((Identifiable) one).equalsTo(temp)) {
                return (Domain) one;
            }
        }
        return null;
    }

    public Domain get(long id) {
        Domain t = (Domain) cache.get(id);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getDomains());
        } else {
            transaction(null);
        }
    }


    public int size() {
        return cache.size();
    }

    public Set<Domain> getWaiters() {
        return waiters;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

}
