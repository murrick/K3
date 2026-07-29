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
import org.kanger.enums.LibMode;
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

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;

    public LibraryFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(LibraryFactory base) throws Exception {
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
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
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized IOperation add(IOperation s) throws Exception {
        Operation x = find(s.toString());
        if (x != null) {
            if (x.getMode() == LibMode.FUNCTION || s.getMode() == LibMode.FUNCTION) {
                mind.getFValues().invalidateUdf(x.getName(), x.getRange());
            }
            x.setDeleted(false, mind);
            x.setMode(s.getMode());
            x.setProc(((Operation) s).getProc());
            x.getScripts().clear();
            x.getScripts().addAll(s.getScripts());
            x.getParams().clear();
            x.getParams().addAll(s.getParams());
        } else {
            ((Operation) s).setId(((User) mind.getUser()).nextId(SCHEMA));
            ((Operation) s).setMindId(mind.getId());
            cache.add((IUnit) s);
            if (top == null) {
                top = cache.getRoot();
            }
            x = (Operation) s;
        }
        if (x.getScripts().isEmpty()) {
            x.setDeleted(true, mind);
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
            }
        }
        return t;
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

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction((LibraryFactory) mind.getNext().getLibrary());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public int size() {
        return cache.size();
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
