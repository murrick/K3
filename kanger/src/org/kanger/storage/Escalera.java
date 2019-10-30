package org.kanger.storage;

import org.kanger.exception.OutOfBufferException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;

import java.io.IOException;
import java.util.*;

public class Escalera implements ICache {

    private IStep root = null;
    private IStep top = null;
//    private IStep child = null;

    private ICache parent = null;
    private Stack<IStep> stack = new Stack<>();
    private IUser user = null;
    private String schema = "";

    public Escalera(IUser user, String schema, ICache parent) {
        this.parent = parent;
        this.user = user;
        this.schema = schema;

        if (this.parent != null) {
            root = this.parent.getRoot();
            top = this.parent.getTop();
        } else {
            if (!user.isClosed()) {
                root = user.getStorage(schema).getRoot();
                top = user.getStorage(schema).getTop();
            }
        }
    }


    @Override
    public void add(IUnit one) throws IOException, ClassNotFoundException, OutOfBufferException {
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());

        s.setNext(root);
        if (root != null) {
            root.setPrev(s);
            root.update();
//            child = s;
        }
        root = s;
        if (top == null) {
            top = s;
        }
    }

    @Override
    public void add(long id, Object one) throws IOException {
        Step s = new Step();
        s.setData(one);
        s.setId(id);
        s.setHash(one.hashCode());

        s.setNext(root);
        if (root != null) {
            root.setPrev(s);
            root.update();
        }
        root = s;
        if (top == null) {
            top = s;
        }
    }

    @Override
    public Object get(long id) throws IOException, ClassNotFoundException, OutOfBufferException {
        if (root instanceof Sapato) {
            root.setData(((Sapato) user.getStorage(schema).get(root.getId())).getData());
        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getId() == id) {
                return s.getData();
            }
        }
        // Прямое обращение к БД имеет значение только в начальной загрузке
//        return user.isClosed() ? null : user.getStorage(schema).get(id).getData();
        return null;
    }

    @Override
    public void delete(long id) throws IOException {
        if (root != null && root.getId() == id) {
            IStep s = root;
            root = root.getNext();
            s.delete();
        } else if (top != null && top.getId() == id) {
            IStep s = top;
            top = top.getPrev();
            s.delete();
        } else {
            for (IStep s = root; s != null; s = s.getNext()) {
                if (s.getId() == id) {
                    s.delete();
                    break;
                }
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
    public Set<Long> find(int h) throws IOException, ClassNotFoundException, OutOfBufferException {
        Set<Long> set = new HashSet<>();
        if (root instanceof Sapato) {
            root.setData(((Sapato) user.getStorage(schema).get(root.getId())).getData());
        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getHash() == h) {
                set.add(s.getId());
            }
        }
        return set;
    }

    @Override
    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException {
        root = null;
        top = null;
        if (parent == null && !user.isClosed()) {
            user.getStorage(schema).clear();
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
    public boolean containsKey(long id) throws IOException, ClassNotFoundException, OutOfBufferException {
        return get(id) != null;
    }

    @Override
    public void unlink() throws IOException {
        if (root != null && root.getPrev() != null) {
            root.getPrev().setNext(null);
            root.setPrev(null);
            root.update();
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new WalkIterator(true, -1);
    }

    @Override
    public Iterator<Object> iterator(boolean backward, long fromId) {
        return new WalkIterator(backward, fromId);
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
    public IStep getTop() {
        return top;
    }

    @Override
    public void setTop(IStep top) {
        this.top = top;
    }

    @Override
    public boolean update() throws IOException {
        // Это самый низ
        if (parent == null && !user.isClosed()) {
            long lastId = user.getStorage(schema).isEmpty() ? -1 : user.getStorage(schema).getRoot().getId();
            List<IStep> list = new ArrayList<>();
            for (IStep s = root; s != null; s = s.getNext()) {
                if (s.getId() < lastId) {
                    break;
                }
                list.add(s);
            }
            for (IStep p : list) {
                Sapato s = new Sapato(user.getStorage(schema), p);
                s.append();
            }

            root = user.getStorage(schema).getRoot();
            top = user.getStorage(schema).getTop();
            stack.clear();

            return true;
        } else {
            return false;
        }
    }

    public class WalkIterator implements Iterator {
        private IStep step;
        private boolean backward;

        public WalkIterator(boolean backward, long fromId) {
            this.backward = backward;
            step = backward ? root : top;

            try {
                if (root instanceof Sapato) {
                    root.setData(user.getStorage(schema).get(root.getId()).getData());
                }
                if (top instanceof Sapato) {
                    top.setData(user.getStorage(schema).get(top.getId()).getData());
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }

            if (fromId >= 0) {
                for (; step != null; step = backward ? step.getNext() : step.getPrev()) {
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
            Object o = step.getData();
            if (backward) {
                step = step.getNext();
            } else {
                if (step.getPrev() == null && !user.isClosed()) {
                    // Чистая магия
                    IStep stop = step;
                    for (step = root; step != null; step = step.getNext()) {
                        if (step.getNext() != null && step.getNext().getId() == stop.getId()) {
                            break;
                        }
                    }
                } else {
                    step = step.getPrev();
                }
            }
            return o;
        }

    }
}
