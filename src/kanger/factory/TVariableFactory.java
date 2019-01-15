package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Right;
import kanger.units.TVariable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TVariableFactory implements Iterable<TVariable> {

    private static final String SCHEMA = "tvariables";

    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public TVariableFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(TVariableFactory base, Collection vars) {
        List<TVariable> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((TVariable) p);
        }
        for (TVariable p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public TVariable createTVar(Right r) {
        TVariable p = new TVariable(user);
        p.setId(++lastID);
        p.setIndex(user.getMind().getTerms().nextVarIndex());
        p.setRight(r);
        cache.add(p);
        return p;
    }

//    public Set<TVariable> get(Right r) {
//        Set<TVariable> set = new HashSet<>();
//        for (TVariable t = root; t != null; t = t.getNext()) {
//            if (t.getRight().getId() == r.getId()) {
//                set.add(t);
//            }
//        }
//        return set;
//    }

    public TVariable get(long id) {
        TVariable d = (TVariable) cache.get(id);
        if (d == null) {
            try {
                d = (TVariable) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

//    public TVariable getRoot() {
//        return root;
//    }
//
//    public void setRoot(TVariable o) {
//        root = o;
//    }
//

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTVars());
        } else {
            transaction(null);
        }
    }

    //    public void update() {
//
//    }
//
//    private void mark() {
//        stack.push(new Object[]{root, lastID});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            TVariable saved = (TVariable) pop[0];
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if(stack.isEmpty()) {
//            mark();
//        }
//    }
//
    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        int sz = size();
//        dos.writeInt(sz);
//        for (TVariable t = root; t != null; t = t.getNext()) {
////            t.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        TVariable a = null, b = null;
//        while (count-- > 0) {
////            b = new TVariable(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }
//
    @Override
    public Iterator<TVariable> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }

    public void update() {
        if (!user.isClosed()) {
            try {
                for (Identifiable p : cache) {
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }
}
