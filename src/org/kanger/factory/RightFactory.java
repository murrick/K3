package org.kanger.factory;

import org.kanger.User;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.storage.Escalera;
import org.kanger.units.*;

import java.io.IOException;
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
            cache = new Escalera(user, SCHEMA, base.cache);
            stored = new Escalera(user, SCHEMA_STORED, base.stored);
        } else {
            cache = new Escalera(user, SCHEMA, null);
            stored = new Escalera(user, SCHEMA_STORED, null);
            if (!cache.isEmpty()) {
                lastId = cache.getRoot().getId() + 1;
                firstId = lastId;
            } else {
                lastId = 0;
                firstId = 0;
            }
        }
    }


    public void commit(RightFactory base) throws Exception {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }
        stored.setRoot(base.stored.getRoot());
        if (stored.getRoot() != null && stored.getTop() == null) {
            stored.setTop(base.stored.getTop());
        }

//        List<Right> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Right) p);
//        }
//        for (Right p : list) {
//            add(p);
//        }
    }

    public void update() throws IOException {
        if (cache.update() && stored.update()) {
            firstId = lastId;
        }
    }

    public Right register(Right r) {
        r.setId(lastId++);
        r.setVarIndex(user.getMind().getTerms().getVarIndex());
        return r;
    }

    public Right add(Right r) throws IOException, ClassNotFoundException {
        Right x = find(r);
        if (x != null) {
            delete(r);
            return x;
        } else {
            if (r.getId() == -1) {
                r.setId(lastId++);
            }
            cache.add(r);
            if (r.isStored()) {
                stored.add(r.getId(), r.getId());
            }
            for (List<Domain> list : r.getTree()) {
                for (Domain d : list) {
                    r.getPredicates().add(d.getPredicateId());
                    d.setRight(r);
                    for (TVariable t : d.getArguments().getTVariables(true)) {
                        t.setRight(r);
                    }
//                    user.getMind().getDomains().add(d);
                }
            }
            return r;
        }
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
                    Right rx = tree.get(0).setStored();
//                    rx.setGenerated(false);
                } else {
                    Right rx = tree.get(0).createStored();
//                    rx.setGenerated(false);
                }
            }
        }
    }

    public Right load(long id) throws IOException, ClassNotFoundException {
        Right t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Right) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Right get(long id) throws IOException, ClassNotFoundException {
        Right t = (Right) cache.get(id);
        return t;
    }

    public void clear() throws IOException, ClassNotFoundException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
        } else {
            cache.clear();
            stored.clear();
            transaction(null);
        }
    }

    public void delete(Right r) throws IOException, ClassNotFoundException {
        r.setDeleted();
            for (List<Domain> list : r.getTree()) {
                for (Domain d : list) {
                    user.getMind().getDomains().delete(d);
                }
            }
//            cache.delete(id);
//            stored.delete(id);
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Right r = get(id);
//        if (r != null) {
//            for (List<Domain> list : r.getTree()) {
//                for (Domain d : list) {
//                    user.getMind().getDomains().delete(d.getId());
//                }
//            }
//            cache.delete(id);
//            stored.delete(id);
//        }
//    }

    public int size() {
        return cache.size();
    }

    public int storedSize() {
        return stored.size();
    }

    public Right add(Domain domain) throws IOException, ClassNotFoundException {
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
            r.setGenerated(true);
            r.setStored();

            //TODO: 1
            if (domain.isQuery()) {
                r.setQuery(true);
            }

            int save = user.getMind().getDebugLevel();
            user.getMind().setDebugLevel(0);
            Term origin = user.getMind().getTerms().add(d.toString());
            user.getMind().setDebugLevel(save);
            r.setOrig(origin);

            return add(r);
        }
    }

    public Right store(Domain d) throws IOException, ClassNotFoundException {
        d.getRight().setStored();
        stored.add(d.getRight().getId(), d.getRight().getId());
        return d.getRight();
    }

    public Right find(Domain domain) throws IOException, ClassNotFoundException {
        for (long id : cache.find(domain.getHashBase())) {
            Right one = load(id);
            if (one.equalsTo(domain)) {
                return (Right) one;
            }
        }
        return null;
    }

    public Right find(Right right) throws IOException, ClassNotFoundException {
        for (long id : cache.find(right.getHash())) {
            Right one = load(id);
            if (one.equalsTo(right)) {
                return one;
            }
        }
        return null;
    }


    public void unlink() throws Exception {
        cache.unlink();
        stored.unlink();
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


    public void pack() throws IOException, ClassNotFoundException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
            stored.delete(((IUnit) o).getId());
        }
        update();

        if (!cache.isEmpty()) {
            lastId = cache.getRoot().getId() + 1;
            firstId = lastId;
        } else {
            lastId = 0;
            firstId = 0;
        }

    }
}
