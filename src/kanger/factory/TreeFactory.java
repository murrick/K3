package kanger.factory;

import kanger.User;
import kanger.units.Tree;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class TreeFactory {

    private Tree root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public TreeFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TreeFactory base) {
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

    public void commit(TreeFactory base) {
        List<Tree> list = new ArrayList();
        for (Tree p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Tree p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public Tree add() {
        Tree p = new Tree(user);
        p.setId(++lastID);
        p.setRight(user.getMind().getRights().getRoot());
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

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTrees());
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
            Tree saved = (Tree) pop[0];
            lastID = (long) pop[1];
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
//            r.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Tree a = null, b = null;
        while (count-- > 0) {
//            b = new Tree(user).readCompiledData(dis);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
