package kanger.factory;

import kanger.User;
import kanger.primitives.TValue;
import kanger.primitives.TVariable;
import kanger.primitives.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class TValueFactory {

    private TValue root = null;
    private Map<TVariable, Long> current = new HashMap<>();
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public TValueFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(TValueFactory base) {
        if (base != null) {
            root = base.root;
            lastID = base.lastID;
        } else {
            root = null;
            lastID = 0;
        }
        current.clear();
        stack.clear();
        mark();
    }

    public void commit(TValueFactory base) {
        List<TValue> list = new ArrayList();
        for (TValue p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (TValue p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
            //TODO: Добавить commitID
        }
    }

    public TValue add(TVariable tv, Term o) {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, user);
            t.setTVar(tv);
            t.setNext(root);
            root = t;
            t.setId(lastID++);
//            t.setTag(-1);
        }

        //TODO: Фиксация текцщего значения подстановки. Правильно ли это?
//        if (isEmpty(tv)) {
//            current.put(tv, t.getId());
//        }

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


    // Мотаем в обратную сторону
    public TValue rewind(TVariable tv) {
        if (root == null) {
            return null;
        }
        TValue n = null;
        for (TValue x = root; x != null; x = x.getNext()) {
            if (x.getTVar().getId() == tv.getId()) {
                n = x;
            }
        }
        return n;
    }

    public boolean isMarked(TValue v) {
        if (stack.isEmpty() || stack.peek()[0] == null) {
            return false;
        } else if (v.getId() == ((TValue) stack.peek()[0]).getId()) {
            return true;
        }
        for (TValue x = root; x != null; x = x.getNext()) {
            if (x.getId() == ((TValue) stack.peek()[0]).getId()) {
                return true;
            } else if (v.getId() == x.getId()) {
                return false;
            }
        }
        return false;
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
    public TValue next(TValue v) {
        if (root == null) {
            return null;
        }
        TValue n = null;
        for (TValue x = root; x != null; x = x.getNext()) {
            if (x.getTVar().getId() == v.getTVar().getId()) {
                if (x.getId() != v.getId()) {
                    n = x;
                } else {
                    break;
                }
            }

        }
        return n;
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

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTValues());
        } else {
            transaction(null);
        }
    }

    public void mark() {
        stack.push(new Object[]{root, lastID});
    }


    public void commit() {
        if (stack.size() > 1) {
//            ++commitID;
            Object[] curr = stack.pop();
//            for(TValue v = root; v != null && (curr[0] == null || v.getId() != ((TValue)curr[0]).getId()); v = v.getNext()) {
//                v.setCommitId(commitID);
//            }
        }
    }

    public void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            TValue saved = (TValue) pop[0];
            lastID = (long) pop[1];
            root = saved;
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

    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v.getId());
        }
        return v;
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
            b = new TValue(dis, user);
            if (a != null) {
                a.setNext(b);
            } else {
                root = b;
            }
            a = b;
        }
    }

//    public long getCommitID() {
//        return commitID;
//    }
//
//    public void setCommitID(long commitID) {
//        this.commitID = commitID;
//    }
//
//    public void incCommitId() {
//        ++this.commitID;
//    }
}
