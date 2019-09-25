package kanger.factory;

import kanger.User;
import kanger.enums.Enums;
import kanger.exception.RuntimeErrorException;
import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;
import kanger.storage.Cache;
import kanger.units.Right;
import kanger.units.Term;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DictionaryFactory {

    public static final String SCHEMA = "dictionary";

    //    private Term root = null;
    private long lastId = 0;
    private long firstId = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    //    private Stack<Object[]> stack = new Stack<>();
    private ICache cache;
    //    private Cache load = new Cache();
    private User user = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) {
//        cache.clear();
//        load.clear();
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            varIndex = base.varIndex;
            cache = new Cache(base.cache);
        } else {
            lastId = 0;
            firstId = 0;
            varIndex = 0;
            cache = new Cache(null);
        }
    }

    public void commit(DictionaryFactory base, Collection<Object> vars) throws Exception {
        List<Term> list = new ArrayList<>();
        for (Object p : base.cache) {
            if (((Identifiable) p).getId() < base.firstId) {
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

    public void update() throws RuntimeErrorException {
        if (!user.isClosed()) {
            //TODO: Коммит в БД
            firstId = lastId;
        }
    }

    public Term add(Object o) throws Exception {
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


    public Term find(Object o) throws Exception {
        Term t = new Term(o, user);
        for (long id : cache.find(t.getHash())) {
            Identifiable one = get(id);
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        return null;
    }

    public Term createCVar(Right r, String name) throws Exception {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setRight(r);
        t.setIndex(i);
        t.setName(add(name));
        return t;
    }

    public Term get(long id) throws Exception {
        Term t = (Term) cache.get(id);
//        t.linkExternal(user);
//        if (t == null) {
//            t = (Term) load.get(id);
//        }
        return t;
    }

//    public Term load(long id) throws RuntimeErrorException {
//        Term t = null;
//        if (!user.isClosed()) {
//            t = (Term) user.getStorage(SCHEMA).get(id);
//            if (t != null) {
//                load.add(t);
//            }
//        }
//        return t;
//    }

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

    public int size() throws Exception {
        return cache.size();
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

    public long getFirstId() {
        return firstId;
    }

//    @Override
//    public Iterator iterator() {
//        return cache.iterator();
////        return new UnitIterator(root);
//    }
}
