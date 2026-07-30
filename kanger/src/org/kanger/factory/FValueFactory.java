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
import org.kanger.enums.FunctionBinding;
import org.kanger.interfaces.IFactory;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.FValue;
import org.kanger.units.Function;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class FValueFactory implements IFactory<FValue> {

    public static final String SCHEMA = "fvalues";

    private ICache cache;
    private IStep top = null;
    private IBase connection = null;

    private final Mind mind;
    private boolean action = false;
    private final Set<Long> invalidated = new CopyOnWriteArraySet<>();
    private final Stack<Set<Long>> invalidatedStack = new Stack<>();

    public FValueFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(FValueFactory base) throws Exception {
        // Drop every anchor into the previous cache/storage generation.
        top = null;
        connection = null;
        action = false;
        invalidated.clear();
        invalidatedStack.clear();
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
            invalidated.addAll(base.invalidated);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }
    }

    public void commit(FValueFactory base) throws Exception {
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
        invalidated.addAll(base.invalidated);
        action = base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized FValue add(Function f) throws Exception {
        FValue t = find(f);
        if (t == null) {
            if (f.isComplete()) {
                t = new FValue(f, mind);
                t.setId(((User) mind.getUser()).nextId(SCHEMA));
                t.setMindId(mind.getId());
                cache.add(t);
                if (top == null) {
                    top = cache.getRoot();
                }
                action = true;
            } else {
                return null;
            }
        } else {
            t.setDeleted(false, mind);
        }
        return t;
    }

    public FValue find(Function f) throws Exception {
        for (long id : cache.find(f.getHashBase(mind))) {
            FValue one = get(id);
            if (!invalidated.contains(id) && one.equalsTo(f)) {
                return one;
            }
        }
        return null;
    }

    public void invalidateUdf(String name, int range) throws Exception {
        for (Object object : cache) {
            if (object instanceof FValue) {
                FValue value = (FValue) object;
                Function function = value.getFunction();
                if (usesUdf(function, name, range)) {
                    invalidated.add(value.getId());
                }
            }
        }
    }

    private boolean usesUdf(Function function, String name, int range) throws Exception {
        if (!name.equals(function.getName(mind).toString())) {
            return false;
        }
        if (range != 0 && function.getRange() != range) {
            return false;
        }
        if (function.getBinding() == FunctionBinding.UDF_DYNAMIC) {
            return true;
        }
        if (function.getBinding() != FunctionBinding.LEGACY_AUTO) {
            return false;
        }

        String signature = name + "(" + function.getRange() + ")";
        String fallback = name + "(0)";
        return !mind.getCalculator().getFunctions().getSysOps().containsKey(signature)
                && !mind.getCalculator().getFunctions().getSysOps().containsKey(fallback);
    }

    public FValue get(long id) throws Exception {
        FValue t = (FValue) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (FValue) s.getData(mind);
            }
        }
        return t;
    }

    public void clear() throws Exception {
        invalidated.clear();
        invalidatedStack.clear();
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getFValues());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public void mark() throws Exception {
        cache.mark();
        invalidatedStack.push(new HashSet<>(invalidated));
    }

    public void commit() throws Exception {
        cache.commit();
        if (!invalidatedStack.isEmpty()) {
            invalidatedStack.pop();
        }
    }

    public void release() throws Exception {
        cache.release();
        if (!invalidatedStack.isEmpty()) {
            invalidated.clear();
            invalidated.addAll(invalidatedStack.pop());
        }
    }

    public int size() {
        return cache.size();
    }

    public long getLastId() {
        return cache.isEmpty() ? -1 : cache.getRoot().getId();
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            IUnit unit = (IUnit) o;
            if (unit.isDeleted(mind) || invalidated.contains(unit.getId())) {
                toDelete.add(o);
            }
        }
        for (Object o : toDelete) {
            cache.delete(((IUnit) o).getId());
        }
        invalidated.clear();
    }

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

    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    public boolean isEmpty() {
        return cache == null || cache.isEmpty();
    }
}
