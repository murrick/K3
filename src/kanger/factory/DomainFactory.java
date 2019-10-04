package kanger.factory;

import kanger.User;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.storage.Escalera;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DomainFactory implements Iterable<Domain> {

    public static final String SCHEMA = "domains";

    private long lastId = 0;
    private long firstId = 0;

    private Set<Domain> waiters = new HashSet<>();

    private ICache cache;
    //    private Cache load = new Cache();
    private User user = null;

    public DomainFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        waiters.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            waiters.addAll(base.waiters);
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

    public void commit(DomainFactory base) throws Exception {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;

            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }
        waiters.addAll(base.waiters);


//        List<Domain> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Domain) p);
//        }
//        for (Domain p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//        }
//        waiters.addAll(base.waiters);
    }

    public void update() throws Exception {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public Domain add(Domain d) throws IOException, ClassNotFoundException {
        cache.add(d);
        return d;
    }

    public Domain add(Right r) throws Exception {
        Domain p = new Domain(user);
        p.setRight(r);
        p.setId(lastId++);
        cache.add(p);
        return p;
    }


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) throws Exception {
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

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) throws Exception {
        Domain temp = new Domain(pred, antc, arg, r);
        for (long id : cache.find(temp.getHash())) {
            Identifiable one = load(id);
            if (one.equalsTo(temp)) {
                return (Domain) one;
            }
        }
        return null;
    }

    public Domain load(long id) throws IOException, ClassNotFoundException {
        Domain t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Domain) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Domain get(long id) throws IOException, ClassNotFoundException {
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

    public void unlink() throws Exception {
        cache.unlink();
    }

    public int size() throws Exception {
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
