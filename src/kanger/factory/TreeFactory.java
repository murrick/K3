package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
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
public class TreeFactory implements Iterable<Tree> {

    private static final String SCHEMA = "trees";

    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public TreeFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TreeFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(TreeFactory base) {
        List<Tree> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Tree) p);
        }
        for (Tree p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public Tree add(Right r) {
        Tree p = new Tree(user);
        p.setId(++lastID);
        p.setRight(r);
        cache.add(p);
        return p;
    }

    public Tree get(long id) {
        Tree d = (Tree) cache.get(id);
        if (d == null) {
            try {
                d = (Tree) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

//    public Tree getRoot() {
//        return root;
//    }
//
//    public void setRoot(Tree o) {
//        root = o;
//    }
//

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTrees());
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
//            Tree saved = (Tree) pop[0];
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
//        dos.writeInt(size());
//        for (Tree r = root; r != null; r = r.getNext()) {
////            r.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        Tree a = null, b = null;
//        while (count-- > 0) {
////            b = new Tree(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }

    @Override
    public Iterator<Tree> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
