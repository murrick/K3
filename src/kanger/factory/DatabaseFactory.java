package kanger.factory;

import kanger.Mind;
import kanger.enums.LogMode;
import kanger.primitives.Argument;
import kanger.primitives.Domain;
import kanger.primitives.Predicate;
import kanger.primitives.Right;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class DatabaseFactory {

    private Domain root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public DatabaseFactory(Mind mind) {
        this.mind = mind;
    }

    public void transaction(DatabaseFactory base) {
        root = base.root;
        lastID = base.lastID;
        mark();
    }

    public void commit(DatabaseFactory base) {
        List<Domain> list = new ArrayList();
        for (Domain p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Domain p : list) {
            p.setNext(root);
            root = p;
            p.setId(lastID++);
        }
    }

    public Domain add(Domain d) {
        Domain c = add(d.getPredicate(), d.isAntc(), d.getArguments(), d.getRight());
        c.getParents().add(d);
        return c;
    }


    public Domain add(Predicate pred, boolean antc, List<Argument> arg, Right r) {
        Domain p = find(pred, antc, arg);
        if (p != null) {
            return p;
        } else {
            p = new Domain(mind);
            p.setNext(root);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
            p.setId(lastID++);
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(new Argument(t.getValue()));
                }
            }
            root = p;
            return p;
        }
    }

    public Domain find(Domain d) {
        return find(d.getPredicate(), d.isAntc(), d.getArguments());
    }

    public Domain find(Predicate pred, boolean antc, List<Argument> arg) {
        for (Domain p = root; p != null; p = p.getNext()) {
            if (p.isAntc() == antc && p.getPredicate() == pred && p.getPredicate().getRange() == pred.getRange() && !p.getArguments().isEmpty()) {
                int i = 0;
                for (; i < pred.getRange(); ++i) {
                    if (p.get(i).getValue().getId() != arg.get(i).getValue().getId()) {
                        break;
                    }
                }
                if (i == pred.getRange()) {
                    return p;
                }
                //return p;
            }
        }
        return null;
    }

    public Domain get(long id) {
        for (Domain p = root; p != null; p = p.getNext()) {
            if (p.getId() == id) {
                return p;
            }
        }
        return null;
    }

    public boolean check(boolean logging) {
        boolean result = false;
        for (Domain p = root; p != null; p = p.getNext()) {
            for (Domain q = p.getNext(); q != null; q = q.getNext()) {
                if (p.equalsBase(q) && p.isAntc() != q.isAntc()) {

                    mind.getSolutions().add(p);
                    for (Domain parent : p.getParents()) {
                        for (int i = 0; i < parent.getPredicate().getRange(); ++i) {
                            if (parent.get(i).isTSet()) {
                                if (parent.get(i).getT().contains(p.get(i).getValue())) {
                                    parent.get(i).getT().setValue(p.get(i).getValue());
                                    mind.getValues().add(parent.get(i).getT(), parent);
                                }
                            } else if (parent.get(i).isFSet()) {
                                //TODO: Добавить обработку функций
                            }
                        }
                    }

                    mind.getSolutions().add(q);
                    for (Domain parent : q.getParents()) {
                        for (int i = 0; i < parent.getPredicate().getRange(); ++i) {
                            if (parent.get(i).isTSet()) {
                                if (parent.get(i).getT().contains(q.get(i).getValue())) {
                                    parent.get(i).getT().setValue(q.get(i).getValue());
                                    mind.getValues().add(parent.get(i).getT(), parent);
                                }
                            } else if (parent.get(i).isFSet()) {
                                //TODO: Добавить обработку функций
                            }
                        }
                    }

                    if (logging) {
                        mind.getLog().add(LogMode.ANALIZER, "Database coincidence : ");
                        mind.getLog().add(LogMode.ANALIZER, "\t" + p.toString());
                        mind.getLog().add(LogMode.ANALIZER, "\t" + q.toString());
                        mind.getLog().add(LogMode.ANALIZER, "===========================================");
                    }
                    result = true;
                }
            }
        }
        return result;
    }

    public Domain getRoot() {
        return root;
    }

    public void setRoot(Domain o) {
        root = o;
    }

    public void clear() {
        do{
            release();
        } while(stack.size() > 1);
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
            Domain saved = (Domain) pop[0];
            lastID = (long) pop[1];
            root = saved;
        }
        if (stack.empty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Domain q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Domain d = root; d != null; d = d.getNext()) {
            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Domain a = null, b;
        while (count-- > 0) {
            b = new Domain(dis, mind);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
    }

}
