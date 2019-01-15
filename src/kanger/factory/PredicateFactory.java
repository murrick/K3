package kanger.factory;

import kanger.User;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.FValue;
import kanger.units.Predicate;
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
public class PredicateFactory implements Iterable<Predicate> {

    private static final String SCHEMA = "predicates";

    private long lastID = 0;

    private Cache cache = new Cache();
    private User user = null;

    public PredicateFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(PredicateFactory base) {
        if (base != null) {
            lastID = base.lastID;
            cache.add(base.cache);
        } else {
            lastID = 0;
            cache.clear();
        }
    }

    public void commit(PredicateFactory base) {
        List<Predicate> list = new ArrayList();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Predicate) p);
        }
        for (Predicate p : list) {
            p.setId(lastID++);
            cache.add(p);
        }
    }

    public Predicate add(Term line, int range) {
        Predicate p = find(line, range);
        if (p != null) {
            return p;
        } else {
            p = new Predicate(user);
            p.setId(lastID++);
            p.setRange(range);
            p.setName(line);
            cache.add(p);
            return p;
        }
    }

    public Predicate find(Term line, int range) {
        Predicate temp = new Predicate(line, range);
        for (Identifiable one : cache.find(temp.getHash())) {
            if (one.equalsTo(temp)) {
                return (Predicate) one;
            }
        }
        if (!user.isClosed()) {
            try {
                for (Identifiable one : user.getStorage(SCHEMA).find(temp.getHash())) {
                    if (one.equalsTo(temp)) {
                        return (Predicate) one;
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

    public Predicate get(long id) {
        Predicate d = (Predicate) cache.get(id);
        if (d == null) {
            try {
                d = (Predicate) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return d;
    }

//    public Predicate getRoot() {
//        return root;
//    }
//
//    public void setRoot(Predicate root) {
//        this.root = root;
//    }
//
    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getPredicates());
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
//            Predicate saved = (Predicate) pop[0];
//            lastID = (long) pop[1];
//            root = saved;
//        }
//        if (stack.isEmpty()) {
//            mark();
//        }
//    }
//
    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }
//
//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        dos.writeInt(size());
//        for (Predicate p = root; p != null; p = p.getNext()) {
////            p.writeCompiledData(dos);
//        }
//        List<Long[]> links = new ArrayList<>();
//        //TODO: Save causes
////        for(Predicate p = root; p != null; p = p.getNext()) {
////            for(Solution s = p.getSolve(); s != null; s = s.getNext()) {
////                for(Solution x : s.getCauses()) {
////                    links.createTVar(new Long[]{s.getPredicate().getId(), s.getId(), x.getPredicate().getId(), x.getId()});
////                }
////            }
////        }
//        dos.writeInt(links.size());
//        for (Long[] l : links) {
//            dos.writeLong(l[0]);
//            dos.writeLong(l[1]);
//            dos.writeLong(l[2]);
//            dos.writeLong(l[3]);
//        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException {
//        clear();
//        lastID = dis.readLong();
//        int count = dis.readInt();
//        Predicate a = null, b = null;
//        while (count-- > 0) {
////            b = new Predicate(user).readCompiledData(dis);
//            if (a == null) {
//                root = b;
//            } else {
//                a.setNext(b);
//            }
//            a = b;
//        }
//        //TODO: Load causes
////        count = dis.readInt();
////        while (count-- > 0) {
////            Predicate p = createCVar(dis.readLong());
////            Solution s = p.getSolve(dis.readLong());
////            Predicate xp = createCVar(dis.readLong());
////            Solution xs = xp.getSolve(dis.readLong());
////            s.getCauses().createTVar(xs);
////        }
//    }
//
    @Override
    public Iterator<Predicate> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
