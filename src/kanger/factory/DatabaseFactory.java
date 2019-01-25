package kanger.factory;

import kanger.User;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.DataIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.*;

import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class DatabaseFactory implements Iterable<Record> {

    public static final String SCHEMA = "database";

    private long lastId = 0;
    private long firstId = 0;

//    private Record current = null;
//    private Record stop = null;
//    private DatabaseFactory next = null;

//    private Stack<Object[]> stack = new Stack<>();

    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

    public DatabaseFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DatabaseFactory base) {
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

    public void commit(DatabaseFactory base) {
        List<Record> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add(0, (Record) p);
        }
        Map<Integer, Integer> map = new HashMap<>();
        for (Record p : list) {
            p.setId(lastId++);
            cache.add(p);
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

    public Record add(Domain d) throws RuntimeErrorException {
        Record p = find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (p != null) {
            return p;
        } else {
            Record r = new Record(d);
            r.setId(lastId++);
            cache.add(r);
            return r;
        }
    }


    public Record add(Predicate pred, boolean antc, boolean isQuery, ArgList arg) throws RuntimeErrorException {
        Record p = find(pred, antc, arg);
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

            user.getMind().getRights().add(r);

            return add(d);
        }
    }

    public Record find(Domain d) throws RuntimeErrorException {
        return find(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public Record find(Predicate pred, boolean antc, ArgList arg) throws RuntimeErrorException {
        Domain d = new Domain(pred, antc, arg);
        Record temp = new Record(d);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Record) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                one.linkExternal(user);
                if (one.equalsTo(temp)) {
                    return (Record) one;
                }
            }
        }
        return null;
    }

    public Record get(long id) {
        Record t = (Record) cache.get(id);
        if (t == null) {
            t = (Record) load.get(id);
        }
        return t;
    }

    public Record load(long id) throws RuntimeErrorException {
        Record t = null;
        if (!user.isClosed()) {
            try {
                t = (Record) user.getStorage(SCHEMA).get(id);
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


//    public Record getRoot() {
//        return root;
//    }

//    public void setRoot(Record o) {
//        root = o;
//    }
//

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getDatabase());
        } else {
            transaction(null);
        }
    }

    //    public void mark() {
//        stack.push(new Object[]{root, lastId});
//    }
//
//    public void commit() {
//        if (!stack.empty()) {
//            stack.pop();
//        }
//    }
//
//    public void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Record saved = (Record) pop[0];
//            lastId = (long) pop[1];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }
//
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
    public Iterator<Record> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new DataIterator(true, cache, storage, user);
    }

}
