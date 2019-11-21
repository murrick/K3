package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.primitives.Argument;
import org.kanger.storage.Escalera;
import org.kanger.units.*;

import java.io.IOException;
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

    public DomainFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        waiters.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            waiters.addAll(base.waiters);
            cache = new Escalera(mind, SCHEMA, base.cache);
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

    public void commit(DomainFactory base) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
        if (base.top != null) {
            if (cache.getRoot() == null) {
                top = base.top;
            } else {
                base.top.setNext(cache.getRoot());
            }
        }
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                ((IUnit) s.getData()).setMind(mind);
            }
        }
        waiters.addAll(base.waiters);
    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = lastId;
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


    public Domain add(Predicate pred, boolean antc, ArgList arg, Right r) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain p = find(pred, antc, arg, r);
        if (p != null) {
            return p;
        } else {
            p = new Domain(mind);
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
            p.setId(mind.getUser().nextId(SCHEMA));
            p.setMindId(mind.getId());
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
//            p.getArguments().setUser(user);
            cache.add(p);
            if (top == null) {
                top = cache.getRoot();
            }
            return p;
        }
    }

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain temp = new Domain(pred, antc, arg, r);
//        temp.setUser(user);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = load(id);
            if (one.equalsTo(temp)) {
                return (Domain) one;
            }
        }
        return null;
    }

    public Domain load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Domain) s.getData(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Domain get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain t = (Domain) cache.get(id);
        return t;
    }

    public void pack() throws IOException, ClassNotFoundException {
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
        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
//            firstId = lastId;
//        } else {
//            lastId = 0;
//            firstId = 0;
//        }

    }

    public void delete(Domain d) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        d.setDeleted();
        for (TVariable t : d.getArguments().getTVariables(mind, true)) {
            mind.getTVars().delete(t);
        }
        for (TValue v : d.getArguments().getTValues(mind, true)) {
            mind.getTValues().delete(v);
        }
        for (Function f : d.getArguments().getFunctions(mind)) {
            mind.getFunctions().delete(f);
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

    public void clear() throws IOException, OutOfBufferException {
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

}
