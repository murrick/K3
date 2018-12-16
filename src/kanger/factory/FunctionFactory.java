package kanger.factory;

import kanger.User;
import kanger.primitives.Domain;
import kanger.primitives.Function;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FunctionFactory { 

    private Function root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user= null;

    public FunctionFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FunctionFactory base) {
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

    public void commit(FunctionFactory base) {
        List<Function> list = new ArrayList();
        for (Function p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Function p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public Function add() {
        Function p = new Function(user);
        p.setId(++lastID);
        p.setNext(root);
        root = p;
        return p;
    }

    public Function get(long id) {
        for (Function r = root; r != null; r = r.getNext()) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    public Function getRoot() {
        return root;
    }

    public void setRoot(Function o) {
        root = o;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFunctions());
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
            Function saved = (Function) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if(stack.isEmpty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Function q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Function r = root; r != null; r = r.getNext()) {
            r.writeCompiledData(dos, user);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Function a = null, b;
        while (count-- > 0) {
            b = new Function(dis, user);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
