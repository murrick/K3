package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.storage.Cache;
import kanger.units.Domain;
import kanger.units.Right;
import kanger.units.TValue;
import kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    public static final String SCHEMA = "rights";
    public static final String SCHEMA_STORED = "stored";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private ICache stored;
    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
//        cache.clear();
//        stored.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Cache(base.cache);
            stored = new Cache(base.stored);
        } else {
            lastId = 0;
            firstId = 0;
            cache = new Cache(null);
            stored = new Cache(null);
        }
    }

    public void release() {
    }

    public void commit(RightFactory base) throws Exception {
        List<Right> list = new ArrayList();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
                break;
            }
            list.add(0, (Right) p);
        }
        for (Right p : list) {
            add(p);
        }
    }

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            //TODO: Коммит в БД
            firstId = lastId;
        }
    }

    public Right add(Right r) throws Exception {
        r.setId(lastId++);
        cache.add(r);
        if (r.isStored()) {
            stored.add(r.getId(), r.getId());
        }
        for (List<Domain> list : r.getTree()) {
            for (Domain d : list) {
                r.getPredicates().add(d.getPredicate());
                d.setRight(r);
                user.getMind().getDomains().add(d);
            }
        }
        return r;
    }

//    public void reindex() throws RuntimeErrorException {
//        if (!user.isClosed()) {
//            //TODO: Переиндексация после открытия БД
//        }
//    }
//


    public void expand(Right r) throws Exception {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(true).isEmpty()) {
                    user.getMind().getDomains().getWaiters().add(tree.get(0));
                } else if (r.getTree().size() == 1) {
                    tree.get(0).setStored();
                } else {
                    tree.get(0).createStored();
                }
            }
        }
    }

    public Right get(long id) throws Exception {
        Right t = (Right) cache.get(id);
//        t.linkExternal(user);
        return t;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
        } else {
            transaction(null);
        }
    }

    public int size() throws Exception {
        return cache.size();
    }

    public int storedSize() throws Exception {
        return stored.size();
    }

    public Right add(Domain domain) throws Exception {
        Right p = find(domain);
        if (p != null) {
            return p;
        } else {
            ArgList list = null;
            if (domain.isQuery()) {
                list = domain.getArguments().convert();
                for (TValue t : list.getTValues(true)) t.setQuery();
            } else {
                list = domain.getArguments().convertBase();
            }
            Right r = new Right(user);
            Domain d = user.getMind().getDomains().add(domain.getPredicate(), domain.isAntc(), list, r);
            r.getTree().get(0).add(d);
            r.setGenerated();
            r.setStored();

            int save = user.getMind().getDebugLevel();
            user.getMind().setDebugLevel(0);
            Term origin = user.getMind().getTerms().add(d.toString());
            user.getMind().setDebugLevel(save);
            r.setOrig(origin);

            return add(r);
        }
    }

    public Right store(Domain d) throws Exception {
        d.getRight().setStored();
        stored.add(d.getRight().getId(), d.getRight().getId());
        return d.getRight();
    }

    public Right find(Domain domain) throws Exception {
        for (long id : cache.find(domain.getHashBase())) {
            Right one = get(id);
            if (one.equalsTo(domain)) {
                return (Right) one;
            }
        }
        return null;
    }


    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public long getLastId() {
        return lastId;
    }

    public long getFirstId() {
        return firstId;
    }

    // ****************** DATABASE

    public Iterable<Long> getDatabase(long fromId) {
        return new Iterable<Long>() {
            @Override
            public Iterator iterator() {
                return stored.iterator(true, fromId);
            }
        };
    }


}
