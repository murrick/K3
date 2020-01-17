package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Right;
import org.kanger.units.Term;

import java.io.IOException;
import java.util.Iterator;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DictionaryFactory implements Iterable<Term> {

    public static final String SCHEMA = "dictionary";

    //    private Term root = null;
//    private long lastId = 0;
//    private long firstId = 0;
    private int varIndex = 0;           // Счетчик C-переменных

    //    private Stack<Object[]> stack = new Stack<>();
    private ICache cache;
    private IStep top = null;
    //    private Cache load = new Cache();
    private Mind mind = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) {
//        cache.clear();
//        load.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            varIndex = base.varIndex;
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
                for (Term t : this) {
                    if (t.isCVariable()) {
                        varIndex = t.getIndex();
                        break;
                    }
                }
            } else {
//                lastId = 0;
//                firstId = 0;
                varIndex = 0;           // Счетчик C-переменных
            }
        }
//        firstId = user.lastId(SCHEMA);
    }

    public void commit(DictionaryFactory base) throws IOException, OutOfBufferException, RuntimeErrorException, ClassNotFoundException {
        if (base.top != null) {
            if (cache.getRoot() == null) {
                top = base.top;
            } else {
                base.top.setNext(cache.getRoot());
            }
        }
        if (cache.getRoot() != null) {
            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    ((IUnit) s.getData()).setMind(mind);
                } else {
                    break;
                }
            }
        }
        cache.setRoot(base.cache.getRoot());
        varIndex = Math.max(base.varIndex, varIndex);

    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = user.lastId(SCHEMA);
        }
    }

    public Term add(Object o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        synchronized (mind.getUser()) {
            Term p = find(o);
            if (p != null) {
                return p;
            } else {
                if (p instanceof Term) {
                    p.setMind(mind);
                } else {
                    p = new Term(o, mind);
                    p.setId(mind.getUser().nextId(SCHEMA));
                    p.setMindId(mind.getId());
                }
                cache.add(p);
                if (top == null) {
                    top = cache.getRoot();
                }
                return p;
            }
        }
    }


    public Term find(Object o) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Term t;
        if (o instanceof Term) {
            t = (Term) o;
        } else {
            t = new Term(o, mind);
        }
        for (long id : cache.find(t.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(t)) {
                return (Term) one;
            }
        }
        return null;
    }

    public Term createCVar(Right r, String name) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setRight(r);
        t.setIndex(i);
        t.setName(add(name));
        return t;
    }

    public Term load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Term t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Term) s.getData(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Term get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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


    public void clear() throws IOException, OutOfBufferException {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getTerms());
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

//    public long getFirstId() {
//        return firstId;
//    }

    @Override
    public Iterator iterator() {
        return cache.iterator(-1);
    }

    public void pack() throws IOException {
        update();
    }
}
