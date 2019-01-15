package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.TValue;
import kanger.units.TVariable;
import kanger.units.Term;

import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TValueFactory implements Iterable<TValue> {

    private static final String SCHEMA = "tvalues";

    private Map<TVariable, Long> current = new HashMap<>();
    private Map<TVariable, Iterator<TValue>> scanner = new HashMap<>();
    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public TValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TValueFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
        current.clear();
    }

    public void commit(TValueFactory base) {
        List<TValue> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((TValue) p);
        }
        for (TValue p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public TValue add(TVariable tv, Term o) {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, user);
            t.setTVar(tv);
            t.setId(lastID++);
            cache.add(t);
        }
        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        TValue v = get(current.get(tv));
        return v;
    }

    public boolean isEmpty(TVariable tv) {
        return size() == 0 || !current.containsKey(tv);
    }


    // Мотаем в обратную сторону
    public TValue rewind(TVariable tv) {
        scanner.put(tv, iterator());
        return next(tv);
    }

//    public boolean isMarked(TValue v) {
//        if (stack.isEmpty() || stack.peek()[0] == null) {
//            return false;
//        } else if (v.getId() == ((TValue) stack.peek()[0]).getId()) {
//            return true;
//        }
//        for (TValue x = root; x != null; x = x.getNext()) {
//            if (x.getId() == ((TValue) stack.peek()[0]).getId()) {
//                return true;
//            } else if (v.getId() == x.getId()) {
//                return false;
//            }
//        }
//        return false;
//    }
//
    //    public TValue rewindTop(TVariable tv) {
//        if (root == null || root == getMark()) {
//            return null;
//        }
//        if (root.getTVar().getId() == tv.getId() && !root.isBlocked()) {
//            return root;
//        } else {
//            TValue v = next(tv, root);
//            if (v == null) {
//                return null;
//            } else {
//                return v;
//            }
//        }
//    }
//
    public TValue next(TVariable tv) {
        TValue n = null;
        while (scanner.get(tv).hasNext()) {
            TValue x = scanner.get(tv).next();
            if (x.getTVar().getId() == tv.getId()) {
                n = x;
                break;
            }
        }
        return n;
    }

    //    public TValue nextTop(TVariable tv, TValue v) {
//        if (root == null || root == getMark()) {
//            return null;
//        }
//        for (v = v.getNext(); v != null && v != getMark(); v = v.getNext()) {
//            if (v.getTVar().getId() == tv.getId()) {
//                return v;
//            }
//        }
//        return null;
//    }
//
    public TValue find(TVariable tv, Term v) {
        TValue temp = new TValue(tv, v);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                if (one.equalsTo(temp)) {
                    return (TValue) one;
                }
            }
        }
        return null;
    }

    public TValue get(long id)  {
        TValue d = (TValue) cache.get(id);
        if (d == null) {
            try {
                d = (TValue) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

//    public TValue getRoot() {
//        return root;
//    }

//    public void setRoot(TValue o) {
//        root = o;
//    }
//

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTValues());
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

//    public TValue getMark() {
//        if (!stack.empty()) {
//            Object[] pop = stack.peek();
//            return (TValue) pop[0];
//        } else {
//            return null;
//        }
//    }
//
    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v.getId());
        }
        return v;
    }

//    public void remove(TVariable tv) throws IOException, ClassNotFoundException {
//        TValue t = get(tv);
//        if (t != null) {
//            if(cache.containsKey(t.getId())) {
//                cache.remove(t.getId());
//            } else if(!user.isClosed()) {
//                user.getStorage(SCHEMA).remove(t.getId());
//            }
//        }
//    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        int count = size();
//        dos.writeInt(count);
//        for (TValue d = root; d != null; d = d.getNext()) {
////            d.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        TValue a = null, b = null;
//        while (count-- > 0) {
////            b = new TValue(user).readCompiledData(dis);
//            if (a != null) {
//                a.setNext(b);
//            } else {
//                root = b;
//            }
//            a = b;
//        }
//    }
//
    @Override
    public Iterator<TValue> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }

//    public long getCommitID() {
//        return commitID;
//    }
//
//    public void setCommitID(long commitID) {
//        this.commitID = commitID;
//    }
//
//    public void incCommitId() {
//        ++this.commitID;
//    }
}
