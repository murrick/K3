package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Index;
import kanger.storage.Storage;
import kanger.units.*;

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
            for (Map.Entry<Predicate, List<Right>> e : base.predicatesLink.entrySet()) {
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
                if (!predicatesLink.get(d.getPredicate()).contains(r)) {
                    predicatesLink.get(d.getPredicate()).add(r);
                }
            }
        }

        return r;
    }

    public Right add(Domain d) throws RuntimeErrorException {
        Right p = find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (p != null) {
            return p;
        } else {
            Right r = new Right(d);
            r.setId(lastId++);
            cache.add(r);
            return r;
        }
    }


    public Right add(Predicate pred, boolean antc, boolean isQuery, ArgList arg) throws RuntimeErrorException {
        Right p = find(pred, antc, arg);
        if (p != null) {
            return p;
        } else {
            ArgList list = null;
            if (arg != null) {
                if (isQuery) {
                    list = arg.convert();
                    for (TValue t : list.getTValues(true)) t.setQuery();
                } else {
                    list = arg.convertBase();
                }
            }
            Right r = new Right(user);
            Domain d = user.getMind().getDomains().add(pred, antc, list, r);
            r.getTree().get(0).add(d);
            r.setGenerated(true);

            int save = user.getMind().getDebugLevel();
            user.getMind().setDebugLevel(0);
            Term origin = user.getMind().getTerms().add(d.toString());
            user.getMind().setDebugLevel(save);
            r.setOrig(origin);

            return user.getMind().getRights().add(r);
        }
    }

    public Right find(Domain d) throws RuntimeErrorException {
        return find(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public Right find(Predicate pred, boolean antc, ArgList arg) throws RuntimeErrorException {
        Domain d = new Domain(pred, antc, arg);
        Right temp = new Right(d);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Right) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                if (one.equalsTo(temp)) {
                    one.linkExternal(user);
                    return (Right) one;
                }
            }
        }
        return null;
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

    public long getFirstId() {
        return firstId;
    }

    public long getLastId() {
        return lastId;
    }

    @Override
    public Iterator<Right> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage, user);
    }

    public Iterator<Right> iterator(Predicate predicate) throws RuntimeErrorException {
        return new RightIterator(predicate);
    }

    public Iterator<Right> baseIterator(Boolean antc) throws RuntimeErrorException {
        return new DatabaseIterator(antc);
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

    public class DatabaseIterator implements Iterator<Right> {

        Iterator<Right> iterator = null;
        Right next = null;
        Boolean antc = null;

        public DatabaseIterator(Boolean antc) throws RuntimeErrorException {
            this.antc = antc;
            this.iterator = iterator();
            while (iterator.hasNext()) {
                next = next();
                if (next.isStored()) {
                    if(this.antc != null) {
                        next.linkExternal(user);
                        if (next.getDomain().isAntc() == antc) {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        @Override
        public Right next() {
            Right current = next;
            if (next != null) {
                try {
                    while (iterator.hasNext()) {
                        next = next();
                        if (next.isStored()) {
                            if(antc != null) {
                                next.linkExternal(user);
                                if (next.getDomain().isAntc() == antc) {
                                    break;
                                }
                            } else {
                                break;
                            }
                        }
                    }
                } catch (RuntimeErrorException e) {
                    next = null;
                    e.printStackTrace();
                }
            }
            return current;
        }
    }

    public Map<Predicate, List<Right>> getPredicatesLink() {
        return predicatesLink;
    }
}
