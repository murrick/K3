package kanger.factory;

import kanger.User;
import kanger.units.FValue;
import kanger.units.Function;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class FValueFactory {
    private FValue root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public FValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(FValueFactory base) {
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

    public void commit(FValueFactory base) {
        List<FValue> list = new ArrayList();
        for (FValue p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (FValue p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public FValue add(Function f) {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, user);
                t.setNext(root);
                root = t;
                t.setId(lastID++);
            } else {
                return null;
            }
        }
        return t;
    }

//    public FValue get(Function f) {
//        for (FValue v = root; v != null; v = v.getNext()) {
//            if (v.getFunc().getId() == f.getId() && v.isActual(f)) {
//                return v;
//            }
//        }
//        return null;
//    }
//

    public FValue find(Function f) {
        for (FValue t = root; t != null; t = t.getNext()) {
            if (f.getId() == t.getFunc().getId()
                    && (f.getArguments().get(f.getRange()).isEmpty()
                    || t.getValue().getId() == f.getArguments().get(f.getRange()).getValue().getId())) {
                boolean complete = true;
                for (int i = 0; i < f.getRange(); ++i) {
                    if (!f.getArguments().get(i).isEmpty() && f.getArguments().get(i).getValue().getId() != t.getCondition().get(i).getValue().getId()) {
                        complete = false;
                        break;
                    }
                }
                if (complete) {
                    return t;
                }
            }
        }
        return null;
    }

    public FValue get(long id) {
        for (FValue t = root; t != null; t = t.getNext()) {
            if (id == t.getId()) {
                return t;
            }
        }
        return null;
    }

    public FValue getRoot() {
        return root;
    }

    public void setRoot(FValue o) {
        root = o;
    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getFValues());
        } else {
            transaction(null);
        }
    }

    public void mark() {
        stack.push(new Object[]{root, lastID});
    }


    public void commit() {
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    public void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            FValue saved = (FValue) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if (stack.isEmpty()) {
            mark();
        }
    }

    //    public void commit() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            FValue saved = (FValue) pop[0];
//            lastID = (long) pop[1];
//
//            FValue t = root;
//            FValue q = root;
//            while (t != null && t != saved) {
//                if (!t.isClosed()) {
//                    if (t == root) {
//                        root = t = q = t.getNext();
//                    } else {
//                        q.setNext(t.getNext());
//                        t = q.getNext();
//                    }
//                } else {
//                    q = t;
//                    t = t.getNext();
//                }
//            }
//        }
//        if (stack.isEmpty()) {
//            mark();
//        }
//    }
//
//    public void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            FValue saved = (FValue) pop[0];
////            lastID = (long) pop[1];
//
//            FValue t = root;
//            FValue q = root;
//            while (t != null && t != saved) {
////                if (t.isBlocked()) {
////                    if (t == root) {
////                        root = t = q = t.getNext();
////                    } else {
////                        q.setNext(t.getNext());
////                        t = q.getNext();
////                    }
////                } else {
//                    q = t;
//                    t = t.getNext();
////                }
//            }
//        }
//        if (stack.isEmpty()) {
//            mark();
//        }
//    }


    public int size() {
        int cnt = 0;
        for (FValue q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        int count = size();
        dos.writeInt(count);
        for (FValue d = root; d != null; d = d.getNext()) {
//            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        FValue a = null, b = null;
        while (count-- > 0) {
//            b = new FValue(user).readCompiledData(dis);
            if (a != null) {
                a.setNext(b);
            } else {
                root = b;
            }
            a = b;
        }
    }

    public FValue getMark() {
        if (!stack.empty()) {
            Object[] pop = stack.peek();
            return (FValue) pop[0];
        } else {
            return null;
        }
    }


}
