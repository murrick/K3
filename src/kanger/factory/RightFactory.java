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
public class RightFactory {

    private Right root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public RightFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(RightFactory base) {
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

    public void commit(RightFactory base) {
        List<Right> list = new ArrayList();
        for (Right p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Right p : list) { 
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public Right add() {
        Right p = new Right(user);
        p.setId(++lastID);
        p.setNext(root);
        root = p;
        return p;
    }

    public Right get(long rd) {
        for (Right r = root; r != null; r = r.getNext()) {
            if (r.getId() == rd) {
                return r;
            }
        }
        return null;
    }

    public Right getLast() {
        for (Right r = root; r != null; r = r.getNext()) {
            if(r.getNext() == null) {
                return r;
            }
        }
        return null;
    }

    public Right getRoot() {
        return root;
    }

    public void setRoot(Right o) {
        root = o;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getRights());
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
            Right saved = (Right) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if(stack.isEmpty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Right q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Right r = root; r != null; r = r.getNext()) {
            r.writeCompiledData(dos, user);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Right a = null, b;
        while (count-- > 0) {
            b = new Right(user).readCompiledData(dis);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

    public void add(Domain d) {
        Right r = add();
        Tree t = user.getMind().getTrees().add();
        r.getTree().add(t);
        ArgList arg = new ArgList();
        for (Argument a : d.getArguments()) {
            arg.add(new Argument(a.getValue()));
        }
        t.getSequence().add(user.getMind().getDomains().add(d.getPredicate(), d.isAntc(), arg, r));
    }
}
