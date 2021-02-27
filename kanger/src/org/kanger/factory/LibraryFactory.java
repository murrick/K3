/*
 * MIT License
 *
 * Copyright (c) 2021 Dmitry G. Quznetsov
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to
 *  deal in the Software without restriction, including without limitation the
 *  rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 *  sell copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 *  FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *  IN THE SOFTWARE.
 *
 */

package org.kanger.factory;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.IOperation;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.Operation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by Dmitry G. Quznetsov on 25.01.2016.
 */
public class LibraryFactory implements IFactory<IOperation> {
    public static final String SCHEMA = "library";

//    private long lastId = 0;
//    private long firstId = 0;

    private ICache cache;
    private IStep top = null;
    private transient Mind mind = null;
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
        if (mind.getNext() == null && mind.isStorageUsed()) {
//            if(mind.getNext() == null) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
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
        if (top == null) {
            top = base.top;
        } else if (base.top != null) {
            base.top.setNext(cache.getRoot());
        }
        cache.setRoot(base.cache.getRoot());
        for (Object s : cache) {
            if (((IUnit) s).getMindId() == base.mind.getId()) {
                ((IUnit) s).setMind(mind);
                ((IUnit) s).setMindId(mind.getId());
            }
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
//        pack();
//        update();
    }

    public void update() throws Exception {
        if (cache.update()) {
//            firstId = lastId;
//            mind.getUser().getStorage(SCHEMA).flush();
        }
    }

    public synchronized IOperation add(IOperation s) throws Exception {
        Operation x = find(s.toString());
        if (x != null) {
            x.setDeleted(false, mind);
            x.setMode(s.getMode());
            x.setProc(((Operation) s).getProc());
//            if (s.isDeleted()) {
//                x.setDeleted(true);
//            }
            x.getScripts().clear();
            x.getScripts().addAll(s.getScripts());
            x.getParams().clear();
            x.getParams().addAll(s.getParams());
//            update();
        } else {
            ((Operation) s).setId(((User) mind.getUser()).nextId(SCHEMA));
            ((Operation) s).setMindId(mind.getId());
            cache.add((IUnit) s);
            if (top == null) {
                top = cache.getRoot();
            }
            x = (Operation) s;
        }
        return x;
    }

    public Operation find(String title) throws Exception {
        for (long id : cache.find((title).hashCode())) {
            IUnit one = get(id);
            if (one.toString().equals(title)) {
                return (Operation) one;
            }
        }
        return null;
    }

    public Operation get(long id) throws Exception {
        Operation t = (Operation) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (Operation) s.getData(mind);
//                t.setMind(mind);
//                t.setUser(user);
            }
        }
        return t;
    }

//    private SysOp get(long id) throws Exception {
//        SysOp t = (SysOp) cache.get(id);
//        return t;
//    }

//    public void delete(SysOp x) {
//        x.setDeleted(true, mind);
//    }

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
            transaction((LibraryFactory) mind.getNext().getLibrary());
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

    @Override
    public Iterator iterator() {
        return cache.iterator();
    }

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }

    public void mark() throws Exception {
        cache.mark();
    }


    public void commit() throws Exception {
        cache.commit();
    }

    public void release() throws Exception {
        cache.release();
    }

}
