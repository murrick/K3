package kanger.storage;

import kanger.interfaces.Identifiable;

import java.util.*;

public class Cache implements Iterable<Identifiable> {

    private NavigableMap<Long, Identifiable> index = new TreeMap<>();
    private Map<Integer, Set<Identifiable>> hash = new HashMap<>();
    private Stack<Long> stack = new Stack<>();

    public void add(Identifiable one) {
        index.put(one.getId(), one);
        int h = one.getHash();
        if (!hash.containsKey(h)) {
            hash.put(h, new HashSet<>());
        }
        hash.get(h).add(one);
    }

    public void add(Cache cache) {
        index.putAll(cache.index);
        hash.putAll(cache.hash);
    }

    public Identifiable get(long id) {
        return index.get(id);
    }

    public int size() {
        return index.size();
    }

    public Identifiable getFirst() {
        if (index.firstEntry() != null) {
            return index.firstEntry().getValue();
        } else {
            return null;
        }
    }

    public Identifiable getLast() {
        if (index.lastEntry() != null) {
            return index.lastEntry().getValue();
        } else {
            return null;
        }
    }

    public List<Identifiable> find(int h) {
        List<Identifiable> list = new ArrayList<>();
        if (hash.containsKey(h)) {
            list.addAll(hash.get(h));
        }
        return list;
    }

    public void remove(long id) {
        Identifiable one = get(id);
        if (one != null) {
            int h = one.getHash();
            if (hash.containsKey(h)) {
                hash.get(h).remove(one);
                if (hash.get(h).isEmpty()) {
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

//    public void reindex(Identifiable root) {
//        clear();
//        //TODO: реализовать обход
////        for(Identifiable one = root; one != null; one = one.getNext()) {
////            add(one);
////        }
//    }

    public void mark() {
        stack.push(index.lastKey());
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
            List<Long> toDelete = new ArrayList<>();
            for (long idx : index.tailMap(id).keySet()) {
                if (idx != id) {
                    toDelete.add(idx);
                }
            }
            for (long idx : toDelete) {
                remove(idx);
            }
            return id;
        } else {
            return -1;
        }
    }

    private long getNext(long id, NavigableMap<Long, Identifiable> block) {
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

    private long getPrevious(long id, NavigableMap<Long, Identifiable> block) {
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
    public Iterator<Identifiable> iterator() {
        return new CacheIterator(true);
    }

    public class CacheIterator implements Iterator<Identifiable> {

        private NavigableMap<Long, Identifiable> block = new TreeMap<>();

        private long currentId = 0;
        private boolean backward = false;

        public CacheIterator() {
            currentId = -1;
            block.putAll(index);
        }

        public CacheIterator(boolean backward) {
            this();
            this.backward = backward;
        }

        @Override
        public void remove() {

        }

        @Override
        public boolean hasNext() {
            if (backward) {
                return getPrevious(currentId, block) != -1;
            } else {
                return getNext(currentId, block) != -1;
            }
        }

        @Override
        public Identifiable next() {
            if (backward) {
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
