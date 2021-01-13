package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.storage.Escalera;
import org.kanger.units.*;

import java.util.*;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class DomainFactory implements Iterable<Domain> {

    public static final String SCHEMA = "domains";

//    private long lastId = 0;
//    private long firstId = 0;

    private Set<Domain> waiters = new HashSet<>();

    private ICache cache;
    private IStep top = null;
    //    private Cache load = new Cache();
    private Mind mind = null;
    private IBase connection = null;

    public DomainFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DomainFactory base) throws Exception {
        if (!mind.getUser().isClosed()) {
//            if(mind.getNext() == null) {
            connection = mind.getUser().getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        waiters.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            waiters.addAll(base.waiters);
            cache = new Escalera(mind, SCHEMA, base.cache);

//            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
//                ((IUnit) s.getData()).setMind(mind);
//            }

        } else {
            cache = new Escalera(mind, SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }

    public void commit(DomainFactory base) throws Exception {
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    ((IUnit) s.getData()).setMind(mind);
                    ((IUnit) s.getData()).setMindId(mind.getId());
                } else {
                    break;
                }
            }
        }
//        pack();
//        update();
        waiters.addAll(base.waiters);
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

//    public Domain add(Domain d) throws IOException, ClassNotFoundException {
//        cache.add(d);
//        return d;
//    }

//    public Domain add(Right r) throws Exception {
//        Domain p = new Domain(user);
//        p.setRight(r);
//        p.setId(lastId++);
//        cache.add(p);
//        return p;
//    }


    public synchronized Domain add(Predicate pred, boolean antc, ArgList arg, Rule r) throws Exception {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            return p;
        } else {
            p = new Domain(mind);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRule(r);
            p.setId(mind.getUser().nextId(SCHEMA));
            p.setMindId(mind.getId());
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
            return add(p);
        }
    }

    public synchronized Domain add(Domain p) throws Exception {
        cache.add(p);
        if (top == null) {
            top = cache.getRoot();
        }
        return p;
    }

    public Domain find(Predicate pred, boolean antc, ArgList arg, Rule r) throws Exception {
        Domain temp = new Domain(pred, antc, arg, r);
        return find(temp);
//        temp.setUser(user);
    }

    public Domain find(Domain d) throws Exception {
        for (long id : cache.find(d.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(d)) {
                return (Domain) one;
            }
        }
        return null;
    }

    public Domain load(long id) throws Exception {
        Domain t = get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Domain) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    private Domain get(long id) throws Exception {
        Domain t = (Domain) cache.get(id);
        return t;
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            waiters.remove(o);
            cache.delete(((IUnit) o).getId());
        }
//        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
//            firstId = lastId;
//        } else {
//            lastId = 0;
//            firstId = 0;
//        }

    }

    public void delete(Domain d) throws Exception {
        d.setDeleted();
        for (TVariable t : d.getArguments().getTVariables(mind)) {
            mind.getTVars().delete(t);
        }
        for (Function f : d.getArguments().getFunctions(mind)) {
            mind.getFunctions().delete(f);
        }
        for (TValue v : d.getArguments().getTValues(mind, true)) {
            if (v.getMindId() == mind.getId()) {
                mind.getTValues().delete(v);
            }
        }

//            for (TValue v : mind.getTValues()) {
//                Set<Cause> toDelete = new HashSet<>();
//                for (Cause c : v.getCauses()) {
//                    if (c.getSrcId() == d.getId() || c.getDstId() == d.getId()) {
//                        toDelete.add(c);
//                    }
//                }
//                if (!toDelete.isEmpty()) {
//                    v.getCauses().removeAll(toDelete);
//                }
//            }
//            mind.getTValues().update();
//            waiters.remove(d);
//            cache.delete(id);
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Domain d = get(id);
//        if (d != null) {
//            for (TVariable t : d.getArguments().getTVariables(true)) {
//                mind.getTVars().delete(t.getId());
//            }
//            for (TValue v : mind.getTValues()) {
//                Set<Cause> toDelete = new HashSet<>();
//                for (Cause c : v.getCauses()) {
//                    if (c.getSrcId() == d.getId() || c.getDstId() == d.getId()) {
//                        toDelete.add(c);
//                    }
//                }
//                if (!toDelete.isEmpty()) {
//                    v.getCauses().removeAll(toDelete);
//                }
//            }
//            mind.getTValues().update();
//            waiters.remove(d);
//            cache.delete(id);
//        }
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getDomains());
        } else {
            cache.clear();
            transaction(null);
        }
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public void mark() throws Exception {
        cache.mark();
    }


    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

    public int size() throws Exception {
        return cache.size();
    }

    public Set<Domain> getWaiters() {
        return waiters;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }
}
