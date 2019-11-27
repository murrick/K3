package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.primitives.ArgList;
import org.kanger.storage.Escalera;
import org.kanger.units.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Qusnetsov on 25.05.15.
 */
public class RightFactory implements Iterable<Right> {

    public static final String SCHEMA = "rights";
//    public static final String SCHEMA_STORED = "stored";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    //    private ICache stored;
    private IStep top = null;
    //    private IStep topStored = null;
    private Mind mind = null;

    private transient boolean action = false;

    public RightFactory(Mind mind) {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(RightFactory base) {
//        cache.clear();
//        stored.clear();
//        user.nextId(SCHEMA);
        if (base != null) {
//            System.err.println(" --------------------------------------------------- ");
//            lastId = base.lastId;

//            lastId = user.nextId(SCHEMA);
//            firstId = base.lastId;
            cache = new Escalera(mind, SCHEMA, base.cache);
//            stored = new Escalera(mind, SCHEMA_STORED, base.stored);
        } else {
//            System.err.println(" =================================================== ");
            cache = new Escalera(mind, SCHEMA, null);
//            stored = new Escalera(mind, SCHEMA_STORED, null);
//            if (!cache.isEmpty()) {
//                lastId = cache.getRoot().getId() + 1;
//                firstId = lastId;
//            } else {
//                lastId = 0;
//                firstId = 0;
//            }
        }
    }


    public void commit(RightFactory base) throws Exception {
        for (IStep s = base.cache.getRoot(); s != null; s = s.getNext()) {
            if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                Right r = (Right) s.getData();
                r.commit(mind);
            } else {
                break;
            }
        }
    }

    public void commit2(RightFactory base) throws ClassNotFoundException, RuntimeErrorException, OutOfBufferException, IOException {
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
                if (((IUnit) s.getData()).getMindId() == base.mind.getId()) {
                    Right r = (Right) s.getData();
                    r.setMind(mind);
                } else {
                    break;
                }
            }
        }

//        if (base.topStored != null) {
//            if (stored.getRoot() == null) {
//                topStored = base.topStored;
//            } else {
//                base.topStored.setNext(stored.getRoot());
//            }
//        }
//        stored.setRoot(base.stored.getRoot());
//        if (stored.getRoot() != null && stored.getTop() == null) {
//            stored.setTop(base.stored.getTop());
//        }

//        List<Right> list = new ArrayList();
//        for (Object p : base.cache) {
//            if (((Identifiable) p).getId() < base.firstId) {
//                break;
//            }
//            list.add(0, (Right) p);
//        }
//        for (Right p : list) {
//            add(p);
//        }

        action = base.isAction();

    }

    public void update() throws IOException {
        if (cache.update()) {
//            firstId = lastId;
        }
    }

    public Right register(Right r) {
        r.setId(mind.getUser().nextId(SCHEMA));
        r.setMindId(mind.getId());
        r.setVarIndex(mind.getTerms().getVarIndex());
        return r;
    }

    public Right add(Right r) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right x = find(r);
        if (x != null) {
            delete(r);
            return x;
        } else {
//            if (r.getId() == -1) {
//                r.setId(lastId++);
//            }
            cache.add(r);
            if (top == null) {
                top = cache.getRoot();
            }
//            if (r.isStored()) {
//                stored.add(r.getId(), r.getId());
//                if (topStored == null) {
//                    topStored = cache.getRoot();
//                }
//            }
            for (List<Domain> list : r.getTree()) {
                for (Domain d : list) {
                    r.getPredicates().add(d.getPredicateId());
                    d.setRight(r);
//                    d.setMind(mind);
                    for (TVariable t : d.getArguments().getTVariables(mind, true)) {
                        t.setRight(r);
                    }
//                    mind.getDomains().add(d);
                }
            }
            action = true;
            return r;
        }
    }


