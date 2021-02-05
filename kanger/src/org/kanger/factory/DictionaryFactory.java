package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.enums.Enums;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Rule;
import org.kanger.units.Term;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    private IBase connection = null;

//    private Map<Integer, Set<Long>> hashCache = new HashMap<>();
//    private Map<Long, Term> idCache = new HashMap<>();
//    private DictionaryFactory base = null;


    public DictionaryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DictionaryFactory base) throws Exception {
//        cache.clear();
//        load.clear();
        if (mind.getNext() == null && !mind.getUser().isClosed()) {
//            if (mind.getNext() == null) {
            connection = mind.getUser().getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            varIndex = base.varIndex;
            cache = new Escalera(mind, SCHEMA, base.cache);

//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

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

    public void commit(DictionaryFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
//        if (cache.getRoot() != null) {
//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
//                    ((IUnit) s.getData()).setMind(mind);
//                    ((IUnit) s.getData()).setMindId(mind.getId());
//                } else {
//                    break;
//                }
//            }
//        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
        }

//        pack();
//        update();
        varIndex = Math.max(base.varIndex, varIndex);

    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = user.lastId(SCHEMA);
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized Term add(Object o) throws Exception {
        Term p = find(o);
        if (p != null) {
            p.setDeleted(false, mind);
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


    public Term find(Object o) throws Exception {
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

    public Term createCVar(Rule r, Term name) throws Exception {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.CVC, i);
        Term t = add(temp);
        t.setRule(r);
        t.setIndex(i);
        t.setName(name);
        return t;
    }

    public Term createXVar(Term c) throws Exception {
        Term t = null;
//        for(Term x : this) {
//            if(x.getParent().getId() == c.getId()) {
//                t = x;
//                break;
//            }
//        }
//        if(t == null) {
        int i = nextVarIndex();
        String temp = String.format("%c%d", Enums.XVC, i);
        t = add(temp);
        t.setRule(c.getRule());
        t.setIndex(i);
        t.setName(c.getName());
//            c.getChilds().add(t.getId());
        t.setParent(c);
//        }
        return t;
    }

    public Term load(long id) throws Exception {
        Term t = get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Term) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private Term get(long id) throws Exception {
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


    public void clear() throws Exception {
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

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public Term getRoot() throws Exception {
        IStep one = cache.getRoot();
        if (one != null) {
            return (Term) cache.getRoot().getData(mind);
        } else {
            return null;
        }
    }
}
