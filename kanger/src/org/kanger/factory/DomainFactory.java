package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;
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
    //    private Cache load = new Cache();
    private IUser user = null;
    private Mind mind = null;

    public DomainFactory(IUser user) {
        this.user = user;
        this.mind = user.getMind();
        transaction(null);
    }

    public void transaction(DomainFactory base) {
        waiters.clear();
        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
            waiters.addAll(base.waiters);
            cache = new Escalera(user.getMind(), SCHEMA, base.cache);
        } else {
            cache = new Escalera(user.getMind(), SCHEMA, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }

    public void commit(DomainFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {

            for (IStep s = cache.getRoot(); s != null; s = s.getNext()) {
                ((Domain) s.getData()).setMind(mind);
            }

//            lastId = cache.getRoot().getId() + 1;

//            if (cache.getTop() == null) {
//                cache.setTop(base.cache.getTop());
////                firstId = cache.getTop().getId();
//            }
        }
        waiters.addAll(base.waiters);


//        List<Domain> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Domain) p);
//        }
//        for (Domain p : list) {
//            p.setId(lastId++);
//            cache.add(p);
//        }
//        waiters.addAll(base.waiters);
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
            p = new Domain(user.getMind());
            p.setPredicate(pred);
            p.setAntc(antc);
            p.setRight(r);
            p.setId(user.nextId(SCHEMA));
            p.setMindId(user.getMind().getId());
            if (arg != null) {
                for (Argument t : arg) {
                    p.add(t);
                }
            }
//            p.getArguments().setUser(user);
            cache.add(p);
            return p;
        }
    }

    public Domain find(Predicate pred, boolean antc, ArgList arg, Right r) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Domain temp = new Domain(pred, antc, arg, r);
        temp.setUser(user);
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
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Domain) s.getData(user.getMind());
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
        for (TVariable t : d.getArguments().getTVariables(user.getMind(), true)) {
            user.getMind().getTVars().delete(t);
        }
        for (TValue v : d.getArguments().getTValues(user.getMind(), true)) {
            user.getMind().getTValues().delete(v);
        }
        for (Function f : d.getArguments().getFunctions(user.getMind())) {
            user.getMind().getFunctions().delete(f);
        }

//            for (TValue v : user.getMind().getTValues()) {
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
//            user.getMind().getTValues().update();
//            waiters.remove(d);
//            cache.delete(id);
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Domain d = get(id);
//        if (d != null) {
//            for (TVariable t : d.getArguments().getTVariables(true)) {
//                user.getMind().getTVars().delete(t.getId());
//            }
//            for (TValue v : user.getMind().getTValues()) {
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
//            user.getMind().getTValues().update();
//            waiters.remove(d);
//            cache.delete(id);
//        }
//    }

    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getDomains());
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
