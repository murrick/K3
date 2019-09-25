package kanger.storage;

import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Stack;

public class Escalera implements ICache {

    private Step root = null;
    private Step top = null;
    private Escalera parent = null;
    private Stack<Step> stack = new Stack<>();

    public Escalera(ICache parent) {
        this.parent = (Escalera) parent;

        if (this.parent != null) {
            root = this.parent.root;
            top = this.parent.top;
        }
    }


    @Override
    public void add(Identifiable one) throws Exception {
        Step s = new Step();
        s.setData(one);
        s.setId(one.getId());
        s.setHash(one.getHash());

        s.setNext(root);
        if (root != null) {
            root.setPrev(s);
        }
        root = s;
        if (top == null) {
            top = s;
        }
    }

    @Override
    public void add(long id, Object one) throws Exception {
        Step s = new Step();
        s.setData(one);
        s.setId(id);
        s.setHash(one.hashCode());

        s.setNext(root);
        if (root != null) {
            root.setPrev(s);
        }
        root = s;
        if (top == null) {
            top = s;
        }
    }

    @Override
    public Object get(long id) throws Exception {
        for (Step s = root; s != null; s = s.getNext()) {
            if (s.getId() == id) {
                return s.getData();
            }
        }
        return null;
    }

    @Override
    public int size() throws Exception {
        int cnt = 0;
        for (Step s = root; s != null; s = s.getNext()) {
            ++cnt;
        }
        return cnt;
    }

    @Override
    public boolean isEmpty() throws Exception {
        return root == null;
    }

    @Override
    public Set<Long> find(int h) throws Exception {
        Set<Long> set = new HashSet<>();
        for (Step s = root; s != null; s = s.getNext()) {
            if (s.getHash() == h) {
                set.add(s.getId());
            }
        }
        return set;
    }

    @Override
    public void clear() throws Exception {
        root = null;
    }

    @Override
    public void mark() throws Exception {
        if (root != null) {
            stack.push(root);
        }
    }

    @Override
    public long commit() throws Exception {
        if (!stack.isEmpty()) {
            return stack.pop().getId();
        } else {
            return -1;
        }
    }

    @Override
    public long release() throws Exception {
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
    public void unlink() {
        if (root != null && root.getPrev() != null) {
            root.getPrev().setNext(null);
            root.setPrev(null);
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


    public class WalkIterator implements Iterator {
        private Step step;
        private boolean backward;

        public WalkIterator(boolean backward, long fromId) {
            this.backward = backward;
            step = backward ? root : top;
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
            step = backward ? step.getNext() : step.getPrev();
            return o;
        }

    }

    public class Step {
        Object data = null;
        Step next = null;
        Step prev = null;
        long id = -1;
        int hash = 0;

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }

        public Step getNext() {
            return next;
        }

        public void setNext(Step next) {
            this.next = next;
        }

        public Step getPrev() {
            return prev;
        }

        public void setPrev(Step prev) {
            this.prev = prev;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public int getHash() {
            return hash;
        }

        public void setHash(int hash) {
            this.hash = hash;
        }
    }
}
