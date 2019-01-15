package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.FValue;
import kanger.units.Function;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class FValueFactory implements Iterable<FValue> {

    private static final String SCHEMA = "fvalues";

    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public FValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(FValueFactory base) {
        List<FValue> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((FValue) p);
        }
        for (FValue p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public FValue add(Function f) {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, user);
                t.setId(lastID++);
                cache.add(t);
            } else {
                return null;
            }
        }
        return t;
    }

//    public FValue get(Function f) {
//        for (FValue v = root; v != null; v = v.getNext()) {
//            if (v.getFunc().getId() == f.getId() && v.isActual(f)) {
//                return v;
//            }
//        }
//        return null;
//    }
//

    public FValue find(Function f) {
        FValue temp = new FValue(f, user);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (FValue) one;
            }
        }
        if (!user.isClosed()) {
            try {
                for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                    if (one.equalsTo(temp)) {
                        return (FValue) one;
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

    public FValue get(long id) throws IOException, ClassNotFoundException {
        FValue d = (FValue) cache.get(id);
        if (d == null) {
            d = (FValue) user.getStorage(SCHEMA).get(id);
        }
        return d;
    }

//    public FValue getRoot() {
//        return root;
//    }

    //    public void setRoot(FValue o) {
//        root = o;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFValues());
        } else {
            transaction(null);
        }
    }

    public void mark() {
        cache.mark();
    }


    public void commit() {
        cache.commit();
    }

    public void release() {
        lastID = cache.release() + 1;
    }

    //    public void commit() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            FValue saved = (FValue) pop[0];
//            lastID = (long) pop[1];
//
//            FValue t = root;
//            FValue q = root;
//            while (t != null && t != saved) {
//                if (!t.isClosed()) {
//                    if (t == root) {
//                        root = t = q = t.getNext();
//                    } else {
//                        q.setNext(t.getNext());
//                        t = q.getNext();
//                    }
//                } else {
//                    q = t;
//                    t = t.getNext();
//                }
//            }
//        }
//        if (stack.isEmpty()) {
//            mark();
//        }
//    }
//
//    public void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            FValue saved = (FValue) pop[0];
////            lastID = (long) pop[1];
//
//            FValue t = root;
//            FValue q = root;
//            while (t != null && t != saved) {
////                if (t.isBlocked()) {
////                    if (t == root) {
////                        root = t = q = t.getNext();
////                    } else {
////                        q.setNext(t.getNext());
////                        t = q.getNext();
////                    }
////                } else {
//                    q = t;
//                    t = t.getNext();
////                }
//            }
//        }
//        if (stack.isEmpty()) {
//            mark();
//        }
//    }


    public int size() {
        return cache.size();
//        int cnt = 0;
//        for (FValue q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        int count = size();
//        dos.writeInt(count);
//        for (FValue d = root; d != null; d = d.getNext()) {
////            d.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        FValue a = null, b = null;
//        while (count-- > 0) {
////            b = new FValue(user).readCompiledData(dis);
//            if (a != null) {
//                a.setNext(b);
//            } else {
//                root = b;
//            }
//            a = b;
//        }
//    }

//    public FValue getMark() {
//        if (!stack.empty()) {
//            Object[] pop = stack.peek();
//            return (FValue) pop[0];
//        } else {
//            return null;
//        }
//    }
//

    @Override
    public Iterator<FValue> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
