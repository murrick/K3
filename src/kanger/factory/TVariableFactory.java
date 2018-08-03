package kanger.factory;

import kanger.Mind;
import kanger.primitives.Right;
import kanger.primitives.TVariable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class TVariableFactory {

    private TVariable root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public TVariableFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public TVariable createTVar() {
        TVariable p = new TVariable(mind);
        p.setId(++lastID);
        p.setIndex(mind.getTerms().nextVarIndex());
        p.setRight(mind.getRights().getRoot());
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

    public void reset() {
        while(stack.size() > 1) {
            stack.pop();
        }
        release();
    }

    public void clear() {
        root = null;
        lastID = 0;
        stack.clear();
        mark();
    }

    public void mark() {
        stack.push(new Object[]{root, lastID});
    }

    public void commit() {
        if(!stack.empty()) {
            stack.pop();
        }
    }

    public void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            TVariable saved = (TVariable) pop[0];
            lastID = (long) pop[1];
//            if (root != null && saved != null && root.getId() != saved.getId()) {
//                for (TVariable t = root; t != null; t = t.getNext()) {
//                    if (t.getNext() != null && t.getNext().getId() == saved.getId()) {
//                        t.setNext(null);
//                        break;
//                    }
//                }
//            }
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
            t.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        TVariable a = null, b;
        while (count-- > 0) {
            b = new TVariable(dis, mind);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
