package kanger.factory;

import kanger.Mind;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Right;
import kanger.primitives.Tree;

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

    private Mind mind = null;

    public RightFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public Right add() {
        Right p = new Right(mind);
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

    public Right getRoot() {
        return root;
    }

    public void setRoot(Right o) {
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

//    public void init() {
//        for (Right r = root; r != null; r = r.getNext()) {
//            for (Tree t = r.getT(); t != null; t = t.getRight()) {
//                for (Tree u = t; u != null; u = u.getDown()) {
//                    u.setCuted(0);
//                    u.setClosed(false);
//                }
//            }
//        }
//    }

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
            Right saved = (Right) pop[0];
            lastID = (long) pop[1];
//            if (root != null && saved != null && root.getId() != saved.getId()) {
//                for (Right t = root; t != null; t = t.getNext()) {
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
        for (Right q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Right r = root; r != null; r = r.getNext()) {
            r.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Right a = null, b;
        while (count-- > 0) {
            b = new Right(dis, mind);
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
        Tree t = mind.getTrees().add();
        r.getTree().add(t);
        List<Argument> arg = new ArrayList<>();
        for (Argument a : d.getArguments()) {
            arg.add(new Argument(a.getValue()));
        }
        t.getSequence().add(mind.getDomains().add(d.getPredicate(), d.isAntc(), arg, r));
    }
}
