package kanger.factory;

import kanger.Mind;
import kanger.primitives.FValue;
import kanger.primitives.Function;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Stack;

public class FValueFactory {
    private FValue root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public FValueFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public FValue add(Function f) {
        FValue t = find(f);
        if (t == null) {
            if (f.isDirtyComplete()) {
                t = new FValue(f, mind);
                t.setNext(root);
                root = t;
                t.setId(lastID++);
            } else {
                return null;
            }
        }
        return t;
    }

    public FValue get(Function f) {
        for (FValue v = root; v != null; v = v.getNext()) {
            if (v.getFunction().getId() == f.getId() && v.isActual(f)) {
                return v;
            }
        }
        return null;
    }


    public FValue find(Function f) {
        for (FValue t = root; t != null; t = t.getNext()) {
            if (f.getId() == t.getFunction().getId()
                    && t.getValue().getId() == f.getArguments().get(f.getRange()).getDirtyValue().getId()
                    && t.isActual(f)) {
//                boolean complete = true;
//                for (TVariable tv : f.getTVariables()) {
//                    if (!tv.isEmpty() && t.getCondition().createCVar(tv.getId()) != tv.getValue().getId()) {
//                        complete = false;
//                        break;
//                    }
//                }
//                if (complete) {
                return t;
//                }
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

    public void reset() {
        while (stack.size() > 1) {
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
        if (!stack.empty()) {
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
    public void rollback() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            FValue saved = (FValue) pop[0];
//            lastID = (long) pop[1];

            FValue t = root;
            FValue q = root;
            while (t != null && t != saved) {
//                if (t.isBlocked()) {
//                    if (t == root) {
//                        root = t = q = t.getNext();
//                    } else {
//                        q.setNext(t.getNext());
//                        t = q.getNext();
//                    }
//                } else {
                    q = t;
                    t = t.getNext();
//                }
            }
        }
        if (stack.isEmpty()) {
            mark();
        }
    }


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
            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        FValue a = null, b;
        while (count-- > 0) {
            b = new FValue(dis, mind);
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
