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
import org.kanger.interfaces.IReactor;
import org.kanger.interfaces.ITerm;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;
import org.kanger.storage.Escalera;
import org.kanger.units.TValue;
import org.kanger.units.TVariable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * Created by Dmitry G. Quznetsov on 25.05.15.
 */
public class TValueFactory implements IFactory<TValue> {

    public static final String SCHEMA = "tvalues";

    private ICache cache;
    private final Mind mind;
    private IStep top = null;
    private IBase connection = null;
    private final Map<TVariable, TValue> current = new HashMap<>();
    private boolean action = false;

    /**
     * Transaction-layered acceleration metadata. Buckets contain TValue IDs
     * only; values are hydrated through Escalera/IBase on demand.
     */
    private TValueFactory parentIndex = null;
    private final Map<Long, LinkedHashSet<Long>> localByVariable = new HashMap<>();
    private final Object indexLock = new Object();

    /**
     * Nested Linker marks are extremely frequent. Recording only values added
     * since a mark keeps rollback proportional to the delta instead of copying
     * the complete index for every candidate pair.
     */
    private final Stack<List<TValue>> additionsStack = new Stack<>();
    private boolean indexInitialized = false;

    public TValueFactory(Mind mind) throws Exception {
        this.mind = mind;
        transaction(null);
    }

    public void transaction(TValueFactory base) throws Exception {
        top = null;
        connection = null;
        action = false;
        if (mind.getNext() == null && mind.isStorageUsed()) {
            connection = ((User) mind.getUser()).getStorage(SCHEMA);
        }

        current.clear();
        if (base != null) {
            cache = new Escalera(mind, SCHEMA, base.cache);
        } else {
            cache = new Escalera(mind, SCHEMA, null);
        }

        parentIndex = base;
        synchronized (indexLock) {
            localByVariable.clear();
            additionsStack.clear();
            indexInitialized = base != null;
        }
    }

    private void indexLocked(TValue value) {
        if (value == null) {
            return;
        }
        LinkedHashSet<Long> ids = localByVariable.get(value.getTVarId());
        if (ids == null) {
            ids = new LinkedHashSet<>();
            localByVariable.put(value.getTVarId(), ids);
        }
        ids.add(value.getId());
    }

    private void index(TValue value) {
        synchronized (indexLock) {
            indexLocked(value);
        }
    }

    private void unindex(TValue value) {
        synchronized (indexLock) {
            if (value == null || !indexInitialized) {
                return;
            }
            LinkedHashSet<Long> ids = localByVariable.get(value.getTVarId());
            if (ids != null) {
                ids.remove(value.getId());
                if (ids.isEmpty()) {
                    localByVariable.remove(value.getTVarId());
                }
            }
        }
    }

    private void ensureIndex() throws Exception {
        synchronized (indexLock) {
            if (indexInitialized) {
                return;
            }
            localByVariable.clear();

            // Escalera iterates newest first, while the historical forward()
            // traversal delivered oldest values first. Reversing here preserves
            // the observable substitution order.
            List<TValue> values = new ArrayList<>();
            for (Object value : cache) {
                values.add((TValue) value);
            }
            for (int i = values.size() - 1; i >= 0; --i) {
                indexLocked(values.get(i));
            }
            indexInitialized = true;
        }
    }

    private Map<Long, LinkedHashSet<Long>> snapshotIndex() throws Exception {
        ensureIndex();
        synchronized (indexLock) {
            Map<Long, LinkedHashSet<Long>> snapshot = new HashMap<>();
            for (Map.Entry<Long, LinkedHashSet<Long>> entry : localByVariable.entrySet()) {
                snapshot.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
            }
            return snapshot;
        }
    }

    private void collectIds(long variableId, LinkedHashSet<Long> result) throws Exception {
        if (parentIndex != null) {
            parentIndex.collectIds(variableId, result);
        }
        ensureIndex();
        synchronized (indexLock) {
            LinkedHashSet<Long> local = localByVariable.get(variableId);
            if (local != null) {
                result.addAll(new LinkedHashSet<>(local));
            }
        }
    }

    private void mergeIndex(TValueFactory child) throws Exception {
        Map<Long, LinkedHashSet<Long>> childSnapshot = child.snapshotIndex();
        ensureIndex();
        synchronized (indexLock) {
            for (Map.Entry<Long, LinkedHashSet<Long>> entry : childSnapshot.entrySet()) {
                LinkedHashSet<Long> ids = localByVariable.get(entry.getKey());
                if (ids == null) {
                    ids = new LinkedHashSet<>();
                    localByVariable.put(entry.getKey(), ids);
                }
                ids.addAll(entry.getValue());
            }
        }
    }

    public void commit(TValueFactory base) throws Exception {
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
        mergeIndex(base);
        action = action || base.isAction();
    }

