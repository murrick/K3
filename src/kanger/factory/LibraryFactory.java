package kanger.factory;

import kanger.User;
import kanger.compiler.SysOp;
import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.interfaces.IUnit;
import kanger.storage.Escalera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry Kuznetsov on 25.01.2016.
 */
public class LibraryFactory implements Iterable<SysOp> {
    public static final String SCHEMA = "library";

    private long lastId = 0;
    private long firstId = 0;

    private ICache cache;
    private User user = null;


//    private SysOp root = null;
//    private SysOp save = null;
//    private Map<String, SysOp> index = new HashMap<>();
//    private User user = null;

    public LibraryFactory(User user) {
        this.user = user;
        transaction(null);
    }

    public void transaction(LibraryFactory base) {
        if (base != null) {
            lastId = base.lastId;
            firstId = base.lastId;
            cache = new Escalera(user, SCHEMA, base.cache);
        } else {
            cache = new Escalera(user, SCHEMA, null);
            if (!cache.isEmpty()) {
                lastId = cache.getRoot().getId() + 1;
                firstId = lastId;
            } else {
                lastId = 0;
                firstId = 0;
            }
        }
    }

    public void commit(LibraryFactory base) {
        cache.setRoot(base.cache.getRoot());
        if (cache.getRoot() != null) {
            lastId = cache.getRoot().getId() + 1;
            if (cache.getTop() == null) {
                cache.setTop(base.cache.getTop());
                firstId = cache.getTop().getId();
            }
        }
    }

    public void update() throws IOException {
        if (cache.update()) {
            firstId = lastId;
        }
    }

    public SysOp add(SysOp s) throws IOException, ClassNotFoundException {
        SysOp x = find(s.toString());
        if (x != null) {
            x.setMode(s.getMode());
            x.setProc(s.getProc());
            x.getScripts().clear();
            x.getScripts().addAll(s.getScripts());
//            update();
        } else {
            s.setId(lastId++);
            cache.add(s);
            x = s;
        }
        return x;
    }

    public SysOp find(String title) throws IOException, ClassNotFoundException {
        for (long id : cache.find((title).hashCode())) {
            IUnit one = load(id);
            if (one.toString().equals(title)) {
                return (SysOp) one;
            }
        }
        return null;
    }

    public SysOp load(long id) throws IOException, ClassNotFoundException {
        SysOp t = get(id);
        if (t == null && !user.isClosed()) {
            IStep s = user.getStorage(SCHEMA).get(id);
            if (s != null) {
                t = (SysOp) s.getData();
                t.setUser(user);
            }
        }
        return t;
    }

    public SysOp get(long id) throws IOException, ClassNotFoundException {
        SysOp t = (SysOp) cache.get(id);
        return t;
    }

    public void delete(SysOp x) {
        x.setDeleted();
    }

    public void pack() throws IOException, ClassNotFoundException {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted()) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
        update();

        if (!cache.isEmpty()) {
            lastId = cache.getRoot().getId() + 1;
            firstId = lastId;
        } else {
            lastId = 0;
            firstId = 0;
        }
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

    public void clear() throws IOException, ClassNotFoundException {
        if (user.getMind().getNext() != null) {
            transaction(user.getMind().getNext().getLibrary());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void unlink() throws Exception {
        cache.unlink();
    }

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

}
