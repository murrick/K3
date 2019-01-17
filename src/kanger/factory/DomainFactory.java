package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by murray on 25.05.15.
 */
public class DomainFactory implements Iterable<Domain> {

    public static final String SCHEMA = "domains";

    private long lastId = 0;
    private long firstId = 0;

    private Cache cache = new Cache();
    private User user = null;

    public DomainFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            cache.clear();
        }
    }

    public void commit(DomainFactory base) {
        List<Domain> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Domain) p);
        }
        for (Domain p : list) {
            p.setId(lastId++);
            cache.add(p);
        }
    }

    public void update() {
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
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }



    public Domain add(Right r) {
        Domain p = new Domain(user);
        p.setRight(r);
        p.setId(lastId++);
        cache.add(p);
        return p;
    }


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) {
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

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) {
        Domain temp = new Domain(pred, antc, arg, r);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Domain) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                if (one.equalsTo(temp)) {
                    return (Domain) one;
                }
            }
        }
        return null;
    }

    public Domain get(long id) {
        Domain t = (Domain) cache.get(id);
        if (t == null) {
            try {
                t = (Domain) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    cache.add(t);
                    t.linkExternal();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return t;
    }

//    public Domain getRoot() {
//        return root;
//    }
//
//    public void setRoot(Domain o) {
//        root = o;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getDomains());
        } else {
            transaction(null);
        }
    }


    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    @Override
    public Iterator<Domain> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage);
    }
}
