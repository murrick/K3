package kanger.factory;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Right;
import kanger.units.Term;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by murray on 25.05.15.
 */
public class DictionaryFactory {

    public static final String SCHEMA = "dictionary";

    //    private Term root = null;
    private long lastId = 0;
    private long firstId = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    //    private Stack<Object[]> stack = new Stack<>();
    private Cache cache = new Cache();
    private Cache load = new Cache();
    private User user = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) {
        cache.clear();
        load.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.firstId;
            varIndex = base.varIndex;
            cache.add(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            varIndex = 0;
        }
    }

    public void commit(DictionaryFactory base, Collection<Object> vars) {
        List<Term> list = new ArrayList<>();
        for (Identifiable p : base.cache) {
            if (p.getId() < base.firstId) {
                break;
            }
            list.add((Term) p);
        }
        for (Term p : list) {
            p.setId(lastId++);
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
                    if (p.getId() < firstId) {
                        break;
                    }
                    user.getStorage(SCHEMA).add(p);
                }
                cache.clear();
                firstId = lastId;
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
            p.setId(lastId++);
            cache.add(p);
            return p;
        }
    }


    public Term find(Object o) {
        Term t = new Term(o, user);
        for (Identifiable one : cache.find(t.getHash())) {
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        if (!user.isClosed()) {
            for (Identifiable one : user.getStorage(SCHEMA).find(t.getHash())) {
                one.linkExternal(user);
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
        if (t == null) {
            t = (Term) load.get(id);
            if (t == null) {
                try {
                    t = (Term) user.getStorage(SCHEMA).get(id);
                    if (t != null) {
                        t.linkExternal(user);
                        load.add(t);
                    }
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return t;
    }

//    public Term getRoot() {
//        return root;
//    }

//    public void setRoot(Term o) {
//        root = o;
//    }

//    private void mark() {
//        stack.push(new Object[]{root, lastId, varIndex});
//    }
//
//    private void release() {
//        if (!stack.empty()) {
//            Object[] pop = stack.pop();
//            Term saved = (Term) pop[0];
//            lastId = (long) pop[1];
//            varIndex = (int) pop[2];
//            root = saved;
//        }
//        if (stack.empty()) {
//            mark();
//        }
//    }

    public int size() {
        return cache.size() + (user.isClosed() ? 0 : user.getStorage(SCHEMA).size());
    }


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

//    @Override
//    public Iterator iterator() {
//        return cache.iterator();
////        return new UnitIterator(root);
//    }
}
