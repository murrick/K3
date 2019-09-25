package kanger.storage;

import kanger.User;
import kanger.interfaces.ICache;
import kanger.interfaces.Identifiable;
import org.cojen.tupl.Cursor;
import org.cojen.tupl.Database;
import org.cojen.tupl.Index;

import java.io.*;
import java.util.*;

public class Base implements ICache {

    protected Index index;
    private Index hash;
    private Stack<Long> stack;

    private ICache parent;
    private Database db = null;
    private String name = "";
    private int level = 0;
    private User user = null;

    public Base(User user, String name, Base parent) throws IOException {
        this.user = user;
        this.db = user.getDb();
        this.name = name;
        this.parent = parent;

        this.index = db.openIndex(name + ".index");
        this.hash = db.openIndex(name + ".hash");
        this.stack = new Stack<>();

    }

    public Base(Base parent) throws IOException {
        this.level = parent.level + 1;
        this.db = parent.db;
        this.name = parent.name;
        this.parent = parent;
        this.user = parent.user;

        this.index = db.openIndex(name + "." + level + ".index");
        this.hash = db.openIndex(name + "." + level + ".hash");
        this.stack = new Stack<>();

    }

    private byte[] fromObject(Object o) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ObjectOutput out = new ObjectOutputStream(buffer);
        out.writeObject(o);
        out.close();
        return buffer.toByteArray();
    }

    private Object toObject(byte[] bytes) throws Exception {
        if (bytes == null) {
            return null;
        } else {
            ObjectInput in = new ObjectInputStream(new ByteArrayInputStream(bytes));
            Object o = in.readObject();
            in.close();
//            if(o instanceof Identifiable) {
//                ((Identifiable) o).linkExternal(user);
//            }
            return o;
        }
    }

    public void add(Identifiable one) throws Exception {
        index.store(null, fromObject(one.getId()), fromObject(one));
        int h = one.getHash();
        Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(one.getId());
        hash.store(null, fromObject(h), fromObject(set));
    }

    public void add(long id, Object one) throws Exception {
        index.store(null, fromObject(id), fromObject(one));
        int h = one.hashCode();
        Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
        if (set == null) {
            set = new HashSet<>();
        }
        set.add(id);
        hash.store(null, fromObject(h), fromObject(set));
    }

