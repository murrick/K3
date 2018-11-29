package kanger.factory;

import kanger.User;
import kanger.primitives.*;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class DatabaseFactory {

    private Record root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private User user = null;

    public DatabaseFactory(User user) {
        this.user = user;
    }

    public void transaction(DatabaseFactory base) {
        root = base.root;
        lastID = base.lastID;
        mark();
    }

    public void commit(DatabaseFactory base) {
        List<Record> list = new ArrayList();
        for (Record p = base.root; p != null && (root == null || p.getDomain().getId() != root.getDomain().getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Record p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public Record add(Domain d) {
        Record p = find(d.getPredicate(), d.isAntc(), d.getArguments());
        if (p != null) {
            return p;
        } else {
            Record r = new Record(d);
            r.setNext(root);
            root = r;
            r.setId(lastID++);
            return r;
        }
    }


    public Record add(Predicate pred, boolean antc, boolean isQuery, List<Argument> arg) {
        Record p = find(pred, antc, arg);
        if (p != null) {
            return p;
        } else {
            List<Argument> list = null;
            if (arg != null) {
                list = new ArrayList<>();
                for (Argument t : arg) {
                    if (t.isTSet()) {
                        TValue v = t.getT().getCurrent();
                        if (isQuery) {
                            v.setQuery();
                        }
                        list.add(new Argument(v));

                    } else if (t.isFSet()) {
                        list.add(new Argument(t.getF().getCurrent()));
                    } else {
                        list.add(new Argument(t.getValue()));
                    }
                }
            }
            Right r = user.getMind().getRights().add();
            Tree t = user.getMind().getTrees().add();
            t.setRight(r);
            t.setGenerated();
            t.setUsed();
            Domain d = user.getMind().getDomains().add(pred, antc, list, r);
            d.setRight(r);
            t.getSequence().add(d);
            r.getTree().add(t);
            r.setGenerated(true);

            int save = user.getMind().getDebugLevel();
            user.getMind().setDebugLevel(0);
            String origin = d.toString();
            user.getMind().setDebugLevel(save);
            r.setOrig(origin);

            return add(d);
        }
    }

    public Record find(Domain d) {
        return find(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public Record find(Predicate pred, boolean antc, List<Argument> arg) {
        for (Record p = root; p != null; p = p.getNext()) {
            if (p.getDomain().isAntc() == antc
                    && p.getDomain().getPredicate() == pred
                    && p.getDomain().getPredicate().getRange() == pred.getRange()
                /*&& !p.getArguments().isEmpty()*/) {
                int i = 0;
                for (; i < pred.getRange(); ++i) {
                    if (!p.getDomain().get(i).isEmpty() && !arg.get(i).isEmpty() && p.getDomain().get(i).getValue().getId() != arg.get(i).getValue().getId()) {
                        break;
                    }
                }
                if (i == pred.getRange()) {
                    return p;
                }
            }
        }
        return null;
    }

    public Record get(long id) {
        for (Record p = root; p != null; p = p.getNext()) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }


    public Record getRoot() {
        return root;
    }

    public void setRoot(Record o) {
        root = o;
    }

    public void clear() {
        while (stack.size() > 1) {
            release();
        }
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
            Record saved = (Record) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if (stack.empty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Record q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeInt(size());
        for (Record d = root; d != null; d = d.getNext()) {
            dos.writeLong(d.getDomain().getId());
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        int count = dis.readInt();
        Record a = null, b;
        while (count-- > 0) {
            b = new Record(user.getMind().getDomains().get(dis.readLong()));
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

    public class Record {
        private Domain domain = null;
        private long id = -1;
        private Record next = null;

        public Record(Domain domain) {
            this.domain = domain;
        }

        public Domain getDomain() {
            return domain;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public Record getNext() {
            return next;
        }

        public void setNext(Record next) {
            this.next = next;
        }
    }

    public Set<TVariable> getTVariables(boolean full) {
        Set<TVariable> set = new HashSet<>();
        for (Record d = root; d != null; d = d.getNext()) {
            set.addAll(d.getDomain().getTVariables(full));
        }
        return set;
    }
}
