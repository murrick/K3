package kanger.factory;

import kanger.User;
import kanger.enums.Enums;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.interfaces.Identifiable;
import kanger.storage.Escalera;
import kanger.units.Right;
import kanger.units.Term;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DictionaryFactory implements Iterable<Term> {

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
            cache = new Escalera(user, SCHEMA, base.cache);
        } else {
            cache = new Escalera(user, SCHEMA, null);
            if (!cache.isEmpty()) {
                lastId = cache.getRoot().getId() + 1;
                firstId = lastId;
                for (Term t : this) {
                    if (t.isCVariable()) {
                        varIndex = t.getIndex();
                        break;
                    }
                }
            } else {
                lastId = 0;
                firstId = 0;
                varIndex = 0;           // Счетчик C-переменных
            }
        }
    }

    public void commit(DictionaryFactory base, Collection<Object> vars) throws Exception {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;

            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }

            for (Object p : cache) {
                if (((Term) p).getId() >= base.firstId && ((Term) p).isCVariable()) {
                    vars.add(p);
                } else {
                    break;
                }
            }
        }

//        List<Term> list = new ArrayList<>();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add((Term) p);
//        }
//        for (Term p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//            if (p.isCVariable()) {
//                vars.add(p);
//            }
//        }

    }

    public void unlink() throws Exception {
        cache.unlink();
    }

    public void update() throws Exception {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public Term add(Object o) throws IOException, ClassNotFoundException {
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


    public Term find(Object o) throws IOException, ClassNotFoundException {
        Term t = new Term(o, user);
        for (long id : cache.find(t.getHash())) {
            Identifiable one = load(id);
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        return null;
    }

    public Term createCVar(Right r, String name) throws IOException, ClassNotFoundException {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setRight(r);
        t.setIndex(i);
        t.setName(add(name));
        return t;
    }

    public Term load(long id) throws IOException, ClassNotFoundException {
        Term t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Term) s.getData();
                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Term get(long id) throws IOException, ClassNotFoundException {
        Term t = (Term) cache.get(id);
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


    public void clear() throws IOException, ClassNotFoundException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getTerms());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public int nextVarIndex() {
        return ++varIndex;
    }

    public int getVarIndex() {
        return varIndex;
    }

    public long getFirstId() {
        return firstId;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator(true, -1);
    }
}