//    public void add(Cache cache) {
//        index.putAll(cache.index);
//        hash.putAll(cache.hash);
//    }

    public Object get(long id) throws Exception {
        byte[] o = index.load(null, fromObject(id));
        if (o == null && parent != null) {
            return parent.get(id);
        }
        return toObject(o);
    }

    public int size() throws Exception {
        return (int) (index.count(null, null) + (parent == null ? 0 : parent.size()));
    }

    public boolean isEmpty() throws Exception {
        return size() == 0;
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

    public Set<Long> find(int h) throws Exception {
        Set<Long> list = new HashSet<>();
        Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
        if (set != null) {
            list.addAll(set);
        }
        if (parent != null) {
            list.addAll(parent.find(h));
        }
        return list;
    }

    public void remove(long id) throws Exception {
        Object one = index.load(null, fromObject(id));
        if (one != null) {
            index.delete(null, fromObject(id));
            int h = (one instanceof Identifiable) ? ((Identifiable) one).getHash() : one.hashCode();
            Set<Long> set = (Set<Long>) toObject(hash.load(null, fromObject(h)));
            if (set != null) {
                set.remove(one);
                if (set.isEmpty()) {
                    hash.delete(null, fromObject(h));
                } else {
                    hash.store(null, fromObject(h), fromObject(set));
                }
            }
        }
    }

    public void clear() throws Exception {
        index.evict(null, null, null, null, true);
        hash.evict(null, null, null, null, true);
        stack.clear();
        if (parent != null) {
            parent.clear();
        }
    }

    @Override
    public void mark() throws Exception {
        if (index.count(null, null) == 0) {
            stack.push(-1L);
        } else {
            Cursor c = index.newCursor(null);
            c.last();
            long lastKey = (long) toObject(c.key());
            stack.push(lastKey);
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
    public long release() throws Exception {
        if (!stack.isEmpty()) {
            long id = stack.pop();
            if (id == -1L) {
                index.evict(null, null, null, null, true);
                hash.evict(null, null, null, null, true);
            } else {
                List<Long> toDelete = new ArrayList<>();
                Cursor cursor = index.newCursor(null);
                cursor.findGe(fromObject(id));
                byte[] x;
                while ((x = cursor.key()) != null) {
                    long idx = (long) toObject(x);
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

//    public void mark() throws IOException {
//        if (index.count(null, null) == 0) {
//            stack.push(-1L);
//        } else {
//            stack.push(index.lastKey());
//        }
//    }
//
//    public long commit() {
//        if (!stack.isEmpty()) {
//            return stack.pop();
//        } else {
//            return -1;
//        }
//    }
//
//    public long release() {
//        if (!stack.isEmpty()) {
//            long id = stack.pop();
//            if (id == -1L) {
//                index.clear();
//                hash.clear();
//            } else {
//                List<Long> toDelete = new ArrayList<>();
//                for (long idx : index.tailMap(id).keySet()) {
//                    if (idx > id) {
//                        toDelete.add(idx);
//                    }
//                }
//                for (long idx : toDelete) {
//                    remove(idx);
//                }
//            }
//            return id;
//        } else {
//            return -1;
//        }
//    }

    public boolean containsKey(long id) throws Exception {
        if (index.load(null, fromObject(id)) == null) {
            if (parent != null) {
                return parent.containsKey(id);
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public void unlink() {

    }


//    protected long getNext(long id, NavigableMap<Long, Object> block) {
//        if (block.isEmpty()) {
//            return -1;
//        } else {
//            Long next = block.higherKey(id);
//            if (next != null) {
//                return next;
//            } else {
//                return -1;
//            }
//        }
//    }
//
//    protected long getPrevious(long id, NavigableMap<Long, Object> block) {
//        if (block.isEmpty()) {
//            return -1;
//        } else {
//            Long next = id == -1 ? block.lastKey() : block.lowerKey(id);
//            if (next != null) {
//                return next;
//            } else {
//                return -1;
//            }
//        }
//    }


    @Override
    public Iterator<Object> iterator() {
        try {
            return new CacheIterator(true, -1);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    public Iterator<Object> iterator(boolean backward, long fromId) {
        try {
            return new CacheIterator(backward, fromId);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return null;
        }
    }

    public class CacheIterator implements Iterator<Object> {

        private Cursor block = null;

        private long currentId = 0;
        private long minId = 0;
        private long maxId = 0;
        private boolean backward = false;
        private Iterator<Object> parentIterator = null;

        public CacheIterator() throws Exception {
            block.last();
            maxId = (long) (block.key() == null ? -1L : toObject(block.key()));
            block.first();
            minId = (long) (block.key() == null ? -1L : toObject(block.key()));

            block = index.newCursor(null);
            if (backward) {
                block.last();
                currentId = maxId;
            } else {
                block.first();
                currentId = minId;
            }
            if (parent != null) {
                parentIterator = parent.iterator();
            }
        }

        public CacheIterator(boolean backward, long fromId) throws Exception {
            block = index.newCursor(null);
            this.currentId = fromId;
            block.last();
            maxId = (long) (block.key() == null ? -1L : toObject(block.key()));
            block.first();
            minId = (long) (block.key() == null ? -1L : toObject(block.key()));
            this.backward = backward;
            if (parent != null) {
                parentIterator = parent.iterator(backward, fromId);
            }
            if (backward) {
                if (currentId == -1) {
                    currentId = maxId;
                }
                block.findLe(fromObject(currentId));
            } else {
                if (currentId == -1) {
                    currentId = minId;
                }
                block.findGe(fromObject(currentId));
            }

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
                if (block.key() == null) {
                    if (parentIterator != null) {
                        return parentIterator.hasNext();
                    } else {
                        return false;
                    }
                } else {
                    return true;
                }
            } else {
                if (parentIterator != null) {
                    if (parentIterator.hasNext()) {
                        return true;
                    } else {
                        return block.key() != null;
                    }
                } else {
                    return block.key() != null;
                }
            }
        }

        @Override
        public Object next() {
            if (backward) {
                try {
                    if (block.key() != null) {
                        currentId = (long) toObject(block.key());
                        block.previous();
                    } else if (parentIterator != null) {
                        return parentIterator.next();
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return null;
                }
            } else {
                if (parentIterator != null) {
                    Object o = parentIterator.next();
                    if (o != null) {
                        return o;
                    }
                }
                try {
                    currentId = (long) toObject(block.key());
                    block.next();
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    currentId = -1;
                }
            }
            if (currentId != -1) {
                try {
                    return get(currentId);
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    return null;
                }
            } else {
                return null;
            }
        }
    }

}
