package kanger.factory;

import kanger.Mind;
import kanger.enums.Enums;
import kanger.primitives.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

/**
 * Created by murray on 25.05.15.
 */
public class DictionaryFactory {

    private Term root = null;
    private long lastID = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    private Stack<Object[]> stack = new Stack<>();

    private Mind mind = null;

    public DictionaryFactory(Mind mind) {
        this.mind = mind;
    }

    public void transaction(DictionaryFactory base) {
        root = base.root;
        lastID = base.lastID;
        varIndex = base.varIndex;
        mark();
    }

    public void commit(DictionaryFactory base, Collection vars) {
        List<Term> list = new ArrayList();
        for (Term p = base.root; p != null && (root == null || p.getId() != root.getId()); p = p.getNext()) {
            list.add(0, p);
        }
        for (Term p : list) {
            p.setMind(mind);
            p.setNext(root);
            root = p;
            p.setId(lastID++);
            if (p.isCVariable()) {
                vars.add(p);
            }
        }
    }

    public Term add(Object o) {
        Term p = find(o);
        if (p != null) {
            return p;
        } else {
            p = new Term(o, mind);
            p.setNext(root);
            root = p;
            p.setRight(mind.getRights().getRoot());
            p.setId(lastID++);
            return p;
        }
    }

    public Term find(Object o) {
        Term t = new Term(o, mind);
        for (Term dic = root; dic != null; dic = dic.getNext()) {
            if (dic.compareTo(t) == 0) {
                return dic;
            }
        }
        return null;
    }

    public Term createCVar(String name) {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setIndex(i);
        t.setName(name);
        return t;
    }

    public Term get(long id) {
        for (Term dic = root; dic != null; dic = dic.getNext()) {
            if (id == dic.getId()) {
                return dic;
            }
        }
        return null;
    }

    public Term getRoot() {
        return root;
    }

    public void setRoot(Term o) {
        root = o;
    }

    private void mark() {
        stack.push(new Object[]{root, lastID, varIndex});
    }

    private void release() {
        if (!stack.empty()) {
            Object[] pop = stack.pop();
            Term saved = (Term) pop[0];
            lastID = (long) pop[1];
            varIndex = (int) pop[2];
            root = saved;
        }
        if (stack.empty()) {
            mark();
        }
    }

    public int size() {
        int cnt = 0;
        for (Term q = root; q != null; q = q.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    public void writeCompiledData(DataOutputStream dos) throws IOException {
        dos.writeLong(lastID);
        dos.writeInt(varIndex);
        int count = size();
        dos.writeInt(count);
        for (Term d = root; d != null; d = d.getNext()) {
            d.writeCompiledData(dos);
        }
    }

    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
        clear();
        lastID = dis.readLong();
        varIndex = dis.readInt();
        int count = dis.readInt();
        Term a = null, b;
        while (count-- > 0) {
            b = new Term(dis, mind);
            if (a != null) {
                a.setNext(b);
            } else {
                root = b;
            }
            a = b;
        }
    }

    public void clear() {
        while (stack.size() > 1) {
            release();
        }
        ;
    }

    public int nextVarIndex() {
        return ++varIndex;
    }

}
