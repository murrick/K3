package org.kanger.storage;

import org.kanger.exception.OutOfBufferException;
import org.kanger.exception.RuntimeErrorException;
import org.kanger.interfaces.ICache;
import org.kanger.interfaces.IStep;
import org.kanger.interfaces.IUnit;
import org.kanger.interfaces.IUser;

import java.io.IOException;
import java.util.*;

public class Escalera implements ICache {

    private IStep root = null;

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
        } else {
            if (!user.isClosed()) {
                root = user.getStorage(schema).getRoot();
            }
        }
    }


    @Override
    public void add(IUnit one) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());
        s.setNext(root);
        root = s;
    }

    @Override
    public void add(long id, Object one) throws IOException {
        Step s = new Step();
        s.setData(one);
        s.setId(id);
        s.setHash(one.hashCode());
        s.setNext(root);
        root = s;
    }

    @Override
    public Object get(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        if (root instanceof Sapato) {
            IStep e = user.getStorage(schema).get(root.getId());
            root.setData(e.getData(user));
        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getId() == id) {
//                if(s.getData() != null && s.getData() instanceof IUnit && ((IUnit) s.getData()).getUser() == null) {
//                    ((IUnit) s.getData()).setUser(user);
//                }
                return s.getData(user);
            }
        }
        // Прямое обращение к БД имеет значение только в начальной загрузке
//        return user.isClosed() ? null : user.getStorage(schema).get(id).getData();
        return null;
    }

    @Override
    public void delete(long id) throws IOException {
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
        if (!user.isClosed()) {
            user.getStorage(schema).delete(id);
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
    public Set<Long> find(int h) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        Set<Long> set = new HashSet<>();
        if (root instanceof Sapato) {
            root.setData(((Sapato) user.getStorage(schema).get(root.getId())).getData(user));
        }
        for (IStep s = root; s != null; s = s.getNext()) {
            if (s.getHash() == h) {
                set.add(s.getId());
            }
        }
        return set;
    }


    @Override
    public void clear() throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
        root = null;
        if (parent == null) {
            if (!user.isClosed()) {
                user.getStorage(schema).clear();
            } else {
                user.clearCounters(schema);
            }
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
    public boolean containsKey(long id) throws IOException, ClassNotFoundException, OutOfBufferException, RuntimeErrorException {
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
            stack.clear();

            return true;
        } else {
            return false;
        }
    }

    public class WalkIterator implements Iterator {
        private IStep step;

        public WalkIterator(long fromId) {
            step = root;

            try {
                if (root instanceof Sapato) {
                    root.setData(user.getStorage(schema).get(root.getId()).getData(user));
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
                o = step.getData(user);
                step = step.getNext();
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
            return o;
        }

    }
}
