package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;
import kanger.units.Tree;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    private static final String SCHEMA = "rights";

    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(RightFactory base) {
        List<Right> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Right) p);
        }
        for (Right p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public Right add() {
        Right p = new Right(user);
        p.setId(++lastID);
        cache.add(p);
        return p;
    }

    public Right get(long id) {
        Right d = (Right) cache.get(id);
        if (d == null) {
            try {
                d = (Right) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

//    public Right getLast() {
//        for (Right r = root; r != null; r = r.getNext()) {
//            if(r.getNext() == null) {
//                return r;
//            }
//        }
//        return null;
//    }

//    public Right getRoot() {
//        return root;
//    }
//
//    public void setRoot(Right o) {
//        root = o;
//    }
//

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
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
//            Right saved = (Right) pop[0];
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if(stack.isEmpty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
//        int cnt = 0;
//        for (Right q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        dos.writeInt(size());
//        for (Right r = root; r != null; r = r.getNext()) {
////            r.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        Right a = null, b = null;
//        while (count-- > 0) {
////            b = new Right(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }
//
    public void add(Domain d) {
        Right r = add();
        Tree t = user.getMind().getTrees().add(r);
        r.getTree().add(t);
        ArgList arg = new ArgList();
        for (Argument a : d.getArguments()) {
            arg.add(new Argument(a.getValue()));
        }
        t.getSequence().add(user.getMind().getDomains().add(d.getPredicate(), d.isAntc(), arg, r));
    }

    @Override
    public Iterator<Right> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
