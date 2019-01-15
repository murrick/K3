package kanger.factory;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.primitives.UnitIterator;
import kanger.storage.Cache;
import kanger.storage.Storage;
import kanger.units.Right;
import kanger.units.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by murray on 25.05.15.
 */
public class DictionaryFactory implements Iterable<Term> {

    private static final String SCHEMA = "dictionary";

    //    private Term root = null;
    private long lastID = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    //    private Stack<Object[]> stack = new Stack<>();
    private Cache cache = new Cache();
    private User user = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) {
        if (base != null) {
//            root = base.root;
            lastID = base.lastID;
            varIndex = base.varIndex;
            cache.add(base.cache);
        } else {
//            root = null;
            lastID = 0;
            varIndex = 0;
            cache.clear();
//            if(!user.isClosed()) {
//                try {
//                    user.getStorage().clear();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            }
        }
//        this.base = base;
//        stack.clear();
//        mark();
    }

    public void commit(DictionaryFactory base, Collection<Object> vars) {
        List<Term> list = new ArrayList<>();
        for (Identifiable p : base.cache) {
            if (cache.getLast() != null && p.getId() <= cache.getLast().getId()) {
                break;
            }
            list.add((Term) p);
        }
        for (Term p : list) {
            p.setId(lastID++);
            cache.add(p);
            if (p.isCVariable()) {
                vars.add(p);
            }
        }
    }

    public void update() {
        if (!user.isClosed()) {
            try {
                for (Identifiable p : cache) {
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public Term add(Object o) {
        Term p = find(o);
        if (p != null) {
            return p;
        } else {
            p = new Term(o, user);
            p.setId(lastID++);
            cache.add(p);
            return p;
        }
    }

//    private void append(Term term) {
////        term.setNext(root);
////        root = term;
//        term.setId(lastID++);
//        cache.add(term);
//    }

    public Term find(Object o) {
        Term t = new Term(o, user);
        for (Identifiable one : cache.find(t.getHash())) {
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(t.getHash())) {
                if (one.equalsTo(t)) {
                    return (Term) one;
                }
            }
        }
//        for (Term dic = root; dic != null; dic = dic.getNext()) {
//            if (dic.equalsTo(t)) {
//                return dic;
//            }
//        }
        return null;
    }

    public Term createCVar(Right r, String name) {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setRight(r);
        t.setIndex(i);
        t.setName(add(name));
        return t;
    }

    public Term get(long id) {
        Term t = (Term) cache.get(id);
        if(t == null) {
            try {
                t = (Term) user.getStorage(SCHEMA).get(id);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return t;
//        for (Term dic = root; dic != null; dic = dic.getNext()) {
//            if (id == dic.getId()) {
//                return dic;
//            }
//        }
//        return null;
    }

//    public Term getRoot() {
//        return root;
//    }

//    public void setRoot(Term o) {
//        root = o;
//    }

//    private void mark() {
//        stack.push(new Object[]{root, lastID, varIndex});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Term saved = (Term) pop[0];
//            lastID = (long) pop[1];
//            varIndex = (int) pop[2];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
//        int cnt = 0;
//        for (Term q = root; q != null; q = q.getNext()) {
//            ++cnt;
//        }
//        return cnt;
    }

//    public void writeCompiledData(DataOutputStream dos) throws IOException {
//        dos.writeLong(lastID);
//        dos.writeInt(varIndex);
//        int count = size();
//        dos.writeInt(count);
////        for (Term d = root; d != null; d = d.getNext()) {
////            d.writeCompiledData(dos);
////        }
//    }
//
//    public void readCompiledData(DataInputStream dis) throws IOException, ClassNotFoundException {
//        clear();
//        lastID = dis.readLong();
//        varIndex = dis.readInt();
//        int count = dis.readInt();
//        Term a = null, b = null;
////        while (count-- > 0) {
////            b = new Term(dis, user);
////            if (a != null) {
////                a.setNext(b);
////            } else {
////                root = b;
////            }
////            a = b;
////        }
//    }

    public void clear() {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTerms());
        } else {
            transaction(null);
        }
    }

    public int nextVarIndex() {
        return ++varIndex;
    }

    @Override
    public Iterator<Term> iterator() {
        Storage storage = user.isClosed() ? null : user.getStorage(SCHEMA);
        return new UnitIterator(cache.iterator(), storage);
    }
}
