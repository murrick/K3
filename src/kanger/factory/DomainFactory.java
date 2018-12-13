package kanger.factory;

import kanger.User;
import kanger.primitives.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class DomainFactory {

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


    public Domain add(Right r) {
        Domain p = new Domain(user);
        p.setNext(root);
        p.setRight(r);
        p.setId(lastID++);
        root = p;
        return p;
    }


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            return p;
        } else {
            p = new Domain(user);
            p.setNext(root);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
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

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) {
        for (Domain p = root; p != null; p = p.getNext()) {
            if (p.isAntc() == antc
                    && p.getPredicate() == pred
                    && p.getRight().getId() == r.getId()) {
                int i = 0;
                for (; i < pred.getRange(); ++i) {
                    if ((p.get(i).isTSet() && arg.get(i).isTSet() && p.get(i).getT().getId() == arg.get(i).getT().getId())
                            || (p.get(i).isFSet() && arg.get(i).isFSet() && p.get(i).getF().getId() == arg.get(i).getF().getId())
                            || (!p.get(i).isTSet() && !arg.get(i).isTSet()
                            && !p.get(i).isFSet() && !arg.get(i).isFSet()
                            && !p.get(i).isEmpty() && !arg.get(i).isEmpty()
                            && p.get(i).getValue().getId() == arg.get(i).getValue().getId())) {
                    } else {
                        break;
                    }
                }
                if (i == pred.getRange()) {
                    return p;
                }
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

    public Domain getRoot() {
        return root;
    }

    public void setRoot(Domain o) {
        root = o;
    }

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
            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Domain a = null, b;
        while (count-- > 0) {
            b = new Domain(dis, user);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
