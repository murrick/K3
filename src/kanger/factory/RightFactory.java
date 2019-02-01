package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.DataIterator;
import kanger.storage.RightsCache;
import kanger.storage.Storage;
import kanger.units.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by murray on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    public static final String SCHEMA = "rights";

    private long lastId = 0;
    private long firstId = 0;

    private RightsCache cache = new RightsCache();
    private RightsCache load = new RightsCache();
    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
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
        return r;
    }

    public void addSolve(Right query, Right solve) {
        cache.addSolve(query, solve);
    }

    public void addSolve(Right query) {
        cache.addSolve(query);
    }

    public void expand(Right r) throws RuntimeErrorException {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(true).isEmpty()) {
                    user.getMind().getDomains().getWaiters().add(tree.get(0));
                } else if(r.getTree().size() == 1){
                    tree.get(0).setStored();
                } else {
                    tree.get(0).createStored();
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

    public Right add(Domain domain) throws RuntimeErrorException {
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

    public Right store(Domain d) {
        cache.setStored(d.getRight());
        return d.getRight();
    }

    public Right find(Domain domain) throws RuntimeErrorException {
        for (Identifiable one : cache.find(domain.getHashBase())) {
            if (((Right) one).equalsTo(domain)) {
                return (Right) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(domain.getHashBase())) {
                one.linkExternal(user);
                if (((Right) one).equalsTo(domain)) {
                    return (Right) one;
                }
            }
        }
        return null;
    }


    @Override
    public Iterator<Right> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage, user);
    }

    public RightsCache.Database getDatabase() {
        return cache.getDatabase(-1);
    }

    public RightsCache.Database getDatabase(long fromId) {
        return cache.getDatabase(fromId);
    }

    public RightsCache.Links getLinks(Predicate predicate) {
        return cache.getLinks(predicate);
    }

    public RightsCache.Solves getSolves() {
        return cache.getSolves();
    }

    public RightsCache.Values getValues() {
        return cache.getValues();
    }

    public long getLastId() {
        return lastId;
    }

    public long getFirstId() {
        return firstId;
    }
}
