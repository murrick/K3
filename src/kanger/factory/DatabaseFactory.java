package kanger.factory;

import kanger.User;
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
    private int lastTag = 0;

//    private Record current = null;
//    private Record stop = null;
//    private DatabaseFactory next = null;

//    private Stack<Object[]> stack = new Stack<>();

    private Cache cache = new Cache();
    private User user = null;

    public DatabaseFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DatabaseFactory base) {
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            lastTag = base.lastTag;
            cache.add(base.cache);
        } else {
//            root = null;
//            next = null;
            lastId = 0;
            firstId = 0;
            lastTag = 0;
            cache.clear();
        }
//        stack.clear();
//        mark();
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
            if (!map.containsKey(p.getTag())) {
                map.put(p.getTag(), ++lastTag);
            }
            p.setTag(map.get(p.getTag()));
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


    public Record add(Domain d) {
        Record p = find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (p != null) {
            return p;
        } else {
            Record r = new Record(d);
            r.setId(lastId++);
            r.setTag(lastTag);
            cache.add(r);
            return r;
        }
    }


    public Record add(Predicate pred, boolean antc, boolean isQuery, ArgList arg) {
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
            Right r = user.getMind().getRights().add();
            List<Domain> t = new ArrayList<>();
            Domain d = user.getMind().getDomains().add(pred, antc, list, r);
            d.setRight(r);
            t.add(d);
            r.getTree().add(t);
            r.setGenerated(true);

            int save = user.getMind().getDebugLevel();
            user.getMind().setDebugLevel(0);
            Term origin = user.getMind().getTerms().add(d.toString());
            user.getMind().setDebugLevel(save);
            r.setOrig(origin);

            return add(d);
        }
    }

    public Record find(Domain d) {
        return find(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public Record find(Predicate pred, boolean antc, ArgList arg) {
        Domain d = new Domain(pred, antc, arg);
        Record temp = new Record(d);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Record) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
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
            try {
                t = (Record) user.getStorage(SCHEMA).get(id);
                if (t != null) {
                    cache.add(t);
                    t.linkExternal(user);
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
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

    public int incTag() {
        ++lastTag;
        return lastTag;
    }

    public int getTag() {
        return lastTag;
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
        return new RecordIterator(true, cache, storage);
    }

    public class RecordIterator extends DataIterator {

        public RecordIterator(boolean backward, Cache cache, Storage storage) {
            super(backward, cache, storage);
        }

        @Override
        public Identifiable next() {
            Identifiable next = super.next();
            next.linkExternal(user);
            return next;
        }
    }
}
