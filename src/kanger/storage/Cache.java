package kanger.storage;

import kanger.interfaces.Identifiable;

import java.util.*;

public class Cache implements Iterable {

    protected NavigableMap<Long, Object> index;
    private Map<Integer, Set<Object>> hash;
    private Stack<Long> stack;

    public Cache() {
        index = new TreeMap<>();
        hash = new HashMap<>();
        stack = new Stack<>();
    }

    public void add(Identifiable one) {
        index.put(one.getId(), one);
        int h = one.getHash();
        if(!hash.containsKey(h)) {
            hash.put(h, new HashSet<>());
        }
        hash.get(h).add(one);
    }

    public void add(long id, Object one) {
        index.put(id, one);
        int h = one.hashCode();
        if (!hash.containsKey(h)) {
            hash.put(h, new HashSet<>());
        }
        hash.get(h).add(one);
    }

    public void add(Cache cache) {
        index.putAll(cache.index);
        hash.putAll(cache.hash);
    }

    public Object get(long id) {
        return index.get(id);
    }

    public int size() {
        return index.size();
    }

    public boolean isEmpty() {
        return index.isEmpty();
    }

    public long firstKey() {
        if (index.firstKey() != null) {
            return index.firstKey();
        } else {
            return -1;
        }
    }

    public long lastKey() {
        if (index.lastKey() != null) {
            return index.lastKey();
        } else {
            return -1;
        }
    }

    public List<Object> find(int h) {
        List<Object> list = new ArrayList<>();
        if (hash.containsKey(h)) {
            list.addAll(hash.get(h));
        }
        return list;
    }

    public void remove(long id) {
        Object one = get(id);
        if(one != null) {
            int h = (one instanceof Identifiable) ? ((Identifiable) one).getHash() : one.hashCode();
            if(hash.containsKey(h)) {
                hash.get(h).remove(one);
                if(hash.get(h).isEmpty()) {
                    hash.remove(h);
                }
            }
            index.remove(id);
        }
    }

    public void clear() {
        index.clear();
        hash.clear();
    }

    public void mark() {
        if (index.isEmpty()) {
            stack.push(-1L);
        } else {
            stack.push(index.lastKey());
        }
    }

    public long commit() {
        if (!stack.isEmpty()) {
            return stack.pop();
        } else {
            return -1;
        }
    }

    public long release() {
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

    public boolean containsKey(long id) {
        return index.containsKey(id);
    }

    protected long getNext(long id, NavigableMap<Long, Object> block) {
        if(block.isEmpty()) {
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
        if(block.isEmpty()) {
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

    public Iterator<Object> iterator(boolean backward, long fromId) {
        return new CacheIterator(backward, fromId);
    }

    public class CacheIterator implements Iterator<Object> {

        private NavigableMap<Long, Object> block = new TreeMap<>();

        private long currentId = 0;
        private boolean backward = false;

        public CacheIterator()  {
            currentId = -1;
            block.putAll(index);
        }

        public CacheIterator(boolean backward, long fromId) {
            this();
            this.currentId = fromId;
            this.backward = backward;
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
        public void remove() {

        }

        @Override
        public boolean hasNext() {
                if(backward) {
                    return getPrevious(currentId, block) != -1;
                } else {
                    return getNext(currentId, block) != -1;
                }
        }

        @Override
        public Object next() {
                if(backward) {
                    currentId = getPrevious(currentId, block);
                } else {
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
