package kanger.factory;

import kanger.User;
import kanger.primitives.ArgList;
import kanger.primitives.Argument;
import kanger.primitives.UnitIterator;
import kanger.units.Domain;
import kanger.units.Predicate;
import kanger.units.Right;

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

    private Domain root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public DomainFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        if (base != null) {
            root = base.root;
            lastID = base.lastID;
        } else {
            root = null;
            lastID = 0;
        }
        stack.clear();
        mark();
    }

    public void commit(DomainFactory base) {
        List<Domain> list = new ArrayList();
        for (Domain p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Domain p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }


    public Domain add(long rightId) {
        Domain p = new Domain(user);
        p.setNext(root);
        p.setRightId(rightId);
        p.setId(lastID++);
        root = p;
        return p;
    }


    public Domain add(long predicateId, boolean antc, ArgList arg, long rightId) {
        Domain p = find(predicateId, antc, arg, rightId);
        if (p != null) {
            return p;
        } else {
            p = new Domain(user);
            p.setNext(root);
            p.setPredicateId(predicateId);
            p.setAntc(antc);
            p.setRightId(rightId);
            p.setId(lastID++);
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
            root = p;
            return p;
        }
    }

    public Domain find(long predicateId, boolean antc, ArgList arg, long rightId) {
        Domain temp = new Domain(predicateId, antc, arg, rightId);
        for (Domain p = root; p != null; p = p.getNext()) {
            if(p.equalsTo(temp)) {
                return p;
            }
        }
        return null;
    }

    public Domain get(long id) {
        for (Domain p = root; p != null; p = p.getNext()) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
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


    private void mark() {
        stack.push(new Object[]{root, lastID});
    }

    private void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            Domain saved = (Domain) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if (stack.empty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Domain q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Domain d = root; d != null; d = d.getNext()) {
//            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Domain a = null, b = null;
        while (count-- > 0) {
//            b = new Domain(user).readCompiledData(dis);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

    @Override
    public Iterator<Domain> iterator() {
        return new UnitIterator(root);
    }
}
