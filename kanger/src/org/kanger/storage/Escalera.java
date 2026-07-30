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
    // target ID -> predecessor ID in the root-to-tail chain.
    private final Map<Long, Long> predecessorById = new HashMap<>();
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
        predecessorById.clear();
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
        IStep predecessor = null;
        for (IStep step = root; step != null; step = step.getNext()) {
            indexStep(step);
            if (predecessor != null) {
                predecessorById.put(step.getId(), predecessor.getId());
            }
            predecessor = step;
        }
        indexedRoot = root;
        indexValid = true;
    }

    private IStep indexedStep(long id) throws Exception {
        IStep step = memoryById.get(id);
        if (step == null && persistentIds.contains(id) && mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                step = ((User) mind.getUser()).getStorage(schema).get(id);
            }
        }
        return step;
    }

    @Override
    public void add(IUnit one) throws Exception {
        ensureIndex();
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());
        IStep previousRoot = root;
        s.setNext(previousRoot);
        root = s;
        indexStep(s);
        predecessorById.remove(s.getId());
        if (previousRoot != null) {
            predecessorById.put(previousRoot.getId(), s.getId());
        }
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

    private void deleteOne(long id) throws Exception {
        ensureIndex();
        IStep target = indexedStep(id);
        if (target == null) {
            return;
        }

        IStep successor = target.getNext();
        Long predecessorId = predecessorById.get(id);
        if (predecessorId == null) {
            if (root != null && root.getId() == id) {
                root = successor;
            }
        } else {
            IStep predecessor = indexedStep(predecessorId);
            if (predecessor != null) {
                predecessor.setNext(successor);
                if (predecessor instanceof Sapato) {
                    predecessor.update();
                }
            }
        }

        predecessorById.remove(id);
        if (successor != null) {
            if (predecessorId == null) {
                predecessorById.remove(successor.getId());
            } else {
                predecessorById.put(successor.getId(), predecessorId);
            }
        }
        removeIndexedStep(id, target);
        indexedRoot = root;

        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).delete(id);
            }
        }
    }

    @Override
    public void delete(long id) throws Exception {
        deleteOne(id);
    }

    @Override
    public void deleteAll(Collection<Long> ids) throws Exception {
        if (ids == null || ids.isEmpty()) {
            return;
        }

        if (!mind.isStorageUsed() || ids.size() == 1) {
            LinkedHashSet<Long> unique = new LinkedHashSet<>(ids);
            for (Long id : unique) {
                if (id != null) {
                    deleteOne(id);
                }
            }
            return;
        }

        ensureIndex();
        LinkedHashSet<Long> targets = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && (memoryById.containsKey(id) || persistentIds.contains(id))) {
                targets.add(id);
            }
        }
        if (targets.isEmpty()) {
            return;
        }

        /*
         * Unlink complete deleted runs. A run changes only one boundary link,
         * so a persistent predecessor is rewritten at most once even when many
         * adjacent records are removed.
         */
        LinkedHashMap<Long, IStep> removed = new LinkedHashMap<>();
        LinkedHashSet<IStep> changedPersistentPredecessors = new LinkedHashSet<>();
        for (long startId : targets) {
            Long predecessorId = predecessorById.get(startId);
            if (predecessorId != null && targets.contains(predecessorId)) {
                continue;
            }

            IStep current = indexedStep(startId);
            if (current == null) {
                continue;
            }
            IStep predecessor = predecessorId == null ? null : indexedStep(predecessorId);
            while (current != null && targets.contains(current.getId())) {
                removed.put(current.getId(), current);
                current = current.getNext();
            }
            IStep successor = current;

            if (predecessor == null) {
                if (root != null && targets.contains(root.getId())) {
                    root = successor;
                }
            } else {
                predecessor.setNext(successor);
                if (predecessor instanceof Sapato) {
                    changedPersistentPredecessors.add(predecessor);
                }
            }

            if (successor != null) {
                if (predecessorId == null) {
                    predecessorById.remove(successor.getId());
                } else {
                    predecessorById.put(successor.getId(), predecessorId);
                }
            }
        }

        for (IStep predecessor : changedPersistentPredecessors) {
            predecessor.update();
        }
        for (Map.Entry<Long, IStep> entry : removed.entrySet()) {
            predecessorById.remove(entry.getKey());
            removeIndexedStep(entry.getKey(), entry.getValue());
        }
        indexedRoot = root;

        if (mind.isStorageUsed() && !removed.isEmpty()) {
            IBase base = ((User) mind.getUser()).getStorage(schema);
            synchronized (base) {
                for (long id : removed.keySet()) {
                    base.delete(id);
                }
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
        IStep predecessor = null;
        for (IStep step = newRoot; step != null; step = step.getNext()) {
            if (oldRoot != null && step.getId() == oldRoot.getId()) {
                reachedOldRoot = true;
                if (predecessor == null) {
                    predecessorById.remove(step.getId());
                } else {
                    predecessorById.put(step.getId(), predecessor.getId());
                }
                break;
            }
            indexStep(step);
            if (predecessor == null) {
                predecessorById.remove(step.getId());
            } else {
                predecessorById.put(step.getId(), predecessor.getId());
            }
            predecessor = step;
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

                /*
                 * Newly added in-memory Steps always form a prefix in front of
                 * the persistent Sapato suffix. Persist that prefix only: once
                 * the first Sapato is reached, the rest of the chain is already
                 * durable and must not be walked or hydrated again.
                 */
                Deque<IStep> pending = new ArrayDeque<>();
                for (IStep s = root; s != null && !(s instanceof Sapato); s = s.getNext()) {
                    pending.push(s);
                }

                while (!pending.isEmpty()) {
                    IStep s = pending.pop();
                    Sapato p = new Sapato(base, s);
                    p.append();
                    IStep e = base.get(s.getId());
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
