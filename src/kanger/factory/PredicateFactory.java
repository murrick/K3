package kanger.factory;

import kanger.Mind;
import kanger.primitives.Predicate;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class PredicateFactory {

    private Predicate root = null;
    private long lastID = 0;

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public PredicateFactory(Mind mind) {
        this.mind = mind;
        reset();
    }

    public Predicate add(String line, int range) {
        Predicate p = (Predicate) find(line, range);
        if (p != null) {
            return p;
        } else {
            p = new Predicate(mind);
            p.setId(lastID++);
            p.setNext(root);
            p.setRange(range);
            p.setName(line);
            root = p;
            return p;
        }
    }

    public Object find(String line, int range) {
        for (Predicate p = root; p != null; p = p.getNext()) {
            if (line.equals(p.getName()) && p.getRange() == range) {
                return p;
            }
        }
        return null;
    }

    public Predicate get(long id) {
        for (Predicate p = root; p != null; p = p.getNext()) {
            if (id == p.getId()) {
                return p;
            }
        }
        return null;
    }

    public Predicate getRoot() {
        return root;
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
        if(!stack.empty()) {
            Object[] pop = stack.pop();
            Predicate saved = (Predicate) pop[0];
            lastID = (long) pop[1];
//            if (root != null && saved != null && root.getId() != saved.getId()) {
//                for (Predicate p = root; p != null; p = p.getNext()) {
//                    if (p.getNext() != null && p.getNext().getId() == saved.getId()) {
//                        p.setNext(null);
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
        for (Predicate q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(size());
        for (Predicate p = root; p != null; p = p.getNext()) {
            p.writeCompiledData(dos);
        }
        List<Long[]> links = new ArrayList<>();
        //TODO: Save causes
//        for(Predicate p = root; p != null; p = p.getNext()) {
//            for(Solution s = p.getSolve(); s != null; s = s.getNext()) {
//                for(Solution x : s.getCauses()) {
//                    links.createTVar(new Long[]{s.getPredicate().getId(), s.getId(), x.getPredicate().getId(), x.getId()});
//                }
//            }
//        }
        dos.writeInt(links.size());
        for(Long[] l : links) {
            dos.writeLong(l[0]);
            dos.writeLong(l[1]);
            dos.writeLong(l[2]);
            dos.writeLong(l[3]);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException {
        clear();
        lastID = dis.readLong();
        int count = dis.readInt();
        Predicate a = null, b;
        while (count-- > 0) {
            b = new Predicate(dis, mind);
            if (a == null) {
                root = b;
            } else {
                a.setNext(b);
            }
            a = b;
        }
        //TODO: Load causes
//        count = dis.readInt();
//        while (count-- > 0) {
//            Predicate p = createCVar(dis.readLong());
//            Solution s = p.getSolve(dis.readLong());
//            Predicate xp = createCVar(dis.readLong());
//            Solution xs = xp.getSolve(dis.readLong());
//            s.getCauses().createTVar(xs);
//        }
    }

}
