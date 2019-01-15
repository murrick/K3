package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class DatabaseFactory implements Iterable<Record> {

    private static final String SCHEMA = "database";

    //    private Record root = null;
    private long lastID = 0;
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
//            next = base;
//            root = base.root;
            lastID = base.lastID;
            lastTag = base.lastTag;
            cache.add(base.cache);
        } else {
//            root = null;
//            next = null;
            lastID = 0;
            lastTag = 0;
            cache.clear();
        }
//        stack.clear();
//        mark();
    }

    public void commit(DatabaseFactory base) {
        List<Record> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Record) p);
        }

        Map<Integer, Integer> map = new HashMap<>();
        for (Record p : list) {
            if (!map.containsKey(p.getTag())) {
                map.put(p.getTag(), ++lastTag);
            }
            p.setTag(map.get(p.getTag()));
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public Record add(Domain d) {
        Record p = find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (p != null) {
            return p;
        } else {
            Record r = new Record(d);
            r.setId(lastID++);
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
            Tree t = user.getMind().getTrees().add(r);
//            t.setRight(r);
            t.setUsed();
            Domain d = user.getMind().getDomains().add(pred, antc, list, r);
            d.setRight(r);
            t.getSequence().add(d);
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
        Domain temp = new Domain(pred, antc, arg);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Record) one;
            }
        }
        if (!user.isClosed()) {
            try {
                for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                    if (one.equalsTo(temp)) {
                        return (Record) one;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Record get(long id) {
        Record t = (Record) cache.get(id);
        if (t == null) {
            try {
                t = (Record) user.getStorage(SCHEMA).get(id);
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
//        stack.push(new Object[]{root, lastID});
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
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
//        int cnt = 0;
//        for (Record q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

    //    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeInt(size());
//        for (Record d = root; d != null; d = d.getNext()) {
//            dos.writeLong(d.getDomain().getId());
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        int count = dis.readInt();
//        Record a = null, b;
//        while (count-- > 0) {
//            b = new Record(user.getMind().getDomains().get(dis.readLong()));
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }
//
    public int incTag() {
        ++lastTag;
        return lastTag;
    }

    public int getTag() {
        return lastTag;
    }

//    public Set<TVariable> getTVariables(boolean full) {
//        Set<TVariable> set = new HashSet<>();
//        for (Record d = root; d != null; d = d.getNext()) {
//            set.addAll(d.getDomain().getArguments().getTVariables(full));
//        }
//        return set;
//    }

    //    public DatabaseFactory localOnly(boolean local) {
//        if(local && next != null) {
//            stop = next.root;
//        } else {
//            stop = null;
//        }
//        return this;
//    }
//
    @Override
    public Iterator<Record> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
