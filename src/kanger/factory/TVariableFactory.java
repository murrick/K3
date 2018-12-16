package kanger.factory;

import kanger.User;
import kanger.primitives.Right;
import kanger.primitives.TVariable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TVariableFactory {

    private TVariable root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public TVariableFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TVariableFactory base) {
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

    public void commit(TVariableFactory base, Collection vars) {
        List<TVariable> list = new ArrayList();
        for (TVariable p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (TVariable p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
            vars.add(p);
        }
    }

    public TVariable createTVar() {
        TVariable p = new TVariable(user);
        p.setId(++lastID);
        p.setIndex(user.getMind().getTerms().nextVarIndex());
        p.setRight(user.getMind().getRights().getRoot());
        p.setNext(root);
        root = p;
        return p;
    }

    public Set<TVariable> get(Right r) {
        Set<TVariable> set = new HashSet<>();
        for (TVariable t = root; t != null; t = t.getNext()) {
            if (t.getRight().getId() == r.getId()) {
                set.add(t);
            }
        }
        return set;
    }

    public TVariable get(long id) {
        for (TVariable t = root; t != null; t = t.getNext()) {
            if (t.getId() == id) {
                return t;
            }
        }
        return null;
    }

    public TVariable getRoot() {
        return root;
    }

    public void setRoot(TVariable o) {
        root = o;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTVars());
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
            TVariable saved = (TVariable) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if(stack.isEmpty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (TVariable q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        int sz = size();
        dos.writeInt(sz);
        for (TVariable t = root; t != null; t = t.getNext()) {
            t.writeCompiledData(dos, user);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        TVariable a = null, b;
        while (count-- > 0) {
            b = new TVariable(dis, user);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
