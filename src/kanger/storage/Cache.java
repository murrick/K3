package kanger.storage;

import kanger.interfaces.ICache;
import kanger.interfaces.IStep;
import kanger.interfaces.Identifiable;

import java.io.IOException;
import java.util.*;

public class Cache implements ICache {

    protected NavigableMap<Long, Object> index;
    private Map<Integer, Set<Long>> hash;
    private Stack<Long> stack;
    private ICache parent;

    public Cache(ICache parent) {
        this.index = new TreeMap<>();
        this.hash = new HashMap<>();
        this.stack = new Stack<>();

        this.parent = parent;

        if (parent != null) {
            index.putAll(((Cache) parent).index);
            hash.putAll(((Cache) parent).hash);
        }
    }

    @Override
    public void add(Identifiable one) throws IOException, ClassNotFoundException {
        index.put(one.getId(), one);
        int h = one.getHash();
        if (!hash.containsKey(h)) {
            hash.put(h, new HashSet<>());
        }
        hash.get(h).add(one.getId());
    }

    @Override
    public void add(long id, Object one) {
        index.put(id, one);
        int h = one.hashCode();
        if (!hash.containsKey(h)) {
            hash.put(h, new HashSet<>());
        }
        hash.get(h).add(id);
    }

//    public void add(Cache cache) {
//        index.putAll(cache.index);
//        hash.putAll(cache.hash);
//    }

    @Override
    public Object get(long id) {
        Object o = index.get(id);
//        if (o == null && parent != null) {
//            o = parent.get(id);
//        }
        return o;
    }

    @Override
    public int size() {
        return index.size(); // + (parent == null ? 0 : parent.size());
    }

    @Override
    public boolean isEmpty() {
        return index.isEmpty(); // && (parent == null || parent.isEmpty());
    }

//    public long firstKey() {
//        if (index.firstKey() != null) {
//            return index.firstKey();
//        } else {
//            return -1;
//        }
//    }
//
//    public long lastKey() {
//        if (index.lastKey() != null) {
//            return index.lastKey();
//        } else {
//            return -1;
//        }
//    }

    @Override
    public Set<Long> find(int h) {
        Set<Long> list = new HashSet<>();
        if (hash.containsKey(h)) {
            list.addAll(hash.get(h));
        }
//        if (list.isEmpty() && parent != null) {
//            list.addAll(parent.find(h));
//        }
        return list;
    }

    //    @Override
    private void remove(long id) throws IOException, ClassNotFoundException {
        Object one = index.get(id);
        if (one != null) {
            int h = (one instanceof Identifiable) ? ((Identifiable) one).getHash() : one.hashCode();
            if (hash.containsKey(h)) {
                hash.get(h).remove(one);
                if (hash.get(h).isEmpty()) {
                    hash.remove(h);
                }
            }
            index.remove(id);
        }
    }

    @Override
    public void clear() {
        index.clear();
        hash.clear();
//        if (parent != null) {
//            parent.clear();
//        }
    }

    @Override
    public void mark() {
        if (index.isEmpty()) {
            stack.push(-1L);
        } else {
            stack.push(index.lastKey());
        }
    }

    @Override
    public long commit() {
        if (!stack.isEmpty()) {
            return stack.pop();
        } else {
            return -1;
        }
    }

    @Override
    public long release() throws IOException, ClassNotFoundException {
        if (!stack.isEmpty()) {
            long id = stack.pop();
            if (id == -1L) {
                index.clear();
                hash.clear();
            } else {
                List<Long> toDelete = new ArrayList<>();
                for (long idx : index.tailMap(id).keySet()) {
                    if (idx > id) {
                        toDelete.add(idx);
                    }
                }
                for (long idx : toDelete) {
                    remove(idx);
                }
            }
            return id;
        } else {
            return -1;
        }
    }

    @Override
    public boolean containsKey(long id) {
        if (!index.containsKey(id)) {
//            if (parent != null) {
//                return parent.containsKey(id);
//            } else {
            return false;
//            }
        } else {
            return true;
        }
    }

    @Override
    public void unlink() {

    }

    @Override
    public IStep getRoot() {
        return null;
    }

    @Override
    public void setRoot(IStep root) {
    }

    @Override
    public IStep getTop() {
        return null;
    }

    @Override
    public void setTop(IStep top) {
    }

    protected long getNext(long id, NavigableMap<Long, Object> block) {
        if (block.isEmpty()) {
            return -1;
        } else {
            Long next = block.higherKey(id);
            if (next != null) {
                return next;
            } else {
                return -1;
            }
        }
    }

    protected long getPrevious(long id, NavigableMap<Long, Object> block) {
        if (block.isEmpty()) {
            return -1;
        } else {
            Long next = id == -1 ? block.lastKey() : block.lowerKey(id);
            if (next != null) {
                return next;
            } else {
                return -1;
            }
        }
    }


    @Override
    public Iterator<Object> iterator() {
        return new CacheIterator(true, -1);
    }

    @Override
    public Iterator<Object> iterator(boolean backward, long fromId) {
        return new CacheIterator(backward, fromId);
    }

    @Override
    public boolean update() {
        return false;
    }


    public class CacheIterator implements Iterator<Object> {

        private NavigableMap<Long, Object> block = new TreeMap<>();

        private long currentId = 0;
        private boolean backward = false;
//        private Iterator<Object> parentIterator = null;

        public CacheIterator(boolean backward, long fromId) {
            block.putAll(index);
            this.currentId = fromId;
            this.backward = backward;
//            if (parent != null) {
//                parentIterator = parent.iterator(backward, fromId);
//            }

//            if(fromId >= 0) {
//                get(fromId);
//                if(!backward) {
//                    currentId = getPrevious(currentId, block);
//                } else {
//                    currentId = getNext(currentId, block);
//                }
//            }
        }


        @Override
        public boolean hasNext() {
            if (backward) {
                if (getPrevious(currentId, block) != -1) {
                    return true;
//                } else if (parentIterator != null) {
//                    return parentIterator.hasNext();
                } else {
                    return false;
                }
            } else {
//                if (parentIterator != null) {
//                    if (parentIterator.hasNext()) {
//                        return true;
//                    }
//                }
                return getNext(currentId, block) != -1;
            }
        }

        @Override
        public Object next() {
            if (backward) {
                currentId = getPrevious(currentId, block);
//                if (currentId == -1 && parentIterator != null) {
//                    return parentIterator.next();
//                }
            } else {
//                if (parentIterator != null) {
//                    Object o = parentIterator.next();
//                    if (o != null) {
//                        return o;
//                    }
//                }
                currentId = getNext(currentId, block);
            }
            if (currentId != -1) {
                return block.get(currentId);
            } else {
                return null;
            }
        }
    }

}
