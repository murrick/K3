package kanger.factory;

import kanger.Mind;
import kanger.primitives.Tree;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class TreeFactory {

    private Tree root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public TreeFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public Tree add() {
        Tree p = new Tree(mind);
        p.setId(++lastID);
        p.setRight(mind.getRights().getRoot());
        p.setNext(root);
        root = p;
        return p;
    }

    public Tree get(long id) {
        for (Tree r = root; r != null; r = r.getNext()) {
            if (r.getId() == id) {
                return r;
            }
        }
        return null;
    }

    public Tree getRoot() {
        return root;
    }

    public void setRoot(Tree o) {
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
            Tree saved = (Tree) pop[0];
            lastID = (long) pop[1];
//            if (root != null && saved != null && root.getId() != saved.getId()) {
//                for (Tree t = root; t != null; t = t.getNext()) {
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
        for (Tree q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Tree r = root; r != null; r = r.getNext()) {
            r.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Tree a = null, b;
        while (count-- > 0) {
            b = new Tree(dis, mind);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
