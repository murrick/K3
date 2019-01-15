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
import kanger.units.Term;

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
public class DomainFactory implements Iterable<Domain> {

    private static final String SCHEMA = "domains";

//    private Domain root = null;
    private long lastID = 0;

//    private Stack<Object[]> stack = new Stack<>();

    private Cache cache = new Cache();
    private User user = null;

    public DomainFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        if (base != null) {
//            root = base.root;
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
//            root = null;
            lastID = 0;
            cache.clear();
        }
//        stack.clear();
//        mark();
    }

    public void commit(DomainFactory base) {
        List<Domain> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Domain) p);
        }
        for (Domain p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }


    public Domain add(Right r) {
        Domain p = new Domain(user);
        p.setRight(r);
        p.setId(lastID++);
        cache.add(p);
        return p;
    }


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            return p;
        } else {
            p = new Domain(user);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
            p.setId(lastID++);
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
            cache.add(p);
            return p;
        }
    }

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) {
        Domain temp = new Domain(pred, antc, arg, r);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Domain) one;
            }
        }
        if (!user.isClosed()) {
            try {
                for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                    if (one.equalsTo(temp)) {
                        return (Domain) one;
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

    public Domain get(long id) throws IOException, ClassNotFoundException {
        Domain d = (Domain) cache.get(id);
        if(d == null) {
            d = (Domain) user.getStorage(SCHEMA).get(id);
        }
        return d;
    }

//    public Domain getRoot() {
//        return root;
//    }
//
//    public void setRoot(Domain o) {
//        root = o;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getDomains());
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
//            Domain saved = (Domain) pop[0];
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size();
//        int cnt = 0;
//        for (Domain q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        dos.writeInt(size());
//        for (Domain d = root; d != null; d = d.getNext()) {
////            d.writeCompiledData(dos);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        Domain a = null, b = null;
//        while (count-- > 0) {
////            b = new Domain(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//    }

    @Override
    public Iterator<Domain> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
