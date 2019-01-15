package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Function;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public class FunctionFactory implements Iterable<Function> {

    private static final String SCHEMA = "functions";

    //    private Function root = null;
    private long lastID = 0;

//    private Stack<Object[]> stack = new Stack<>();

    private Cache cache = new Cache();
    private User user = null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(FunctionFactory base) {
        List<Function> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Function) p);
        }
        for (Function p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public Function add() {
        Function p = new Function(user);
        p.setId(++lastID);
        cache.add(p);
        return p;
    }

    public Function get(long id) throws IOException, ClassNotFoundException {
        Function d = (Function) cache.get(id);
        if (d == null) {
            d = (Function) user.getStorage(SCHEMA).get(id);
        }
        return d;
    }

    //    public Function getRoot() {
//        return root;
//    }
//
//    public void setRoot(Function o) {
//        root = o;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFunctions());
        } else {
            transaction(null);
        }
    }


//    private void mark() {
//        stack.push(new Object[]{root, lastID});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Function saved = (Function) pop[0];
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if(stack.isEmpty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size();
//        int cnt = 0;
//        for (Function q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        dos.writeInt(size());
//        for (Function r = root; r != null; r = r.getNext()) {
////            r.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        Function a = null, b = null;
//        while (count-- > 0) {
////            b = new Function(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }

    @Override
    public Iterator<Function> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