    public void update() throws Exception {
        if (cache.update()) {
        }
    }

    public synchronized TValue add(TVariable tv, ITerm o) throws Exception {
        TValue t = find(tv, o);
        if (t == null) {
            t = new TValue(tv, o, mind);
            t.setTVar(tv);
            t.setId(((User) mind.getUser()).nextId(SCHEMA));
            t.setMindId(mind.getId());
            cache.add(t);
            index(t);
            synchronized (indexLock) {
                if (!additionsStack.isEmpty()) {
                    additionsStack.peek().add(t);
                }
            }
            if (top == null) {
                top = cache.getRoot();
            }
            action = true;
            tv.incFloodControl(o);
        } else {
            t.setDeleted(false, mind);
        }
        return t;
    }

    public TValue get(TVariable tv) {
        if (isEmpty(tv)) {
            return null;
        }
        return current.get(tv);
    }

    public boolean isEmpty(TVariable tv) {
        return !current.containsKey(tv);
    }

    public TValue find(TVariable tv, ITerm v) throws Exception {
        TValue temp = new TValue(tv, v);
        for (long id : cache.find(temp.getHash())) {
            IUnit one = get(id);
            // Intentionally no deleted filter: canonical resurrection reuses the
            // existing TValue identity and clears its transaction deletion mark.
            if (one.equalsTo(temp)) {
                return (TValue) one;
            }
        }
        return null;
    }

    public TValue get(long id) throws Exception {
        TValue t = (TValue) cache.get(id);
        if (t == null && connection != null) {
            IStep s = connection.get(id);
            if (s != null) {
                t = (TValue) s.getData(mind);
            }
        }
        return t;
    }

    public void pack() throws Exception {
        List<Object> toDelete = new ArrayList<>();
        for (Object o : cache) {
            if (((IUnit) o).isDeleted(mind)) {
                toDelete.add(o);
            } else {
                boolean found = mind.getRules().hasActiveRuleWithTerm(
                        ((TValue) o).getValue(mind).getId());
                if (!found) {
                    toDelete.add(o);
                }
            }
        }
        for (Object o : toDelete) {
            unindex((TValue) o);
            cache.delete(((IUnit) o).getId());
        }
        Iterator<Map.Entry<TVariable, TValue>> iterator = current.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!cache.containsKey(iterator.next().getValue().getId())) {
                iterator.remove();
            }
        }
        top = cache.getRoot();
    }

    public void clear() throws Exception {
        if (mind.getNext() != null) {
            transaction(((Mind) mind.getNext()).getTValues());
        } else {
            cache.clear();
            transaction(null);
        }
    }

    public long mark() throws Exception {
        synchronized (indexLock) {
            additionsStack.push(new ArrayList<TValue>());
        }
        return cache.mark();
    }

    public long commit() throws Exception {
        synchronized (indexLock) {
            if (!additionsStack.isEmpty()) {
                List<TValue> additions = additionsStack.pop();
                if (!additionsStack.isEmpty()) {
                    additionsStack.peek().addAll(additions);
                }
            }
        }
        return cache.commit();
    }

    public long release() throws Exception {
        long result = cache.release();
        List<TValue> additions = null;
        synchronized (indexLock) {
            if (!additionsStack.isEmpty()) {
                additions = additionsStack.pop();
            }
        }
        if (additions != null) {
            for (int i = additions.size() - 1; i >= 0; --i) {
                unindex(additions.get(i));
            }
        }
        return result;
    }

    public TValue set(TVariable tv, TValue v) {
        if (v == null) {
            current.remove(tv);
        } else {
            current.put(tv, v);
        }
        return v;
    }

    public int size() throws Exception {
        return cache.size();
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

    public TValue getRoot(TVariable t) throws Exception {
        for (TValue v : this) {
            if (!v.isDeleted(mind) && v.getTVar(mind).getId() == t.getId()) {
                return v;
            }
        }
        return null;
    }

    public Map<TVariable, TValue> getCurrent() {
        return current;
    }

    public void forEach(TVariable t, IReactor reactor) throws Exception {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        collectIds(t.getId(), ids);
        for (long id : ids) {
            TValue value = get(id);
            if (value != null) {
                reactor.run(value);
            }
        }
    }

    public void scan(TVariable t, IReactor reactor) throws Exception {
        if (!cache.isEmpty()) {
            IStep root;
            IStep bottom = null;
            do {
                root = cache.getRoot();
                IStep saveRoot = root;
                for (; root != bottom; root = root.getNext()) {
                    if (((TValue) root.getData(mind)).getTVarId() == t.getId()) {
                        reactor.run(root.getData(mind));
                    }
                }
                bottom = saveRoot;
            } while (root != cache.getRoot());
        }
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
