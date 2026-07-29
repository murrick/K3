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

package org.kanger.storage;

import org.kanger.Mind;
import org.kanger.User;
import org.kanger.interfaces.internal.IBase;
import org.kanger.interfaces.internal.ICache;
import org.kanger.interfaces.internal.IStep;
import org.kanger.interfaces.internal.IUnit;

import java.util.*;

/**
 * Created by Dmitry G. Quznetsov on 27.05.20.
 */
public class Escalera implements ICache {

    private IStep root = null;

    private String schema = "";
    private Stack<IStep> stack = new Stack<>();

    private Mind mind = null;

    /**
     * Lightweight acceleration metadata. Persistent semantic objects are not
     * retained here: persistent entries are represented by IDs and are loaded
     * through the configured IBase only when get(id) is requested.
     */
    private final Map<Long, IStep> memoryById = new HashMap<>();
    private final Set<Long> persistentIds = new HashSet<>();
    private final Map<Integer, Set<Long>> idsByHash = new HashMap<>();
    private boolean indexValid = false;
    private IStep indexedRoot = null;

    public Escalera(Mind mind, String schema, ICache parent) {
        this.mind = mind;
        this.schema = schema;

        if (parent == null && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                root = ((User) mind.getUser()).getStorage(schema).getRoot();
            }
        } else if (parent != null) {
            root = parent.getRoot();
        }
    }

    private void invalidateIndex() {
        indexValid = false;
        indexedRoot = null;
    }

    private void clearIndex() {
        memoryById.clear();
        persistentIds.clear();
        idsByHash.clear();
    }

    private void indexStep(IStep step) {
        if (step == null) {
            return;
        }
        long id = step.getId();
        if (step instanceof Sapato) {
            persistentIds.add(id);
            memoryById.remove(id);
        } else {
            memoryById.put(id, step);
            persistentIds.remove(id);
        }
        Set<Long> ids = idsByHash.get(step.getHash());
        if (ids == null) {
            ids = new HashSet<>();
            idsByHash.put(step.getHash(), ids);
        }
        ids.add(id);
    }

    private void removeIndexedStep(long id, IStep step) {
        memoryById.remove(id);
        persistentIds.remove(id);
        if (step != null) {
            Set<Long> ids = idsByHash.get(step.getHash());
            if (ids != null) {
                ids.remove(id);
                if (ids.isEmpty()) {
                    idsByHash.remove(step.getHash());
                }
            }
        } else {
            Iterator<Map.Entry<Integer, Set<Long>>> iterator = idsByHash.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<Integer, Set<Long>> entry = iterator.next();
                entry.getValue().remove(id);
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
    }

    private void ensureIndex() {
        if (indexValid && indexedRoot == root) {
            return;
        }
        clearIndex();
        for (IStep step = root; step != null; step = step.getNext()) {
            indexStep(step);
        }
        indexedRoot = root;
        indexValid = true;
    }

    @Override
    public void add(IUnit one) throws Exception {
        ensureIndex();
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());
        s.setNext(root);
        root = s;
        indexStep(s);
        indexedRoot = root;
    }

    @Override
    public Object get(long id) throws Exception {
        ensureIndex();
        IStep step = memoryById.get(id);
        if (step == null && persistentIds.contains(id) && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                step = ((User) mind.getUser()).getStorage(schema).get(id);
            }
        }
        return step == null ? null : step.getData(mind);
    }

    @Override
    public void delete(long id) throws Exception {
        ensureIndex();
        IStep indexed = memoryById.get(id);
        if (indexed == null && persistentIds.contains(id) && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                indexed = ((User) mind.getUser()).getStorage(schema).get(id);
            }
        }

        if (root != null && root.getId() == id) {
            root = root.getNext();
        } else {
            for (IStep s = root; s != null && s.getNext() != null; s = s.getNext()) {
                if (s.getNext().getId() == id) {
                    s.setNext(s.getNext().getNext());
                    break;
                }
            }
        }
        removeIndexedStep(id, indexed);
        indexedRoot = root;

        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).delete(id);
            }
        }
    }

    @Override
    public int size() {
        ensureIndex();
        return memoryById.size() + persistentIds.size();
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public Set<Long> find(int h) throws Exception {
        ensureIndex();
        Set<Long> ids = idsByHash.get(h);
        return ids == null ? new HashSet<Long>() : new HashSet<>(ids);
    }


    @Override
    public void clear() throws Exception {
        root = null;
        clearIndex();
        indexedRoot = null;
        indexValid = true;
        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).clear();
            }
        } else {
            ((User) mind.getUser()).clearCounters(schema);
        }
    }

    @Override
    public long mark() {
        if (root != null) {
            return stack.push(root).getId();
        } else {
            return -1;
        }
    }

    @Override
    public long commit() {
        if (!stack.isEmpty()) {
            stack.pop();
        }
        return root == null ? -1 : root.getId();
    }

    @Override
    public long release() {
        if (!stack.isEmpty()) {
            root = stack.pop();
            invalidateIndex();
        }
        return root == null ? -1 : root.getId();
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        ensureIndex();
        return memoryById.containsKey(id) || persistentIds.contains(id);
    }

    @Override
    public Iterator<Object> iterator() {
        return new WalkIterator(-1);
    }

    @Override
    public Iterator<Object> iterator(long fromId) {
        return new WalkIterator(fromId);
    }

    @Override
    public IStep getRoot() {
        return root;
    }

    @Override
    public void setRoot(IStep newRoot) {
        if (!indexValid) {
            root = newRoot;
            return;
        }

        IStep oldRoot = root;
        root = newRoot;
        if (oldRoot == newRoot) {
            indexedRoot = root;
            return;
        }

        boolean reachedOldRoot = oldRoot == null;
        for (IStep step = newRoot; step != null; step = step.getNext()) {
            if (oldRoot != null && step.getId() == oldRoot.getId()) {
                reachedOldRoot = true;
                break;
            }
            indexStep(step);
        }
        if (reachedOldRoot) {
            indexedRoot = root;
        } else {
            invalidateIndex();
        }
    }

    @Override
    public boolean update() throws Exception {
        // Это самый низ
        if (mind.isStorageUsed()) {

            IBase base = ((User) mind.getUser()).getStorage(schema);
            synchronized (base) {

                List<IStep> list = new ArrayList<>();
                for (IStep s = root; s != null; s = s.getNext()) {
                    if (!base.containsKey(s.getId())) {
                        list.add(0, s);
                    }
                }

                for (IStep s : list) {
                    Sapato p = new Sapato(base, s);
                    p.append();
                    IStep e = ((User) mind.getUser()).getStorage(schema).get(s.getId());
                    p.setData(e.getData(mind));
                    root = p;
                    if (indexValid) {
                        memoryById.remove(s.getId());
                        persistentIds.add(s.getId());
                    }
                }
                if (indexValid) {
                    indexedRoot = root;
                }
                stack.clear();
                return true;
            }
        } else {
            return false;
        }
    }

    public class WalkIterator implements Iterator {

        @Override
        public void remove() {
        }

        private IStep step;

        public WalkIterator(long fromId) {
            step = root;
            try {
                if (root instanceof Sapato) {
                    root.setData(((User) mind.getUser()).getStorage(schema).get(root.getId()).getData(mind));
                }
            } catch (Exception e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }

            if (fromId >= 0) {
                for (; step != null; step = step.getNext()) {
                    if (step.getId() == fromId) {
                        break;
                    }
                }
            }
        }

        @Override
        public boolean hasNext() {
            return step != null;
        }

        @Override
        public Object next() {
            Object o = null;
            try {
                o = step.getData(mind);
                step = step.getNext();
            } catch (Exception e) {
                System.err.println(new Date());
                e.printStackTrace(System.err);
            }
            return o;
        }

    }
}
