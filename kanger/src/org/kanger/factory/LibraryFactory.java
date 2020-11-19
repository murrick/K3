package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.interfaces.IBase;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.SysOp;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry Kuznetsov on 25.01.2016.
 */
public class LibraryFactory implements Iterable<SysOp> {
    public static final String SCHEMA = "library";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private Mind mind = null;
    private IBase connection = null;


//    private SysOp root = null;
//    private SysOp save = null;
//    private Map<String, SysOp> index = new HashMap<>();
//    private User user = null;

    public LibraryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(LibraryFactory base) throws Exception {
        if (!mind.getUser().isClosed()) {
//            if(mind.getNext() == null) {
            connection = mind.getUser().getStorage(SCHEMA);
//            } else {
//                connection = mind.getUser().connect(SCHEMA);
//            }
        }

        if (base != null) {
//            lastId = base.lastId;
//            firstId = base.lastId;
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

    public void commit(LibraryFactory base) throws Exception {
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
                    ((IUnit) s.getData()).setMind(mind);
                    ((IUnit) s.getData()).setMindId(mind.getId());
                } else {
                    break;
                }
            }
        }
//        pack();
//        update();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized SysOp add(SysOp s) throws Exception {
        SysOp x = find(s.toString());
        if (x != null) {
            x.setMode(s.getMode());
            x.setProc(s.getProc());
            x.getScripts().clear();
            x.getScripts().addAll(s.getScripts());
            x.getParams().clear();
            x.getParams().addAll(s.getParams());
//            update();
        } else {
                s.setId(mind.getUser().nextId(SCHEMA));
                s.setMindId(mind.getId());
                cache.add(s);
                if (top == null) {
                    top = cache.getRoot();
                }
                x = s;
            }
            return x;
    }

    public SysOp find(String title) throws Exception {
        for (long id : cache.find((title).hashCode())) {
            IUnit one = load(id);
            if (one.toString().equals(title)) {
                return (SysOp) one;
            }
        }
        return null;
    }

    public SysOp load(long id) throws Exception {
        SysOp t = get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (SysOp) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
            }
        }
        return t;
    }

    private SysOp get(long id) throws Exception {
        SysOp t = (SysOp) cache.get(id);
        return t;
    }

    public void delete(SysOp x) {
        x.setDeleted();
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
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

//    public SysOp find(String key) {
//        if (index.containsKey(key)) {
//            return index.get(key);
//        } else {
//            return null;
//        }
//    }

    //    public void mark() {
//        save = root;
//    }
//
//    public void release() {
//        root = save;
//    }
//
//    public void reset() {
//        root = null;
//        save = null;
//        index.clear();
//    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(mind.getNext().getLibrary());
        } else {
            cache.clear();
            transaction(null);
        }
    }

//    public void unlink() throws Exception {
//        cache.unlink();
//    }

    public int size() {
        return cache.size();
    }


//    public LibraryStore clone(Mind mind) {
//        LibraryStore stores = new LibraryStore(mind);
//        stores.root = root;
//        stores.save = root;
//        for(String key : index.keySet()) {
//            stores.index.put(key, index.createCVar(key));
//        }
//        return stores;
//    }
//
//    public void commit() {
//        LibraryStore parent = mind.getParent().getLibrary();
//        for(SysOp op = root; op != null && op != save; op = op.getNext()) {
//            parent.createTVar(op);
//        }
//    }

//    public Map<String, SysOp> getRoot() {
//        return index;
//    }

    public boolean isEmpty() {
        return cache.isEmpty();
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
