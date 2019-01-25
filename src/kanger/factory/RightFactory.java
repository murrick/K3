package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Index;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    public static final String SCHEMA = "rights";

    private long lastId = 0;
    private long firstId = 0;

    private Map<Predicate, List<Right>> predicatesLink = new HashMap<>();

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
        cache.clear();
        load.clear();
        predicatesLink.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache.add(base.cache);
            for(Map.Entry<Predicate, List<Right>> e : base.predicatesLink.entrySet()) {
                List<Right> rights = new ArrayList<>();
                rights.addAll(e.getValue());
                predicatesLink.put(e.getKey(), rights);
            }
        } else {
            lastId = 0;
            firstId = 0;
        }
    }

    public void commit(RightFactory base) {
        List<Right> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
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

    public Right add(Right r) {
        r.setId(lastId++);
        cache.add(r);

        for (List<Domain> tree : r.getTree()) {
            for (Domain d : tree) {
                if (!predicatesLink.containsKey(d.getPredicate())) {
                    predicatesLink.put(d.getPredicate(), new ArrayList<>());
                }
                if(!predicatesLink.get(d.getPredicate()).contains(r)) {
                    predicatesLink.get(d.getPredicate()).add(r);
                }
            }
        }

        return r;
    }

    public void expand(Right r) throws RuntimeErrorException {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(true).isEmpty()) {
                    user.getMind().getDomains().getWaiters().add(tree.get(0));
                } else {
                    tree.get(0).setStored();
                }
            }
        }
    }

    public Right get(long id) {
        Right t = (Right) cache.get(id);
        if (t == null) {
            t = (Right) load.get(id);
        }
        return t;
    }

    public Right load(long id) throws RuntimeErrorException {
        Right t = null;
        if (!user.isClosed()) {
            try {
                t = (Right) user.getStorage(SCHEMA).get(id);
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

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
        } else {
            transaction(null);
        }
    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

    @Override
    public Iterator<Right> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage, user);
    }

    public Iterator<Right> iterator(Predicate predicate) throws RuntimeErrorException {
        return new RightIterator(predicate);
    }

    public class RightIterator implements Iterator<Right> {

        Iterator<Long> iterator = null;
        Set<Long> rights = new HashSet<>();

        public RightIterator(Predicate p) throws RuntimeErrorException {
            if (predicatesLink.containsKey(p)) {
                for (Right r : predicatesLink.get(p)) {
                    rights.add(r.getId());
                }
            }
            if (!user.isClosed()) {
                try {
                    Index.IndexOne one = user.getPredicatesLink().getOne(p.getId());
                    if (one != null) {
                        rights.addAll(one.getData());
                    }
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    throw new RuntimeErrorException(e.toString());
                }
            }
            if (!rights.isEmpty()) {
                iterator = rights.iterator();
            }
        }

        @Override
        public boolean hasNext() {
            if (iterator != null) {
                return iterator.hasNext();
            } else {
                return false;
            }
        }

        @Override
        public Right next() {
            Long id = iterator.next();
            if (id != null) {
                Right r = get(id);
                if (r == null) {
                    try {
                        r = load(id);
                        r.linkExternal(user);
                    } catch (RuntimeErrorException e) {
                        e.printStackTrace(System.err);
                        return null;
                    }
                }
                return r;
            } else {
                return null;
            }

        }
    }
}