//    public void reindex() throws RuntimeErrorException {
//        if (!user.isClosed()) {
//            //TODO: Переиндексация после открытия БД
//        }
//    }
//


    public void expand(Right r) throws Exception {
        for (List<Domain> tree : r.getTree()) {
            if (tree.size() == 1) {
                if (!tree.get(0).getArguments().getTVariables(mind, true).isEmpty()) {
                    mind.getDomains().getWaiters().add(tree.get(0));
                } else if (r.getTree().size() == 1) {
                    Right rx = tree.get(0).setStored();
//                    rx.setGenerated(false);
                } else {
                    Right rx = tree.get(0).createStored();
//                    rx.setGenerated(false);
                }
            }

            for (Domain d : tree) {
                d.setMind(mind);
            }
        }
    }

    public Right load(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right t = get(id);
        if (t == null && !mind.getUser().isClosed()) {
            IStep s = mind.getUser().getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (Right) s.getData(mind);
//                t.setUser(user);
//                t.linkExternal(user);
            }
        }
        return t;
    }

    public Right get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right t = (Right) cache.get(id);
        return t;
    }

    public void clear() throws IOException, OutOfBufferException {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getRights());
        } else {
            cache.clear();
//            stored.clear();
            transaction(null);
        }
    }

    public void delete(Right r) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        r.setDeleted();
        for (List<Domain> list : r.getTree()) {
            for (Domain d : list) {
                mind.getDomains().delete(d);
            }
        }
//            cache.delete(id);
//            stored.delete(id);
    }

//    public void delete(long id) throws IOException, ClassNotFoundException {
//        Right r = get(id);
//        if (r != null) {
//            for (List<Domain> list : r.getTree()) {
//                for (Domain d : list) {
//                    mind.getDomains().delete(d.getId());
//                }
//            }
//            cache.delete(id);
//            stored.delete(id);
//        }
//    }

    public int size() {
        return cache.size();
    }

//    public int storedSize() {
//        return stored.size();
//    }

    public Right add(Domain domain) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Right p = find(domain);
        if (p != null) {
            return p;
        } else {
            ArgList list = null;
            if (domain.isQuery()) {
                list = domain.getArguments().convert(mind);
                for (TValue t : list.getTValues(mind, true)) t.setQuery();
            } else {
                list = domain.getArguments().convertBase(mind);
            }
            Right r = new Right(mind);
            register(r);

            Domain d = mind.getDomains().add(domain.getPredicate(), domain.isAntc(), list, r);
            r.getTree().get(0).add(d);
            r.setGenerated(true);
            r.setStored();

            //TODO: 1
            if (domain.isQuery()) {
                r.setQuery(true);
            }

            int save = mind.getDebugLevel();
            mind.setDebugLevel(0);
            Term origin = mind.getTerms().add(d.toString());
            mind.setDebugLevel(save);
            r.setOrig(origin);

            return add(r);
        }
    }

    public Right store(Domain d) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        d.getRight().setStored();
//        stored.add(d.getRight().getId(), d.getRight().getId());
        return d.getRight();
    }

    public Right find(Domain domain) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
//        domain.setUser(user);
        for (long id : cache.find(domain.getHashBase())) {
            Right one = load(id);
            if (one.equalsTo(domain)) {
                return (Right) one;
            }
        }
        return null;
    }

    public Right find(Right right) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        for (long id : cache.find(right.getHash())) {
            Right one = load(id);
            if (one.equalsTo(right)) {
                return one;
            }
        }
        return null;
    }


    //    public void unlink() throws Exception {
//        cache.unlink();
//        stored.unlink();
//    }
//
    public boolean isAction() {
        return action;
    }

    public void dropAction() {
        action = false;
    }

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

//    public long getLastId() {
//        return lastId;
//    }

//    public long getFirstId() {
//        return firstId;
//    }

    // ****************** DATABASE

//    public Iterable<Long> getDatabase(long fromId) {
//        return new Iterable<Long>() {
//            @Override
//            public Iterator iterator() {
//                return stored.iterator(fromId);
//            }
//        };
//    }


    public void pack() throws IOException, ClassNotFoundException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
//            stored.delete(((IUnit) o).getId());
        }
        update();

//        if (!cache.isEmpty()) {
//            lastId = cache.getRoot().getId() + 1;
////            firstId = lastId;
//        } else {
//            lastId = 0;
////            firstId = 0;
//        }

    }
}
