package kanger.factory;

import kanger.Mind;
import kanger.primitives.TValue;
import kanger.primitives.TVariable;
import kanger.primitives.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class TValueFactory {

    private TValue root = null;
    private Map<TVariable, Long> current = new HashMap<>();
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public TValueFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public TValue add(TVariable tv, Term o) {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setNext(root);
            root = t;
            t.setId(lastID++);
        }
//        current.put(tv, t.getId());
        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        TValue v = get(current.get(tv));
        return v;
    }

    public boolean isEmpty(TVariable tv) {
        return root == null || !current.containsKey(tv);
    }


    public TValue rewind(TVariable tv) {
        if (root == null) {
            return null;
        }
        if (root.getTVar().getId() == tv.getId() && !root.isBlocked()) {
            return root;
        } else {
            TValue v = next(tv, root);
            if (v == null) {
                return null;
            } else {
                return v;
            }
        }
    }

    //    public TValue rewindTop(TVariable tv) {
//        if (root == null || root == getMark()) {
//            return null;
//        }
//        if (root.getTVar().getId() == tv.getId() && !root.isBlocked()) {
//            return root;
//        } else {
//            TValue v = next(tv, root);
//            if (v == null) {
//                return null;
//            } else {
//                return v;
//            }
//        }
//    }
//
    public TValue next(TVariable tv, TValue v) {
        if (root == null) {
            return null;
        }
        for (v = v.getNext(); v != null; v = v.getNext()) {
            if (v.getTVar().getId() == tv.getId() && !v.isBlocked()) {
                return v;
            }
        }
        return null;
    }

    //    public TValue nextTop(TVariable tv, TValue v) {
//        if (root == null || root == getMark()) {
//            return null;
//        }
//        for (v = v.getNext(); v != null && v != getMark(); v = v.getNext()) {
//            if (v.getTVar().getId() == tv.getId()) {
//                return v;
//            }
//        }
//        return null;
//    }
//
    public TValue find(TVariable tv, Term v) {
        for (TValue t = root; t != null; t = t.getNext()) {
            if (tv.getId() == t.getTVar().getId() && t.getValue().getId() == v.getId()) {
                return t;
            }
        }
        return null;
    }

    public TValue get(long id) {
        for (TValue t = root; t != null; t = t.getNext()) {
            if (id == t.getId()) {
                return t;
            }
        }
        return null;
    }

    public TValue getRoot() {
        return root;
    }

    public void setRoot(TValue o) {
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
        if(!stack.empty()) {
            stack.pop();
        }
    }

    public void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            TValue saved = (TValue) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if(stack.isEmpty()) {
            mark();
        }
    }

//    public void commit() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            TValue saved = (TValue) pop[0];
//            lastID = (long) pop[1];
//
//            TValue t = root;
//            TValue q = root;
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

    public void rollback() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            TValue saved = (TValue) pop[0];
//            lastID = (long) pop[1];

            TValue t = root;
            TValue q = root;
            while (t != null && t != saved) {
                if (t.isBlocked()) {
                    if (t == root) {
                        root = t = q = t.getNext();
                    } else {
                        q.setNext(t.getNext());
                        t = q.getNext();
                    }
                } else {
                    q = t;
                    t = t.getNext();
                }
            }
        }
        if (stack.isEmpty()) {
            mark();
        }
    }

    public TValue getMark() {
        if (!stack.empty()) {
            Object[] pop = stack.peek();
            return (TValue) pop[0];
        } else {
            return null;
        }
    }

    public void set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v.getId());
        }
    }

    public void remove(TVariable tv) {
        TValue t = get(tv);
        if (t != null) {
            if (t.getId() == root.getId()) {
                root = t.getNext();
                t.setNext(null);
            } else {
                for (TValue x = root; x != null; x = x.getNext()) {
                    if (x.getNext() != null && x.getNext().getId() == t.getId()) {
                        x.setNext(t.getNext());
                        t.setNext(null);
                    }
                }
            }
//            rewind(tv);
        }
    }

    public int size() {
        int cnt = 0;
        for (TValue q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        int count = size();
        dos.writeInt(count);
        for (TValue d = root; d != null; d = d.getNext()) {
            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        TValue a = null, b;
        while (count-- > 0) {
            b = new TValue(dis, mind);
            if (a != null) {
                a.setNext(b);
            } else {
                root = b;
            }
            a = b;
        }
    }

}
