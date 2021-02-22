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
 * Created by Dmitry G. Qusnetsov on 27.05.20.
 */
public class Escalera implements ICache {

    private IStep root = null;

    //    private ICache parent = null;
    private Stack<IStep> stack = new Stack<>();
    private Mind mind = null;
    private String schema = "";

    public Escalera(Mind mind, String schema, ICache parent) {
//        this.parent = parent;
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


    @Override
    public void add(IUnit one) throws Exception {
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());
        s.setNext(root);
        root = s;
    }

//    @Override
//    public void update(IUnit one) throws Exception {
//        IStep s = mind.getUser().getStorage(schema).get(one.getId());
//        if (s != null) {
//            s.setData(one);
//            s.update();
//        }
//
////        for (IStep s = root; s != null; s = s.getNext()) {
////            if (s.getId() == one.getId()) {
////                s.setData(one);
////                s.update();
////                break;
////            }
////        }
//    }

//    @Override
//    public void add(long id, Object one) throws IOException {
//        Step s = new Step();
//        s.setData(one);
//        s.setId(id);
//        s.setHash(one.hashCode());
//        s.setNext(root);
//        root = s;
//    }

    @Override
    public Object get(long id) throws Exception {
//        if (root instanceof Sapato) {
//            IStep e = mind.getUser().getStorage(schema).get(root.getId());
//            root.setData(e.getData(mind));
//        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getId() == id) {
//                if(s.getData() != null && s.getData() instanceof IUnit && ((IUnit) s.getData()).getUser() == null) {
//                    ((IUnit) s.getData()).setUser(user);
//                }
//                if (!((IUnit)s.getData()).isLoaded()) {
//                    IStep e = mind.getUser().getStorage(schema).get(s.getId());
//                    s.setData(e.getData(mind));
//                }
                return s.getData(mind);
            }
        }
        // Прямое обращение к БД имеет значение только в начальной загрузке
//        return user.isClosed() ? null : user.getStorage(schema).get(id).getData();
        return null;
    }

    @Override
    public void delete(long id) throws Exception {
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
        if (mind.isStorageUsed()) {
            synchronized (((User) mind.getUser()).getStorage(schema)) {
                ((User) mind.getUser()).getStorage(schema).delete(id);
            }
        }
    }

    @Override
    public int size() {
        int cnt = 0;
        for (IStep s = root; s != null; s = s.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    @Override
    public boolean isEmpty() {
        return root == null;
    }

    @Override
    public Set<Long> find(int h) throws Exception {
        Set<Long> set = new HashSet<>();
        if (root instanceof Sapato) {
            root.setData((((User) mind.getUser()).getStorage(schema).get(root.getId())).getData(mind));
        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getHash() == h) {
                set.add(s.getId());
            }
        }
        return set;
    }


    @Override
    public void clear() throws Exception {
        root = null;
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
        }
        return root == null ? -1 : root.getId();
    }

    @Override
    public boolean containsKey(long id) throws Exception {
        return get(id) != null;
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
    public void setRoot(IStep root) {
        this.root = root;

    }

    @Override
    public boolean update() throws Exception {
        // Это самый низ

        if (mind.isStorageUsed()) {

            IBase base = ((User) mind.getUser()).getStorage(schema);
            synchronized (base) {

                List<IStep> list = new ArrayList<>();
                for (IStep s = root; s != null; s = s.getNext()) {
                    if (!base.containsKey(s.getId())/*s instanceof Step*/ /*&& !((IUnit)s.getData()).isDeleted()*//*!mind.getUser().getStorage(schema).containsKey(s.getId())*/) {
                        list.add(0, s);
//                    } else {
//                        break;
                    }
                }

                for (IStep s : list) {
                    Sapato p = new Sapato(base, s);
                    p.append();
                    IStep e = ((User) mind.getUser()).getStorage(schema).get(s.getId());
                    p.setData(e.getData(mind));

//                    p.getData(mind);
                    root = p;
                }


//            long lastId = mind.getUser().getStorage(schema).isEmpty() ? -1 : mind.getUser().getStorage(schema).getRoot().getId();
//            List<IStep> list = new ArrayList<>();
//            for (IStep s = root; s != null; s = s.getNext()) {
//                if (s.getId() < lastId) {
//                    break;
//                }
//                list.add(s);
//            }
//            for (IStep p : list) {
//                Sapato s = new Sapato(mind.getUser().getStorage(schema), p);
//                s.append();
//            }

//                root = base.getRoot();
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
            // TODO: Implement this method
        }

        private IStep step;

        public WalkIterator(long fromId) {
            step = root;
            try {
                if (root instanceof Sapato) {
                    root.setData(((User) mind.getUser()).getStorage(schema).get(root.getId()).getData(mind));
                }
            } catch (Exception e) {
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
                e.printStackTrace(System.err);
            }
            return o;
        }

    }
}
